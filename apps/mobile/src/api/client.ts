import createClient from "openapi-fetch";

import { DEFAULT_REQUEST_TIMEOUT_MS } from "../config";
import type { paths } from "./generated/schema";
import type { RuntimeGuard } from "./guards";
import type { NetworkState, NetworkStatusProbe } from "./network";
import { unknownNetworkStatus } from "./network";

/**
 * The anonymous public API client.
 *
 * Every request and response type comes from `docs/api/openapi.json` through `pnpm generate:api`;
 * nothing here hand-writes an API shape (AGENTS.md §3.7). `generate:api` runs before `typecheck`
 * and `test`, so a client that has fallen behind the contract fails the build rather than the app.
 *
 * Two properties are product decisions rather than implementation details:
 *
 * - it never attaches an `Authorization` header, because nobody signs in (plan 023, decision 6);
 * - `offline` is returned only on real device network evidence, and a request that hit its deadline
 *   is `timeout` even when the device also claims to be offline (plan 023, "Error model").
 */

export type ApiFailureOutcome =
  | "notFound"
  | "offline"
  | "timeout"
  | "server"
  | "malformed"
  | "unknown";

export type ApiOutcome = "ok" | ApiFailureOutcome;

export type ApiResult<T> =
  | { outcome: "ok"; data: T }
  | { outcome: ApiFailureOutcome };

/** Only paths the contract actually serves with GET; asking for any other path is a type error. */
export type GetApiPath = {
  [Path in keyof paths]: paths[Path] extends { get: unknown } ? Path : never;
}[keyof paths] &
  string;

type GetResponses<P extends GetApiPath> = paths[P] extends { get: { responses: infer R } }
  ? R
  : never;

/**
 * The 200 body type of a GET path, taken from the generated schema.
 *
 * The public controllers answer without an explicit `produces`, so the committed spec declares
 * their bodies under a wildcard media type; the admin operations declare `application/json`. Both
 * spellings resolve to the same generated component type, which is why both are handled here.
 */
export type GetSuccessBody<P extends GetApiPath> =
  GetResponses<P> extends { 200: { content: infer Content } }
    ? Content extends { "application/json": infer Body }
      ? Body
      : Content extends { "*/*": infer Body }
        ? Body
        : never
    : never;

type RequiredKeysOf<T> = { [K in keyof T]-?: undefined extends T[K] ? never : K }[keyof T];

type GetParameters<P extends GetApiPath> = paths[P] extends { get: { parameters?: infer Params } }
  ? NonNullable<Params>
  : never;

/**
 * The generated parameter object for a path. `params` is required when the operation has a required
 * parameter (a path id, say) and optional when it does not, mirroring openapi-fetch's own rule.
 */
export type GetRequestOptions<P extends GetApiPath> = RequiredKeysOf<GetParameters<P>> extends never
  ? { params?: GetParameters<P> }
  : { params: GetParameters<P> };

export interface MobileApiClient {
  readonly baseUrl: string;
  readonly timeoutMs: number;
  /**
   * Performs a GET and classifies the outcome. `guard` decides whether the parsed body is the shape
   * the caller reads; a 2xx body that does not satisfy it is `malformed`.
   */
  get<P extends GetApiPath>(
    path: P,
    guard: RuntimeGuard<GetSuccessBody<P>>,
    options?: GetRequestOptions<P>,
  ): Promise<ApiResult<GetSuccessBody<P>>>;
}

export interface MobileApiClientOptions {
  baseUrl: string;
  timeoutMs?: number;
  /** Device network evidence. Defaults to "unknown", which can never produce `offline`. */
  networkStatus?: NetworkStatusProbe;
  /** Injectable fetch, used by tests. */
  fetch?: (input: Request) => Promise<Response>;
}

/**
 * openapi-fetch cannot forward its per-path generics through our own generic `path` parameter, so
 * the call goes through this narrow structural signature. It is the client's only cast: the body
 * stays `unknown` until `guard` narrows it, and the public method signature is derived from the
 * generated schema rather than from here.
 *
 * The body is requested as text so this client owns JSON parsing. Left to openapi-fetch, a
 * truncated or HTML body throws, and the throw is indistinguishable from a transport failure that
 * plan 023 classifies as `unknown`; a 200 that is not JSON has to be `malformed`.
 */
type UntypedGet = (
  path: string,
  init: { signal: AbortSignal; parseAs: "text"; params?: unknown },
) => Promise<{ response: Response; data?: unknown; error?: unknown }>;

export function createMobileApiClient(options: MobileApiClientOptions): MobileApiClient {
  const timeoutMs = options.timeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS;
  const networkStatus = options.networkStatus ?? unknownNetworkStatus;
  const client = createClient<paths>({
    baseUrl: options.baseUrl,
    ...(options.fetch ? { fetch: options.fetch } : {}),
  });
  const rawGet = client.GET as unknown as UntypedGet;

  async function get<P extends GetApiPath>(
    path: P,
    guard: RuntimeGuard<GetSuccessBody<P>>,
    options?: GetRequestOptions<P>,
  ): Promise<ApiResult<GetSuccessBody<P>>> {
    const controller = new AbortController();
    let deadlineElapsed = false;
    const deadline = setTimeout(() => {
      deadlineElapsed = true;
      controller.abort();
    }, timeoutMs);

    try {
      const call = await rawGet(path, {
        signal: controller.signal,
        parseAs: "text",
        ...(options?.params ? { params: options.params } : {}),
      });
      if (!call.response.ok) {
        return { outcome: call.response.status === 404 ? "notFound" : "server" };
      }
      if (typeof call.data !== "string" || call.data.length === 0) {
        return { outcome: "malformed" };
      }
      let parsed: unknown;
      try {
        parsed = JSON.parse(call.data);
      } catch {
        return { outcome: "malformed" };
      }
      if (!guard(parsed)) {
        return { outcome: "malformed" };
      }
      return { outcome: "ok", data: parsed };
    } catch {
      // Nothing is retried here on purpose: retry is manual and idempotent, driven by the screen.
      return { outcome: await classifyFailure(deadlineElapsed, networkStatus) };
    } finally {
      clearTimeout(deadline);
    }
  }

  return { baseUrl: options.baseUrl, timeoutMs, get };
}

async function classifyFailure(
  deadlineElapsed: boolean,
  networkStatus: NetworkStatusProbe,
): Promise<ApiFailureOutcome> {
  if (deadlineElapsed) {
    return "timeout";
  }
  let state: NetworkState = "unknown";
  try {
    state = await networkStatus.read();
  } catch {
    state = "unknown";
  }
  return state === "offline" ? "offline" : "unknown";
}

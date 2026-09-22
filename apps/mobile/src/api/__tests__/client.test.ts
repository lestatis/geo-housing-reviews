import { createMobileApiClient, type MobileApiClientOptions } from "../client";
import type { components } from "../generated/schema";
import { hasString, isRecord, type RuntimeGuard } from "../guards";
import {
  createExpoNetworkStatusProbe,
  type NetworkState,
  type NetworkStatusProbe,
} from "../network";

// Used by the one test that drives the client through the real device probe; `expo-network` is a
// native module, so it is stubbed rather than imported.
const mockGetNetworkStateAsync = jest.fn();
jest.mock("expo-network", () => ({
  getNetworkStateAsync: (...args: unknown[]) => mockGetNetworkStateAsync(...args),
}));

/**
 * The client foundation is exercised against a real path from the committed contract so that the
 * generated types, the URL building and the error classification are all covered by one set of
 * tests. Nothing here is a screen: the property detail UI arrives in 023-C.
 */
type PropertyResponse = components["schemas"]["PropertyResponse"];

const PROPERTY_PATH = "/api/properties/{propertyId}" as const;
const BASE_URL = "https://api.test";

/** A guard over the fields a caller of this path actually reads. */
const isPropertyResponse: RuntimeGuard<PropertyResponse> = (value): value is PropertyResponse =>
  isRecord(value) && hasString(value, "propertyId") && hasString(value, "canonicalName");

function onlineProbe(): NetworkStatusProbe {
  return { read: async () => "online" };
}

function probeReporting(state: NetworkState): NetworkStatusProbe {
  return { read: async () => state };
}

function jsonResponse(body: string, status = 200): Response {
  return new Response(body, { status, headers: { "content-type": "application/json" } });
}

function clientWith(
  fetch: (input: Request) => Promise<Response>,
  overrides: Partial<MobileApiClientOptions> = {},
) {
  return createMobileApiClient({
    baseUrl: BASE_URL,
    networkStatus: onlineProbe(),
    fetch,
    ...overrides,
  });
}

describe("mobile API client", () => {
  it("returns the parsed body for a 200 that matches the guard", async () => {
    const body = { propertyId: "prop-1", canonicalName: "Abashidze 12" };
    const client = clientWith(async () => jsonResponse(JSON.stringify(body)));

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "ok", data: body });
  });

  it("interpolates path parameters from the generated parameter object", async () => {
    const requests: Request[] = [];
    const client = clientWith(async (input) => {
      requests.push(input);
      return jsonResponse(JSON.stringify({ propertyId: "prop-1", canonicalName: "Abasi" }));
    });

    await client.get(PROPERTY_PATH, isPropertyResponse, { params: { path: { propertyId: "prop-1" } } });

    expect(requests[0]?.url).toBe(`${BASE_URL}/api/properties/prop-1`);
  });

  it("never attaches an Authorization header", async () => {
    const requests: Request[] = [];
    const client = clientWith(async (input) => {
      requests.push(input);
      return jsonResponse(JSON.stringify({ propertyId: "prop-1", canonicalName: "Abasi" }));
    });

    await client.get(PROPERTY_PATH, isPropertyResponse, { params: { path: { propertyId: "prop-1" } } });

    const request = requests[0];
    expect(request).toBeDefined();
    expect(request?.headers.get("authorization")).toBeNull();
    expect([...(request?.headers.keys() ?? [])]).not.toContain("authorization");
  });

  it.each([404])("classifies %i as notFound", async (status) => {
    const client = clientWith(async () => new Response(null, { status }));

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "gone" } },
    });

    expect(result).toEqual({ outcome: "notFound" });
  });

  it.each([400, 403, 500, 503])("classifies %i as server", async (status) => {
    const client = clientWith(async () => new Response("upstream exploded", { status }));

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "server" });
  });

  it("classifies a 200 whose body is not JSON as malformed", async () => {
    const client = clientWith(async () => jsonResponse("<html>proxy error</html>"));

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "malformed" });
  });

  it("classifies a 200 whose body fails the guard as malformed", async () => {
    const client = clientWith(async () => jsonResponse(JSON.stringify({ propertyId: 42 })));

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "malformed" });
  });

  it("classifies an empty 200 as malformed", async () => {
    const client = clientWith(async () => new Response(null, { status: 200 }));

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "malformed" });
  });

  it("classifies a deadline that elapses as timeout", async () => {
    const client = clientWith(
      (input) =>
        new Promise<Response>((_resolve, reject) => {
          input.signal.addEventListener("abort", () => reject(new Error("aborted")));
        }),
      { timeoutMs: 5 },
    );

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "slow" } },
    });

    expect(result).toEqual({ outcome: "timeout" });
  });

  it("classifies a transport failure on a device with no network as offline", async () => {
    const client = clientWith(
      async () => {
        throw new TypeError("Network request failed");
      },
      { networkStatus: probeReporting("offline") },
    );

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "offline" });
  });

  it("reaches offline through the real device probe, not just an injected stub", async () => {
    // The client's classification and the probe's mapping are separate units; this is the seam
    // between them, and the only path by which `offline` can ever reach a user.
    mockGetNetworkStateAsync.mockResolvedValue({ isConnected: false });
    const client = createMobileApiClient({
      baseUrl: BASE_URL,
      networkStatus: createExpoNetworkStatusProbe(),
      fetch: async () => {
        throw new TypeError("Network request failed");
      },
    });

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "offline" });
  });

  it("never reports a timeout as offline, even when the device claims to be offline", async () => {
    const client = clientWith(
      (input) =>
        new Promise<Response>((_resolve, reject) => {
          input.signal.addEventListener("abort", () => reject(new Error("aborted")));
        }),
      { timeoutMs: 5, networkStatus: probeReporting("offline") },
    );

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "slow" } },
    });

    expect(result).toEqual({ outcome: "timeout" });
  });

  it("classifies a transport failure on a connected device as unknown, not offline", async () => {
    const client = clientWith(async () => {
      throw new TypeError("Network request failed");
    });

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "unknown" });
  });

  it("classifies a device whose network state cannot be read as unknown", async () => {
    const client = clientWith(
      async () => {
        throw new TypeError("Network request failed");
      },
      {
        networkStatus: {
          read: async () => {
            throw new Error("native module unavailable");
          },
        },
      },
    );

    const result = await client.get(PROPERTY_PATH, isPropertyResponse, {
      params: { path: { propertyId: "prop-1" } },
    });

    expect(result).toEqual({ outcome: "unknown" });
  });
});

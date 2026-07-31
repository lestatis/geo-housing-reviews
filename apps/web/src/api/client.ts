import createClient from "openapi-fetch";
import type { paths } from "./generated/schema";
import { accessToken } from "@/src/auth/session";

/**
 * The typed API client.
 *
 * <p>Every request and response type comes from `docs/api/openapi.json` via `pnpm generate:api`.
 * Nothing in this app hand-writes an API shape (AGENTS.md §3.7), and the spec itself is pinned by
 * OpenApiContractIntegrationTest, so a backend change that this app has not caught up with fails
 * the backend build rather than surfacing as a runtime surprise here.
 */
export function apiBaseUrl(): string {
  return process.env.API_BASE_URL ?? "http://localhost:8080";
}

/** Server-side only: reads the httpOnly session cookie, which client components cannot see. */
export async function serverApi() {
  const token = await accessToken();
  return createClient<paths>({
    baseUrl: apiBaseUrl(),
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

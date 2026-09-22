/**
 * Runtime configuration for the mobile app.
 *
 * The mobile client is anonymous: it talks to the public endpoints only, and there is no token or
 * session anywhere in this app (plan 023, decision 6). Nothing here may grow an authenticated
 * variant without a plan that says so.
 */

/** Every request fails with `timeout` once this deadline elapses. */
export const DEFAULT_REQUEST_TIMEOUT_MS = 10_000;

/** The API on the developer's machine. Override it with `EXPO_PUBLIC_API_BASE_URL`. */
export const DEFAULT_API_BASE_URL = "http://localhost:8080";

/**
 * Metro inlines `process.env.EXPO_PUBLIC_*` at build time, which is why the variable is read as a
 * literal member access rather than through a dynamic lookup.
 */
export function apiBaseUrl(): string {
  return process.env.EXPO_PUBLIC_API_BASE_URL ?? DEFAULT_API_BASE_URL;
}

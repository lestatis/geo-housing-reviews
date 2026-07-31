import * as client from "openid-client";

/**
 * Standards-only OIDC wiring. Nothing here names a vendor: the app discovers the issuer's endpoints
 * at runtime, so choosing a provider later (P-008) is a change of environment variables, not code.
 */

const SESSION_COOKIE = "gh_session";
const TRANSACTION_COOKIE = "gh_auth_tx";

export { SESSION_COOKIE, TRANSACTION_COOKIE };

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is not set; see apps/web/.env.example`);
  }
  return value;
}

export function appBaseUrl(): string {
  return process.env.APP_BASE_URL ?? "http://localhost:3000";
}

export function redirectUri(): string {
  return new URL("/api/auth/callback", appBaseUrl()).toString();
}

let discovered: Promise<client.Configuration> | undefined;

export function oidcConfiguration(): Promise<client.Configuration> {
  discovered ??= client.discovery(
    new URL(required("OIDC_ISSUER")),
    required("OIDC_CLIENT_ID"),
    process.env.OIDC_CLIENT_SECRET,
    undefined,
    // The local provider in infra/docker/docker-compose.yml is plain HTTP on loopback. Any other
    // issuer must be HTTPS, so an http:// issuer in a deployed environment fails here rather than
    // sending tokens over the wire in the clear.
    new URL(required("OIDC_ISSUER")).protocol === "http:"
      ? { execute: [client.allowInsecureRequests] }
      : undefined,
  );
  return discovered;
}

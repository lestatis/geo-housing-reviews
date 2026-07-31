import { execFileSync } from "node:child_process";
import path from "node:path";

/**
 * Puts one reported review in front of a moderator, using the same HTTP API a person would.
 *
 * <p>Two things are not HTTP and cannot be: minting a token (the local provider does it, standing
 * in for a person at a login form) and granting the first administrator. Identity exposes no
 * bootstrap-an-admin endpoint, so the role is written directly — exactly as the backend
 * acceptance suite does, and for the same reason. Everything else goes through the real endpoints
 * and the real security chain.
 */

const OIDC = process.env.OIDC_ISSUER ?? "http://localhost:8081/default";
const API = process.env.API_BASE_URL ?? "http://localhost:8080";
const COMPOSE = path.resolve(__dirname, "../../../infra/docker/docker-compose.yml");

export const MODERATOR = "nino-moderator";
const RESIDENT = "tamar-resident";
const REPORTER = "giorgi-neighbour";

async function tokenFor(subject: string): Promise<string> {
  const response = await fetch(`${OIDC}/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "client_credentials",
      client_id: subject,
      client_secret: "local-dev-only",
      scope: "openid",
    }),
  });
  if (!response.ok) {
    throw new Error(`the local OIDC provider refused a token for ${subject}`);
  }
  return ((await response.json()) as { access_token: string }).access_token;
}

async function call<T>(token: string, method: string, urlPath: string, body?: unknown): Promise<T> {
  const response = await fetch(`${API}${urlPath}`, {
    method,
    headers: {
      authorization: `Bearer ${token}`,
      ...(body ? { "content-type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    throw new Error(`${method} ${urlPath} → ${response.status} ${await response.text()}`);
  }
  return (await response.json()) as T;
}

function grantAdmin(accountId: string): void {
  execFileSync(
    "docker",
    [
      "compose",
      "-f",
      COMPOSE,
      "exec",
      "-T",
      "postgres",
      "psql",
      "-U",
      "geo_housing",
      "-d",
      "geo_housing",
      "-c",
      `update identity.account set role = 'ADMIN' where id = '${accountId}'`,
    ],
    { stdio: "pipe" },
  );
}

export async function seed(): Promise<{ propertyId: string; reviewId: string }> {
  // The moderator signs in once over HTTP so identity provisions the account, then is promoted.
  const moderatorToken = await tokenFor(MODERATOR);
  const moderator = await call<{ accountId: string }>(moderatorToken, "GET", "/api/me");
  grantAdmin(moderator.accountId);

  const residentToken = await tokenFor(RESIDENT);
  const property = await call<{ propertyId: string }>(residentToken, "POST", "/api/properties", {
    type: "BUILDING",
    canonicalName: `Vake Heights ${Date.now()}`,
    address: { country: "GE", city: "Tbilisi", street: "Abashidze Street" },
    allowDuplicate: true,
  });

  const review = await call<{ reviewId: string; version: number }>(
    residentToken,
    "POST",
    `/api/properties/${property.propertyId}/reviews`,
    {
      relationshipType: "CURRENT_RESIDENT",
      locale: "en",
      body: "The lift breaks every other week and nobody answers the building manager's phone.",
      recommendation: "NOT_RECOMMEND",
      ratings: [{ category: "NOISE", value: 2 }],
    },
  );

  // Only published content is reportable — an unpublished review's existence is not something a
  // stranger gets to confirm. So the moderator publishes it before anyone can complain about it.
  await call(moderatorToken, "POST", `/api/admin/reviews/${review.reviewId}/publish`, {
    version: review.version ?? 0,
    reasonCode: "CLEAN",
  });

  const reporterToken = await tokenFor(REPORTER);
  await call(reporterToken, "POST", "/api/reports", {
    targetType: "REVIEW",
    targetId: review.reviewId,
    category: "PERSONAL_DATA",
    description: "Mentions a neighbour by flat number.",
  });

  return { propertyId: property.propertyId, reviewId: review.reviewId };
}

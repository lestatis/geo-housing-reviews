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
export const SECOND_MODERATOR = "tekla-moderator";
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

/**
 * Writes the role directly. Reserved for the *first* administrator, because nothing in the product
 * can grant a role until somebody already holds one — every later grant goes through the API.
 */
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

/**
 * Makes this account the platform's only administrator.
 *
 * <p>Written directly, like the bootstrap above, because it is a statement about the whole
 * population rather than about one account — no endpoint expresses "and nobody else". Runs share a
 * database and each leaves its administrators behind, so a test asserting "this is the last one"
 * has to establish that rather than hope the previous run tidied up.
 */
export function leaveOnlyAdministrator(accountId: string): void {
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
      `update identity.account set role = 'USER' where role = 'ADMIN' and id <> '${accountId}'`,
    ],
    { stdio: "pipe" },
  );
}

export type SeededCase = {
  propertyId: string;
  reviewId: string;
  caseId: string;
};

export async function seed(): Promise<SeededCase> {
  // Both moderators sign in once over HTTP so identity provisions the accounts. Two of them,
  // because an appeal must be heard by someone other than the original decider.
  //
  // Only the first is written directly: nothing can grant a role until somebody holds one. The
  // second goes through the API like a real operator would.
  const moderatorToken = await tokenFor(MODERATOR);
  const moderator = await call<{ accountId: string }>(moderatorToken, "GET", "/api/me");
  grantAdmin(moderator.accountId);

  const secondToken = await tokenFor(SECOND_MODERATOR);
  const second = await call<{ accountId: string }>(secondToken, "GET", "/api/me");
  const seen = await call<{ version: number; role: string }>(
    moderatorToken,
    "GET",
    `/api/admin/accounts/${second.accountId}`,
  );
  if (seen.role !== "ADMIN") {
    await call(moderatorToken, "PATCH", `/api/admin/accounts/${second.accountId}/role`, {
      role: "ADMIN",
      version: seen.version,
    });
  }

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
  const report = await call<{ caseId: string }>(reporterToken, "POST", "/api/reports", {
    targetType: "REVIEW",
    targetId: review.reviewId,
    category: "PERSONAL_DATA",
    description: "Mentions a neighbour by flat number.",
  });

  // A reporter is never told the case id — that is the point of ReportResponse. The seed asks as a
  // moderator, which is who legitimately knows the mapping.
  const queue = await call<{ items: { caseId: string; targetId: string }[] }>(
    moderatorToken,
    "GET",
    "/api/admin/moderation/cases",
  );
  const opened = queue.items.find((entry) => entry.targetId === review.reviewId);
  if (!opened) {
    throw new Error(`reporting ${review.reviewId} opened no case (report ${JSON.stringify(report)})`);
  }

  return { propertyId: property.propertyId, reviewId: review.reviewId, caseId: opened.caseId };
}

/**
 * A case already decided against the author, and appealed — the state the appeals queue works from.
 *
 * <p>MODERATOR takes the content down, so MODERATOR is the one who may not hear the appeal.
 */
export async function seedAppeal(): Promise<SeededCase> {
  const seeded = await seed();
  const moderatorToken = await tokenFor(MODERATOR);

  await call(moderatorToken, "POST", `/api/admin/moderation/cases/${seeded.caseId}/decide`, {
    action: "REMOVE",
    reasonCode: "DOXXING",
    publicExplanation: "Your review named a neighbour.",
    internalNote: "Third from this account.",
  });

  const residentToken = await tokenFor(RESIDENT);
  await call(residentToken, "POST", "/api/appeals", {
    targetType: "REVIEW",
    targetId: seeded.reviewId,
    appealText: "I never named anyone. The flat number was my own.",
  });

  return seeded;
}

/**
 * A Tier 2 verification case with a document attached, waiting for a moderator.
 *
 * <p>The evidence is a synthetic 1×1 PNG. It must never be anything resembling a real lease or
 * identity document — `.claude/rules/security.md` forbids fixtures that could be mistaken for one,
 * and a test that needs a realistic document is a test that should not exist.
 */
export async function seedVerification(): Promise<{ caseId: string; propertyId: string }> {
  const moderatorToken = await tokenFor(MODERATOR);
  grantAdmin((await call<{ accountId: string }>(moderatorToken, "GET", "/api/me")).accountId);

  const residentToken = await tokenFor(RESIDENT);
  const property = await call<{ propertyId: string }>(residentToken, "POST", "/api/properties", {
    type: "BUILDING",
    canonicalName: `Verification Court ${Date.now()}`,
    address: { country: "GE", city: "Batumi", street: "Rustaveli Street" },
    allowDuplicate: true,
  });

  const opened = await call<{ caseId: string }>(residentToken, "POST", "/api/verifications", {
    propertyId: property.propertyId,
    method: "DOCUMENT",
    relationshipClaim: "CURRENT_RESIDENT",
  });

  const form = new FormData();
  form.set("document", new Blob([SYNTHETIC_PNG], { type: "image/png" }), "synthetic.png");
  const uploaded = await fetch(
    `${API}/api/verifications/${opened.caseId}/evidence`,
    { method: "POST", headers: { authorization: `Bearer ${residentToken}` }, body: form },
  );
  if (!uploaded.ok) {
    throw new Error(`attaching evidence → ${uploaded.status} ${await uploaded.text()}`);
  }

  return { caseId: opened.caseId, propertyId: property.propertyId };
}

/** A 1×1 transparent PNG. Synthetic by construction — it is not a document of any kind. */
const SYNTHETIC_PNG = Uint8Array.from(
  atob(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk" +
      "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  ),
  (c) => c.charCodeAt(0),
);

/** A property an administrator can act on. Created DRAFT, like every user-contributed record. */
export async function seedProperty(): Promise<{ propertyId: string; name: string }> {
  const moderatorToken = await tokenFor(MODERATOR);
  grantAdmin((await call<{ accountId: string }>(moderatorToken, "GET", "/api/me")).accountId);

  const name = `Lifecycle Terraces ${Date.now()}`;
  const residentToken = await tokenFor(RESIDENT);
  const property = await call<{ propertyId: string }>(residentToken, "POST", "/api/properties", {
    type: "BUILDING",
    canonicalName: name,
    address: { country: "GE", city: "Batumi", street: "Parnavaz Mepe Street" },
    allowDuplicate: true,
  });
  return { propertyId: property.propertyId, name };
}

/**
 * Two administrators and an ordinary resident with a pseudonym to look them up by.
 *
 * <p>Each run makes fresh accounts, so restricting or demoting one cannot disturb another test.
 */
export async function seedAccounts(): Promise<{
  moderatorAccountId: string;
  secondModeratorAccountId: string;
  residentAccountId: string;
  residentPseudonym: string;
}> {
  const moderatorToken = await tokenFor(MODERATOR);
  const moderator = await call<{ accountId: string }>(moderatorToken, "GET", "/api/me");
  grantAdmin(moderator.accountId);

  const secondToken = await tokenFor(SECOND_MODERATOR);
  const second = await call<{ accountId: string }>(secondToken, "GET", "/api/me");
  const seen = await call<{ version: number; role: string }>(
    moderatorToken,
    "GET",
    `/api/admin/accounts/${second.accountId}`,
  );
  if (seen.role !== "ADMIN") {
    await call(moderatorToken, "PATCH", `/api/admin/accounts/${second.accountId}/role`, {
      role: "ADMIN",
      version: seen.version,
    });
  }

  // A brand-new resident each run: the screen tests change this account's role and standing.
  const residentToken = await tokenFor(`resident-${Date.now()}`);
  const resident = await call<{ accountId: string; pseudonym: string }>(
    residentToken,
    "GET",
    "/api/me",
  );

  return {
    moderatorAccountId: moderator.accountId,
    secondModeratorAccountId: second.accountId,
    residentAccountId: resident.accountId,
    residentPseudonym: resident.pseudonym,
  };
}

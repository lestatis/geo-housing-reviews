# Plan 012 — the admin web app, and something to sign in with

Status: **chunk 1 complete, chunks 2–3 outstanding**

## Context

All five MVP loops work over HTTP, but loop 5's actual condition is "operate without database
access" — and until now an operator moderated with `curl`. `MVP_SCOPE` lists "admin interface" as a
Must-have and PRD §5.8 enumerates it.

This is the repo's first frontend. `ARCHITECTURE.md` §Clients specifies **Next.js for the admin web
app** with a **generated API client from OpenAPI**.

Two things were checked before planning:

- **The OpenAPI spec was ready** — 40 paths, 46 operations, 45 schemas, every operation with an
  `operationId` and every success response with a schema. No backend preparation was needed.
- **Nothing could sign in.** `jwk-set-uri` defaulted to `https://issuer.invalid/…`, every test used
  a stub `JwtDecoder`, and `P-008` leaves the IdP vendor "pending ADR". A moderator had no way to
  obtain a token, so a login screen had nothing to talk to.

## The sign-in problem, and what this plan does about it

It does **not** pick an IdP vendor. Instead `infra/docker/docker-compose.yml` gained a **local OIDC
provider** (`ghcr.io/navikt/mock-oauth2-server`), and the API points `OIDC_JWK_SET_URI` at it for
local development. Token validation stays real — the app verifies signatures against a JWKS exactly
as it will in production. The admin app implements a standards-only authorization-code flow with
PKCE via `openid-client`, so choosing a vendor is a change of environment variables.

The stub `JwtDecoder` in tests is unchanged; this plan adds no way to skip authentication in a
running application. MFA/step-up for moderators (SECURITY_PRIVACY §63) stays out of scope until the
vendor exists.

## Scope

| §5.8 item | Backing API | Plan |
|---|---|---|
| Review moderation queue | `/api/admin/reviews/**` | chunks 1–2 |
| Reports / disputes | `/api/admin/moderation/**` | chunks 1–2 |
| Verification queue | `/api/admin/verifications/**` | chunk 3 |
| Property merge queue | `/api/admin/properties/**` | chunk 3 |
| Role-based access | `ADMIN` role, enforced server-side | chunk 1 |
| User restrictions | none | out — no endpoint exists |
| Audit log | written everywhere, readable nowhere | out — no read endpoint |
| Basic metrics | none | out — that is the analytics gap |

## Chunks

### 1. Toolchain and one real screen — **complete**

pnpm workspace; `apps/web` (Next.js 15, React 19, TypeScript); generated client; the local OIDC
provider; working sign-in; the moderation queue listing real cases; Playwright driving all of it.

**How the client is generated, and why it cannot go stale.** `OpenApiContractIntegrationTest` boots
the application, canonicalises `/v3/api-docs` (springdoc builds it from hash-ordered maps, so keys
are sorted) and byte-compares it to `docs/api/openapi.json`. Change an endpoint without regenerating
and the **backend** build fails with the regeneration command in the message. `apps/web` then
generates `src/api/generated/schema.d.ts` from that file on every `dev`/`build`/`test`/`typecheck`,
and the generated file is not committed — it is a build product of a file that is. Verified in both
directions: the test passes on a fresh spec and fails on a one-word edit to it.

**Where the session lives.** The access token is stored in an httpOnly cookie and read only on the
server; the queue is a Server Component that calls the API server-side. The token never reaches
browser JavaScript and the API needs no CORS opening for the admin origin.

**What the queue shows.** `src/moderation/queue.ts` maps `ModerationCaseResponse` to a `QueueRow`,
and the page renders only `QueueRow` fields. "What a moderator can see in the queue" is therefore
one testable list rather than a property spread across JSX — and a case is worked from a **concern
count**, never from who raised it. Both the Vitest and the Playwright privacy assertions were proven
non-vacuous by making the page leak a reporter and watching exactly one test fail each time.

Timestamps render in UTC and say so. The page is server-rendered, so a browser-local format would
disagree with the clock the queue is measured against.

**A bug the negative-path test found.** A session the API rejects used to bounce between `/` and
`/moderation` forever: the queue redirected home on 401, and home forwarded anything holding a
cookie straight back. A Server Component cannot clear a cookie, so the fix is a route handler
(`/api/auth/expired`) that drops the dead session and explains itself. This only surfaced because
the suite tests a *tampered* session, not just a missing role.

### 2. Working a case — outstanding

Case detail, decide with reason code and explanation, appeals queue and appeal decision. This is the
screen that makes loop 5 real.

### 3. The other queues — outstanding

Verification decisions, property activate/hide/merge, account role management.

## What the UI must not do

- The moderation queue shows a **concern count, never reporter identities**.
- `ModerationDecisionResponse` carries `internalNote`. It is admin-only by construction; it must
  never reach a component shared with anything author-facing.
- Evidence is private media behind short-lived authorization — the admin app links to it, never
  proxies or caches it.

## Verification

```bash
cd apps/web && pnpm lint && pnpm typecheck && pnpm test
pnpm e2e                            # needs postgres + oidc + the API up; see apps/web/README.md
cd ../.. && ./scripts/check.sh      # the frontend branch now actually runs
```

`./scripts/check.sh` deliberately does **not** run `pnpm e2e`: those tests need a running stack, and
a gate that silently skips when it is absent is worse than one that never claimed to run it.

## Findings this chunk surfaced, not fixed

- **The OpenAPI document declares only success responses.** No operation documents its RFC 7807
  error bodies, so the generated client types `error` as `never` and destructuring a response
  narrows `response` away entirely (worked around explicitly in `app/moderation/page.tsx`).
  Documenting error responses would improve both the contract and the client.
- **Collision-suffixed `operationId`s.** `queue_1`, `decide_1`, `get_7` — springdoc's fallback when
  controller methods share a name. Harmless for a path-based client, poor for anyone reading the
  spec or generating a method-per-operation SDK.
- **No way to bootstrap the first administrator.** Identity exposes no endpoint, so both the backend
  acceptance suite and `apps/web/e2e/seed.ts` write the role with SQL. Fine for local development;
  it will need an answer before there is a production environment.
- **`scripts/validate_repo_governance.py` walked `node_modules`** and reported 75 "findings" from
  dependency files. Now scoped to directories this repository authors.

## Risks

Playwright needs browsers downloaded on first run. The local OIDC provider is one more container in
an environment that has already shown it leaks them when a run is killed — the `CONTRIBUTING.md`
recovery applies.

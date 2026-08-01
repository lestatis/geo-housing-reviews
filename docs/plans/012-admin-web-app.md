# Plan 012 — the admin web app, and something to sign in with

Status: **complete for what the API supports** — chunks 1–3 delivered; account-role management
and an admin property listing remain as backend work (see findings)

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

Role-based access moved from "chunk 1" to "chunk 1 for enforcement, never for management": the
`ADMIN` gate is enforced server-side and the app respects it, but nothing in the product can grant
or remove a role. See the findings below.

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

### 2. Working a case — **complete**

Case detail, decide with reason code and explanation, appeals queue and appeal decision. This is the
screen that makes loop 5 real.

**An API gap found before writing any UI.** `AdminAppealResponse` carried the appellant's text, the
status and the original decider — but nothing about *what* was being appealed. Since an appeal is by
rule heard by someone other than the original decider, that moderator arrives with no memory of the
case, and a queue showing only one side is a request to guess. `AppealService.pending()` now returns
`PendingAppeal` (appeal + contested decision + target) and the queue exposes it as
`AdminAppealQueueEntryResponse.contestedDecision`. Unit test first (it failed to compile), then the
implementation, then a Gherkin scenario — proven non-vacuous by changing the expected action and
watching exactly that scenario fail.

**Decisions are Server Actions.** Nothing in this app fetches the API from the browser; the session
cookie stays where it is. The decision form is a client component only so a rejected submission
keeps what was typed.

**Two due-process rules are mirrored, not owned, by the UI.** A takedown must tell the author why,
and an appeal outcome must be explained. Both are invariants on the server
(`ModerationDecision.requireExplanationWhenAdverse`, `Appeal.checkInvariants`); the form checks them
so a moderator learns before submitting rather than after. Both were proven load-bearing by removing
them and watching exactly the two journeys fail.

**`publicExplanation` and `internalNote` are never concatenated.** They are separate fields on
`DecisionRow` and separate elements on the page. Merging them is how a moderators-only note would one
day follow the author-facing explanation out of the building — proven caught at both the Vitest and
Playwright levels.

**Moderation's mutation threshold rose 85 → 86.** The chunk left the score at 86% (195/228), so the
ratchet moves, per `.claude/rules/testing.md`. One mutant this chunk added is uncovered — the
`orElseThrow` for a case that has gone missing under a pending appeal, which foreign keys make
unreachable. Contriving a repository into that state would test the test double, not the rule, so it
is left uncovered on purpose rather than papered over.

**The internal note is deliberately absent from the appeals queue.** It reaches the app, but a note
like "third from this account" is one moderator's characterisation of the author, and leading a
fresh hearing with it is how an appeal becomes a rubber stamp on the decision it is meant to test.
It stays one click away on the case, read as history rather than as the case for the prosecution.

### 3. The other queues — **complete for what the API supports**

Verification decisions and property activate/hide/merge. **Account role management is not built**:
identity exposes only `GET /api/admin/accounts/{accountId}`, which needs an id you already have, and
there is no endpoint that changes a role. That is the same gap both test suites work around with
SQL. Founder decision (2026-08-01): ship what is reachable, and leave the account-role API and an
admin property listing as backend work with their own decisions.

**Evidence is proxied, not linked — and the plan was wrong to say otherwise.** This document
previously required that the admin app "links to it, never proxies or caches it". Checking the API
showed no such link exists: `readEvidence` streams the bytes with `no-store`, and its own comment
says it is *"deliberately a proxied response, rather than a reusable storage URL"*. With the access
token httpOnly on the Next origin, a browser link to the API cannot authenticate at all. So
`/api/evidence/[caseId]/[evidenceId]` adds the bearer server-side and streams straight through:
nothing is stored, `no-store` and `nosniff` and `attachment` are asserted rather than merely
forwarded, and every view still lands in the API's access audit — which is the property that
actually matters. Founder decision (2026-08-01).

**Local evidence upload had never worked.** MinIO in `infra/docker/docker-compose.yml` had no
`MINIO_KMS_SECRET_KEY`, and `S3EvidenceStore` writes with server-side encryption, so MinIO answered
every upload with a 501 — "Server side encryption specified but KMS is not configured". The backend
integration tests set the key on their own container, which is why the suite never noticed. Compose
now sets the same throwaway localhost key.

**Two bugs the evidence journeys found once they could run.** `VerificationDecisionRequest.validThrough`
is an `Instant`, not a date, so the date input's `2027-07-30` was rejected outright with a 400 — every
approval carrying an expiry failed. And the server lapses a badge once `validThrough` is in the past,
so the chosen date has to become the instant that day *ends*: sending the start of the day would have
expired the badge on the morning of the date a moderator just said it was good through. Both are
covered by `expiryInstantFor` and its tests.

**Server actions are bound, not wrapped.** All four forms previously passed a client-side closure to
`useActionState`. React can only submit a form before hydration when the form's action *is* a server
action, so a wrapped one silently swallows a click that lands early — which Playwright reproduces
reliably on a route reached by client navigation. Every form now uses `action.bind(null, id)`. This
narrows the window rather than closing it in a dev build; the property journey still waits for the
route to settle, and says so.

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
- **The moderation enums are undocumented in OpenAPI.** `action`, `outcome`, `category`,
  `targetType` and the rest are typed as bare `string`, so generation produces nothing to pick from
  and `apps/web/src/moderation/decision.ts` restates `DecisionAction` — the one place in the client
  that duplicates something the API knows (AGENTS.md §3.7). Fixing it needs a backend decision, not
  a frontend one: either springdoc annotations in a module that has no springdoc dependency, or
  enum-typed request fields, which would change how an unrecognised value is reported (the
  controllers currently parse leniently and return a specific message). Deliberately left for its
  own chunk.
- **No way to bootstrap or change an administrator.** Identity exposes no role endpoint, so both the
  backend acceptance suite and `apps/web/e2e/seed.ts` write the role with SQL. This is why PRD §5.8's
  "role-based access" is enforced but unmanageable, and it needs an answer before there is a
  production environment. An audited `PATCH /api/admin/accounts/{id}/role` is the obvious shape.
- **No admin property listing.** Lifecycle actions are reachable only by id, so the admin app finds
  properties through the public search. Drafts awaiting activation and merge candidates are
  therefore not enumerable — a status-filtered admin listing would close it.
- **`scripts/validate_repo_governance.py` walked `node_modules`** and reported 75 "findings" from
  dependency files. Now scoped to directories this repository authors.

## Risks

Playwright needs browsers downloaded on first run. The local OIDC provider is one more container in
an environment that has already shown it leaks them when a run is killed — the `CONTRIBUTING.md`
recovery applies.

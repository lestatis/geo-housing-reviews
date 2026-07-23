# Task handoff

## Objective

Build the `verification` backend module (plan `docs/plans/006-verification-module.md`): a private
workflow that checks whether an account had its claimed relationship with a property, producing a
strength tier and a public-safe badge that feeds the reviews `VerificationTier` projection. MVP loop
3 ("Verify relationship → improve trust signal"). This is chunk 6 (public + admin endpoints).

## Active branch

`feat/006-verification-chunk6-endpoints` (branched from `main` at `853e895`)

## Related issue or plan

No issue. See `docs/plans/006-verification-module.md` — this is chunk 6 of 8. Tier 2 evidence is
deferred to plan 007 (its storage subsystem is highly sensitive and gets its own security review).

## Current status

chunk6_implemented — ready for fresh independent review and merge before chunk 7.

### Verification chunk 6 — public + admin endpoints (this branch)

User endpoints (all authenticated; a case is private) and admin moderation endpoints, with an
RFC 7807 handler scoped to the verification web package.

- `POST /api/verifications`, `GET /api/verifications/{id}`, `POST /api/verifications/{id}/cancel`,
  `GET /api/verifications?propertyId=` (my case for a property).
- `/api/admin/verifications`: queue, get, approve, reject (ROLE_ADMIN gate from the security chain).

**Points a reviewer should push on:**
- No security-config change was needed: `/api/verifications/**` is authenticated (not in the
  public-GET matcher, unlike properties/reviews) and `/api/admin/**` is admin-only. Confirm that is
  the intended privacy posture (cases are confidential, so yes).
- A stranger reading another account's case gets **404, not 403** — the private-workflow rule at the
  wire (asserted).
- Least exposure: `decidedBy` is omitted from the response; the badge carries no
  document/apartment/address.
- The full open → approve loop through the real security chain raises the review tier and writes the
  audit row (asserted end to end).

### Verification chunk 5 — persistence (merged to `main`)

JPA for the single-row case aggregate + the decision-audit table, and the Spring wiring that finally
runs the verification services in the app context.

- `JpaVerificationCaseRepository.save` loads the stored row and applies only the decision fields
  (immutable account/property/claim/method are out of reach), refusing a stale `@Version`.
- `JpaVerificationDecisionRepository` writes the case **through the case port** (so the optimistic
  check is not bypassed) and the audit row in one transaction.
- `VerificationBeanConfiguration` wires the services; the app `@EntityScan`/`@EnableJpaRepositories`
  now covers `verification.infrastructure.persistence`.

**Points a reviewer should push on:**
- The whole verification → reviews push is proven end to end on Postgres now
  (`VerificationToReviewProjectionIntegrationTest`): approve → the review row's tier rises to
  RELATIONSHIP_SIGNAL. The verify-first case (approve with no review) succeeds harmlessly.
- `save` is load-then-apply, not merge — deliberate, to keep immutable fields immutable and to give a
  deterministic version-conflict rather than relying on merge semantics.
- The cross-module push is not in the decision's DB transaction (idempotent, re-pushable) — same
  design as chunk 4.

### Verification chunk 4 — reviews.api inbound port + tier projection (merged to `main`)

The reviews↔verification seam, **push-only (verification → reviews)** — the founder-approved
resolution of the plan's push+pull, which would have formed a module cycle.

- Reviews publishes its first inbound contract: `reviews.api.ReviewVerificationUpdater` +
  `ReviewVerificationTier`, implemented by `ReviewVerificationApplier` (sets the tier on the author's
  live review). Wired as a bean.
- Verification: `ReviewProjection` outbound port, `ReviewProjectionAdapter` over reviews.api,
  `CatalogPropertyResolver` over properties.api. `VerificationDecisionService` pushes the resulting
  tier after every decision.

**Points a reviewer should push on:**
- Direction: push-only, reviews never calls verification (keeps them acyclic — the no-cycles ArchUnit
  rule was the forcing function). Was this the right call vs. pull-at-read?
- **Verify-first gap (accepted):** a review written after approval starts UNVERIFIED until a re-push.
- The tier push is a separate concern from the decision transaction — idempotent, re-pushable. Not
  transactional across modules by design.
- Verification services are **not wired in the app** yet (need chunk-5 repositories); only the two
  outbound adapters are component-scanned. The app context stays green.
- `CatalogPropertyResolver` is named differently from reviews' `CatalogPropertyLookup` to avoid a
  component-scan bean-name collision (which the app context caught).

### Verification chunk 3 — application layer (merged to `main`)

Framework-free use cases and the ports they need:
- `VerificationCaseRepository` (by id; live case for account+property, mirroring the V5.1 partial
  index; latest case for a badge; status queue; create/save), `PropertyLookup` (resolve to merge
  survivor — no reviewability question, since one may verify a past relationship to a now-withheld
  property), `VerificationDecisionRepository` (applyDecision + recordAttempt, atomic mutation+audit).
- Domain audit types added this chunk: `VerificationDecisionAction/Outcome/AuditEvent` (actor absent
  only for system EXPIRE, reason mandatory), plus not-found / version-conflict exceptions.
- `VerificationSubmissionService.open` / `.cancel`, `VerificationDecisionService.approve` / `.reject`,
  `VerificationQueryService.getById` / `.findMine` / `.pendingQueue`.

**Points a reviewer should push on:**
- Visibility is one shared rule (`VerificationVisibility`): a case is visible only to its owner and
  moderators, reported **not found** to everyone else — and there is **no anonymous viewer** (a case
  is confidential, unlike a published review).
- `open` resolves the property to its merge survivor and enforces one live case (PENDING or APPROVED)
  per account+property; an APPROVED case still blocks a duplicate.
- `cancel` is owner-only; a moderator withdrawing a case is a REJECT (audited), not a cancel.
- Decisions validate the reason first, refuse a stale `expectedVersion` before any transition, and
  audit a decision against a missing case as NOT_FOUND while returning empty.
- Chunk 4 note: the reviews-side tier projection and the `PropertyLookup` adapter over `properties.api`
  are the next chunk; this chunk only defines the outbound port.

### Verification chunk 2 — domain model (merged to `main`)

Framework-free `verification.domain`: the `VerificationCase` aggregate + state machine, the
method → tier mapping, and the public `VerificationBadge` projection.

**Points a reviewer should push on:**
- The badge label depends on the **tier**, not only the claim: a Tier 1 signal is always
  `RELATIONSHIP_SIGNAL_CONFIRMED`, never "verified current tenant" (§2). The four claim-specific
  badges are reachable only at Tier 2.
- Strength is granted by the method (`VerificationMethod.grantedTier()`), not chosen by the
  moderator — an approval is as strong as the method allows and no stronger.
- Revoke and expire both revert the tier to UNVERIFIED and are terminal; they never delete the case,
  so the review it fed falls back to unverified rather than disappearing (§8).
- Decision methods validate the reason **before** any state mutation (a bug the tests caught: a blank
  reason used to leave the case half-transitioned). Regression test present.

### Verification chunk 1 — foundation + schema (merged to `main`)

- `V5.1__create_verification_tables.sql` (verification schema; migration major version 5, after
  reviews' 4). `verification_case` with opaque `account_id`/`property_id` (no cross-module FK),
  claim/method/status/tier CHECKs, `verified_at`/`valid_through`/`decided_by`, `version BIGINT`, a
  partial unique index for one live case (PENDING or APPROVED) per (account, property), and two
  guard constraints: an APPROVED case must record `verified_at`, and an APPROVED/REJECTED case must
  record `decided_by` + `decision_reason_code`. `verification_decision_audit_event` is append-only
  with a mandatory non-blank reason code and no FK on actor/case (a NOT_FOUND attempt must still be
  auditable); `actor_account_id` is nullable **only** for system-initiated EXPIRE.
- Verified constraint-by-constraint on scratch Postgres before writing the test.
- Module build deps: `data-jpa` + `properties` (for the future `properties.api` check) + `assertj`;
  web deferred to the endpoints chunk.

**Points a reviewer should push on:**
- The Tier 1 / Tier 2 split: `method` CHECK lists only the three Tier 1 signal methods; `DOCUMENT`
  is intentionally absent until plan 007's evidence subsystem adds it in a later migration.
- The one-live-case slot deliberately includes APPROVED (an active badge blocks a duplicate);
  rejection/expiry/cancellation frees it. Same shape as reviews' one-live-review index.
- `actor_account_id` nullable for EXPIRE only — a human decision always records its actor.

## Remaining work

Chunks 7–8 (see the plan). Next up: chunk 7 (revocation + expiration: revoke an approved badge back
to unverified, expire current-resident badges; audit; the tier reverts on the review).

## Decisions and assumptions

- Migration registry by major version: root=1, identity=2, properties=3, reviews=4, verification=5.
- Cross-module refs are opaque UUIDs; property checked via `properties.api` from chunk 3.
- Verification → reviews is push-on-decision (chunk 4 publishes the first inbound `reviews.api`) plus
  pull-at-submit; reviews owns the tier projection.
- Automated fraud signals prioritise cases; a human always makes the adverse decision.

## Commands and tests

```bash
cd apps/api
./gradlew :modules:verification:check
./gradlew :app:test --tests '*VerificationMigrationIntegrationTest'
cd .. && ./scripts/check.sh
```

## Failures / unresolved risks

None. `./scripts/check.sh` passes (7 migration integration tests green).

Environment note: scratch verification containers named `geo-*-verify` may linger in `docker ps`
because `docker stop/kill` returns "permission denied" from the daemon for this user; they are
harmless `--rm` containers cleared by a daemon restart.

## Next action

Fresh independent review of chunk 1, then merge to `main` before starting chunk 2.

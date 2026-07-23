# Task handoff

## Objective

Build the `verification` backend module (plan `docs/plans/006-verification-module.md`): a private
workflow that checks whether an account had its claimed relationship with a property, producing a
strength tier and a public-safe badge that feeds the reviews `VerificationTier` projection. MVP loop
3 ("Verify relationship → improve trust signal"). This is chunk 3 (application layer).

## Active branch

`feat/006-verification-chunk3-application` (branched from `main` at `7b67ae9`)

## Related issue or plan

No issue. See `docs/plans/006-verification-module.md` — this is chunk 3 of 8. Tier 2 evidence is
deferred to plan 007 (its storage subsystem is highly sensitive and gets its own security review).

## Current status

chunk3_implemented — ready for fresh independent review and merge before chunk 4.

### Verification chunk 3 — application layer (this branch)

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

Chunks 4–8 (see the plan). Next up: chunk 4 (`reviews.api` inbound port + verification→reviews tier
push/pull, and the `PropertyLookup` adapter over `properties.api`).

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

# Task handoff

## Objective

Build the `verification` backend module (plan `docs/plans/006-verification-module.md`): a private
workflow that checks whether an account had its claimed relationship with a property, producing a
strength tier and a public-safe badge that feeds the reviews `VerificationTier` projection. MVP loop
3 ("Verify relationship → improve trust signal"). This is chunk 1 (foundation + schema).

## Active branch

`feat/006-verification-chunk1-foundation` (branched from `main` at `6312174`)

## Related issue or plan

No issue. See `docs/plans/006-verification-module.md` — this is chunk 1 of 8. Tier 2 evidence is
deferred to plan 007 (its storage subsystem is highly sensitive and gets its own security review).

## Current status

chunk1_implemented — ready for fresh independent review and merge before chunk 2.

### Verification chunk 1 — foundation + schema (this branch)

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

Chunks 2–8 (see the plan). Next up: chunk 2 (domain — `VerificationCase` aggregate + state machine,
methods, claim, tier mapping, badge projection).

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

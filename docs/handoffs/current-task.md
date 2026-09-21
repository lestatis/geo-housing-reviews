# Task handoff

## Objective

Complete Plan 019: when an appeal overturns a `RESTRICT_ACCOUNT` moderation decision, lift the
specific identity restriction that decision created, while preserving restrictions created by other
cases.

## Active branch

`feat/019-appeal-lifts-restriction`, with uncommitted implementation already present when this
handoff was recovered. The branch tip is `7946363`; the worktree is the source of truth for this
task.

## Related issue or plan

`docs/plans/019-appeal-lifts-restriction.md`; this is the second P1 in Group C of
`docs/plans/017-unreviewed-module-debt.md`.

## Current status

ready_for_review — implementation, verification and the end-to-end scenario are complete and
committed. The branch needs an independent review in a separate session before a human decides
whether to merge.

## Completed work

The recovered worktree implements the intended vertical slice:

- `AccountRestraint` returns the ID of a restriction it newly creates and adds an ID-based lift.
- The moderation decision stores the optional ID immutably, with JPA mapping and a nullable V6.2
  migration.
- `ModerationCaseService` carries the created ID into the persisted decision.
- `AppealService` passes the stored ID into the reviews adapter, which lifts it through identity.
- Identity resolves the affected account from its own restriction row, so a deleted review cannot
  strand a restriction after its decision is overturned; the administrator endpoint keeps its
  account-plus-ID guard.
- Unit tests cover the linked lift, isolation from an unrelated restriction, mapper round-tripping,
  and a deleted-review reversal. The app migration test confirms V6.2 and its nullable opaque link.
- **An acceptance scenario now covers the journey end to end** — restricted by a decision, appeal
  overturned, and the account can contribute again. It was missing when this handoff was recovered,
  and it is the only test that proves the user-visible outcome rather than the mechanism. Severing
  the link in `AppealService` fails exactly that scenario and nothing else.
- `docs/DOMAIN_MODEL.md` records the optional restriction reference in the conceptual model.

The complete diff has been inspected and the checks below passed. It has not been independently
reviewed in this implementation session.

## Remaining work

An independent review from the complete branch diff, then a human merge decision. No agent merges.

## Decisions made

- The decision records an optional opaque restriction ID, not an account-wide "active restriction"
  lookup; only the decision that created a restriction can reverse it.
- The nullable moderation link has no foreign key into identity, preserving module-owned schemas.
- The identity application contract resolves the account from a restriction ID only for this
  cross-module workflow. The administrator HTTP endpoint still requires both account and ID.

## Assumptions

- Historical decisions did not record an ownership link and therefore must not lift any restriction
  when overturned.
- A restriction manually lifted while its appeal is pending remains a successful no-op on reversal;
  identity retains the audit record of that attempted lift.

## Files changed

- Identity: `AccountRestraint`, `AccountRestrictionUseCase`, its service and transactional adapter,
  plus restriction tests.
- Moderation: decision/effect/appeal flow, JPA mapping, reviews adapter, V6.2 migration, and unit
  tests.
- App migration integration test and the Plan 019/domain-model/handoff documentation.

## Commands and tests

```bash
cd apps/api && ./gradlew :modules:moderation:test -PskipMutation  # passed
cd apps/api && ./gradlew :app:test --tests \
  'com.example.geohousing.app.moderation.ModerationMigrationIntegrationTest' -PskipMutation
  # passed
cd apps/api && ./gradlew :modules:moderation:check -PskipMutation  # passed after one
  Spotless-only failure in the new mapper test was corrected
./scripts/check.sh  # passed: governance, Gradle checks/mutation, web lint/test/typecheck
```

Final additional verification:

```bash
cd apps/api && ./gradlew :modules:identity:test :modules:moderation:test -PskipMutation
# passed
cd apps/api && ./gradlew :app:test --tests \
  'com.example.geohousing.app.moderation.ModerationMigrationIntegrationTest' \
  -PskipMutation --rerun-tasks  # passed
cd apps/api && ./gradlew :modules:identity:check :modules:moderation:check -PskipMutation
# passed
./scripts/check.sh  # passed in 5m 33s; identity mutation 90%, moderation mutation 86%
```

## Failures and blockers

None.

## Unresolved risks

- Cross-module orchestration cannot make identity and moderation atomic without a larger design;
  the existing effect-first decision protocol remains unchanged and is documented in the service.
- Historical decisions have no restriction link and therefore do not lift anything when overturned.
  This is intentional: guessing could end a restriction owned by another case.

## Next action

Perform an independent read-only review from the complete branch diff.

## Verified in this session

`./scripts/check.sh` → `EXIT=0`, re-run after the scenario was added rather than trusting an
up-to-date result. 41 acceptance scenarios, no failures.

## Last updated

2026-08-17

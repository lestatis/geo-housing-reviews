# Task handoff

## Objective

Implement plan 009, chunk 5: JPA persistence for reports, cases and decisions, so the moderation
module can actually run — and with it, the report → case → decision → effect flow end to end.

## Active branch

`feat/009-moderation-chunk5-persistence`, branched from clean `main` at `c466200`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/009-moderation-module.md`, chunk 5 of 8.

## Current status

completed, awaiting independent review

## Completed work

- Three JPA entities, one mapper, three Spring Data repositories, three port adapters under
  `moderation.infrastructure.persistence`.
- `ModerationBeanConfiguration` wiring `ReportIntakeService` and `ModerationCaseService`, with the
  policy version read from configuration.
- `GeoHousingApplication` gained moderation in `@EntityScan` and `@EnableJpaRepositories`.
- `ModerationCaseAlreadyOpenException` plus race recovery in `ReportIntakeService`.
- Two fixes to earlier chunks' code, both found by these tests (below).
- 9 persistence integration tests, 5 flow integration tests, 2 new adapter tests.

## Remaining work

Chunks 6–8: reporter endpoints, admin queue endpoints, appeals. Chunk 6 also brings loop 4's Gherkin
feature file, which plan 010 deferred until reporting had endpoints.

## Decisions made

- **The one-live-case race is recovered, not surfaced.** Intake catches
  `ModerationCaseAlreadyOpenException`, re-reads, and attaches its report to the case that won.
  Losing that race means convergence worked; reporting it as an error would punish the second
  reporter for doing nothing wrong.
- **Decisions are ordered by `(decided_at, id)`.** Ordering by timestamp alone is not a total order,
  and an audit trail that reorders itself between reads is not one an appeal can rely on.
- **`APPROVE` is conditional on current state.** It publishes content awaiting moderation, and is a
  no-op on content already visible. See the defect note below.
- **`save` refuses an aggregate that was never created** rather than silently inserting, and mutates
  the loaded row in place so Hibernate's `@Version` check covers the read-modify-write.
- **The decision entity has no mutator and no `@Version`**, matching a table with no `updated_at`.
  Append-only is expressed three times over — port, entity, schema.
- **Enums are stored by name.** The schema's CHECK constraints spell the values out, and an ordinal
  would silently remap every stored row the moment a constant is inserted.

## Assumptions

- `moderation.policy-version` defaults to 1. Raising it is an operational act, since an appeal must
  be judged under the policy in force when the decision was made.

## Files changed

- 10 new files under `modules/moderation/.../infrastructure/persistence/`
- new `modules/moderation/.../infrastructure/ModerationBeanConfiguration.java`
- new `modules/moderation/.../application/ModerationCaseAlreadyOpenException.java`;
  `ReportIntakeService` gained race recovery
- `modules/moderation/.../infrastructure/reviews/ReviewsModerationEffectApplier.java` (APPROVE fix)
- `app/src/main/java/com/example/geohousing/app/GeoHousingApplication.java` (scanning)
- new `app/src/test/.../moderation/ModerationPersistenceIntegrationTest.java`,
  `ModerationFlowIntegrationTest.java`; extended `ReviewsModerationAdapterTest`
- `docs/plans/009-moderation-module.md`, `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :modules:moderation:test -PskipMutation`
- `cd apps/api && ./gradlew :app:test --tests '*ModerationPersistenceIntegrationTest' --tests '*ModerationFlowIntegrationTest'`
- `cd apps/api && ./gradlew :modules:reviews:mutationTest :modules:moderation:mutationTest --rerun-tasks`
- `./scripts/check.sh` → `EXIT=0`, 3m 48s

## Tests and verification

All passed on 2026-07-29. Mutation: reviews 91% (threshold 85), moderation 87% (85).

**MVP loop 4 runs for the first time.** `ModerationFlowIntegrationTest` files a report against a
published review, converges three reporters onto one case, assigns it, decides `REMOVE`, and proves
the review is genuinely gone — plus the case decided, the reports closed out, the decision recorded
with its user-facing explanation, and reviews' own audit row written for the effect.

**Two defects in earlier chunks were found by these tests**, and both are the kind that only appear
against a real database or a real flow:

1. `findByCaseIdOrderByDecidedAtAsc` returned decisions in arbitrary order when two shared a
   timestamp. Fixed with an id tiebreaker.
2. `APPROVE` mapped to an unconditional `PUBLISH`, which throws `IllegalReviewStateTransitionException`
   on an already-published review — the *common* case, since most reports are about published
   content. Dismissing a report would have failed in production. Now `APPROVE` publishes only what is
   awaiting moderation and does nothing to what was never withdrawn.

## Known failures

None observed.

## Risks and unresolved questions

- `alreadyVisible` reads the target before deciding the effect, so there is a read-then-write window.
  The `expectedVersion` check on the write closes it: if the review changed in between, the apply is
  refused as a conflict.
- The moderation module now has no `-PskipMutation`-free margin to spare at 87% against a threshold
  of 85. Chunk 6 should expect to add assertions.
- Nothing exposes any of this over HTTP yet. A moderator still cannot work the queue without direct
  service access — that is chunks 6 and 7, and until then MVP loop 5 remains unmet.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge. When requested, start plan 009
chunk 6 (reporter endpoints plus loop 4's Gherkin feature file) from `main`.

## Last updated

2026-07-29

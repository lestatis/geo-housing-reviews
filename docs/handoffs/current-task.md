# Task handoff

## Objective

Implement plan 009, chunk 3: the moderation application layer — repository ports, report intake that
converges onto one live case per target, case assignment and decision recording. First chunk written
test-first under ADR-0009.

## Active branch

`feat/009-moderation-chunk3-application`, branched from clean `main` at `f2ea810`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/009-moderation-module.md`, chunk 3 of 8.

## Current status

completed, awaiting independent review

## Completed work

- Ports: `ReportRepository`, `ModerationCaseRepository`, `ModerationDecisionRepository`
  (append-only), and the outbound `ModerationTargetLookup` with its `ModeratableTarget` record.
- Application exceptions: `ModerationTargetNotFoundException`, `SelfReportNotAllowedException`,
  `DuplicateReportException`, `ModerationCaseNotFoundException`.
- `ReportIntakeService` and `ModerationCaseService`.
- Four in-memory fakes and 22 application tests.
- Killed the `touch()` mutation survivors carried over from chunk 2; `moderation` threshold raised
  75 → 85.
- Two mutation-tooling fixes in `geohousing.mutation-testing` (see below).

## Remaining work

Chunks 4–8 of plan 009. Chunk 4 (`reviews.api` inbound port + the adapter satisfying
`ModerationTargetLookup`) is next and needs a new branch from `main`.

## Decisions made

- **Test-first, genuinely.** Ports, exceptions and service stubs throwing
  `UnsupportedOperationException` went in first so the tests compiled and failed on behaviour rather
  than compilation; 22 tests were written and watched fail; then the services were implemented.
- **`ModerationTargetLookup` is declared with no adapter.** Same pattern reviews used for
  `PropertyLookup` in plan 005: the port states what this module needs, and chunk 4 satisfies it
  through `reviews.api`. Keeping the dependency inverted is what stops moderation reaching into
  another module's tables.
- **`ModerationDecisionRepository` has no `save`.** Append-only in the port, not just by convention,
  because an appeal must be able to show what was decided rather than what a decision later became.
- **Intake builds the report before opening a case.** A report the domain refuses (an `OTHER` with
  nothing written) would otherwise leave an orphan case for a moderator to puzzle over.
- **`decide` builds the decision before moving the case.** An adverse action missing its explanation
  refuses while the case is still `IN_REVIEW`; the alternative leaves a case marked decided with
  nothing recorded to explain it.
- **`APPROVE` dismisses the reports; anything else conclusive resolves them.** Recording an unupheld
  concern as "resolved" would overstate what happened, and that difference is what a reporter is
  owed. `ESCALATE` leaves reports open — it has decided nothing yet.
- **A refused report leaves no trace.** Tests assert both the case and report stores stay empty,
  because a partially-written refusal would turn the reporting endpoint into a way to probe for
  content.

## Assumptions

- Any moderator may decide a case that is `IN_REVIEW`, not only its assignee — a supervisor override
  is legitimate, and the decision records who made it either way. Worth revisiting if recusal rules
  get stricter.

## Files changed

- 10 new files under
  `apps/api/modules/moderation/src/main/java/com/example/geohousing/moderation/application/`
- 6 new files under the matching test package (4 fakes, 2 test classes)
- `apps/api/modules/moderation/src/test/.../domain/ModerationCaseTest.java` (2 new tests)
- `apps/api/modules/moderation/build.gradle.kts` (threshold 75 → 85)
- `apps/api/buildSrc/src/main/kotlin/geohousing.mutation-testing.gradle.kts` (exclusion glob)
- `docs/plans/009-moderation-module.md`, `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :modules:moderation:test -PskipMutation` (red, then green)
- `cd apps/api && ./gradlew :modules:<each>:mutationTest --rerun-tasks`
- `cd apps/api && ./gradlew :modules:moderation:spotlessApply`
- `./scripts/check.sh`

## Tests and verification

All passed on 2026-07-28. 22 new application tests (11 intake, 11 case service) plus 2 new domain
tests. The red phase was real and observed: every one of the 22 failed on
`UnsupportedOperationException` before the services existed.

**Mutation results after this chunk**, all modules re-measured against their thresholds:

| Module | Score | Threshold |
|---|---|---|
| identity | 83% | 80 |
| properties | 76% | 75 |
| reviews | 89% | 85 |
| verification | 89% | 85 |
| moderation | **87%** (was 76%) | **85** (was 75) |

**Two tooling defects found and fixed while doing this**, both of which had been inflating scores:

1. PITest was mutating the in-memory test doubles — they live in the `application` package, so the
   existing `*Test` exclusion missed them. Mutating a fake measures nothing about production code
   and pads the denominator.
2. The first fix (`InMemory*`) silently did nothing, because PITest globs match fully-qualified
   names. Corrected to `*.InMemory*`, verified by confirming the mutated-file list now contains only
   production classes.

## Known failures

None observed.

## Risks and unresolved questions

- `properties` remains 1 point above its threshold and is the most likely module to block a future
  chunk.
- Remaining `moderation` survivors are mostly `Appeal` accessors and `removed call to
  checkInvariants` after a valid transition, which is an equivalent mutant — killing it would require
  asserting a state the aggregate cannot reach. Chunk 8 (appeals) will cover the accessors naturally.
- The mutation step now dominates `./scripts/check.sh` when cold; the run exceeded 10 minutes on this
  branch. If it becomes a drag, narrow `targetClasses` rather than lowering thresholds.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge. When requested, start plan 009
chunk 4 (`reviews.api` inbound port and the moderation-side adapter) from `main`.

## Last updated

2026-07-28

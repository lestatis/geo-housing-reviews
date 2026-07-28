# Task handoff

## Objective

Implement plan 010, chunk 1: mutation testing as an enforced build gate, so that "we write good
tests" becomes a measured property rather than a claim. Cucumber/Gherkin is chunk 2.

## Active branch

`feat/010-mutation-testing-foundation`, branched from clean `main` at `b4956ba`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/010-bdd-and-mutation-testing.md`, chunk 1 of 3. ADR-0009.

## Current status

completed, awaiting independent review

## Completed work

- `gradle/libs.versions.toml`: cucumber 7.34.6 (for chunk 2), pitest 1.25.8, pitestJunit5 1.2.3.
- New `buildSrc/src/main/kotlin/geohousing.mutation-testing.gradle.kts` + `MutationTestingExtension`:
  registers a `mutationTest` `JavaExec` task driving `pitest-command-line`, wired into `check`.
- Applied to the five modules with domain/application code, each with a measured threshold.
- ADR-0009, plan 010, `.claude/rules/testing.md` and `AGENTS.md` §6 updated.

## Remaining work

Chunks 2–3 of plan 010 (Cucumber foundation + MVP loop features; then plan 009 resumes test-first).

## Decisions made

- **PITest runs via its CLI, not the Gradle plugin.** `info.solidsoft.pitest:1.15.0` (newest) cannot
  be applied on Gradle 9.6.1 — it reads `reporting.baseDir`, removed in Gradle 9. Recorded in
  ADR-0009 so nobody re-attempts it.
- **Mutation scope is `domain` + `application` only.** Mutants in Spring/JPA/web glue are largely
  equivalent or untestable; a score dominated by noise is one nobody acts on.
- **`--excludedClasses *Test,*Test$*,*IT,*IT$*`.** The target glob matches test classes in the same
  package, so without this PITest mutates the tests themselves. The pre-plan spike lacked this and
  reported an inflated 80% for `moderation` against a true 76%.
- **Thresholds are measured floors** (baseline rounded down to nearest 5), not aspirations.
- **Wired into `check`** rather than a separate script, so `./scripts/check.sh` enforces it.
  `-PskipMutation` exists for local iteration and is documented as never valid for pre-review runs.

## Assumptions

- Cucumber and `pitest-junit5-plugin` support JUnit Platform 6 only incidentally (both target 1.x).
  Verified working by spike; ADR-0009 records it as an upgrade hazard to re-check.

## Files changed

- `apps/api/gradle/libs.versions.toml`
- new `apps/api/buildSrc/src/main/kotlin/geohousing.mutation-testing.gradle.kts`,
  `MutationTestingExtension.kt`
- `apps/api/modules/{identity,properties,reviews,verification,moderation}/build.gradle.kts`
- new `docs/adr/0009-bdd-and-mutation-testing.md`, `docs/plans/010-bdd-and-mutation-testing.md`
- `.claude/rules/testing.md`, `AGENTS.md`, `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :modules:<each>:mutationTest --rerun-tasks`
- `./scripts/check.sh`

## Tests and verification

All passed on 2026-07-28. Measured baselines and thresholds:

| Module | Mutations | Killed | Score | Threshold |
|---|---|---|---|---|
| identity | 126 | 104 | 83% | 80 |
| properties | 103 | 78 | 76% | 75 |
| reviews | 219 | 194 | 89% | 85 |
| verification | 249 | 217 | 87% | 85 |
| moderation | 148 | 113 | 76% | 75 |

**The gate was proven non-vacuous two ways**, because a threshold that cannot fail is decorative:

1. Raising `moderation`'s threshold to 95 failed the build —
   `RuntimeException: Mutation score of 76 is below threshold of 95`.
2. Deleting one seven-test class (`AppealTest`) dropped `moderation` from 76% to 55% and failed.

Both were reverted; `git status` confirms no test file was left modified. A first attempt at proof —
weakening two `assertThatThrownBy` calls in `AppealTest` — did **not** move the score, because other
tests already killed those mutants; that attempt proved nothing and was not relied on.

Cold cost of the mutation step is 55s across five modules, so `./scripts/check.sh` goes from ~2m13s
to roughly 3m when it has not run recently.

## Known failures

None observed.

## Risks and unresolved questions

- Mutation scores can be gamed by assertions that kill mutants without checking meaning. The gate
  raises the floor; it does not replace review.
- `properties` (76%) and `moderation` (76%) sit closest to their thresholds, so they are the most
  likely to block a future chunk. That is the intended pressure, but the first chunk touching either
  should expect to add assertions.
- The known survivors in `moderation` (`removed call to touch()` on every `ModerationCase` mutator —
  nothing asserts `updatedAt` moves) are deliberately left for plan 009 chunk 3, which is the first
  work under the new test-first rule.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge. When requested, start plan 010
chunk 2 (Cucumber foundation and the five MVP loop feature files) from `main`.

## Last updated

2026-07-28

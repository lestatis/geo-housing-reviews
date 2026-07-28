# Task handoff

## Objective

Implement plan 010, chunk 2: a Cucumber acceptance harness so endpoint behaviour is written in
language a non-developer can check, plus feature files for the MVP loops that have endpoints today.

## Active branch

`feat/010-cucumber-acceptance`, branched from clean `main` at `52e2b43`. Local only; not pushed.
Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/010-bdd-and-mutation-testing.md`, chunk 2 of 3. ADR-0009.

## Current status

completed, awaiting independent review

## Completed work

- Cucumber dependencies in `app/build.gradle.kts` via `cucumber-bom`.
- `AcceptanceTest` — the `@Suite` runner over `classpath:features`.
- `AcceptanceWorld` — `@CucumberContextConfiguration @SpringBootTest @AutoConfigureMockMvc`, one
  Postgres container for the whole suite, and the same stub `JwtDecoder` the JUnit endpoint tests
  use so scenarios run through the real security chain.
- `ScenarioState` and `TestApi`, both scenario-scoped.
- Six step-definition classes: actors, catalogue, reviews, trust, moderation, outcomes.
- Four feature files, 16 scenarios: `find-property`, `submit-experience`, `verify-relationship`,
  `moderate-and-administer`.
- Gherkin rules added to `.claude/rules/testing.md`.

## Remaining work

Plan 010 chunk 3 is now just "plan 009 resumes test-first" — the rules landed in chunks 1 and 2, so
there is no separate documentation chunk left. Next real work is plan 009 chunk 3 (moderation
application layer), written test-first.

## Decisions made

- **Step definitions grouped by domain, not by Given/When/Then.** The plan sketched the keyword
  split; domain grouping scales better, because the step library grows per feature area and plan 009
  will add reporting steps to it. Noted as a deliberate deviation.
- **Outcome steps are phrased by meaning, not status code.** "the content is reported as not found"
  carries the privacy rule a 404 exists to enforce; "returns 404" would hide it. This is the main
  reason the feature files are worth reading.
- **`ScenarioState` and `TestApi` are `@ScenarioScope`.** Cucumber shares one Spring context across
  every scenario; a singleton would leak one scenario's actors and ids into the next, and the
  symptom would look like a flaky test rather than shared state.
- **One container for the whole suite.** The 14 JUnit endpoint classes each start their own;
  consolidating as they migrate is the main speed argument for the on-touch migration.
- **Loop 4 has no feature file yet.** "Report/dispute → resolve safely" has no endpoints — plan 009
  is building them — so its scenarios arrive with plan 009 chunk 6 rather than as a file of skipped
  scenarios that would report green while proving nothing.
- **`moderate-and-administer` asserts against the verification queue**, because no review queue
  endpoint exists yet. Reworded from "the moderation queue" so the scenario does not describe
  something the system lacks.

## Assumptions

- Publishing inside a `Given` step arranges its own moderator ("PublishingModerator") rather than
  requiring the feature file to introduce one. Scenarios about reading should not be cluttered with
  moderation plumbing.

## Files changed

- `apps/api/app/build.gradle.kts`
- new `apps/api/app/src/test/java/com/example/geohousing/app/acceptance/` (8 classes)
- new `apps/api/app/src/test/resources/features/` (4 feature files)
- `.claude/rules/testing.md`, `docs/plans/010-bdd-and-mutation-testing.md`,
  `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :app:test --tests 'com.example.geohousing.app.acceptance.AcceptanceTest'`
- `cd apps/api && ./gradlew :app:spotlessApply`
- `./scripts/check.sh`

## Tests and verification

All passed on 2026-07-28. 16 scenarios across four feature files, 0 failures, 0 skipped — verified by
reading the JUnit XML per feature rather than trusting `BUILD SUCCESSFUL`, since a suite that
discovers nothing also reports success.

**Proven non-vacuous two ways**, because a scenario suite that cannot fail is documentation wearing a
test's clothes:

1. Changing `REVIEW_NOT_FOUND` from 404 to 403 in `ReviewsExceptionHandler` failed exactly one
   scenario — "an author sees their own unpublished review but a stranger is told it does not
   exist". The scenarios therefore test real application behaviour, and this one guards a privacy
   rule specifically.
2. An intentionally undefined step failed its scenario rather than being skipped, confirming
   Cucumber 7's strictness is in force.

Both experiments were reverted; `git status` confirms no production file was left modified.

## Known failures

None observed. The IDE reports unresolved `io.cucumber` imports; that is a stale IDE classpath after
the module gained the dependency — Gradle compiles and runs the suite.

## Risks and unresolved questions

- The 16 scenarios overlap the JUnit endpoint tests in places. That is expected during on-touch
  migration and is not duplication to eliminate now: they sit at different altitudes until an area's
  JUnit class is migrated.
- `TestApi.grantAdministrator` writes the role via SQL because identity exposes no
  bootstrap-an-admin endpoint. It verifies the grant took effect, but it is the one place a scenario
  reaches past the API.
- Scenario count will grow fastest in plan 009; if suite runtime becomes a problem the answer is
  tagging (`@slow`) rather than deleting coverage.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge. When requested, start plan 009
chunk 3 (moderation application layer) from `main`, written test-first, and kill the `touch()`
mutation survivors in `ModerationCase` as part of it.

## Last updated

2026-07-28

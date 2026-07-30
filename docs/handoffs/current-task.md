# Task handoff

## Objective

Implement plan 009, chunk 6: the reporter-facing endpoints, plus MVP loop 4's Gherkin feature file
that plan 010 deferred until reporting had endpoints to describe.

## Active branch

`feat/009-moderation-chunk6-reporter-endpoints`, branched from clean `main` at `fedd42f`. Local
only; not pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/009-moderation-module.md`, chunk 6 of 8; completes an acceptance criterion of
`docs/plans/010-bdd-and-mutation-testing.md`.

## Current status

completed, awaiting independent review

## Completed work

- `report-and-dispute.feature` — 8 scenarios, **written first** and watched fail as undefined steps.
- `POST /api/reports` and `GET /api/reports/{reportId}` in a new `moderation.infrastructure.web`.
- `ReportQueryService`, `ReportNotFoundException`, `ReportRepository.findById`.
- `ModerationExceptionHandler` — RFC 7807, scoped to the moderation web package.
- `ReporterFacingStatus`, `ReportResponse`, `SubmitReportRequest`, `ModerationWebAuthentication`.
- `ReportSteps` acceptance steps; moderation gained `spring-boot-starter-web`.
- A privacy fix in chunk 3/4 code that the scenarios caught (below).

## Remaining work

Chunks 7 and 8: the admin queue endpoints, then appeals. Until chunk 7 lands, a moderator still
cannot work the queue over HTTP, so MVP loop 5 remains unmet.

## Decisions made

- **Visibility is a fact on the port, a policy in the service.** `ModeratableTarget.visible` reports
  whether the owning module shows the content publicly; `ReportIntakeService` decides what that
  means. A moderator works withdrawn content routinely, while a reporter must not learn it exists —
  one fact, two different rules, so the port must not bake either in.
- **A reporter sees a coarser status than moderation keeps.** `AWAITING_MODERATION` / `RESOLVED` /
  `DISMISSED`, never the internal `OPEN`/`LINKED` distinction. That distinction is queue plumbing,
  and exposing it would let a reporter infer how busy moderation is and whether others reported the
  same content.
- **Someone else's report is 404, not 403.** A "forbidden" confirms a report exists for that
  identifier, which is enough to learn that a given piece of content has been reported.
- **Self-report is 403.** The author wrote the content, so they already know it exists; explaining
  the refusal discloses nothing.
- **No listing endpoint and no case identifier anywhere in the reporter surface.** A reporter is owed
  the progress of their own concern and nothing more.
- **No JUnit endpoint test class.** Under ADR-0009 new endpoint behaviour lives in Gherkin; adding a
  parallel JUnit class would duplicate it at a second altitude for no gain.

## Assumptions

- Report categories and target types are parsed case-insensitively from the request and rejected as
  `INVALID_REQUEST` when unknown, matching how the properties controller parses its enums.

## Files changed

- new `app/src/test/resources/features/report-and-dispute.feature`
- new `app/src/test/.../acceptance/ReportSteps.java`; `ScenarioState` gained the current report
- 6 new files under `modules/moderation/.../infrastructure/web/`
- new `modules/moderation/.../application/ReportQueryService.java`,
  `ReportNotFoundException.java`; `ReportRepository`/`JpaReportRepository`/fake gained `findById`
- `ModeratableTarget` (visible), `ReviewsModerationTargetLookup`, `ReportIntakeService`
- `modules/moderation/build.gradle.kts`, `ModerationBeanConfiguration`
- `docs/plans/009-moderation-module.md`, `docs/plans/010-bdd-and-mutation-testing.md`,
  `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :app:test --tests '*AcceptanceTest'` (red first, then green)
- `cd apps/api && ./gradlew :modules:moderation:test -PskipMutation`
- `cd apps/api && ./gradlew :modules:moderation:mutationTest --rerun-tasks`
- `./scripts/check.sh` → `EXIT=0`, 4m 05s

## Tests and verification

All passed on 2026-07-29. The acceptance suite is now **24 scenarios across all five MVP loops**,
0 failures, 0 skipped. Mutation: moderation 85% (threshold 85).

**The scenarios caught a real privacy bug**, which is the whole argument for writing them first.
"Reporting a review that is not public does not confirm it exists" failed against an implementation
that otherwise worked: `ModerationTargetLookup` returned any review regardless of publication state,
so an unpublished review could be reported and the 201 confirmed it existed. Anyone could have
probed identifiers to discover content awaiting moderation. Now the port reports visibility and
intake refuses invisible targets as not-found, with a unit test alongside the scenario.

## Known failures

None observed.

## Risks and unresolved questions

- Moderation sits exactly **on** its mutation threshold (85 vs 85). The next chunk touching it will
  have to add assertions before it can merge. That is the gate working as intended, but it will feel
  like friction.
- `ReporterFacingStatus` collapses `RESOLVED` and `DISMISSED` into distinct public values, so a
  reporter does learn whether their concern was upheld. That seemed right — the content itself
  already reveals it — but it is a product judgement worth confirming.
- There is still no rate limit on reporting. The one-live-report-per-target index bounds abuse per
  target, not across targets; that remains separate policy work (plan 009 non-goals).

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge. When requested, start plan 009
chunk 7 (admin queue endpoints: list, assign, decide) from `main`.

## Last updated

2026-07-29

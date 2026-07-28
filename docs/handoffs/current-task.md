# Task handoff

## Objective

Implement plan 009, chunk 2: the moderation module's domain model — reports, cases, immutable
decisions and appeals, with their state machines and invariants. No persistence, endpoints or
cross-module dependency.

## Active branch

`feat/009-moderation-chunk2-domain`, branched from clean `main` at `3f6c683`. Local only; not pushed.
Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/009-moderation-module.md`, chunk 2 of 8.

## Current status

completed, awaiting independent review

## Completed work

- Aggregates: `Report`, `ModerationCase`, `ModerationDecision` (immutable), `Appeal`.
- Value objects: `ReportId`, `ModerationCaseId`, `ModerationDecisionId`, `AppealId`, `ReporterId`,
  `ModeratorId`, `AppellantId`, `ModerationTargetRef`, `ReasonCode`, `PolicyVersion`.
- Enums: `ReportCategory`, `ReportStatus`, `ModerationCaseStatus`, `CaseTrigger`, `RiskLevel`,
  `DecisionAction`, `AppealStatus`, `ModerationTargetType`.
- Exceptions: `IllegalModerationStateTransitionException`, `AppealDeciderConflictException`.
- 33 domain tests across four test classes.

## Remaining work

Chunks 3–8 of plan 009. Chunk 3 (application layer: ports, report intake converging onto the live
case, case assignment, decision recording) is next and needs a new branch from `main`.

## Decisions made

- **A decision may only be recorded on an `IN_REVIEW` case.** Stronger than the schema, which only
  requires an assignee for `IN_REVIEW`. The effect is that no outcome can exist without a named
  moderator accountable for it.
- **A case may only close from `DECIDED` or `APPEALED`.** Nothing lets a case vanish unexplained; the
  affected user is always owed a recorded reason, which is what makes an appeal possible at all.
- **`Appeal` carries `originalDecider`.** The different-decider rule is then checked on the object
  rather than by a caller that happens to look the decision up — and it mirrors the row-level CHECK
  added in chunk 1. Both layers hold it because due process should not depend on either alone.
- **A refused appeal decision moves nothing.** Conflict and missing-explanation checks run before any
  mutation, so a rejected attempt leaves the appeal exactly `PENDING` rather than half-decided. Same
  pattern as the fix made earlier in `VerificationCase.approve`.
- **Reassignment preserves `firstResponseAt`.** A recusal handover is not a second first response;
  the reporter waited once and the SLA should say so.
- **Distinct `ReporterId` / `ModeratorId` / `AppellantId` types** even though all three are account
  ids, so a reporter cannot be passed where a decision-maker is expected.
- **`ReasonCode` is a validated free code, not an enum.** The taxonomy grows with policy; pinning it
  in a type or a CHECK means a migration per reason code, which is how a moderator ends up choosing
  the nearest wrong code.
- **`DecisionAction.requiresPublicExplanation()`** puts "which actions owe the user an explanation"
  in one place shared by the domain and (already) the schema.

## Assumptions

- `PolicyVersion` is an integer that the application layer will supply from configuration in a later
  chunk; the domain only insists it is positive and recorded.
- `affectedTargetVersion` is nullable because a target without optimistic-lock versioning may be
  moderatable later; for reviews it is always present.

## Files changed

- 20 new files under
  `apps/api/modules/moderation/src/main/java/com/example/geohousing/moderation/domain/`
- updated `package-info.java` in the same package
- 4 new test classes under
  `apps/api/modules/moderation/src/test/java/com/example/geohousing/moderation/domain/`
- `docs/plans/009-moderation-module.md`, `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :modules:moderation:test`
- `cd apps/api && ./gradlew :modules:moderation:spotlessApply`
- `cd apps/api && ./gradlew :modules:moderation:check`
- `cd apps/api && ./gradlew :app:test --tests 'com.example.geohousing.app.architecture.*'`
- `./scripts/check.sh`

## Tests and verification

All passed on 2026-07-28. 33 domain tests: `ModerationCaseTest` (11) covers the full state machine
including every refused transition, the `firstResponseAt` rule on reassignment, and a takedown demand
as an ordinary case; `ReportTest` (8) covers intake, linking, terminal states and the
`OTHER`-needs-a-description rule; `ModerationDecisionTest` (7) covers the adverse-action explanation
rule across every action, the separation of public explanation from internal note, reason-code and
policy-version validation, and a reflection guard that the type grows no mutator; `AppealTest` (7)
covers the different-decider refusal in both directions, hear-once, the outcome-explanation
requirement, and `reconstitute` refusing rows that contradict due process. The four ArchUnit boundary
rules pass with real moderation domain classes now in scope.

## Known failures

None observed. The IDE reported unresolved `org.assertj` imports in the new test sources; that is a
stale IDE classpath after the module gained test dependencies — Gradle compiles and runs them.

## Risks and unresolved questions

- Requiring `IN_REVIEW` before a decision means the application layer must assign before deciding
  even for a one-step admin action. Chunk 7 should make that a single endpoint that assigns and
  decides, rather than forcing a moderator through two calls.
- `ModerationCase` and `Appeal` carry a `version` field for optimistic locking that nothing reads
  yet; chunk 5 wires it to `@Version`.

## Human actions required

None.

## Recommended next action

Independent review of the chunk-2 branch in a fresh session, then merge. When requested, start plan
009 chunk 3 (application layer) from `main`.

## Last updated

2026-07-28

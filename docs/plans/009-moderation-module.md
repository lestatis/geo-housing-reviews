# Moderation Module: Reports, Cases, Decisions and Appeals

Status: Complete
Owner: Claude
Related issue: none
Last updated: 2026-07-28

## Objective

Give the platform an auditable case workflow for contested content: a report becomes a case, a case
receives a reason-coded immutable decision under a recorded policy version, and a decision can be
appealed once to a different decider. This closes MVP loop 4, "Report/dispute → resolve safely", and
ROADMAP Phase 3's last open item.

## Acceptance criteria

- [x] The `moderation` schema owns reports, cases, decisions and appeals in the `V6.x` namespace,
      with no foreign key into another module's tables; many reports about one target converge on a
      single live case, and one account cannot report the same content repeatedly.
- [x] A signed-in account can report a review it can see; a reporter reads only their own report's
      status and never the case, another reporter, or a decision's internal note.
- [x] Decisions are append-only and carry action, reason code, policy version, the judged content
      version, a user-visible explanation for anything adverse, and an internal note that never
      reaches a user.
- [x] A decision's effect on a review is applied through the reviews module's published contract
      with its stale-version check; moderation never writes reviews' tables.
- [x] Exactly one appeal per decision, decided by an account other than the original decider, with
      the outcome and explanation recorded; negative paths have explicit tests.
- [x] An owner or developer takedown demand travels the same audited workflow as any other report.

## Non-goals

- Right of reply, representative claims and representative-reply moderation (plan 010).
- A `MODERATOR` role distinct from `ADMIN`.
- Automated content classifiers or risk scoring.
- IP/device abuse metadata or fingerprinting.
- User restrictions and account sanctions as a mechanism (identity owns account state; a decision may
  record that one was requested).
- Notification emails for moderation outcomes (Should-have, notifications module).

## Current system

Identity (002), properties (004), reviews (005 + 008) and verification (006 + 007) are complete and
merged. Reviews owns publication state and audits its own transitions; its `ReviewModerationService`
states the split explicitly ("Reports, appeals, redaction and the reason-code taxonomy belong to the
moderation module"), and `ReviewModerationAction` says richer decision actions belong here too.
`modules/moderation` was four empty `package-info.java` files. There is currently no way for any user
to report anything.

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|
| Migration namespace | `moderation` schema, `V6.x` (root=1, identity=2, properties=3, reviews=4, verification=5, moderation=6) | Module owns its tables | — |
| Cross-module refs | Opaque UUIDs, no FKs | Modules never reference another module's tables | — |
| Applying a decision | Moderation calls an inbound `reviews.api` port; reviews keeps publication state | ARCHITECTURE §5 split; one-way push, so no cycle (same shape as `ReviewVerificationUpdater`) | Another module needs the same effect |
| Case convergence | One live case per target, partial unique index | Twenty reports must not open twenty cases, or the queue is floodable | Targets gain sub-parts worth separate cases |
| Decisions | Append-only, immutable, with `policy_version` | Appeals must show what was decided and under which policy | — |
| Appeals | One per decision, different decider, enforced by a row-level CHECK | Due process must not depend on a service remembering it | A recusal workflow needs richer rules |
| Reporter privacy | Reporter identity never reaches the reported author or a public representation | SECURITY_PRIVACY: identifying critics is an abuse path | — |
| Scope | Stops before right of reply (founder, `P-013`) | A public reply is only safe once a representative claim is verified, and that is a Should-have that does not exist | Plan 010 |
| Moderator role | Reuse `ADMIN` (founder, `P-013`) | The rules that matter key off account id, which exists; a new role means an identity migration nobody needs yet | An operator needs queue access without full admin rights |

## Implementation chunks

1. **Foundation + schema** (this branch): module build deps, `V6.1`, migration integration test.
2. **Domain**: `Report`, `ModerationCase` state machine, immutable `ModerationDecision`, `Appeal`,
   `ReportCategory`, `DecisionAction`, `ReasonCode`, `PolicyVersion`.
3. **Application**: repository ports; report intake converging onto the live case for a target; case
   assignment; decision recording. In-memory-fake tests for self-report and duplicate refusal.
4. **`reviews.api` inbound port + adapter**: reviews publishes a moderation gateway carrying the
   `expectedVersion` stale-content check; moderation calls it when a decision hides/removes/restores.
5. **Persistence adapters** + integration tests: decision immutability, the one-live-case race, the
   one-appeal-per-decision race.
6. **Reporter endpoints**: `POST /api/reports` and a reporter reading only their own report status.
7. **Admin queue endpoints**: list/assign/decide, RFC 7807 advice scoped to the moderation web
   package, audit for every action.
8. **Appeals**, split once the work was understood:
   - **8a — reinstatement capability**: `V4.5` widens the audited action vocabulary, and reviews
     gains an appeal-only way back from a terminal decision.
   - **8b — appeals proper**: appeal persistence, author submission, admin appeal decision with the
     different-decider rule, and overturn wired to reinstatement.

## Verification

```bash
cd apps/api
./gradlew :modules:moderation:check
./gradlew :app:test --tests 'com.example.geohousing.app.moderation.ModerationMigrationIntegrationTest'
cd .. && ./scripts/check.sh
```

## Risks and rollback/forward-fix

The one-live-case index is the queue's flood defence; if it proved too strict (a target needing two
concurrent cases for unrelated issues), the forward fix is a discriminator column in the index, not
dropping it. Migrations are append-only after merge. Decisions and appeals are records of due
process: a forward fix may stop new writes but must never delete or rewrite existing rows.

## Progress log

- 2026-07-28: Plan created after the founder chose moderation as the next module, scoped it to stop
  before right of reply, and kept `ADMIN` as the moderator role (`P-013`). Started chunk 1 on
  `feat/009-moderation-chunk1-foundation` from clean `main` at `cec1e39`.
- 2026-07-28: Chunk 1 implemented. `V6.1` creates `moderation.moderation_case`, `report`,
  `moderation_decision` and `appeal`, all referencing other modules by opaque UUID only. The schema
  carries the rules that must not depend on application code: a partial unique index converges every
  report about one target onto a single live case while letting a settled case be reopened as a new
  one; a second partial unique index stops one account report-bombing the same content while still
  permitting a genuinely new report after the first is closed out; an adverse decision cannot be
  written without a user-visible explanation, while `APPROVE` and `ESCALATE` need none; a decision
  has no `updated_at` and no version column because nothing updates it; and an appeal is unique per
  decision with a row-level CHECK refusing a decider equal to the moderator being appealed against —
  the original decider is copied onto the appeal row precisely so a single-row CHECK can say it. The
  migration was verified statement-by-statement against a scratch Postgres before any test was
  written. 13 integration tests cover every CHECK, both partial unique indexes in the positive and
  negative direction, and an assertion that the schema holds no foreign key out of `moderation`.
  `:modules:moderation:check` and `./scripts/check.sh` pass. Ready for independent review.

- 2026-07-28: Chunk 1 fast-forward merged to `main` at `3f6c683`.
- 2026-07-28: Chunk 2 implemented on `feat/009-moderation-chunk2-domain`. Added the framework-free
  domain: `Report`, `ModerationCase`, immutable `ModerationDecision` and `Appeal`, plus opaque ids,
  `ModerationTargetRef`, `ReasonCode`, `PolicyVersion` and the enums. Two restrictions go beyond the
  schema on purpose: a decision may only be recorded on an `IN_REVIEW` case, so no outcome exists
  without a named moderator accountable for it; and a case may only close once decided, so nothing
  lets a case vanish unexplained — which is also what makes an appeal possible. `Appeal` carries the
  original decider so the different-decider rule is checked on the object rather than by a caller
  that remembers to look the decision up, and refuses a conflicted decision before any state moves.
  Reassignment keeps the original `firstResponseAt`, because a recusal handover is not a second
  first response and the SLA measures the reporter's actual wait. Distinct `ReporterId`,
  `ModeratorId` and `AppellantId` types stop the same account id being used in the wrong role.
  33 domain tests, including every `reconstitute` refusing state that contradicts the invariants and
  a reflection guard that `ModerationDecision` grows no mutator. No persistence, no endpoints, no
  cross-module dependency. `:modules:moderation:check`, the ArchUnit boundary rules and
  `./scripts/check.sh` pass. Ready for independent review.

- 2026-07-28: Chunk 2 fast-forward merged to `main` at `b4956ba`.
- 2026-07-28: Chunk 3 implemented on `feat/009-moderation-chunk3-application`, and the first chunk
  written test-first under ADR-0009. Ports, exceptions and service stubs went in so the tests could
  compile and fail on behaviour; 22 tests were written and watched fail on
  `UnsupportedOperationException`; then the services were implemented to green. Added
  `ReportRepository`, `ModerationCaseRepository`, `ModerationDecisionRepository` (append-only — no
  `save`, because an appeal must show what was decided rather than what a decision later became),
  and the outbound `ModerationTargetLookup` port with no adapter yet, exactly as reviews declared
  `PropertyLookup` before the properties contract existed. `ReportIntakeService` converges every
  report about one target onto one live case, refuses a self-report, a duplicate live report and a
  report about content that does not exist — and builds the report before opening a case, so a
  report the domain refuses leaves no orphan case behind. `ModerationCaseService` assigns and records
  decisions, building the decision before moving the case so an adverse action missing its
  explanation refuses while the case is still `IN_REVIEW`; a conclusive outcome resolves the reports
  that fed it, while `APPROVE` dismisses them, because recording an unupheld concern as "resolved"
  would overstate what happened. The `touch()` mutation survivors from chunk 2 were killed by
  asserting that every transition stamps `updatedAt`. Mutation score for `moderation` rose 76% → 87%
  and its threshold was raised 75 → 85. Two tooling fixes fell out of this: PITest was also mutating
  the in-memory test doubles (they share the `application` package), and the exclusion glob needed a
  leading wildcard because PITest matches fully-qualified names. All five module scores re-measured
  and holding. `./scripts/check.sh` passes. Ready for independent review.

- 2026-07-29: Chunk 3 fast-forward merged to `main` at `84b58ff`.
- 2026-07-29: Chunk 4 implemented on `feat/009-moderation-chunk4-reviews-gateway`. Reviews publishes
  `ReviewModerationGateway` — `find` plus an `apply` carrying `expectedVersion` — with
  `ModeratableReview`, `ReviewModerationEffect` and `ReviewModerationConflictException`. The
  contract deliberately carries **no review content**: moderation needs the author (to refuse a
  self-report) and the version (to stamp a decision and detect staleness), while a moderator who
  must read the review uses reviews' own audited admin endpoint rather than having the text copied
  into a second module. The reviews-side adapter adds no moderation logic — every effect goes
  through `ReviewModerationService`, so the stale-version check and the audit row committed with the
  mutation apply exactly as they do for reviews' own endpoints. Moderation gained
  `implementation(project(":modules:reviews"))` and two adapters; the `DecisionAction →
  ReviewModerationEffect` mapping lives in the adapter because whether an action even has a content
  effect is a property of the target's module, and four actions deliberately have none.
  `ModerationCaseService.decide` now **applies the effect before recording** (founder decision): if
  recording fails afterwards the content is correctly withheld and reviews' own audit already
  carries the action and reason, whereas recording first could leave a trail asserting a review was
  removed while it is still visible. `ModerationCase.requireDecidable()` was added so the
  accountability check runs before the irreversible part. 22 new tests (9 reviews adapter, 8
  moderation adapters, 5 app integration) plus 4 new case-service tests. The api-only boundary rule
  was proven non-vacuous for the new dependency: importing a reviews *internal* type failed
  `ModuleBoundaryArchitectureTest`, then reverted. Mutation: reviews 91% (threshold 85), moderation
  88% (85). The full report → case → decision flow cannot run end to end yet — moderation has no
  persistence adapters until chunk 5 — so the app test exercises the ports Spring resolves to these
  adapters. Ready for independent review.

- 2026-07-29: Chunks 3 and 4 fast-forward merged to `main` (`84b58ff`, `8729c4a`).
- 2026-07-29: Chunk 5 implemented on `feat/009-moderation-chunk5-persistence`. JPA entities, a
  mapper and adapters for all three ports, wired into the app's entity and repository scanning
  (moderation was absent from both lists, which is what a missing-bean failure first surfaced).
  Both partial unique indexes are translated at the port: the one-live-case index into
  `ModerationCaseAlreadyOpenException`, which intake now *catches and recovers from* — a report that
  loses the race to open a case re-reads and joins the case that won, because losing that race means
  convergence worked, not that the reporter did anything wrong. The one-live-report index maps to
  the existing `DuplicateReportException`.

  Two real defects surfaced while testing. Decisions were ordered by `decided_at` alone, which is
  not a total order when two decisions share an instant — an audit trail that reorders itself
  between reads is not one an appeal can rely on; the query now breaks ties on id. And
  `APPROVE` was mapped to an unconditional `PUBLISH`, which throws on an already-published review —
  i.e. on the *common* case of a report heard and not upheld. It now publishes only content that is
  awaiting moderation and is a no-op on content that was never withdrawn.

  MVP loop 4 runs end to end for the first time: `ModerationFlowIntegrationTest` files a report,
  converges three reporters onto one case, works it, and proves a `REMOVE` genuinely removes the
  review, with the case decided, reports closed out, the decision recorded with its explanation, and
  reviews' own audit row written. 9 persistence tests (both races included), 5 flow tests, 2 new
  adapter tests. Mutation: reviews 91% (85), moderation 87% (85). `./scripts/check.sh` passes in
  3m48s. Ready for independent review.

- 2026-07-29: Chunk 5 fast-forward merged to `main` at `fedd42f`.
- 2026-07-29: Chunk 6 implemented on `feat/009-moderation-chunk6-reporter-endpoints`, written
  outside-in: `report-and-dispute.feature` went in first and its eight scenarios were watched fail
  as undefined steps before any endpoint existed. `POST /api/reports` and
  `GET /api/reports/{id}` are the whole reporter surface — there is deliberately no listing and no
  path to a case.

  Writing the scenarios first paid for itself immediately. "Reporting a review that is not public
  does not confirm it exists" failed against a working implementation, because
  `ModerationTargetLookup` returned any review regardless of publication state — so an unpublished
  review could be reported, and the response confirmed it existed. Fixed by making visibility a
  *fact* the port reports (`ModeratableTarget.visible`) and the *policy* the service applies: a
  moderator works withdrawn content all the time, while a reporter must not learn it exists.

  A reporter sees a coarser status than moderation keeps: `AWAITING_MODERATION` / `RESOLVED` /
  `DISMISSED`, never the internal `OPEN`/`LINKED` distinction, which is queue plumbing and would let
  a reporter infer how busy moderation is. A scenario asserts the response mentions no case, no
  other reporter, no moderator and no internal note.

  Loop 4 completes the acceptance suite at 24 scenarios across all five MVP loops. Mutation:
  moderation 85% (threshold 85). `./scripts/check.sh` passes in 4m05s. Ready for independent review.

- 2026-07-29: Chunk 6 fast-forward merged to `main` at `d9184dd`.
- 2026-07-29: Chunk 7 implemented on `feat/009-moderation-chunk7-admin-queue`, scenarios first.
  `GET /api/admin/moderation/cases`, `GET .../{id}`, `POST .../{id}/assign` and
  `POST .../{id}/decide` complete MVP loop 5: an operator can now work the queue entirely over HTTP,
  with no database access.

  The queue carries a **concern count, not reporters**. One account can raise at most one live
  report per target, so the count already answers the question a moderator has — one complaint or
  twenty — and identities would add nothing while inviting decisions based on who complained. The
  case detail carries each concern's category, description and timestamp, which is the substance to
  judge; a scenario asserts no account id of anyone the scenario introduced appears in the response.

  `decide` claims the case in the same call when nobody holds it. The accountability rule is that a
  decision names a moderator, not that they clicked twice to get there. `claim` deliberately does
  not displace an existing assignee: the case keeps saying who owns it while the decision records
  who actually made it.

  Loop 5's feature file grew from 4 to 10 scenarios; the suite is now 30. Mutation: moderation 86%
  (threshold 85). `./scripts/check.sh` passes in 4m05s. Ready for independent review.

- 2026-07-29: Chunk 7 fast-forward merged to `main` at `0c3a740`.
- 2026-07-30: Chunk 8 split into 8a and 8b. Designing the appeal endpoints surfaced a blocker worth
  a founder decision: `REJECTED` and `REMOVED` are terminal and `restore` only works from `HIDDEN`,
  so an overturned appeal could record an outcome but never give the content back. An appeals
  process that cannot return the content is a hollow remedy — a takedown demand that succeeds and
  then loses on appeal would still get exactly what it wanted. Recorded as `P-014`: reinstatement
  through an appeal-only audited path.
- 2026-07-30: Chunk 8a implemented on `feat/009-moderation-chunk8a-reinstate`, test-first. `V4.5`
  widens the `review_moderation_audit_event` action CHECK to include `REINSTATE`, verified
  statement-by-statement on scratch Postgres before any test — including that an unknown action is
  still rejected, so widening the vocabulary did not turn the column into free text.
  `Review.reinstate` refuses anything not terminal, so it can never stand in for an ordinary publish;
  `ReviewModerationService.reinstate` audits it like any other action; the published
  `ReviewModerationEffect` gains `REINSTATE`. `Review`'s own javadoc and `DOMAIN_MODEL.md` now say
  terminal means terminal except by appeal, since leaving them claiming otherwise would be the
  documentation lying about an invariant. 6 new domain tests, 2 gateway tests, 1 migration test.
  Mutation: reviews 90% (threshold 85). `./scripts/check.sh` passes. Ready for independent review.

- 2026-07-30: Chunk 8a fast-forward merged to `main` at `8bf2413`.
- 2026-07-30: Chunk 8b implemented on `feat/009-moderation-chunk8b-appeals`, scenarios first.
  Appeal persistence, `AppealService`, `POST /api/appeals`, the appellant's own view, and the admin
  appeals queue and decision. An appellant names the content rather than a decision id: an author
  knows their review was taken down and should not need an internal identifier to say so.

  Overturning reverses the decision through the port's new `reverse` operation — a withheld review
  is restored, a rejected or removed one reinstated through the appeal-only door from 8a. The
  reversal reads the review's *current* version rather than the one the decision recorded, because
  the takedown itself moved it.

  The collision flagged in 8a is handled rather than left: if the owning module refuses to take the
  content back — most likely because the author published a replacement and the one-live-review rule
  will not hold two — the appeal is **not** marked overturned. The moderator is told and can uphold
  with an explanation instead, because recording "overturned" over content that is still gone would
  be the audit trail asserting something untrue.

  The appellant's view carries neither moderator's identity; the admin view names the original
  decider, because whoever picks the appeal up needs to know it is not them. 6 new scenarios (36
  total), 10 service tests. Mutation: moderation 85% (threshold 85). `./scripts/check.sh` passes.
  Ready for independent review.

## Final outcome

Complete at 8 chunks (chunk 8 delivered as 8a and 8b), pending independent review of the last two.

MVP loop 4 — report, dispute, resolve — works end to end: a resident reports a published review, the
concerns converge on one case, a moderator decides it with a reason code and a user-facing
explanation, the effect is applied to the content, and the author can appeal to a different
moderator who can put the review back. MVP loop 5 works too: an operator runs the whole thing over
HTTP without database access.

Deliberately not built, and not blocking: right of reply and representative claims (plan 010 in the
original numbering — now a future plan); a `MODERATOR` role distinct from `ADMIN`; automated
classifiers; IP/device abuse metadata; rate limiting on reporting; account restrictions as a
mechanism; notification emails for outcomes. Queue paging, SLA reporting and exclusive case
assignment are named in the chunk-7 handoff as the first things a real operator will ask for.

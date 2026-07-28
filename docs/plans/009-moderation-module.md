# Moderation Module: Reports, Cases, Decisions and Appeals

Status: Active
Owner: Claude
Related issue: none
Last updated: 2026-07-28

## Objective

Give the platform an auditable case workflow for contested content: a report becomes a case, a case
receives a reason-coded immutable decision under a recorded policy version, and a decision can be
appealed once to a different decider. This closes MVP loop 4, "Report/dispute → resolve safely", and
ROADMAP Phase 3's last open item.

## Acceptance criteria

- [ ] The `moderation` schema owns reports, cases, decisions and appeals in the `V6.x` namespace,
      with no foreign key into another module's tables; many reports about one target converge on a
      single live case, and one account cannot report the same content repeatedly.
- [ ] A signed-in account can report a review it can see; a reporter reads only their own report's
      status and never the case, another reporter, or a decision's internal note.
- [ ] Decisions are append-only and carry action, reason code, policy version, the judged content
      version, a user-visible explanation for anything adverse, and an internal note that never
      reaches a user.
- [ ] A decision's effect on a review is applied through the reviews module's published contract
      with its stale-version check; moderation never writes reviews' tables.
- [ ] Exactly one appeal per decision, decided by an account other than the original decider, with
      the outcome and explanation recorded; negative paths have explicit tests.
- [ ] An owner or developer takedown demand travels the same audited workflow as any other report.

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
8. **Appeals**: author submission and admin appeal decision with the different-decider rule.

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

## Final outcome

Not yet complete.

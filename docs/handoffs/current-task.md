# Task handoff

## Objective

Implement plan 009, chunk 8b: appeals — author submission, the appellant's own view, the admin
appeals queue and decision, with an overturn that actually puts the content back. Last chunk of the
plan.

## Active branch

`feat/009-moderation-chunk8b-appeals`, branched from clean `main` at `8bf2413`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/009-moderation-module.md`, chunk 8b of 8 — the plan is now **Complete**.

## Current status

completed, awaiting independent review

## Completed work

- 6 appeal scenarios in `report-and-dispute.feature`, written first and watched fail.
- `AppealRepository` port, JPA entity/mapper/adapter; `ModerationDecisionRepository.findById`.
- `ModerationEffectApplier.reverse` and its reviews-side implementation.
- `AppealService`: file, findOwn, pending, uphold, overturn.
- `POST /api/appeals`, `GET /api/appeals/{id}`, `GET /api/admin/moderation/appeals`,
  `POST /api/admin/moderation/appeals/{id}/decide`, plus four records and five error mappings.
- `AppealServiceTest` (10 tests), `InMemoryAppealRepository`, `reverse` on the effect fake.

## Remaining work

None for plan 009. Right of reply and representative claims remain a future plan (`P-013`).

## Decisions made

- **An appellant names the content, not a decision id.** An author knows their review was taken
  down; handing them an internal identifier to quote back would be a worse interface and would leak
  case structure.
- **The reversal reads the review's current version, not the recorded one.** The takedown itself
  moved the version, so trusting `affectedTargetVersion` would fail every reversal as stale.
- **A refused reinstatement leaves the appeal PENDING.** This is the collision flagged in 8a: if the
  author published a replacement, the one-live-review rule will not hold two and the owning module
  refuses. Marking the appeal "overturned" over content that is still gone would make the audit
  trail assert something untrue, so the moderator is told and can uphold with an explanation.
- **The decider conflict is checked before the effect.** Reinstating content cannot be undone by
  throwing afterwards.
- **The appellant's view names neither moderator; the admin view names the original decider**,
  because whoever picks the appeal up needs to know it is not them.
- **`APPEAL_DECIDER_CONFLICT` is 403, not 400.** Due process, not a malformed request.

## Assumptions

- The appealable decision is the latest one on the target's live case whose action took something
  away. An approval is not appealable by its beneficiary.

## Files changed

- `app/src/test/resources/features/report-and-dispute.feature` (8 -> 14 scenarios)
- new `app/src/test/.../acceptance/AppealSteps.java`; `ScenarioState`, `ModerationSteps`
- 6 new files in `modules/moderation/.../application/`; `ModerationEffectApplier`,
  `ModerationDecisionRepository`
- 3 new files in `modules/moderation/.../infrastructure/persistence/`; `ModerationJpaMapper`,
  `JpaModerationDecisionRepository`
- 6 new files in `modules/moderation/.../infrastructure/web/`; `ModerationExceptionHandler`
- `ReviewsModerationEffectApplier`, `ModerationBeanConfiguration`
- new `modules/moderation/src/test/.../application/AppealServiceTest.java`,
  `InMemoryAppealRepository.java`; `InMemoryModerationEffectApplier`,
  `InMemoryModerationDecisionRepository`
- `docs/plans/009-moderation-module.md` (closed), `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :app:test --tests \'*AcceptanceTest\'` (red first, then green)
- `cd apps/api && ./gradlew :modules:moderation:test -PskipMutation`
- `cd apps/api && ./gradlew :modules:moderation:mutationTest --rerun-tasks`
- `./scripts/check.sh` -> `EXIT=0`, 4m 11s

## Tests and verification

All passed on 2026-07-30. Acceptance suite: **36 scenarios**, 0 failures, 0 skipped. Mutation:
moderation 85% (threshold 85).

Loop 4 now works end to end including the dispute half: a resident reports a published review, a
moderator removes it with a reason and an explanation, the author appeals, and a *different*
moderator overturning it puts the review back in the public listing — asserted by reading the
listing, not by trusting a status field.

## Known failures

None observed.

## Risks and unresolved questions

- **Moderation is exactly on its mutation threshold again (85 vs 85).** The next chunk touching it
  must add assertions before it can merge.
- The appeal resolves against the *live* case for the target. A case closed after its decision would
  leave nothing to appeal; nothing closes cases automatically today, so this is latent rather than
  live, but chunk-7's note about case lifecycle applies here too.
- An overturned appeal republishes without re-moderation, bypassing the `P-005` pre-moderation
  default. Deliberate — the appeal already decided the takedown was wrong — but worth confirming.
- Nothing notifies an author that a decision was made or an appeal resolved. They have to look. The
  notifications module is where that belongs.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge — after which plan 009 is closed.
The next task is a new plan; the open MVP gaps are the search module (loop 1's discovery half) and
the admin web app, with right of reply and representative claims as the moderation follow-on.

## Last updated

2026-07-30

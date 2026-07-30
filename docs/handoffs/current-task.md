# Task handoff

## Objective

Implement plan 009, chunk 8a: give reviews an appeal-only way back from a terminal decision, so that
chunk 8b's appeals can actually deliver a remedy.

## Active branch

`feat/009-moderation-chunk8a-reinstate`, branched from clean `main` at `0c3a740`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/009-moderation-module.md`, chunk 8a of 8 (chunk 8 was split — see below).

## Current status

completed, awaiting independent review

## Completed work

- `V4.5__allow_reinstate_moderation_action.sql` widening the audited action CHECK.
- `Review.reinstate`, `ReviewModerationAction.REINSTATE`, `ReviewModerationService.reinstate`.
- `ReviewModerationEffect.REINSTATE` on the published contract, handled by the gateway adapter.
- `DECISION_LOG` `P-014`; `DOMAIN_MODEL.md` and `Review`'s javadoc corrected.
- 6 domain tests, 2 gateway tests, 1 migration test.

## Remaining work

Chunk 8b: appeal persistence, `POST /api/appeals`, the appellant's own view, the admin appeal queue
and decision, with overturn wired to `REINSTATE`. That is the last chunk of plan 009.

## Decisions made

- **Chunk 8 was split.** Reinstatement is a reviews-module capability with its own migration and its
  own invariant change; appeals are a moderation feature. Shipping them as one branch would have put
  a domain-invariant change and a feature behind a single review.
- **`P-014`: terminal means terminal except by appeal.** An appeals process that cannot return the
  content is a hollow remedy, and a takedown that survives a successful appeal is a takedown that
  worked. Founder decision, 2026-07-30.
- **The door is deliberately narrow.** `reinstate` refuses anything not terminal, so it cannot stand
  in for an ordinary publish or restore; the only caller will be the appeal path, and every use is
  audited with its moderator and reason code.
- **The docs were corrected, not just extended.** `Review`'s javadoc said "a terminal review rejects
  all further mutation" and `DOMAIN_MODEL.md` implied the same. Leaving them would be documentation
  lying about an invariant.

## Assumptions

- Reinstating sets `publishedAt` only when it was never set, so a review rejected before its first
  publication gets a publication time while a removed-then-restored one keeps its original.

## Files changed

- new `modules/reviews/src/main/resources/db/migration/reviews/V4.5__allow_reinstate_moderation_action.sql`
- `Review`, `ReviewModerationAction`, `ReviewModerationService`, `ReviewModerationEffect`,
  `ReviewModerationGatewayAdapter`
- `modules/reviews/src/test/.../domain/ReviewTest.java`,
  `.../application/ReviewModerationGatewayAdapterTest.java`
- `app/src/test/.../reviews/ReviewsMigrationIntegrationTest.java`
- `docs/DECISION_LOG.md`, `docs/DOMAIN_MODEL.md`, `docs/plans/009-moderation-module.md`,
  `docs/handoffs/current-task.md`

## Commands run

- scratch Postgres: V4.1-V4.3 + V4.5 applied, `REINSTATE` accepted and `UNDELETE` rejected
- `cd apps/api && ./gradlew :modules:reviews:test -PskipMutation` (red on the new tests, then green)
- `cd apps/api && ./gradlew :app:test --tests \'*ReviewsMigrationIntegrationTest\'`
- `cd apps/api && ./gradlew :modules:reviews:mutationTest --rerun-tasks`
- `./scripts/check.sh` -> `EXIT=0`, 4m 12s

## Tests and verification

All passed on 2026-07-30. Mutation: reviews 90% (threshold 85).

The migration was verified on scratch Postgres before any test was written, and the probe checks
both directions: `REINSTATE` is accepted, and an unknown action is still rejected by the constraint,
so widening the vocabulary did not turn the column into a free-text field. A first attempt at that
probe omitted the `id` column and failed on a null violation rather than the CHECK — it proved
nothing and was redone.

The domain tests were written before `reinstate` existed and failed to compile, which is the red
phase for a new method.

## Known failures

None observed.

## Risks and unresolved questions

- Nothing yet calls `reinstate`. It is dead code until chunk 8b, which is the argument for reviewing
  the two chunks close together even though they merge separately.
- `reinstate` returns content to `PUBLISHED` without re-moderating it. That is the point — the appeal
  already decided the takedown was wrong — but it does mean an overturn bypasses the pre-moderation
  default in `P-005`.
- The one-live-review index excludes terminal states, so reinstating a removed review could in
  principle collide with a fresh review the author started in the meantime. Chunk 8b should decide
  what happens then; the database would refuse the reinstatement.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge. When requested, start plan 009
chunk 8b (appeals) from `main`.

## Last updated

2026-07-30

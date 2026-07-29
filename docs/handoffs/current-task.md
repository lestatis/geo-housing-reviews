# Task handoff

## Objective

Implement plan 009, chunk 4: give moderation a way to reach the content it moderates — a published
`reviews.api` gateway and the moderation-side adapters — and make a decision actually take effect.

## Active branch

`feat/009-moderation-chunk4-reviews-gateway`, branched from clean `main` at `84b58ff`. Local only;
not pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/009-moderation-module.md`, chunk 4 of 8.

## Current status

completed, awaiting independent review

## Completed work

- `reviews.api` gained `ModeratableReview`, `ReviewModerationEffect`,
  `ReviewModerationConflictException` and `ReviewModerationGateway`.
- `ReviewModerationGatewayAdapter` in reviews, wired in `ReviewsBeanConfiguration`.
- Moderation gained `implementation(project(":modules:reviews"))`, the `ModerationEffectApplier`
  port, `ModerationEffectConflictException`, and two `@Component` adapters under
  `moderation.infrastructure.reviews`.
- `ModerationCaseService.decide` reordered to apply-then-record; `ModerationCase.requireDecidable()`
  added.
- 22 new tests plus 4 new case-service tests.

## Remaining work

Chunks 5–8. Chunk 5 (persistence adapters) is next and is what finally lets the whole report → case
→ decision flow run end to end.

## Decisions made

- **The contract carries no review content.** Moderation needs the author and the version, nothing
  more. A moderator who must read the review uses reviews' own audited admin endpoint; copying the
  text out would put the same sensitive material in a second module under a second set of access
  rules. A test pins the record to exactly four components.
- **Apply the effect before recording the decision** (founder, 2026-07-29). If recording fails
  afterwards, the content is correctly withheld and reviews' own audit row — written atomically with
  its mutation — already carries the action and reason; the case stays `IN_REVIEW` and is retryable.
  Recording first risks an audit trail asserting a review was removed while it is still publicly
  visible, and an appeal referencing a decision that never took effect.
- **`requireDecidable()` added to the domain** so the accountability check runs *before* the
  irreversible part. An effect cannot be undone by throwing afterwards.
- **Two ports, not one.** Reading about content and changing it are different authorities, and a
  future target type may support one without the other.
- **The action → effect mapping lives in the adapter.** Whether an action even has a content effect
  is a property of the target's module. Four actions map to nothing:
  `APPROVE_WITH_REDACTION`/`REQUEST_CHANGES` need a content-editing path that does not exist,
  `RESTRICT_ACCOUNT` is identity's, and `ESCALATE` has decided nothing. Each is still a recorded
  decision.
- **The adapters refuse a non-`REVIEW` target rather than returning empty**, because a silent empty
  would read as "content does not exist" and quietly drop real reports.

## Assumptions

- `judgedVersion` is null only when the target lookup finds nothing; `decide` then records the
  decision without applying an effect. That path is reachable if content disappears between intake
  and decision, and is worth a second look in chunk 7 when the admin endpoints exist.

## Files changed

- 4 new files in `modules/reviews/.../api/`, 1 new adapter + `ReviewsBeanConfiguration`
- new `modules/reviews/src/test/.../ReviewModerationGatewayAdapterTest.java`
- `modules/moderation/build.gradle.kts`; 2 new files in `moderation/application/`; 2 new adapters in
  `moderation/infrastructure/reviews/`
- `ModerationCaseService`, `ModerationCase`, and their tests; new
  `InMemoryModerationEffectApplier`, `ReviewsModerationAdapterTest`
- new `app/src/test/.../moderation/ModerationReviewGatewayIntegrationTest.java`
- `docs/plans/009-moderation-module.md`, `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :modules:reviews:test :modules:moderation:test -PskipMutation`
- `cd apps/api && ./gradlew :app:test --tests '*ModerationReviewGatewayIntegrationTest'`
- `cd apps/api && ./gradlew :app:test --tests 'com.example.geohousing.app.architecture.*'`
- `cd apps/api && ./gradlew :modules:reviews:mutationTest :modules:moderation:mutationTest --rerun-tasks`
- `./scripts/check.sh`

## Tests and verification

All passed on 2026-07-29. 9 reviews-adapter tests, 8 moderation-adapter tests, 15 case-service tests
(4 new), and 5 app integration tests. Mutation: reviews 91% (threshold 85), moderation 88% (85).

**The api-only boundary rule was proven non-vacuous for the new dependency**: importing a reviews
*internal* type (`ReviewStatus`) into a moderation adapter failed
`ModuleBoundaryArchitectureTest > modules_should_only_be_reached_through_their_api_package`, then
was reverted and the rule went green again. Until this chunk the rule had nothing to catch for
moderation, since the module had no cross-module dependency at all.

The app integration test proves the edge in a running context: a published review is removed through
the moderation port and genuinely disappears from the public listing, a stale version is refused,
and reviews' own audit row is written for the effect.

## Known failures

None observed. The IDE repeatedly reported unresolved `com.example.geohousing.reviews` imports in
moderation sources — a stale IDE classpath after the module gained the dependency; Gradle compiles
and runs them.

## Risks and unresolved questions

- **Scope note, stated plainly**: the plan said this chunk would prove "a report opens a case, a
  moderator decides REMOVE, and the review disappears". The report → case half cannot run in a
  Spring context yet because moderation has no repository implementations until chunk 5, so the app
  test drives the moderation *ports* instead. The cross-module edge is genuinely proven; the
  full-flow proof moves to chunk 5.
- The apply-then-record window is real and accepted. If moderation's write fails, reviews' audit
  holds the action and reason but the user-facing explanation is lost until the moderator retries.
- `ModerationCaseService` now has seven constructor parameters. Chunk 7 should consider whether the
  decision path wants its own smaller collaborator.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge. When requested, start plan 009
chunk 5 (persistence adapters and their integration tests) from `main`.

## Last updated

2026-07-29

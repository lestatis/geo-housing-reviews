# Task handoff

## Objective

Implement plan 008, chunk 4: the HTTP contract for helpful signals — add/withdraw endpoints and a
public-safe aggregate count on review representations, with authorization and privacy tests. Ranking
behaviour is excluded.

## Active branch

`feat/008-review-helpful-signals-http`, branched from clean `main` at `803a664`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/008-review-helpful-signals.md`, chunk 4 of 5.

## Current status

completed, awaiting independent review

## Completed work

- `POST /api/reviews/{reviewId}/helpful` and `DELETE /api/reviews/{reviewId}/helpful` add and
  withdraw the caller's signal, answering `200` with the review's new aggregate.
- `HelpfulSignalResponse` is `{reviewId, helpfulCount}` only. It does not echo whether the caller has
  an active signal, so no endpoint on this path discloses who signalled what.
- `helpfulCount` added to `ReviewResponse` and through `ReviewListResponse` to the public listing.
- Batch count added to `HelpfulSignalRepository`, the Spring Data repository (grouped query, no
  voter column selected), the JPA adapter, and the in-memory fake, so a listing page costs one query
  instead of one per review.
- `HelpfulSignalQueryService.activeCountsForVisibleReviews` decorates already-authorised reviews;
  `countForPublishedReview` remains the guarded single-id entry point.
- `WebAuthentication.helpfulSignalVoterId` resolves the caller as a voter (a distinct type from
  `AuthorId`).
- RFC 7807 mappings: `SelfHelpfulSignalException` → `403 HELPFUL_SIGNAL_SELF_NOT_ALLOWED`,
  `HelpfulSignalAlreadyActiveException` → `409 HELPFUL_SIGNAL_ALREADY_ACTIVE`.
- `AdminReviewController` supplies the count for the moderation representations.
- Added `HelpfulSignalEndpointIntegrationTest` (9 tests, Testcontainers Postgres + stub
  `JwtDecoder`).

## Remaining work

None for chunk 4. Chunk 5 (versioned bounded ranking input) remains and needs a new branch from
`main` after this one is reviewed and merged.

## Decisions made

- `ReviewResponse.from(Review)` (count-defaulting overload) was **removed**. Every construction site
  must pass a count, so a forgotten path fails to compile rather than reporting a review with
  signals as having none.
- Self-signal is `403`, not `404`: the review is published and the caller can already see it, so
  explaining the refusal discloses nothing.
- An unpublished target is `404 REVIEW_NOT_FOUND` on both POST and DELETE, so the endpoint cannot be
  used to probe the moderation queue.
- `activeCountsForVisibleReviews` performs no visibility check of its own; this is documented on the
  method as post-authorization-only, because the reviews it decorates were resolved through
  `ReviewQueryService`.
- No security-configuration change: `GET /api/reviews/**` is already anonymous-permitted and the new
  POST/DELETE fall through to `anyRequest().authenticated()`.

## Assumptions

- A client that needs to render its own toggle state will read it from its own action result or a
  future explicit endpoint; not echoing it here is deliberate, not an oversight.

## Files changed

- `modules/reviews/.../application/HelpfulSignalRepository.java`,
  `HelpfulSignalQueryService.java`
- `modules/reviews/.../infrastructure/persistence/SpringDataHelpfulSignalRepository.java`,
  `JpaHelpfulSignalRepository.java`
- `modules/reviews/.../infrastructure/web/ReviewController.java`, `ReviewResponse.java`,
  `ReviewListResponse.java`, `ReviewsExceptionHandler.java`, `WebAuthentication.java`,
  `AdminReviewController.java`, new `HelpfulSignalResponse.java`
- `modules/reviews/src/test/.../application/InMemoryHelpfulSignalRepository.java`
- new `app/src/test/java/com/example/geohousing/app/reviews/HelpfulSignalEndpointIntegrationTest.java`
- `docs/plans/008-review-helpful-signals.md`, `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :app:test --tests 'com.example.geohousing.app.reviews.HelpfulSignalEndpointIntegrationTest'`
- `cd apps/api && ./gradlew :modules:reviews:check`
- `cd apps/api && ./gradlew :app:spotlessApply`
- `./scripts/check.sh`

## Tests and verification

All passed on 2026-07-28. The endpoint test (9 tests, 0 failures) covers: anonymous POST/DELETE →
`401` while the anonymous count read succeeds; add → count 1 and withdraw → count 0; duplicate add →
`409` without inflating the aggregate; author self-signal and self-withdraw → `403`; unpublished and
unknown targets → `404`; a malformed id → `400`; withdraw with nothing active → `200`; and a listing
where one review shows 3 and its neighbour 0. The privacy test asserts the voter's account id and
the string `voter` appear in no signal, review, or listing body, while confirming the active row
exists in the database.

## Known failures

None observed.

## Risks and unresolved questions

- Coordinated voting remains possible; rate limits and stronger abuse signals are separate
  policy/privacy work.
- The listing count is one extra grouped query per page. It is unbounded only by page size, so it
  scales with the cursor limit rather than the property's review total.

## Human actions required

None.

## Recommended next action

Independent review of the chunk-4 branch in a fresh session, then merge. When requested, start plan
008 chunk 5 from `main`.

## Last updated

2026-07-28

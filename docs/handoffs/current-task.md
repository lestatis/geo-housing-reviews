# Task handoff

## Objective

Implement plan 008, chunk 3: persist helpful signals through JPA and provide a public-safe,
active-count query projection. HTTP endpoints and ranking behaviour are excluded.

## Active branch

`feat/008-review-helpful-signals-persistence` (branched from clean `main` at `a276cad`)

## Related issue or plan

No issue. `docs/plans/008-review-helpful-signals.md`, chunk 3 of 5.

## Current status

ready_for_review

## Completed work

- Inspected clean Git state, plan/handoff, accepted domain/architecture/ADR constraints, existing
  reviews JPA patterns, and persistence integration tests.
- Added JPA entity, mapper, Spring Data repository, and adapter for `reviews.review_helpful_signal`.
- The adapter flushes writes and translates only PostgreSQL constraint
  `review_helpful_signal_one_active_voter_idx` into `HelpfulSignalAlreadyActiveException`; unrelated
  integrity failures are rethrown.
- Added active-row count to the port and `HelpfulSignalQueryService`/`HelpfulSignalCount`, which
  returns no voter details and treats an unpublished review as not found.
- Wired helpful-signal command/query services in `ReviewsBeanConfiguration`.
- Added a Testcontainers Postgres integration test for persistence, withdrawal, count projection,
  and simultaneous duplicate creation.

## Remaining work

No implementation work remains for chunk 3. A fresh independent read-only review is required before
human merge. Plan 008 chunks 4–5 remain future work and must start only after this branch is merged.

## Decisions made

- Active count is queried from `withdrawn_at IS NULL` rows, not stored/mutated as a second counter;
  this avoids aggregate drift under concurrent writes.
- Withdrawal updates the existing row and is harmless if a racing request already withdrew it.
- The adapter owns database-exception translation; the application service stays framework-free.

## Assumptions

- The future HTTP controller will resolve public review visibility before exposing the count; the
  application query service enforces the same published-only invariant as a second boundary.

## Files changed

- helpful-signal count/query application types and extended persistence port
- reviews JPA entity, mapper, Spring Data repository, adapter, and bean configuration
- in-memory fake and query-service unit test
- `apps/api/app/src/test/java/com/example/geohousing/app/reviews/HelpfulSignalPersistenceIntegrationTest.java`
- `docs/plans/008-review-helpful-signals.md`
- `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :app:test --tests 'com.example.geohousing.app.reviews.HelpfulSignalPersistenceIntegrationTest'`
- `cd apps/api && ./gradlew :modules:reviews:test --tests 'com.example.geohousing.reviews.application.HelpfulSignalServiceTest' --tests 'com.example.geohousing.reviews.application.HelpfulSignalQueryServiceTest' --tests 'com.example.geohousing.reviews.domain.HelpfulSignalTest'`
- `cd apps/api && ./gradlew :modules:reviews:spotlessApply :app:spotlessApply`
- `cd apps/api && ./gradlew :modules:reviews:check`
- `./scripts/check.sh`

## Tests and verification

All commands above passed on 2026-07-27. `HelpfulSignalPersistenceIntegrationTest` uses real
Postgres and proves the adapter mapping, active-only count, withdrawal, and a two-thread race: one
insert succeeds, one is translated to `HelpfulSignalAlreadyActiveException`, and one active row
remains.

## Known failures

None observed.

## Risks and unresolved questions

- The count is correct transactionally but coordinated voting remains possible; rate limits and
  stronger abuse signals require separate policy/privacy work.
- Endpoint response semantics and whether a client can read its own current signal remain chunk 4
  contract decisions; this branch exposes no HTTP surface.

## Human actions required

None.

## Recommended next action

Run a fresh independent read-only review of the complete branch diff. If merged by a human, mark this
handoff completed and start plan 008 chunk 4 from updated `main`.

## Last updated

2026-07-27

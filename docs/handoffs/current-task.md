# Task handoff

## Objective

Implement plan 008, chunk 2: a framework-free helpful-signal lifecycle and application eligibility
service. Persistence adapters, HTTP endpoints, aggregate counts, and ranking behaviour are excluded.

## Active branch

`main` (chunk 2 fast-forward merged at `e2665b4`)

## Related issue or plan

No issue. `docs/plans/008-review-helpful-signals.md`, chunk 2 of 5.

## Current status

completed

## Completed work

- Inspected the plan/handoff, clean Git state, reviews domain/application/test conventions,
  `DOMAIN_MODEL.md`, `ARCHITECTURE.md`, and ADR-0003.
- Added `HelpfulSignal`, opaque signal and voter identifiers, and lifecycle validation (active until
  withdrawal; no withdrawal before creation or twice).
- Added `HelpfulSignalRepository` and `HelpfulSignalService` application ports/use cases.
- Enforced application eligibility: target must be published; unpublished targets return
  `ReviewNotFoundException` to prevent state probing; the author cannot signal their own review;
  duplicate active signals conflict; withdrawal is idempotent.
- Added in-memory persistence fake plus focused domain and application tests.

## Remaining work

No implementation work remains for chunk 2. Plan 008 chunks 3–5 remain future work and require a
new task branch from `main`.

## Decisions made

- `HelpfulSignalVoterId` is distinct from `AuthorId`: both are opaque identity references, but their
  roles and invariants differ.
- A missing active signal makes withdrawal a no-op, suitable for a future idempotent DELETE endpoint.
- Signal eligibility reads the review aggregate but does not mutate it; helpful signals remain their
  own persistence lifecycle.

## Assumptions

- A voter cannot have an active signal on a review that later becomes unpublished; the future
  persistence/query slice will exclude inactive signals and public representations cannot expose an
  unpublished review. Moderation-specific retention policy is not part of this chunk.

## Files changed

- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/domain/HelpfulSignal.java`
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/domain/HelpfulSignalId.java`
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/domain/HelpfulSignalVoterId.java`
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/application/HelpfulSignalService.java`
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/application/HelpfulSignalRepository.java`
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/application/HelpfulSignalAlreadyActiveException.java`
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/application/SelfHelpfulSignalException.java`
- focused domain/application tests and in-memory fake
- `docs/plans/008-review-helpful-signals.md`
- `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :modules:reviews:test --tests 'com.example.geohousing.reviews.domain.HelpfulSignalTest' --tests 'com.example.geohousing.reviews.application.HelpfulSignalServiceTest'`
- `cd apps/api && ./gradlew :modules:reviews:spotlessApply`
- `cd apps/api && ./gradlew :modules:reviews:check`
- `./scripts/check.sh`

## Tests and verification

All commands above passed on 2026-07-27. The initial focused-test run failed only because an
in-memory repository reconstitutes values and the test asserted object identity; the assertion was
corrected to compare stable signal fields before the succeeding focused and full checks.

## Known failures

None remaining.

## Risks and unresolved questions

- The database partial unique index remains the final concurrency backstop; chunk 3 must translate
  its race into the application conflict and prove it against Postgres.
- No rate limiting or coordinated-campaign detection is introduced; these need a later privacy and
  policy decision.

## Human actions required

None.

## Recommended next action

No action remains for this chunk. When requested, start plan 008 chunk 3 from `main`.

## Last updated

2026-07-27

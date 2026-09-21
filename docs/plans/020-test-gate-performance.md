# Plan 020 — reuse PostgreSQL integration-test infrastructure

Status: Draft
Owner: Human
Related issue: None
Last updated: 2026-09-21

## Objective

Reduce cold integration-test wall-clock time by reusing PostgreSQL test infrastructure across test
classes without weakening database isolation, endpoint coverage, or mutation-testing thresholds.

## Acceptance criteria

- [ ] Record a reproducible cold baseline using `./gradlew check --rerun-tasks --no-build-cache`.
- [ ] Replace per-class PostgreSQL startup with a shared JVM/test-harness lifecycle where the test
  architecture permits it.
- [ ] Isolate each test class through schema/database reset or equivalent deterministic cleanup;
  no test may depend on another class's data or execution order.
- [ ] Demonstrate that many integration classes do not cause the same number of PostgreSQL starts.
- [ ] Record before/after timings and the test-count/container-start evidence in this plan or its
  handoff.
- [ ] Keep PITest thresholds and the full endpoint/Cucumber suites enabled; `./gradlew check` passes.

## Non-goals

- Removing PITest, lowering a mutation threshold, or skipping integration/Cucumber tests.
- Broadly rewriting passing endpoint tests solely to change their test framework.
- Changing production PostgreSQL configuration or module boundaries.

## Current system

ADR-0009 records that nearly every `:app` integration test class starts a PostgreSQL Testcontainer.
It also records a previous cold measurement of 1m45s on 2026-07-29 after parallel-fork work. The
current baseline must be measured again on the target CI/local environment before comparing a
change. Relevant sources: `docs/adr/0009-bdd-and-mutation-testing.md`, `CONTRIBUTING.md`, and the
integration-test harness under `apps/api/app/src/test`.

## Decisions

| Decision | Choice | Reason | Revisit when |
| --- | --- | --- | --- |
| Performance metric | Cold `check` with rerun tasks and no build cache | Avoids reporting an up-to-date build as a speed improvement | Build topology changes |
| Quality boundary | Preserve PITest and endpoint suites | The bottleneck is container lifecycle, not the quality gate | Evidence shows another dominant cost |
| Isolation | Decide from a spike, document the selected reset mechanism | Shared lifecycle is safe only with deterministic data isolation | The spike exposes cross-test leakage |

## Implementation steps

1. Measure and retain the cold baseline:

   ```bash
   cd apps/api
   time ./gradlew check --rerun-tasks --no-build-cache
   ```

2. Inventory container lifecycle declarations and test data cleanup in `:app` integration tests.
3. Build a small shared-container spike, including deliberately order-sensitive tests to validate
   cleanup behavior.
4. Select and document the isolation mechanism, then migrate the harness incrementally.
5. Re-run the cold benchmark and full suite; compare elapsed time, container starts, failures, and
   test count with the baseline.
6. Submit the implementation as a dedicated `perf(test)` change with its own handoff and review.

## Verification

- The baseline and final command use `--rerun-tasks --no-build-cache`.
- Run focused integration classes repeatedly and in changed order where Gradle permits.
- Run `./gradlew check` without `-PskipMutation` before review.
- Inspect failures for leaked schema/data and compare test count with the baseline.

## Risks and rollback/forward-fix

Shared state can create order-dependent or flaky tests, and a shared container can make a harness
failure affect more classes at once. Roll back by retaining the existing per-class lifecycle until
the replacement has deterministic cleanup evidence. If the experiment cannot preserve isolation,
stop and record the measurement rather than weakening the test gate.

## Progress log

- 2026-09-21: Drafted as a separate performance task; no test infrastructure has changed.

## Final outcome

Not started.

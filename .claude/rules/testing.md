# Testing rules

- Test observable behavior and invariants, not private implementation details.
- Write the test first for domain and application logic: state the behavior, watch it fail, then
  implement. Test-after is allowed only where a test cannot precede the code (a schema constraint, a
  third-party integration whose shape is unknown until it runs).
- A module's mutation score must not fall below the threshold in its `build.gradle.kts`
  (`mutationTesting { }`, ADR-0009). Raise the threshold when a chunk leaves the score above it;
  never lower it to make a build pass — a threshold that only moves down measures nothing.
- `-PskipMutation` is for local iteration only. Never use it for a pre-review or CI run.
- A mutation score of 0% usually means no test was selected, not that every mutant survived. The
  test glob defaults to the module's Gradle name, which is its package for every module except
  `shared-kernel` (package `shared`) — set `mutationTesting.targetTests` when the two differ. The
  failure is silent: PITest reports a score rather than saying it selected nothing.
- Iterate with a scoped check (`:modules:<module>:test -PskipMutation`, or a single `--tests` class);
  run the full `./scripts/check.sh` once per chunk, before requesting review. Do not defer the gate
  to the end of a module — every chunk merges, so an unverified chunk becomes the next one's
  baseline and a later failure has no bisect point.
- A killed build leaves Testcontainers instances running, because the reaper dies with the JVM.
  Clear them before trusting any timing (see CONTRIBUTING.md "Required checks").
- Read `./scripts/check.sh`'s own exit code. A background-task summary may report its wrapper's
  status, which is not the same thing.
- New endpoint behaviour is expressed as a Gherkin scenario in `app/src/test/resources/features/`
  (ADR-0009). Write the scenario first — it is the failing test. Existing JUnit endpoint classes
  migrate when a chunk next touches that area; do not migrate them as a separate campaign.
- Scenarios state behaviour by meaning, not by status code: "the content is reported as not found"
  carries the privacy rule that a 404 exists to enforce, where "returns 404" hides it.
- Keep schema, persistence, ArchUnit and boot tests as JUnit. Gherkin for a unique-index rejection is
  ceremony that costs more to read than the assertion it replaces.
- Every bug fix adds a regression test when feasible.
- Authorization tests include another user's object and missing/expired role cases.
- Verification tests cover forged/reused/expired/revoked evidence paths.
- Moderation tests cover redaction, appeal, stale-version conflict and audit events.
- Ranking tests assert hard invariants and diversity, not brittle full ordering for every score.
- Migration tests start from a realistic previous schema state.
- Do not replace meaningful integration tests with mocks for database/security behavior.

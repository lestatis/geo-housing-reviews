# Testing rules

- Test observable behavior and invariants, not private implementation details.
- Write the test first for domain and application logic: state the behavior, watch it fail, then
  implement. Test-after is allowed only where a test cannot precede the code (a schema constraint, a
  third-party integration whose shape is unknown until it runs).
- A module's mutation score must not fall below the threshold in its `build.gradle.kts`
  (`mutationTesting { }`, ADR-0009). Raise the threshold when a chunk leaves the score above it;
  never lower it to make a build pass — a threshold that only moves down measures nothing.
- `-PskipMutation` is for local iteration only. Never use it for a pre-review or CI run.
- Every bug fix adds a regression test when feasible.
- Authorization tests include another user's object and missing/expired role cases.
- Verification tests cover forged/reused/expired/revoked evidence paths.
- Moderation tests cover redaction, appeal, stale-version conflict and audit events.
- Ranking tests assert hard invariants and diversity, not brittle full ordering for every score.
- Migration tests start from a realistic previous schema state.
- Do not replace meaningful integration tests with mocks for database/security behavior.

# Testing rules

- Test observable behavior and invariants, not private implementation details.
- Every bug fix adds a regression test when feasible.
- Authorization tests include another user's object and missing/expired role cases.
- Verification tests cover forged/reused/expired/revoked evidence paths.
- Moderation tests cover redaction, appeal, stale-version conflict and audit events.
- Ranking tests assert hard invariants and diversity, not brittle full ordering for every score.
- Migration tests start from a realistic previous schema state.
- Do not replace meaningful integration tests with mocks for database/security behavior.

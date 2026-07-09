---
name: finish-task
description: Close an implementation task with deterministic checks and a compact handoff.
---

# Finish task

1. Inspect `git diff` and verify no unrelated changes or generated secrets are present.
2. Run the narrowest relevant tests, then `./scripts/check.sh`.
3. Confirm migrations/contracts/docs match behavior.
4. Check acceptance criteria one by one.
5. Produce this handoff:

```text
Objective:
Implemented:
Changed files:
Tests/checks:
Known risks or limits:
Decisions needed:
HUMAN_ACTION_REQUIRED:
Suggested reviewer focus:
```

Never claim a check passed unless it was actually run. Include the failing command and concise failure when a check cannot complete.

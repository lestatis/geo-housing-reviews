---
name: finish-task
description: Close an implementation task with deterministic checks and a compact handoff.
---

# Finish task

1. Inspect `git diff` and verify no unrelated changes or generated secrets are present.
2. Confirm L0/L1 scoped checks have passed; run any missing narrow check. Do not rerun the L2
   full gate after every fix.
3. Hand the merge candidate to CI for one L2 full-gate run. If CI is unavailable, run
   `./scripts/check.sh` once and record why local L2 evidence was needed.
4. Confirm migrations/contracts/docs match behavior.
5. Check acceptance criteria one by one.
6. Produce this handoff:

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

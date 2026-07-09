# Claude Code — implementation prompt

```text
Implement issue #<ID> on a dedicated branch/worktree.

Read AGENTS.md first, then only the source-of-truth documents routed for this task. Inspect the current implementation and tests before changing files. Restate acceptance criteria and non-goals. Create/update an execution plan if required by PLANS.md.

Use focused read-only subagents where they add independent value. Keep scope bounded. Add tests alongside the change. Update relevant docs/contracts. Run the narrow checks and ./scripts/check.sh.

Never run or work around sudo/privileged/system-modifying commands. Use HUMAN_ACTION_REQUIRED exactly as defined in AGENTS.md.

Finish with a PR-ready summary: behavior, changed files, checks actually run, risks/limitations, decisions, and suggested Codex review focus.
```

# Claude Code — implementation prompt

```text
Implement issue #<ID> on one dedicated branch. Work one branch at a time unless the user explicitly approves another rollout.

Read AGENTS.md first, then only the source-of-truth documents routed for this task. Inspect the current implementation and tests before changing files. Restate acceptance criteria and non-goals. Create or update an execution plan under docs/plans/ for non-trivial work.

For long or multi-step work, create and maintain an active handoff under docs/handoffs/. Update it at meaningful checkpoints and before intentionally pausing so another Claude Code or Codex session can reconstruct progress from Git and tests. Set its final status accurately.

Use focused read-only subagents where they add independent value. Keep scope bounded. Add tests alongside the change. Update relevant docs/contracts. Run the narrow checks and ./scripts/check.sh.

Never install system packages, global dependencies, or AI/GitHub CLIs. Never run or work around sudo/privileged/system-modifying commands. Use HUMAN_ACTION_REQUIRED exactly as defined in AGENTS.md.

Finish with a PR-ready summary: behavior, changed files, checks actually run, final handoff status, risks/limitations, decisions, and suggested independent-review focus.
```

---
name: task-handoff
description: Create, validate, recover, transfer, and close repository task handoffs across implementation agents and sessions.
---

# Task handoff

Use this skill for an active non-trivial task, an intentional agent switch, or recovery after an interrupted session.

1. Read `AGENTS.md`, the active execution plan, `docs/handoffs/README.md`, and the current handoff when one exists.
2. Inspect the actual branch, `git status`, unstaged and staged diffs, recent commits, modified and untracked files, relevant tests, and affected accepted documentation. Never treat the handoff as the source of truth.
3. Create or update a handoff from `docs/handoffs/TEMPLATE.md`. Record objective, branch, status, completed and remaining work, decisions, assumptions, files, commands, test results, failures, risks, human actions, next action, and update date.
4. For long or multi-step work, update the handoff at meaningful checkpoints and before intentionally pausing or changing agents.
5. When no handoff exists after an interruption, reconstruct only observable state, label every inference, and write a recovered handoff before continuing. Continue only if the next action is unambiguous; otherwise ask one focused blocking question.
6. On takeover, preserve correct work and continue only the remaining scope. Validate handoff claims against Git, code, tests, and accepted documentation; record discrepancies rather than hiding them.
7. Run the relevant checks and record the exact command, result, and useful notes. Never claim an unavailable or failed check passed.
8. Set the handoff to `ready_for_review` when implementation is verified. After human merge, mark it `completed` and archive or remove temporary handoffs according to repository conventions.

This workflow is symmetric: neither Claude Code nor Codex is permanently the implementer or reviewer. It does not authorize commits, pushes, pull requests, merges, privileged commands, global installs, or editing during an independent review.

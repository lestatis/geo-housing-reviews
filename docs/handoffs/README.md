# Task handoffs

Handoffs make the state of a non-trivial task portable between Claude Code, Codex, and a later session of the same tool. They record what the previous agent observed and did so another agent can continue the remaining scope without restarting completed work.

## When a handoff is required

Every active or interrupted non-trivial task needs a handoff. Create it when work begins, update it at meaningful checkpoints during long or multi-step work, and update it before an intentional pause or agent switch. Use [TEMPLATE.md](TEMPLATE.md); `current-task.md` is reserved for a genuinely active task and must not be created speculatively.

A handoff supports, but never replaces, the execution plan under `docs/plans/`. The plan describes intended work and decisions; the handoff records observed progress, evidence, failures, risks, and the next action.

## Transfer and takeover

Claude-to-Codex and Codex-to-Claude transfers use the same process:

1. Stay on the current branch and preserve staged, unstaged, and untracked work.
2. Update the plan and handoff with commands, results, changed files, risks, and the next action.
3. Start the receiving agent at the repository root.
4. The receiving agent independently inspects the branch, status, staged and unstaged diffs, recent commits, plan, handoff, changed files, and relevant tests.
5. Reconcile the handoff with Git, code, accepted documentation, and test evidence. Those sources take priority when they disagree.
6. Continue only the remaining scope and keep the handoff current.

Use `prompts/CODEX_TAKEOVER.md` or `prompts/CLAUDE_TAKEOVER.md` for the receiving agent. A switch does not change the branch or authorize unrelated refactoring.

## Unexpected interruption

If the previous agent stopped without a usable handoff, use `prompts/RECOVER_INTERRUPTED_TASK.md`. Reconstruct state from the repository, write a recovered handoff that labels inferences, and continue only when the next action is unambiguous. Otherwise ask the user one focused blocking question.

## Completion

Set the handoff to `ready_for_review` when implementation and local checks are complete, and to `completed` only when the task is actually complete. After merge, temporary handoffs may be archived or removed according to project conventions; retained handoffs must not masquerade as active state.

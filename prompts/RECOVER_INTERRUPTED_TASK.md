# Recover an interrupted task

```text
The previous implementation session stopped without a usable handoff. Recover repository state before continuing.

Read AGENTS.md and relevant accepted documentation. Inspect the current branch, git status, unstaged diff, staged diff, recent commits, execution plans, all modified and untracked files, relevant tests, and documentation affected by the change. Compare observable work with the plan's acceptance criteria and preserve correct completed work.

Before editing implementation files, write a recovered handoff from docs/handoffs/TEMPLATE.md. Record only observable facts as facts. Label every conclusion not directly supported by Git, code, tests, or accepted documentation as an inference. Include commands and results, known failures, risks, and the recommended next action.

Continue only when the next action is unambiguous and within the existing scope. If intent, ownership of changes, or the safe next action remains materially ambiguous, stop and ask the user one focused blocking question. Do not restart the task, discard changes, install tools, use privileged commands, merge, or publish work.
```

# Independent branch review

```text
Perform a read-only independent review of the complete branch diff against its target branch.

Use a fresh session if the same tool implemented the branch. Read AGENTS.md, the task objective and acceptance criteria, and only relevant accepted documentation. Start from the actual branch diff and surrounding code; do not rely on conclusions, assurances, or informal self-checks from the implementation session as review evidence.

Do not edit files, approve, merge, or start a fix phase. Classify each actionable finding as blocker, high, medium, or low. For every finding include the file and line where possible, the concrete failure or risk, why it matters, a concrete correction, and whether it blocks merge. Avoid style-only noise.

State explicitly when no blocking findings are found. If independent review cannot be performed, report that limitation; do not hide it or replace it with self-review. A separate fix phase starts only when the user explicitly requests it.
```

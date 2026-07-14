# Codex — task takeover

```text
Take over the current implementation task without restarting completed work.

Before editing, read AGENTS.md, the active plan under docs/plans/, the current handoff under docs/handoffs/, and only the relevant accepted product, architecture, domain, API, or security documentation. Inspect the current branch, git status, git diff, git diff --staged, recent commits, modified and untracked files, surrounding implementation, and relevant tests.

Reconstruct actual progress from Git, code, tests, and accepted documentation. Treat the handoff as supporting context and record discrepancies. Preserve correct existing work, accepted architecture, and product decisions. Continue only the remaining acceptance criteria; avoid unrelated refactoring.

Do not install global dependencies, system packages, Claude Code, Codex CLI, or GitHub CLI. Do not use privileged commands or bypass permission failures; use HUMAN_ACTION_REQUIRED as defined in AGENTS.md.

Run the relevant checks. Keep the execution plan and handoff current at meaningful checkpoints, including commands and actual results. Stop at the end of the current branch; do not start the next branch, merge, or publish unless explicitly requested.

Finish with changed files, completed behavior, tests and checks, risks, decisions, remaining work, and the final handoff status.
```

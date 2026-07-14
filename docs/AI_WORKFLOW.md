# AI Development Workflow

## Roles

### Human founders

- own product, legal, privacy, architecture and merge decisions;
- provide credentials and execute privileged/system commands;
- resolve disagreements between agents;
- approve changes to scope, dependencies and public policy.

### ChatGPT

- product/architecture discussion;
- documentation, research and cross-project reasoning;
- preparing tasks, ADRs and review rubrics;
- not the implicit owner of repository state unless files are provided/connected.

### Implementation and review agents

- Claude Code may implement or independently review a branch.
- Codex may implement or independently review a branch.
- Neither tool is permanently restricted to one role; the role is assigned per branch or task.
- Implementation and independent review remain separate phases. When the same tool changes roles, review starts in a fresh independent session.
- Both tools follow `AGENTS.md`, repository plans, handoffs, checks, and accepted documentation.

## Normal implementation flow

```text
Issue
→ execution plan
→ implementation branch
→ local checks
→ handoff summary
→ independent review in a fresh session
→ fixes
→ second review when necessary
→ human merge
```

The assigned implementer inspects relevant sources, keeps the plan and handoff current, implements the smallest bounded change, and records deterministic evidence. The assigned reviewer begins from the complete branch diff, edits nothing, and reports evidence independently. A human owns merge approval.

Cap automated review/fix cycles at two. Persist unresolved disagreements in the PR.

## Agent handoff and takeover

### Claude-to-Codex fallback

1. Stay on the same Git branch.
2. Preserve staged, unstaged, and untracked working-tree changes.
3. Update the execution plan and handoff, or recover the handoff from repository evidence.
4. Start Codex at the repository root.
5. Have Codex inspect the branch, status, diffs, recent commits, plan, handoff, changed files, and relevant tests before editing.
6. Continue only the remaining scope; do not restart completed work.
7. Run the relevant repository checks and record actual results.
8. Stop after the current branch.

### Codex-to-Claude fallback

Use the same sequence in reverse: stay on the branch, preserve every working-tree state, update or recover the handoff, start Claude Code at the repository root, require independent Git/test inspection, continue only remaining scope, run checks, and stop after the current branch.

### Unexpected interruption

When no usable handoff exists, follow `prompts/RECOVER_INTERRUPTED_TASK.md`. Reconstruct progress from branch, status, staged and unstaged diffs, recent commits, plans, modified and untracked files, tests, and affected documentation. Write a recovered handoff before implementation. Mark inferences explicitly and continue only when the next action is unambiguous; otherwise ask one focused blocking question.

The handoff is supporting context. Git, code, tests, and accepted documentation take priority.

## Independent review

- Review requires a fresh session when the same tool implemented the branch.
- Start from the complete branch diff and repository rules, not implementation-session conclusions.
- Review is read-only. Fixes occur only in a separately requested fix phase.
- Do not hide an unavailable review or replace it with an informal self-check.
- Follow `prompts/INDEPENDENT_REVIEW.md`; `prompts/CODEX_REVIEW.md` is a concise Codex-specific entry point, not a permanent role assignment.

## Tool availability

- The repository must remain usable when either Claude Code or Codex is unavailable.
- Agents do not install AI CLIs, GitHub CLI, system packages, or global dependencies. Required unavailable commands use the `HUMAN_ACTION_REQUIRED` format below.
- CI must not invoke or depend on a locally installed AI CLI.
- AI review is an additional quality gate, not a replacement for deterministic tests, CI, or human merge approval.

## Rollout policy

Work one branch at a time by default. Another rollout strategy requires explicit user approval. No agent automatically merges a pull request.

## Token-efficient context rules

- one session/thread per task;
- start with `AGENTS.md` and the routing table, not all docs;
- skills hold repeatable procedures because their body loads only when used;
- path-scoped rules load only for relevant files;
- subagents receive narrow prompts and read-only tools for review;
- use short execution plans and tracked handoffs for non-trivial work;
- never paste full logs when the failing section is enough;
- reference files and line ranges;
- after compaction or a session switch, verify the repository handoff against Git and tests;
- avoid parallel agents editing the same files.

## Recommended implementation prompts

### Implement an issue

```text
Implement issue #<id>. First read AGENTS.md and only the source-of-truth documents relevant to this issue. Inspect the current implementation. For non-trivial work, create/update an execution plan and active handoff. Keep scope limited to the acceptance criteria. Run relevant tests and ./scripts/check.sh. Finish with a PR-ready summary, final handoff status, risks, and HUMAN_ACTION_REQUIRED items if any.
```

### Fix review findings

```text
Review the independent findings against the actual diff and repository rules. For each finding, classify it as accepted, rejected with evidence, or requiring human decision. Implement only accepted findings in the separate fix phase, add regression tests, run checks, and summarize what changed. Do not make unrelated refactors.
```

## Recommended independent review prompt

```text
Review this PR against main in a fresh independent session. Read AGENTS.md, prompts/INDEPENDENT_REVIEW.md, and only relevant accepted documentation. Do not edit files. Review the complete branch diff. Report actionable findings with blocker/high/medium/low severity, file/line evidence, concrete risk, why it matters, correction, and merge-blocking status. State explicitly if no blocking findings are found. Do not approve or merge.
```

For a large PR, the assigned reviewer may use separate read-only analysis agents for security/privacy, correctness, tests, and maintainability, then consolidate results.

## GitHub integration

When integrations are available, start with manual triggers:

- use Codex GitHub review through an explicitly requested review trigger;
- use Claude GitHub integration through explicit `@claude` instructions;
- require branch protection and human approval;
- do not give pull-request agents production credentials;
- do not allow workflows triggered by untrusted fork content to access write tokens/secrets;
- prefer read-only review tokens for first-pass review.

A fully automatic Claude ↔ Codex loop is intentionally not enabled. It can waste tokens, loop indefinitely, and create a privilege-boundary problem. Introduce automation only after the manual loop is stable, with max iterations, labels, timeouts, and human escalation.

## Human-action format

```text
HUMAN_ACTION_REQUIRED
Command: <exact command>
Reason: <why the agent cannot/should not perform this>
Expected result: <what should happen>
Verification command: <safe command>
```

The agent must not attempt a workaround after producing this block.

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

### Claude Code

- primary implementation agent in the local repository;
- explores, plans, edits, runs tests and prepares PRs;
- uses project rules, skills, hooks and subagents;
- must hand off privileged commands to a human.

### Codex

- independent reviewer and parallel investigator;
- reviews PR diffs for correctness, security, privacy, tests and maintainability;
- may implement isolated tasks on separate branches/worktrees;
- does not rubber-stamp Claude’s reasoning.

## Task lifecycle

1. **Issue** — human or ChatGPT writes problem, acceptance criteria, non-goals and risk notes.
2. **Plan** — Claude inspects only relevant sources and creates a plan when required.
3. **Implementation** — small changes, tests early, docs alongside behavior.
4. **Self-check** — Claude runs checks and produces evidence.
5. **PR** — concise description using template.
6. **Independent Codex review** — no edits during first review pass.
7. **Triage** — Claude classifies each finding: accept, reject with evidence, or ask human.
8. **Fix** — accepted findings fixed with tests.
9. **Re-review** — Codex checks changed areas.
10. **Human merge** — after CI and explicit decision.

Cap automated review/fix cycles at two. Persist unresolved disagreements in the PR.

## Token-efficient context rules

- one session/thread per task;
- start with `AGENTS.md` and the routing table, not all docs;
- skills hold repeatable procedures because their body loads only when used;
- path-scoped rules load only for relevant files;
- subagents receive narrow prompts and read-only tools for review;
- use short execution plans and handoff summaries;
- never paste full logs when the failing section is enough;
- reference files and line ranges;
- after compaction/session switch, provide objective, current state, changed files, tests and next step;
- avoid parallel agents editing the same files.

## Recommended Claude prompts

### Implement an issue

```text
Implement issue #<id>. First read AGENTS.md and only the source-of-truth documents relevant to this issue. Inspect the current implementation. If the task meets the plan criteria, create/update an execution plan. Keep scope limited to the acceptance criteria. Use suitable read-only subagents for independent analysis. Run relevant tests and ./scripts/check.sh. Finish with a PR-ready summary, risks, and HUMAN_ACTION_REQUIRED items if any.
```

### Fix review findings

```text
Review the Codex findings against the actual diff and repository rules. For each finding, classify it as accepted, rejected with evidence, or requiring human decision. Implement only accepted findings, add regression tests, run checks, and summarize what changed. Do not make unrelated refactors.
```

## Recommended Codex review prompt

```text
Review this PR against main. Read AGENTS.md and only relevant domain/security documents. Do not edit files. Focus on correctness, object-level authorization, personal-data leakage, moderation/verification abuse, migration safety, concurrency, ranking integrity, and missing tests. Report only actionable findings. For each finding include severity, evidence with file/line, user impact, and a concrete fix. State explicitly if no blocking findings are found.
```

For a large PR, explicitly ask Codex to spawn separate subagents for security/privacy, correctness, tests and maintainability, then consolidate results.

## GitHub integration

Start with manual triggers:

- use Codex GitHub review through `@codex review` or configured automatic review;
- use Claude GitHub integration through explicit `@claude` instructions;
- require branch protection and human approval;
- do not give pull-request agents production credentials;
- do not allow workflows triggered by untrusted fork content to access write tokens/secrets;
- prefer read-only review tokens for first-pass review.

A fully automatic Claude ↔ Codex loop is intentionally not enabled in this starter. It can waste tokens, loop indefinitely and create a privilege boundary problem. Introduce automation only after the manual loop is stable, with max iterations, labels, timeouts and human escalation.

## Human-action format

```text
HUMAN_ACTION_REQUIRED
Reason: <why the agent cannot/should not perform this>
Command: <exact command>
Expected result: <what should happen>
Verify with: <safe command>
After completion: reply with <required output/status>
```

The agent must not attempt a workaround after producing this block.

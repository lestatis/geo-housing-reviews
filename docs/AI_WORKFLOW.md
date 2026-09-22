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

- DeepSeek Flash is the default implementation worker for an accepted, bounded implementation
  packet. Use Pro only when a lead explicitly assigns an unusually difficult implementation of an
  already accepted design.
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
→ L0/L1 scoped checks
→ handoff summary
→ parallel CI L2 gate
→ independent review in a fresh session
→ fixes
→ second review when necessary
→ human merge
```

The assigned implementer inspects relevant sources, keeps the plan and handoff current, implements the smallest bounded change, and records deterministic evidence. The assigned reviewer begins from the complete branch diff, edits nothing, and reports evidence independently. A human owns merge approval.

Cap automated review/fix cycles at two. Persist unresolved disagreements in the PR.

## Lead-to-worker implementation contract

The lead gives an implementation worker a bounded packet; the worker does not infer product policy
or architecture from a broad request such as "implement the next module." Plans in `docs/plans/`
are the preferred source for this packet.

```text
# Worker implementation contract

## Objective
One measurable outcome.

## Acceptance criteria
- ...

## Allowed scope
Modules:
- ...
Expected files/areas:
- ...

## Relevant sources of truth
- AGENTS.md
- docs/plans/<plan>.md
- ...

## Non-goals
- ...

## Architectural constraints
- ...

## Required tests
L0 (inner loop):
- ...
L1 (worker handoff):
- ...

## Escalate instead of deciding when
- a dependency, public API, or module boundary must change;
- an accepted ADR conflicts with implementation;
- security or privacy semantics are ambiguous;
- a migration would be destructive.
```

Keep the stable worker instructions first and append only the small task-specific contract. This
allows provider prompt caching to reuse the stable prefix and avoids loading unrelated documentation.
The worker runs L0/L1 checks only. `READY_FOR_LEAD_REVIEW` triggers the L2 CI gate; it is not a
claim that the merge candidate has passed all checks.

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

### Reviewer packet

Give the reviewer the complete branch diff and the smallest context that makes it reviewable:

```text
Review task <id>.

Read:
- AGENTS.md
- docs/plans/<plan>.md
- relevant accepted ADRs

Review: git diff <base>...HEAD
CI: governance <status>; backend <status>; frontend <status>

Focus: domain invariants, transaction boundaries, authorization, concurrency, and missing tests.
Do not edit.
```

The reviewer does not need the implementer's full conversational context. Accepted review findings
return to the worker as a bounded fix task; after two review/fix iterations, escalate unresolved
questions to the lead.

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
Implement issue #<id> from the supplied Worker implementation contract. First read AGENTS.md and
only the explicitly relevant source-of-truth documents. Inspect the current implementation. For
non-trivial work, create/update an execution plan and active handoff. Keep scope limited to the
acceptance criteria. Run L0/L1 checks; do not run the L2 full gate repeatedly. Finish with a
PR-ready summary, final handoff status, risks, and HUMAN_ACTION_REQUIRED items if any.
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

A bounded local Claude ↔ DeepSeek loop is available for an explicitly approved implementation chunk:
`scripts/ai/run-implementation-cycle.sh`. It is deliberately **not** a CI workflow and it never
itself creates branches, commits, pushes, opens a pull request, or merges. It requires an existing
feature branch, an explicit locally configured `AI_DEEPSEEK_MODEL`, a plan path and a chunk
identifier. The worker wrapper records the original branch/HEAD and stops if the worker changed
either; the reviewer wrapper likewise stops if a review changed tracked Git state.

The script invokes DeepSeek through `codex exec` with a workspace-write sandbox and a strict worker
result schema. Only `READY_FOR_LEAD_REVIEW` starts a fresh non-persistent Claude Code session using
the read-only `lead-reviewer` definition, a read-oriented tool allowlist and plan permission mode.
The reviewer returns a strict JSON decision. Valid
`FIXES_REQUIRED` findings go back to the worker as a bounded fix packet; at most two review/fix
cycles are allowed. Timeouts, invalid result contracts, unavailable CLIs, decision-required states,
and the cycle cap stop the run and retain ignored `.ai/runs/` evidence for the task handoff.

`READY_FOR_HUMAN_MERGE` means the local worker/reviewer loop completed. It does not replace L2 CI,
independent human judgment, branch protection, or the human merge decision. See
`scripts/ai/README.md` for prerequisites and invocation. Do not introduce a CI trigger, automatic
retry, or a higher cycle cap without explicit human approval and a security review.

## Human-action format

```text
HUMAN_ACTION_REQUIRED
Command: <exact command>
Reason: <why the agent cannot/should not perform this>
Expected result: <what should happen>
Verification command: <safe command>
```

The agent must not attempt a workaround after producing this block.

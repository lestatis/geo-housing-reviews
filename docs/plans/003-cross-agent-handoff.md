# Cross-agent Handoff and Takeover Workflow

Status: Completed
Owner: Codex
Related issue: none (direct user request)
Last updated: 2026-07-11

## Objective

Make non-trivial task state portable between Claude Code and Codex through Git, execution plans, handoffs, deterministic checks, and independent review rules.

## Acceptance criteria

- [x] Shared instructions define takeover inspection, handoff content, tool restrictions, independent review, one-branch rollout, and human-only merge authority.
- [x] `docs/handoffs/` contains usage guidance, a reusable template, a named handoff for this task, and an evidence-based recovered handoff for interrupted identity work.
- [x] Claude and Codex have equivalent takeover/recovery prompts and mirrored `task-handoff` skills.
- [x] Existing implementation/review prompts and workflow documentation support either tool as implementer or reviewer without conflating those roles.
- [x] Governance validation checks the required handoff files, prompts, and skill symmetry without requiring an AI CLI or a permanent `current-task.md`.
- [x] `FILE_INVENTORY.txt` and concise repository entry points list the new workflow files.
- [x] Required governance and repository checks were run and their actual results recorded; the full check's sandbox-only Gradle startup failure is explicit.

## Non-goals

- Application business logic, product decisions, runtime infrastructure, dependencies, AI CLI installation, Git commits, pull requests, or merges.
- Automating AI review in CI or treating self-review as independent review.
- Changing the separate active identity-module implementation scope.

## Current system

The repository starts this task clean on `main`, 10 commits ahead of `origin/main`, with no staged, unstaged, or untracked files. Existing governance has five mirrored skills, a compact handoff prompt, and a workflow that largely assigns implementation to Claude and review to Codex. There is no `docs/handoffs/` workflow, no takeover/recovery prompts, and no handoff validator. `docs/plans/002-identity-module.md` is an independently active product implementation plan and remains out of scope.

Baseline evidence:

- `python3 scripts/validate_repo_governance.py` passed: 15 required files and 5 shared skills.
- `./scripts/check.sh` reached Gradle but failed because the sandbox cannot write the default Gradle cache.
- Retrying with `GRADLE_USER_HOME=/tmp/geo-housing-gradle` reached the wrapper download but sandbox network access was unavailable.

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|
| Handoff files | Use a task-named handoff for this workflow and reserve `current-task.md` for the interrupted identity work | Both tasks are observable; a single file would conflate their state | mark this task ready for review; archive/remove temporary handoffs after merge |
| Shared skill | Keep Claude and Codex skill bodies identical | Prevent semantic drift while minimizing platform-specific instructions | only if a platform requires syntax that cannot remain shared |
| Review prompt hierarchy | `INDEPENDENT_REVIEW.md` owns the general process; `CODEX_REVIEW.md` is a concise Codex entry point | Avoid duplication and preserve the existing filename | if review requirements diverge by platform |
| CI | Validate files and symmetry only; invoke no AI CLI | Deterministic CI must work without either agent tool | not expected |

## Implementation steps

1. Add the compact shared policy and detailed handoff documentation.
2. Add takeover, interruption recovery, and independent-review prompts; align existing prompts.
3. Update shared workflow and Claude-specific instructions.
4. Add identical `task-handoff` skills for both platforms.
5. Extend governance validation, inventory, and concise repository entry points.
6. Search for conflicting fixed-role/tool-installation language and correct only active guidance.
7. Run required validation and repository checks; update this plan and the active handoff with evidence.

## Verification

```bash
python3 scripts/validate_repo_governance.py
./scripts/check.sh
find .claude/skills .agents/skills -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort
while IFS= read -r path; do test -e "$path" || exit 1; done < FILE_INVENTORY.txt
rg -n "primary implementation|Claude Code implements|Codex performs|install .*Codex|install .*Claude|self-review" --glob '*.md' .
rg -n "claude|codex" .github/workflows
git diff --check
```

Run `./scripts/check.sh` with a writable local Gradle cache if the default user cache remains sandboxed. Distinguish governance failures from application/build or tool-availability failures.

## Risks and rollback/forward-fix

- Duplicated procedures can drift. Keep shared policy concise, put detail in one workflow document, and keep mirrored skill bodies identical.
- A permanent stale `current-task.md` can mislead takeovers. Its status and evidence must be updated now and it may be archived or removed after merge.
- Existing historical plans truthfully name the tools that performed earlier work; do not rewrite history, only active prescriptive guidance.
- These are repository-process files only. Reverting the documentation and validator changes restores the prior workflow without runtime impact.

## Progress log

- 2026-07-11: Reconstructed branch, commits, clean Git state, plans, prompts, skills, CI, and governance validator; captured baseline validation and identified partial fixed-role workflow.
- 2026-07-11: Initial `.agents/skills/task-handoff/` creation was sandbox-blocked; user created/restored the directory and work resumed without tracked partial changes.
- 2026-07-11: Added the cross-agent policy, handoff docs, takeover/recovery/review prompts, role-neutral workflow, mirrored skill, inventory, and governance checks. Recovered the interrupted identity handoff from commits and file evidence.
- 2026-07-11: Governance validation passed with 23 required files and 6 shared skills. Skill symmetry, inventory existence, template headings, CI search, fixed-role search, and `git diff --check` passed. `./scripts/check.sh` passed governance but Gradle could not start because sandbox networking prevents its file-lock listener from selecting a wildcard IP.

## Final outcome

The repository now has a symmetric Claude Code/Codex handoff and takeover process grounded in Git, plans, tracked handoffs, tests, and fresh read-only independent review. Existing useful instructions were integrated rather than replaced, the interrupted identity task has an evidence-based recovered handoff, and CI remains deterministic with no AI CLI dependency. Governance checks pass. The only incomplete verification is the Gradle-backed portion of `./scripts/check.sh`, which cannot start in this sandbox due to local cache/network socket restrictions; this is an environment limitation, not a governance failure introduced by the change.

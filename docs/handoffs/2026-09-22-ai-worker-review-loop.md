# Task handoff

## Objective

Implement the bounded local DeepSeek worker → fresh Claude reviewer process described in
`docs/plans/2026-09-22-ai-worker-review-loop.md`.

## Active branch

`feat/automation`, current `HEAD` (`fix(ai): review worker working tree`). The user explicitly
authorized this fix commit. No push, pull request, or merge was authorized.

## Related issue or plan

`docs/plans/2026-09-22-ai-worker-review-loop.md`

## Current status

ready_for_review

## Completed work

- Read the repository workflow, plan/handoff conventions, existing DeepSeek worker prompt, relevant
  Claude definitions, governance validator, scoped-check script, current Git state, and tool help.
- Confirmed the implementation must update `docs/AI_WORKFLOW.md`: its current rollout policy says
  the automatic loop is not enabled.
- Confirmed local capabilities only: `codex exec` supports `--model`, `--sandbox workspace-write`,
  `--output-schema` and output files; Claude Code supports `-p`, `--json-schema`, `--agent`,
  `--tools`, `--permission-mode plan` and `--no-session-persistence`.
- Added `scripts/ai/`: strict worker/review schemas, standard-library validation and Claude envelope
  extraction, a DeepSeek worker wrapper, read-only Claude review wrapper, and an orchestrator capped
  at two review/fix rounds.
- Added the `lead-reviewer` Claude definition; mirrored `delegate-implementation` skill; operating
  documentation; ignored `.ai/` run artefacts; and an explicit local-automation policy in
  `docs/AI_WORKFLOW.md`.
- Added fake-CLI tests. They prove routing implementation → review → fix → re-review →
  `READY_FOR_HUMAN_MERGE`, reject malformed worker JSON before review, and reject `main` before any
  worker command runs; they also stop a worker that commits. A post-test entry-point smoke check
  found missing `--help` support in two wrappers; it is fixed and covered.
- Staged the complete scoped diff, verified `git diff --cached --check`, and committed it as
  `3a99bdb3`.
- Independent review found a blocker: `git diff <base>...HEAD` omits the worker's uncommitted and
  untracked implementation. It also identified missing provider pinning, wasteful routing of
  non-blocking findings, an implicit Claude model default, and this stale handoff branch/SHA.
- Fixed the review scope: Claude now receives explicit instructions to inspect `git status --short`,
  `git diff --no-ext-diff <base>` and relevant untracked files. The wrapper changes directory to the
  repository root before invoking Claude.
- Required `AI_CODEX_PROFILE`, passed it to `codex exec --profile`, and explicitly overrides
  `model_provider = "deepseek"`; required `AI_CLAUDE_REVIEW_MODEL` and removed the agent's hidden
  Sonnet default. The worker also changes to the repository root before invoking Codex.
- Updated result semantics: `FIXES_REQUIRED` requires at least one merge-blocking finding;
  `READY_TO_MERGE` may preserve non-blocking findings without starting another worker cycle.
- Added fake-CLI coverage for a worker tracked edit plus untracked file visible to the reviewer,
  profile/provider/model arguments, root working directory, non-blocking final findings and invalid
  `FIXES_REQUIRED` without a merge blocker.
- Committed the accepted review fixes at the current `HEAD` after `git diff --cached --check` passed.

## Remaining work

- Obtain re-review and CI evidence for the current `HEAD`.

## Decisions made

- The loop will be manual and local, not a CI workflow.
- DeepSeek model selection will be explicit through a non-committed environment variable.
- The loop will require an existing feature branch and cap itself at two review/fix cycles.
- Worker review must cover the current working tree and relevant untracked files; only merge-blocking
  findings receive another worker cycle.
- DeepSeek selection is an explicit local profile plus `model_provider = "deepseek"`; the reviewer
  model is always explicit rather than inherited from agent metadata.

## Assumptions

- The user authorized this repository-local process implementation and accepted review fixes through
  the current `HEAD`, but not credentials, provider configuration, push, pull request, or merge.

## Files changed

- `docs/plans/2026-09-22-ai-worker-review-loop.md`
- `docs/handoffs/2026-09-22-ai-worker-review-loop.md`
- `.gitignore`, `FILE_INVENTORY.txt`, `docs/AI_WORKFLOW.md`
- `.claude/agents/lead-reviewer.md`, `.claude/skills/delegate-implementation/SKILL.md`,
  `.agents/skills/delegate-implementation/SKILL.md`
- `scripts/ai/README.md`, `lib.sh`, `deepseek-worker.sh`, `claude-reviewer.sh`,
  `run-implementation-cycle.sh`, `validate_result.py`, `extract_claude_result.py`, both schemas and
  `tests/test_ai_automation.py`

## Commands run

- Read the applicable delivery and handoff skills; relevant repository workflow/governance files;
  existing prompts and agent definitions; current Git state.
- Ran `codex exec --help`, `claude --help`, and `claude -p --help` (read-only capability checks).

## Tests and verification

- `bash -n scripts/ai/*.sh` — passed.
- `python3 -m py_compile scripts/ai/*.py` — passed.
- `python3 -m unittest discover -s scripts/ai/tests -p 'test_*.py'` — passed: 5 tests.
- `python3 scripts/validate_repo_governance.py` — passed: 24 required files and 7 shared skills.
- `git diff --check` — passed.
- All three `scripts/ai/*` entry points accepted `--help` without configuration — passed.
- `git diff --cached --check` immediately before commit — passed.
- `./scripts/check.sh` L2 — started once; governance passed and backend Gradle work ran, but the
  terminal detached before its final result. See Known failures.
- `PYTHONDONTWRITEBYTECODE=1 bash -n scripts/ai/*.sh` — passed after review fixes.
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/ai/tests -p 'test_*.py'` —
  passed: 6 tests after review fixes.
- `PYTHONDONTWRITEBYTECODE=1 ./scripts/check-scoped.sh governance` — passed after review fixes.
- `git diff --check` — passed after review fixes.

## Known failures

None in the scoped checks. A single local `./scripts/check.sh` L2 run was started because CI cannot
run without a push. Governance passed and the Gradle check executed (fresh backend test artefacts
were observed), but the terminal detached before its final exit code and did not retain the frontend
tail. Do not treat L2 as green; rerun it in CI or a terminal that preserves the final result.

## Risks and unresolved questions

- Actual DeepSeek/Anthropic authentication and provider availability cannot be tested without using
  the user's credentials and incurring model calls. Fake-CLI tests cover repository routing and
  validation, not provider connectivity or model behavior.
- Claude `plan` permission mode and restricted tool list are CLI policy controls, not operating-system
  isolation; the documented trusted-local-clone constraint remains important.
- The local L2 gate's final exit status is unavailable as described above. This is verification
  evidence loss, not a known source/test failure.

## Human actions required

None at this checkpoint.

## Recommended next action

Request re-review focused on the working-tree scope and provider/model routing changes, then let CI
produce the L2 result before any merge decision.

## Last updated

2026-09-23

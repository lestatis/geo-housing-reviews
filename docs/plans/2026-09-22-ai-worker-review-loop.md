# Bounded local AI worker and review loop

Status: Active
Owner: Codex
Related issue: None
Last updated: 2026-09-22

## Objective

Provide a repository-native, opt-in local command that runs one approved plan chunk through a
configured DeepSeek Codex worker and a fresh, read-only Claude review session, with bounded fix
cycles and machine-readable results.

## Acceptance criteria

- [x] A worker wrapper accepts one plan path and chunk identifier, requires an explicit DeepSeek
  model configuration, uses `codex exec` with `workspace-write`, and emits a validated JSON result.
- [x] A reviewer wrapper starts a fresh Claude Code print session with a read-oriented tool allowlist and plan
  permission mode, then emits a validated JSON review result.
- [x] A loop wrapper routes `READY_FOR_LEAD_REVIEW` to review, routes accepted fixes back to the
  worker, caps review/fix cycles at two, and never creates a branch, commits, pushes, opens a PR,
  or merges.
- [x] Run artefacts and secrets remain untracked; invalid paths, main-branch runs, malformed model
  responses, worker/ref mutations, unavailable CLIs, and decision-required results stop safely with
  useful evidence.
- [x] Automated tests exercise wrapper argument construction and routing with fake CLIs, and
  governance plus shell/Python syntax checks pass.
- [x] Workflow and operating documentation explain setup, invocation, limits, and human merge
  ownership.

## Non-goals

- Installing, configuring, or storing DeepSeek/Anthropic credentials or changing a user's provider
  configuration.
- CI-triggered agents, automatic branch creation, commits, PRs, merges, or a third review/fix
  cycle.
- Replacing deterministic L0/L1/L2 checks or an independent human-approved review process.

## Current system

`docs/AI_WORKFLOW.md` now defines the worker/reviewer separation and the bounded local runner.
`prompts/DEEPSEEK_IMPLEMENT.md` remains the stable worker contract. The implementation is local,
does not modify CI, and stores only ignored run evidence under `.ai/`.

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|
| Orchestration | Local, manually invoked shell wrappers | Keeps credentials, authority and execution on the developer machine; CI stays deterministic | a human explicitly approves CI rollout |
| Worker identity | `AI_DEEPSEEK_MODEL` must be set per invocation/environment | Prevents the automation from silently using the model last selected in a UI | a checked-in non-secret provider profile becomes accepted |
| Branch authority | Require an existing non-`main` branch | The loop must not choose branch names or alter Git history | a human approves a branch-management workflow |
| Result contract | Strict JSON schemas plus local standard-library validation | Downstream routing must not parse prose or need a new dependency | the contracts need versioned interoperability |
| Review isolation | New `claude -p --no-session-persistence` session, read tools and plan mode | It cannot reuse the worker context or write a fix itself | CLI permissions offer a stronger documented read-only mode |

## Implementation steps

1. Add active plan and handoff; inspect local CLI capabilities and existing workflow constraints.
2. Add result schemas, standard-library validators/extractor, worker/reviewer wrappers, and a bounded
   orchestrator.
3. Add the read-only Claude reviewer definition, mirrored delegation skill, documentation, and
   ignored local run directory.
4. Add fake-CLI integration tests; run syntax, test, governance and diff checks.
5. Record final evidence and hand off for independent review.

## Verification

```bash
python3 -m unittest discover -s scripts/ai/tests -p 'test_*.py'
bash -n scripts/ai/*.sh
python3 -m py_compile scripts/ai/*.py
./scripts/check-scoped.sh governance
git diff --check
```

## Risks and rollback/forward-fix

- A configured provider/model may still be unavailable or unauthenticated. The wrappers fail before
  any routing result and expose no credential values; fix local configuration outside the repository.
- LLM output can violate the contract. Validation stops the loop and retains the raw local output for
  diagnosis instead of proceeding on guessed text.
- `plan` permission mode is a Claude Code policy, not OS isolation. The wrapper additionally limits
  built-in tools and the reviewer definition forbids edits; run only in a trusted local clone.
- Reverting the new local scripts, docs, agent definition and `.gitignore` entry restores the prior
  manual workflow without application or data migration impact.

## Progress log

- 2026-09-22: Started from a clean `main` worktree; verified `codex exec` supports model selection,
  workspace sandboxing and output schemas, and Claude Code supports print mode, JSON schema output,
  an agent definition, explicit tools and plan permission mode.
- 2026-09-22: Added the opt-in local wrappers, strict worker/review contracts, fresh read-only Claude
  reviewer, mirrored delegation skill, operating guide and ignored run artefacts. Fake-CLI tests
  verified one fix/review round, malformed worker output, the main-branch guard, and detection of a
  worker commit. An entry-point smoke check found missing `--help` handling in the reviewer and
  orchestrator; both were corrected and now have regression coverage.

## Final outcome

Implementation is ready for independent review. Real provider/authentication calls intentionally
remain untested because credentials are not part of the repository or this change.

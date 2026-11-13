# Plan 024 — the loop runner owns L1

Status: Active
Owner: Claude
Related issue: None; follow-up to `docs/plans/2026-09-22-ai-worker-review-loop.md`
Last updated: 2026-11-13

## Objective

Let `scripts/ai/run-implementation-cycle.sh` run a chunk's L1 checks itself, outside the DeepSeek
sandbox, so a chunk that needs Gradle or Docker can reach review without a human running L1 by hand.

## Problem

The worker runs in Codex `workspace-write`, which disables networking and denies
`/var/run/docker.sock`. Gradle cannot start (it needs loopback sockets) and Testcontainers cannot
reach Docker, so any backend chunk returns `HUMAN_ACTION_REQUIRED` and the reviewer never starts
(020-A, 2026-11-13, `.ai/runs/implementation-loop.NzzT37`).

Loosening the sandbox was rejected: Docker-group access is root-equivalent on the host, and network
access plus the sandbox's broad read access would let a remote-model-driven agent send local files
out.

## Acceptance criteria

- [x] A chunk declares L1 in a fenced ```` ```ai-l1 ```` block inside its own plan section, one
  command per line, run from the repository root.
- [x] The runner copies the block when the loop starts; a later worker edit to the plan does not
  change what runs.
- [x] With a block present, the worker is told the runner owns L1 and may return
  `READY_FOR_LEAD_REVIEW` without a passed test of its own; without one, behaviour is unchanged.
- [x] Before running L1, the runner stops (exit 4) if the worker changed build, automation or agent
  configuration files since the loop started; those would otherwise run unsandboxed unreviewed.
- [x] Each command runs with a hard timeout (`AI_L1_TIMEOUT_SECONDS`, default 900, 1–7200); its
  process group is killed on timeout. Every command runs; results go to `l1-<n>.json` plus a log
  per command.
- [x] A failed L1 becomes a fix packet through the existing `--review-findings` path and counts
  against the same cycle cap (`--max-cycles` = maximum worker runs = maximum reviews).
- [x] The reviewer starts only after a green L1 and is given the runner's L1 evidence path.
- [x] Fake-CLI tests cover: block extraction, pass → review, fail → fix → pass, fail at the cap,
  timeout, protected-file stop, plan edit by worker ignored, and no-block legacy behaviour.
- [x] `scripts/ai/README.md`, `docs/AI_WORKFLOW.md`, the worker prompt and the
  `delegate-implementation` skill describe the change.

## Non-goals

- Changing the Codex sandbox, network or Docker access.
- Running L2, committing, pushing or merging.

## Decisions

| Decision | Choice | Reason |
| --- | --- | --- |
| Command source | Fenced `ai-l1` block in the chunk's plan section, snapshotted at loop start | Lead-owned and reviewed with the plan; the snapshot stops the worker choosing what runs outside its sandbox |
| Protected paths | `scripts/`, `.claude/`, `.github/`, `prompts/`, `.codex/`, `AGENTS.md`, `CLAUDE.md`, any `buildSrc/`, `build-logic/` or `gradle/` directory, `*.gradle`, `*.gradle.kts`, `gradlew*`, `gradle.properties`, `package.json`, `pnpm-lock.yaml`, `pnpm-workspace.yaml`, `.npmrc`, `Makefile`, `Dockerfile*`, `docker-compose*` | Code that configures how commands run. Test and production code still run unsandboxed under L1, exactly as they do when a human or CI runs it; review follows |
| Fix-packet shape | The L1 failure is written as a `FIXES_REQUIRED` review result | Reuses the validated worker fix path; no second packet format |
| Log tail in the packet | Last 60 lines, at most 6000 characters, per failed command | Enough to diagnose; the worker can read the full log in the workspace |

## Risks

- L1 executes worker-written test and production code outside the sandbox. This is the same exposure
  as a human running L1 on an unreviewed branch; the protected-path stop removes the build-script
  route, which runs before any test.
- A log tail is sent to the DeepSeek provider. Test logs here hold only synthetic data and
  container-local credentials.

## Rollback

Script-only and opt-in per plan: removing a chunk's `ai-l1` block restores the previous behaviour.

## Progress log

- 2026-11-13: Founder asked for the runner to own L1 after 020-A stopped on the sandbox. Drafted and
  implemented in the same session on `perf/020-shared-test-postgres`, committed separately (founder
  choice) so the 020-A review diff excludes it; to be moved to its own branch or PR before merge.
  `python3 -m unittest discover -s scripts/ai/tests -p 'test_*.py'`: 16 tests OK; `bash -n` and
  `py_compile` clean; shellcheck is not installed. `scripts/validate_repo_governance.py` fails
  locally only on two malformed ignored `.ai/` artefacts from 2026-09-27, unrelated to this change.
- 2026-11-14: The first real review run failed before reviewing: Claude Code 2.1.283 rejects a
  `--json-schema` carrying `"$schema": "https://json-schema.org/draft/2020-12/schema"` ("no schema
  with key or ref"). Pre-existing in `claude-reviewer.sh` (never reached before). The wrapper now
  drops that key from the argument; regression test added; a live `claude -p` smoke call returned a
  structured result that `extract_claude_result.py` accepts.
- 2026-11-14: The next review returned free text: `--agent lead-reviewer` limits tools to its
  `tools:` allowlist, which lacked `StructuredOutput`, so `--json-schema` was not enforced. Added
  it to `.claude/agents/lead-reviewer.md` with a regression test; a live probe with the real agent
  now returns a structured result the extractor accepts.

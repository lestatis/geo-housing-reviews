# Local AI implementation loop

This opt-in developer command runs one already-approved plan chunk through a configured DeepSeek
model in Codex, then sends the resulting branch diff to a fresh read-only Claude Code reviewer.
It is a local aid, not CI and not a merge authority.

## Prerequisites

- Start on an existing feature branch; the scripts reject `main` and `master` and never create a
  branch.
- `codex`, `claude`, `python3`, GNU `timeout`, Git and the repository dependencies must already be
  available. The scripts never install them.
- Configure a user-level DeepSeek profile (normally `~/.codex/deepseek.config.toml`) outside this
  repository. It must define `model_provider = "deepseek"` and its
  `[model_providers.deepseek]` table; keep the API key out of this repository. Codex profile files
  and provider settings are intentionally machine-local. Supply the exact profile and model names:

  ```bash
  export AI_CODEX_PROFILE='deepseek'
  export AI_DEEPSEEK_MODEL='your-configured-deepseek-model'
  export AI_CLAUDE_REVIEW_MODEL='your-explicit-claude-review-model'
  ```

- Create or update the applicable execution plan and active handoff before running a non-trivial
  chunk.

## Run one approved chunk

```bash
AI_CODEX_PROFILE='deepseek' \
AI_DEEPSEEK_MODEL='your-configured-deepseek-model' \
AI_CLAUDE_REVIEW_MODEL='your-explicit-claude-review-model' \
  scripts/ai/run-implementation-cycle.sh main docs/plans/023-mobile-discovery.md 023-B
```

Use `--max-cycles 1` for implementation plus a single review, or omit it for the default maximum of
two worker runs (each followed by L1 and review). `AI_COMMAND_TIMEOUT_SECONDS` defaults to 1800 and accepts 1–7200. The
reviewer model is required rather than silently inheriting an agent default.

Run artefacts are stored under ignored `.ai/runs/`. Each worker result and review result is validated
before its next stage. A successful run prints `READY_FOR_HUMAN_MERGE`; it means the bounded local
workflow completed, not that CI is green or a merge is authorized.

## Runner-owned L1

The worker's Codex sandbox has no network and no Docker socket, so Gradle and Testcontainers cannot
run in it. A chunk that needs them declares its L1 in a fenced block inside its own plan section
(the section under the heading that starts with the chunk id), one command per line, run from the
repository root:

````markdown
### 020-A — harness, baseline and pilot

```ai-l1
cd apps/api && ./gradlew :app:test --tests '*SomeTest' -PskipMutation
cd apps/api && ./gradlew :app:spotlessCheck :app:checkstyleTest
```
````

With a block present the runner:

1. copies the commands when the loop starts (`l1-commands.txt`), so a worker edit to the plan does
   not change what runs, and tells the worker that the runner owns L1;
2. after a `READY_FOR_LEAD_REVIEW` worker result, stops with `LEAD_DECISION_REQUIRED` (exit 4) if the
   worker changed build, automation or agent files (`protected-fingerprint.py` lists them), because
   L1 would run them unsandboxed before any review;
3. runs every command with a hard timeout (`AI_L1_TIMEOUT_SECONDS`, default 900, 1–7200), killing
   the command's whole process group on timeout, and writes `l1-<n>.json` plus `l1-<n>-<k>.log`;
4. on failure, sends the failing commands and their log tails back to the worker as a fix packet,
   within the same `--max-cycles` budget; on success, starts the reviewer with the L1 evidence.

L1 still executes worker-written test and production code outside the sandbox, as it would if a
human ran it on an unreviewed branch. Without a block, the worker must report its own L1 evidence as
before.

## Safety and stopping conditions

- The worker receives only one plan chunk and is instructed not to create branches, commit, push,
  open PRs or merge. The wrapper records the initial branch/HEAD and stops if either changes; it
  does not itself perform any of those Git operations.
- Claude runs in a new non-persistent session with the repository's `lead-reviewer` definition,
  a read-oriented tool allowlist and `plan` permission mode. It reviews the current working-tree
  diff and relevant untracked files; it does not fix.
- Only findings marked `blocks_merge: true` are copied into the next DeepSeek fix packet.
  Non-blocking findings remain in the complete review evidence for the human; they are neither sent
  to the worker nor allowed to consume the bounded fix cycle.
- A non-ready worker result, malformed output, timeout, unavailable command, human/lead decision,
  reviewer stop, or exhausted cycle cap exits non-zero and leaves local evidence for the handoff.
- The loop does not run L2. Follow `CONTRIBUTING.md`: worker evidence is L0/L1 only, the L2 full gate
  and final merge decision remain with CI and a human.

To exercise wrapper logic without model calls, run:

```bash
python3 -m unittest discover -s scripts/ai/tests -p 'test_*.py'
```

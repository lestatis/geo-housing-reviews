---
name: "delegate-implementation"
description: "Run one approved plan chunk through the bounded local DeepSeek worker and fresh Claude review loop."
---

# Delegate implementation

Use this skill only when a human has approved one bounded implementation chunk. It invokes a local
workflow; it never authorizes a credential change, branch creation, commit, push, pull request or
merge.

1. Read `AGENTS.md`, the named plan, current handoff and `docs/AI_WORKFLOW.md`. Inspect the branch,
   status and diff before invoking anything.
2. Require an existing non-`main` feature branch, a valid base ref, `AI_CODEX_PROFILE` configured
   for `model_provider = "deepseek"`, `AI_DEEPSEEK_MODEL`, and `AI_CLAUDE_REVIEW_MODEL` configured
   outside the repository. Do not inspect or print credential values.
3. Invoke exactly one bounded run:

   ```bash
   AI_CODEX_PROFILE=deepseek AI_DEEPSEEK_MODEL=<configured-model> \
   AI_CLAUDE_REVIEW_MODEL=<configured-reviewer-model> \
     scripts/ai/run-implementation-cycle.sh main docs/plans/<plan>.md <chunk-id>
   ```

4. Read the JSON artefacts printed by the script and independently inspect the resulting Git diff.
   `READY_FOR_HUMAN_MERGE` is evidence for human review only; never merge automatically.
5. If the script stops for a decision, malformed result, unavailable tool, timeout, or the two-cycle
   cap, update the active handoff and return the exact evidence path to the human. Do not retry
   indefinitely or substitute self-review.

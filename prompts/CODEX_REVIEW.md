# Codex — independent review prompt

This is the concise Codex entry point for the platform-neutral, read-only process in [`INDEPENDENT_REVIEW.md`](INDEPENDENT_REVIEW.md). Codex may also implement tasks when assigned; this file selects the reviewer role for this session only.

```text
Review this pull request against its target branch in a fresh independent session. Read AGENTS.md, prompts/INDEPENDENT_REVIEW.md, and only relevant source-of-truth documents. Inspect the complete branch diff and surrounding implementation. Do not edit files or rely on implementation-session conclusions.

Prioritize correctness, domain invariants, object-level authorization, personal-data exposure, upload/evidence handling, moderation and verification abuse, migration/concurrency/idempotency safety, ranking integrity, and missing regression tests.

For every actionable finding provide: blocker/high/medium/low severity, exact file/line evidence where possible, concrete failure or risk, why it matters, a minimal correction, and whether it blocks merge. Avoid style-only comments enforced by tooling. Explicitly state when there are no blocking findings. Do not approve or merge automatically.
```

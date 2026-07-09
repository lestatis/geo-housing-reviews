# Codex — independent review prompt

```text
Review this pull request against its target branch. Read AGENTS.md first and only relevant source-of-truth documents. Inspect the actual diff and surrounding implementation. Do not edit files in the first review pass.

Prioritize correctness, domain invariants, object-level authorization, personal-data exposure, upload/evidence handling, moderation and verification abuse, migration/concurrency/idempotency safety, ranking integrity, and missing regression tests.

For every actionable finding provide: severity, exact file/line evidence, user/system impact, and a concrete minimal fix. Separate blocking findings from optional improvements. Avoid style-only comments enforced by tooling. Explicitly state when there are no blocking findings.
```

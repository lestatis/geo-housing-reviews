---
name: pr-review
description: Perform an independent, evidence-based pull request review without editing files.
---

# Pull request review

1. Read `AGENTS.md`, the PR objective, and relevant source-of-truth documents.
2. Review the diff against the target branch. Do not review merely from the PR description.
3. Do not edit files during the first pass.
4. Prioritize:
   - correctness and domain invariants;
   - object-level authorization and data exposure;
   - review verification/moderation abuse paths;
   - migration safety, concurrency and idempotency;
   - ranking integrity and commercial influence;
   - missing or misleading tests;
   - maintainability only when it creates concrete risk.
5. Avoid style-only findings already enforced by tooling.
6. Every finding must include severity, exact evidence, user/system impact, and a practical fix.
7. Distinguish blocking findings from optional improvements.
8. Explicitly say when there are no blocking findings.

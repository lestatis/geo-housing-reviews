---
name: feature-delivery
description: Deliver a repository feature from issue to PR-ready state with bounded scope, tests, documentation and review evidence.
---

# Feature delivery

Use this skill when implementing a user-facing or internal feature.

1. Read `AGENTS.md`, the issue, and only the routed source-of-truth documents.
2. Inspect the current code and tests before proposing changes.
3. Restate acceptance criteria and explicit non-goals.
4. Create or update an execution plan when the task crosses modules, changes schemas/contracts, or is expected to take more than one focused session.
5. Implement the smallest coherent vertical slice. Do not add speculative abstractions.
6. Add tests at the cheapest effective level; include authorization, invalid input, and important state transitions.
7. Update API/domain/product documentation in the same change when behavior changes.
8. During the edit loop use L0 tests. Before handoff run L1 affected-module checks with
   `-PskipMutation`; do not run `./scripts/check.sh` repeatedly. Declare
   `READY_FOR_LEAD_REVIEW` only after reporting those scoped results.
9. The lead/CI owns the one L2 full-gate run for the merge candidate.
10. Finish with: changed files, behavior, tests, risks, decisions, and any `HUMAN_ACTION_REQUIRED` block.

Stop and ask for a human decision for scope, legal/public policy, new paid infrastructure, destructive migration, secrets, or privileged commands.

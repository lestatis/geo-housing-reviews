# Execution Plans

Use this template for work that is multi-step, cross-module, security-sensitive, migration-heavy, or likely to outlive one agent session.

Create a task-specific plan under `docs/plans/YYYY-MM-DD-short-name.md`. Keep it updated while working. The plan is an execution record, not a speculative essay.

## Required template

```markdown
# <Plan title>

Status: Draft | Active | Blocked | Completed
Owner: Human | Claude | Codex
Related issue: #<number>
Last updated: YYYY-MM-DD

## Objective

One measurable result.

## Acceptance criteria

- [ ] Observable criterion
- [ ] Observable criterion

## Non-goals

- Explicitly excluded work

## Current system

Relevant modules, files, behavior, constraints, and links to source-of-truth docs.

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|

## Implementation steps

1. Small verifiable step.
2. Small verifiable step.

## Verification

Commands, test cases, manual checks, security/privacy checks, migration verification.

## Risks and rollback/forward-fix

Known failure modes and recovery strategy.

## Progress log

- YYYY-MM-DD: completed X; learned Y; next Z.

## Final outcome

What changed, deviations from plan, tests, remaining follow-ups.
```

## Rules

- Do not create a plan for trivial one-file changes.
- Do not treat a plan as approval to expand scope.
- Update the plan when facts change.
- If blocked by permissions or credentials, record `HUMAN_ACTION_REQUIRED` and continue only with independent work.
- Archive completed plans; do not rewrite history to make the work look linear.

# Contributing

## Branches

Use short-lived branches:

- `feat/<issue>-<slug>`
- `fix/<issue>-<slug>`
- `docs/<issue>-<slug>`
- `chore/<issue>-<slug>`

Do not develop directly on `main`.

## Task quality

Every task should include:

- problem and user value;
- acceptance criteria;
- out-of-scope items;
- design/API references when relevant;
- security/privacy notes;
- expected tests.

Use the GitHub issue templates in `.github/ISSUE_TEMPLATE/`.

## Commits

Use Conventional Commits where practical:

```text
feat(reviews): add structured noise rating
fix(verification): expire pending evidence
chore(ci): validate agent configuration
```

Keep commits reviewable. Do not combine generated formatting, dependency upgrades, migrations, and feature behavior unless they are inseparable.

## Pull requests

A PR should usually be small enough to review in one focused session. It must include:

- why the change exists;
- what changed;
- screenshots or recordings for UI changes;
- test evidence;
- migration and compatibility notes;
- security/privacy/moderation impact;
- known limitations and follow-ups.

Use `.github/PULL_REQUEST_TEMPLATE.md`.

## Required checks

Before requesting review:

```bash
./scripts/check.sh
```

After applications are scaffolded, each application must document its own build, lint, unit, integration, and end-to-end commands in a local README and, where useful, a nested `AGENTS.md`.

## Review loop

1. The assigned implementation agent implements and self-verifies on one branch.
2. The assigned review agent performs an independent review in a fresh session without editing the branch.
3. The implementation agent addresses accepted findings in a separate fix phase.
4. The review agent re-reviews changed areas when necessary.
5. A human decides whether to merge.

Cap automated fix/review loops at two iterations. After that, summarize disagreement for human resolution instead of creating an endless agent loop.

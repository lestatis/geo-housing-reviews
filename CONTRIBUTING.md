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

Before requesting review, from the repository root:

```bash
./scripts/check.sh
```

It runs three phases and stops at the first failure:

1. `scripts/validate_repo_governance.py` — required files exist, `CLAUDE.md` still imports
   `@AGENTS.md`, instruction files stay under 32 KiB, skills match across Claude and Codex, and a
   coarse scan for committed keys. Seconds.
2. `apps/api/gradlew check` — per module: `checkstyle`, `spotlessCheck`, `test`, and `mutationTest`
   (ADR-0009). For `:app` this includes the Testcontainers integration suites, the Cucumber
   acceptance scenarios and the ArchUnit boundary rules. This is nearly all of the wall time.
3. `pnpm lint/test/typecheck` — skipped while there is no `package.json`.

### The fast loop

The full gate is the *merge* gate. While iterating, check only what you touched:

```bash
cd apps/api
./gradlew :modules:<module>:test -PskipMutation      # seconds
./gradlew :app:test --tests '*SomeIntegrationTest'   # one suite
```

`-PskipMutation` is for local iteration only — never for a pre-review or CI run
(`.claude/rules/testing.md`).

### If the gate is unexpectedly slow or fails oddly

Most of the cost is container startup: nearly every `:app` integration test class starts its own
PostgreSQL container. Two failure modes are worth recognising:

- **Orphaned containers.** A build killed mid-run leaves containers behind, because Testcontainers'
  reaper dies with the JVM. They accumulate and starve the machine. Check and clear with:

  ```bash
  docker ps -q --filter label=org.testcontainers=true | wc -l
  docker rm -f $(docker ps -aq --filter label=org.testcontainers=true)
  ```

- **`NoSuchFileException: .../test-results/test/binary/in-progress-results-generic.bin`.** Not a test
  failure — an interrupted run left Gradle's test bookkeeping inconsistent. Remove
  `apps/api/app/build/test-results` and re-run.

When capturing the result programmatically, read the script's own exit code rather than a wrapper's;
a background runner may report the wrapper's status instead.

After applications are scaffolded, each application must document its own build, lint, unit, integration, and end-to-end commands in a local README and, where useful, a nested `AGENTS.md`.

## Review loop

1. The assigned implementation agent implements and self-verifies on one branch.
2. The assigned review agent performs an independent review in a fresh session without editing the branch.
3. The implementation agent addresses accepted findings in a separate fix phase.
4. The review agent re-reviews changed areas when necessary.
5. A human decides whether to merge.

Cap automated fix/review loops at two iterations. After that, summarize disagreement for human resolution instead of creating an endless agent loop.

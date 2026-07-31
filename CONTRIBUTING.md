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
3. `pnpm lint/test/typecheck` — ESLint, Vitest and `tsc` across the workspace. `typecheck` and
   `test` regenerate `apps/web`'s API client from `docs/api/openapi.json` first, so they cannot pass
   against a stale one. If Node is installed through nvm, the script sources it: a non-interactive
   shell does not read `~/.bashrc`, and the gate should not fail on a machine where Node plainly
   works.

It does **not** run `pnpm e2e`. Playwright needs Postgres, the local OIDC provider and the API
running (`apps/web/README.md`); a gate that silently skipped them would be worse than one that never
claimed to cover them. Run it yourself before requesting review on a change to `apps/web`.

Changing an API endpoint fails phase 2, not phase 3: `OpenApiContractIntegrationTest` compares the
published document to `docs/api/openapi.json`. Regenerate with

```bash
cd apps/api && ./gradlew :app:test --tests '*OpenApiContractIntegrationTest' -DupdateOpenApiSpec=true
```

and commit the diff alongside the change that caused it.

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
  reaper dies with the JVM. They accumulate and starve the machine. Check with:

  ```bash
  docker ps -q --filter label=org.testcontainers=true | wc -l
  docker rm -f $(docker ps -aq --filter label=org.testcontainers=true)
  ```

  If removal fails with `could not kill container: permission denied` — including under `sudo` —
  the shim state is wedged and no amount of privilege will signal them. The full recovery is three
  steps, and stopping after the second leaves the machine unable to run any integration test:

  ```bash
  sudo systemctl restart docker          # 1. clears the wedged containers
  pgrep -c docker-proxy                  # 2. these survive the restart
  sudo pkill -f docker-proxy && sudo systemctl restart docker   # 3. release their ports
  ```

  Step 3 is the one that is easy to miss. Each leaked container leaves a root-owned `docker-proxy`
  holding an ephemeral port; the daemon restart removes the containers but not these processes.
  Until they are killed, every new container fails with
  `failed to bind host port 0.0.0.0:<port>/tcp: address already in use`, which surfaces as
  `initializationError` on most `:app` test classes rather than as anything resembling a Docker
  problem.

  Observed on 2026-07-29 with Docker 29.6.1, cgroup v2 and the systemd cgroup driver: 113 orphaned
  containers left 264 orphaned proxies holding 269 ports. Prevention is cheaper than recovery — let
  the gate finish rather than killing it mid-run.

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

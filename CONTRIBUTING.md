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

## Migrations

Each module owns a version range and its own Flyway history table, in its own schema:

| Range | Module | Location |
| --- | --- | --- |
| `V1.x` | shared setup (extensions) | `app/src/main/resources/db/migration/root` |
| `V2.x` | identity | `modules/identity/src/main/resources/db/migration/identity` |
| `V3.x` | properties | likewise |
| `V4.x` | reviews | |
| `V5.x` | verification | |
| `V6.x` | moderation | |

**A module's migrations are ordered only against its own.** Take the next free number in your
module's range; what the other modules have already applied does not matter. That is the whole point
of the split — before it, every module shared one history in `public`, so a new `V2.7` sorted below
the `V6.1` other modules had already applied and Flyway refused to start the application. It never
showed up in tests, because a Testcontainers database is empty and any order is in order there.

Two rules keep that working:

- **Extensions belong in the root range, not a module.** A module migrates inside its own schema, so
  `CREATE EXTENSION` there installs it where the runtime search path cannot see it — which is a
  silent failure, not an error. `pg_trgm` reached `V3.4` before this was understood; it lives in
  `V1.1` now.
- **Root migrations stay idempotent.** They run against `public`, which every schema can see, and the
  transition for an existing database may replay them.

Migrations are append-only after merge; fix forward rather than editing an applied one.

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

Checks have three deliberately distinct levels. Do not substitute a faster level for a required
later level, and do not pay for L2 repeatedly while editing.

### L0 — inner loop

Run the narrowest test that can prove the edit while iterating. It should normally take seconds:

```bash
cd apps/api
./gradlew :modules:<module>:test -PskipMutation
./gradlew :app:test --tests '*SomeIntegrationTest' -PskipMutation
```

### L1 — worker handoff

Before declaring `READY_FOR_LEAD_REVIEW`, run affected-module tests plus their formatting/static
checks, without mutation testing:

```bash
./scripts/check-scoped.sh module <module>
./scripts/check-scoped.sh app-test '*SomeIntegrationTest'
```

Use `./scripts/check-scoped.sh --help` for the supported commands. The worker reports the exact
commands and results to the lead. `-PskipMutation` is allowed only at L0 and L1.

### L2 — merge candidate

The full gate runs once per merge candidate in CI, in parallel governance, backend, and frontend
workflows. From the repository root, it remains available locally when CI is unavailable or a human
explicitly needs a pre-push reproduction:

```bash
./scripts/check.sh
```

Do not repeatedly run it during the edit/test loop. It runs three phases and stops at the first
failure:

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

`-PskipMutation` is never allowed for the L2 gate or CI (`.claude/rules/testing.md`).

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

- **Orphaned volumes, which is the one that fills the disk.** Removing the containers reclaims
  almost nothing: each leaked PostgreSQL leaves an *anonymous volume* behind, and those are what
  accumulate. A long session can leave a four-figure count of them holding a hundred gigabytes or
  more, at which point `initializationError` returns with `No space left on device` underneath.
  `docker ps` looks merely untidy while this happens — check the disk instead:

  ```bash
  df -h /                 # the number that matters
  docker system df        # look at RECLAIMABLE under Local Volumes
  ```

  Reclaim in this order, because a stopped container still holds a reference to its volume and
  pruning volumes first silently skips those:

  ```bash
  docker container prune -f    # 1. remove stopped containers, releasing their volume references
  docker volume prune -f       # 2. now the anonymous volumes are dangling and can go
  ```

  Neither needs `sudo`. `docker volume prune` without `--all` removes only *anonymous* volumes, so
  the named ones in `infra/docker/docker-compose.yml` (`geo_housing_postgres_data`,
  `geo_housing_evidence_data`) survive — confirm with
  `docker volume ls -f dangling=true | grep -v '^[0-9a-f]\{64\}$'`, which should list nothing before
  you prune.

  Observed on 2026-07-29 with Docker 29.6.1, cgroup v2 and the systemd cgroup driver: 113 orphaned
  containers left 264 orphaned proxies holding 269 ports. Prevention is cheaper than recovery — let
  the gate finish rather than killing it mid-run.

- **`NoSuchFileException: .../test-results/test/binary/in-progress-results-generic.bin`.** Not a test
  failure — an interrupted run left Gradle's test bookkeeping inconsistent. Remove
  `apps/api/app/build/test-results` and re-run.

- **A green `compileJava` that did not compile your new file.** Observed on 2026-08-04: `:app:test`
  failed a scenario with a 500 because a newly added `@RestControllerAdvice` was never registered.
  The cause was not the code — incremental compilation had skipped the file, and
  `:app:compileJava` still reported `BUILD SUCCESSFUL`. When a class you just added behaves as
  though it does not exist, check that it does:

  ```bash
  ls apps/api/app/build/classes/java/main/com/example/geohousing/app/<package>/
  ./gradlew :app:compileJava --rerun-tasks   # if it is missing
  ```

  A successful build is not evidence that a new class was produced.

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

# Task handoff

## Objective

Plan 020: one shared PostGIS container and database per test JVM, with every application table
truncated before each test class, to cut cold `:app` test time and remove the dead-pool shutdown
warnings, without weakening isolation, endpoint coverage or PITest thresholds.

## Active branch

`perf/020-shared-test-postgres`, based on `origin/main` at `f51d666`; `c8cc682` (MinIO image
replacement) fast-forwarded onto it by the founder 2026-11-13. Not pushed.

## Related issue or plan

`docs/plans/020-test-gate-performance.md` (Active; 020-A implemented 2026-11-13).

## Current status

020-A: runner L1 green and fresh Claude review `READY_TO_MERGE` with no blocking findings
(2026-11-14); committed locally on this branch with plan 024. Not pushed; no PR yet. Awaiting L2 in
CI and the human merge decision. 020-B not started.

## Completed work

- Inventory of container lifecycle and data cleanup across `apps/api/app/src/test` (recorded in the
  plan's "Current system").
- Isolation, wiring, connection-budget and lifecycle decisions recorded in the plan.
- Chunks 020-A (harness, baseline, pilot) and 020-B (remaining classes, docs) specified for a
  shared database with a catalogue-driven truncate before each class; 020-C withdrawn.
- Found the two scheduled verification sweeps (`VerificationExpiryJob`, `EvidenceRetentionJob`,
  `PT15M`, no initial delay) that would write to the shared database from cached contexts; the
  harness overrides both to `PT24H`.
- 020-A implementation:
  - `SharedPostgis`: one lazily started `postgis/postgis:18-3.6` container per JVM
    (`asCompatibleSubstituteFor("postgres")`), command
    `postgres -c max_connections=200 -c fsync=off -c synchronous_commit=off -c full_page_writes=off`,
    and a `register(DynamicPropertyRegistry)` that wires the datasource, Hikari
    `maximum-pool-size=5` / `minimum-idle=0`, and both sweep delays to `PT24H`.
  - `SharedPostgis.ResetBeforeClass`: `BeforeAllCallback` that truncates every ordinary table in
    every non-system schema in one `TRUNCATE … RESTART IDENTITY CASCADE`, excluding
    `flyway_schema_history*` and extension-owned tables (`pg_depend.deptype = 'e'`).
  - `SharedPostgisResetTest`: migrates with the same per-module Flyway arrangement the app uses and
    proves rows are removed from every module, history and `public.spatial_ref_sys` survive, a
    table created after the first reset is discovered by the next reset, and two `register` calls
    resolve to the same container/port.
  - Pilot classes `PropertySearchIntegrationTest`, `LastAdministratorConcurrencyIntegrationTest`
    and `EvidenceEndpointIntegrationTest` now use `@ExtendWith(SharedPostgis.ResetBeforeClass.class)`
    and `SharedPostgis.register(registry)`; the evidence class keeps its MinIO `@Testcontainers`
    setup. The concurrency class uses `register(registry, 12)` and its isolation comment was updated;
    no other test behaviour changed.
  - The `SharedPostgis` javadoc was rewrapped to the google-java-format 1.28.0 layout. Plan 020's
    stale 5-connection-pool wording was corrected to the implemented 12-connection override.

## Remaining work

- Rerun the 020-A L1 gate outside the worker sandbox and record the authoritative result.
- Lead review of the pilot and the `@ExtendWith` ordering dependency.
- 020-B only after 020-A review.

## Decisions made

See the plan's Decisions table. Founder (2026-11-08): shared database; `fsync`,
`synchronous_commit` and `full_page_writes` off; Cucumber keeps its own container. Lead:
catalogue-driven `TRUNCATE … RESTART IDENTITY CASCADE` before each class (excluding Flyway history
and extension-owned tables), sweep delays `PT24H`, pools `maximum-pool-size=5`/`minimum-idle=0`,
`max_connections=200`, `MigrationHistorySplitIntegrationTest` keeps its own container.

Worker finding: JUnit 6.1.1 does not apply `@Order` to class-level `@ExtendWith` extension classes.
The reset extension is declared first on each pilot class, before `@SpringBootTest`, so JUnit
registers it before Spring's meta-annotated `SpringExtension` and its `beforeAll` truncates before
the context is created or reused. Compiled class files were inspected with `javap -v` to confirm
`@ExtendWith(SharedPostgis.ResetBeforeClass.class)` is the first class-level annotation in all
three pilots. A later annotation reorder would silently move the reset after context startup.

Lead decisions (2026-11-13, recorded in the plan's Decisions table): "one container" means one
shared container per test JVM, evidenced with `--max-workers=1`; the `:app` test-count target is
356, not the original 354 baseline.

## Assumptions

- No migration seeds rows and none defines triggers or rules (checked 2026-11-08), so truncating
  every non-history, non-extension table restores a freshly migrated state.
- No test changes container-level state (roles, settings, schemas) at runtime.
- A test JVM runs classes sequentially, so one shared database is safe with a before-class reset.

## Files changed

020-A (uncommitted): `SharedPostgis.java`, `SharedPostgisResetTest.java` (new, `app/support/`),
`PropertySearchIntegrationTest.java`, `LastAdministratorConcurrencyIntegrationTest.java`,
`EvidenceEndpointIntegrationTest.java`, this handoff, plan 020 (decisions, `ai-l1` block, progress).

## Commands run

- Read-only inventory with `grep`/`rg` over `apps/api/app/src/test`, the module migrations,
  `application.yml`, `ModuleMigrationConfiguration`, the `java-conventions` plugin,
  `CONTRIBUTING.md`, ADR-0009 and `.github/workflows`.
- `javac @/tmp/javac-args.txt` over the five changed test sources, against the repository build
  outputs and cached dependency jars: exit 0. Only the pre-existing deprecation note for
  `EvidenceEndpointIntegrationTest` was emitted.
- `git diff --check`: clean.
- `git diff` on the pilot classes: only the PostGIS declaration and registration changed; the
  pre-existing long MinIO lines were deliberately left untouched.
- Checkstyle 10.24.0 with `apps/api/config/checkstyle/checkstyle.xml` over the five changed Java
  files: exit 0 (`Audit done.`).
- google-java-format 1.28.0 (the Spotless 8.8.0 default) with `--skip-reflowing-long-strings
  --dry-run --set-exit-if-changed` over every Java file under
  `apps/api/app/src/test/java`: exit 0 after the javadoc wrap fix (no pending formatting change).
- 2026-11-14 worker re-verification: `javac` over the five changed test sources against the
  repository build outputs, exit 0 (only the pre-existing deprecated-API note); Checkstyle 10.24.0
  over the five files, `Audit done.`; google-java-format 1.28.0 dry run over every test Java file,
  exit 0. These are not L1.
- `docker info`: exit 1, `permission denied while trying to connect to the docker API at
  unix:///var/run/docker.sock`.
- `GRADLE_USER_HOME=/tmp/gh-gradle-home ./gradlew --offline --no-daemon --stacktrace -q help`:
  exit 1, `Could not create service of type FileLockContentionHandler … Could not determine a
  usable wildcard IP for this machine`.

## Tests and verification

**020-A cold baseline** — 2026-11-13, lead, outside the worker sandbox, `apps/api` on unmodified
`c8cc682`, no Testcontainers running beforehand (script `.ai/baseline-020/run.sh`, log
`.ai/baseline-020/gradle.log`, both ignored):

| Metric | Value |
| --- | --- |
| Command | `./gradlew check --rerun-tasks --no-build-cache` |
| Result | BUILD SUCCESSFUL, 97 tasks executed |
| Wall clock | 1m 41s (101 s) |
| `:app` tests | 354, 0 failures, 0 errors, 0 skipped |
| `:modules:verification` tests | 80, 0 failures |
| PostGIS container starts (`docker events --filter image=postgis/postgis:18-3.6 --filter event=start`) | 41 |

An earlier attempt on `f51d666` failed (8m 3s) because the plan-021 image
`quay.io/minio/minio@sha256:14cea…8936e` now returns `401` from quay.io; its numbers are not a
baseline.

**020-A L1 — not run by this worker.** The sandbox blockers above prevent Gradle and Testcontainers.
The exact commands are in "Runner-owned L1" below. No authoritative claim is made here that the
pilot classes pass together or that one PostGIS container starts per JVM.

**Discovered outside-sandbox artifacts (not worker-run).** The ignored `.ai/baseline-020/` directory
contains a prior loop-runner attempt whose logs and `docker events` captures are useful but not
authoritative:

| Evidence | Observed |
| --- | --- |
| `l1-run-1.log`, `l1-run-2.log` (combined four-class filter, `--rerun-tasks`) | BUILD SUCCESSFUL each, 23 s / 22 s Gradle time, 4 PostGIS starts each |
| `l1-run-1jvm.log` (combined filter, `--max-workers=1`) | BUILD SUCCESSFUL, 23 s Gradle time, 2 PostGIS starts |
| `l1-solo-*.log` (one class per invocation) | BUILD SUCCESSFUL for all four classes |
| `l1-status.txt` shutdown-warning lines | 0 matching `Failed to validate connection` / `Connection refused` lines in the combined logs |
| `l1-lint.log` | Incomplete: stops at `:app:spotlessJava`; lint success is not proven |

The test totals in `l1-status.txt` are cumulative/stale XML summaries (`tests=103` for the
four-class filter and 80-91 for single-class filters), so they must not be used as test-count
evidence. The runner's clean `--rerun-tasks` results are the only valid L1 evidence.

**Compile/format evidence (not L1).** All five changed test sources compile with `javac`; the
compiled pilot classes were additionally inspected with `javap -v` to confirm the reset extension is
declared first. Checkstyle passes on the five files and the post-fix google-java-format dry run over
the test tree reports no pending change. These do not replace the L1 test and Task-level
`spotlessCheck`/`checkstyleTest` run.

## Known failures

None observed in the implementation. L1 behavior is unverified by this worker because the test
command could not start; the discovered artifacts are not authoritative and lint did not finish.

## Risks and unresolved questions

- **Test count.** Resolved by the lead 2026-11-13: the target is 356, i.e. the 354 baseline plus
  the two new `SharedPostgisResetTest` tests. The full-count comparison still needs the runner's
  clean `:app` result.
- **Ordering dependency.** The reset-before-context guarantee depends on `@ExtendWith` being the
  first class-level annotation. JUnit 6 does not apply `@Order` here; the harness javadoc and this
  handoff record the constraint.
- **Unverified runtime behavior.** The reset test's insert fixtures match the current NOT NULL and
  CHECK constraints by inspection, but only the L1 run can prove they pass against a migrated
  PostGIS database.
- **Shutdown warnings.** The container is intentionally never stopped by an extension; Ryuk owns
  cleanup. The discovered combined logs contain zero matching Hikari validation/refused lines, but
  the runner's final shutdown log remains authoritative.
- **Fork distribution.** Resolved by the lead 2026-11-13: the criterion is one shared container per
  test JVM, to be evidenced with `--max-workers=1`. `geohousing.java-conventions.gradle.kts` leaves
  `maxParallelForks` at `availableProcessors / 5` (4 on the 20-core workstation) with no
  `forkEvery`, so default runs may start one container per fork; that is accepted. The runner must
  still record the `--max-workers=1` start count.

## Runner-owned L1

```bash
cd apps/api && ./gradlew :app:test --tests '*SharedPostgisResetTest' --tests '*PropertySearchIntegrationTest' --tests '*LastAdministratorConcurrencyIntegrationTest' --tests '*EvidenceEndpointIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*SharedPostgisResetTest' --tests '*PropertySearchIntegrationTest' --tests '*LastAdministratorConcurrencyIntegrationTest' --tests '*EvidenceEndpointIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*SharedPostgisResetTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*PropertySearchIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*LastAdministratorConcurrencyIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*EvidenceEndpointIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:spotlessCheck :app:checkstyleTest
```

The runner must record the combined-run container start count with `docker events` and the final
`:app` test count from a clean XML directory. The accepted target is one shared container per test
JVM (2 starts with `--max-workers=1`: the shared container plus Cucumber's) and 356 `:app` tests.

## Recommended next action

Push `perf/020-shared-test-postgres` and open one PR containing 020-A and plan 024 (founder
decision 2026-11-14); L2 runs in CI; the founder decides the merge. Before 020-B: fold in the
review's low findings (drop the reset test's temporary table in `finally`; guard the
`@ExtendWith`-before-`@SpringBootTest` order across the ~36 migrated classes).

## Last updated

2026-11-14

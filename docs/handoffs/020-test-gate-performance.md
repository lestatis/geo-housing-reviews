# Task handoff

## Objective

Plan 020: one shared PostGIS container and database per test JVM, with every application table
truncated before each test class, to cut cold `:app` test time and remove the dead-pool shutdown
warnings, without weakening isolation, endpoint coverage or PITest thresholds.

## Active branch

`perf/020-b-migrate-classes`, created from `main` at `c654440` (020-A and plan 024 merged in #6).
Not pushed.

## Related issue or plan

`docs/plans/020-test-gate-performance.md` (Active; 020-B implementation complete 2026-11-14).

## Current status

020-B: loop `READY_FOR_HUMAN_MERGE` (`.ai/runs/implementation-loop.KuKd5I`), lead cold L2 green
2026-11-14 — 77 s vs 101 s baseline, 41 → 6 PostGIS starts, 359 `:app` tests, 0 shutdown
warnings, PITest passing. Uncommitted on `perf/020-b-migrate-classes`; awaiting the founder's
commit/PR decision.

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
- 020-B implementation:
  - All 36 remaining standard `:app` classes now declare
    `@ExtendWith(SharedPostgis.ResetBeforeClass.class)` before `@SpringBootTest` and call
    `SharedPostgis.register(registry)` from a `@DynamicPropertySource`; their per-class
    `PostgreSQLContainer` fields, `@ServiceConnection` imports and now-unused `@Testcontainers`
    declarations are gone. `EvidencePersistenceIntegrationTest` keeps `@Testcontainers` for MinIO
    and adds the shared registration to its existing property method. No test gained local cleanup
    and no other behaviour changed.
  - `MigrationHistorySplitIntegrationTest` and `acceptance/AcceptanceWorld` still construct their
    own PostgreSQL containers; `SharedPostgis` itself is the third and only other constructor.
  - `SharedPostgisResetTest` drops `properties.future_migration_table` in a `finally` block, so a
    failed assertion cannot leave it behind in the shared database.
  - New `SharedPostgisWiringArchitectureTest` (ArchUnit plus two JUnit fixture tests) rejects a
    `SharedPostgis.register` caller that lacks the reset extension or declares it after
    `@SpringBootTest`. The fixtures deliberately exercise both failure modes without editing a real
    test class. The rule skips the manual `SharedPostgisResetTest`, which exercises `register`
    directly and creates no Spring context.
    The production rule also ignores `SharedPostgis` itself, whose delegating `register` looks like
    a self-call to ArchUnit; the fixture checks use a separate rule because the production rule
    excludes `*Fixture` classes.
  - `CONTRIBUTING.md` now describes the shared container, the before-class reset, the `PT24H`
    sweep-delay override, and the two classes that keep their own container and why.

## Remaining work

- Run the 020-B L1 block outside the worker sandbox and record the authoritative result.
- Lead review of the 36-class migration, the reset cleanup and the wiring/order guard.
- Lead: edit the forking comment in `buildSrc/.../geohousing.java-conventions.gradle.kts`, then run
  the cold L2 `check` and record timings, container starts, test count and shutdown warnings.

## Decisions made

See the plan's Decisions table. Founder (2026-11-08): shared database; `fsync`,
`synchronous_commit` and `full_page_writes` off; Cucumber keeps its own container. Lead:
catalogue-driven `TRUNCATE … RESTART IDENTITY CASCADE` before each class (excluding Flyway history
and extension-owned tables), sweep delays `PT24H`, pools `maximum-pool-size=5`/`minimum-idle=0`,
`max_connections=200`, `MigrationHistorySplitIntegrationTest` keeps its own container.

Worker finding: JUnit 6.1.1 does not apply `@Order` to class-level `@ExtendWith` extension classes.
The reset extension is declared first on every shared-database class, before `@SpringBootTest`, so
JUnit registers it before Spring's meta-annotated `SpringExtension` and its `beforeAll` truncates
before the context is created or reused. A later annotation reorder would silently move the reset
after context startup; `SharedPostgisWiringArchitectureTest` now fails on that regression.

Lead decisions (2026-11-13, recorded in the plan's Decisions table): "one container" means one
shared container per test JVM, evidenced with `--max-workers=1`; the `:app` test-count target is
now an expected 359 (354 baseline + 2 reset tests + 3 guard tests), not the original 354 baseline.

## Assumptions

- No migration seeds rows and none defines triggers or rules (checked 2026-11-08), so truncating
  every non-history, non-extension table restores a freshly migrated state.
- No test changes container-level state (roles, settings, schemas) at runtime.
- A test JVM runs classes sequentially, so one shared database is safe with a before-class reset.
- The wiring guard inspects declared annotation order through reflection, which is the same order
  JUnit uses to register class-level extensions.

## Files changed

020-B (uncommitted):

- `app/`: `GeoHousingApplicationIntegrationTest`, `OpenApiContractIntegrationTest`,
  `SwaggerEndpointIntegrationTest`.
- `app/audit/`: `AuditPagingIntegrationTest`.
- `app/identity/`: `AdminAccountEndpointIntegrationTest`, `IdentityMigrationIntegrationTest`,
  `IdentityPersistenceIntegrationTest`, `MeEndpointIntegrationTest`, `SelfServiceEndpointIntegrationTest`.
- `app/metrics/`: `AdminMetricsIntegrationTest`.
- `app/moderation/`: `ModerationFlowIntegrationTest`, `ModerationMigrationIntegrationTest`,
  `ModerationPersistenceIntegrationTest`, `ModerationReviewGatewayIntegrationTest`,
  `StaleModerationWriteIntegrationTest`.
- `app/properties/`: `AdminPropertyEndpointIntegrationTest`, `PropertiesMigrationIntegrationTest`,
  `PropertyCreationFlowIntegrationTest`, `PropertyDuplicateDetectionIntegrationTest`,
  `PropertyEndpointIntegrationTest`, `PropertyPersistenceIntegrationTest`.
- `app/reviews/`: `AdminReviewEndpointIntegrationTest`, `HelpfulSignalEndpointIntegrationTest`,
  `HelpfulSignalPersistenceIntegrationTest`, `PropertyLookupIntegrationTest`,
  `ReviewEndpointIntegrationTest`, `ReviewPersistenceIntegrationTest`,
  `ReviewVerificationProjectionIntegrationTest`, `ReviewsMigrationIntegrationTest`.
- `app/verification/`: `EvidencePersistenceIntegrationTest`, `ScheduledSweepWiringTest`,
  `VerificationEndpointIntegrationTest`, `VerificationMigrationIntegrationTest`,
  `VerificationPersistenceIntegrationTest`, `VerificationRevocationIntegrationTest`,
  `VerificationToReviewProjectionIntegrationTest`.
- `app/support/SharedPostgisResetTest.java` (cleanup fix);
- new `app/architecture/SharedPostgisWiringArchitectureTest.java`;
- `CONTRIBUTING.md`, this handoff, plan 020.

## Commands run

- Read-only inventory with `grep`/`rg` over `apps/api/app/src/test`, the module migrations,
  `application.yml`, `ModuleMigrationConfiguration`, the `java-conventions` plugin,
  `CONTRIBUTING.md`, ADR-0009 and `.github/workflows`.
- `javac @/tmp/javac-args-020b.txt` over the 38 changed/added test sources, against the repository
  build outputs and cached dependency jars: exit 0.
- google-java-format 1.28.0 (the Spotless 8.8.0 default) with `--skip-reflowing-long-strings
  --dry-run --set-exit-if-changed` over the 38 changed/added Java files: exit 0.
- Checkstyle 10.24.0 with `apps/api/config/checkstyle/checkstyle.xml` over the 38 changed/added
  Java files: exit 0 (`Audit done.`).
- `git diff --check`: clean.
- After the first runner L1: compiled `SharedPostgisWiringArchitectureTest` with `javac` against the
  cached test classpath and ran it through the JUnit Platform Launcher API: 3 tests, 0 failures.
  google-java-format 1.28.0 (`--skip-reflowing-long-strings`) and Checkstyle 10.24.0 pass on the
  changed file.
- `rg -l 'new PostgreSQLContainer' apps/api/app/src/test`: only `SharedPostgis.java`,
  `MigrationHistorySplitIntegrationTest.java` and `acceptance/AcceptanceWorld.java`.
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

**020-B L1 — not run by this worker.** The sandbox blockers above prevent Gradle and Testcontainers.
The exact commands are in "Runner-owned L1" below. No authoritative claim is made here that the
36 migrated classes pass together, that the guard tests pass under Gradle, or that one PostGIS
container starts per JVM.

**020-A outside-sandbox artifacts (not worker-run).** The ignored `.ai/baseline-020/` directory
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

**020-B compile/format evidence (not L1).** The 38 changed/added test sources compile with `javac`;
google-java-format 1.28.0 and Checkstyle 10.24.0 pass on those files, and `git diff --check` is
clean. These do not replace the L1 test and Task-level `spotlessCheck`/`checkstyleTest` run.

## Known failures

None observed in the implementation. L1 behavior is unverified by this worker because Gradle and
Docker cannot start in the sandbox; the discovered 020-A artifacts are not authoritative.

## Risks and unresolved questions

- **Test count.** The target is now 359: 354 baseline + 2 `SharedPostgisResetTest` tests + 3
  `SharedPostgisWiringArchitectureTest` tests (one ArchUnit rule and two JUnit fixture tests). The
  full-count comparison still needs the runner's clean `:app` result.
- **Ordering dependency.** The reset-before-context guarantee depends on `@ExtendWith` being the
  first class-level annotation. JUnit 6 does not apply `@Order` here; the harness javadoc and
  `SharedPostgisWiringArchitectureTest` record and enforce the constraint.
- **Guard implementation.** The guard reads declared annotation order through reflection. That is
  the same order JUnit uses for class-level extension registration, but a future JUnit change to
  that ordering would need both the harness and the guard revisited.
- **Unverified runtime behavior.** The reset test's insert fixtures and all 36 migrated classes
  match the current schema by inspection, but only the L1 run can prove they pass against a
  migrated and shared PostGIS database.
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
cd apps/api && ./gradlew :app:test -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*SharedPostgisWiringArchitectureTest' --tests '*SharedPostgisResetTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:spotlessCheck :app:checkstyleTest
```

The runner must record the combined-run container start count with `docker events` and the final
`:app` test count from a clean XML directory. The accepted target is one shared container per test
JVM plus the two accepted exceptions (3 starts with `--max-workers=1`: the shared container,
`MigrationHistorySplitIntegrationTest`'s own container and Cucumber's) and an expected 359 `:app`
tests.

## Recommended next action

Founder: commit, push and open the 020-B PR; L2 in CI; merge decision. Optional follow-up from the
020-B review: make the wiring guard exclude `SharedPostgis`/`SharedPostgisResetTest` by exact class
and fixtures by enclosing class rather than by simple name. After merge, close plan 020.

## Last updated

2026-11-14

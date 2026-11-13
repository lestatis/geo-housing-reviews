# Plan 020 — reuse PostgreSQL integration-test infrastructure

Status: Active — 020-A merged in #6 (2026-11-14); 020-B implemented on
`perf/020-b-migrate-classes` 2026-11-14, runner-owned L1 and lead L2/review pending
Owner: Claude (drafted by Human 2026-09-21; design revised by Claude 2026-11-08)
Related issue: None
Last updated: 2026-11-14
Base ref: `origin/main` at `f51d666`
Target scope: `apps/api/app/src/test/**` (harness and test classes), plus the comment in
`apps/api/buildSrc/src/main/kotlin/geohousing.java-conventions.gradle.kts` and the "slow gate"
paragraph of `CONTRIBUTING.md`. No production code, no migration, no CI workflow change.

## Objective

Reduce cold integration-test wall-clock time by reusing PostgreSQL test infrastructure across test
classes without weakening database isolation, endpoint coverage, or mutation-testing thresholds.

## Acceptance criteria

Evidence for every box: lead L2 on 2026-11-14 (020-B working tree on `c654440`), table under
"Results".

- [x] Record a reproducible cold baseline using `./gradlew check --rerun-tasks --no-build-cache`.
- [x] Replace per-class PostgreSQL startup with a shared JVM/test-harness lifecycle where the test
  architecture permits it. All 36 standard classes migrated; only the two recorded exceptions remain.
- [x] Isolate each test class through schema/database reset or equivalent deterministic cleanup;
  no test may depend on another class's data or execution order. Full `:app` suite green in L1 and
  in the cold L2.
- [x] Demonstrate that many integration classes do not cause the same number of PostgreSQL starts:
  41 → 6.
- [x] Record before/after timings and the test-count/container-start evidence in this plan or its
  handoff.
- [x] Keep PITest thresholds and the full endpoint/Cucumber suites enabled; `./gradlew check` passes
  without `-PskipMutation` (thresholds untouched; no `build.gradle.kts` changed).

Added by the 2026-11-08 design:

- [x] At most one shared PostgreSQL container starts per test JVM (fork) for the `:app` JUnit
  suite; the only other PostgreSQL containers are the two recorded exceptions. 6 starts = 4 forks +
  `MigrationHistorySplitIntegrationTest` + Cucumber's `AcceptanceWorld`.
- [x] Every test class starts with every application table empty, enforced by the harness before
  the class's first test, not by the class itself. Every shared-database class declares the reset
  extension before `@SpringBootTest`; `SharedPostgisWiringArchitectureTest` enforces that order.
- [x] Test JVM shutdown no longer logs `HikariPool-N - Failed to validate connection` /
  `Connection to localhost:<port> refused`: 0 matching lines in the cold L2 log.
- [x] The `:app` test count after the change equals the baseline count plus the tests this plan
  adds (`SharedPostgisResetTest`: 2; `SharedPostgisWiringArchitectureTest`: one ArchUnit rule plus
  two JUnit tests): 359 = 354 + 5, 0 failures, 0 skipped.

## Results

| Measure (cold `./gradlew check --rerun-tasks --no-build-cache`, this workstation) | Before (baseline on `c8cc682`) | After (020-B) |
| --- | --- | --- |
| Wall clock | 1m 41s (101 s) | 1m 16s (77 s), −24% |
| PostGIS container starts | 41 | 6 |
| `:app` tests | 354, 0 failures | 359, 0 failures |
| Shutdown-warning lines | present (motivating run, 2026-11-08) | 0 |
| PITest | passes | passes, thresholds unchanged |

Note the baseline already ran with 4 forks; the saving is container startup and per-class migration.

## Non-goals

- Removing PITest, lowering a mutation threshold, or skipping integration/Cucumber tests.
- Broadly rewriting passing endpoint tests solely to change their test framework (the ADR-0009
  on-touch Cucumber migration continues independently).
- Changing production PostgreSQL configuration, production datasource settings, production
  scheduling, or module boundaries.
- Sharing Spring application contexts between classes that do not share them today.
- MinIO containers (three classes): they are not the dominant cost and stay per class.
- Testcontainers reuse across JVMs/builds (`withReuse(true)`): it outlives the build, needs a
  user-level `~/.testcontainers.properties`, and is what leaks containers on this machine.
- Moving the Cucumber suite onto the shared container (founder decision: it keeps its own).

## Current system

Inventory taken 2026-11-08 on `origin/main` (`f51d666`):

- **41** `:app` test classes declare their own PostGIS container
  (`postgis/postgis:18-3.6`, `asCompatibleSubstituteFor("postgres")`). 39 use the identical
  pattern `@Container @ServiceConnection static final PostgreSQLContainer POSTGRES = …`. Two are
  special:
  - `MigrationHistorySplitIntegrationTest` starts its container in a static initialiser and runs a
    hand-configured Flyway against `POSTGRES.getJdbcUrl()` before Spring starts, to reproduce the
    pre-split migration history (every module in one `public` history).
  - `acceptance/AcceptanceWorld` (Cucumber) starts its container manually because Cucumber, not the
    JUnit extension, owns that class's lifecycle. One Spring context serves every scenario.
- Two classes (`EvidencePersistenceIntegrationTest`, `EvidenceEndpointIntegrationTest`) also have a
  MinIO container and an existing `@DynamicPropertySource`.
- **Isolation today is a side effect of the container per class.** Only three classes delete data
  themselves (`PropertySearchIntegrationTest`, `AuditPagingIntegrationTest`,
  `MigrationHistorySplitIntegrationTest`); the other 38 assume they start from an empty, freshly
  migrated database.
- Migrations: Spring's Flyway auto-configuration is off; `ModuleMigrationConfiguration` runs one
  Flyway instance per module, each with its own schema and `flyway_schema_history` table, on every
  context start. `root/V1__init.sql` creates the `postgis` extension (which owns
  `public.spatial_ref_sys`) and `V1.1` creates `pg_trgm`. **No migration inserts rows**, so there is
  no migration-seeded reference data that a reset could wipe. No migration defines a trigger or
  rule, so nothing blocks `TRUNCATE`.
- No `ApplicationRunner`, `CommandLineRunner`, `@PostConstruct` or `ApplicationReadyEvent` writes data
  at startup.
- **Background writers:** the verification module enables scheduling. `VerificationExpiryJob` and
  `EvidenceRetentionJob` run `@Scheduled(fixedDelayString = …)` with `PT15M` defaults and no initial
  delay: each fires once as its context starts, then every 15 minutes, in every cached context.
  With one database per class that is harmless; with a shared database, a cached earlier context
  could sweep a later class's rows. `ScheduledSweepWiringTest` asserts only that the delay is
  positive.
- Datasource: `application.yml` has only url/username/password; Hikari runs with defaults
  (maximum pool 10, minimum idle = maximum), so every context opens 10 connections eagerly.
- Forking: `maxParallelForks = (availableProcessors / 5).coerceIn(1, 4)`. CI (`ubuntu-latest`,
  4 vCPU) therefore runs **one** test JVM; a 20-core workstation runs four. JUnit runs classes
  sequentially within a JVM (no `junit-platform.properties` parallelism).
- Spring's test context cache keeps every context until JVM exit (LRU limit 32). Because each
  class has its own container, no context is reused between classes, and at shutdown each cached
  pool keeps validating connections to a container Testcontainers already stopped — the
  `Failed to validate connection … Connection refused` noise and the slow JVM exit seen on
  2026-11-08.
- `CONTRIBUTING.md` ("If the gate is unexpectedly slow or fails oddly") and ADR-0009 both name
  per-class container startup as the dominant cost.

## Decisions

| Decision | Choice | Reason | Revisit when |
| --- | --- | --- | --- |
| Performance metric | Cold `check` with rerun tasks and no build cache | Avoids reporting an up-to-date build as a speed improvement | Build topology changes |
| Quality boundary | Preserve PITest and endpoint suites | The bottleneck is container lifecycle, not the quality gate | Evidence shows another dominant cost |
| Isolation (**founder decision, 2026-11-08**) | **One shared PostGIS container and one shared database per test JVM; the harness truncates every application table before each test class** | Founder preference over the database-per-class alternative. Also removes per-class Flyway migration cost: after the first class the module migrations find the schema current and only validate. The trade-off accepted: isolation now depends on the reset being complete, so the reset is computed from the catalogue rather than listed by hand, and is itself tested | A class fails only because of state the reset does not cover |
| Reset scope | `TRUNCATE … RESTART IDENTITY CASCADE` over every ordinary table in every non-system schema, **except** each `flyway_schema_history` and every extension-owned table (`pg_depend.deptype = 'e'`, e.g. `public.spatial_ref_sys`) | Discovered from `pg_catalog` at reset time, so a table added by a future migration is reset automatically. Keeping the history tables means migrations stay applied; keeping extension tables keeps PostGIS working. No migration seeds rows, so nothing else needs preserving | A migration starts seeding reference data — then that table must be excluded or re-seeded, and the harness test must say so |
| Reset timing | A JUnit `BeforeAllCallback` (plain JDBC, independent of Spring) that runs before the class's Spring context is created or reused | Matches today's guarantee (empty at class start, data accumulates within the class) and does not depend on Spring listener ordering | — |
| Background sweeps | The harness registers `verification.expiry.sweep-fixed-delay=PT24H` and `verification.evidence.retention.sweep-fixed-delay=PT24H` for every class it wires | A cached earlier context then never sweeps during the run. The one sweep each context fires on start is unchanged from today and runs against that class's own (just reset) data. `ScheduledSweepWiringTest` still sees a positive delay | A test needs a sweep to fire on its own schedule |
| Wiring | Per class: `@ExtendWith(SharedPostgis.ResetBeforeClass.class)` plus a `@DynamicPropertySource` calling `SharedPostgis.register(registry)`, replacing `@Container @ServiceConnection` | Explicit and greppable, no `spring.factories` customizer or base class, merges with the two existing `@DynamicPropertySource` methods. A class that needs a larger pool calls the `register(registry, maximumPoolSize)` overload. Each class keeps its own context cache key, so no context is shared that is not shared today | A third wiring pattern appears |
| Connection budget | Test pools `maximum-pool-size=5`, `minimum-idle=0`; container `max_connections=200` | One CI JVM can hold up to 32 cached contexts, all on one database now. Default Hikari (10 eager each) would need 320 connections against PostgreSQL's default 100. With `minimum-idle=0` a pool holds only connections in use plus idle ones for `idle-timeout`; 5 covers ordinary classes. **Revised 2026-11-13:** `LastAdministratorConcurrencyIntegrationTest` races six callers, and each refused caller opens a second connection for its `REQUIRES_NEW` audit row, so a pool of 5 deadlocks (L1 timeout). `register(registry, maximumPoolSize)` lets that class use 12; the budget stays far below 200 | Another test needs more than 5 concurrent connections — it passes its own size |
| Durability (**founder decision, 2026-11-08**) | Shared container runs with `fsync=off`, `synchronous_commit=off`, `full_page_writes=off` | Test data is disposable; these remove disk-flush waits. They do not change transaction semantics, locking or visibility, so the concurrency test is unaffected | — |
| Container lifecycle | Started once per JVM by the harness, never stopped explicitly; Ryuk removes it at JVM exit | Standard Testcontainers singleton pattern; the database stays reachable until every cached context has closed, which removes the shutdown noise | — |
| Cucumber (**founder decision, 2026-11-08**) | `AcceptanceWorld` keeps its own container | One container; Cucumber owns that class's lifecycle | The suite grows enough to matter |
| Acceptance wording (lead decision, 2026-11-13) | "One container" means one shared container per test JVM, shown with `--max-workers=1`; the test-count target is baseline + tests this plan adds (expected 359: 354 baseline + 2 reset tests + 3 guard tests) | Gradle forks up to 4 test JVMs (`maxParallelForks`), and the design is a per-JVM singleton; the count criterion guards against lost tests, not the required new ones. Raised by the 020-A worker (`.ai/runs/implementation-loop.f95DMb/worker-0.json`) | Fork configuration changes |
| Migration-history split test (lead decision, founder-confirmed 2026-11-08) | `MigrationHistorySplitIntegrationTest` keeps its own container | It must meet a database whose history was written the pre-split way (every module in one `public` history). The shared database already holds the split histories, and resetting rows cannot undo that. Truncating history tables to fake it would break every class that runs after it | Never, while that test exists |

## Implementation steps

Chunks are delivered in order; each is one bounded worker packet. 020-B starts only after 020-A is
reviewed.

### 020-A — harness, baseline and pilot

Files:

- new `apps/api/app/src/test/java/com/example/geohousing/app/support/SharedPostgis.java`
- new `apps/api/app/src/test/java/com/example/geohousing/app/support/SharedPostgisResetTest.java`
- pilot classes, container declaration only:
  `properties/PropertySearchIntegrationTest.java`,
  `identity/LastAdministratorConcurrencyIntegrationTest.java`,
  `verification/EvidenceEndpointIntegrationTest.java`
- this plan and `docs/handoffs/020-test-gate-performance.md` (baseline numbers)

Behaviour:

1. Before any change, measure the cold baseline (`time ./gradlew check --rerun-tasks
   --no-build-cache` from `apps/api`) and record wall-clock time, the `:app` test count (sum of
   `tests=` in `app/build/test-results/test/*.xml`) and the number of PostGIS containers started
   (`docker events --filter image=postgis/postgis:18-3.6 --filter event=start`, counted for the
   run).
2. `SharedPostgis` holds one lazily started `PostgreSQLContainer` per JVM
   (`postgis/postgis:18-3.6`, `asCompatibleSubstituteFor("postgres")`, command
   `postgres -c max_connections=200 -c fsync=off -c synchronous_commit=off -c full_page_writes=off`).
   - `register(DynamicPropertyRegistry)` starts the container if needed and registers
     `spring.datasource.url`/`username`/`password`,
     `spring.datasource.hikari.maximum-pool-size=5` (an overload `register(registry, size)` sets
     another size; the concurrency pilot uses 12), `spring.datasource.hikari.minimum-idle=0`,
     and both sweep delays as `PT24H`.
   - `ResetBeforeClass` (a JUnit `BeforeAllCallback`) starts the container if needed and, over a
     plain JDBC connection, truncates every ordinary table in every non-system schema except
     `flyway_schema_history` tables and extension-owned tables, in one
     `TRUNCATE … RESTART IDENTITY CASCADE`. On a database with no application tables yet (the first
     class in the JVM) it does nothing.
3. `SharedPostgisResetTest` (plain JUnit, no Spring) migrates the shared database with the same
   per-module Flyway arrangement the app uses, then proves:
   - rows inserted into a table of each module schema are gone after a reset;
   - every `flyway_schema_history` table keeps its rows, and `public.spatial_ref_sys` keeps its rows;
   - a table created after the first reset (simulating a future migration) is also emptied by the
     next reset;
   - two `register` calls in one JVM point at the same mapped port (one container).
4. The three pilot classes replace their `@Container @ServiceConnection` PostGIS declaration with
   the extension and `@DynamicPropertySource static void database(DynamicPropertyRegistry r) {
   SharedPostgis.register(r); }`. The concurrency class uses `register(r, 12)` and updates its
   isolation comment; the evidence class adds the call to its existing method. No other behaviour
   in those classes changes.

Acceptance:

- the baseline is recorded before any test file changes;
- `SharedPostgisResetTest` and the three pilot classes pass together in one invocation (one JVM,
  so they share the database) and each on its own, and the combined run passes twice in a row with
  `--rerun-tasks`;
- run together in one test JVM (`--max-workers=1`), the pilot classes start exactly one shared
  PostGIS container (docker events evidence; Cucumber's `AcceptanceWorld` container, which runs in
  every filtered `:app:test`, is counted separately). With default forks it is one per test JVM;
- `LastAdministratorConcurrencyIntegrationTest` still demonstrates its race outcome with its
  12-connection pool and durability off;
- spotless and checkstyle pass.

L0: `./gradlew :app:test --tests '*SharedPostgisResetTest' -PskipMutation`.
L1: `./gradlew :app:test --tests '*SharedPostgisResetTest' --tests '*PropertySearchIntegrationTest'
--tests '*LastAdministratorConcurrencyIntegrationTest' --tests '*EvidenceEndpointIntegrationTest'
-PskipMutation` and `./gradlew :app:spotlessCheck :app:checkstyleTest`.

Runner-owned L1 (plan 024; the worker sandbox cannot run Gradle or Docker):

```ai-l1
cd apps/api && ./gradlew :app:test --tests '*SharedPostgisResetTest' --tests '*PropertySearchIntegrationTest' --tests '*LastAdministratorConcurrencyIntegrationTest' --tests '*EvidenceEndpointIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*SharedPostgisResetTest' --tests '*PropertySearchIntegrationTest' --tests '*LastAdministratorConcurrencyIntegrationTest' --tests '*EvidenceEndpointIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*SharedPostgisResetTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*PropertySearchIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*LastAdministratorConcurrencyIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*EvidenceEndpointIntegrationTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:spotlessCheck :app:checkstyleTest
```

The PostGIS container count is lead evidence (`docker events`), not part of the runner block.

Lead decision required if: a pilot class fails only under the shared database, the reset test
shows a table the catalogue query misses, the concurrency test needs more than 12 connections, or
the reset is slower than starting a container.

### 020-B — migrate the remaining classes

Revised 2026-11-14 after the 020-A review and plan 024; founder-approved 2026-11-14.

Files (worker):

- the other 35 standard `:app` classes declaring `PostgreSQLContainer`, plus
  `EvidencePersistenceIntegrationTest` (36 in all). `MigrationHistorySplitIntegrationTest` and
  `acceptance/AcceptanceWorld` are **not** changed;
- `app/support/SharedPostgisResetTest.java` (cleanup fix below);
- new `app/architecture/SharedPostgisWiringArchitectureTest.java`;
- the "slow gate" paragraph of `CONTRIBUTING.md`; this plan and its handoff.

Files (lead, after the loop): the forking comment in
`buildSrc/src/main/kotlin/geohousing.java-conventions.gradle.kts`. It is a build file on the
runner's protected list (plan 024), so a worker edit would stop the loop; the change is comment-only.

Behaviour:

- Standard classes: the same one-for-one replacement as the pilot (`@ExtendWith(SharedPostgis.
  ResetBeforeClass.class)` declared before `@SpringBootTest`, `@Container @ServiceConnection`
  PostGIS field replaced by `SharedPostgis.register(registry)` in a `@DynamicPropertySource`, or
  added to the class's existing one). Remove `@Testcontainers` only where no other `@Container`
  remains. Nothing else changes. No class gains its own cleanup to make it pass.
- `SharedPostgisResetTest` drops `properties.future_migration_table` in a `finally` block, so a
  failed assertion cannot leave it in the shared database (020-A review, low).
- `SharedPostgisWiringArchitectureTest` (ArchUnit, like `ModuleBoundaryArchitectureTest`) fails if
  a Spring test class calls `SharedPostgis.register` without declaring
  `@ExtendWith(SharedPostgis.ResetBeforeClass.class)`, or declares it after `@SpringBootTest` in
  its declared-annotation order (the order JUnit registers extensions in). The manual
  `SharedPostgisResetTest` is excluded because it exercises `register` directly and creates no
  Spring context. The guard must be shown to fail on deliberately misordered fixture classes
  inside the test, not by editing a real class (020-A review, low).
- Docs: `CONTRIBUTING.md` no longer says each class starts its own container; it describes the
  shared container, the reset before each class, the sweep-delay override, and the two classes that
  keep their own container and why.

Implementation status (2026-11-14): all 36 standard classes migrated one-for-one; the 37 changed
test sources plus the new guard compile with `javac`; `SharedPostgisResetTest` drops its temporary
table in `finally`; `SharedPostgisWiringArchitectureTest` rejects both a missing and a late reset
extension using fixture classes. `CONTRIBUTING.md` is updated. `grep -rln 'new
PostgreSQLContainer' apps/api/app/src/test` lists only the three intended files. After the first
runner L1 failed only in that guard, the rule now ignores the harness's own self-call and the
fixture checks use a dedicated rule that does not exclude fixtures; the three guard tests pass in a
local JUnit launcher run. L1 remains runner-owned.

Runner-owned L1 (plan 024):

```ai-l1
cd apps/api && ./gradlew :app:test -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*SharedPostgisWiringArchitectureTest' --tests '*SharedPostgisResetTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:spotlessCheck :app:checkstyleTest
```

Acceptance:

- `grep -rln 'new PostgreSQLContainer' apps/api/app/src/test` lists only `SharedPostgis`,
  `MigrationHistorySplitIntegrationTest` and `AcceptanceWorld`;
- the runner L1 above passes;
- lead L2, once: the cold `./gradlew check --rerun-tasks --no-build-cache` passes **without**
  `-PskipMutation`, with the `:app` test count equal to the expected 359 (354 baseline + 2 reset
  tests + 3 guard tests), and PITest thresholds unchanged;
- lead evidence: PostGIS container starts per run are at most the number of forks plus 2
  (the shared container in each fork, plus `MigrationHistorySplitIntegrationTest` and Cucumber via
  `docker events`), wall time is recorded against the 1m41s baseline, and the run log contains no
  `Failed to validate connection` / `Connection refused` warnings.

L0: `./gradlew :app:test --tests '<changed class>' -PskipMutation` per class (lead or runner; the
worker sandbox cannot run Gradle).

Lead decision required if: any class fails only under the shared database (stop and report the
class and the leaked state; do not add cleanup to that class), a class's container declaration does
not fit the one-for-one pattern, or the measured time does not improve.

### 020-C — withdrawn

The template-database optimisation applied only to the database-per-class design. With one shared
database, migrations run once per JVM, so there is nothing for it to save.

## Verification

- The baseline and final command use `--rerun-tasks --no-build-cache`.
- Run focused integration classes repeatedly and in changed order where Gradle permits.
- Run `./gradlew check` without `-PskipMutation` before review.
- Inspect failures for leaked schema/data and compare test count with the baseline.
- Count PostGIS container starts with `docker events` for the baseline and final runs.
- Read the final run's shutdown log for the Hikari validation/refused warnings.
- Privacy/security: test-only change; fixtures and credentials stay synthetic Testcontainers
  defaults. No production configuration, schedule or migration is touched.

## Risks and rollback/forward-fix

- **Incomplete reset (accepted with the founder decision).** A class could see another class's
  rows if the reset misses state. Mitigated by computing the table list from the catalogue at reset
  time and by `SharedPostgisResetTest`. State outside tables — sequences are covered by
  `RESTART IDENTITY`; roles, settings and schemas are created only by migrations — is not reset;
  a class that changes such state at runtime would leak it. None does today.
- **Order dependence.** A class that passes alone and fails after another class indicates leaked
  state; it stops the chunk for a lead decision rather than gaining local cleanup.
- **Background writers.** Covered by the `PT24H` sweep override; any new `@Scheduled` writer added
  later needs the same treatment, which `CONTRIBUTING.md` will say.
- **Blast radius.** A harness failure (container start, reset) now fails every class in that JVM.
  Acceptable: the same failure today fails them one by one.
- **Connection exhaustion.** Bounded by the pool settings and `max_connections=200`; a test needing
  more than 5 connections overrides the pool size in its own `@DynamicPropertySource`.
- **Fork distribution.** The Java conventions set `maxParallelForks` to
  `availableProcessors / 5` (4 on the 20-core workstation) and do not set `forkEvery`, so a
  filtered four-class run can be split across up to four test JVMs. The one-container-per-JVM
  guarantee still holds, but the 020-A "exactly one container for the combined pilot run" evidence
  needs a single fork (`--max-workers=1`) or a lead decision to accept one container per fork.
  This is an L1 execution detail, not a code change in 020-A.
- **Durability off.** Applies only to the disposable test container.
- **Rollback.** Each chunk is test-only and revertable on its own; the old declaration is a
  four-line block per class.

## Progress log

- 2026-09-21: Drafted as a separate performance task; no test infrastructure has changed.
- 2026-11-08: Design revised by Claude after a 2026-11-08 run logged `HikariPool-N - Failed to
  validate connection` and `Connection refused` during JVM shutdown. Inventory on `f51d666`; work
  split into 020-A/020-B/020-C, proposing a database per class.
- 2026-11-08: Founder approved the plan with three decisions: a **shared database** rather than a
  database per class, **durability off** on the test container, and Cucumber **keeping its own
  container**. The shared-database choice materially changed 020-A and 020-B, so both were
  redesigned (catalogue-driven truncate before each class, sweep-delay override after finding the
  two scheduled verification jobs, `MigrationHistorySplitIntegrationTest` keeping its own
  container) and are flagged for confirmation before delegation. 020-C withdrawn. Docker on the
  workstation can stop containers again (founder, 2026-11-08). No test infrastructure has changed.
- 2026-11-08: Founder confirmed the revised 020-A, including `MigrationHistorySplitIntegrationTest`
  keeping its own container. 020-A is approved for delegation; 020-B follows after 020-A review.
- 2026-11-13: Founder re-confirmed 020-A. Lead baseline on unmodified `f51d666` failed (8m 3s,
  `:app` 345 tests with 2 class-start failures, `:modules:verification` 1): the plan-021 MinIO image
  `quay.io/minio/minio@sha256:14cea…8936e` now returns 401 from quay.io. The pilot
  `EvidenceEndpointIntegrationTest` needs it, so 020-A was not delegated.
- 2026-11-13: Founder merged `c8cc682` (MinIO test image replaced by pinned `pgsty/silo`). Cold
  baseline on `c8cc682`: BUILD SUCCESSFUL in 1m 41s, `:app` 354 tests, 41 PostGIS container starts
  (details in the handoff). 020-A dispatched.
- 2026-11-13: 020-A implemented on `perf/020-shared-test-postgres`: `SharedPostgis` (one lazily
  started container per JVM, durability off, `max_connections=200`, Hikari 5/0, both sweep delays
  `PT24H`, catalogue-driven `TRUNCATE … RESTART IDENTITY CASCADE` excluding Flyway history and
  extension-owned tables), `ResetBeforeClass`, `SharedPostgisResetTest`, and the three pilot
  classes rewired to `@ExtendWith(SharedPostgis.ResetBeforeClass.class)` plus
  `SharedPostgis.register(registry)`. JUnit 6 does not order class-level extensions with `@Order`;
  the reset extension is therefore declared first on each pilot class so its `beforeAll` runs
  before Spring's context-creating `SpringExtension`. All five changed test sources compile with
  `javac` against the repository build outputs.
- 2026-11-13: Fixed the `SharedPostgis` javadoc wrap so the test sources pass the Spotless
  8.8.0/google-java-format 1.28.0 dry run with `--skip-reflowing-long-strings`. Corrected this
  plan's stale 5-connection-pool wording to the implemented 12-connection concurrency override.
- 2026-11-13: L1 is **not yet run**. The worker sandbox cannot start Gradle (`Could not determine a
  usable wildcard IP for this machine`) and cannot reach `/var/run/docker.sock` (`permission
  denied`), so the four-class test command and `spotlessCheck`/`checkstyleTest` must run outside the
  sandbox. The new `SharedPostgisResetTest` adds two tests; the plan's "same `:app` test count"
  criterion needs a lead decision on whether 356 (354 + 2) replaces the 354 baseline.
- 2026-11-13: Loop-runner artifacts from an earlier outside-sandbox run were found in the ignored
  `.ai/baseline-020/` directory; these were not produced by this worker. They record the combined
  pilot command twice with exit 0 and 25 s wall clock each, but 4 PostGIS starts each; a
  `--max-workers=1` run also exited 0 in 25 s with 2 PostGIS starts. All four solo commands exited
  0. The reported test totals in `l1-status.txt` are cumulative/stale XML summaries (for example
  `tests=103` for the four-class filter), so they are not authoritative; `l1-lint.log` stops at
  `:app:spotlessJava`, so lint success is not proven. The runner must rerun the L1 block below.
- 2026-11-13: The "exactly one PostGIS container for the combined pilot run" criterion is not
  demonstrated: 4 starts by default and 2 with `--max-workers=1`. One-container-per-JVM remains
  the implementation intent, but this evidence needs a lead decision on accepting one container
  per fork or requiring an additional single-fork measurement before 020-B.
- 2026-11-13: Lead decision on the worker's two questions (recorded in Decisions): one shared
  container per test JVM, evidenced with `--max-workers=1` (lead-run: 2 starts = shared +
  Cucumber's own); test-count target 356. The 020-A loop is restarted with these criteria.
- 2026-11-14: 020-A loop complete. Runner L1 (`.ai/runs/implementation-loop.vE3eqb/l1-1.json`):
  7/7 passed. Fresh Claude review (`review-1-retry.json`): `READY_TO_MERGE`, no blocking findings;
  low notes carried to 020-B (temporary-table cleanup in `SharedPostgisResetTest`; guard the
  extension-before-`@SpringBootTest` order). Awaiting the human merge decision and L2.
- 2026-11-14: 020-A implementation re-verified in the worker sandbox against the accepted
  criteria. No code change was needed: the five changed test sources compile with `javac`,
  Checkstyle 10.24.0 reports `Audit done.`, and the google-java-format 1.28.0 dry run over the test
  tree is clean. L1 remains runner-owned; no authoritative test, container-start or test-count
  evidence is claimed here.

- 2026-11-14: 020-A merged (#6; CI backend/frontend/governance green). 020-B revised with the
  020-A review's two low findings (reset-test cleanup, wiring/order guard), an `ai-l1` block, and
  the build-file comment moved to the lead (protected path). Founder approved; dispatched from
  `perf/020-b-migrate-classes` (base `c654440`).
- 2026-11-14: 020-B implemented on `perf/020-b-migrate-classes`: all 36 standard classes moved to
  `@ExtendWith(SharedPostgis.ResetBeforeClass.class)` plus `SharedPostgis.register(registry)`; the
  two recorded exceptions keep their own containers. `SharedPostgisResetTest` now drops its
  temporary table in `finally`; `SharedPostgisWiringArchitectureTest` guards missing and misordered
  reset extensions; `CONTRIBUTING.md` describes the shared container, reset, sweep-delay override
  and exceptions. The 38 changed/added test sources compile with `javac`, formatting is clean and
  `git diff --check` passes. Runner-owned L1 and lead L2/review remain.
- 2026-11-14: Runner L1 found the wiring guard itself broken: ArchUnit selected
  `SharedPostgis` through its own delegating `register` self-call, and the fixture tests reused the
  production rule that excludes `*Fixture`, so they could never reject. The production rule now
  excludes the harness class, and the fixture checks use a separate rule without that exclusion.
  All three guard tests pass under a local JUnit launcher; Spotless-style google-java-format and
  Checkstyle 10.24.0 pass on the changed file. Runner L1 is rerun by the loop runner.

- 2026-11-14: 020-B loop (`.ai/runs/implementation-loop.KuKd5I`): runner L1 round 1 failed — the
  new guard flagged `SharedPostgis`'s own `register` overload call — fixed by the worker; round 2
  L1 3/3 green; fresh review `READY_TO_MERGE`, two low findings (guard excludes by simple name,
  not exact class; acceptance boxes ticked before evidence — corrected here). Lead updated the
  forking comment in `geohousing.java-conventions.gradle.kts` and ran the cold L2: see "Results".

## Final outcome

020-A merged (#6). 020-B implemented on `perf/020-b-migrate-classes`; runner-owned L1 and lead
L2/review pending.

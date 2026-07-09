# Project Scaffold: Gradle Multi-Module Backend

Status: Completed
Owner: Claude
Related issue: none (direct founder request)
Last updated: 2026-07-09

## Objective

Stand up the backend's build and infrastructure skeleton — Gradle multi-module Java 21 project, Spring Boot application shell, modular-monolith module boundaries, PostgreSQL/PostGIS dev infrastructure, Flyway, Testcontainers, formatting/static analysis, a health endpoint, architecture-boundary tests, and a basic CI workflow — with **zero business functionality** (no auth, reviews, verification, listings or moderation logic).

## Acceptance criteria

- [x] `apps/api` builds with `./gradlew build` on a clean checkout using only the committed Gradle wrapper.
- [x] `./gradlew check` runs formatting/static-analysis checks, unit tests, and architecture tests, and passes.
- [x] A Testcontainers-backed integration test starts `postgis/postgis`, applies the Flyway baseline migration, boots the Spring context, and asserts `/actuator/health` returns `UP`.
- [x] Architecture tests fail the build if a domain module depends on another domain module directly, or if a `domain` package depends on Spring/JPA types. (Verified live: a real cross-module violation was temporarily introduced and confirmed to fail the build, then reverted.)
- [x] `docker compose -f infra/docker/docker-compose.yml up -d` provides a local Postgres+PostGIS instance the app can connect to. (Verified live against a real `bootRun`, not just Testcontainers.)
- [x] CI runs the same checks on push/PR — via the extended `.github/workflows/governance-check.yml` rather than a separate `ci.yml` (see step 13 deviation note).
- [x] No entity, endpoint, or table related to identity, properties, reviews, verification, moderation, media, search or listings exists yet — only empty module skeletons.

## Non-goals

- No microservices — one deployable Spring Boot application (ADR-0001).
- No authentication, review, verification, listings or moderation domain logic.
- No OpenAPI contract, no public API endpoints beyond `/actuator/health`.
- No mobile/admin app scaffolding (`apps/mobile`, `apps/admin`) — out of scope for this plan.

## Current system

The repository currently contains **documentation and process files only**; no application code exists. Confirmed by direct inspection:

- No `apps/`, `packages/`, `infra/`, or `docs/plans/` (other than this plan) directories exist yet.
- `.claude/`, `.agents/`, and `.github/` (CODEOWNERS, issue templates, PR template, `governance-check.yml`) were missing as of 2026-07-09 despite being listed in `FILE_INVENTORY.txt`, which caused `./scripts/check.sh` to fail at the governance step regardless of backend state. **Resolved 2026-07-09** — the founder added the missing files; `./scripts/check.sh` now runs clean (`Governance validation passed: 15 required files, 5 shared skills.`) with Gradle/pnpm checks correctly skipped since no scaffold exists yet.
- `scripts/check.sh` already anticipates a Gradle backend at `apps/api/gradlew` and a root `package.json` for a JS frontend — this plan implements the former only.
- [docs/ARCHITECTURE.md](../ARCHITECTURE.md) specifies the stack (Java 21+, Spring Boot, Spring Security, PostgreSQL, PostGIS, Flyway, OpenAPI, Testcontainers) and the module list (`identity, properties, reviews, verification, moderation, media, search, notifications, analytics, listings (future), shared-kernel`) but does not pin a build tool or exact versions — the user's request fixes the build tool as Gradle.
- Locally available: OpenJDK 21.0.11, Docker 29.6.0, no system Gradle (wrapper will be bootstrapped during implementation).

## Proposed directory tree

```text
apps/
  api/
    gradlew, gradlew.bat, gradle/wrapper/...
    settings.gradle.kts
    gradle.properties
    gradle/libs.versions.toml          # version catalog
    buildSrc/
      build.gradle.kts
      src/main/kotlin/geohousing.java-conventions.gradle.kts
    app/                               # bootable Spring Boot composition root
      build.gradle.kts
      src/main/java/com/example/geohousing/app/
        GeoHousingApplication.java
      src/main/resources/
        application.yml
        db/migration/V1__init.sql
      src/test/java/com/example/geohousing/app/
        architecture/ModuleBoundaryArchitectureTest.java
        ApplicationContextIntegrationTest.java
    modules/
      shared-kernel/build.gradle.kts + src/main/java/.../shared/{api,application,domain,infrastructure}/package-info.java
      identity/        (same shape)
      properties/       (same shape)
      reviews/          (same shape)
      verification/     (same shape)
      moderation/       (same shape)
      media/            (same shape)
      search/           (same shape)
      notifications/    (same shape)
      analytics/        (same shape)
    README.md                          # build/lint/test/integration commands, per CONTRIBUTING.md
infra/
  docker/
    docker-compose.yml                 # postgis/postgis dev database only
.github/
  workflows/
    ci.yml
docs/
  plans/
    001-project-scaffold.md            # this file
  adr/
    0004-backend-build-tooling.md      # new — records Gradle/Spring Boot/tooling trade-offs
.gitignore                             # new — Gradle/Java/IDE artifacts
```

`listings` is intentionally **not** scaffolded (ADR-0001/ARCHITECTURE.md mark it future/disabled, matching accepted decision P-A02).

## Gradle modules

| Module | Depends on | Purpose |
|---|---|---|
| `buildSrc` | — | Shared convention plugin: Java 21 toolchain, Spotless, Checkstyle, common test deps |
| `modules:shared-kernel` | — | Identifiers, clock, domain-event abstractions only (per ARCHITECTURE.md §3) |
| `modules:identity` | `shared-kernel` | Empty skeleton, `api/application/domain/infrastructure` packages |
| `modules:properties` | `shared-kernel` | Empty skeleton |
| `modules:reviews` | `shared-kernel` | Empty skeleton |
| `modules:verification` | `shared-kernel` | Empty skeleton |
| `modules:moderation` | `shared-kernel` | Empty skeleton |
| `modules:media` | `shared-kernel` | Empty skeleton |
| `modules:search` | `shared-kernel` | Empty skeleton |
| `modules:notifications` | `shared-kernel` | Empty skeleton |
| `modules:analytics` | `shared-kernel` | Empty skeleton |
| `app` | all of the above | Spring Boot main class, Actuator health, Flyway baseline migration, architecture + integration tests |

No domain module depends on any sibling domain module — enforced structurally by Gradle (no such dependency is declared) and continuously by an ArchUnit slice-cycle check in `app`.

## Dependencies

Versions confirmed current as of 2026-07-09; **re-confirm exact patch versions at implementation time** since all of these ship frequent patches.

| Dependency | Version | Notes |
|---|---|---|
| Java | 21 (LTS) | matches ARCHITECTURE.md and local toolchain |
| Gradle | 9.6.x, Kotlin DSL, wrapper committed | latest stable; no system Gradle installed locally — wrapper must be bootstrapped |
| Spring Boot | 4.1.x (Spring Framework 7) | latest stable; confirmed by founder 2026-07-09 |
| Spring Boot starters | `web`, `actuator`, `data-jpa` | actuator restricted to `health` endpoint only |
| PostgreSQL driver | managed via Spring Boot BOM | — |
| Flyway | managed via Spring Boot BOM | one baseline migration only |
| Testcontainers | 2.0.5 (`testcontainers`, `postgresql`, `junit-jupiter`) | major-version jump from the 1.x line — confirm Spring Boot 4.1 compatibility during implementation |
| ArchUnit | 1.4.2 (`archunit-junit5`) | architecture-boundary tests |
| Spotless | latest Gradle plugin, `googleJavaFormat` | formatting |
| Checkstyle | latest Gradle plugin, Google ruleset baseline | static analysis |
| Dev database image | `postgis/postgis:18-3.6` | PostgreSQL 18 + PostGIS 3.6, local Docker Compose only |
| CI actions | `actions/checkout@v4`, `actions/setup-java@v4` (temurin), `gradle/actions/setup-gradle@v4` | standard, cacheable |

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|
| Build tool | Gradle 9.6.x, Kotlin DSL | explicit instruction; strong multi-module + convention-plugin support | not expected |
| Spring Boot line | 4.1.x (Spring Framework 7) — **confirmed by founder 2026-07-09** | greenfield project — starting on latest stable avoids a near-term forced major upgrade | if ecosystem/plugin gaps (IDE, third-party libs) block progress, fall back to latest Spring Boot 3.x LTS-track release instead |
| Module boundary enforcement | Gradle subproject isolation + ArchUnit in `app` | matches ADR-0001; compiler-level isolation plus a continuously-run test | if module count/complexity later warrants a dedicated `architecture-tests` module |
| Formatting/static analysis | Spotless (google-java-format) + Checkstyle | low-config, common, runs under `./gradlew check` | if the team wants a different style guide |
| CI runs full `./scripts/check.sh` | yes, unmodified | keeps CI aligned with the same command contributors run locally (CONTRIBUTING.md); governance-check now passes (resolved 2026-07-09) | if governance requirements change again |
| Package root | `com.example.geohousing` | matches ARCHITECTURE.md §4 exactly | before any real domain registration/public release, since `example` is a placeholder |
| `analytics` module included | yes — **confirmed by founder 2026-07-09** | ARCHITECTURE.md §3 lists it without a "future" marker (unlike `listings`) | not expected |

## Implementation steps

1. Add root `.gitignore` for Gradle/Java/IDE build artifacts.
2. Bootstrap the Gradle wrapper at `apps/api/` targeting Gradle 9.6.x (no system Gradle is installed; use a temporary local install to generate the wrapper, then rely on the wrapper only — no privileged/system-wide install needed).
3. Create `apps/api/settings.gradle.kts` declaring `app` and all `modules/*` subprojects, plus `gradle/libs.versions.toml` version catalog.
4. Create `buildSrc` with a `geohousing.java-conventions` plugin: Java 21 toolchain, Spotless, Checkstyle, common test dependencies (JUnit 5), applied to every subproject.
5. Create `modules/shared-kernel` with `api/application/domain/infrastructure` `package-info.java` stubs per ARCHITECTURE.md §3/§4.
6. Create the remaining 9 domain modules with the same package shape, each depending only on `shared-kernel`.
7. Create the `app` module: Spring Boot starters (`web`, `actuator`, `data-jpa`), Flyway, Postgres driver, dependencies on all domain modules; `GeoHousingApplication` main class; `application.yml` restricting exposed actuator endpoints to `health`.
8. Add Flyway baseline migration `V1__init.sql` that only enables the `postgis` extension — no domain tables.
9. Add ArchUnit tests in `app`: no dependency cycles between module packages, `..domain..` packages free of `org.springframework..` and JPA annotations, `shared-kernel` has no dependents among itself.
10. Add a Testcontainers integration test in `app`: start `postgis/postgis:18-3.6`, wire datasource via `@DynamicPropertySource`, assert context loads, Flyway migration applied, `/actuator/health` returns `UP`.
11. Add `infra/docker/docker-compose.yml` with a single dev-only Postgres+PostGIS service (non-secret default credentials, clearly commented as local-dev-only, never for shared/deployed use).
12. Add `apps/api/README.md` documenting build/lint/test/integration/run commands, per CONTRIBUTING.md's requirement that each application documents its own commands.
13. ~~Add `.github/workflows/ci.yml`~~ — **deviation**: `.github/workflows/governance-check.yml` already existed (added alongside the governance files) and already runs `./scripts/check.sh` on `pull_request`/`push: main`. Since `check.sh` already invokes the Gradle build/check when `apps/api/gradlew` is present, a separate `ci.yml` would just run the identical script twice on every PR. Extended the existing workflow with `actions/setup-java@v4` (temurin, 21) and `gradle/actions/setup-gradle@v4` for caching instead of adding a duplicate file.
14. Add `docs/adr/0004-backend-build-tooling.md` recording the Gradle/Spring Boot 4.x/Testcontainers/ArchUnit/Spotless+Checkstyle trade-off, per AGENTS.md rule 5 (new production dependencies require an ADR).
15. Run all verification commands below; fix until green.
16. Hand off with a summary of what changed and any remaining follow-ups.

### Branch sequencing

Implemented as 4 small, independently reviewable branches rather than one, per CONTRIBUTING.md's "small enough to review in one focused session." Recorded here so a reviewer (human or Codex) working from this file alone knows a given branch's absence of later steps is intentional scoping, not an oversight:

| Branch | Covers steps | Explicitly excludes |
|---|---|---|
| `chore/001-gradle-module-skeleton` | 1–6, 9 (partial: module-boundary rules only) | Spring Boot (step 7), Flyway/Testcontainers (steps 8, 10), docker-compose (11), CI (13), ADR (14) — these land in later branches |
| `chore/001-spring-boot-app` | 7 | DB/Flyway/Testcontainers |
| `chore/001-postgres-flyway-testcontainers` | 8, 10, 11 | — |
| `chore/001-ci-and-adr` | 12, 13, 14 | — |

Each branch gets its own self-check, independent Codex review, and human review before the next starts.

## Verification

```bash
# from apps/api
./gradlew build
./gradlew check                     # formatting, static analysis, unit + architecture tests
./gradlew test --tests "*Architecture*"
./gradlew test --tests "*IntegrationTest*"   # Testcontainers-backed, requires Docker

# local dev database
docker compose -f infra/docker/docker-compose.yml up -d
docker compose -f infra/docker/docker-compose.yml down

# manual smoke check
./gradlew bootRun
curl -f http://localhost:8080/actuator/health

# repo-wide gate (from repo root)
./scripts/check.sh
```

## Risks and assumptions

- ~~`./scripts/check.sh` fails independent of this scaffold~~ — **resolved 2026-07-09**: founder added the missing `.claude/`, `.agents/`, `.github/` governance files; `./scripts/check.sh` now passes cleanly with the backend scaffold correctly reported as not-yet-present.
- **No system Gradle installed.** Bootstrapping the wrapper requires a one-time local (non-privileged, user-writable) Gradle install; not a `HUMAN_ACTION_REQUIRED` case, but noted so it isn't a surprise mid-implementation.
- **Spring Boot 4.1.x is a recent major line** (Spring Framework 7, released within the last few months). Tooling/IDE/third-party-library maturity may lag behind the Spring Boot 3.x line. Flagged in Decisions; fallback path identified.
- **Testcontainers 2.x is also a recent major line** (jumped from 1.x). Verify Spring Boot 4.1 + Testcontainers 2.0.5 interop before relying on it; if incompatible, this is a same-day fallback to the latest compatible version, not a redesign.
- **Docker-in-CI assumption**: GitHub-hosted `ubuntu-latest` runners include Docker by default, which Testcontainers needs; confirm at CI-implementation time rather than assuming indefinitely.
- **Dev-only database credentials** in `docker-compose.yml` are non-secret, localhost-only defaults — must never be reused in any shared or deployed environment (SECURITY_PRIVACY.md).
- **ArchUnit rules are structurally in place but largely vacuous** until real classes exist in each module — they activate as business code lands in later tasks, they don't retroactively validate anything today.
- **This plan does not touch** authentication, OpenAPI contract generation, or any entity/table — those depend on P-008 (auth provider ADR, already accepted in principle) and later domain-specific plans.

## Progress log

- 2026-07-09: Plan drafted from repository inspection; no implementation started; awaiting approval.
- 2026-07-09: Founder confirmed Spring Boot 4.1.x and inclusion of the `analytics` module. Both open decisions in the Decisions table are now resolved.
- 2026-07-09: Founder added the missing `.claude/`, `.agents/`, `.github/` governance files. Verified via `./scripts/check.sh` → `Governance validation passed: 15 required files, 5 shared skills.` No open blockers remain. Implementation not yet started — awaiting explicit go-ahead.
- 2026-07-09: Branch 1 (`chore/001-gradle-module-skeleton`) implemented, self-checked, independently reviewed by Codex (2 findings: 1 rejected as intentional scope-splitting, 1 accepted — added the sibling-module api-boundary ArchUnit rule, verified it actually fires against a real temporary violation). Merged to `main`.
- 2026-07-09: Branch 2 (`chore/001-spring-boot-app`) implemented: Spring Boot 4.1.0 wired in, actuator restricted to `health`. Hit and fixed a real Boot-4-specific package relocation (`@AutoConfigureMockMvc` moved, needs `spring-boot-starter-webmvc-test`). Verified live via `bootRun` + `curl`. Codex review: no findings. Merged to `main`.
- 2026-07-09: Branch 3 (`chore/001-postgres-flyway-testcontainers`) implemented: Flyway baseline migration, docker-compose, Testcontainers integration test. Found and fixed two real bugs by actually running things instead of trusting green tests: Flyway autoconfiguration is a separate `spring-boot-starter-flyway` module in Boot 4.1 (was silently never running — a DB-less smoke test couldn't have caught this, only an explicit `flyway_schema_history`/`pg_extension` assertion did), and the docker-compose volume used the pre-Postgres-18 mount path. Verified live via `docker compose up` + `bootRun` + `curl`. Codex review: no findings. Merged to `main`.
- 2026-07-09: Branch 4 (`chore/001-ci-and-adr`) implemented: ADR-0004 recording the tooling trade-offs, `apps/api/README.md`, and CI — deviated from the original step 13 by extending the already-existing `.github/workflows/governance-check.yml` instead of adding a duplicate `ci.yml`, since that workflow already ran `./scripts/check.sh` on the same triggers.

## Final outcome

All four branches implemented, self-checked (`./gradlew build`/`check`, live `bootRun`/`docker compose` smoke tests), independently reviewed by Codex, and merged to `main`. Acceptance criteria met. Deviations from the original plan: CI reused/extended the existing governance workflow instead of adding a new one; three real, unanticipated bugs (two Spring Boot 4.1 package/module relocations, one Postgres 18 volume-path change) were found and fixed during implementation, documented in ADR-0004 and this progress log rather than hidden. No business functionality was added, consistent with scope. Follow-ups: P-010 (legal entity) remains deferred per `docs/DECISION_LOG.md`; auth/OpenAPI/domain entities are separate future plans.

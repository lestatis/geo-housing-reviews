# ADR-0004: Backend build tooling and framework versions

Status: Accepted
Date: 2026-07-09
Deciders: Founders
Related: `docs/plans/001-project-scaffold.md`, ADR-0001 (modular monolith)

## Context

ADR-0001 committed to a single Spring Boot application with Java 21, PostgreSQL, PostGIS and Flyway, but did not fix a build tool, exact framework versions, module-boundary enforcement mechanism, or formatting/static-analysis tooling. The initial project scaffold (`docs/plans/001-project-scaffold.md`) needed to make these choices concrete before any domain code could be written.

## Decision drivers

- Team is starting from scratch on a greenfield codebase — no migration cost from picking the latest stable versions now.
- ADR-0001's module boundaries need continuous, automated enforcement, not just code-review discipline.
- Small team (see decision log P-008): minimize new operational/tooling surface.
- AGENTS.md rule 5 requires this trade-off to be documented before the dependencies land.

## Considered options

### Build tool: Gradle vs Maven

Gradle was specified directly in the founder's scaffold request. Gradle's multi-module + convention-plugin (`buildSrc`) support fits the modular-monolith layout (10 subprojects) more naturally than Maven's parent-POM inheritance, at the cost of Kotlin DSL being less universally familiar than XML.

### Spring Boot line: 4.1.x vs latest 3.x

- **4.1.x (chosen):** latest stable (Spring Framework 7), avoids a near-term forced major upgrade on a brand-new codebase.
- **3.x LTS-track:** more mature ecosystem/IDE/third-party-library support, but starting a new project on a line already scheduled for eventual replacement.

### Module-boundary enforcement

- **ArchUnit tests (chosen):** continuous, automated, runs in every build.
- **Code review only:** no automated guarantee, doesn't scale as the team/AI-agent set grows.

### Formatting/static analysis

- **Spotless (google-java-format) + Checkstyle (chosen):** low-config, standard, integrates with `./gradlew check`.
- **Hand-rolled IDE settings only:** no CI enforcement.

## Decision

- Gradle 9.6.x, Kotlin DSL, wrapper committed, `buildSrc` convention plugin (`geohousing.java-conventions`) for shared Java 21 toolchain + Spotless + Checkstyle config.
- Spring Boot 4.1.0, imported via `implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))` in the `app` module only (domain modules stay framework-free, enforced by ArchUnit).
- Flyway via `spring-boot-starter-flyway` + `flyway-database-postgresql` (Spring Boot 4.1 moved Flyway autoconfiguration into its own starter — see Consequences).
- Testcontainers 2.0.5 (`testcontainers-junit-jupiter`, `testcontainers-postgresql`) plus `spring-boot-testcontainers` for `@ServiceConnection`.
- ArchUnit 1.4.2 (`archunit-junit5`) for module-cycle-freedom, domain-package framework-independence, and cross-module api-only-access rules, run from the `app` module's test source.
- Root `gradle/libs.versions.toml` version catalog for versions used by regular subprojects; `buildSrc` itself hardcodes a couple of versions directly (see trade-offs).

## Consequences

### Positive

- Greenfield project starts on the latest stable framework line instead of one already headed toward its own migration.
- ArchUnit rules make ADR-0001's module boundaries a build failure, not just a review comment, and were verified to actually fire (a real cross-module violation was temporarily introduced during implementation and confirmed to fail the build before being reverted).
- Gradle module isolation (no sibling-to-sibling `implementation(project(...))` dependencies) already prevents most boundary violations at compile time; ArchUnit is the fallback once such a dependency is legitimately added later.

### Negative / trade-offs

- Spring Boot 4.1.x and Testcontainers 2.x are both very recent major lines with less accumulated community troubleshooting content. Implementation hit two real package-relocation surprises not obvious from cached documentation:
  - `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure` and now requires the separate `spring-boot-starter-webmvc-test` artifact.
  - Flyway autoconfiguration moved out of `spring-boot-autoconfigure` into its own `spring-boot-starter-flyway` module; without it, Flyway silently never ran and a DB-less smoke test still passed, masking the gap. Caught only by asserting `flyway_schema_history`/`pg_extension` state directly rather than trusting a green "context loads" test.
  - Testcontainers 2.x renamed its artifacts (e.g. `org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter`).
- `buildSrc` precompiled script plugins could not reliably resolve the root version catalog's generated `libs` accessor (`the<LibrariesForLibs>()` failed to resolve); worked around by hardcoding the JUnit/Spotless versions directly in `buildSrc/build.gradle.kts` and `geohousing.java-conventions.gradle.kts` with a comment to keep them in sync with `gradle/libs.versions.toml` manually.
- Postgres 18's Docker image changed its data-directory volume convention (single mount at `/var/lib/postgresql`, not `/var/lib/postgresql/data`); `infra/docker/docker-compose.yml` was fixed accordingly, but this class of breakage (image-version-specific volume layout) isn't caught by Testcontainers' ephemeral-volume tests and needs a live `docker compose up` to catch.

### Follow-up

- Consider Dependabot/Renovate for keeping pinned versions current once the project has enough surface area to justify it — out of scope for this scaffold.
- If the manual version-sync comment in `buildSrc` becomes a real maintenance burden, revisit with a composite `build-logic` build instead of `buildSrc`, which has more reliable version-catalog sharing in some Gradle versions.

## Revisit when

- A required library isn't yet available for Spring Boot 4.x, forcing a fallback to the latest Spring Boot 3.x line (already anticipated in `docs/plans/001-project-scaffold.md`'s Decisions table).
- Module count or build time makes `buildSrc` convention-plugin overhead a measured problem.
- Testcontainers 2.x proves incompatible with a dependency not yet exercised (e.g. a future search/OpenSearch Testcontainers module).

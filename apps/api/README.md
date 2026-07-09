# Backend (`apps/api`)

Java 21, Spring Boot 4.1, Gradle multi-module modular monolith. See `docs/ARCHITECTURE.md` for module boundaries and `docs/adr/0004-backend-build-tooling.md` for the tooling trade-offs.

No business functionality yet — this is the project scaffold from `docs/plans/001-project-scaffold.md`.

## Prerequisites

- Java 21 (the Gradle wrapper handles Gradle itself — no local Gradle install needed).
- Docker, for local Postgres/PostGIS and for Testcontainers-backed integration tests.

## Common commands

Run from `apps/api/`:

```bash
./gradlew build              # compile, format-check, static analysis, unit + integration tests
./gradlew check               # formatting (Spotless), static analysis (Checkstyle), all tests
./gradlew spotlessApply        # auto-fix formatting
./gradlew test --tests "*ArchitectureTest*"     # module-boundary architecture tests only
./gradlew test --tests "*IntegrationTest*"      # Testcontainers-backed tests only (needs Docker)
./gradlew bootRun              # run the app locally on :8080
```

## Local database

```bash
docker compose -f ../../infra/docker/docker-compose.yml up -d    # start local Postgres/PostGIS
docker compose -f ../../infra/docker/docker-compose.yml down -v  # stop and remove data
```

Default local credentials (`geo_housing`/`geo_housing`) are throwaway dev-only defaults set in `infra/docker/docker-compose.yml` — never reuse them anywhere shared or deployed. Override via `DB_URL` / `DB_USER` / `DB_PASSWORD` environment variables if needed.

## Smoke test

```bash
docker compose -f ../../infra/docker/docker-compose.yml up -d
./gradlew bootRun
curl http://localhost:8080/actuator/health   # expect {"status":"UP", ...}
```

Only `/actuator/health` is exposed publicly (see `app/src/main/resources/application.yml`) — no other actuator endpoints.

## Module layout

```text
app/                  Spring Boot composition root: main class, config, migrations, architecture/integration tests
modules/shared-kernel/ Identifiers, clock, domain-event abstractions — no other module dependencies
modules/<domain>/      identity, properties, reviews, verification, moderation, media, search, notifications, analytics
                       Each depends only on shared-kernel. Cross-module access is restricted to a sibling's
                       `api` package by an ArchUnit rule in app/src/test/.../ModuleBoundaryArchitectureTest.java.
```

`listings` is intentionally not scaffolded yet — it's a future module per ADR-0001 and decision P-A02.

# Identity Module: Auth, Accounts, Public Profiles

Status: Active
Owner: Claude
Related issue: none (direct founder request)
Last updated: 2026-07-09

## Objective

Implement the `identity` backend module: vendor-agnostic OAuth2 Resource Server authentication, `Account`/`PublicProfile`/`UserRestriction` domain model, self-service `/api/me` endpoints, a minimal admin RBAC proof-of-concept, and identity-local data export/deletion.

## Acceptance criteria

- [ ] `identity.domain` classes satisfy the ArchUnit rules (framework-free, no infrastructure dependency) — these currently pass vacuously and must activate for real.
- [ ] `POST`-free auto-provisioning: a new valid JWT subject gets an `Account` + `PublicProfile` (auto-generated pseudonym) created atomically on first authenticated request.
- [ ] `GET /api/me` / `PATCH /api/me/profile` work end-to-end against a real Testcontainers Postgres, with the full negative-path suite (401, 403 restricted, 409 stale version, 422 duplicate pseudonym) passing.
- [ ] Role is always resolved server-side from our own `account.role` column — a JWT with a spoofed `roles` claim never grants elevated access (explicit test).
- [ ] `GET /api/admin/accounts/{id}` enforces `ROLE_ADMIN` and writes an audit event per call.
- [ ] `POST /api/me/export` / `DELETE /api/me` work with idempotency-key semantics; deletion blocks future re-provisioning of the same subject.
- [ ] No other module's code exists yet, so no cross-module consumption to verify — `identity.api` intentionally stays an empty stub.

## Non-goals

- Picking/signing up for an actual IdP vendor (separate human action later — vendor-agnostic code only).
- Moderator UI/workflow for creating `UserRestriction`s (future `moderation` module consumes identity later).
- MFA/step-up auth enforcement (depends on the eventually-chosen IdP).
- Full cross-module data-subject-request orchestration (future, once other modules hold data).
- OpenAPI contract generation.
- First-ADMIN bootstrapping tooling (manual SQL, one-time human step).

## Current system

Gradle/Spring Boot scaffold complete (`docs/plans/001-project-scaffold.md`, merged to `main`). `identity` module currently has only empty `package-info.java` stubs under `api/application/domain/infrastructure`. `app` module has Spring Boot 4.1.0, web/actuator/data-jpa/flyway/postgres, one migration (`V1__init.sql`, enables `postgis`), a Testcontainers integration test proving DB/Flyway/health wiring. ArchUnit rules in `apps/api/app/src/test/java/com/example/geohousing/app/architecture/ModuleBoundaryArchitectureTest.java` (cross-module cycle-freedom, api-only cross-module access, domain framework-independence) already exist and constrain all new code — the two domain-independence rules are currently `allowEmptyShould(true)` and will activate for the first time with this module.

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|
| Auth vendor timing | Vendor-agnostic OAuth2 Resource Server now (`jwk-set-uri` configurable); pick actual IdP vendor later | P-008 already defers vendor choice; code is identical for any OIDC provider | when a vendor is actually chosen — config-only change |
| Pseudonym flow | Auto-generate default pseudonym atomically with Account on first login | Zero-friction onboarding; generated pseudonym is safe by construction (P-003) | not expected |
| Export/delete scope | Include identity-local export/delete now | Explicitly Must-have in MVP_SCOPE.md, cleanly scoped to what this module owns | once other modules hold data, build full cross-module DSR orchestration |
| Postgres schema-per-module | `CREATE SCHEMA identity`, tables as `identity.<table>` | DB-level reinforcement of "modules own their data" (architecture.md) | not expected |
| Package-layer meaning | `domain` = framework-free; `application` = use cases + ports; `infrastructure` = JPA/Security/REST; `api` = cross-module contracts | Matches existing ArchUnit rule wording exactly | not expected |
| `identity.api` stays empty | Yes | No module exists yet to consume it; architecture.md requires ≥2 concrete use cases before a shared abstraction | when `moderation` needs to create a `UserRestriction` without touching identity's tables |
| `UserRestriction` creation | Data model + enforcement only, no moderator endpoint | That workflow belongs to future `moderation` module | when moderation module is planned |
| Security filter chain location | `app/.../config/SecurityConfiguration.java`, not `identity` | URL-space routing spans the whole app — composition-root concern | not expected |
| Shared generic ID type | None; `AccountId` is local to `identity.domain` | architecture.md: no shared abstraction until a second module needs it | when a second module needs the same pattern |
| Flyway version-per-module | Root = major `1`; identity = major `2` (`V2.1`, `V2.2`, ...); next module = `3`. Migrations live in a `db/migration/<module>` subfolder for readability, but **no explicit `spring.flyway.locations` config is needed** — the Boot default `classpath:db/migration` scans recursively, so an explicit sub-location entry gets silently discarded by Flyway as redundant (confirmed empirically in Chunk 1). Only the version-number registry actually prevents collisions | never (append-only registry, extend for each new module) |

## Implementation chunks (one git branch each — same process as the scaffold: self-check, local Codex review, human review, merge before the next starts)

1. **Build/config foundation** (`feat/002-identity-chunk1-foundation`, this branch): ADR-0005, Spring Security deps on `app` + `identity`, `jwk-set-uri` placeholder in `application.yml`, `V2.1`/`V2.2` migrations (`identity.account`, `identity.public_profile`) under `db/migration/identity/`, `IdentityMigrationIntegrationTest`. Also required (found empirically, not in the original plan): a minimal `SecurityConfiguration` permitting `/actuator/health` only, since adding the OAuth2 Resource Server starter pulls in Spring Security's default "authenticate everything" auto-config, which regressed the existing health-check test. Chunk 5 replaces this with the full JWT-aware config.
2. **Domain model**: `AccountId`, `AccountRole`, `AccountStatus` (`ACTIVE`/`CLOSED` only — restriction computed live, not a status flag), `Account`, `PublicProfile`, `Pseudonym`, `PseudonymFormatter`, `UserRestriction`, `RestrictionScope`, `AppealStatus`, domain exceptions. Activates the two currently-vacuous ArchUnit domain rules for real.
3. **Application layer**: repository port interfaces, `PseudonymAllocator`, `AccountProvisioningService`, `ProfileService`, `AdminAccountLookupService`. Unit-tested with hand-written in-memory fakes, no Spring context.
4. **Persistence adapters**: JPA entities/repositories/mappers/adapters implementing the Chunk 3 ports; `V2.3` migration (`identity.user_restriction`).
5. **Security wiring** (highest-risk, kept isolated): `IdentityJwtAuthenticationConverter` (role resolved from DB, never from JWT claims), `app`'s `SecurityConfiguration`. Spike `jwk-set-uri` lazy-fetch behavior first. Handle the first-request-provisioning race via the `auth_subject` unique constraint + retry.
6. **`/api/me` self-service endpoints**: `MeController` (GET/PATCH), `IdentityExceptionHandler` → RFC 7807 `ProblemDetail`.
7. **Admin RBAC proof-of-concept**: `V2.4` migration (`identity.admin_audit_event`), `AdminAccountController`, audit event per call.
8. **Data export/deletion**: `V2.5` migration (`identity.self_service_request`, idempotency), `AccountDataExportService`, `AccountDeletionService`, `/api/me/export` + `DELETE /api/me`.

## Verification

```bash
# from apps/api
./gradlew build
./gradlew check
./gradlew test --tests "*ModuleBoundaryArchitectureTest*"
./gradlew test --tests "*IntegrationTest*"

# repo-wide gate
cd /home/vladimir/IdeaProjects/geo-housing-reviews && ./scripts/check.sh
```
Live smoke test at least once after Chunk 6 and again after Chunk 8: `docker compose -f infra/docker/docker-compose.yml up -d`, `./gradlew bootRun`, exercise `/api/me` end-to-end — same discipline that caught the Flyway and docker-volume bugs during the scaffold work.

## Risks and assumptions

- **`jwk-set-uri` vs `issuer-uri` startup behavior** (Chunk 5) — high confidence but must be empirically spiked first, given this repo's track record of Boot-4-specific package-relocation surprises (MockMvc autoconfig, Flyway autoconfig).
- **Whether `spring-boot-starter-oauth2-resource-server` pulls `spring-boot-starter-security` transitively in Boot 4.1** — verify via `./gradlew dependencies`, don't assume.
- **Whether `SecurityMockMvcRequestPostProcessors` package path is stable** in this Spring Security/Boot 4.1 pairing — verify at Chunk 5.
- **Consent/notice-version tracking** (ARCHITECTURE.md §5 lists it under identity ownership) is not built in this plan — flagged so it isn't silently forgotten.
- **First-ADMIN bootstrap** requires a manual DB `UPDATE` — no self-service role-escalation endpoint exists by design (would defeat "never trust client role").
- **This plan does not implement MFA/step-up** for admins (SECURITY_PRIVACY.md §4) — depends on the eventually-chosen IdP's capabilities.

## Progress log

- 2026-07-09: Plan drafted via formal plan mode (CLAUDE.md authN/authZ trigger), reviewed via a Plan subagent, three product/architecture decisions confirmed with founder, approved. Chunk 1 branch created.

## Final outcome

Not yet complete.

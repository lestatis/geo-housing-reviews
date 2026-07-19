# Identity Module: Auth, Accounts, Public Profiles

Status: Active
Owner: Codex
Related issue: none (direct founder request)
Last updated: 2026-07-14

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
| Auth subject storage | Store only `HMAC-SHA256(subject, pepper)` (`auth_subject_hash`), never the raw subject (**ADR-0006**, added after Chunk 2 review) | Raw external subject is confidential personal data (SECURITY_PRIVACY.md §1); hashing removes the reversible identifier while still supporting no-resurrection. Domain holds an opaque `authSubjectHash`; HMAC computed at the boundary (Chunks 3/5); column renamed by a fix-forward migration (Chunk 4) | if the IdP offers a non-correlatable pairwise subject, or pepper rotation is needed |
| Pseudonym flow | Auto-generate default pseudonym atomically with Account on first login | Zero-friction onboarding; generated pseudonym is safe by construction (P-003) | not expected |
| Export/delete scope | Include identity-local export/delete now | Explicitly Must-have in MVP_SCOPE.md, cleanly scoped to what this module owns | once other modules hold data, build full cross-module DSR orchestration |
| Postgres schema-per-module | `CREATE SCHEMA identity`, tables as `identity.<table>` | DB-level reinforcement of "modules own their data" (architecture.md) | not expected |
| Package-layer meaning | `domain` = framework-free; `application` = use cases + ports; `infrastructure` = JPA/Security/REST; `api` = cross-module contracts | Matches existing ArchUnit rule wording exactly | not expected |
| `identity.api` stays empty | Yes | No module exists yet to consume it; architecture.md requires ≥2 concrete use cases before a shared abstraction | when `moderation` needs to create a `UserRestriction` without touching identity's tables |
| `UserRestriction` creation | Data model + enforcement only, no moderator endpoint | That workflow belongs to future `moderation` module | when moderation module is planned |
| Security filter chain location | `app/.../config/SecurityConfiguration.java`, not `identity` | URL-space routing spans the whole app — composition-root concern | not expected |
| Shared generic ID type | None; `AccountId` is local to `identity.domain` | architecture.md: no shared abstraction until a second module needs it | when a second module needs the same pattern |
| Flyway version-per-module | Root = major `1`; identity = major `2` (`V2.1`, `V2.2`, ...); next module = `3`. Migrations live in a `db/migration/<module>` subfolder for readability, but **no explicit `spring.flyway.locations` config is needed** — the Boot default `classpath:db/migration` scans recursively, so an explicit sub-location entry gets silently discarded by Flyway as redundant (confirmed empirically in Chunk 1). Only the version-number registry actually prevents collisions | never (append-only registry, extend for each new module) |

### Identity migration registry (append-only)

| Version | Contents | Chunk |
|---|---|---|
| `V2.1` | `identity.account` | 1 (merged) |
| `V2.2` | `identity.public_profile` | 2 (merged) |
| `V2.3` | rename to `auth_subject_hash CHAR(64)`, `identity.user_restriction` | 4 (merged) |
| `V2.4` | `auth_subject_hash` hex-format CHECK, unique-constraint rename | 4 review follow-up (merged) |
| `V2.5` | `identity.admin_audit_event` | 7 (reserved) |
| `V2.6` | `identity.self_service_request` | 8 (reserved) |

## Implementation chunks (one branch each: self-check, fresh independent read-only review, human review, then merge before the next starts)

1. **Build/config foundation** (`feat/002-identity-chunk1-foundation`, this branch): ADR-0005, Spring Security deps on `app` + `identity`, `jwk-set-uri` placeholder in `application.yml`, `V2.1`/`V2.2` migrations (`identity.account`, `identity.public_profile`) under `db/migration/identity/`, `IdentityMigrationIntegrationTest`. Also required (found empirically, not in the original plan): a minimal `SecurityConfiguration` permitting `/actuator/health` only, since adding the OAuth2 Resource Server starter pulls in Spring Security's default "authenticate everything" auto-config, which regressed the existing health-check test. Chunk 5 replaces this with the full JWT-aware config.
2. **Domain model**: `AccountId`, `AccountRole`, `AccountStatus` (`ACTIVE`/`CLOSED` only — restriction computed live, not a status flag), `Account` (holds `authSubjectHash`, not raw subject — ADR-0006), `PublicProfile`, `Pseudonym`, `PseudonymFormatter`, `UserRestriction`, `RestrictionScope`, `AppealStatus`, domain exceptions. Activates the two currently-vacuous ArchUnit domain rules for real.
3. **Application layer**: repository port interfaces, an `AuthSubjectHasher` port, `PseudonymAllocator`, `AccountProvisioningService` (hashes the subject before lookup/insert), `ProfileService`, `AdminAccountLookupService`. Unit-tested with hand-written in-memory fakes, no Spring context.
4. **Persistence adapters**: JPA entities/repositories/mappers/adapters implementing the Chunk 3 ports; a fix-forward migration renaming `account.auth_subject VARCHAR(255)` → `auth_subject_hash CHAR(64)` (ADR-0006; new migration since V2.1 is merged/append-only); `V2.x` migration (`identity.user_restriction`). Review follow-up: `V2.4` adds the hex-format CHECK the rename omitted, and the adapter translates both unique constraints into domain exceptions.
5. **Security wiring** (highest-risk, kept isolated): `IdentityJwtAuthenticationConverter` (role resolved from DB, never from JWT claims; hashes the incoming `sub` before resolving the account), an HMAC `AuthSubjectHasher` reading the pepper from config, `app`'s `SecurityConfiguration`. Spike `jwk-set-uri` lazy-fetch behavior first. Handle the first-request-provisioning race by catching `AuthSubjectAlreadyProvisionedException` from `IdentityProvisioningRepository.create` (the unique constraint settles the race; the adapter translates it) and re-reading the winner's account. The HMAC must emit **lowercase hex** — `V2.4`'s `account_auth_subject_hash_format_check` rejects anything else.
6. **`/api/me` self-service endpoints**: `MeController` (GET/PATCH), `IdentityExceptionHandler` → RFC 7807 `ProblemDetail`. **Also owns restriction enforcement** (the "403 restricted" acceptance criterion, which no chunk previously claimed — found in the Chunk 3/4 review): add a `UserRestrictionRepository` port, check `UserRestriction.isActiveAt(now)` in `ProfileService.updateProfile`, and throw the already-defined `AccountRestrictedException`. Chunk 4 shipped the table, JPA entity, mapper and Spring Data repository for this, but no application port, so they are currently unused — wire them here rather than adding new infrastructure.
7. **Admin RBAC proof-of-concept**: `V2.5` migration (`identity.admin_audit_event`), `AdminAccountController`, audit event per call. `AdminAccountLookupService` returns the whole `Account`, so the controller must map to a DTO that omits `authSubjectHash` (and `email` unless the use case needs it) — do not serialize the domain object.
8. **Data export/deletion**: `V2.6` migration (`identity.self_service_request`, idempotency), `AccountDataExportService`, `AccountDeletionService`, `/api/me/export` + `DELETE /api/me`.

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
- 2026-07-14: Recovered from Git evidence after the previous session ended. Chunks 1–2 and their follow-up fixes are committed on `main`; chunk 3 starts on `feat/002-identity-chunk3-application`. Scope is application ports/services and in-memory-fake unit tests only; no schema, infrastructure, endpoint, or security-wiring changes.
- 2026-07-14: Chunk 3 implemented: framework-free account/profile/provisioning ports, HMAC boundary port, unique default-pseudonym allocation, provisioning/profile/admin services, and 11 in-memory-fake application tests. The provisioning persistence port explicitly requires atomic account/profile creation; HMAC implementation, database adapters, JWT race retry, endpoints, and migrations remain in their planned later chunks. `:modules:identity:check`, the module-boundary test, and `./scripts/check.sh` pass.
- 2026-07-14: Chunk 3 is committed on `feat/002-identity-chunk3-application` and ready for fresh independent read-only review.
- 2026-07-14: Chunk 3 is now merged on `main` (observable as `0c5c3af`). Chunk 4 starts on `feat/002-identity-chunk4-persistence`: add a single `V2.3` forward migration to rename `auth_subject` to `auth_subject_hash CHAR(64)` and create `identity.user_restriction`, then add identity-local JPA entities/repositories/mappers/adapters and integration coverage. The one migration avoids colliding with reserved later `V2.4` and `V2.5` versions; no data backfill is possible or needed because ADR-0006 records that no production account data exists.
- 2026-07-14: Chunk 4 is implemented and awaiting independent review on `feat/002-identity-chunk4-persistence`. `V2.3` has the planned append-only rename and restriction table; the JPA adapter atomically persists matching account/profile pairs, maps fixed-width hashes safely, translates duplicate pseudonyms, and rejects stale profile updates. Migration and adapter integration tests pass, as does `./scripts/check.sh`. No endpoint, JWT/HMAC implementation, provisioning-race retry, or admin/self-service behavior was started.
- 2026-07-14: Correction to the entry above, from the independent review of Chunks 3–4: chunks 3 and 4 were merged and pushed to `main` *before* that review, contrary to this plan's own per-chunk process, and `./scripts/check.sh` did **not** pass — four Chunk 4 files failed `spotlessCheck`, so CI was red on `main`. Fixed on `fix/002-identity-chunk4-review-findings`, along with two substantive findings: `V2.3` let a pre-existing raw OIDC subject survive as a fake hash (reproduced against Postgres; `V2.4` now rejects it with a hex-format CHECK), and `JpaIdentityPersistenceAdapter.create` translated no constraint violation, leaking `DataIntegrityViolationException` out of the application port on a provisioning race. Restriction enforcement was found to be owned by no chunk despite being an acceptance criterion, and is now assigned to Chunk 6. Remaining review items are deliberately left open: `create()` uses `merge` rather than `persist` (an extra SELECT per provisioning, correctness unaffected).
- 2026-07-15: Chunk 6 (`/api/me` self-service endpoints) implemented on `feat/002-identity-chunk6-me-endpoints`, branched from `main` after chunk 5 merged (`1922d71`). First REST surface in the app, establishing the RFC 7807 pattern. `GET /api/me` and `PATCH /api/me/profile` in `identity.infrastructure.web` (self-scoped: account id read from the authenticated principal, never a client identifier). Restriction enforcement finally wired (the "403 restricted" criterion the review reassigned here): new `UserRestrictionRepository` port + `JpaUserRestrictionRepository` adapter reusing chunk 4's until-now-unused `UserRestrictionJpaMapper`/`SpringDataUserRestrictionRepository`; `ProfileService.updateProfile` throws `AccountRestrictedException` when a restriction `isActiveAt(now)` (domain stays the authoritative gate; SQL is an index-friendly pre-filter). `IdentityExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to `ProblemDetail` with stable `code`s and no internal leakage. Email deliberately omitted from the self view. No migration, no app-module change, no new dependency. Tests: `ProfileServiceTest` gains active-restriction (403) and expired-restriction (allowed) cases; `MeEndpointIntegrationTest` drives the full 401/403/409/422 matrix end-to-end through the real security chain via a stub `JwtDecoder` (which `jwt()` post-processors cannot exercise). `./scripts/check.sh` passes. Deferred to later chunks: admin endpoint + audit + Account→DTO (7); export/delete (8).
- 2026-07-15: `fix/002-identity-chunk4-review-findings` merged to `main` (`b0eaf2e`), restoring green CI. Chunk 5 (security wiring) then implemented on `feat/002-identity-chunk5-security`, branched from `main` per the decided sequencing. Added: `HmacAuthSubjectHasher` (lowercase-hex HMAC-SHA256, satisfying `V2.4`'s format CHECK; blank pepper fails fast at startup); auth-time provisioning-race handling in `AccountProvisioningService` (catches `AuthSubjectAlreadyProvisionedException` and re-reads the winner); `IdentityJwtAuthenticationConverter` resolving the role **only** from `account.role`, never from a JWT claim, auto-provisioning unknown subjects and rejecting closed accounts as authentication failures; `IdentityBeanConfiguration` wiring the framework-free services; and the real app `SecurityConfiguration` (health `permitAll`, everything else `authenticated`, stateless, JWT resource server), with the converter injected by its Spring type so the app keeps no compile dependency on identity internals. Pepper injected via `IDENTITY_AUTH_SUBJECT_PEPPER` (empty default → fail fast); app tests supply a fixed non-secret test pepper via the build. Tests: hasher format/determinism/fail-fast, converter anti-spoofing/admin/closed/auto-provision/opaque-principal/missing-subject, provisioning-race retry, and an app-level fail-closed guard (anonymous `/api/me` → 401). `./scripts/check.sh` passes. Not started (later chunks): `/api/me` endpoints and restriction enforcement (6), admin endpoint + audit (7), export/delete (8); no live-IdP token smoke test is possible yet (ADR-0005).

## Final outcome

Not yet complete.

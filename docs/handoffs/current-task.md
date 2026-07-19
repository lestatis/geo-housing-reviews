# Task handoff

## Objective

Complete the identity backend module described in `docs/plans/002-identity-module.md`: vendor-neutral OAuth2 authentication, account/profile/restriction behavior, persistence, self-service and admin endpoints, and identity-local export/deletion.

## Active branch

`feat/002-identity-chunk6-me-endpoints` (branched from `main` at `1922d71`, after chunk 5 merged)

## Related issue or plan

No issue. See `docs/plans/002-identity-module.md`.

## Current status

chunk6_implemented — ready for fresh independent review and human review before merge

## Completed work

Observable committed work on `main`:

- Chunk 1 foundation in `f8cc350`: OAuth2 resource-server dependencies and configuration, minimal health-only security configuration, account/profile migrations, migration integration test, ADR-0005, and the execution plan.
- Chunk 1 correction in `506353b`: database constraints and negative-path migration coverage for account roles/statuses.
- Chunk 2 domain model in `9b79b87`: framework-free account, public profile, pseudonym, restriction domain types and unit tests.
- Chunk 2 corrections in `16b0dee`: collision-proof anonymization tombstones, HMAC-hash-only auth subject design, ADR-0006, and plan updates.
- Chunk 3 application layer in `0c5c3af`: repository/hasher ports, `PseudonymAllocator`, provisioning/profile/admin services, in-memory-fake unit tests.
- Chunk 4 persistence in `ebdcaea`: `V2.3` migration, JPA entities/mappers/repositories, `JpaIdentityPersistenceAdapter`, migration and adapter integration tests.
- Chunk 3–4 review fix merged to `main` as `b0eaf2e` (spotless, `V2.4` hash-format CHECK + constraint rename, adapter constraint translation, docs). CI green again.
- Chunk 5 security wiring merged to `main` as `1922d71` (JWT converter with DB-derived role, HMAC hasher, real filter chain, provisioning-race retry). Merged out-of-band during chunk 6 planning; this handoff trusts that its fresh independent review happened (git shows it on `main`/`origin/main`).

Chunks 3 and 4 were merged and pushed to `origin/main` **before** independent review, contrary to the per-chunk process in the plan (self-check → independent review → human review → merge).

### Chunk 6 — `/api/me` self-service endpoints (this branch, not yet reviewed)

First REST surface in the app; establishes the RFC 7807 error pattern. All new code is in
`identity.infrastructure.web` (`MeController`, `MeResponse`, `UpdateProfileRequest`,
`IdentityExceptionHandler`) plus a `UserRestrictionRepository` port and `JpaUserRestrictionRepository`
adapter.
- `GET /api/me` → 200 `{ accountId, role, pseudonym, avatarUrl?, locale, version }`. Self-scoped:
  the account id is read from `authentication.getName()` (the opaque id chunk 5's converter set),
  so there is no client-supplied identifier and no way to address another user. `role` comes from
  the granted authority (no DB hit). **Email deliberately omitted** (least exposure).
- `PATCH /api/me/profile` → `ProfileService.updateProfile`; `version` carries optimistic concurrency.
- **Restriction enforcement wired** (the review's reassigned "403 restricted"): `ProfileService`
  gained a `UserRestrictionRepository` dependency and throws `AccountRestrictedException` when a
  restriction `isActiveAt(now)`. The domain's `isActiveAt` is the authoritative gate; the adapter's
  `@Query` (`start_at <= :asOf and (end_at is null or end_at > :asOf)`, on the existing index) is an
  index-friendly pre-filter. This finally uses chunk 4's dormant `UserRestrictionJpaMapper` /
  `SpringDataUserRestrictionRepository`.
- `IdentityExceptionHandler` maps: `AccountRestrictedException`→403 `ACCOUNT_RESTRICTED`,
  `AccountClosedException`→403 `ACCOUNT_CLOSED`, `OptimisticLockConflictException`→409
  `PROFILE_VERSION_CONFLICT`, `PseudonymAlreadyInUseException`→422 `PSEUDONYM_TAKEN`,
  `InvalidPseudonymException`→422 `INVALID_PSEUDONYM`, `AccountNotFoundException`→404
  `ACCOUNT_NOT_FOUND`, `IllegalArgumentException`→400 `INVALID_REQUEST`. 401 stays with the
  resource-server entry point. No stack traces / SQL / internal state leak.
- No migration, no app-module change, no new dependency. `ProfileService` bean wiring updated in
  `IdentityBeanConfiguration`.

### Chunk 5 — security wiring (merged to `main`)

Turns a request into an authenticated account. New in `identity.infrastructure`:
- `security/HmacAuthSubjectHasher` — HMAC-SHA256(subject, pepper) as 64 lowercase hex chars (satisfies `V2.4`'s `account_auth_subject_hash_format_check`; a base64 rendering would be rejected). Blank pepper → `IllegalArgumentException` at construction, so the context fails fast rather than hashing with an empty key.
- `security/IdentityJwtAuthenticationConverter` — resolves/auto-provisions the account and grants exactly one authority, `ROLE_<account.role>`, derived **only** from the DB. No JWT claim (`roles`/`scope`/`authorities`) ever contributes an authority. Missing `sub` → `InvalidBearerTokenException`; closed account → `DisabledException` (no resurrection). Principal name is the opaque account id, not the raw subject.
- `IdentitySecurityProperties` (`@ConfigurationProperties identity.auth`) + `IdentityBeanConfiguration` wiring the framework-free application services, the hasher, a `SecureRandom`-backed pseudonym suffix supplier, and a `Clock`.

`AccountProvisioningService.provision` now catches `AuthSubjectAlreadyProvisionedException` from `create(...)` and re-reads the race winner (the plan's provisioning-race strategy); a `requireActive` helper keeps the closed-account check in one place.

App composition root: `SecurityConfiguration` replaced with the real chain — `/actuator/health` `permitAll`, `anyRequest().authenticated()`, stateless, CSRF off, JWT resource server with the identity converter **injected by its Spring type** `Converter<Jwt, AbstractAuthenticationToken>` so the app keeps no compile dependency on identity internals (module-boundary rule). `application.yml` gains `identity.auth.subject-pepper: ${IDENTITY_AUTH_SUBJECT_PEPPER:}` (empty default → fail fast); `app/build.gradle.kts` sets a fixed non-secret test pepper on the test task so the context boots in tests.

On this fix branch, after an independent read-only review of `72d6c94..ebdcaea` by Claude Code (Codex implemented chunks 3–4, so the review was a fresh independent session):

- `fa85679` — `spotlessApply` on four Chunk 4 files. They were merged unformatted, so `./gradlew check` failed and the `governance-check` CI job was red on `main`. The Chunk 4 handoff had claimed `./scripts/check.sh` passed; it did not.
- `8cde797` — two substantive review findings:
  - **`V2.3` let a raw OIDC subject survive as a fake hash.** It renamed `auth_subject` to `auth_subject_hash` and retyped it to `CHAR(64)` without validating existing values; `CHAR` blank-pads shorter values, so a pre-V2.3 row keeps its raw subject and the rename reports success. Reproduced against Postgres: `google-oauth2|1092847561` survived intact in the renamed column. Chunk 5 resolves accounts by HMAC and would never match such a row again, orphaning exactly the reversible identifier ADR-0006 exists to remove. `V2.4` adds `account_auth_subject_hash_format_check` (64-char lowercase hex), which validates existing rows and therefore fails the migration loudly instead; the file carries the remediation SQL. It also prevents a non-hex Chunk 5 hasher (base64 is 44 chars) from being silently padded to 64.
  - **`JpaIdentityPersistenceAdapter.create` translated no constraint violation**, so a provisioning race leaked `DataIntegrityViolationException` — a Spring infrastructure type — out of the application port. Both unique constraints are now mapped to domain exceptions (`PseudonymAlreadyInUseException`, the new `AuthSubjectAlreadyProvisionedException`), matched by constraint name so an unrelated integrity failure is no longer reported to the user as "pseudonym already taken". Writes are explicitly flushed: left to commit-time the violation would have been raised after the method returned, where no `catch` could reach it.
  - `V2.4` also renames `account_auth_subject_key`, which `V2.3` left pointing at a column that no longer exists.

## Remaining work

Chunks 7–8 remain (see the plan). Carry-forward items:

- **Chunk 7 must not serialize the domain `Account`.** `AdminAccountLookupService.findAccount` returns the whole object, including `authSubjectHash`; the admin controller needs a DTO that omits it. Chunk 6's `MeResponse` is the DTO pattern to follow. `V2.5` is reserved for the admin audit table.
- **Chunk 8** (`V2.6` self-service-request table) owns export/delete with idempotency; deletion must block re-provisioning of a deleted subject (chunk 5 already 401s closed accounts at auth via `DisabledException`).
- The `IdentityExceptionHandler` (`@RestControllerAdvice`) is currently global; when other modules add controllers, confirm the mapping scope is still correct or narrow it.

Accepted, unfixed nits (unchanged from earlier chunks): provisioning `create()` issues `merge` rather than `persist` (an extra SELECT); the pseudonym-collision-on-create path is translated but not retried (only the auth-subject race is). Correctness is unaffected.

## Decisions made

- Store only `HMAC-SHA256(subject, pepper)`, never the raw OIDC subject (ADR-0006). The stored form is **lowercase hex**, now enforced by a database CHECK.
- Keep security-filter-chain composition in the app module and resolve roles from the identity database, not JWT role claims.
- Keep `identity.api` empty until another module has a concrete cross-module use case.
- Use schema-local Flyway migrations and append-only fix-forward changes after merge. `V2.3` was not edited; `V2.4` fixes forward.
- Treat restrictions as time-bounded facts rather than an account status.
- The provisioning persistence port owns atomic creation of the account/profile pair, and now documents the two conflicts it can raise.
- The unique constraint — not the `isPseudonymInUse` check-then-act — is the authority on both pseudonym and auth-subject uniqueness. Chunk 5's race retry hangs off `AuthSubjectAlreadyProvisionedException`.
- The migration registry now lives in the plan: `V2.5` is reserved for Chunk 7's audit table and `V2.6` for Chunk 8's self-service table (both shifted by one, since this fix took `V2.4`).
- The app module explicitly registers identity's persistence package with JPA; component scanning alone does not extend Spring Data repository/entity auto-discovery beyond the app package.

## Assumptions

- ADR-0006's claim that no production account data exists is taken at face value; `V2.4` is written so that if it is wrong, the migration fails loudly rather than corrupting data silently.
- No environment has applied `V2.3` with account rows present. If one has, `V2.4` will fail there and the remediation SQL in the migration file applies.

## Files changed on this branch (chunk 6)

New:
- `modules/identity/.../application/UserRestrictionRepository.java`
- `modules/identity/.../infrastructure/persistence/JpaUserRestrictionRepository.java`
- `modules/identity/.../infrastructure/web/MeController.java`, `MeResponse.java`, `UpdateProfileRequest.java`, `IdentityExceptionHandler.java`
- `app/.../identity/MeEndpointIntegrationTest.java`

Edited:
- `modules/identity/.../application/ProfileService.java` (restriction dependency + `isActiveAt` check)
- `modules/identity/.../application/ProfileServiceTest.java` (restriction fake + active/expired cases)
- `modules/identity/.../infrastructure/persistence/SpringDataUserRestrictionRepository.java` (`findActive` query)
- `modules/identity/.../infrastructure/IdentityBeanConfiguration.java` (`profileService` dependency)
- `docs/plans/002-identity-module.md`, `docs/handoffs/current-task.md`

No app-module main-source change, no migration, no new dependency.

## Tests and verification

Run on this branch, 2026-07-15:

- `./gradlew :modules:identity:check` — passed. `ProfileServiceTest` (7, incl. active-restriction→403 and expired-restriction→allowed).
- `./gradlew :app:test` — passed, incl. ArchUnit (`web`/`persistence` stay in `infrastructure`; domain/application acquire no Spring dependency). `MeEndpointIntegrationTest` (7) drives the full matrix end-to-end through the real chain via a stub `JwtDecoder`: anonymous→401, first request auto-provisions, update bumps version, stale version→409, duplicate pseudonym→422, malformed pseudonym→422, active restriction→403.
- `./scripts/check.sh` — passed (governance + full Gradle gate; frontend skipped).

The stub-`JwtDecoder` approach is what makes the converter run for real in tests (a `jwt()` post-processor would bypass it). No live-IdP smoke test is possible (ADR-0005).

## Known failures

None.

## Risks and unresolved questions

- The real pepper must be provisioned in every non-test environment via `IDENTITY_AUTH_SUBJECT_PEPPER`, and per ADR-0006 it cannot be rotated without invalidating existing lookups. A blank value fails startup by design.
- Earlier chunks (3–4) reached `origin/main` before independent review. If that sequencing must hold, it needs a branch-protection guard, not just plan text. Chunks 5 and 6 followed the process (branched from `main`, awaiting review before merge).
- No rate limiting on `/api/me` yet (API_GUIDELINES lists it as a general concern; not in this plan's scope).

## Human actions required

Review and merge `feat/002-identity-chunk6-me-endpoints` into `main` after a fresh independent review (this branch was implemented by Claude Code; the review must be a fresh independent pass — e.g. Codex — not self-review). The branch is local and not pushed; there is no credential path to push from this environment.

## Recommended next action

Obtain a fresh independent review of `feat/002-identity-chunk6-me-endpoints`, address only actionable findings in a separate fix phase, then merge to `main`. Do not start Chunk 7 automatically; when it starts, fold in the admin `Account`→DTO carry-forward item above and reserve `V2.5` for the audit table.

## Last updated

2026-07-15

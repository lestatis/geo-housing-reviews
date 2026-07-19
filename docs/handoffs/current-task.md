# Task handoff

## Objective

Complete the identity backend module described in `docs/plans/002-identity-module.md`: vendor-neutral OAuth2 authentication, account/profile/restriction behavior, persistence, self-service and admin endpoints, and identity-local export/deletion.

## Active branch

`feat/002-identity-chunk7-admin-audit` (branched from `main` at `1851e62`, after chunk 6 merged)

## Related issue or plan

No issue. See `docs/plans/002-identity-module.md`.

## Current status

chunk7_implemented — ready for fresh independent review and human review before merge

## Completed work

Observable committed work on `main`:

- Chunk 1 foundation in `f8cc350`: OAuth2 resource-server dependencies and configuration, minimal health-only security configuration, account/profile migrations, migration integration test, ADR-0005, and the execution plan.
- Chunk 1 correction in `506353b`: database constraints and negative-path migration coverage for account roles/statuses.
- Chunk 2 domain model in `9b79b87`: framework-free account, public profile, pseudonym, restriction domain types and unit tests.
- Chunk 2 corrections in `16b0dee`: collision-proof anonymization tombstones, HMAC-hash-only auth subject design, ADR-0006, and plan updates.
- Chunk 3 application layer in `0c5c3af`: repository/hasher ports, `PseudonymAllocator`, provisioning/profile/admin services, in-memory-fake unit tests.
- Chunk 4 persistence in `ebdcaea`: `V2.3` migration, JPA entities/mappers/repositories, `JpaIdentityPersistenceAdapter`, migration and adapter integration tests.
- Chunk 3–4 review fix merged to `main` as `b0eaf2e` (spotless, `V2.4` hash-format CHECK + constraint rename, adapter constraint translation, docs). CI green again.
- Chunk 5 security wiring merged to `main` as `1922d71` (JWT converter with DB-derived role, HMAC hasher, real filter chain, provisioning-race retry).
- Chunk 6 `/api/me` endpoints merged to `main` as `1851e62` (GET/PATCH, RFC 7807 handler, restriction enforcement). Both merged out-of-band during the next chunk's planning; this handoff trusts their fresh independent reviews happened (git shows them on `main`).

Chunks 3 and 4 were merged and pushed to `origin/main` **before** independent review, contrary to the per-chunk process in the plan (self-check → independent review → human review → merge).

### Chunk 7 — admin RBAC + audit (this branch, not yet reviewed)

First admin capability and the module's audit trail.
- `GET /api/admin/accounts/{id}` gated by `/api/admin/** → hasRole('ADMIN')` in the app
  `SecurityConfiguration` (URL-based; no method security in the codebase). Role comes from
  `account.role` via chunk 5's converter, so a spoofed claim cannot elevate. `USER`→403, anon→401.
- `V2.5` adds append-only `identity.admin_audit_event`. **`target_account_id` is deliberately not a
  foreign key** so a lookup of a non-existent id is still auditable (`NOT_FOUND`); the first cut had
  the FK and it rejected exactly that insert — the integration test now guards it. `admin_account_id`
  keeps its FK (the acting admin always exists).
- Domain `AdminAuditEvent` + `AdminAuditAction`/`AdminAuditOutcome` (framework-free); port
  `AdminAuditEventRepository` + `JpaAdminAuditEventRepository` adapter.
- `AdminAccountService` **replaces** `AdminAccountLookupService` (which only its own test used, and
  whose Javadoc anticipated this): it looks up the target and records an audit event on every call,
  returning `Optional` (empty → controller throws `AccountNotFoundException` → 404) so the audit is
  on the committing path. **Fail-closed:** an audit-write failure propagates, so no account data is
  returned unaudited.
- `AdminAccountView` DTO omits `authSubjectHash` **and** email (the review carry-forward; the
  integration test asserts `authSubjectHash` never appears in the body). Shared
  `WebAuthentication.accountId(Authentication)` helper now used by both `MeController` and
  `AdminAccountController`.
- First-ADMIN promotion is a manual SQL `UPDATE` by design (no self-service role escalation).

### Chunk 6 — `/api/me` self-service endpoints (merged to `main`)

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

Chunk 8 remains (see the plan): export/delete with idempotency (`V2.6` `identity.self_service_request`); deletion must block re-provisioning of a deleted subject (chunk 5 already 401s closed accounts at auth via `DisabledException`).

Carry-forward notes for chunk 8 / later:
- The `IdentityExceptionHandler` (`@RestControllerAdvice`) is global; when other modules add controllers, confirm the mapping scope is still correct or narrow it.
- Access-denied (403) requests are blocked at the filter and are **not** audited; if failed-authorization auditing is wanted, add it (out of scope for chunk 7).
- MFA/step-up for admins (SECURITY_PRIVACY.md §4) is still deferred to a chosen IdP (ADR-0005).

Accepted, unfixed nits (unchanged): provisioning `create()` issues `merge` rather than `persist` (an extra SELECT); the pseudonym-collision-on-create path is translated but not retried (only the auth-subject race is). Correctness is unaffected.

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

## Files changed on this branch (chunk 7)

New:
- `db/migration/identity/V2.5__create_admin_audit_event.sql`
- `modules/identity/.../domain/AdminAuditEvent.java`, `AdminAuditAction.java`, `AdminAuditOutcome.java`
- `modules/identity/.../application/AdminAuditEventRepository.java`, `AdminAccountService.java`
- `modules/identity/.../infrastructure/persistence/AdminAuditEventJpaEntity.java`, `AdminAuditEventJpaMapper.java`, `SpringDataAdminAuditEventRepository.java`, `JpaAdminAuditEventRepository.java`
- `modules/identity/.../infrastructure/web/AdminAccountController.java`, `AdminAccountView.java`, `WebAuthentication.java`
- `modules/identity/.../application/AdminAccountServiceTest.java`
- `app/.../identity/AdminAccountEndpointIntegrationTest.java`

Removed: `AdminAccountLookupService.java` + `AdminAccountLookupServiceTest.java` (replaced by `AdminAccountService`).

Edited:
- `app/.../config/SecurityConfiguration.java` (`/api/admin/** → hasRole('ADMIN')`)
- `modules/identity/.../infrastructure/web/MeController.java` (use `WebAuthentication`)
- `modules/identity/.../infrastructure/IdentityBeanConfiguration.java` (bean swap)
- `docs/plans/002-identity-module.md`, `docs/handoffs/current-task.md`

No new dependency; one migration (`V2.5`).

## Tests and verification

Run on this branch, 2026-07-15:

- `./gradlew :modules:identity:check` — passed. `AdminAccountServiceTest` (3): FOUND audit, NOT_FOUND audit, audit-failure propagation.
- `./gradlew :app:test` — passed, incl. ArchUnit (new domain types framework-free; `web`/`persistence` in `infrastructure`). `AdminAccountEndpointIntegrationTest` (4): anon→401, USER→403, ADMIN→200 with no `authSubjectHash` in the body + a `FOUND` audit row, unknown→404 with a `NOT_FOUND` audit row.
- `./scripts/check.sh` — passed (governance + full Gradle gate; frontend skipped).

First cut of `V2.5` had a foreign key on `target_account_id`, which rejected auditing a lookup of a non-existent account; caught by the NOT_FOUND integration test, fixed by dropping that FK (kept on `admin_account_id`). ADMIN role in tests is set by direct SQL `update`, mirroring the manual first-admin bootstrap.

## Known failures

None.

## Risks and unresolved questions

- The real pepper must be provisioned in every non-test environment via `IDENTITY_AUTH_SUBJECT_PEPPER`, and per ADR-0006 it cannot be rotated without invalidating existing lookups. A blank value fails startup by design.
- Earlier chunks (3–4) reached `origin/main` before independent review. If that sequencing must hold, it needs a branch-protection guard, not just plan text. Chunks 5–7 followed the process (branched from `main`, awaiting review before merge).
- Audit log has no retention/rotation or restricted-read enforcement yet beyond DB grants (SECURITY_PRIVACY.md wants "restricted access" and a retention policy — a later, cross-cutting concern).

## Human actions required

Review and merge `feat/002-identity-chunk7-admin-audit` into `main` after a fresh independent review (this branch was implemented by Claude Code; the review must be a fresh independent pass — e.g. Codex — not self-review). The branch is local and not pushed; there is no credential path to push from this environment.

## Recommended next action

Obtain a fresh independent review of `feat/002-identity-chunk7-admin-audit`, address only actionable findings in a separate fix phase, then merge to `main`. Do not start Chunk 8 automatically; it is the last chunk (export/delete, `V2.6`).

## Last updated

2026-07-15

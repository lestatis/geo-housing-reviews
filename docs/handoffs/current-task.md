# Task handoff

## Objective

Complete the identity backend module described in `docs/plans/002-identity-module.md`: vendor-neutral OAuth2 authentication, account/profile/restriction behavior, persistence, self-service and admin endpoints, and identity-local export/deletion.

## Active branch

`feat/002-identity-chunk5-security` (branched from `main` at `b0eaf2e`, after the chunk 3–4 review fix merged)

## Related issue or plan

No issue. See `docs/plans/002-identity-module.md`.

## Current status

chunk5_implemented — ready for fresh independent review and human review before merge

## Completed work

Observable committed work on `main`:

- Chunk 1 foundation in `f8cc350`: OAuth2 resource-server dependencies and configuration, minimal health-only security configuration, account/profile migrations, migration integration test, ADR-0005, and the execution plan.
- Chunk 1 correction in `506353b`: database constraints and negative-path migration coverage for account roles/statuses.
- Chunk 2 domain model in `9b79b87`: framework-free account, public profile, pseudonym, restriction domain types and unit tests.
- Chunk 2 corrections in `16b0dee`: collision-proof anonymization tombstones, HMAC-hash-only auth subject design, ADR-0006, and plan updates.
- Chunk 3 application layer in `0c5c3af`: repository/hasher ports, `PseudonymAllocator`, provisioning/profile/admin services, in-memory-fake unit tests.
- Chunk 4 persistence in `ebdcaea`: `V2.3` migration, JPA entities/mappers/repositories, `JpaIdentityPersistenceAdapter`, migration and adapter integration tests.
- Chunk 3–4 review fix merged to `main` as `b0eaf2e` (spotless, `V2.4` hash-format CHECK + constraint rename, adapter constraint translation, docs). CI green again.

Chunks 3 and 4 were merged and pushed to `origin/main` **before** independent review, contrary to the per-chunk process in the plan (self-check → independent review → human review → merge).

### Chunk 5 — security wiring (this branch, not yet reviewed)

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

Chunks 6–8 remain (see the plan). Carry-forward items for those chunks:

- **Restriction enforcement is owned by Chunk 6.** It is an acceptance criterion ("403 restricted") that no chunk originally claimed. `AccountRestrictedException` is never thrown, there is no `UserRestrictionRepository` port, and `ProfileService.updateProfile` checks for a closed account but not an active restriction. Chunk 4 shipped the table, JPA entity, mapper and Spring Data repository for restrictions, but no port, so they are currently **unused code** — Chunk 6 should wire them rather than add more.
- **Chunk 6 also owns the end-to-end auth status codes.** Chunk 5 unit-tests the converter and adds one app-level fail-closed guard (anonymous → 401), but the full 401/403/409/422 matrix lands with the `/api/me` endpoints and their `ProblemDetail` mapping. A `MockMvc` test with the `jwt()` post-processor belongs there; note that `jwt()` bypasses the real converter/decoder, so to exercise `IdentityJwtAuthenticationConverter` end-to-end a stubbed `JwtDecoder` is needed.
- **Chunk 7 must not serialize the domain `Account`.** `AdminAccountLookupService.findAccount` returns the whole object, including `authSubjectHash`; the admin controller needs a DTO that omits it.

Accepted, unfixed nits: `create()` uses `JpaRepository.save()` on entities with an assigned id and a primitive `@Version`, so Spring Data issues `merge` rather than `persist` — an extra SELECT per provisioning. The pseudonym-collision-on-create path is translated to a domain exception but not retried (astronomically unlikely with a random hex suffix); only the auth-subject race is retried. Correctness is unaffected in both.

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

## Files changed on this branch (chunk 5)

New:
- `modules/identity/.../infrastructure/security/HmacAuthSubjectHasher.java`
- `modules/identity/.../infrastructure/security/IdentityJwtAuthenticationConverter.java`
- `modules/identity/.../infrastructure/IdentitySecurityProperties.java`
- `modules/identity/.../infrastructure/IdentityBeanConfiguration.java`
- `modules/identity/.../infrastructure/security/HmacAuthSubjectHasherTest.java`
- `modules/identity/.../infrastructure/security/IdentityJwtAuthenticationConverterTest.java`

Edited:
- `modules/identity/.../application/AccountProvisioningService.java` (race retry, `requireActive` helper)
- `modules/identity/.../application/AccountProvisioningServiceTest.java` (two race tests + `RacingProvisioningRepository`)
- `app/.../config/SecurityConfiguration.java` (real JWT chain)
- `app/.../GeoHousingApplicationIntegrationTest.java` (fail-closed guard)
- `app/src/main/resources/application.yml` (pepper placeholder)
- `app/build.gradle.kts` (test pepper on the test task)
- `docs/plans/002-identity-module.md`, `docs/handoffs/current-task.md`

## Tests and verification

Run on this branch, 2026-07-15:

- `./gradlew :modules:identity:check` — passed (compile, unit tests, spotless, checkstyle). New: `HmacAuthSubjectHasherTest` (6), `IdentityJwtAuthenticationConverterTest` (6), `AccountProvisioningServiceTest` (7, incl. the two race tests).
- `./gradlew :app:test` — passed; the context boots with the real security chain and the `jwk-set-uri` placeholder is not dereferenced at startup (ADR-0005 confirmed). `GeoHousingApplicationIntegrationTest` (3, incl. fail-closed `/api/me` → 401).
- `./scripts/check.sh` — passed (governance + full Gradle gate; frontend skipped). One spotless miss on the app module was caught by the gate and fixed before this entry.
- The anti-spoofing invariant is asserted directly: `derivesRoleFromTheAccountAndIgnoresSpoofedClaims` builds a JWT with `roles`/`scope`/`authorities` all claiming ADMIN over a DB account of role USER, and the resulting authorities are exactly `[ROLE_USER]`.

No live-IdP smoke test is possible yet (no vendor; ADR-0005 defers it). `jwt()`-post-processor end-to-end tests arrive with chunk 6.

## Known failures

None.

## Risks and unresolved questions

- Restriction enforcement does not exist yet; until Chunk 6 lands, a restricted account is not actually prevented from editing its profile.
- The real pepper must be provisioned in every non-test environment via `IDENTITY_AUTH_SUBJECT_PEPPER`, and per ADR-0006 it cannot be rotated without invalidating existing lookups. A blank value fails startup by design.
- Chunks 3–4 reached `origin/main` before independent review. If that sequencing must hold, it needs a branch-protection guard, not just plan text. Chunk 5 followed the process: branched from `main` after the fix merged, awaiting review before merge.

## Human actions required

Review and merge `feat/002-identity-chunk5-security` into `main` after a fresh independent review (this branch was implemented by Claude Code; review must be a fresh independent pass, not self-review). The branch is local and not pushed; there is no credential path to push from this environment.

## Recommended next action

Obtain a fresh independent review of `feat/002-identity-chunk5-security`, address only actionable findings in a separate fix phase, then merge to `main`. Do not start Chunk 6 automatically; when it starts, fold in the restriction-enforcement, auth-status-code, and admin-DTO carry-forward items above.

## Last updated

2026-07-15

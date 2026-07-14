# Task handoff

## Objective

Complete the identity backend module described in `docs/plans/002-identity-module.md`: vendor-neutral OAuth2 authentication, account/profile/restriction behavior, persistence, self-service and admin endpoints, and identity-local export/deletion.

## Active branch

`fix/002-identity-chunk4-review-findings` (branched from `main` at `c45423b`)

## Related issue or plan

No issue. See `docs/plans/002-identity-module.md`.

## Current status

review_findings_fixed — awaiting human review and merge of the fix branch

## Completed work

Observable committed work on `main`:

- Chunk 1 foundation in `f8cc350`: OAuth2 resource-server dependencies and configuration, minimal health-only security configuration, account/profile migrations, migration integration test, ADR-0005, and the execution plan.
- Chunk 1 correction in `506353b`: database constraints and negative-path migration coverage for account roles/statuses.
- Chunk 2 domain model in `9b79b87`: framework-free account, public profile, pseudonym, restriction domain types and unit tests.
- Chunk 2 corrections in `16b0dee`: collision-proof anonymization tombstones, HMAC-hash-only auth subject design, ADR-0006, and plan updates.
- Chunk 3 application layer in `0c5c3af`: repository/hasher ports, `PseudonymAllocator`, provisioning/profile/admin services, in-memory-fake unit tests.
- Chunk 4 persistence in `ebdcaea`: `V2.3` migration, JPA entities/mappers/repositories, `JpaIdentityPersistenceAdapter`, migration and adapter integration tests.

Chunks 3 and 4 were merged and pushed to `origin/main` **before** independent review, contrary to the per-chunk process in the plan (self-check → independent review → human review → merge).

On this fix branch, after an independent read-only review of `72d6c94..ebdcaea` by Claude Code (Codex implemented chunks 3–4, so the review was a fresh independent session):

- `fa85679` — `spotlessApply` on four Chunk 4 files. They were merged unformatted, so `./gradlew check` failed and the `governance-check` CI job was red on `main`. The Chunk 4 handoff had claimed `./scripts/check.sh` passed; it did not.
- `8cde797` — two substantive review findings:
  - **`V2.3` let a raw OIDC subject survive as a fake hash.** It renamed `auth_subject` to `auth_subject_hash` and retyped it to `CHAR(64)` without validating existing values; `CHAR` blank-pads shorter values, so a pre-V2.3 row keeps its raw subject and the rename reports success. Reproduced against Postgres: `google-oauth2|1092847561` survived intact in the renamed column. Chunk 5 resolves accounts by HMAC and would never match such a row again, orphaning exactly the reversible identifier ADR-0006 exists to remove. `V2.4` adds `account_auth_subject_hash_format_check` (64-char lowercase hex), which validates existing rows and therefore fails the migration loudly instead; the file carries the remediation SQL. It also prevents a non-hex Chunk 5 hasher (base64 is 44 chars) from being silently padded to 64.
  - **`JpaIdentityPersistenceAdapter.create` translated no constraint violation**, so a provisioning race leaked `DataIntegrityViolationException` — a Spring infrastructure type — out of the application port. Both unique constraints are now mapped to domain exceptions (`PseudonymAlreadyInUseException`, the new `AuthSubjectAlreadyProvisionedException`), matched by constraint name so an unrelated integrity failure is no longer reported to the user as "pseudonym already taken". Writes are explicitly flushed: left to commit-time the violation would have been raised after the method returned, where no `catch` could reach it.
  - `V2.4` also renames `account_auth_subject_key`, which `V2.3` left pointing at a column that no longer exists.

## Remaining work

Chunks 5–8 remain (see the plan). Two review items were deliberately routed into later chunks rather than fixed here:

- **Restriction enforcement is now owned by Chunk 6.** It was an acceptance criterion ("403 restricted") that no chunk claimed. `AccountRestrictedException` is never thrown, there is no `UserRestrictionRepository` port, and `ProfileService.updateProfile` checks for a closed account but not an active restriction. Chunk 4 shipped the table, JPA entity, mapper and Spring Data repository for restrictions, but no port, so they are currently **unused code** — Chunk 6 should wire them rather than add more.
- **Chunk 7 must not serialize the domain `Account`.** `AdminAccountLookupService.findAccount` returns the whole object, including `authSubjectHash`; the admin controller needs a DTO that omits it.

One accepted, unfixed nit: `create()` uses `JpaRepository.save()` on entities with an assigned id and a primitive `@Version`, so Spring Data issues `merge` rather than `persist` — an extra SELECT per provisioning. Correctness is unaffected.

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

## Files changed on this branch

- `apps/api/modules/identity/src/main/resources/db/migration/identity/V2.4__constrain_auth_subject_hash_format.sql` (new)
- `apps/api/modules/identity/src/main/java/com/example/geohousing/identity/domain/AuthSubjectAlreadyProvisionedException.java` (new)
- `apps/api/modules/identity/src/main/java/com/example/geohousing/identity/infrastructure/persistence/JpaIdentityPersistenceAdapter.java`
- `apps/api/modules/identity/src/main/java/com/example/geohousing/identity/application/IdentityProvisioningRepository.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/identity/IdentityPersistenceIntegrationTest.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/identity/IdentityMigrationIntegrationTest.java`
- four Chunk 4 persistence files, formatting only (`fa85679`)
- `docs/plans/002-identity-module.md`, `docs/handoffs/current-task.md`

## Tests and verification

Run on this branch, on 2026-07-14:

- `./gradlew :modules:identity:spotlessCheck` — **failed** on `main` before the fix (four files), confirming CI was red. Passes now.
- `./gradlew :modules:identity:check :app:test` — passed. `IdentityMigrationIntegrationTest` 5 tests, `IdentityPersistenceIntegrationTest` 6 tests, 0 failures, 0 skipped.
- `./scripts/check.sh` — passed (governance validation plus the full Gradle check, including Testcontainers-backed app tests; frontend checks skipped, no frontend scaffold).
- `V2.4` was exercised directly against the dev Postgres before being wired in: it applies cleanly to an empty database, accepts a 64-char hex digest, rejects a non-hex value, and fails with `check constraint ... is violated by some row` on a database seeded with a pre-V2.3 raw subject.

New regression tests: duplicate pseudonym on create, duplicate auth-subject hash on create, rollback of the account when the profile insert fails (the port's atomicity contract, previously asserted only against an in-memory fake), and rejection of a raw subject by the format constraint.

The Chunk 4 handoff's claim that `./scripts/check.sh` passed was false and has been corrected here and in the plan's progress log.

## Known failures

None.

## Risks and unresolved questions

- The HMAC port still has no implementation. Chunk 5 must emit lowercase hex or `V2.4`'s CHECK will reject every insert, and must never pass a raw auth subject to a repository or persisted object.
- Restriction enforcement does not exist yet; until Chunk 6 lands, a restricted account is not actually prevented from editing its profile.
- `main` was pushed before independent review for chunks 3–4. If that sequencing is meant to hold, the process needs a guard, not just the plan text.

## Human actions required

None. The fix branch is local and not pushed.

## Recommended next action

Human-review and merge `fix/002-identity-chunk4-review-findings` into `main` (this restores a green CI). Do not start Chunk 5 automatically; when it starts, take the restriction-enforcement and admin-DTO items above as part of its plan.

## Last updated

2026-07-14

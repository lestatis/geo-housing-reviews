# Task handoff

## Objective

Complete the identity backend module described in `docs/plans/002-identity-module.md`: vendor-neutral OAuth2 authentication, account/profile/restriction behavior, persistence, self-service and admin endpoints, and identity-local export/deletion.

## Active branch

`feat/002-identity-chunk3-application`

## Related issue or plan

No issue. See `docs/plans/002-identity-module.md`.

## Current status

ready_for_review

## Completed work

Observable committed work on `main`:

- Chunk 1 foundation in `f8cc350`: OAuth2 resource-server dependencies and configuration, minimal health-only security configuration, account/profile migrations, migration integration test, ADR-0005, and the execution plan.
- Chunk 1 correction in `506353b`: database constraints and negative-path migration coverage for account roles/statuses.
- Chunk 2 domain model in `9b79b87`: framework-free account, public profile, pseudonym, restriction domain types and unit tests.
- Chunk 2 corrections in `16b0dee`: collision-proof anonymization tombstones, HMAC-hash-only auth subject design, ADR-0006, and plan updates.

The domain and migration test files described by those commits exist in the working tree. No uncommitted identity changes were present when this handoff was recovered.

- 2026-07-14: Created `feat/002-identity-chunk3-application` from committed `main` to begin chunk 3 only.
- 2026-07-14: Completed chunk 3 application ports/services and 11 in-memory-fake unit tests. No schema, infrastructure, endpoint, or security-wiring changes were made.

## Remaining work

According to the plan and current file tree, chunks 4–8 remain:

- persistence adapters and fix-forward migrations, including `auth_subject_hash` and restrictions;
- JWT/security wiring and provisioning-race handling;
- `/api/me` profile endpoints and error handling;
- admin account lookup with authorization and audit;
- idempotent export and deletion.

The plan acceptance criteria remain unchecked because later chunks implement the end-to-end behavior. This branch needs a fresh independent read-only review and human merge decision before chunk 4 starts.

## Decisions made

- Store only `HMAC-SHA256(subject, pepper)`, never the raw OIDC subject (ADR-0006).
- Keep security-filter-chain composition in the app module and resolve roles from the identity database, not JWT role claims.
- Keep `identity.api` empty until another module has a concrete cross-module use case.
- Use schema-local Flyway migrations and append-only fix-forward changes after merge.
- Treat restrictions as time-bounded facts rather than an account status.
- The chunk-3 provisioning persistence port owns atomic creation of the account/profile pair; its future adapter must implement that atomicity.
- Default pseudonym generation retries uniqueness through the profile repository and fails after 100 collisions instead of looping indefinitely.

## Assumptions

- Inference: the four identity commits have already landed on local `main`, because they are ancestors of `HEAD`; no claim is made about a remote merge or pull request.
- Inference: chunk 3 is next because the plan orders it after the committed foundation/domain chunks and no application implementation beyond `package-info.java` exists.
- Historical commit messages report local checks and independent Codex reviews; this recovered handoff does not independently certify those past results.

## Files changed

Committed identity work includes:

- `apps/api/app/src/main/java/com/example/geohousing/app/config/SecurityConfiguration.java`
- `apps/api/app/src/main/resources/application.yml`
- `apps/api/app/src/test/java/com/example/geohousing/app/identity/IdentityMigrationIntegrationTest.java`
- `apps/api/modules/identity/build.gradle.kts`
- `apps/api/modules/identity/src/main/java/com/example/geohousing/identity/domain/`
- `apps/api/modules/identity/src/main/resources/db/migration/identity/`
- `apps/api/modules/identity/src/test/java/com/example/geohousing/identity/domain/`
- `docs/adr/0005-oauth2-resource-server-vendor-agnostic.md`
- `docs/adr/0006-hash-auth-subject.md`
- `docs/plans/002-identity-module.md`

See `git show --stat f8cc350 506353b 9b79b87 16b0dee` for the exact committed file list.

The current feature-branch commit contains chunk-3 work in:

- `apps/api/modules/identity/src/main/java/com/example/geohousing/identity/application/`
- `apps/api/modules/identity/src/test/java/com/example/geohousing/identity/application/`
- `docs/plans/002-identity-module.md`
- `docs/handoffs/current-task.md`

## Commands run

During recovery:

- `git branch --show-current`
- `git status --short --branch`
- `git diff`
- `git diff --staged`
- `git log -8 --oneline --decorate`
- `git show --stat --oneline --summary f8cc350 506353b 9b79b87 16b0dee`
- `git show -s --format=fuller f8cc350 506353b 9b79b87 16b0dee`
- identity source/test file inventory via `rg --files`
- baseline governance and repository checks listed below
- `./gradlew :modules:identity:test`
- `./gradlew :modules:identity:check`
- `./gradlew :app:test --tests "*ModuleBoundaryArchitectureTest*"`
- `./scripts/check.sh`

## Tests and verification

- `python3 scripts/validate_repo_governance.py` — passed during recovery before governance changes: 15 required files and 5 shared skills.
- `./scripts/check.sh` — governance passed, then Gradle could not create a cache lock in the sandbox's read-only default user cache.
- `GRADLE_USER_HOME=/tmp/geo-housing-gradle ./scripts/check.sh` — governance passed, then Gradle wrapper download failed because sandbox network access was unavailable.
- Commit `9b79b87` reports 33 passing plain-JUnit domain tests and an empirical ArchUnit negative check. This is historical commit evidence, not a test rerun during recovery.
- Commits `506353b` and `16b0dee` report independent local Codex review fixes. This is historical commit evidence, not a new review.
- `./gradlew :modules:identity:test` — passed: 37 tests, after correcting one new-test assertion that initially checked the `AtomicReference` object rather than its value.
- `./gradlew :modules:identity:check` — passed: compile, 37 tests, Spotless, and Checkstyle.
- `./gradlew :app:test --tests "*ModuleBoundaryArchitectureTest*"` — passed.
- `./scripts/check.sh` — passed: governance validation, all Gradle checks including Testcontainers-backed app tests; frontend checks correctly skipped because no frontend scaffold exists.

## Known failures

- `docs/plans/002-identity-module.md` has a stale progress log that mentions only plan creation; Git commits provide newer evidence and take priority.

## Risks and unresolved questions

- The HMAC port deliberately has no implementation yet; raw auth subjects must never be passed to repositories or persisted objects when chunk 5 wires it.
- Provisioning-race retry, database transaction enforcement, unique-constraint translation, and optimistic-lock enforcement belong to chunks 4–5; the chunk-3 ports make those requirements explicit but cannot enforce them in memory.
- The plan's documented security, privacy, concurrency, migration, and negative-path risks remain active for later chunks.
- Independent review has not yet run for this branch and must not be replaced by self-review.

## Human actions required

None.

## Recommended next action

Start a fresh read-only independent review of this branch using `prompts/INDEPENDENT_REVIEW.md`. If approved and merged by a human, begin chunk 4 on a new branch; do not start it automatically.

## Last updated

2026-07-14

# Task handoff

## Objective

Complete the identity backend module described in `docs/plans/002-identity-module.md`: vendor-neutral OAuth2 authentication, account/profile/restriction behavior, persistence, self-service and admin endpoints, and identity-local export/deletion.

## Active branch

`main`

## Related issue or plan

No issue. See `docs/plans/002-identity-module.md`.

## Current status

in_progress

## Completed work

Observable committed work on `main`:

- Chunk 1 foundation in `f8cc350`: OAuth2 resource-server dependencies and configuration, minimal health-only security configuration, account/profile migrations, migration integration test, ADR-0005, and the execution plan.
- Chunk 1 correction in `506353b`: database constraints and negative-path migration coverage for account roles/statuses.
- Chunk 2 domain model in `9b79b87`: framework-free account, public profile, pseudonym, restriction domain types and unit tests.
- Chunk 2 corrections in `16b0dee`: collision-proof anonymization tombstones, HMAC-hash-only auth subject design, ADR-0006, and plan updates.

The domain and migration test files described by those commits exist in the working tree. No uncommitted identity changes were present when this handoff was recovered.

## Remaining work

According to the plan and current file tree, chunks 3–8 remain:

- application services and repository/hasher ports;
- persistence adapters and fix-forward migrations, including `auth_subject_hash` and restrictions;
- JWT/security wiring and provisioning-race handling;
- `/api/me` profile endpoints and error handling;
- admin account lookup with authorization and audit;
- idempotent export and deletion.

The plan acceptance criteria remain unchecked. The recommended next scope is chunk 3 only; do not start later branches automatically.

## Decisions made

- Store only `HMAC-SHA256(subject, pepper)`, never the raw OIDC subject (ADR-0006).
- Keep security-filter-chain composition in the app module and resolve roles from the identity database, not JWT role claims.
- Keep `identity.api` empty until another module has a concrete cross-module use case.
- Use schema-local Flyway migrations and append-only fix-forward changes after merge.
- Treat restrictions as time-bounded facts rather than an account status.

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

## Tests and verification

- `python3 scripts/validate_repo_governance.py` — passed during recovery before governance changes: 15 required files and 5 shared skills.
- `./scripts/check.sh` — governance passed, then Gradle could not create a cache lock in the sandbox's read-only default user cache.
- `GRADLE_USER_HOME=/tmp/geo-housing-gradle ./scripts/check.sh` — governance passed, then Gradle wrapper download failed because sandbox network access was unavailable.
- Commit `9b79b87` reports 33 passing plain-JUnit domain tests and an empirical ArchUnit negative check. This is historical commit evidence, not a test rerun during recovery.
- Commits `506353b` and `16b0dee` report independent local Codex review fixes. This is historical commit evidence, not a new review.

## Known failures

- Current full repository-check status is unverified in this sandbox because Gradle could not use the default cache and could not download into the writable `/tmp` cache.
- `docs/plans/002-identity-module.md` has a stale progress log that mentions only plan creation; Git commits provide newer evidence and take priority.

## Risks and unresolved questions

- The repository is on `main`, 10 commits ahead of `origin/main`; remote publication/merge status was not inspected and must not be inferred.
- Chunk 3 will introduce the HMAC boundary port and application behavior; preserve ADR-0006 and do not place crypto or framework code in the domain package.
- The plan's documented security, privacy, concurrency, migration, and negative-path risks remain active for later chunks.

## Human actions required

None for recovery. If the Gradle distribution remains unavailable to a future agent, use the `HUMAN_ACTION_REQUIRED` format rather than installing or bypassing permissions.

## Recommended next action

Start a dedicated branch for chunk 3 from the intended base only after the user confirms that task. Re-inspect Git state, read the active plan and this handoff, implement only the application-layer ports/services and unit tests, run the relevant checks, and update this handoff before stopping.

## Last updated

2026-07-11

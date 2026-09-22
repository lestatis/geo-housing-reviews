# Task handoff

## Objective

Repair the clean-runner CI baseline without weakening frontend, backend, governance, PITest, or
real S3-compatible integration coverage.

## Active branch

`main` (the baseline-repair commits were pushed directly to main)

## Related issue or plan

`docs/plans/021-ci-baseline-repair.md`

## Current status

completed

## Completed work

- Removed `cache: pnpm` from the frontend job, which failed before pnpm was available on a clean
  GitHub runner, and added a lockfile-enforced install before each frontend check.
- Diagnosed the next frontend CI failure: `corepack pnpm lint` starts a root lifecycle script whose
  nested `pnpm -r lint` cannot resolve pnpm without a Corepack shim on `PATH`. Added `corepack
  enable` and switched the job to normal `pnpm` commands.
- Restricted the default PITest test-selection glob to domain and application packages. Mutation
  targets were already limited to those layers, so infrastructure tests such as
  `S3EvidenceStoreIntegrationTest` now remain in normal Gradle test execution rather than running
  again inside PITest.
- Replaced the unavailable Docker Hub image in all three MinIO-backed tests with the lead-approved
  public official Quay MinIO image, pinned by immutable digest. The Quay AIStor image was fetched
  successfully but requires a license and therefore cannot run the public CI test harness.

## Remaining work

None for Plan 021.

## Decisions made

- The frontend job favors deterministic installation over pnpm cache optimization until a clean run
  is green.
- Enable Corepack once in CI rather than wrapping each command, so nested package scripts can
  resolve pnpm normally.
- PITest infrastructure-test exclusion is achieved by positive test selection, not by disabling the
  normal integration test.
- The lead approved an official Quay MinIO/AIStor image. The implementation uses the public
  `quay.io/minio/minio@sha256:14cea…8936e` variant because it is MinIO Inc's image and does not
  require the AIStor license that prevented the alternate Quay image from starting.

## Assumptions

- The public Quay MinIO image remains compatible with the Testcontainers `MinIOContainer` API and
  the current test-specific entrypoint override; the three local integration suites passed.

## Files changed

- `.github/workflows/frontend-check.yml`
- `apps/api/buildSrc/src/main/kotlin/geohousing.mutation-testing.gradle.kts`
- `apps/api/buildSrc/src/main/kotlin/MutationTestingExtension.kt`
- `apps/api/modules/verification/src/test/java/.../S3EvidenceStoreIntegrationTest.java`
- `apps/api/app/src/test/java/.../EvidenceEndpointIntegrationTest.java`
- `apps/api/app/src/test/java/.../EvidencePersistenceIntegrationTest.java`
- `docs/plans/021-ci-baseline-repair.md`
- `docs/handoffs/021-ci-baseline-repair.md`

## Commands run

- Docker Hub repository/tag HTTP checks: repository/tag unavailable.
- `docker manifest inspect minio/minio:RELEASE.2025-04-08T15-41-24Z`: unavailable without registry
  authentication; public Docker Hub API independently returned 404.

## Tests and verification

- `corepack pnpm install --frozen-lockfile` — passed on the prior clean CI run.
- `./scripts/check-scoped.sh frontend` — passed: ESLint, 75 Vitest tests, and TypeScript.
- `cd apps/api && ./gradlew :modules:verification:test --tests
  'com.example.geohousing.verification.application.*' --tests
  'com.example.geohousing.verification.domain.*' -PskipMutation` — passed.
- `cd apps/api && ./gradlew :modules:verification:mutationTest --rerun-tasks` — passed in 19
  seconds; PITest itself completed in 7 seconds at 89% against its 88% threshold, with no
  MinIO-backed test in the run.
- `cd apps/api && ./gradlew :modules:verification:test --tests
  'com.example.geohousing.verification.infrastructure.storage.S3EvidenceStoreIntegrationTest'
  -PskipMutation` — passed with the pinned public Quay image.
- `cd apps/api && ./gradlew :app:test --tests
  'com.example.geohousing.app.verification.EvidenceEndpointIntegrationTest' --tests
  'com.example.geohousing.app.verification.EvidencePersistenceIntegrationTest' -PskipMutation` —
  passed with the pinned public Quay image.
- `cd apps/api && ./gradlew :modules:verification:spotlessCheck` — passed.
- `cd apps/api && ./gradlew :modules:verification:spotlessCheck :app:spotlessCheck` — passed after
  the image-reference edits.
- `./scripts/check-scoped.sh governance` and shell syntax checks — passed.
- GitHub Actions: Governance — PASS and Frontend — PASS on `654148a`; Backend — PASS on
  `0d5b457`, the last commit changing backend paths. Backend source is unchanged on `654148a`.

## Known failures

None.

## Risks and unresolved questions

Plan 021 is complete. Branch protection and a required aggregate merge gate remain a separate
follow-up; path-filtered workflows alone are not suitable as three independently required checks.

## Human actions required

None. The lead approved the official Quay AIStor image and pinned release.

## Recommended next action

Close Plan 021 and proceed to the next product or engineering chunk. Plan a protected merge workflow
before configuring required status checks.

## Last updated

2026-09-22

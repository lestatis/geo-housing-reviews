# Task handoff

## Objective

Repair the clean-runner CI baseline without weakening frontend, backend, governance, PITest, or
real S3-compatible integration coverage.

## Active branch

`fix/ci-baseline`

## Related issue or plan

`docs/plans/021-ci-baseline-repair.md`

## Current status

blocked

## Completed work

- Removed `cache: pnpm` from the frontend job, which failed before pnpm was available on a clean
  GitHub runner, and added a lockfile-enforced install before each frontend check.
- Restricted the default PITest test-selection glob to domain and application packages. Mutation
  targets were already limited to those layers, so infrastructure tests such as
  `S3EvidenceStoreIntegrationTest` now remain in normal Gradle test execution rather than running
  again inside PITest.

## Remaining work

Obtain a lead decision for the official MinIO image source/version, update all three test classes
consistently, then push the branch and collect green governance/backend/frontend CI evidence.

## Decisions made

- The frontend job favors deterministic installation over pnpm cache optimization until a clean run
  is green.
- PITest infrastructure-test exclusion is achieved by positive test selection, not by disabling the
  normal integration test.

## Assumptions

- The Docker Hub 404/unauthorized response reflects removal or unavailability of the pinned public
  image, not a Testcontainers API regression.

## Files changed

- `.gitignore`
- `.github/workflows/frontend-check.yml`
- `apps/api/buildSrc/src/main/kotlin/geohousing.mutation-testing.gradle.kts`
- `apps/api/buildSrc/src/main/kotlin/MutationTestingExtension.kt`
- `docs/plans/021-ci-baseline-repair.md`
- `docs/handoffs/021-ci-baseline-repair.md`

## Commands run

- Docker Hub repository/tag HTTP checks: repository/tag unavailable.
- `docker manifest inspect minio/minio:RELEASE.2025-04-08T15-41-24Z`: unavailable without registry
  authentication; public Docker Hub API independently returned 404.

## Tests and verification

- `corepack pnpm install --frozen-lockfile` — passed.
- `./scripts/check-scoped.sh frontend` — passed: ESLint, 75 Vitest tests, and TypeScript.
- `cd apps/api && ./gradlew :modules:verification:test --tests
  'com.example.geohousing.verification.application.*' --tests
  'com.example.geohousing.verification.domain.*' -PskipMutation` — passed.
- `cd apps/api && ./gradlew :modules:verification:mutationTest --rerun-tasks` — passed in 19
  seconds; PITest itself completed in 7 seconds at 89% against its 88% threshold, with no
  MinIO-backed test in the run.
- `cd apps/api && ./gradlew :modules:verification:spotlessCheck` — passed.
- `./scripts/check-scoped.sh governance` and shell syntax checks — passed.

## Known failures

The three MinIO-backed tests cannot run on a clean GitHub runner with the pinned
`minio/minio:RELEASE.2025-04-08T15-41-24Z` image.

## Risks and unresolved questions

Changing image registry/version may alter product, licensing, support, or container behavior and is
outside the accepted scope.

## Human actions required

LEAD_DECISION_REQUIRED
Question: Which official MinIO image source and immutable version/digest should the project approve
for Testcontainers after `minio/minio:RELEASE.2025-04-08T15-41-24Z` became unavailable on Docker Hub?
Why it matters: The real S3-compatible integration tests cannot execute in clean CI without an
approved replacement, and a registry/image change is a dependency decision.
Options: Approve an official Quay-hosted MinIO/AIStor image and immutable release; publish/approve a
project-controlled mirror of the previously approved image; or choose another official supported
source after reviewing licensing and compatibility.
Current evidence: Docker Hub repository/tag API returned 404 on 2026-09-21; all three tests use the
same unavailable image; ADR-0008 requires real S3-compatible coverage.

## Recommended next action

Lead selects the approved MinIO source/version. The implementer then changes only the common image
reference(s), runs the three affected integration tests plus the verification mutation task, and
pushes `fix/ci-baseline` for CI.

## Last updated

2026-09-21

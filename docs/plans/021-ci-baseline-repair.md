# Plan 021 — repair the clean-runner CI baseline

Status: Active
Owner: Codex
Related issue: None
Last updated: 2026-09-21

## Objective

Make the split frontend and backend checks reproducible on a clean GitHub Actions runner without
weakening the L0/L1/L2 model, mutation thresholds, or real S3-compatible integration coverage.

## Acceptance criteria

- [x] Frontend CI installs the locked dependencies on Node 22 before lint, test, and typecheck.
- [x] PITest selects only domain/application tests for modules whose mutation targets are those
  layers; infrastructure integration tests remain in ordinary Gradle test execution.
- [ ] All three MinIO-backed integration classes start the approved, reproducible official image on
  a clean GitHub runner.
- [ ] Governance, backend, and frontend CI pass on this branch.

## Non-goals

- Changing production storage semantics or removing real S3-compatible integration coverage.
- Lowering mutation thresholds, disabling PITest, or skipping the app integration suite.
- Enabling GitHub branch protection; that is an administrator action after CI is green.

## Current system

Commit `2f33f65` split the formerly sequential gate. The first clean-runner CI execution exposed
two baseline failures: `actions/setup-node` could not locate pnpm when asked to configure its pnpm
cache, and the `minio/minio:RELEASE.2025-04-08T15-41-24Z` image could not be fetched. Docker Hub's
public repository and manifest endpoints return 404/unauthorized for that repository/tag as of
2026-09-21. The same image is used by `S3EvidenceStoreIntegrationTest`,
`EvidenceEndpointIntegrationTest`, and `EvidencePersistenceIntegrationTest`.

ADR-0009 limits mutation targets to domain/application classes, but the old test glob selected the
entire module and therefore started the MinIO infrastructure test during PITest.

## Decisions

| Decision | Choice | Reason | Revisit when |
| --- | --- | --- | --- |
| Frontend cache | Remove pnpm cache setup initially | A clean install is required before optimizing cache behavior | CI is green and timing evidence exists |
| PITest selection | Domain/application test globs only | Matches ADR-0009 target layers and retains infrastructure coverage in `test` | A module has cross-layer tests required to kill a mutant |
| MinIO source | `quay.io/minio/minio@sha256:14cea…8936e` | Lead approved an official Quay MinIO image. The public MinIO Inc image starts without the license required by Quay AIStor | CI or compatibility evidence fails |

## Implementation steps

1. Make the frontend job install from `pnpm-lock.yaml` before its commands. **Completed**
2. Restrict the default PITest test glob to domain/application packages. **Completed**
3. Update all three tests to the approved public official Quay MinIO image and verify startup on a clean
   runner. **Local integration evidence completed; CI pending.**
4. Push this branch and require green governance, backend, and frontend CI before review.

## Verification

- `python3 scripts/validate_repo_governance.py`
- `./scripts/check-scoped.sh frontend`
- `cd apps/api && ./gradlew :modules:verification:test --tests '*application.*' --tests '*domain.*' -PskipMutation`
- `cd apps/api && ./gradlew :modules:verification:mutationTest`
- CI: governance, backend, frontend all green after the approved MinIO decision.

## Risks and rollback/forward-fix

The PITest change can expose a mutation-score regression because it no longer permits infrastructure
tests to kill mutants. That is intended evidence, not a reason to lower a threshold. If it fails,
add or improve a framework-free test for the mutated behavior. The approved image may still expose
container-entrypoint or S3-compatibility differences; retain the prior source only as history, not
as a fallback, because it is unavailable on a clean runner.

## Progress log

- 2026-09-21: Diagnosed the reported clean-runner failures. Docker Hub no longer serves the pinned
  MinIO repository/tag; frontend setup and PITest test selection are independently repairable.
- 2026-09-21: `corepack pnpm install --frozen-lockfile` and the frontend L1 checks passed. The
  verification domain/application tests passed, and `:modules:verification:mutationTest
  --rerun-tasks` passed in 7 seconds at 89%; no MinIO-backed test ran during mutation analysis.
- 2026-09-21: The approved Quay AIStor image was fetchable but requires a license and exited before
  its health endpoint. The public official `quay.io/minio/minio` image was verified as MinIO Inc's
  release `RELEASE.2025-09-07T16-13-09Z` and pinned by manifest digest for testing.
- 2026-09-21: The pinned public Quay image passed `S3EvidenceStoreIntegrationTest`,
  `EvidenceEndpointIntegrationTest`, and `EvidencePersistenceIntegrationTest` locally. The branch
  now awaits the corresponding clean GitHub Actions evidence.

## Final outcome

Pending local integration and clean-runner CI evidence for the approved image.

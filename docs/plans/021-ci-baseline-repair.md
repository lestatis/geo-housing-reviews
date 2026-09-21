# Plan 021 — repair the clean-runner CI baseline

Status: Blocked
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
- [ ] All three MinIO-backed integration classes start an approved, reproducible official image on
  a clean GitHub runner.
- [ ] Governance, backend, and frontend CI pass on this branch.

## Non-goals

- Changing production storage semantics or removing real S3-compatible integration coverage.
- Lowering mutation thresholds, disabling PITest, or skipping the app integration suite.
- Selecting a replacement MinIO image, registry, or version without an accepted decision.
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
| MinIO source | Block pending lead decision | An image registry/version is a dependency decision outside this plan | Lead accepts an official source/version |

## Implementation steps

1. Make the frontend job install from `pnpm-lock.yaml` before its commands. **Completed**
2. Restrict the default PITest test glob to domain/application packages. **Completed**
3. Obtain an approved official MinIO image source/version, update all three tests consistently, and
   verify its startup on a clean runner. **Blocked: lead decision required.**
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
add or improve a framework-free test for the mutated behavior. Do not change MinIO dependencies
until the lead has approved a source/version compatible with ADR-0008.

## Progress log

- 2026-09-21: Diagnosed the reported clean-runner failures. Docker Hub no longer serves the pinned
  MinIO repository/tag; frontend setup and PITest test selection are independently repairable.
- 2026-09-21: `corepack pnpm install --frozen-lockfile` and the frontend L1 checks passed. The
  verification domain/application tests passed, and `:modules:verification:mutationTest
  --rerun-tasks` passed in 7 seconds at 89%; no MinIO-backed test ran during mutation analysis.

## Final outcome

Blocked pending the MinIO image decision and subsequent CI evidence.

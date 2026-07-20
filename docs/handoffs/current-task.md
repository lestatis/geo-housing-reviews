# Task handoff

## Objective

Build the `properties` backend module (plan `docs/plans/004-properties-module.md`): the canonical
catalogue of reviewable objects. This is chunk 2 (domain model). The identity module (plan `002`) is
complete; the Swagger/OpenAPI feature and properties chunk 1 are merged to `main`.

## Active branch

`feat/004-properties-chunk2-domain` (branched from `main` at `6d9096b`)

## Related issue or plan

No issue. See `docs/plans/004-properties-module.md` — this is chunk 2 of 8.

## Current status

chunk2_implemented — ready for fresh independent review and merge before chunk 3.

## Completed work

On `main`: identity module complete (ends `068e647`), properties chunk 1 schema (`293da3d`), and the
public Swagger/OpenAPI docs feature (`6d9096b`, Codex-implemented, Claude-Code-finished).

### Properties chunk 2 — domain model (this branch)

Framework-free `properties.domain` (no Spring, no JPA — the ArchUnit domain-purity rules now apply
to it and pass):
- Identifiers/enums: `PropertyId`, `CreatorId` (the creating account, held as an opaque UUID with
  **no dependency on the identity module** — boundary rule), `PropertyType`
  (BUILDING/RESIDENTIAL_COMPLEX/BLOCK/PHASE), `PropertyStatus` (DRAFT/ACTIVE/MERGED/HIDDEN),
  `AliasSource`.
- Value objects: `Coordinates` (WGS84 range-checked; PostGIS mapping deferred to chunk 5),
  `Address` (country defaults to GE, 2-letter; other parts optional; original text preserved),
  `PropertyAlias` (locale/name/source, optional confidence in [0,1]), `PropertySource` (provenance).
- `Property` aggregate with the lifecycle state machine: `activate` (DRAFT→ACTIVE), `hide`
  (DRAFT/ACTIVE→HIDDEN), `mergeInto` (any non-merged → MERGED, **terminal** — a merged property
  rejects all further mutation), plus `rename`/`setAddress`/`setCoordinates`/`setParent`/`addAlias`/
  `addSource`. `IllegalPropertyStateTransitionException` for state-machine violations;
  `IllegalArgumentException`/NPE for value validation.
- Invariants mirror the `V3.1` CHECKs: a merge target is set exactly when status is MERGED; no
  self-parent; no self-merge; non-blank canonical name. Enforced in both `create` and `reconstitute`.

## Remaining work

Chunks 3–8 (see the plan): application layer + `DuplicateCandidateFinder` port (3), persistence
adapters mapping the aggregate to the four tables (4), duplicate detection + PostGIS geo +
`hibernate-spatial` + ADR-0007 (5), public `/api/properties` endpoints (6), admin merge/status (7),
`properties.api` contract when reviews needs it (8).

## Decisions made

- The creator is modelled as a local `CreatorId(UUID)` rather than importing identity's `AccountId`
  — modules do not share domain types (ARCHITECTURE boundary rules).
- `Coordinates` is a plain lat/lng value object; PostGIS geometry is an infrastructure concern for
  chunk 5, keeping the domain library-free.
- `MERGED` is terminal and enforced by an `ensureMutable()` guard on every mutator.
- `AliasSource` is a domain enum even though `V3.1` left `alias.source` un-CHECKed (domain stricter
  than the DB is fine; a CHECK can be added later if desired).

## Files changed on this branch

- New `properties.domain`: `PropertyId`, `CreatorId`, `PropertyType`, `PropertyStatus`,
  `AliasSource`, `Coordinates`, `Address`, `PropertyAlias`, `PropertySource`, `Property`,
  `IllegalPropertyStateTransitionException`.
- New tests: `PropertyTest`, `PropertyValueObjectsTest`.
- `docs/plans/004-properties-module.md`, `docs/handoffs/current-task.md`.

No migration, no dependency, no app-module change.

## Tests and verification

Run on this branch, 2026-07-20:

- `./gradlew :modules:properties:check` — passed (compile, unit tests, spotless, checkstyle).
- `./gradlew :app:test` (via the full gate) — passed, incl. `ModuleBoundaryArchitectureTest`: the
  `domain_packages_should_not_depend_on_spring` and `..infrastructure..` rules now run against real
  `properties.domain` classes and pass.
- `./scripts/check.sh` — passed. Domain tests: `PropertyTest` (12), `PropertyValueObjectsTest` (4).

## Known failures

None.

## Risks and unresolved questions

- Whether `hide` should be reversible (HIDDEN→ACTIVE) is not modelled yet; add an `unhide`/`restore`
  transition if a use case appears (chunk 6/7).
- `reconstitute` does not re-validate value-object internals (they were validated when first
  constructed); persistence in chunk 4 must rebuild them through their constructors.

## Human actions required

Review and merge `feat/004-properties-chunk2-domain` after a fresh independent review (implemented by
Claude Code; review must be a fresh independent pass). The branch is local and not pushed; there is
no credential path to push from this environment.

## Recommended next action

Independent review of chunk 2, then merge to `main`. Chunk 3 (application ports + services) branches
from `main` after that.

## Last updated

2026-07-20

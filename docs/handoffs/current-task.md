# Task handoff

## Objective

Build the `properties` backend module (plan `docs/plans/004-properties-module.md`): the canonical
catalogue of reviewable objects. This is chunk 3 (application layer). Identity (plan `002`),
Swagger/OpenAPI, and properties chunks 1–2 are merged to `main`.

## Active branch

`feat/004-properties-chunk3-application` (branched from `main` at `af8ed3d`)

## Related issue or plan

No issue. See `docs/plans/004-properties-module.md` — this is chunk 3 of 8.

## Current status

chunk3_implemented — ready for fresh independent review and merge before chunk 4.

## Completed work

On `main`: identity module complete (ends `068e647`), properties chunk 1 schema (`293da3d`), the
public Swagger/OpenAPI docs feature (`6d9096b`, Codex-implemented, Claude-Code-finished), and
properties chunk 2 domain model (`af8ed3d`).

### Properties chunk 3 — application layer (this branch)

Framework-free `properties.application`:
- `PropertyRepository` port (`findById`, `create`) and `DuplicateCandidateFinder` port +
  `DuplicateCandidate` record.
- `PropertyCreationService`: creates a `DRAFT`, but surfaces possible duplicates as an expected
  **result**, not an error. `PropertyCreationResult` is a sealed `Created(property)` /
  `DuplicatesFound(candidates)`. On a plain attempt (`allowDuplicate=false`) with candidates present,
  nothing is created and the candidates are returned; re-submitting with `allowDuplicate=true`
  creates anyway and skips the finder. Address/coordinates from the command are applied to the draft.
- `PropertyQueryService.getById` → `Property` or `PropertyNotFoundException` (new domain exception).
- Deterministic duplicate detection is **not** here — chunk 3 only defines the port and the flow.
  Chunk 5 implements the finder (normalized name/address + PostGIS proximity).

### Properties chunk 2 — domain model (merged to `main`)

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

Chunks 4–8 (see the plan): persistence adapters mapping the aggregate to the four tables + the
`PropertyRepository` implementation (4), duplicate detection implementing `DuplicateCandidateFinder`
+ PostGIS geo + `hibernate-spatial` + ADR-0007 (5), public `/api/properties` endpoints — where
`DuplicatesFound` becomes a 409-with-candidates response (6), admin merge/status (7), `properties.api`
contract when reviews needs it (8).

## Decisions made

- Duplicates are an expected outcome, so `PropertyCreationService` returns a sealed
  `PropertyCreationResult` (Created / DuplicatesFound) rather than throwing. `PropertyNotFoundException`
  is thrown (a genuine miss).
- The creator is a local `CreatorId(UUID)`, not identity's `AccountId` — modules do not share domain
  types (ARCHITECTURE boundary rules).
- `Coordinates` is a plain lat/lng value object; PostGIS geometry is chunk 5 infrastructure.
- `MERGED` is terminal (`ensureMutable()` guard on every mutator).

## Files changed on this branch (chunk 3)

- New `properties.application`: `PropertyRepository`, `DuplicateCandidateFinder`, `DuplicateCandidate`,
  `CreatePropertyCommand`, `PropertyCreationResult`, `PropertyCreationService`, `PropertyQueryService`.
- New domain: `PropertyNotFoundException`.
- New tests: `PropertyCreationServiceTest`, `PropertyQueryServiceTest`.
- `docs/plans/004-properties-module.md`, `docs/handoffs/current-task.md`.

No migration, no dependency, no Spring wiring yet (services are plain classes; the bean wiring +
`PropertyRepository` adapter arrive with chunk 4, as identity did).

## Tests and verification

Run on this branch, 2026-07-20:

- `./gradlew :modules:properties:check` — passed (compile, unit tests, spotless, checkstyle).
- `./scripts/check.sh` — passed, incl. ArchUnit (application classes stay framework-free).
  `PropertyCreationServiceTest` (3: creates when clean, returns candidates when not allowed, creates
  when allowed), `PropertyQueryServiceTest` (2: found / not-found).

## Known failures

None.

## Risks and unresolved questions

- The `DuplicateCandidateFinder` port is faked in tests; the real detection (chunk 5) must match this
  signature or the port evolves. `DuplicateCandidate` is minimal (id + name); chunk 5 may enrich it
  (distance/score) additively.
- `reconstitute` does not re-validate value-object internals; chunk 4 persistence must rebuild them
  through their constructors.

## Human actions required

Review and merge `feat/004-properties-chunk3-application` after a fresh independent review
(implemented by Claude Code; review must be a fresh independent pass). The branch is local and not
pushed; there is no credential path to push from this environment.

## Recommended next action

Independent review of chunk 3, then merge to `main`. Chunk 4 (JPA persistence adapters + bean wiring)
branches from `main` after that.

## Last updated

2026-07-20

# Task handoff

## Objective

Build the `properties` backend module (plan `docs/plans/004-properties-module.md`): the canonical
catalogue of reviewable objects. This is chunk 4 (persistence adapters). Identity (plan `002`),
Swagger/OpenAPI, and properties chunks 1–3 are merged to `main`.

## Active branch

`feat/004-properties-chunk4-persistence` (branched from `main` at `a07a220`)

## Related issue or plan

No issue. See `docs/plans/004-properties-module.md` — this is chunk 4 of 8.

## Current status

chunk4_implemented — ready for fresh independent review and merge before chunk 5.

## Completed work

On `main`: identity module complete (ends `068e647`), properties chunk 1 schema (`293da3d`), the
public Swagger/OpenAPI docs feature (`6d9096b`, Codex-implemented, Claude-Code-finished), and
properties chunk 2 domain model (`af8ed3d`), and properties chunk 3 application layer (`a07a220`).

### Properties chunk 4 — persistence adapters (this branch)

JPA persistence of the aggregate across the four `V3.1` tables:
- `PropertyJpaEntity` (root) with a cascaded `@ManyToOne` `AddressJpaEntity` and cascaded
  unidirectional `@OneToMany` `PropertyAliasJpaEntity`/`PropertySourceJpaEntity` keyed by
  `property_id` — saving the root writes the whole graph in one transaction.
- `PropertyJpaMapper` generates the surrogate row ids (domain value objects have none), maps
  `confidence` Double↔`BigDecimal` (the NUMERIC column) and coordinates↔lat/lng columns, and sets
  child `created_at` to the property's creation time.
- `JpaPropertyRepository` (`@Repository`) implements the `PropertyRepository` port
  (`findById`/`create`).
- App composition root: `@EntityScan`/`@EnableJpaRepositories` now list both the identity and
  properties persistence packages (the only app-module change).
- Because `ddl-auto=validate`, the passing `@SpringBootTest` is itself proof the mappings match the
  schema exactly (confidence NUMERIC↔BigDecimal, note TEXT↔String, enums, FKs).

### Properties chunk 3 — application layer (merged to `main`)

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

Chunks 5–8 (see the plan): duplicate detection implementing `DuplicateCandidateFinder` + PostGIS geo
+ `hibernate-spatial` + ADR-0007 (5), public `/api/properties` endpoints — where `DuplicatesFound`
becomes a 409-with-candidates response (6), admin merge/status (7), `properties.api` contract when
reviews needs it (8). No `PropertiesBeanConfiguration` exists yet: the application services
(`PropertyCreationService`/`PropertyQueryService`) are still unwired plain classes and
`DuplicateCandidateFinder` has no bean — chunk 5 provides the finder impl, and chunk 5/6 wires the
service beans (as identity wired its services in chunk 5). The `JpaPropertyRepository` **is** a
`@Repository` bean already.

## Decisions made

- The aggregate is persisted as one graph via JPA cascade (address `@ManyToOne`, aliases/sources
  unidirectional `@OneToMany` with `@JoinColumn`), not flat manual saves — properties is a genuine
  aggregate with child collections, unlike identity's flat entities.
- Surrogate row ids for address/alias/source are generated in the mapper (the domain value objects
  have no identity). `confidence` maps Double↔`BigDecimal` to match the NUMERIC column under
  `ddl-auto=validate`.
- Duplicates are an expected outcome → sealed `PropertyCreationResult`, not an exception.
- `MERGED` is terminal; the creator is a local `CreatorId(UUID)` (no identity dependency).

## Files changed on this branch (chunk 4)

- New `properties.infrastructure.persistence`: `PropertyJpaEntity`, `AddressJpaEntity`,
  `PropertyAliasJpaEntity`, `PropertySourceJpaEntity`, `PropertyJpaMapper`,
  `SpringDataPropertyRepository`, `JpaPropertyRepository`.
- Edit: `app/.../GeoHousingApplication.java` (add the properties persistence package to
  `@EntityScan`/`@EnableJpaRepositories`).
- New test: `app/.../properties/PropertyPersistenceIntegrationTest.java`.
- `docs/plans/004-properties-module.md`, `docs/handoffs/current-task.md`.

No new migration, no new dependency.

## Tests and verification

Run on this branch, 2026-07-20:

- `./gradlew :modules:properties:check` — passed.
- `./gradlew :app:test` (via the full gate) — passed. `PropertyPersistenceIntegrationTest` (3):
  full-aggregate round-trip (address + coordinates + two aliases + one source), a bare property, and
  not-found. The context boots with `ddl-auto=validate`, so this also proves the mappings match the
  `V3.1` schema. `PropertiesMigrationIntegrationTest` (5) still green.
- `./scripts/check.sh` — passed (governance + full Gradle gate; ArchUnit; frontend skipped).

## Known failures

None.

## Risks and unresolved questions

- Chunk 5 will add a PostGIS `geography` column via a new migration (`V3.2`) and map it with
  `hibernate-spatial` — the first new production dependency and an ADR (ADR-0007). Under
  `ddl-auto=validate`, the entity's spatial column mapping must match the migration exactly.
- The `DuplicateCandidateFinder` port is still faked; chunk 5's real finder must match the signature
  (or evolve it), and `DuplicateCandidate` may gain distance/score additively.

## Human actions required

Review and merge `feat/004-properties-chunk4-persistence` after a fresh independent review
(implemented by Claude Code; review must be a fresh independent pass — the JPA mapping and the
composition-root change are the parts worth a close look). The branch is local and not pushed; there
is no credential path to push from this environment.

## Recommended next action

Independent review of chunk 4, then merge to `main`. Chunk 5 (duplicate detection + PostGIS geo +
`hibernate-spatial` + ADR-0007) branches from `main` after that — it adds a dependency and a
migration, so it gets its own plan-mode pass.

## Last updated

2026-07-20

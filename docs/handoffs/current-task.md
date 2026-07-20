# Task handoff

## Objective

Build the `properties` backend module (plan `docs/plans/004-properties-module.md`): the canonical
catalogue of reviewable objects. This is chunk 5 (duplicate detection + geo). Identity (plan `002`),
Swagger/OpenAPI, and properties chunks 1–4 are merged to `main`.

## Active branch

`feat/004-properties-chunk5-duplicates` (branched from `main` at `995a665`)

## Related issue or plan

No issue. See `docs/plans/004-properties-module.md` — this is chunk 5 of 8. ADR-0007 records the geo
decision.

## Current status

chunk5_implemented — ready for fresh independent review and merge before chunk 6.

## Completed work

On `main`: identity complete (ends `068e647`), the Swagger/OpenAPI feature (`6d9096b`), and properties
chunks 1–4 — schema (`293da3d`), domain (`af8ed3d`), application (`a07a220`), persistence (`995a665`).

### Properties chunk 5 — duplicate detection + PostGIS geo (this branch)

- **ADR-0007** — PostGIS proximity via a **native query, no `hibernate-spatial`**. The module plan's
  tentative dependency was dropped (confirmed with the founder): proximity filtering is the only need
  and `ST_DWithin` covers it in SQL, so no dependency, no entity change, `ddl-validate` unaffected.
- `V3.2` adds a `GENERATED ALWAYS` `geo geography(Point,4326)` column derived from lat/lng (a single
  source of truth, never app-written), a GiST index, and a normalized-name functional index. Verified
  against dev Postgres before wiring the test (the `geometry::geography` cast is immutable enough for a
  generated column; the point populates and `ST_DWithin` filters correctly).
- `JpaDuplicateCandidateFinder` implements the `DuplicateCandidateFinder` port via a native query on
  `SpringDataPropertyRepository` matching normalized `canonical_name` equality OR `ST_DWithin`
  (75 m default, tunable), excluding `MERGED`, returning `(id, name)` projections. Address-component
  matching is a documented later refinement.
- `PropertiesBeanConfiguration` wires `PropertyCreationService`/`PropertyQueryService` now that a
  finder bean exists. `PropertyJpaEntity` does **not** map `geo`.
- **Clock gotcha (fixed):** introducing a `propertiesClock` bean made every `Clock` injection
  ambiguous (identity already defines `identityClock`, and the convention build lacks `-parameters`,
  so Spring can't resolve `@Bean` args by name). Fixed the contained way — the properties clock is
  constructed inline, no second bean. Enabling `-parameters` project-wide is a flagged infra
  follow-up.

### Properties chunk 4 — persistence adapters (merged to `main`)

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

Chunks 6–8 (see the plan): public `/api/properties` endpoints — `POST` (where `DuplicatesFound`
becomes a 409-with-candidates response), `GET /{id}`, list — with an RFC 7807 handler and MockMvc
end-to-end (6); admin merge/hide/status under `/api/admin/properties/**` with audit (7);
`properties.api` cross-module contract when `reviews` needs it (8). The application services are now
wired beans (chunk 5), so chunk 6 just adds the web layer on top.

Flagged infra follow-up (not in this chunk): enable `-parameters` in the java-conventions build so
modules can keep named `Clock` (and other) beans without ambiguity — see the note in
`PropertiesBeanConfiguration`.

## Decisions made

- PostGIS proximity via native SQL, no `hibernate-spatial` (ADR-0007). The `geo` column is generated
  from lat/lng and unmapped by JPA, so the entity and `ddl-validate` are untouched.
- The finder matches normalized `canonical_name` equality OR `ST_DWithin` (75 m), excluding `MERGED`.
  Address-component matching deferred.
- Aggregate persisted as one JPA graph (chunk 4); duplicates are a sealed `PropertyCreationResult`,
  not an exception (chunk 3); `MERGED` terminal; creator is a local `CreatorId(UUID)`.

## Files changed on this branch (chunk 5)

- New: `db/migration/properties/V3.2__add_property_geography.sql`, `docs/adr/0007-postgis-duplicate-detection.md`.
- New `properties.infrastructure.persistence`: `JpaDuplicateCandidateFinder`, `PropertyCandidateProjection`;
  edit `SpringDataPropertyRepository` (native `findDuplicateCandidates` query).
- New `properties.infrastructure`: `PropertiesBeanConfiguration`.
- New tests: `app/.../properties/PropertyDuplicateDetectionIntegrationTest.java`,
  `PropertyCreationFlowIntegrationTest.java`.
- `docs/plans/004-properties-module.md`, `docs/handoffs/current-task.md`.

New migration (`V3.2`), no new dependency, no app-module change.

## Tests and verification

Run on this branch, 2026-07-20:

- `./gradlew :modules:properties:check` — passed.
- `./gradlew :app:test` (via the full gate) — passed. `PropertyDuplicateDetectionIntegrationTest`
  (5): normalized-name match, geo proximity within/outside the radius, `MERGED` excluded, empty when
  nothing matches, generated `geo` populated only when coordinates exist.
  `PropertyCreationFlowIntegrationTest` (1): wired service → `Created` → `DuplicatesFound` (same name)
  → `Created` (allowDuplicate). `PropertyPersistenceIntegrationTest` (3) and
  `PropertiesMigrationIntegrationTest` (5) still green with `V3.2` applied — the geo column is unmapped
  so `ddl-validate` is unaffected.
- `./scripts/check.sh` — passed (governance + full Gradle gate; ArchUnit; frontend skipped).
- `V3.2` was exercised directly against dev Postgres first: the generated `geo` populates from
  lat/lng (null when absent) and `ST_DWithin` matches within 75 m / not beyond.

## Known failures

None. (Two were caught and fixed during the run: a `Clock`-bean ambiguity — see the chunk-5 note —
and a test that pointed `merged_into_property_id` at a non-existent id, violating the FK; it now
merges into a real target.)

## Risks and unresolved questions

- `-parameters` is not enabled in the convention build, so modules can't safely expose more than one
  bean of the same type resolved by name (surfaced by the second `Clock` bean). Worth enabling
  globally as a small follow-up.
- The finder matches name + geo only; address-component matching (same street/building) is a
  documented later refinement. `DuplicateCandidate` may gain distance/score additively.

## Human actions required

Review and merge `feat/004-properties-chunk5-duplicates` after a fresh independent review
(implemented by Claude Code; review must be a fresh independent pass — the native SQL query, ADR-0007,
and the `V3.2` generated column are the parts worth a close look). The branch is local and not pushed;
there is no credential path to push from this environment.

## Recommended next action

Independent review of chunk 5, then merge to `main`. Chunk 6 (public `/api/properties` endpoints:
create with 409-and-candidates, get, list; RFC 7807; MockMvc) branches from `main` after that — it
touches public API + authorization, so it gets its own plan-mode pass.

## Last updated

2026-07-20

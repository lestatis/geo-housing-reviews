# Task handoff

## Objective

Build the `properties` backend module (plan `docs/plans/004-properties-module.md`): the canonical
catalogue of reviewable objects. This is chunk 6 (public endpoints). Identity (plan `002`),
Swagger/OpenAPI, and properties chunks 1–5 are merged to `main`.

## Active branch

`feat/004-properties-chunk6-endpoints` (branched from `main` at `10be962`)

## Related issue or plan

No issue. See `docs/plans/004-properties-module.md` — this is chunk 6 of 8.

## Current status

chunk6_implemented — ready for fresh independent review and merge before chunk 7.

## Completed work

On `main`: identity complete (ends `068e647`), the Swagger/OpenAPI feature (`6d9096b`), and properties
chunks 1–5 — schema (`293da3d`), domain (`af8ed3d`), application (`a07a220`), persistence (`995a665`),
duplicates + geo (`10be962`).

### Properties chunk 6 — public endpoints (this branch)

The module finally has an HTTP surface.
- `PropertyController` (`/api/properties`): `POST` → 201 + `Location` + `PropertyResponse`, or
  **409 with `candidates`** (`code: PROPERTY_DUPLICATE_CANDIDATES`) when chunk 5's finder matches and
  the caller has not set `allowDuplicate` — this is where the duplicate work becomes user-visible.
  `GET /{id}` → 200 / 404 `PROPERTY_NOT_FOUND` / 400 on a malformed id. `GET` → bounded newest-first
  list, `?limit=` clamped to [1,50]; **not** cursor-paginated (rich listing/search is the `search`
  module's job — a cursor arrives when a real feed needs one).
- **Advice scoping (the flagged follow-up, settled here):** `IdentityExceptionHandler` was a *global*
  `@RestControllerAdvice`, so it would have answered for properties' controllers — catching their
  `IllegalArgumentException` and reporting an identity code, while `PropertyNotFoundException` fell
  through to a 500. Both advices are now scoped to their own module's web package, with a regression
  test that identity's endpoints still map their own errors.
- The module took `spring-boot-starter-web` but **no Spring Security dependency**: the caller is read
  through the JDK `Principal` (whose name is the opaque account id set by identity's JWT converter),
  keeping security policy in the app.
- Responses omit `createdBy` (another user's opaque account id); the test asserts it is persisted
  correctly via the DB instead. No `SecurityConfiguration` change was needed —
  `anyRequest().authenticated()` already matches the intended policy.

### Properties chunk 5 — duplicate detection + PostGIS geo (merged to `main`)

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

Chunks 7–8 (see the plan): admin merge/hide/status under `/api/admin/properties/**` with audit (7) —
the `Property` aggregate already has `activate`/`hide`/`mergeInto`, but there is **no persisted update
path yet** (`PropertyRepository` only has `findById`/`findRecent`/`create`), so chunk 7 must add one;
`properties.api` cross-module contract when `reviews` needs it (8).

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

## Files changed on this branch (chunk 6)

- New `properties.infrastructure.web`: `PropertyController`, `CreatePropertyRequest`,
  `PropertyResponse`, `PropertyListResponse`, `DuplicateCandidatesProblem`,
  `PropertiesExceptionHandler`, `WebAuthentication`.
- Edit: `PropertyRepository` + `JpaPropertyRepository` + `SpringDataPropertyRepository`
  (`findRecent`), `PropertyQueryService` (`listRecent` with clamping),
  `modules/properties/build.gradle.kts` (`spring-boot-starter-web`),
  `IdentityExceptionHandler` (**scoped to identity's web package**).
- Tests: new `app/.../properties/PropertyEndpointIntegrationTest.java`; updated the chunk-3 fakes for
  the new port method and added a limit-clamping unit test.
- `docs/plans/004-properties-module.md`, `docs/handoffs/current-task.md`.

New migration (`V3.2`), no new dependency, no app-module change.

## Tests and verification

Run on this branch, 2026-07-20:

- `./gradlew :modules:properties:check` — passed (22 module tests, incl. a new limit-clamping test).
- `./gradlew :app:test` — passed, **60 app tests, 0 failures**, incl. ArchUnit.
  `PropertyEndpointIntegrationTest` (7): anonymous → 401; create → 201 + `Location` + DRAFT with
  `created_by` persisted as the caller (asserted via `JdbcTemplate`, since the response omits it);
  a second same-name create → 409 `PROPERTY_DUPLICATE_CANDIDATES` containing the first property;
  `allowDuplicate=true` → 201; blank name → 400 `INVALID_REQUEST` (proving the *properties* advice
  handles it); `GET /{id}` 200 / unknown 404 / malformed 400; list respects `?limit=`; and a
  regression that identity's endpoints still return their own mapped errors after the advice scoping.
- `./scripts/check.sh` — passed (governance + full Gradle gate; frontend skipped).

## Known failures

None. (One was caught during the run: adding `findRecent` to the `PropertyRepository` port broke the
chunk-3 unit-test fakes, which did not implement it; fixed and used as an opportunity to cover the
new limit clamping.)

## Risks and unresolved questions

- The list endpoint is intentionally un-paginated beyond a capped `limit`. Revisit when the `search`
  module or a real feed needs cursors.
- `-parameters` is still not enabled in the convention build (see `PropertiesBeanConfiguration`);
  worth enabling globally so modules can expose same-typed beans resolved by name.
- Chunk 7 needs a persisted **update** path — the aggregate can `activate`/`hide`/`mergeInto` in
  memory, but `PropertyRepository` has no `save`/`update`, only `create`.

## Human actions required

Review and merge `feat/004-properties-chunk6-endpoints` after a fresh independent review (implemented
by Claude Code; review must be a fresh independent pass — the public API shape, the 409-with-candidates
contract, and the cross-module advice-scoping change are the parts worth close attention). The branch
is local and not pushed; there is no credential path to push from this environment.

## Recommended next action

Independent review of chunk 6, then merge to `main`. Chunk 7 (admin merge/hide/status with audit)
branches from `main` after that — it touches authorization and admin mutations, so it gets its own
plan-mode pass, and must add the missing persisted update path.

## Last updated

2026-07-20

# Properties Module: Catalogue, Aliases, Addresses, Duplicates, Merge

Status: Active
Owner: Claude Code
Related issue: none (direct founder request; follows the completed identity module, plan 002)
Last updated: 2026-07-15

## Objective

Implement the `properties` backend module: the canonical catalogue of reviewable objects
(buildings, residential complexes, blocks, phases) with structured addresses, multilingual aliases,
coordinates, hierarchy, status lifecycle (draft/active/merged/hidden), merge history, and source
provenance. This is the foundation every later module points at — reviews, search,
verification, and moderation all reference a property.

## Acceptance criteria

- [ ] `properties` schema owns its tables under the `V3.x` migration namespace; no cross-module
      foreign keys (a creator is stored as an opaque identity account id).
- [ ] A `Property` domain aggregate with framework-free invariants (status transitions, a merge
      target only when `MERGED`, no self-parent/self-merge) that activates the properties ArchUnit
      domain-purity rules.
- [ ] `POST /api/properties` creates a `DRAFT` property for the authenticated caller and surfaces
      deterministic duplicate candidates; `GET /api/properties/{id}` and `GET /api/properties` read.
- [ ] Deterministic duplicate-candidate detection (normalized name/address + geo proximity) backed
      by PostGIS.
- [ ] Merge/hide/status changes are admin-only (`/api/admin/properties/**`) with audit.
- [ ] `properties.api` exposes a minimal public property-lookup contract when `reviews` needs it
      (otherwise stays a stub).

## Non-goals

- Address/name/map **search** endpoints (the separate `search` module; this module provides the
  data and duplicate candidates).
- Listings (explicitly out of MVP), OpenSearch, representative-claim workflow (Should-have),
  public unauthenticated browsing.

## Module-wide decisions

| Decision | Choice | Reason |
|---|---|---|
| Migration namespace | `properties` schema, `V3.x` (root=1, identity=2, properties=3) | Module owns its tables/migrations (ARCHITECTURE boundary rules) |
| Cross-module references | `property.created_by` is an opaque `UUID`, never an FK to `identity.account` | No module reads/points at another module's tables |
| Geo | `latitude`/`longitude` columns; a generated PostGIS `geography(Point,4326)` column (`V3.2`) queried via native SQL — **no `hibernate-spatial`** (ADR-0007) | Proximity is the only need; native `ST_DWithin` covers it without a new dependency (AGENTS rule 5) |
| `properties.api` | Minimal/empty until `reviews` needs property lookup | No consumer yet (mirrors `identity.api`) |
| AuthZ | Create = any authenticated user (DRAFT); merge/hide/status = admin; reads authenticated for now | Reuses the existing security chain; public browsing is a later decision |

## Identity migration registry (append-only)

| Version | Contents | Chunk |
|---|---|---|
| `V3.1` | `properties` schema; `address`, `property`, `property_alias`, `property_source` | 1 |
| `V3.2` | generated `geo geography(Point,4326)` column + GiST index + normalized-name index | 5 |
| `V3.3` | reserved — admin/merge audit table if needed | 7 |

## Implementation chunks (one branch each: self-check → fresh independent read-only review → merge before the next starts)

1. **Foundation + schema** (this branch): module Boot/data-jpa deps, `V3.1` catalogue migration,
   `PropertiesMigrationIntegrationTest`, this plan.
2. **Domain model**: `PropertyId`, `PropertyType`, `PropertyStatus`, `Property` aggregate, `Address`,
   `PropertyAlias`, `PropertySource`, invariants; activates the properties ArchUnit domain rules.
3. **Application layer**: repository ports, `PropertyCreationService`, `PropertyQueryService`, a
   `DuplicateCandidateFinder` port; in-memory-fake unit tests.
4. **Persistence adapters**: JPA entities/mappers/adapters; integration tests.
5. **Duplicate handling + geo**: deterministic candidate detection, PostGIS geography column +
   `hibernate-spatial` (ADR-0007), integration tests.
6. **Public endpoints**: `GET`/list/`POST /api/properties`, RFC 7807 handler, MockMvc end-to-end.
7. **Merge + status lifecycle + admin**: `/api/admin/properties/**` with audit.
8. **`properties.api` contract**: built when `reviews` needs it.

## Verification

```bash
cd apps/api
./gradlew :modules:properties:check
./gradlew :app:test
cd /home/vladimir/IdeaProjects/geo-housing-reviews && ./scripts/check.sh
```

## Progress log

- 2026-07-20: Chunk 6 (public endpoints) implemented on `feat/004-properties-chunk6-endpoints`,
  branched from `main` after chunk 5 (`10be962`). `PropertyController` exposes `POST /api/properties`
  (201 + `Location`, or **409 with `candidates`** and code `PROPERTY_DUPLICATE_CANDIDATES` when the
  chunk-5 finder matches and the caller has not set `allowDuplicate`), `GET /api/properties/{id}`
  (404 `PROPERTY_NOT_FOUND`, 400 on a malformed id), and a bounded newest-first `GET /api/properties`
  (`?limit=`, clamped to [1,50] in `PropertyQueryService`). Deliberately **not** cursor-paginated —
  rich listing/search is the `search` module's job; a cursor arrives when a real feed needs one.
  **Advice scoping fixed:** `IdentityExceptionHandler` was a *global* `@RestControllerAdvice` and
  would have answered for properties' controllers (catching their `IllegalArgumentException`, and
  leaving `PropertyNotFoundException` as a 500); both advices are now scoped to their own module's
  web package, with a regression test that identity's endpoints still map their own errors. The
  module took `spring-boot-starter-web` but **no Spring Security dependency** — the caller is read
  via the JDK `Principal`, keeping security policy in the app. Responses omit `createdBy` (another
  user's opaque account id); the test asserts it is persisted via the DB instead. No security-config
  change was needed (`anyRequest().authenticated()` already covers these routes). 7 endpoint tests +
  a limit-clamping unit test; `./scripts/check.sh` passes (60 app tests, 22 module tests).
- 2026-07-20: Chunk 5 (duplicate detection + geo) implemented on `feat/004-properties-chunk5-duplicates`,
  branched from `main` after chunk 4 (`995a665`). **ADR-0007**: PostGIS proximity via a native query,
  **no `hibernate-spatial`** — the module plan's tentative dependency was dropped because the only
  need is proximity filtering, which native `ST_DWithin` covers (confirmed with the founder). `V3.2`
  adds a `GENERATED ALWAYS` `geo geography(Point,4326)` column (from lat/lng), a GiST index, and a
  normalized-name functional index; verified directly against dev Postgres before wiring the test.
  `JpaDuplicateCandidateFinder` implements the `DuplicateCandidateFinder` port with a native query
  matching on normalized `canonical_name` equality OR `ST_DWithin` (75 m default), excluding `MERGED`;
  address-component matching deferred. `PropertiesBeanConfiguration` wires the application services;
  the clock is inline (not a bean) to avoid a second `Clock` bean clashing with identity's under a
  build that lacks `-parameters` — flagged as a small infra follow-up. `PropertyJpaEntity` is
  unchanged and does not map `geo`, so `ddl-auto=validate` still passes. Tests: finder name/geo/merged
  paths + the generated column, and a creation-flow integration test proving chunks 3+4+5 compose
  (second same-named create → `DuplicatesFound`, then `allowDuplicate` → `Created`).
  `./scripts/check.sh` passes.
- 2026-07-20: Chunk 4 (persistence adapters) implemented on `feat/004-properties-chunk4-persistence`,
  branched from `main` after chunk 3 (`a07a220`). JPA entities for the four `V3.1` tables mapped as
  a single aggregate: `PropertyJpaEntity` with a cascaded `@ManyToOne` address and cascaded
  unidirectional `@OneToMany` aliases/sources keyed by `property_id`, so saving the root writes the
  whole graph. `PropertyJpaMapper` generates the surrogate row ids (the domain value objects have
  none) and converts `confidence` Double↔`BigDecimal` (NUMERIC) and coordinates↔lat/lng columns.
  `JpaPropertyRepository` (`@Repository`) implements the `PropertyRepository` port; the app's
  `@EntityScan`/`@EnableJpaRepositories` now include the properties persistence package. Because
  `spring.jpa.hibernate.ddl-auto=validate`, the passing `@SpringBootTest` proves the mappings match
  the schema exactly. `PropertyPersistenceIntegrationTest` (3): full-aggregate round-trip (address,
  coordinates, two aliases, one source), a bare property, and not-found. `./scripts/check.sh` passes.
- 2026-07-20: Chunk 3 (application layer) implemented on `feat/004-properties-chunk3-application`,
  branched from `main` after chunk 2 (`af8ed3d`). Framework-free `properties.application`:
  `PropertyRepository` port (`findById`, `create`), `DuplicateCandidateFinder` port +
  `DuplicateCandidate` record, `PropertyCreationService`, `PropertyQueryService`. Creation surfaces
  duplicates as a **result**, not an exception: a `PropertyCreationResult` sealed type is either
  `Created(property)` or `DuplicatesFound(candidates)` — on a plain attempt with candidates present
  the property is not created; the caller re-submits with `allowDuplicate=true` to create anyway
  (finder is then skipped). `PropertyNotFoundException` added to the domain. The deterministic
  duplicate detection itself is still chunk 5; chunk 3 only defines the port and the flow around it.
  5 in-memory-fake unit tests; `./scripts/check.sh` passes.
- 2026-07-20: Chunk 2 (domain model) implemented on `feat/004-properties-chunk2-domain`, branched
  from `main` after chunk 1 (`293da3d`) and the Swagger feature (`6d9096b`) merged. Framework-free
  `properties.domain`: `PropertyId`, `CreatorId` (opaque creator account id — no identity
  dependency), `PropertyType`/`PropertyStatus`/`AliasSource` enums, `Coordinates`/`Address`/
  `PropertyAlias`/`PropertySource` value objects, and the `Property` aggregate with a lifecycle
  state machine (DRAFT→ACTIVE via `activate`; DRAFT/ACTIVE→HIDDEN via `hide`; any→MERGED via
  `mergeInto`, terminal) plus `IllegalPropertyStateTransitionException`. Invariants mirror the `V3.1`
  CHECKs (merge target only when MERGED, no self-parent, no self-merge, non-blank name; coordinate
  ranges; alias confidence in [0,1]). This activates the properties ArchUnit domain-purity rules for
  the first time (they pass — the domain uses only `java.time`/`java.util`). 16 unit tests;
  `./scripts/check.sh` passes.
- 2026-07-15: Plan approved (plan mode; multi-module/migration/public-API triggers). Chunk 1 started
  on `feat/004-properties-chunk1-foundation`: `V3.1` creates the `properties` schema and the
  `address`/`property`/`property_alias`/`property_source` tables with type/status/merge/name CHECK
  constraints, `created_by` as an opaque account id (no cross-module FK), and coordinates as plain
  lat/lng (PostGIS geometry deferred to chunk 5). Migration verified directly against dev Postgres
  (tables created; invalid type, non-`MERGED` merge target, and blank name all rejected), then
  covered by `PropertiesMigrationIntegrationTest`.

## Final outcome

Not yet complete.

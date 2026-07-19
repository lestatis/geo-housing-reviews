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
| Geo | `latitude`/`longitude` columns now; PostGIS `geography(Point,4326)` + `hibernate-spatial` in the duplicate/geo chunk behind an ADR | Don't add a geo dependency before it's used (AGENTS rule 5) |
| `properties.api` | Minimal/empty until `reviews` needs property lookup | No consumer yet (mirrors `identity.api`) |
| AuthZ | Create = any authenticated user (DRAFT); merge/hide/status = admin; reads authenticated for now | Reuses the existing security chain; public browsing is a later decision |

## Identity migration registry (append-only)

| Version | Contents | Chunk |
|---|---|---|
| `V3.1` | `properties` schema; `address`, `property`, `property_alias`, `property_source` | 1 |
| `V3.2` | reserved — duplicate/geo (PostGIS geography column, indexes) | 5 |
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

- 2026-07-15: Plan approved (plan mode; multi-module/migration/public-API triggers). Chunk 1 started
  on `feat/004-properties-chunk1-foundation`: `V3.1` creates the `properties` schema and the
  `address`/`property`/`property_alias`/`property_source` tables with type/status/merge/name CHECK
  constraints, `created_by` as an opaque account id (no cross-module FK), and coordinates as plain
  lat/lng (PostGIS geometry deferred to chunk 5). Migration verified directly against dev Postgres
  (tables created; invalid type, non-`MERGED` merge target, and blank name all rejected), then
  covered by `PropertiesMigrationIntegrationTest`.

## Final outcome

Not yet complete.

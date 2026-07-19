# Task handoff

## Objective

Build the `properties` backend module (plan `docs/plans/004-properties-module.md`): the canonical
catalogue of reviewable objects with addresses, aliases, coordinates, hierarchy, status lifecycle,
merge history, and duplicate handling. This is the foundation reviews/search/verification/moderation
all point at. The identity module (plan `002`) is complete and merged.

## Active branch

`feat/004-properties-chunk1-foundation` (branched from `main` at `068e647`, after the identity
module completed)

## Related issue or plan

No issue. See `docs/plans/004-properties-module.md` (module plan, 8 chunks) — this is chunk 1.

## Current status

chunk1_implemented — ready for fresh independent review and merge before chunk 2.

## Completed work

Identity module (plan `002`) is complete: all 8 chunks merged to `main`, ending at `068e647`
(export/delete). See `docs/plans/002-identity-module.md` for its history.

### Properties chunk 1 — foundation + schema (this branch)

- `modules/properties/build.gradle.kts`: added the Spring Boot dependencies platform and
  `spring-boot-starter-data-jpa` (entities land in chunk 4; web deferred to chunk 6) plus the
  `assertj-core` test dep — mirroring identity's chunk-1 module build.
- `V3.1__create_property_catalogue.sql`: creates the `properties` schema and four tables —
  `address`, `property`, `property_alias`, `property_source`. Key points:
  - `property.created_by` is an **opaque `UUID`** (the identity account id), deliberately **not** a
    foreign key to `identity.account` — modules never reference another module's tables.
  - Coordinates are plain `latitude`/`longitude`; the PostGIS `geography` column + `hibernate-spatial`
    are deferred to chunk 5 (the duplicate/geo chunk) behind an ADR, so no geo dependency is pulled
    in yet.
  - CHECKs: `type`/`status` enums; a merge target only when `status = 'MERGED'`; no self-parent /
    self-merge; non-blank `canonical_name` and alias `name`; `confidence` in `[0,1]`.
- `PropertiesMigrationIntegrationTest` (Testcontainers): asserts `V3.1` applied, the four tables
  exist, and the CHECKs reject an invalid type, an invalid status, a non-`MERGED` merge target, and
  a blank name.

## Remaining work

Chunks 2–8 (see the plan): domain model (2), application layer (3), persistence (4), duplicate
handling + geo with PostGIS/`hibernate-spatial` + ADR-0007 (5), public `/api/properties` endpoints
(6), admin merge/status (7), `properties.api` contract when reviews needs it (8).

## Decisions made

- Migration registry `V3.x` (root=1, identity=2, properties=3); `V3.1` = catalogue, `V3.2`/`V3.3`
  reserved (see the plan's registry table).
- No cross-module FKs; creator stored as an opaque account id.
- Geo deferred to chunk 5 to avoid an unused `hibernate-spatial` dependency now.
- Create = any authenticated user (DRAFT); merge/hide/status = admin; reads authenticated for now.

## Files changed on this branch

- `modules/properties/build.gradle.kts`
- `modules/properties/src/main/resources/db/migration/properties/V3.1__create_property_catalogue.sql`
- `app/src/test/java/com/example/geohousing/app/properties/PropertiesMigrationIntegrationTest.java`
- `docs/plans/004-properties-module.md` (new), `docs/handoffs/current-task.md`

## Tests and verification

Run on this branch, 2026-07-15:

- `./gradlew :modules:properties:check` — passed (module now builds with data-jpa; spotless/checkstyle).
- `./gradlew :app:test` — passed. `PropertiesMigrationIntegrationTest` (5): migration applied, four
  tables exist, invalid type/status rejected, non-`MERGED` merge target rejected, blank name rejected.
- `./scripts/check.sh` — passed (governance + full Gradle gate; frontend skipped).
- `V3.1` was also applied directly to a scratch dev-Postgres DB before wiring the test: tables
  created; a valid insert accepted; invalid type, non-`MERGED` merge target, and blank name rejected.

## Known failures

None.

## Risks and unresolved questions

- Reads currently require authentication (the chain is `anyRequest().authenticated()`). Whether the
  property catalogue should be publicly browsable without login is a product decision, deferred.
- Geo/duplicate detection (chunk 5) will add `hibernate-spatial` and a PostGIS column via a new
  migration + ADR-0007 — the first new production dependency since identity.

## Human actions required

Review and merge `feat/004-properties-chunk1-foundation` into `main` after a fresh independent review
(this branch was implemented by Claude Code; the review must be a fresh independent pass — e.g.
Codex — not self-review). The branch is local and not pushed; there is no credential path to push
from this environment.

## Recommended next action

Obtain a fresh independent review of chunk 1, then merge to `main`. Chunk 2 (domain model) branches
from `main` after that and activates the properties ArchUnit domain-purity rules for the first time.

## Last updated

2026-07-15

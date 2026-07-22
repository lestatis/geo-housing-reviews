# Task handoff

## Objective

Build the `properties` backend module (plan `docs/plans/004-properties-module.md`): the canonical
catalogue of reviewable objects. This is chunk 7 (admin lifecycle + audit). Identity (plan `002`),
Swagger/OpenAPI, and properties chunks 1–6 are merged to `main`.

## Active branch

`feat/004-properties-chunk7-admin` (branched from `main` at `44ed465`)

## Related issue or plan

No issue. See `docs/plans/004-properties-module.md` — this is chunk 7 of 8.

## Current status

chunk7_implemented — ready for fresh independent review and merge before chunk 8 (the last one).

## Completed work

On `main`: identity complete (ends `068e647`), the Swagger/OpenAPI feature (`6d9096b`), and properties
chunks 1–6 — schema (`293da3d`), domain (`af8ed3d`), application (`a07a220`), persistence (`995a665`),
duplicates + geo (`10be962`), public endpoints (`44ed465`).

### Properties chunk 7 — admin lifecycle + audit (this branch)

- `AdminPropertyController` (`/api/admin/properties`, already ROLE_ADMIN-gated): `POST
  /{id}/activate|hide|merge` — action sub-resources rather than a `PATCH status`, so only transitions
  the state machine allows are expressible. Each carries the `version` the admin saw; stale → 409
  `PROPERTY_VERSION_CONFLICT` (API_GUIDELINES: conflict when a moderator acts on stale content).
- **First persisted update path in the module.** Before this chunk the aggregate could transition in
  memory but nothing could save it. `PropertyJpaEntity.applyLifecycleChange` writes only
  status/merge-target/updated-at; name, address, aliases and sources still need their own update path.
- **Audit atomicity:** an admin mutation must never be applied unaudited, and the application layer is
  framework-free (no `@Transactional` there), so `PropertyAdminRepository` takes the mutated property
  *and* the audit event, and `JpaPropertyAdminRepository` writes both in one `@Transactional` method.
- `V3.3` adds the append-only `property_admin_audit_event`. Neither id column is a foreign key:
  `admin_account_id` because identity owns accounts (no cross-module FK), and `property_id` because an
  action against a missing property must still be auditable — the same lesson already learned in
  identity's `V2.5`.
- A missing property records `NOT_FOUND` and returns `Optional.empty()` (controller → 404), keeping
  the audit on the committing path instead of losing it to a throw.

### Properties chunk 6 — public endpoints (merged to `main`)

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

Chunk 8 only (see the plan): the `properties.api` cross-module contract, built when `reviews` needs
property lookup — it may reasonably stay a stub until then, in which case the module is effectively
complete at chunk 7.

Known gaps, deliberately deferred: editing a property's name/address/aliases (a separate update path
from the lifecycle one); auditing *rejected* admin attempts (version conflicts / illegal transitions
are not recorded, matching identity's precedent); un-hide (`HIDDEN→ACTIVE`); address-component
duplicate matching; cursor pagination; enabling `-parameters` in the convention build.

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

## Files changed on this branch (chunk 7)

- New: `db/migration/properties/V3.3__create_property_admin_audit_event.sql`
- New domain: `AdminId`, `PropertyAdminAction`, `PropertyAdminOutcome`, `PropertyAdminAuditEvent`,
  `PropertyVersionConflictException`
- New application: `PropertyAdminRepository`, `AdminPropertyService`
- New persistence: `PropertyAdminAuditEventJpaEntity`, `PropertyAdminAuditEventJpaMapper`,
  `SpringDataPropertyAdminAuditEventRepository`, `JpaPropertyAdminRepository`
- New web: `AdminPropertyController`, `AdminLifecycleRequest`
- Edit: `PropertyJpaEntity` (+`applyLifecycleChange`), `PropertiesExceptionHandler` (+409
  `PROPERTY_VERSION_CONFLICT`), `PropertiesBeanConfiguration` (+`adminPropertyService`),
  `WebAuthentication` (+`adminId`)
- New tests: `AdminPropertyServiceTest`, `app/.../properties/AdminPropertyEndpointIntegrationTest.java`
- `docs/plans/004-properties-module.md`, `docs/handoffs/current-task.md`

New migration (`V3.3`), no new dependency, no app-module or security-config change.

## Tests and verification

Run on this branch, 2026-07-20:

- `./gradlew :modules:properties:check` — passed (**28 module tests**). `AdminPropertyServiceTest` (6):
  activate/hide/merge apply and audit `APPLIED`; unknown property records `NOT_FOUND` and returns
  empty; version conflict propagates; a merged property refuses further transitions.
- `./gradlew :app:test` — passed (**67 app tests, 0 failures**), incl. ArchUnit.
  `AdminPropertyEndpointIntegrationTest` (7): anonymous → 401; `USER` → 403; ADMIN activate → 200 with
  DB `ACTIVE` + an `APPLIED` audit row; hide → `HIDDEN`; merge → `MERGED` with `merged_into_property_id`
  set; **stale version → 409 and the row is unchanged with no audit row**; a merged property → 409
  `PROPERTY_STATE_CONFLICT`; unknown id → 404 with a `NOT_FOUND` audit row.
- `./scripts/check.sh` — passed (governance + full Gradle gate; frontend skipped).
- `V3.3` was applied to a scratch dev-Postgres DB first, including inserting an audit row for a
  non-existent property — confirming the deliberate absence of a foreign key on `property_id`.

## Known failures

None.

## Risks and unresolved questions

- Rejected admin attempts (version conflict, illegal transition) are not audited — only `APPLIED` and
  `NOT_FOUND`. For a moderation trail this is a real gap; recording them needs the mutation and audit
  to be in separate transactions, which was judged out of scope here.
- The merge target must be an existing property (`merged_into_property_id` has an FK from `V3.1`), and
  the target is not itself validated as non-merged — merging into an already-merged property would
  create a chain. Worth a guard when merge gets real use.
- Editing name/address/aliases still has no persisted path.

## Human actions required

Review and merge `feat/004-properties-chunk7-admin` after a fresh independent review (implemented by
Claude Code; review must be a fresh independent pass — the audit-atomicity design, the optimistic
concurrency, and the no-FK audit columns are the parts worth close attention). The branch is local and
not pushed; there is no credential path to push from this environment.

## Recommended next action

Independent review of chunk 7, then merge to `main`. Chunk 8 (`properties.api` cross-module contract)
is the last one and is genuinely optional until `reviews` exists — consider closing plan `004` at
chunk 7 and building the contract when the consumer arrives.

## Last updated

2026-07-20

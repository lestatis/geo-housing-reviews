# ADR-0007: PostGIS duplicate detection via native queries, no hibernate-spatial

Status: Accepted
Date: 2026-07-20
Deciders: Founders
Related: `docs/plans/004-properties-module.md` (chunk 5), `docs/ARCHITECTURE.md` §8

## Context

The properties module must surface likely-duplicate properties when a user creates one, from the
normalized name and (optional) coordinates (`docs/MVP_SCOPE.md`: "property creation and duplicate
handling"; ARCHITECTURE §8: "deterministic duplicate candidates", PostgreSQL/PostGIS first). The
`DuplicateCandidateFinder` port (chunk 3) needs a real implementation.

The property row already stores `latitude`/`longitude` (plain columns, chunk 1). We need geographic
proximity ("within N metres") which those raw columns cannot index efficiently on their own.

## Decision drivers

- Deterministic, index-assisted proximity, PostgreSQL-first (ARCHITECTURE §8).
- Do not add a production dependency without measured need (AGENTS.md §3.5).
- Keep the domain framework/library-free; keep `ddl-auto=validate` green.

## Considered options

### Option A — native SQL with a generated geography column (chosen)

Add a `geo geography(Point,4326)` column to `properties.property`, `GENERATED ALWAYS AS` a PostGIS
point derived from `longitude`/`latitude` (`STORED`), with a GiST index. Run duplicate detection with
a native `@Query` using `ST_DWithin(geo, …, :metres)` plus a normalized-name equality match backed by
a functional index. No entity maps `geo`.

- Pros: no new dependency; the domain and JPA entity are untouched; `ddl-auto=validate` is unaffected
  (Hibernate ignores an unmapped column); `geo` has a single source of truth (lat/lng) and is never
  written by the app; index-assisted.
- Cons: proximity logic lives in SQL rather than in Java/HQL; geography type is not available as a
  typed value in the ORM (we do not need it).

### Option B — `hibernate-spatial` + JTS, mapped geometry field

Add `hibernate-spatial` (which pulls JTS), map a `Point` field on `PropertyJpaEntity`, and use spatial
functions in HQL.

- Rejected: it adds a production dependency and changes the entity mapping (which `ddl-auto=validate`
  then checks against the column) to buy ORM integration we do not require — the only need is a
  proximity filter, which Option A satisfies with plain SQL. `hibernate-spatial` becomes justified
  only if a future feature needs to read/write geometry as typed domain/ORM values.

## Decision

Use Option A. Duplicate detection is a native PostGIS query over a generated `geo` column;
`hibernate-spatial` is not added.

## Consequences

- `V3.2` adds the generated `geo` column and the GiST + normalized-name indexes.
- The finder matches on normalized `canonical_name` equality OR `ST_DWithin` proximity (default 75 m,
  a tunable constant), excluding `MERGED` properties.
- Address-*component* matching (same street/building) is a later additive refinement; chunk 5 ships
  name + geo, the two strong index-backed signals.

## Revisit when

- A feature needs geometry as a typed ORM/domain value (then reconsider `hibernate-spatial`), or
- proximity/scale needs outgrow PostgreSQL/PostGIS (ARCHITECTURE §8 already flags OpenSearch as the
  later option).

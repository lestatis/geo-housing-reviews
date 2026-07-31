# Property Search: the Missing Half of MVP Loop 1

Status: Complete
Owner: Claude
Related issue: none
Last updated: 2026-07-30

## Objective

Let a resident find their building. The catalogue exists but nothing can search it, so MVP loop 1
("Find property → understand experience") stops at its first step and every other loop assumes a
step that cannot happen.

## Acceptance criteria

- [x] Searching by name fragment, alias, address fragment or proximity returns ranked matches from
      the active catalogue, with typo tolerance and without a language-specific configuration.
- [x] Withdrawn and merged properties are never findable; user-contributed drafts are (corrected
      in chunk 2 — see below).
- [x] The search is reachable over HTTP without an account (`P-011`), with bounded limit and radius.
- [x] Loop 1's feature file covers name, alias, address, proximity and the exclusions.

## Non-goals

- A `search` module, a projection, or a dedicated search engine (see Decisions).
- Cross-entity search over reviews or areas.
- Faceting, autocomplete, paging beyond a bounded limit, or relevance tuning knobs.
- Geocoding free-text addresses into coordinates.

## Decisions

| Decision | Choice | Reason | Revisit when |
|---|---|---|---|
| Module | Built in `properties`, not `search` | Search needs `property`, `property_alias` and `address`; another module may not read them, so it would need a projection. ARCHITECTURE §8 prescribes trigram + PostGIS over the owning tables, and §5 gives `search` no responsibilities — it is a placeholder directory | Cross-entity search or a dedicated engine is justified |
| Matching | `word_similarity` (`<%`), not `similarity` (`%`) | Measured: `similarity('orbi','Orbi Sea Towers Residence')` = 0.19, below the 0.3 threshold, so the building would not come back at all. Word similarity scores the same pair 1.0 | — |
| Operand order | Query on the left of `<%` | Verified with EXPLAIN: the reverse order plans a sequential scan even with the GIN index present | — |
| Index type | Trigram GIN, not `tsvector` | PostgreSQL ships no Georgian full-text configuration, and the launch market is Georgian/Russian/English (`P-002`). Trigram is language-agnostic and tolerates the typos people make | A Georgian dictionary exists and quality demands stemming |
| Visibility | Everything except `HIDDEN` and `MERGED` | `PropertyCatalogService.visibilityOf` already rules that a user-contributed DRAFT is publicly readable; only an administrator hiding one withholds it. Chunk 1 filtered to `ACTIVE` and was wrong — see the chunk-2 log | — |

## Implementation chunks

1. **Migration + repository query** (this branch): `V3.4`, the native ranked query, integration
   tests against real PostgreSQL.
2. **Application + endpoint + scenarios**: query object with validation and clamping,
   `PropertySearchService`, `GET /api/properties/search`, loop 1 scenarios, and the
   `PropertyQueryService` javadoc correction ("richer listing/filtering is the search module's job"
   is no longer true).

## Verification

```bash
cd apps/api
./gradlew :modules:properties:check
./gradlew :app:test --tests '*PropertySearchIntegrationTest'
cd .. && ./scripts/check.sh          # read the EXIT= marker
```

## Risks and rollback/forward-fix

`pg_trgm` is a contrib extension; confirmed present in `postgis/postgis:18-3.6` before the migration
was written. The 0.3 default similarity threshold is a Postgres session setting the query relies on
implicitly — if match quality needs tuning, set it explicitly in the query rather than globally.
Migrations are append-only; a forward fix adds indexes rather than editing `V3.4`.

## Progress log

- 2026-07-30: Plan created after the founder chose search as the next module. Started chunk 1 on
  `feat/011-property-search-chunk1-query` from clean `main` at `08fe4b2`.
- 2026-07-30: Chunk 1 implemented. `V3.4` adds `pg_trgm` and four GIN trigram indexes plus a partial
  index on active rows, verified statement-by-statement on scratch PostgreSQL — including that
  `pg_trgm` is available in the image at all, since the whole approach rests on it.

  The query shape was validated on real data before any Java was written, and that changed the
  design: plain `similarity` scores "orbi" against "Orbi Sea Towers Residence" at **0.19**, below
  the 0.3 threshold, so the plan's original shape would have shipped a search that misses longer
  names. `word_similarity` scores the same pair 1.0. EXPLAIN then showed the operand order matters —
  the query must sit on the left of `<%` or the GIN index is not used.

  12 integration tests prove what only a database can: fragment, typo (`orbe`), Georgian alias
  against an English name, street and city fragments, proximity with distance ordering, a tight
  radius excluding what is outside it, the limit, no false positives, all three excluded statuses,
  and that the index can serve the predicate. Mutation: properties 76% (threshold 75).
  `./scripts/check.sh` passes. Ready for independent review.

- 2026-07-30: Chunk 1 fast-forward merged to `main` at `776a700`.
- 2026-07-30: Chunk 2 implemented on `feat/011-property-search-chunk2-endpoint`, scenarios first —
  and the first thing they exposed was a bug shipped in chunk 1.

  **Chunk 1 filtered search to `ACTIVE` properties.** That contradicts this module's own documented
  rule: `PropertyCatalogService.visibilityOf` states that DRAFT properties are user-contributed and
  already publicly readable, and only an administrator hiding one withholds it. Properties are
  *created* DRAFT and stay that way until an administrator activates them, so the shipped behaviour
  meant a resident could create a property, review it, and then never find it again — including
  their own. Chunk 1's integration test asserted that broken behaviour with a comment claiming DRAFT
  "is awaiting an administrator", which was simply wrong about this module.

  Fixed forward: `V3.5` drops the `ACTIVE`-only partial index and adds one over everything not
  `HIDDEN` or `MERGED`; the query filter matches; the test now asserts a user-contributed property
  *is* findable, and that withdrawn and superseded ones are not.

  `PropertySearchQuery` refuses a search with neither text nor point — an empty search is a table
  scan any caller could trigger — and clamps the limit and radius. `GET /api/properties/search` is
  anonymous per `P-011`. The stale `PropertyQueryService` javadoc ("richer listing/filtering is the
  search module's job") now points at `PropertySearchService`.

  6 loop 1 scenarios (42 total), 8 query-object tests, 13 integration tests. Mutation: properties
  77% (threshold 75). `./scripts/check.sh` passes.

## Final outcome

Complete at 2 chunks, pending independent review of chunk 2.

MVP loop 1 works: a resident can find a building by a fragment of its name, a misspelling, its
Georgian name, or its street — or by standing near it — and reach its reviews. That was the last
Must-have blocking the loop.

Deliberately not built: a `search` module or projection, cross-entity search, autocomplete,
faceting, paging beyond a bounded limit, and geocoding free text into coordinates. The remaining MVP
Must-have gaps are the admin interface, right of reply, and basic analytics.

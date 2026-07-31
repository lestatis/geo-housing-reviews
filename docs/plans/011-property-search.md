# Property Search: the Missing Half of MVP Loop 1

Status: Active
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
- [x] Draft, withdrawn and merged properties are never findable.
- [ ] The search is reachable over HTTP without an account (`P-011`), with bounded limit and radius.
- [ ] Loop 1's feature file covers name, alias, address, proximity and the three exclusions.

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
| Visibility | `ACTIVE` only | DRAFT is awaiting an administrator, HIDDEN was withdrawn, MERGED points elsewhere — surfacing any leaks a queue or leads to a dead record | — |

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

## Final outcome

Not yet complete.

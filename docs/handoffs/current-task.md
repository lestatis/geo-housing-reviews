# Task handoff

## Objective

Implement plan 011, chunk 1: the catalogue search query — `V3.4` trigram/PostGIS indexes and the
ranked native query, proven against real PostgreSQL. No endpoint yet.

## Active branch

`feat/011-property-search-chunk1-query`, branched from clean `main` at `08fe4b2`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/011-property-search.md`, chunk 1 of 2.

## Current status

completed, awaiting independent review

## Completed work

- `V3.4__add_property_search_indexes.sql`: `pg_trgm`, GIN trigram indexes on canonical name, alias
  name, street and city, plus a partial index on active rows.
- `SpringDataPropertyRepository.search(...)` — ranked native query.
- `PropertySearchProjection`, `PropertyMatch`, `PropertyRepository.search(...)` and its adapter.
- `PropertySearchIntegrationTest` (12 tests), `PropertyMatchTest` (4 tests).
- The four in-module `PropertyRepository` fakes gained the new method.

## Remaining work

Chunk 2: `PropertySearchQuery` with validation and clamping, `PropertySearchService`,
`GET /api/properties/search`, loop 1 scenarios, and the `PropertyQueryService` javadoc correction.

## Decisions made

- **Built in `properties`, not the `search` module.** Search needs `property`, `property_alias` and
  `address`; another module may not read them, so it would need a projection — infrastructure for
  scale nobody has measured. ARCHITECTURE §8 prescribes trigram + PostGIS over the owning tables,
  and §5 gives `search` no responsibilities section, unlike every implemented module. **This is the
  decision most worth pushing back on.**
- **`word_similarity` (`<%`), not `similarity` (`%`).** Measured on real data:
  `similarity(\'orbi\', \'Orbi Sea Towers Residence\')` = 0.19, *below* the 0.3 threshold, so the
  building would not be returned at all. Word similarity scores it 1.0. The plan\'s original query
  shape was wrong and validating it first is what caught it.
- **Query on the left of `<%`.** EXPLAIN shows the reverse order plans a sequential scan even with
  the index present.
- **Trigram, not `tsvector`.** PostgreSQL ships no Georgian full-text configuration and the launch
  market is Georgian/Russian/English (`P-002`); trigram is language-agnostic and typo-tolerant.
- **`ACTIVE` only.** DRAFT awaits an administrator, HIDDEN was withdrawn, MERGED points elsewhere.
- **The in-module fakes throw rather than return an empty list** for `search`, so a use-case test
  that starts depending on search fails loudly instead of silently seeing no results.

## Assumptions

- The 0.3 `pg_trgm.similarity_threshold` default is relied on implicitly. If match quality needs
  tuning it should be set in the query, not globally.

## Files changed

- new `modules/properties/src/main/resources/db/migration/properties/V3.4__add_property_search_indexes.sql`
- new `modules/properties/.../infrastructure/persistence/PropertySearchProjection.java`;
  `SpringDataPropertyRepository`, `JpaPropertyRepository`
- new `modules/properties/.../application/PropertyMatch.java`; `PropertyRepository`
- new `app/src/test/.../properties/PropertySearchIntegrationTest.java`
- new `modules/properties/src/test/.../application/PropertyMatchTest.java`; four existing test fakes
- new `docs/plans/011-property-search.md`; `docs/handoffs/current-task.md`

## Commands run

- scratch PostgreSQL: `pg_available_extensions` check, V3.1-V3.4 applied, then each search term run
  separately against seeded data
- `cd apps/api && ./gradlew :app:test --tests \'*PropertySearchIntegrationTest\'`
- `cd apps/api && ./gradlew :modules:properties:mutationTest --rerun-tasks`
- `./scripts/check.sh` -> `EXIT=0`, 4m 56s

## Tests and verification

All passed on 2026-07-30. Mutation: properties 76% (threshold 75).

12 integration tests cover what only a real database can prove: name fragment, typo (`orbe`),
Georgian alias against an English name, street and city fragments, proximity with distance ordering,
a tight radius excluding what is outside it, the limit, no false positives on gibberish, all three
excluded statuses, and that the GIN index can serve the predicate (asserted with `enable_seqscan`
off, since four rows would always plan a scan otherwise).

One verification mistake worth recording: an early scratch run appeared to prove typo and Georgian
matching, but a `\\set` at the top of the SQL file overrode the `-v` parameter, so all three runs
actually queried the same term. Re-run per term, the results held — but the first pass proved
nothing.

## Known failures

None observed.

## Risks and unresolved questions

- Properties is back to 76% against a threshold of 75, its usual margin. Chunk 2 adds a service and
  a query object, both of which need their own tests.
- Nothing is reachable over HTTP yet; the search exists only as a port method.
- `ST_Distance` is computed for every candidate row when a point is supplied. Fine at launch
  volume, and `ST_DWithin` bounds the candidate set first, but it is the query to watch.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge. When requested, start plan 011
chunk 2 (service, endpoint and loop 1 scenarios) from `main`.

## Last updated

2026-07-30

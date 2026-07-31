# Task handoff

## Objective

Implement plan 011, chunk 2: the search service and endpoint, loop 1 scenarios — and a fix-forward
on a visibility bug shipped in chunk 1.

## Active branch

`feat/011-property-search-chunk2-endpoint`, branched from clean `main` at `776a700`. Local only; not
pushed. Awaiting a fresh independent review before merge.

## Related issue or plan

No issue. `docs/plans/011-property-search.md`, chunk 2 of 2 — the plan is now **Complete**.

## Current status

completed, awaiting independent review

## Completed work

- **Fix**: search no longer filters to `ACTIVE`. `V3.5` replaces the partial index; the query now
  excludes only `HIDDEN` and `MERGED`.
- `PropertySearchQuery` (validation and clamping), `PropertySearchService`, bean wiring.
- `GET /api/properties/search`, `PropertySearchHitResponse`, `PropertySearchResponse`.
- 6 loop 1 scenarios and their steps; `ScenarioState` remembers the last created property.
- `PropertyQueryService` javadoc corrected.
- `PropertySearchQueryTest` (8 tests); the chunk-1 integration test corrected and extended.

## Remaining work

None for plan 011. Remaining MVP Must-have gaps: admin interface, right of reply, basic analytics.

## Decisions made

- **A user-contributed DRAFT property is findable.** This is the fix. `PropertyCatalogService`
  already rules that DRAFT is publicly readable and only an administrator hiding one withholds it.
  Properties are created DRAFT and stay so until activated, so filtering search to ACTIVE meant a
  resident could create a property, review it, and never find it again — including their own.
- **`V3.5` rather than editing `V3.4`.** Migrations are append-only once merged, so the corrective
  index drops the old one and adds the right one.
- **A search with neither text nor point is refused.** An empty search returns the catalogue ordered
  by nothing in particular — the listing endpoint\'s job — and at scale it is a table scan any caller
  could trigger at will.
- **Half a point is no point.** A latitude without a longitude is discarded rather than treated as a
  location, which would search from the equator and quietly return nothing.
- **The service is deliberately thin.** Ranking belongs to the database; duplicating any of it in
  Java would create a second place for relevance to disagree with itself.

## Assumptions

- Default radius 2 km, max 50 km; default limit 20, max 50. Chosen for a city-scale launch (`P-001`,
  Batumi only) rather than measured.

## Files changed

- new `modules/properties/src/main/resources/db/migration/properties/V3.5__index_searchable_properties.sql`
- `SpringDataPropertyRepository` (visibility filter + javadoc)
- new `modules/properties/.../application/PropertySearchQuery.java`, `PropertySearchService.java`;
  `PropertyQueryService` javadoc; `PropertiesBeanConfiguration`
- new `modules/properties/.../infrastructure/web/PropertySearchHitResponse.java`,
  `PropertySearchResponse.java`; `PropertyController`
- `app/src/test/resources/features/find-property.feature` (4 -> 10 scenarios);
  `CatalogueSteps`, `ScenarioState`
- new `modules/properties/src/test/.../application/PropertySearchQueryTest.java`;
  `PropertySearchIntegrationTest` corrected
- `docs/plans/011-property-search.md` (closed), `docs/handoffs/current-task.md`

## Commands run

- `cd apps/api && ./gradlew :app:test --tests \'*AcceptanceTest\'` (red first, then green)
- `cd apps/api && ./gradlew :app:test --tests \'*PropertySearchIntegrationTest\'`
- `cd apps/api && ./gradlew :modules:properties:mutationTest --rerun-tasks`
- `./scripts/check.sh` -> `EXIT=0`, 4m 48s

## Tests and verification

All passed on 2026-07-30. Acceptance suite: **42 scenarios**, 0 failures. Mutation: properties 77%
(threshold 75).

MVP loop 1 now works end to end: a resident finds a building by a name fragment, a misspelling, its
Georgian name or its street — or by proximity — and reaches its reviews.

**The scenarios caught the chunk-1 visibility bug**, which is the second time this session that
writing them first has found something the unit tests agreed with. The chunk-1 integration test had
encoded the wrong rule *and explained it in a comment*, so it would never have failed on its own.

## Known failures

None observed.

## Risks and unresolved questions

- `V3.4`\'s `property_active_idx` existed only briefly and is dropped by `V3.5`. Anyone who deployed
  between the two gets the drop cleanly; nothing depended on it.
- Search has no paging — a bounded limit only. Fine for a single-city launch, first thing to revisit
  when the catalogue grows.
- Ranking is trigram score then distance. No signal for review count, verification or recency yet;
  that is a ranking decision, not a search one, and belongs with the ranking work `P-012` started.
- `ST_Distance` is computed per candidate row when a point is given. `ST_DWithin` bounds the
  candidates first, so this is a watch item rather than a problem.

## Human actions required

None.

## Recommended next action

Independent review of this branch in a fresh session, then merge — plan 011 then closes. The next
task is a new plan; the open MVP Must-haves are the admin interface (where Playwright would finally
earn its place), right of reply, and basic analytics.

## Last updated

2026-07-30

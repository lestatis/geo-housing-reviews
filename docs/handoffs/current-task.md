# Task handoff

## Objective

Plan 025 — withhold a property an administrator hid from the public read (`GET /api/properties/{id}`
→ 404 like an unknown id; the public list excludes hidden and merged properties), add an admin read
`GET /api/admin/properties/{id}`, and switch `apps/web`'s property page to it so moderators keep
access.

## Active branch

`fix/025-withhold-hidden-properties`, created from `main` at `1d37c28` (023-C merged in #9) on
2026-11-15. Not pushed.

## Related issue or plan

`docs/plans/025-withhold-hidden-properties.md` (chunk 025-A, `ai-l1` block); origin: plan 023
follow-up 10.

## Current status

in_progress — 025-A L1-green (8/8) and reviewed `READY_TO_MERGE` (`.ai/runs/implementation-loop.p4gghe`);
lead L2 and local-stack checks green 2026-11-15 (see plan 025 progress log). Uncommitted; awaiting the
founder's commit/push/PR decision. `apps/web` `pnpm e2e` not run (needs `.env.local`).

## Completed work

- Verified against `1d37c28`: public `get` and `list` have no status filter; no admin read endpoint
  exists; `apps/web`'s property page reads the public endpoint; admin review reads are not audited.
- Plan 025 written; plan 023 follow-up 10 points to it.
- Application: `PropertyQueryService.getPublicById` throws `PropertyNotFoundException` for `HIDDEN`
  (identical to an unknown id) and still returns `MERGED` as a tombstone; `listRecentPublic`
  excludes `HIDDEN`/`MERGED` and clamps the limit. `getById` remains the unfiltered admin read.
- Persistence: `SpringDataPropertyRepository.findRecentExcluding` filters the excluded statuses in
  JPQL; `JpaPropertyRepository` passes `HIDDEN`/`MERGED`, and `PropertyQueryService` also filters
  defensively at the application boundary.
- Public controller: `get`/`list` use the public variants; the create response is unchanged.
- Admin controller: `GET /api/admin/properties/{propertyId}` returns `AdminPropertyResponse` for any
  status, with 400 for a malformed id and 404 for an unknown one. The class Javadoc now separates
  the unaudited read from the audited lifecycle actions.
- Web: `apps/web/app/properties/[propertyId]/page.tsx` reads the admin endpoint; `toPropertyRow`
  accepts `AdminProperty`, and its test covers the admin shape including `originalText`.
- Tests: `PropertyQueryServiceTest` covers hidden/merged/active/unknown public reads, list
  withholding and limit clamping; `AdminPropertyEndpointIntegrationTest` covers 401/403, hidden 200
  with `originalText`, unknown 404, malformed 400 and merged admin read. Gherkin has three new
  scenarios (public withholding with an identical 404 problem body, moderator read, admin-only
  gate). The 404 comparison excludes the RFC 7807 `instance` path, which necessarily differs by id,
  and separately asserts each `instance` matches its own request.
- Contract/docs: `docs/api/openapi.json` has the new admin read operation (`getProperty`, unchanged
  schemas); `DECISION_LOG.md` has P-020; plan 025 progress updated.

## Remaining work

1. Loop runner: L1 block from plan 025 (Gradle module/app tests, spotless/checkstyle, admin-web
   lint/typecheck/test, mobile typecheck/test). Any failure comes back as a fix packet.
2. Lead: cold L2 with PITest, root pnpm gate, local-stack check (curl public 404 / admin 200;
   emulator deep link shows "no longer listed"; web page opens a hidden property for an admin).
3. Commit, push, one PR (ask the founder).

## Decisions made

See plan 025 "Decisions": hidden → public 404 identical to an unknown id; merged unchanged
(tombstone); public list excludes hidden and merged; admin property read not audited (follows the
admin review read); reviews of a hidden property unchanged; no mobile change.

## Assumptions

- No client depends on the public list including hidden properties (web uses search and detail;
  mobile uses neither).

## Files changed

- `docs/plans/025-withhold-hidden-properties.md` (new)
- `docs/plans/023-mobile-discovery.md` (follow-up 10 pointer)
- `docs/DECISION_LOG.md` (P-020)
- `docs/api/openapi.json` (admin property read)
- `docs/handoffs/current-task.md`
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/application/PropertyQueryService.java`
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/application/PropertyRepository.java`
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/infrastructure/persistence/JpaPropertyRepository.java`
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/infrastructure/persistence/SpringDataPropertyRepository.java`
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/infrastructure/web/AdminPropertyController.java`
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/infrastructure/web/PropertyController.java`
- `apps/api/modules/properties/src/test/java/com/example/geohousing/properties/application/PropertyQueryServiceTest.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/acceptance/CatalogueSteps.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/properties/AdminPropertyEndpointIntegrationTest.java`
- `apps/api/app/src/test/resources/features/find-property.feature`
- `apps/web/app/properties/[propertyId]/page.tsx`
- `apps/web/src/properties/property.ts`
- `apps/web/src/properties/property.test.ts`

`apps/web/src/api/generated/schema.d.ts` is regenerated by the admin-web scripts and is gitignored,
so it is not part of the diff.

## Commands and tests

- Google Java Format 1.28.0 `--dry-run --set-exit-if-changed` on every changed Java file: passed
  after formatting three files.
- `apps/web` `openapi-typescript` against the updated `docs/api/openapi.json`: passed; generated
  schema contains `GET /api/admin/properties/{propertyId}`.
- `apps/web` `vitest run src/properties/property.test.ts`: passed (9 tests).
- `apps/web` `tsc --noEmit`: passed.
- `apps/web` `eslint .`: passed.
- OpenAPI JSON parsed, keys verified sorted, new path present: passed.
- `git diff --check`: passed.
- Runner-owned L1 (Gradle module/app tests, spotless/checkstyle, admin-web lint/typecheck/test,
  mobile typecheck/test): skipped in this sandbox — no network or Docker. Recorded as skipped.

## Known failures

None. Round 1's acceptance failure (the 404 `instance` field) was fixed in round 2.

## Risks and unresolved questions

- Existing tests reading hidden properties through public endpoints were audited; none besides the
  updated admin/acceptance coverage depended on the old behaviour.
- `apps/web` `pnpm e2e` needs the full local stack; if it cannot run, the page is checked by hand.
- Mutation coverage and the PITest threshold are only proven by the lead's L2 run.

## Human actions required

None.

## Recommended next action

Run the plan 025 L1 block from a shell with nvm loaded, then inspect the result. Run `./gradlew
--stop` after any system JDK upgrade.

## Last updated

2026-11-15

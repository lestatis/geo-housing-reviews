# Plan 025 — withhold hidden properties from the public read

Status: Active — 025-A implemented, reviewed and L2-green 2026-11-15; awaiting PR and merge
Owner: Claude Code (lead) · implementation by a worker model
Related issue: plan 023 follow-up 10

## Context

The 023-C Android device check (2026-11-15) found that a property an administrator **hid** is still publicly readable. `GET /api/properties/{id}` returns it with 200 and its full data, and the mobile app shows a withdrawn building as if nothing happened.

That contradicts the properties module's own model:
- `PropertyCatalogService.visibilityOf` classes `HIDDEN` as **WITHHELD** ("only an administrator hiding a property withholds it");
- search excludes `HIDDEN` and `MERGED`.

The founder deferred the fix from #9 to its own task, because `apps/web`'s property page reads the public endpoint. A public 404 would therefore take withdrawn properties away from the moderators who un-hide them, so an admin read endpoint has to come first.

**Verified against `1d37c28` (main, #9 merged):**

- **`PropertyController`:**
  - `get` → `PropertyQueryService.getById`, which throws `PropertyNotFoundException` (mapped to 404 by `PropertiesExceptionHandler`) only when the property doesn't exist;
  - `list` → `listRecent` → `SpringDataPropertyRepository.findRecent`, which is `select p … order by createdAt`, **no status filter**, so hidden properties are listed too.
- **`AdminPropertyController`** (`/api/admin/properties`) has `activate`, `hide` and `merge`, and **no read**. They return `AdminPropertyResponse`, which keeps `originalText` (from #9). `/api/admin/**` already requires ADMIN in the app's `SecurityConfiguration`.
- **`apps/web/app/properties/[propertyId]/page.tsx`** reads `GET /api/properties/{propertyId}` and passes the result to `toPropertyRow` (`src/properties/property.ts`). Its 404 branch shows "No such property."
- **Audit precedent:**
  - identity audits every admin *account* read (`AdminAccountService`, personal data);
  - the admin *review* read (`AdminReviewController.get`, which carries `authorAccountId`) is **not** audited;
  - the properties audit (`PropertyAdminAction`: ACTIVATE, HIDE, MERGE, with a DB `CHECK` constraint) records state changes only.
- **The reviews module's documented rule:** a withheld property's existing reviews stay readable but take no new ones (`CatalogPropertyLookup`).
- **Mobile already handles a property 404** as "This building is no longer listed" (023-C2), so it needs no change.

## Approach

One small chunk on branch `fix/025-withhold-hidden-properties` from `main`, through the bounded loop with a runner-owned `ai-l1` block. Recorded as `docs/plans/025-withhold-hidden-properties.md`. Plan 023 follow-up 10 points to it.

### 025-A — public withholding, admin read, web switch

1. **Gherkin first** (`find-property.feature`, reusing the existing withdraw step):
   - *a property an administrator withdrew is no longer readable by the public*: the public detail answers exactly as for an unknown id (the same 404 problem body, so a withdrawn property's existence isn't confirmed), and the public list doesn't include it;
   - *a moderator can still open a withdrawn property*: the admin read returns it, with status `HIDDEN` and `address.originalText`;
   - *only administrators can use the admin property read*: anonymous → refused unauthenticated (401); a signed-in resident → refused forbidden (403).
2. **Application:** `PropertyQueryService` gets public-read variants.
   - `getPublicById` throws `PropertyNotFoundException` for `HIDDEN`, the same exception as for an unknown id.
   - `listRecentPublic` excludes `HIDDEN` and `MERGED`, matching search, through a status-filtered `findRecent` query.
   - `getById` stays for admin use.
   - `PropertyQueryServiceTest` covers hidden, merged and active for both public variants, mutation-killing.
3. **Public controller:** `get` and `list` use the public variants. The create response is unchanged.
4. **Admin read:** `AdminPropertyController` gains `GET /{propertyId}` → `AdminPropertyResponse.from(queryService.getById(...))`, which returns any status, including `HIDDEN` and `MERGED`. A malformed id gives 400 and an unknown id 404, as on the public endpoint. `AdminPropertyEndpointIntegrationTest` covers 401, 403, 200 (hidden, with `originalText`) and 404.
5. **Web:** `app/properties/[propertyId]/page.tsx` reads `GET /api/admin/properties/{propertyId}`. `toPropertyRow` accepts the admin response type, which is structurally the public shape plus `originalText`, and its test covers the admin shape. No other web change.
6. **Record:** regenerate `docs/api/openapi.json` for the new admin operation, and add a `DECISION_LOG.md` entry for P-020 (a hidden property is withheld from public reads; admins read it through the admin endpoint).

```ai-l1
cd apps/api && ./gradlew :modules:properties:test -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:test --tests '*PropertyEndpointIntegrationTest' --tests '*AdminPropertyEndpointIntegrationTest' --tests '*AcceptanceTest' --tests '*OpenApiContractIntegrationTest' --tests '*PublicNullabilityContractTest' --tests '*ArchitectureTest' -PskipMutation --rerun-tasks
cd apps/api && ./gradlew :app:spotlessCheck :app:checkstyleTest :modules:properties:spotlessCheck :modules:properties:checkstyleMain :modules:properties:checkstyleTest
pnpm --filter @geo-housing/admin-web lint
pnpm --filter @geo-housing/admin-web typecheck
pnpm --filter @geo-housing/admin-web test
pnpm --filter @geo-housing/mobile typecheck
pnpm --filter @geo-housing/mobile test
```

## Decisions (lead; founder-approved 2026-11-15)

| Decision | Choice | Why |
| --- | --- | --- |
| Public read of `HIDDEN` | 404, identical to an unknown id | Matches `visibilityOf` (WITHHELD) and search; an identical body doesn't confirm that a withdrawn property exists |
| Public read of `MERGED` | **Unchanged** (200, a tombstone with `mergedIntoPropertyId`) | Not part of the deferred finding, and a tombstone lets a client redirect. The mobile app following `mergedIntoPropertyId` is recorded as a follow-up |
| Public list | Excludes `HIDDEN` and `MERGED`, like search | Consistent visibility across the catalogue; no client depends on listing hidden properties (web uses search and detail; mobile uses neither) |
| Admin read audit | **Not audited** | Follows the admin review read, which isn't audited; account-data reads stay the only audited admin reads. Auditing would need a new action and a migration of the `CHECK` constraint, for catalogue data |
| Reviews of a hidden property | Unchanged (existing reviews stay readable) | Documented reviews-module rule; out of scope |
| Mobile | No code change | It already renders a 404 as "This building is no longer listed" |

## Verification

- **Runner L1** (above). The Gherkin scenarios fail first, then pass.
- **Lead L2:** the cold `./gradlew check --rerun-tasks --no-build-cache` with PITest (the `:modules:properties` threshold must hold), plus the root `pnpm lint && pnpm test && pnpm typecheck`.
- **Lead, on the local stack** (seeded data still in the volume):
  - `curl` the public detail of the hidden property: 404, with the same body as an unknown id;
  - the public list doesn't include it;
  - `curl` the admin read with an admin token: 200, `HIDDEN`, with `originalText`;
  - the emulator deep link to the hidden property shows "This building is no longer listed";
  - `apps/web`'s property page opens the hidden property for an admin (Playwright `pnpm e2e` if it passes locally, otherwise a manual check).
- **CI** on the PR.

## Risks

- **Existing tests** that read hidden properties through the public endpoints will fail. They are expected updates (list and detail tests). Moderation's tests use internal ports and stay green.
- **`apps/web` e2e** for properties now uses the admin endpoint through the server-side admin token. If `pnpm e2e` can't run locally, it's checked by hand and recorded.
- **Rollback:** no schema change. Reverting restores public reads of hidden properties.

## Follow-ups

1. The mobile app follows `mergedIntoPropertyId` when it opens a merged property, rather than showing
   the tombstone.

## Progress log

- 2026-11-15: Plan written after the 023-C device check found hidden properties publicly readable;
  facts verified against `1d37c28`. Founder approved; 025-A dispatched.
- 2026-11-15: 025-A complete in the loop without lead intervention
  (`.ai/runs/implementation-loop.p4gghe`): L1 round 1 failed one acceptance step (the 404 comparison
  included the RFC 7807 `instance`, which echoes the requested id); the worker compared every other
  field and checked each `instance` separately; L1 round 2 8/8; fresh review `READY_TO_MERGE` with two
  low notes (the service re-filters after the repository `LIMIT`; the web page shows a generic message
  on 403) — no change. Lead L2: cold check with PITest green, `:app` 379 tests, 0 shutdown warnings;
  root pnpm gate green. Local stack: public read of the hidden property 404 with a body identical to
  an unknown id's apart from `instance`; public list excludes it; admin read 401 anonymous, 403
  resident, 200 admin with `HIDDEN` and `originalText`; Android deep link shows "This building is no
  longer listed". `apps/web` `pnpm e2e` not run: it needs `apps/web/.env.local`, which the lead's
  permissions do not allow touching.
- 2026-11-15: 025-A implemented on `fix/025-withhold-hidden-properties`: public detail withholds
  `HIDDEN` identically to an unknown id, the public list excludes `HIDDEN`/`MERGED`, the admin read
  returns any status, `apps/web`'s property page uses the admin endpoint, and the OpenAPI contract
  and P-020 decision entry are updated. Narrow worker checks (formatter, web unit test, typecheck,
  lint, OpenAPI shape) pass; the runner-owned L1 has not run in the worker sandbox (no network or
  Docker).
- 2026-11-15: Runner L1 found one acceptance failure: the "identical 404 body" step compared the
  RFC 7807 `instance` field, which necessarily echoes the different requested path. The step now
  asserts each response's `instance` matches its own request and compares every other problem field,
  preserving the non-disclosure check.

## Final outcome

025-A implementation complete, including the runner-L1 acceptance-step fix. Pending re-run of L1,
then lead L2 and the local-stack checks.

# Task handoff

## Objective

Plan 023-C — the property experience: an anonymous user opens a property from search and reads its
published reviews in the mobile app. Includes three public contract changes: C2 (no `authorAccountId`
in public review responses), C3 (no `address.originalText` in public property responses) and
declared nullability for the public detail and review schemas, plus canonical rating category ids
(P-019).

## Active branch

`feat/023-c-property-experience`, created from `main` at `7301cd0` (023-B merged in #8) on
2026-11-15. Not committed and not pushed.

## Related issue or plan

`docs/plans/023-mobile-discovery.md`: sections C2, C3 and "023-C — property experience" (engineering
design, chunks 023-C1 and 023-C2, `ai-l1` blocks).

## Current status

in_progress — 023-C1 L1-green (5/5) and reviewed `READY_TO_MERGE` 2026-11-15
(`.ai/runs/implementation-loop.MW5d99`), committed locally. 023-C2 dispatched next.

## Completed work

- Verified against `7301cd0`: `PropertyResponse` and `ReviewResponse` are each shared by public and
  admin controllers, so C2 and C3 are splits; no admin read-one property endpoint exists; `apps/web`
  reads neither removed field outside generated types; rating categories have no canonical ids.
- **C2**: public `ReviewResponse` no longer carries `authorAccountId`; new
  `AdminReviewResponse` is returned by every `AdminReviewController` action. Internal moderation
  ports, gateway adapters and ownership checks are untouched.
- **C3**: public `PropertyResponse.AddressView` no longer carries `originalText`; new
  `AdminPropertyResponse`/`AdminAddressView` keep it and are returned by every
  `AdminPropertyController` action. The public create response uses `PropertyResponse`. No
  migration; domain and persistence shapes are unchanged.
- **Nullability**: `OpenApiConfiguration`'s customizer is now one declarative table for
  object-or-null `$ref` fields plus nullable scalar fields, with fail-fast checks on missing schemas
  and properties. It covers `PropertyResponse`, `AddressView`, `AliasView`, `ReviewListResponse`,
  `ReviewResponse`, `ContentView`, `RatingView`, `AddressSummaryView` and
  `PropertySearchHitResponse`.
- `SearchHitNullabilityContractTest` is replaced by table-driven
  `PublicNullabilityContractTest`, including positive and negative assertions and fail-fast tests.
- Gherkin covers: no `originalText` on public detail/list; no internal account identifier on the
  public feed or review read; `authorAccountId` on an admin review action; `originalText` on an admin
  property action.
- `ReviewEndpointIntegrationTest`, `AdminReviewEndpointIntegrationTest` and
  `AdminPropertyEndpointIntegrationTest` assert the public-by-absence and admin-by-presence split.
- `docs/api/openapi.json` regenerated/updated for the new schemas, split operations and nullable
  fields. `DECISION_LOG.md` has P-017 (C2), P-018 (C3) and P-019 (category ids).
- `apps/web`'s `formatDistance` accepts `number | null | undefined` and renders "—" for both nullish
  values; the property-row test covers `null` explicitly. No other admin-web code changed.

## Remaining work

1. 023-C2 loop (mobile property detail and review feed).
2. Lead: Android device check (including 200% text scaling on the review card), cold backend `check`
   with PITest, root `pnpm lint && pnpm test && pnpm typecheck`, then one PR.

## Decisions made

See plan 023 §023-C "Engineering design": splits for C2 and C3; public create response uses the
public type; `country` kept; one nullability table in `:app`; empty address line omitted; P-019
category ids (founder). The lead approved keeping `distanceMeters` nullable and widening only the
`apps/web` null-distance guard.

## Assumptions

- The mobile app is unreleased and `apps/web` reads neither removed field outside generated types,
  so the compatibility window for the removals is satisfied (to be recorded in the PR).
- `PropertySearchHitResponse.from()` can yield a null `distanceMeters` whenever the search has no
  origin coordinates; the SQL projection already returns `NULL` in that case.

## Files changed

- `apps/api/app/src/main/java/com/example/geohousing/app/config/OpenApiConfiguration.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/config/PublicNullabilityContractTest.java` (new)
- `apps/api/app/src/test/java/com/example/geohousing/app/config/SearchHitNullabilityContractTest.java` (deleted)
- `apps/api/app/src/test/java/com/example/geohousing/app/acceptance/CatalogueSteps.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/acceptance/OutcomeSteps.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/acceptance/ReviewSteps.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/acceptance/ScenarioState.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/properties/AdminPropertyEndpointIntegrationTest.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/reviews/AdminReviewEndpointIntegrationTest.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/reviews/ReviewEndpointIntegrationTest.java`
- `apps/api/app/src/test/resources/features/find-property.feature`
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/infrastructure/web/AdminAddressView.java` (new)
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/infrastructure/web/AdminPropertyController.java`
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/infrastructure/web/AdminPropertyResponse.java` (new)
- `apps/api/modules/properties/src/main/java/com/example/geohousing/properties/infrastructure/web/PropertyResponse.java`
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/infrastructure/web/AdminReviewController.java`
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/infrastructure/web/AdminReviewResponse.java` (new)
- `apps/api/modules/reviews/src/main/java/com/example/geohousing/reviews/infrastructure/web/ReviewResponse.java`
- `apps/web/src/properties/property.ts`
- `apps/web/src/properties/property.test.ts`
- `docs/DECISION_LOG.md`
- `docs/api/openapi.json`
- `docs/plans/023-mobile-discovery.md`
- `docs/handoffs/current-task.md`

`apps/web/src/api/generated/schema.d.ts` is regenerated by the admin-web scripts and is gitignored,
so it is not part of the diff.

## Commands and tests

- Google Java Format 1.28.0 dry-run on all changed/new Java files: passed (implementation run).
- `javac` compile of changed backend main and test sources against the cached Gradle classpath:
  passed (implementation run).
- Checkstyle 10.24 on changed Java files: passed (implementation run).
- `git diff --check`: passed.
- OpenAPI JSON parsed and checked: valid, sorted keys, trailing newline; public `AddressView` has no
  `originalText`, public `ReviewResponse` has no `authorAccountId`; admin schemas have both.
- `pnpm --filter @geo-housing/admin-web typecheck`: passed after the nullable-distance guard.
- `pnpm --filter @geo-housing/admin-web lint`: passed.
- `pnpm --filter @geo-housing/admin-web test`: passed (10 files, 75 tests), including the explicit
  `null` distance case.
- Gradle L1 (`:modules:properties:test`, `:modules:reviews:test`, the `:app:test` filters,
  spotless/checkstyle): skipped in this sandbox — no Docker/network. The loop runner owns them.

## Known failures

None. C1's L1 passed 5/5 in the runner.

## Risks and unresolved questions

- Moderation's appeal/report ownership checks must stay on internal ports; `*Moderation*` tests are
  in C1's L1.
- Admin DTO nullability (`AdminPropertyResponse`, `AdminReviewResponse`) is intentionally a
  follow-up; those schemas currently describe optional values as non-null.
- 023-B's Android device check was not recorded before #8 merged.

## Human actions required

The Android device checks for 023-B and 023-C need a device or emulator. They are not required to
finish 023-C1.

## Recommended next action

Lead reviews 023-C1 and dispatches 023-C2 when the C1 diff and runner-owned L1 result are accepted.

## Last updated

2026-11-15

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

in_progress — 023-C1 (`714289c`) and 023-C2 committed locally; both reviewed `READY_TO_MERGE`
(C2: `.ai/runs/implementation-loop.JNcC1u/review-2.json`). Lead L2 green 2026-11-15: cold
`./gradlew check --rerun-tasks --no-build-cache` with PITest — BUILD SUCCESSFUL 1m32s, `:app` 374
tests / 0 failures, thresholds held, 6 PostGIS starts, 0 shutdown warnings; root
`pnpm lint && pnpm test && pnpm typecheck` green (mobile 20 suites / 188 tests). Not pushed.

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
- **C2 mobile**: `propertyGuards` and `reviewGuards` narrow the regenerated nullable responses and
  ignore extra private fields. `useProperty` makes one GET with loading/loaded/notFound/error and
  manual retry. `useReviewFeed` appends 20-item cursor pages, stops without a cursor, prevents a
  second in-flight page, preserves loaded pages after a page failure, and drops superseded responses.
- **C2 UI**: `PropertyScreen` replaces the placeholder and keeps the deep-link-safe Back. It renders
  identity, aliases, address, translated type, DRAFT notice and 404 copy, with independent header and
  feed errors. `ReviewCard` renders relationship, years-only residence, checked-relationship badge,
  recommendation, P-019 category chips, pros/cons/body, helpful count, relative date and the content
  language tag. No author identifier, total or aggregate is rendered.
- **C2 localization/tests**: en/ru property and review copy, Russian plural forms and relative-date
  formatting are covered by node-only Jest tests. Targeted property/review suites pass 36 tests; the
  full local mobile preflight passes lint, typecheck and 187 tests across 20 suites.
- **C2 review fix round 1**: `ReviewCard` appends the "checked evidence … does not certify every
  statement" tooltip to its accessibility label only for `RELATIONSHIP_SIGNAL` and
  `DOCUMENT_VERIFIED`. `UNVERIFIED` and unknown tiers announce the badge text alone, so no check is
  implied. `ReviewCard.test.tsx` covers both checked tiers and both not-checked cases. The narrow
  ReviewCard suite (15 tests), ESLint on the changed files and `tsc --noEmit` pass locally.

## Remaining work

1. Android device checks for 023-B and 023-C (human; no adb/emulator on the lead's machine):
   open from search; header and feed; page to the end; airplane mode on page 2 keeps page 1 with
   Retry; DRAFT notice; 404 for a hidden property; review card at 200% text scaling; Russian copy.
2. Push and open one PR (founder decision).

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
- `apps/mobile/app/property/[id].tsx`
- `apps/mobile/src/api/propertyGuards.ts` (new)
- `apps/mobile/src/api/reviewGuards.ts` (new)
- `apps/mobile/src/api/__tests__/propertyGuards.test.ts` (new)
- `apps/mobile/src/api/__tests__/reviewGuards.test.ts` (new)
- `apps/mobile/src/components/ReviewCard.tsx` (new)
- `apps/mobile/src/components/__tests__/ReviewCard.test.tsx` (new)
- `apps/mobile/src/property/useProperty.ts` (new)
- `apps/mobile/src/property/__tests__/useProperty.test.tsx` (new)
- `apps/mobile/src/reviews/useReviewFeed.ts` (new)
- `apps/mobile/src/reviews/__tests__/useReviewFeed.test.tsx` (new)
- `apps/mobile/src/screens/PropertyScreen.tsx` (new)
- `apps/mobile/src/screens/PropertyPlaceholderScreen.tsx` (deleted)
- `apps/mobile/src/screens/__tests__/PropertyScreen.test.tsx` (new)
- `apps/mobile/src/screens/__tests__/PropertyPlaceholderScreen.test.tsx` (deleted)
- `apps/mobile/src/i18n/en.ts`
- `apps/mobile/src/i18n/ru.ts`
- `apps/mobile/src/i18n/format.ts`
- `apps/mobile/src/i18n/__tests__/format.test.ts`
- `apps/mobile/src/i18n/__tests__/plural.test.ts`
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
- Targeted mobile property/review Jest run (`useProperty`, `useReviewFeed`, `ReviewCard`,
  `PropertyScreen`): passed (4 suites, 36 tests).
- `pnpm --filter @geo-housing/mobile typecheck`: passed locally.
- `pnpm --filter @geo-housing/mobile lint`: passed locally.
- `pnpm --filter @geo-housing/mobile test`: passed locally (20 suites, 187 tests). These are local
  preflight results; the loop runner must rerun the three mobile L1 commands as the handoff gate.
- `git diff --check`: passed.

## Known failures

None. C1's L1 passed 5/5 in the runner. C2's local preflight is green.

## Risks and unresolved questions

- Moderation's appeal/report ownership checks must stay on internal ports; `*Moderation*` tests are
  in C1's L1.
- Admin DTO nullability (`AdminPropertyResponse`, `AdminReviewResponse`) is intentionally a
  follow-up; those schemas currently describe optional values as non-null.
- 023-B's Android device check was not recorded before #8 merged.
- 023-C2's Android device check is still pending: navigation from search, header/feed rendering,
  paging to the end, airplane mode on page 2, DRAFT/404 states, Russian copy and 200% text scaling.
- The loop runner, not the worker sandbox, must produce the authoritative mobile L1 result.

## Human actions required

The Android device checks for 023-B and 023-C need a device or emulator. They are not part of worker
implementation and remain for the lead/human.

## Recommended next action

Founder: push/PR decision and the device checks. Afterwards: plan 024 hardening (tolerate prose
around the worker JSON; stop on environment failures; post-review human checks are not
`human_action_required`), then plan 020 closure and the follow-ups listed in plan 023.

## Last updated

2026-11-15

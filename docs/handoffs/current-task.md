# Task handoff

## Objective

Plan 023-B — mobile discovery. An anonymous user searches for a building in the mobile app and gets
results they can tell apart by address and type. Delivered as 023-B1 (backend contract change C1:
address summary and `type` on search hits), 023-B2 (mobile search and results screens) and 023-B2r
(contract nullability plus the B2 review fixes).

## Active branch

`feat/023-b-mobile-discovery`, created from `main` at `71d8878` on 2026-11-15. Not pushed. Do not
commit, push, open a pull request or merge from this worker session.

## Related issue or plan

`docs/plans/023-mobile-discovery.md`, section "023-B — discovery" (engineering design, chunks
023-B1, 023-B2 and 023-B2r, `ai-l1` blocks). 023-A is merged (#2); 023-C is not started.

## Current status

in_progress — 023-B1 committed locally (`4253d1a`). 023-B2 and 023-B2r implemented, uncommitted:
runner L1 6/6 (`.ai/runs/implementation-loop.ZQb2ow/l1-3.json`) and fresh review `READY_TO_MERGE`
(`review-1.json`). Lead L2 green 2026-11-15: cold `./gradlew check --rerun-tasks --no-build-cache`
without `-PskipMutation` — BUILD SUCCESSFUL 1m23s, `:app` 368 tests / 0 failures, PITest thresholds
held, 6 PostGIS starts, 0 shutdown warnings; root `pnpm lint && pnpm test && pnpm typecheck` green
(mobile 15 suites / 113 tests). Android device check outstanding.

## Completed work

- Re-verified plan 023's "current system" facts against `71d8878` (search SQL already joins the
  address table; `PropertyResponse` uses `address: null`; blank `q` is a 400; the mobile app had no
  production client instance; generated schemas are git-ignored).
- Engineering design, decisions table and L1 blocks written into plan 023.
- 023-B1 backend C1: `PropertySearchProjection` selects `type` and the four address parts (plus
  `hasAddress`) from the existing address join; `PropertyMatch` requires `PropertyType` and exposes
  `Optional<AddressSummary>`; `PropertySearchHitResponse` carries `type` and a nullable
  `AddressSummaryView`; `originalText` and `country` are never selected or exposed.
- 023-B1 tests: `PropertyMatchTest` (required type, absent/present address),
  `PropertySearchIntegrationTest` (full, partial and absent address; type on every hit), two
  acceptance scenarios (same name told apart by address; resident-typed original text absent from
  search results), and `docs/api/openapi.json` plus decision log P-016.
- 023-B2 anonymous client: `ApiClientProvider`/`useApiClient` supplies one client built in
  `app/_layout.tsx` from `apiBaseUrl()` and `createExpoNetworkStatusProbe()`.
- 023-B2 guards and search: `isSearchResponse` validates the fields the UI reads (required string
  id/name/type, `address` null or an object with all four string-or-null parts). `useSearch` sends
  exactly one `GET /api/properties/search` with `{q, limit: 20}` per submitted query, sends nothing
  for a blank query, exposes idle/loading/loaded/empty/error, retries only on request and discards a
  response from a superseded query.
- 023-B2 screens: `HomeScreen` is the search entry with a disabled-while-blank submit and the locale
  switch (023-A diagnostics and catalogue keys removed); `ResultsScreen` renders loading, empty,
  offline/temporary-error, loaded and 20-result states; `ResultRow` renders name, formatted address
  and translated type (with unknown-type fallback), never `score`/`distanceMeters`, has a 44 pt
  target and an accessibility label ending "opens details", and navigates to `/property/[id]`;
  `PropertyPlaceholderScreen` is the localized temporary detail route with a back action.
- 023-B2 tests: guard accept/reject, address formatting (full/partial/null), `useSearch` (one
  request, blank sends none, retry, stale discard, every outcome), `ResultsScreen` (all states, 20
  notice, one search call and no detail call, score/distance absent), `HomeScreen` (submit, blank
  disabled, locale switch) and the placeholder.
- 023-B2r backend contract: `OpenApiConfiguration` adds an `OpenApiCustomizer` that marks
  `PropertySearchHitResponse.address` as `AddressSummaryView` or `null` and every
  `AddressSummaryView` part as `string` or `null`; a missing schema or property fails fast.
  `SearchHitNullabilityContractTest` pins both forms, a Cucumber scenario pins the JSON `null`
  address, and `docs/api/openapi.json` is regenerated.
- 023-B2r mobile fixes: `MobileApiClient.get` takes `T extends GetSuccessBody<P>` so a guard may
  only narrow the generated body (with a `@ts-expect-error` compile-time case); `SearchHit` takes
  `address` from the generated `AddressSummaryView | null` instead of a hand-written type; the
  placeholder uses `canGoBack() ? back() : replace("/")`, with a deep-link test; `intlSupport()` is
  documented as test-only after the 023-A diagnostics removal.

## Remaining work

- Runner-owned L1 for 023-B2r (six commands below), then a fresh independent review.
- Lead: Android device pass against the local API (search, addresses, tap to placeholder and back,
  airplane mode → "No connection" + Retry, Russian strings), then the cold backend `check` without
  `-PskipMutation` and the root `pnpm lint && pnpm test && pnpm typecheck`.
- 023-C has not started.

## Decisions made

See plan 023, "Engineering design" under 023-B: `address: null` without an address row; a dedicated
application-level `AddressSummary` so `originalText` cannot reach a hit; a small `useSearch` hook with
no new dependency; rows navigate to a temporary placeholder detail route until 023-C; Home becomes
the search entry and keeps the locale switch. B2r declares the nullability in `:app`'s
`OpenApiConfiguration` and narrows `MobileApiClient.get` to `T extends GetSuccessBody<P>` so a guard
can only refine the generated contract.

## Assumptions

- Additive B1 response fields do not break `apps/web` (verified by `pnpm --filter @geo-housing/admin-web
  typecheck` in B1's L1).
- The backend serializes all four address parts and uses `null` for absent values (Jackson's default
  inclusion). The mobile guard therefore treats a missing part key as malformed; revisit if a
  `NON_NULL` inclusion policy is introduced.

## Files changed

023-B2:

- `apps/mobile/app/_layout.tsx` (one client provider around the stack)
- `apps/mobile/app/search.tsx`, `apps/mobile/app/property/[id].tsx` (routes)
- `apps/mobile/src/api/ApiClientProvider.tsx`, `apps/mobile/src/api/client.ts`
- `apps/mobile/src/api/searchGuards.ts`, `apps/mobile/src/api/__tests__/searchGuards.test.ts`
- `apps/mobile/src/search/useSearch.ts`, `apps/mobile/src/search/formatAddressSummary.ts` and their
  tests
- `apps/mobile/src/components/ResultRow.tsx` and its test
- `apps/mobile/src/screens/HomeScreen.tsx`, `ResultsScreen.tsx`, `PropertyPlaceholderScreen.tsx` and
  their tests
- `apps/mobile/src/i18n/en.ts`, `apps/mobile/src/i18n/ru.ts`, `apps/mobile/src/i18n/__tests__/format.test.ts`
- `apps/mobile/README.md`, `apps/mobile/src/theme.ts` (stale smoke-screen description)

023-B2r:

- `apps/api/app/src/main/java/com/example/geohousing/app/config/OpenApiConfiguration.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/config/SearchHitNullabilityContractTest.java`
- `apps/api/app/src/test/java/com/example/geohousing/app/acceptance/CatalogueSteps.java`
- `apps/api/app/src/test/resources/features/find-property.feature`
- `docs/api/openapi.json`
- `apps/mobile/src/api/client.ts`, `apps/mobile/src/api/__tests__/client.test.ts`
- `apps/mobile/src/api/searchGuards.ts`
- `apps/mobile/src/screens/PropertyPlaceholderScreen.tsx` and its test
- `apps/mobile/src/i18n/intl.ts`

023-B1 files remain from the prior chunk (backend projection/repository/DTO, tests, OpenAPI, decision
log). This handoff also updates `docs/plans/023-mobile-discovery.md`.

## Commands and tests

Passed locally in this sandbox (2026-11-15):

- `pnpm --filter @geo-housing/mobile typecheck` — passed (regenerated schema, `tsc --noEmit` clean)
- `pnpm --filter @geo-housing/mobile lint` — passed
- `pnpm --filter @geo-housing/mobile test` — passed, 15 suites / 112 tests
- `git diff --check` — passed
- `javac` + a `/tmp` harness against the cached jars — passed: the empty-components path now
  throws `IllegalStateException` naming `PropertySearchHitResponse`, and the positive path yields
  the `AddressSummaryView | null` `anyOf` plus `["string", "null"]` on all four address parts

Runner-owned L1 for 023-B2r (skipped here: the sandbox has no network or Docker, so Gradle and
Testcontainers cannot run):

- `cd apps/api && ./gradlew :app:test --tests '*SearchHitNullabilityContractTest' --tests '*OpenApiContractIntegrationTest' --tests '*AcceptanceTest' --tests '*PropertySearchIntegrationTest' -PskipMutation --rerun-tasks`
- `cd apps/api && ./gradlew :app:spotlessCheck :app:checkstyleMain :app:checkstyleTest`
- `pnpm --filter @geo-housing/admin-web typecheck`
- `pnpm --filter @geo-housing/mobile lint`
- `pnpm --filter @geo-housing/mobile typecheck`
- `pnpm --filter @geo-housing/mobile test`

023-B1's Gradle/runner results from the prior handoff remain valid (properties tests, scoped
`:app:test`, Spotless/Checkstyle, admin-web typecheck with nvm on `PATH`).

## Known failures

None open in 023-B2/B2r. B2r L1 round 1 failed `SearchHitNullabilityContractTest` because the
empty-components path threw a message that did not name `PropertySearchHitResponse`; the customizer
now reports that schema name in both the null-schemas and missing-schema paths, and the test is
unchanged. The next runner L1 attempt (`.ai/runs/implementation-loop.ZQb2ow`) never executed tests:
compilation and `:app:spotlessCheck` passed, but the host JVM could not spawn the test executor
(`Failed to exec spawn helper`) or the Checkstyle worker (`Failed to start 'Gradle Worker Daemon'`).
A later diagnostic showed the same JDK spawning processes normally, so this is an environment
failure rather than a code failure; no source change was made and L1 needs a rerun. Backend L1 for
B2r has not run in this sandbox (no network or Docker); the runner owns those checks. B1's round-1
`pnpm: command not found` was the runner shell lacking nvm; resolved by loading nvm before launch.

## Risks and unresolved questions

- The Android device pass for 023-B2 has not run; airplane-mode copy, 200% text scaling and Russian
  screen copy need human evidence.
- `useSearch` relies on React's normal single effect invocation. React StrictMode's development-only
  double invocation would send the same request twice; StrictMode is not enabled in this app. The
  tests assert one request in the shipped behavior.
- `intlSupport()` remains exported from `src/i18n/intl.ts` as test-only evidence of 023-A's runtime;
  `nativePluralRules`/`nativeDateTimeFormat` remain the production helpers.

## Human actions required

Founder decision before 023-C: the public property detail response returns `address.originalText`
(free text that may hold an apartment number), and plan 023 maps it as a fallback on the detail
screen. Keep, or remove from the public response? Raised by the 023-B1 review.

## Recommended next action

Run the Android device check (search, rows with addresses, tap to the
placeholder and back, deep-link back, airplane mode → "No connection" + Retry, Russian copy); then
commit, push and open one PR. Launch the runner from a shell with nvm loaded, and run
`./gradlew --stop` after any system JDK upgrade.

## Last updated

2026-11-15

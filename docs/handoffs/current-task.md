# Task handoff

## Objective

Plan 023 chunk **023-B — discovery**: anonymous on-device search for a building or complex, from
the app to a ranked result list, plus the one small backend contract change (C1: an address summary
and the property type on a search hit) that makes two similarly-named buildings distinguishable.

## Active branch

`feat/023-b-discovery`, branched from `main` (`f51d666`). Nothing created, committed, pushed or
merged by this worker; all changes are uncommitted in the working tree.

## Related issue or plan

`docs/plans/023-mobile-discovery.md`, chunk **023-B** only. 023-C (property detail, review feed,
backend C2) is explicitly not started. No migration, no module boundary and no new public API were
added.

## Current status

ready_for_lead_review — 023-B scope is complete and committed/pushed as PR #4. PR #4 review raised
two further merge-blockers (no accessible Back from `/search`; OpenAPI declared the address fields
non-null although the backend sends null); both are fixed in the working tree (see "PR #4 review
follow-up"), uncommitted. Backend contract tests and frontend L1 pass.

## Completed work

- **Backend C1** (`apps/api/modules/properties`): the search hit now carries `type` and an address
  summary. The new `AddressSummaryView(city, district, street, building)` is nested in
  `PropertySearchHitResponse` (the same pattern as `PropertyResponse.AddressView`, so springdoc
  names it `AddressSummaryView`). Fields travel the existing seam
  `PropertySearchProjection` → `PropertyMatch` → `PropertySearchHitResponse`; the SQL adds columns
  to the existing `SELECT` and the existing `LEFT JOIN properties.address`, so there is **no new
  join, no second query and no per-hit fetch**. `PropertyAddressSummary` is a new application value
  object; `PropertyMatch` gained `type` and `addressSummary` (with an `Optional address()` helper,
  mirroring `distance()`). A property with no address row yields four null columns and maps to a
  null summary rather than a blank address.
- **OpenAPI**: `docs/api/openapi.json` adds the `AddressSummaryView` schema and the `address`/`type`
  properties on `PropertySearchHitResponse`, in the canonical (sorted, Jackson-pretty) form the
  contract test compares against. Verified canonical with the test's own `canonical()` logic.
- **Decision log**: `P-016` records the founder decision (address summary + type on public search
  hits; summary strictly less than the public detail endpoint).
- **Mobile** (`apps/mobile`): anonymous `ApiProvider` context + `createDefaultApiClient`; `search.ts`
  (guard + one bounded `get` on `/api/properties/search`, no `Authorization`, no location params);
  `src/properties/searchRows.ts` (name / one address line / translated type; `score` and
  `distanceMeters` are dropped, not rendered). Home screen is now the search entry with the existing
  language switch and always-on tagline; the smoke screen and its diagnostics keys are gone.
  `/search?q=` renders all four states (loading skeleton announced as "Searching", truthful empty,
  offline-vs-temporary error with manual retry, loaded) plus the "Showing the first 20" note when a
  full page comes back. Navigation lives in the route files (`app/index.tsx`, `app/search.tsx`).
- **Founder decision applied (2026-09-27)**: result rows are **not pressable** in 023-B.
  `SearchResultsScreen` takes no `onOpenProperty`; a row is a plain accessible `View` with no
  `button` role, no `onPress` and no `router.push`. Its label reads name, address and translated
  type and does not announce that it opens details. The `search.rowLabel` en/ru entries dropped the
  "opens the property" clause, and `app/search.tsx` no longer imports `useRouter` or pushes
  `/property/[id]`. 023-C restores the press and the "opens details" semantics.
- **Tests**: 6 new/updated backend unit tests (53 module tests total, up from 47) and 104 mobile
  tests across 12 suites, including one-request-per-query, no-score-in-the-tree, same-name
  distinguishable-by-address, and every screen state.

## Remaining work

- 023-C: property detail header, review feed, review card, and backend C2 (`AdminReviewResponse`
  split), with its own `DECISION_LOG` entry.
- Independent review of this 023-B branch.

## Decisions made

- **Founder/lead decision (2026-09-27): result rows are not pressable in 023-B.** This is
  deliberate temporary behaviour, not an omission, and is now **implemented on the branch**. The
  property-detail route `/property/[id]` is
  023-C scope, and a tap that lands on expo-router's unmatched route is a dead end. Therefore, in
  023-B:
  - a result row has no `onPress`, is not wrapped in a pressable, has no `button`/`link` role and does
    not call `router.push`; `onOpenProperty` and any navigation to `/property/[id]` are removed;
  - its accessibility label still reads the name, address and translated type, but does **not**
    announce that it opens details;
  - no temporary detail route, placeholder screen or "coming soon" UI is added.

  023-C makes the row pressable, navigates to `/property/[id]`, and adds the final accessibility
  semantics (role, and a label/hint announcing it opens property details) that the plan's
  Accessibility section requires. A reviewer should treat the missing "opens details" announcement
  in 023-B as intended by this decision, not as a defect.
- **The address summary is a new nested record, not the detail endpoint's `AddressView`.** It omits
  `originalText` (free text where an apartment number can hide) and `country` (constant), so the hit
  exposes strictly less than public detail already does.
- **An all-null address maps to a null summary.** The LEFT JOIN cannot distinguish "no address row"
  from "address row with every part blank"; both render the same, and "no address" must be honest.
- **Type and address are optional in the mobile guard**, so an older server without them degrades to
  "Type not recorded" rather than a `malformed` whole response.
- **The results loading state is derived from the request identity** (query + attempt), not set
  synchronously in the effect, because `react-hooks/set-state-in-effect` (correctly) forbids the
  latter; a retry therefore shows its own loading state without an extra render.
- **`reviews.count` plural stays**; only the smoke-screen diagnostic keys were removed.

## Assumptions

- Springdoc will name the nested `AddressSummaryView` by its simple name, as it already does for
  `PropertyResponse.AddressView` (spec component names are simple names today). The contract test
  will confirm this in CI.
- The committed spec's canonical form matches the running app; only the *content* is unverified
  locally (see Human actions).

## Files changed

- `apps/api/modules/properties/src/main/java/.../application/PropertyAddressSummary.java` (new)
- `apps/api/modules/properties/src/main/java/.../application/PropertyMatch.java`
- `apps/api/modules/properties/src/main/java/.../infrastructure/persistence/PropertySearchProjection.java`
- `apps/api/modules/properties/src/main/java/.../infrastructure/persistence/SpringDataPropertyRepository.java`
- `apps/api/modules/properties/src/main/java/.../infrastructure/persistence/JpaPropertyRepository.java`
- `apps/api/modules/properties/src/main/java/.../infrastructure/web/PropertySearchHitResponse.java`
- `apps/api/modules/properties/src/test/java/.../application/PropertyMatchTest.java`
- `apps/api/modules/properties/src/test/java/.../infrastructure/web/PropertySearchHitResponseTest.java` (new)
- `apps/api/app/src/test/java/.../properties/PropertySearchIntegrationTest.java`
- `docs/api/openapi.json`, `docs/DECISION_LOG.md`, `docs/plans/023-mobile-discovery.md`
- `apps/mobile/app/_layout.tsx`, `app/index.tsx`, `app/search.tsx` (new)
- `apps/mobile/src/api/ApiProvider.tsx` (new), `src/api/defaultClient.ts` (new), `src/api/search.ts` (new),
  `src/api/__tests__/search.test.ts` (new)
- `apps/mobile/src/properties/searchRows.ts` (new), `src/properties/__tests__/searchRows.test.ts` (new)
- `apps/mobile/src/screens/HomeScreen.tsx`, `src/screens/__tests__/HomeScreen.test.tsx`
- `apps/mobile/src/screens/SearchResultsScreen.tsx` (new), `src/screens/__tests__/SearchResultsScreen.test.tsx` (new)
- `apps/mobile/src/i18n/en.ts`, `src/i18n/ru.ts`, `src/i18n/__tests__/format.test.ts`

## Commands run

- `./scripts/check-scoped.sh governance` → passed (24 required files, 7 shared skills).
- `./scripts/check-scoped.sh frontend` → passed: `pnpm -r lint`, `pnpm -r test`, `pnpm -r typecheck`
  over `apps/web` and `apps/mobile`.
- `apps/mobile`: `pnpm run lint`, `pnpm run test` (104 passed), `pnpm run typecheck`.
- Worker takeover (2026-09-27), after applying the founder decision: `apps/mobile`
  `pnpm run lint`, `pnpm run test` (104 passed, 12 suites), `pnpm run typecheck`, and the root
  `./scripts/check-scoped.sh frontend` (`pnpm -r lint|test|typecheck` over `apps/web` and
  `apps/mobile`) all pass.
- Backend substitute (Gradle cannot start — see below): `javac` + JUnit Platform launcher over the
  `properties` module, 53 tests passed; `com.google.googlejavaformat.java.Main --dry-run
  --set-exit-if-changed` and `com.puppycrawl.tools.checkstyle.Main -c config/checkstyle/checkstyle.xml`
  over the nine changed Java files, both clean.

## Tests and verification

| Command | Result | Notes |
| --- | --- | --- |
| `cd apps/mobile && pnpm run lint` (review fix, lead, 2026-09-27) | passed | |
| `cd apps/mobile && pnpm run typecheck` (review fix, lead) | passed | |
| `cd apps/mobile && pnpm run test` (review fix, lead) | passed | 12 suites, 109 tests (was 104) |
| `./scripts/check-scoped.sh frontend` (review fix, lead) | passed | web 10 files; mobile 12 suites / 109 tests; lint + typecheck |
| `./scripts/check-scoped.sh governance` (review fix, lead) | failed locally on ignored files only | the validator `rglob`s every `*.json`, including gitignored `.ai/` run evidence; it rejected `.ai/review-result.json.raw.json` and `.ai/runs/implementation-loop.D2dZo6/worker-0.json` (the known malformed automation outputs). The same validator over a copy of `git ls-files -co --exclude-standard` passed: 24 required files, 7 shared skills. Evidence files were kept, not deleted. |
| `./scripts/check-scoped.sh governance` | passed | |
| `./scripts/check-scoped.sh frontend` | passed | lint + test + typecheck, web and mobile |
| `./scripts/check-scoped.sh frontend` | **passed (worker, 2026-09-27)** | rerun after the non-pressable-rows change; `pnpm -r lint|test|typecheck`, mobile 104/104 |
| `javac` all `properties` main+test sources, then JUnit Platform over the module | passed | 53/53 tests; substitutes the un-runnable Gradle module check |
| `javac` `PropertySearchIntegrationTest` against the new signatures | passed | compile check only; the test itself needs Docker |
| google-java-format 1.28.0 `--dry-run --set-exit-if-changed` on changed Java | passed | Spotless' default version |
| checkstyle 10.24.0 with `config/checkstyle/checkstyle.xml` on changed Java | passed | |
| `cd apps/api && ./gradlew :modules:properties:check -PskipMutation` | **passed (lead, 2026-09-27)** | run outside the worker sandbox: compile, tests, spotless, checkstyle |
| `cd apps/api && ./gradlew :app:test --tests '*PropertySearchIntegrationTest' --tests '*OpenApiContractIntegrationTest' -PskipMutation` | **passed (lead, 2026-09-27)** | run outside the worker sandbox against Testcontainers PostGIS: search 16/16, contract 1/1, 0 skipped; run without `-DupdateOpenApiSpec`, so the committed `docs/api/openapi.json` matches the generated contract and was not regenerated |

The worker sandbox still cannot start Gradle or reach Docker. The two backend rows above are the
L1 evidence for the current backend diff; a worker should not raise `HUMAN_ACTION_REQUIRED` for them
again unless it changes backend code, in which case the lead reruns them.

## Review fix (2026-09-27, lead)

Finding (high, `blocks_merge: true`, evidence `.ai/023-b-review.blocking.json`): Spring serializes
absent values as JSON `null` (no `spring.jackson` null exclusion, no `@JsonInclude`), so a hit with no
address arrives as `"address": null` and a missing part as `"district": null`. `isPropertySearchResponse`
accepted only a *missing* key, so any result set containing a partial or absent address became
`malformed` and the screen showed the temporary-availability error. The existing tests built partial
addresses by omitting keys, so they passed.

The DeepSeek fix worker could not run: its bubblewrap sandbox failed before executing any shell
command (evidence `.ai/023-b-worker-fix-1.json`). Sandbox/kernel/system settings were not changed.
The lead applied only the blocker fix:

- `apps/mobile/src/api/search.ts`: the guard accepts `null` as well as a missing key for `address`
  and for `city`/`district`/`street`/`building`, matching what the backend DTO can produce.
  `type` stays non-nullable — `PropertySearchHitResponse.from` always sets it from the enum and the
  contract does not mark it nullable — so a `null` type is still `malformed`. `distanceMeters` is not
  read by any row, so the guard does not check it; a `null` there is accepted (regression-tested).
- `apps/mobile/src/properties/searchRows.ts`: `AddressSummary` is the wire shape (each part
  `string | null`, optional), and `formatAddressSummary`/`trimmed` accept `null`, treating it as
  missing. Runtime mapping behaviour was already null-safe; the types now say so.
- Regression tests use real `JSON.parse`d bodies: `search.test.ts` (null address, null parts, all-null
  parts, null `distanceMeters` accepted; null `type` rejected), `searchRows.test.ts` (null parts and
  null address format as missing; the null-bearing response maps to rows), and
  `SearchResultsScreen.test.tsx` (the null-bearing body goes through the **real** client and guard
  via an injected `fetch` — the stubbed-`get` tests never ran the guard — and renders rows, including
  "Address not recorded", with no error state).
- The regressions were confirmed to fail against the old guard (2 failed: the guard test and the
  screen test; the null-`type` test passed) before the fix was restored.

No backend code was touched, so backend tests were not rerun.

## PR #4 review follow-up (2026-09-27, lead)

1. **Search navigation.** The root stack hides headers, so `/search` had no explicit accessible way
   back. `app/search.tsx` now turns the native stack header on for that route only
   (`headerShown: true`, localized title "Search"/"Поиск" and back title "Back"/"Назад"); home keeps
   its header hidden. `app/_layout.tsx` sets `unstable_settings.initialRouteName = "index"`, so a deep
   link straight to `/search` still has home beneath it to go back to. `SearchResultsScreen` no
   longer pads the top safe-area edge, which the native header now owns. No tabs, no redesign.
   Test: `src/navigation/__tests__/searchNavigation.test.tsx` renders the real route files with
   `expo-router/testing-library` and asserts home's header is hidden, `/search`'s header is shown
   with its back button, and `router.back()` from a deep-linked `/search` lands on `/`. Both tests
   were confirmed to fail against the previous routes.
2. **OpenAPI nullability.** `PropertySearchHitResponse.address` and the four `AddressSummaryView`
   parts carry `@Schema(nullable = true)` (the repository rule in `.claude/rules/backend-java.md`).
   The properties module compiles against `swagger-annotations-jakarta` 2.2.47 as **compileOnly**
   (new version-catalog entry `swagger-annotations`); springdoc already ships that exact version at
   runtime, so nothing new reaches the app's runtime classpath. swagger-core renders `nullable` on a
   `$ref` property as `{"$ref": ..., "type": "null"}`, which under OpenAPI 3.1 admits only `null`;
   a new `OpenApiCustomizer` (`OpenApiConfiguration.nullableReferences`) rewrites exactly that shape
   to `oneOf: [{"$ref": ...}, {"type": "null"}]` and leaves every other property alone (unit-tested
   in `OpenApiConfigurationTest`). The regenerated spec diff is confined to those five properties:
   the parts become `["string", "null"]` and `address` becomes `oneOf [AddressSummaryView, null]`.
   The regenerated mobile types now read `address?: AddressSummaryView | null` and
   `city?: string | null` etc., so the handwritten nullable `AddressSummary` wire type was removed;
   `searchRows.ts` uses the generated type again. The runtime guard and all null-shape regression
   tests remain. `distanceMeters` (already nullable in the DTO, pre-existing in the contract) was
   deliberately left unchanged as out of scope.

Commands (lead, 2026-09-27):

| Command | Result |
| --- | --- |
| `cd apps/api && ./gradlew :app:test --tests '*OpenApiContractIntegrationTest' -DupdateOpenApiSpec=true -PskipMutation` | regenerated `docs/api/openapi.json` |
| `cd apps/api && ./gradlew :modules:properties:check :app:spotlessCheck :app:checkstyleMain :app:checkstyleTest :app:test --tests '*OpenApiContractIntegrationTest' --tests '*OpenApiConfigurationTest' --tests '*PropertySearchIntegrationTest' --tests '*ModuleBoundaryArchitectureTest' --tests '*SwaggerEndpointIntegrationTest' -PskipMutation` | passed: contract 1/1 (no update flag), customizer 3/3, search 16/16, ArchUnit 4/4, Swagger 4/4 |
| `cd apps/mobile && pnpm run lint` / `typecheck` / `test` | passed; 0 warnings; 13 suites, 111 tests |
| `./scripts/check-scoped.sh frontend` | passed (web 10 files, mobile 111 tests) |
| governance validator over `git ls-files -co --exclude-standard` | passed (24 required files, 7 shared skills); the in-place run still trips on the known malformed ignored `.ai/` files |

## Known failures

- None.

## Risks and unresolved questions

- **`/property/[id]` seam resolved** by the founder decision above: rows are non-pressable in 023-B.
- **No device run.** The results screen was exercised in Jest (node) only; the plan's acceptance has
  no device E2E in this slice, but the 200% text-scaling/contrast manual check the plan lists for the
  review card belongs to 023-C.
- **Mutation score (ADR-0009, threshold 77) is unmeasured** — pitest is an L2 CI concern and the
  Gradle run is unavailable here.

## Human actions required

- Resolved 2026-09-27: both backend Gradle commands were run by the lead outside the sandbox and
  passed (see "Tests and verification").
- Still for the human: attach the results-screen screenshots a UI pull request needs, once the app is
  run on a device.

## Recommended next action

Human: commit and push the PR #4 follow-up, then re-review the two fixed findings on the PR and
decide on merge. 023-C is not started. Separately (not 023-B scope): `scripts/ai` rejects DeepSeek results
wrapped in a code fence, and `validate_repo_governance.py` scans gitignored `.ai/` files.

## Last updated

2026-09-27

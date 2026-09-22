# Task handoff

## Objective

Plan 023-A — the mobile foundation: a small, working Expo + React Native application under
`apps/mobile` that boots a smoke screen, navigates with an expo-router stack, ships English and
Russian, consumes generated OpenAPI types, carries an anonymous public API client with the accepted
error model, and participates truthfully in the existing frontend CI gate.

## Active branch

`feat/023-a-mobile-foundation`, branched from `docs/023-mobile-discovery-plan` at `e09aabd`, which is
`main` at `5833ea6` plus the two plan-023 documentation commits. Nothing has been pushed.

## Related issue or plan

`docs/plans/023-mobile-discovery.md`, chunk **023-A** only. 023-B (discovery) and 023-C (property
experience) are explicitly not started, and no backend production code, migration or contract file
was touched.

## Current status

ready_for_review

## Completed work

- **Workspace.** `apps/mobile` is a workspace member of `pnpm-workspace.yaml`, so the root
  `pnpm -r lint|test|typecheck` scripts pick it up without changing a single root script. No new
  `allowBuilds` entry was required: `pnpm ignored-builds` reports nothing ignored and nothing
  automatically skipped.
- **App.** Expo SDK 57 (`expo@57.0.24`, `react-native@0.86.3`, `react@19.2.3`,
  `expo-router@57.0.22`), TypeScript strict, expo-router file-based **stack** — `app/_layout.tsx`
  (stack + locale provider + crash boundary) and `app/index.tsx`. No tabs, no placeholder screens.
- **Generated types.** `pnpm generate:api` copies the `apps/web` pattern
  (`openapi-typescript ../../docs/api/openapi.json -o src/api/generated/schema.d.ts`) and runs ahead
  of both `typecheck` and `test`, so a stale client cannot pass. `src/api/generated/` is gitignored
  exactly as `apps/web`'s copy is.
- **API client** (`src/api/client.ts`), over `openapi-fetch`: base URL from
  `EXPO_PUBLIC_API_BASE_URL` (default `http://localhost:8080`), 10 s deadline, typed outcomes
  `ok | notFound | offline | timeout | server | malformed | unknown`, runtime guards over the fields
  a caller actually reads, no retry loop, and **no `Authorization` header anywhere** — asserted by a
  test. No token, session or login abstraction was added.
- **Localization.** Typed `en`/`ru` catalogues with `en` as the source of truth (a missing Russian
  key is a compile error), `Intl.PluralRules` with a local en/ru fallback for the 1/2/5 forms,
  Russian date formatting with a local fallback, device locale as the initial choice, and a manual
  override persisted on the device through AsyncStorage.
- **Smoke screen** (`src/screens/HomeScreen.tsx`): app name, tagline, a two-button language switch
  with accessibility roles/labels/selected state and ≥44 pt targets, and a "Localization check" card
  that shows the active locale, the 1/2/5 plural forms, a formatted date and whether the runtime
  provided native `Intl` or the local fallback. That card is the evidence the plan's device checks
  ask for; it is removed when 023-B replaces this screen with the search entry.
- **CI.** `frontend-check.yml` gained `apps/mobile/**` in the push `paths` and in the relevance
  regex — two lines, no new workflow, no caching work, backend CI untouched.
- **Docs.** `ARCHITECTURE.md` now names `apps/mobile` next to the Expo line it already mandated;
  `apps/mobile/README.md` documents the build/lint/test commands and the rules that are easy to
  break; plan 023 gained a progress-log entry.
- **Device verification completed on a real Android device** (2026-09-22, human-run, screenshots of
  both language states): the app launches, the English UI renders, the locale switch changes a
  visible string, `1 отзыв · 2 отзыва · 5 отзывов` renders correctly, a Russian date renders as
  `1 сентября 2026 г.`, and no red screen or runtime error appeared. The runtime reported **local
  fallback** for `Intl`.

## Remaining work

- 023-B and 023-C, which are separate pull requests and were deliberately not started.

## Decisions made

- **Expo SDK 57**, the current stable SDK, rather than the newest canary. Every dependency matches
  the SDK's own `bundledNativeModules` pins, verified with `expo install --check` (one deviation,
  below).
- **`react-dom@19.2.3` is a direct dependency** even though this slice has no web target: expo-router
  lists it as a peer, and without the pin pnpm auto-installed `react-dom@19.2.8` against
  `react@19.2.3` and reported an unmet peer. Pinning it to the SDK's own version removes the warning
  without changing `apps/web`'s resolution.
- **Metro keeps hierarchical lookup enabled.** Expo's monorepo guide recommends
  `disableHierarchicalLookup = true`, but under pnpm's isolated layout that makes
  `expo-router/entry` unable to resolve `@expo/metro-runtime`, and `expo export` fails. The reasoning
  is recorded in `metro.config.cjs`; `watchFolders` and `nodeModulesPaths` still cover the workspace.
- **The client asks openapi-fetch for the body as text and parses JSON itself**, because
  openapi-fetch's own JSON parsing throws on a truncated or HTML body, which would classify a 200 as
  `unknown` instead of the `malformed` the plan requires.
- **One documented cast** forwards openapi-fetch's per-path generics through our generic `path`
  parameter. The body stays `unknown` until the caller's guard narrows it, and the public signature
  is derived from `paths` in the generated schema.
- **TypeScript stays on `^5.9.2`**, matching `apps/web`, although `expo install --check` expects
  `~6.0.3` for SDK 57. See "Risks".
- **Tests live under `src/**/__tests__/` and never under `app/`**, because expo-router treats every
  file in `app/` as a route.
- **No runtime validation framework was added.** Malformed detection uses `src/api/guards.ts`
  (object/string/number checks) over the few fields a caller reads, which is what the plan's
  "smallest reasonable runtime guards" rule asks for and avoids a `LEAD_DECISION_REQUIRED` escalation.

## Assumptions

- The plan's error-model table is authoritative for `4xx` other than 404: they are `server`, not a
  separate category.
- "Device reports no network" means `expo-network`'s `isConnected === false` or
  `isInternetReachable === false`; an unreadable state is `unknown`, never `offline`.
- The public endpoints in `docs/api/openapi.json` answering under a wildcard media type are the
  contract as committed, not a spec defect to fix in this chunk.

## Files changed

Created: `apps/mobile/package.json`, `app.json`, `babel.config.cjs`, `metro.config.cjs`,
`jest.config.cjs`, `jest.setup.cjs`, `eslint.config.mjs`, `tsconfig.json`, `expo-env.d.ts`,
`README.md`, `app/_layout.tsx`, `app/index.tsx`, `src/config.ts`, `src/theme.ts`,
`src/screens/HomeScreen.tsx`, `src/api/{client,guards,network}.ts`,
`src/api/__tests__/{client,network}.test.ts`,
`src/i18n/{locale,en,ru,catalogues,intl,plural,format,localeStore,deviceLocale}.ts`,
`src/i18n/LocaleProvider.tsx`, `src/i18n/__tests__/{locale,catalogues,plural,format,localeStore,LocaleProvider}.test.*`,
`src/i18n/__tests__/intlStub.ts`, `src/screens/__tests__/HomeScreen.test.tsx`.

Modified: `pnpm-workspace.yaml`, `pnpm-lock.yaml`, `.gitignore`, `.github/workflows/frontend-check.yml`,
`docs/ARCHITECTURE.md`, `docs/plans/023-mobile-discovery.md`, `docs/handoffs/current-task.md`;
`docs/handoffs/current-task.md` (022's record) was renamed to `docs/handoffs/022-branch-protection.md`.

## Commands run

```bash
CI=true corepack pnpm install --no-frozen-lockfile     # added 1089 packages; no build scripts ignored
CI=true corepack pnpm install --frozen-lockfile        # "Already up to date" — CI will install cleanly
CI=true corepack pnpm peers check                      # two upstream Expo/RN peer warnings, below
cd apps/mobile && corepack pnpm generate:api           # openapi-typescript → src/api/generated/schema.d.ts
cd apps/mobile && corepack pnpm exec tsc --noEmit
cd apps/mobile && corepack pnpm exec jest
cd apps/mobile && corepack pnpm exec eslint .
cd apps/mobile && corepack pnpm exec expo export --platform android --output-dir /tmp/expo-export-check
cd apps/mobile && corepack pnpm exec expo export --platform ios --output-dir /tmp/expo-export-check-ios
cd apps/mobile && corepack pnpm exec expo install --check
./scripts/check-scoped.sh frontend                     # L1: pnpm -r lint / test / typecheck
```

## Tests and verification

| Check | Result |
| --- | --- |
| `apps/mobile` Jest (9 suites, 67 tests) | pass, ~1 s |
| `apps/web` Vitest (unchanged, via the root gate) | pass, 75 tests |
| `pnpm -r lint` / `test` / `typecheck` | pass; both commands report "Scope: 2 of 3 workspace projects" and run `apps/mobile` |
| `expo export --platform android` | 1269 modules bundled to Hermes bytecode |
| `expo export --platform ios` | bundled |
| `expo install --check` | only `typescript@5.9.3` vs expected `~6.0.3` |
| `pnpm install --frozen-lockfile` | up to date |
| Android device, manual (human, 2026-09-22) | pass — launch, English render, locale switch, Russian plurals, Russian date, no red screen; `Intl` reported as local fallback |

Test coverage: locale switching and persistence, device-locale fallback, typed dictionary
completeness and placeholder parity, Russian 1/2/5 plurals (and agreement between the fallback and
`Intl.PluralRules` for 0–200 plus fractions), date formatting with and without `Intl`, every branch
of the error union (`ok`, `notFound`, `server`, `malformed` for non-JSON / wrong-shape / empty
bodies, `timeout`, `offline`, `unknown`), timeout-is-never-offline, path-parameter interpolation, a
missing `Authorization` header, and a smoke render that asserts the visible string changes with the
locale.

## Known failures

`pnpm peers check` reports two unmet peers that come from Expo's own dependency graph, not from this
app: `react-native-worklets@0.13.0` (pulled by `@expo/ui`) against `expo-modules-core`'s
`^0.7.4 || … || ^0.10.0`, and `@react-native/metro-config@0.87.1` against
`@react-native/community-cli-plugin@0.86.3`'s `0.86.3`. Adding `react-native-worklets`/`reanimated`
to this app would silence the first only by installing native runtimes this slice does not use.
Both are recorded rather than hidden.

## Risks and unresolved questions

- **`Intl` on the device is resolved, and it took the fallback path.** The Android runtime reported
  local fallback, so `src/i18n/plural.ts` and `src/i18n/format.ts` are the production path on
  Android, not a rare one. Russian plurals and dates were confirmed correct there, so this is a
  verified outcome rather than an open risk; what stays open is *which* capability is missing, since
  the card reports the two together. Splitting the diagnostics per capability is a small 023-B
  follow-up, not a 023-A defect.
- **TypeScript version.** SDK 57 expects `~6.0.3`; this branch keeps the repository's single TS line
  (`^5.9.2`) so the two frontends do not run different compilers. Typecheck is clean and the bundle
  builds; moving both workspaces to TS 6 is a separate decision for the lead.
- **The app has not been launched on a simulator or device here.** `expo export` proves the bundle
  and the module graph, not that the native app renders.
- **`pnpm-lock.yaml` reshuffles `apps/web`'s peer suffixes** (`supports-color` 7.2.0 → 10.2.2, and
  vitest now listing jsdom/lightningcss/terser peers) without changing any resolved version. Web's
  lint, tests and typecheck still pass; the cause is the shared store, not a dependency change.

## Human actions required

None. The three device checks were completed on 2026-09-22 by a human on a real Android device
(screenshots of the English and Russian states captured); the handoff's earlier
`HUMAN_ACTION_REQUIRED` block is therefore satisfied and removed. Two things remain for the human:
attach those screenshots to the pull request, as `CONTRIBUTING.md` requires for UI changes, and
decide the TypeScript 5.9-vs-6.0 question recorded under "Risks". No emulator, Android Studio, Xcode
component or system package was installed, per AGENTS.md §4.

## Recommended next action

Lead review of `git diff main...feat/023-a-mobile-foundation`; the device evidence is in hand, so
nothing blocks that review. 023-B (backend address summary on search hits, then the search and
results screens) starts on its own branch afterwards, with the per-capability Intl diagnostics and
the TypeScript question as its first small decisions.

## Last updated

2026-09-22

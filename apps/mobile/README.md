# Geo Housing Reviews — mobile app

The public, anonymous mobile client: React Native with Expo and `expo-router`, in TypeScript.

This is the 023-A foundation only. It boots a smoke screen that proves routing, localization and
styling work on a device, and it carries the pieces the next chunks build on: the generated API
contract, an anonymous API client, and the English/Russian catalogues. Search, results, property
detail and the review feed belong to 023-B and 023-C.

## Commands

Run these from this directory, or through the workspace scripts from the repository root.

```bash
pnpm generate:api                 # writes src/api/generated/schema.d.ts from docs/api/openapi.json
pnpm start                        # Expo dev server (press "i" or "a", or scan the QR code)
pnpm ios / pnpm android           # Expo dev server, opening a simulator directly
pnpm lint                         # ESLint (flat config, eslint-config-expo)
pnpm typecheck                    # generate:api, then tsc --noEmit
pnpm test                         # generate:api, then Jest
```

`typecheck` and `test` regenerate the API types first, so a client that has fallen behind
`docs/api/openapi.json` fails the build instead of failing on a device. `src/api/generated/` is
generated output and is not committed.

There is no end-to-end suite: plan 023 deliberately excludes device E2E infrastructure. Device
checks are manual and recorded in the chunk's pull request.

This app targets iOS and Android. The web target is deliberately not configured: `react-native-web`
is not a dependency, so pressing `w` in the dev server fails to bundle. That is the intended state,
not a broken build.

### Dependencies that exist for expo-router

`expo-constants`, `expo-linking`, `react-native-screens` and `react-dom` are not imported by any file
under `app/` or `src/`; they are expo-router's required peers, declared directly because pnpm's
isolated `node_modules` does not hoist them (the same reason `metro.config.cjs` points Metro at the
workspace root). `react-dom` is pinned to the SDK's own version so pnpm's peer resolution stays
consistent — it is a peer of expo-router, not a statement that the app runs on the web. Pruning them
breaks the bundle.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `EXPO_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Base URL of the public API |

Metro inlines `EXPO_PUBLIC_*` at build time; see `src/config.ts`.

## Layout

```text
app/                     expo-router routes — files here are destinations, never tests
  _layout.tsx            stack + locale provider + crash boundary
  index.tsx              home route, re-exporting src/screens/HomeScreen
src/
  api/                   anonymous API client, runtime guards, generated schema
  i18n/                  en/ru catalogues, plural rules, date formatting, locale provider
  screens/               screen components, with their tests alongside
  expo-types.d.ts        typed Metro globals; replaces the CLI's generated expo-env.d.ts
```

Tests live under `src/**/__tests__/` and run in Node only: mobile joins the shared `pnpm -r` gate, so
a slow or flaky React Native test setup would slow `apps/web` pull requests too.

## Rules that are easy to break

- **`src/expo-types.d.ts` is ours; `expo-env.d.ts` is not.** The Expo CLI writes `expo-env.d.ts` when
  the dev server starts and deletes it again when typed routes are disabled — which is this app's
  configuration — taking the matching `tsconfig.json` include entries with it. That is why the file
  is gitignored and why `include` lists only `**/*.ts` and `**/*.tsx`. The reference that types
  Metro's globals lives in `src/expo-types.d.ts`, so `process.env.EXPO_PUBLIC_API_BASE_URL` is
  `string | undefined` in CI instead of `any`.
- **No `Authorization` header, ever.** There is no login, token or session in this app, and
  `src/api/__tests__/client.test.ts` asserts the header is absent. Authentication is a future plan.
  Do not add a token abstraction in anticipation of it.
- **No hand-written request or response types** (AGENTS.md §3.7). Types come from
  `pnpm generate:api`; a response body that does not match its generated type is `malformed`, and
  `src/api/client.ts` explains how that is decided without a validation framework.
- **`offline` is a claim about the device**, not about a failed request. It comes from
  `expo-network`, and a request that hit its 10-second deadline is `timeout` even then.
- **Every message is a whole phrase with named placeholders** in `src/i18n/en.ts` (the source of
  truth) and `src/i18n/ru.ts`. A missing Russian key is a compile error.
- **Nothing under `app/` may be a non-route file.** Screen components live in `src/screens/`.

## Manual verification on a device

These three checks cannot be performed by the test suite and are recorded as evidence in the chunk's
pull request:

1. the app launches and shows the smoke screen;
2. switching the language button changes the visible tagline;
3. the "Localization check" card shows `1 отзыв · 2 отзыва · 5 отзывов` and a Russian date, and
   reports whether the runtime provided native `Intl` or the local fallback.

Verified on a real Android device on 2026-09-22: all three passed, with screenshots of the English
and Russian states. The card reported **local fallback**, so that Hermes build ships without at least
one of `Intl.PluralRules` and `Intl.DateTimeFormat`. The fallback is the production path on Android
rather than a theoretical one, and `1 отзыв · 2 отзыва · 5 отзывов` and `1 сентября 2026 г.` were
confirmed correct there; `src/i18n/plural.ts` and `src/i18n/format.ts` must keep working without
`Intl`.

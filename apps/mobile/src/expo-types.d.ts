/// <reference types="expo/types" />

// Metro's globals — `process.env.EXPO_PUBLIC_*`, `require.context`, `module` — come from
// `expo/types`.
//
// The Expo CLI writes a `expo-env.d.ts` at the project root to pull them in, and this file replaces
// it rather than committing that one. The CLI's own template for that file says it should be in
// `.gitignore`, and it means it: starting the dev server with typed routes disabled — this app's
// configuration — deletes `expo-env.d.ts` *and* removes the matching `tsconfig.json` include
// entries. That is exactly what happened during the 023-A device run, which left `process.env`
// untyped. A file we own is never removed by a dev server, so CI and a laptop typecheck identically.
//
// It is not decorative: without the reference `process.env.EXPO_PUBLIC_API_BASE_URL` is `any` and
// nothing about that expression is checked; with it the value is `string | undefined`.

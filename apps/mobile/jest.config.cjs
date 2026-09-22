/**
 * Node-side tests only.
 *
 * Plan 023 keeps mobile's tests fast and node-only on purpose: mobile joins the shared `pnpm -r`
 * gate the moment it is a workspace member, so a slow or flaky React Native test setup would slow
 * every `apps/web` pull request down as well. Device end-to-end testing is a follow-up.
 *
 * Test files live under `src/`, never under `app/`: expo-router treats everything in `app/` as a
 * route.
 */
module.exports = {
  preset: "jest-expo",
  setupFiles: ["<rootDir>/jest.setup.cjs"],
  testMatch: ["<rootDir>/src/**/__tests__/**/*.test.ts?(x)"],
};

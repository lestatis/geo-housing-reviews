import { defineConfig } from "@playwright/test";

/**
 * Drives the admin app against a running stack: Postgres and the local OIDC provider from
 * infra/docker/docker-compose.yml, plus the API on 8080. See apps/web/README.md.
 *
 * <p>The web server is started here so a test run cannot pass against a stale build, but the API
 * and the containers are not — starting a Gradle application and Docker from a test runner hides
 * failures that belong in the terminal.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  reporter: process.env.CI ? "list" : "html",
  use: {
    baseURL: process.env.APP_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "pnpm dev",
    url: process.env.APP_BASE_URL ?? "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 120_000,
  },
});

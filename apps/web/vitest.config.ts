import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Playwright owns e2e/; vitest's default glob would otherwise try to run those specs.
    exclude: ["e2e/**", "node_modules/**", ".next/**"],
  },
  resolve: {
    alias: { "@": new URL(".", import.meta.url).pathname },
  },
});

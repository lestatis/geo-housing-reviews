// The package's directory import is not resolvable from an ESM config, so the file is addressed
// directly; it is the same flat config.
import expoConfig from "eslint-config-expo/flat.js";

export default [
  {
    // `src/api/generated` is written by `pnpm generate:api` from docs/api/openapi.json and must
    // never be hand-edited, so linting it would only produce noise about generated code.
    ignores: [".expo/**", "coverage/**", "node_modules/**", "src/api/generated/**"],
  },
  ...expoConfig,
  {
    files: ["**/*.cjs"],
    languageOptions: {
      sourceType: "commonjs",
      globals: {
        __dirname: "readonly",
        jest: "readonly",
        module: "writable",
        require: "readonly",
      },
    },
  },
];

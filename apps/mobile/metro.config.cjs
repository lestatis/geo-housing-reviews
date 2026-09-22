const path = require("path");

const { getDefaultConfig } = require("expo/metro-config");

// A pnpm workspace installs dependencies into a virtual store at the repository root, so Metro has
// to watch the workspace and look there as well as in the app's own node_modules.
//
// Hierarchical lookup stays enabled (Metro's default): pnpm links a package's own dependencies
// inside its virtual-store entry rather than flat into the app's node_modules, and expo-router's
// entry file resolves `@expo/metro-runtime` from exactly there. Disabling it, as Expo's monorepo
// guide does for hoisting package managers, makes `expo export` fail to resolve that import.
const projectRoot = __dirname;
const workspaceRoot = path.resolve(projectRoot, "../..");

const config = getDefaultConfig(projectRoot);

config.watchFolders = [workspaceRoot];
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, "node_modules"),
  path.resolve(workspaceRoot, "node_modules"),
];

module.exports = config;

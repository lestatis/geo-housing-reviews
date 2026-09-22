/** babel-preset-expo handles TypeScript, JSX and the expo-router plugin. Nothing else is layered on. */
module.exports = function babelConfig(api) {
  api.cache(true);
  return { presets: ["babel-preset-expo"] };
};

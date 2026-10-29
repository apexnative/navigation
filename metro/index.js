// @ts-check
'use strict';

// The Metro setup of the navigation, for the app's metro.config.js:
//
//   const { withApexNavigation } = require('@apexnative/navigation/metro');
//   module.exports = withApexNavigation(mergeConfig(getDefaultConfig(__dirname), config));
//
// It puts the navigation's Babel transformer in front of the app's own (React Native's, or the one the app set), so an
// edit in development updates the app by Fast Refresh instead of reloading it.

const path = require('node:path');

/** The variable that tells the navigation's transformer which transformer it goes in front of. */
const UPSTREAM = 'APEX_NAVIGATION_BABEL_TRANSFORMER';

/**
 * The app's Metro config with the navigation's Babel transformer in front of the one it had. Metro starts its
 * transform workers after it read the config, so they find the transformer the app had through the environment.
 * @template {{ projectRoot?: string, transformer?: { babelTransformerPath?: string } }} Config
 * @param {Config} config
 * @returns {Config}
 */
function withApexNavigation(config) {
  const transformer = config.transformer ?? {};
  const own = require.resolve('./babelTransformer');
  if (transformer.babelTransformerPath === own) return config;
  process.env[UPSTREAM] =
    transformer.babelTransformerPath ??
    require.resolve('@react-native/metro-babel-transformer', {
      paths: [config.projectRoot ?? process.cwd(), path.join(__dirname, '..')],
    });
  return { ...config, transformer: { ...transformer, babelTransformerPath: own } };
}

module.exports = { withApexNavigation, UPSTREAM };

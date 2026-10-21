const path = require('path');
const { getDefaultConfig } = require('@react-native/metro-config');
const { withMetroConfig } = require('react-native-monorepo-config');

const root = path.resolve(__dirname, '..');

/**
 * Metro configuration
 * https://facebook.github.io/metro/docs/configuration
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = withMetroConfig(getDefaultConfig(__dirname), {
  root,
  dirname: __dirname,
  conditions: ['apexnative-navigation-source'],
});

// The clips of the docs (`index.clips.js`): a Metro started with APEX_CLIPS=1, on a port of its own, serves them in place
// of the Showcase to the clip simulators.
if (process.env.APEX_CLIPS) {
  config.server = {
    ...config.server,
    rewriteRequestUrl: (url) => url.replace(/\/index\.(bundle|map)/, '/index.clips.$1'),
  };
}

module.exports = config;

// @ts-check
'use strict';

// The navigation's Babel transformer: the app's own transformer with two changes, in development only.
//
// - React Native's transformer adds React Refresh's Babel plugin only when Metro's options say `hot`, and Metro gives
//   them no `hot` any more. Without the plugin React Refresh knows only what a module exports: a component of a screen
//   file that it doesn't export loses its state at every edit. The option goes in again here.
// - A module that makes the app (`createNavigation`) accepts its own updates, so an edit of the screens map, or of a
//   module only it imports, runs it again instead of reloading the app; the app that runs takes the new config.

const accept = require('./acceptPlugin');

const upstreamPath = process.env.APEX_NAVIGATION_BABEL_TRANSFORMER;
if (!upstreamPath) {
  throw new Error(
    "[navigation] The navigation's Babel transformer runs only through withApexNavigation in metro.config.js"
  );
}
/** @type {{ transform: (args: TransformArgs) => unknown, getCacheKey?: () => string }} */
const upstream = require(upstreamPath);

/**
 * @typedef {{ filename: string, options: { dev: boolean, hot?: boolean }, plugins?: unknown[], src: string }}
 *   TransformArgs
 */

/** @param {TransformArgs} args */
function transform(args) {
  const { filename, options } = args;
  if (!options.dev) return upstream.transform(args);
  const own = !filename.includes('node_modules');
  return upstream.transform({
    ...args,
    options: { ...options, hot: true },
    plugins: own ? [...(args.plugins ?? []), accept] : args.plugins,
  });
}

// The cache of transformed files changes with this transformer, not only with the one it goes in front of.
function getCacheKey() {
  return `apexnative-navigation-1:${upstream.getCacheKey?.() ?? ''}`;
}

module.exports = { ...upstream, transform, getCacheKey };

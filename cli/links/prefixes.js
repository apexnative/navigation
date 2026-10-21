// @ts-check
'use strict';

const path = require('node:path');
const { findConfig, keysOf, propertyValue, stringsOf } = require('../typescript');

/**
 * A prefix of `linking` as the native side declares it: a scheme of the app (`myapp://`), or a web site
 * (`https://example.com/app`) with the start of its paths, `''` for the whole site.
 * @typedef {{ text: string; scheme: string; host: string; path: string; web: boolean }} Prefix
 */

/**
 * The links of the app: the prefixes and the route patterns of `linking`, read from its code.
 * @typedef {{ prefixes: Prefix[]; routes: string[]; file: string }} Linking
 */

const HOW_TO = `Write the prefixes as a list of strings in linking of createNavigation, so the CLI can read them:

  linking: {
    prefixes: ['myapp://', 'https://example.com'],
    routes: { 'user/:userId': 'Profile' },
  }`;

/**
 * The links of the app, or why they can't be read: no `linking`, or prefixes that aren't a list of strings in the code.
 * @param {import('../typescript').TS} ts
 * @param {string} navigation the module of the app's navigation
 * @returns {Linking | { code: string; message: string; howTo?: string }}
 */
function readLinking(ts, navigation) {
  const linking = findConfig(ts, navigation, 'linking');
  if (!linking || !ts.isObjectLiteralExpression(linking.node)) {
    return {
      code: 'no-linking',
      message: `The app has no linking in createNavigation (${path.basename(navigation)}): see the Deep links guide.`,
    };
  }
  const prefixesValue = propertyValue(ts, linking, 'prefixes');
  const texts = prefixesValue && stringsOf(ts, prefixesValue);
  if (!texts) {
    return {
      code: 'prefixes-unreadable',
      message: prefixesValue
        ? 'The prefixes of linking are computed: the CLI reads only a list of strings.'
        : 'linking has no prefixes: the native side declares the app by them.',
      howTo: HOW_TO,
    };
  }
  const routesValue = propertyValue(ts, linking, 'routes');
  return {
    prefixes: texts.flatMap((text) => {
      const prefix = parsePrefix(text);
      return prefix ? [prefix] : [];
    }),
    routes: routesValue ? keysOf(ts, routesValue) : [],
    file: linking.file,
  };
}

/**
 * A prefix as the native side sees it; undefined for one that isn't `scheme://…`.
 * @param {string} text
 * @returns {Prefix | undefined}
 */
function parsePrefix(text) {
  const found = /^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/([^/?#]*)([^?#]*)/.exec(text.trim());
  if (!found) return undefined;
  const scheme = found[1].toLowerCase();
  const web = scheme === 'http' || scheme === 'https';
  return {
    text,
    scheme,
    host: found[2].toLowerCase(),
    path: found[3].replace(/\/+$/, ''),
    web,
  };
}

/**
 * Whether a URL is the app's: it starts with one of the prefixes.
 * @param {string} url
 * @param {Prefix[]} prefixes
 */
function isAppLink(url, prefixes) {
  return prefixes.some(({ text }) => url.toLowerCase().startsWith(text.toLowerCase()));
}

module.exports = { readLinking, parsePrefix, isAppLink, HOW_TO };

// @ts-check
'use strict';

/** @typedef {import('./types').StepResult} StepResult */

const REQUIRE = "const { withApexNavigation } = require('@apexnative/navigation/metro');";

// The config the file exports: `module.exports = …;`, its last statement.
const EXPORT = /^module\.exports\s*=\s*([\s\S]+?);?\s*$/m;

const HOW_TO = `Wrap the config metro.config.js exports, so an edit updates the app by Fast Refresh instead of
reloading it:

  ${REQUIRE}

  module.exports = withApexNavigation(mergeConfig(getDefaultConfig(__dirname), config));`;

/**
 * metro.config.js: the config it exports goes through withApexNavigation, which puts the navigation's Babel
 * transformer in front of the app's own.
 * @param {string} text
 * @returns {StepResult}
 */
function planMetro(text) {
  if (text.includes('withApexNavigation')) return { status: 'done', note: 'already set up' };
  const found = EXPORT.exec(text);
  // Only an export that ends the file: one in the middle may be changed by what comes after it.
  if (!found || text.slice(found.index + found[0].length).trim() !== '') {
    return { status: 'manual', note: 'changed by hand, see below', howTo: HOW_TO };
  }
  const exported = found[1].trim();
  const before = text.slice(0, found.index);
  const requires = [...before.matchAll(/^.*require\(.*\);?$/gm)];
  const last = requires[requires.length - 1];
  const head =
    last === undefined
      ? `${REQUIRE}\n\n${before}`
      : `${before.slice(0, last.index + last[0].length)}\n${REQUIRE}${before.slice(last.index + last[0].length)}`;
  return {
    status: 'change',
    next: `${head}module.exports = withApexNavigation(${exported});\n`,
    note: 'Fast Refresh for every edit',
  };
}

module.exports = { planMetro, HOW_TO };

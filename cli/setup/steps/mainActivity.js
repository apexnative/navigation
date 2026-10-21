// @ts-check
'use strict';

const { addImport, removeUnusedImports } = require('../source');

/** @typedef {import('./types').StepResult} StepResult */

const HOW_TO = `Extend NavigationActivity. The screens come from JavaScript, so getMainComponentName and
createReactActivityDelegate go.

  import dev.apexnative.navigation.NavigationActivity

  class MainActivity : NavigationActivity()`;

// The members of the template, each with the comment above it.
const DOC = String.raw`(?:\/\*\*(?:(?!\*\/)[\s\S])*\*\/\s*)?`;
const MAIN_COMPONENT_NAME = new RegExp(
  String.raw`\n?[ \t]*${DOC}override\s+fun\s+getMainComponentName\(\)\s*:\s*String\s*` +
    String.raw`(?:=\s*"[^"]*"|\{\s*return\s+"[^"]*"\s*\})[ \t]*\n`
);
const ACTIVITY_DELEGATE = new RegExp(
  String.raw`\n?[ \t]*${DOC}override\s+fun\s+createReactActivityDelegate\(\)\s*:\s*ReactActivityDelegate\s*` +
    String.raw`(?:=\s*DefaultReactActivityDelegate\([^)]*\)|\{\s*return\s+DefaultReactActivityDelegate\([^)]*\)\s*\})` +
    String.raw`[ \t]*\n`
);

// Imports of the template that the change leaves unused.
const TEMPLATE_IMPORTS = [
  'com.facebook.react.ReactActivity',
  'com.facebook.react.ReactActivityDelegate',
  'com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled',
  'com.facebook.react.defaults.DefaultReactActivityDelegate',
];

/**
 * MainActivity.kt: the activity becomes a NavigationActivity.
 * @param {string} text
 * @returns {StepResult}
 */
function planMainActivity(text) {
  if (/:\s*NavigationActivity\(\)/.test(text)) return { status: 'done', note: 'already set up' };
  const header = /class\s+(\w+)\s*:\s*ReactActivity\(\)/;
  const manual = /** @type {StepResult} */ ({
    status: 'manual',
    note: 'changed by hand, see below',
    howTo: HOW_TO,
  });
  if (!header.test(text)) return manual;
  let next = text.replace(MAIN_COMPONENT_NAME, '').replace(ACTIVITY_DELEGATE, '');
  // A delegate of the app's own: what it does has to move elsewhere by hand.
  if (/\b(getMainComponentName|createReactActivityDelegate)\b/.test(next)) return manual;
  next = next.replace(header, 'class $1 : NavigationActivity()');
  next = next.replace(/(class\s+\w+\s*:\s*NavigationActivity\(\))\s*\{\s*\}/, '$1');
  // Added first: with the template's imports gone the file would have none to sort it among.
  next = addImport(next, 'import dev.apexnative.navigation.NavigationActivity', { sorted: true });
  next = removeUnusedImports(next, TEMPLATE_IMPORTS);
  return { status: 'change', next, note: 'NavigationActivity' };
}

module.exports = { planMainActivity };

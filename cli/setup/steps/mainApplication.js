// @ts-check
'use strict';

const { addImport, codeOf, removeUnusedImports } = require('../source');

/** @typedef {import('./types').StepResult} StepResult */

const HOW_TO = `Extend NavigationApplication. It loads React Native itself, so loadReactNative(this) goes:
calling it again crashes the app on start.

  import dev.apexnative.navigation.NavigationApplication

  class MainApplication : NavigationApplication() {
    override val reactHost: ReactHost by lazy { ... }   // as it is
  }`;

// Imports of the template that the change leaves unused.
const TEMPLATE_IMPORTS = [
  'android.app.Application',
  'com.facebook.react.ReactApplication',
  'com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative',
];

/**
 * MainApplication.kt: the application becomes a NavigationApplication.
 * @param {string} text
 * @returns {StepResult}
 */
function planMainApplication(text) {
  const manual = /** @type {StepResult} */ ({
    status: 'manual',
    note: 'changed by hand, see below',
    howTo: HOW_TO,
  });
  if (/:\s*NavigationApplication\(\)/.test(text)) {
    return /\bloadReactNative\s*\(/.test(codeOf(text))
      ? manual
      : { status: 'done', note: 'already set up' };
  }
  const header = /class\s+(\w+)\s*:\s*Application\(\)\s*,\s*ReactApplication\b/;
  if (!header.test(text)) return manual;
  let next = text.replace(header, 'class $1 : NavigationApplication()');
  // The template's onCreate only loads React Native, so it goes; another one keeps the rest of its code.
  next = next.replace(
    /\n\n?[ \t]*override\s+fun\s+onCreate\(\)\s*\{\s*super\.onCreate\(\)\s*loadReactNative\(this\)\s*\}/,
    ''
  );
  next = next.replace(/^[ \t]*loadReactNative\(this\)[ \t]*\r?\n/m, '');
  if (/\bloadReactNative\s*\(/.test(codeOf(next))) return manual;
  // Added first: with the template's imports gone the file would have none to sort it among.
  next = addImport(next, 'import dev.apexnative.navigation.NavigationApplication', {
    sorted: true,
  });
  next = removeUnusedImports(next, TEMPLATE_IMPORTS);
  return { status: 'change', next, note: 'NavigationApplication' };
}

module.exports = { planMainApplication };

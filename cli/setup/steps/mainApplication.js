// @ts-check
'use strict';

const { addImport, codeOf, removeUnusedImports } = require('../source');

/** @typedef {import('./types').StepResult} StepResult */

const HOW_TO = `Extend NavigationApplication and keep the template's loadReactNative(this) in onCreate: it loads
React Native as the app's Gradle files say, and the navigation makes its React host after it.

  import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
  import dev.apexnative.navigation.NavigationApplication

  class MainApplication : NavigationApplication() {
    override val reactHost: ReactHost by lazy { ... }   // as it is

    override fun onCreate() {
      super.onCreate()
      loadReactNative(this)
    }
  }`;

const LOAD_IMPORT = 'import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative';

// Imports of the template that the change leaves unused.
const TEMPLATE_IMPORTS = ['android.app.Application', 'com.facebook.react.ReactApplication'];

// A load of React Native of the app's own, next to loadReactNative(this) a second one that crashes the app at start:
// the New Architecture's entry point (react-native-navigation's apps call it in onCreate) and SoLoader.
// A bare `load()` is the entry point's only where the file imports it. A form these don't take out (`load` with
// arguments, an `if` without braces) is left to the app's author.
const OWN_LOAD = [
  /^[ \t]*if\s*\(\s*BuildConfig\.IS_NEW_ARCHITECTURE_ENABLED\s*\)\s*\{(?:\s*\/\/[^\n]*)*\s*DefaultNewArchitectureEntryPoint\.load\(\)\s*\}[ \t]*\r?\n/gm,
  /^[ \t]*DefaultNewArchitectureEntryPoint\.load\(\)[ \t]*\r?\n/gm,
  /^[ \t]*SoLoader\.init\([^\n]*\)[ \t]*\r?\n/gm,
];
const BARE_LOAD = [
  /^[ \t]*if\s*\(\s*BuildConfig\.IS_NEW_ARCHITECTURE_ENABLED\s*\)\s*\{(?:\s*\/\/[^\n]*)*\s*load\(\)\s*\}[ \t]*\r?\n/gm,
  /^[ \t]*load\(\)[ \t]*\r?\n/gm,
];
const BARE_LOAD_IMPORT =
  /^import\s+com\.facebook\.react\.defaults\.DefaultNewArchitectureEntryPoint\.load\b/m;
const OWN_LOAD_IMPORTS = [
  'com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load',
  'com.facebook.react.defaults.DefaultNewArchitectureEntryPoint',
  'com.facebook.soloader.SoLoader',
  'com.facebook.react.soloader.OpenSourceMergedSoMapping',
];

/**
 * MainApplication.kt: the application becomes a NavigationApplication, and loads React Native in its onCreate as the
 * template does.
 * @param {string} text
 * @returns {StepResult}
 */
function planMainApplication(text) {
  const manual = /** @type {StepResult} */ ({
    status: 'manual',
    note: 'changed by hand, see below',
    howTo: HOW_TO,
  });
  if (/:\s*NavigationApplication\(/.test(text)) {
    if (loadsReactNative(text) && !loadsReactNativeItself(text)) {
      return { status: 'done', note: 'already set up' };
    }
    const next = withReactNativeLoaded(text);
    return next === undefined
      ? manual
      : { status: 'change', next, note: 'loadReactNative(this) in onCreate' };
  }
  const header = /class\s+(\w+)\s*:\s*Application\(\)\s*,\s*ReactApplication\b/;
  if (!header.test(text)) return manual;
  // The template's onCreate stays: its loadReactNative(this) loads React Native for the navigation too.
  let next = withReactNativeLoaded(text.replace(header, 'class $1 : NavigationApplication()'));
  if (next === undefined) return manual;
  // Added first: with the template's imports gone the file would have none to sort it among.
  next = addImport(next, 'import dev.apexnative.navigation.NavigationApplication', {
    sorted: true,
  });
  next = removeUnusedImports(next, TEMPLATE_IMPORTS);
  return { status: 'change', next, note: 'NavigationApplication' };
}

/**
 * Whether the code of the file, not a comment, calls loadReactNative.
 * @param {string} text
 */
function loadsReactNative(text) {
  return /\bloadReactNative\s*\(/.test(codeOf(text));
}

/**
 * Whether the code of the file loads React Native on its own as well: the New Architecture's entry point or SoLoader.
 * @param {string} text
 */
function loadsReactNativeItself(text) {
  const code = codeOf(text);
  return (
    /\bSoLoader\.init\s*\(/.test(code) ||
    /\bDefaultNewArchitectureEntryPoint\.load\s*\(/.test(code) ||
    (BARE_LOAD_IMPORT.test(text) && /(?<![\w.])load\s*\(/.test(code))
  );
}

/**
 * The application with `loadReactNative(this)` in its onCreate, after `super.onCreate()`: as it is when it has the
 * call, with an onCreate of the template's when it has none. Undefined for an onCreate that doesn't call
 * `super.onCreate()`, whose order only its author knows.
 * @param {string} text
 * @returns {string | undefined}
 */
function withReactNativeLoaded(text) {
  let without = text;
  const patterns = BARE_LOAD_IMPORT.test(text) ? [...OWN_LOAD, ...BARE_LOAD] : OWN_LOAD;
  for (const pattern of patterns) without = without.replace(pattern, '');
  without = removeUnusedImports(without, OWN_LOAD_IMPORTS);
  if (loadsReactNativeItself(without)) return undefined;
  if (loadsReactNative(without)) return without;
  return withLoadCall(without);
}

/**
 * @param {string} text an application without a load of React Native
 * @returns {string | undefined}
 */
function withLoadCall(text) {
  let next;
  const superCall = /^([ \t]*)super\.onCreate\(\)[ \t]*\r?\n/m;
  if (/\boverride\s+fun\s+onCreate\s*\(/.test(text)) {
    if (!superCall.test(text)) return undefined;
    next = text.replace(superCall, (line, indent) => `${line}${indent}loadReactNative(this)\n`);
  } else {
    const close = classEnd(text);
    if (close === undefined) return undefined;
    const indent = /^([ \t]+)override\b/m.exec(text)?.[1] ?? '  ';
    const body = text.slice(0, close).replace(/\s*$/, '');
    const onCreate = [
      '',
      `${indent}override fun onCreate() {`,
      `${indent}${indent}super.onCreate()`,
      `${indent}${indent}loadReactNative(this)`,
      `${indent}}`,
    ].join('\n');
    next = `${body}\n${onCreate}\n${text.slice(close)}`;
  }
  return addImport(next, LOAD_IMPORT, { sorted: true });
}

/**
 * The index of the closing brace of the file's application class: its braces counted from the class's own, past any
 * declaration or comment after it. Undefined when the braces don't close.
 * @param {string} text
 * @returns {number | undefined}
 */
function classEnd(text) {
  // The comments and the string and character literals blanked out, so that an index in it is one in the text and a
  // brace in a literal doesn't count.
  const blank = (/** @type {string} */ part) => part.replace(/[^\n]/g, ' ');
  const code = text
    .replace(/\/\*[\s\S]*?\*\//g, blank)
    .replace(/\/\/.*$/gm, blank)
    .replace(/"(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'/g, blank);
  const header = /\bclass\s+\w+\s*:[^{]*\{/.exec(code);
  if (!header) return undefined;
  let depth = 0;
  for (let index = header.index + header[0].length - 1; index < code.length; index++) {
    if (code[index] === '{') depth++;
    else if (code[index] === '}' && --depth === 0) return index;
  }
  return undefined;
}

module.exports = { planMainApplication, withReactNativeLoaded, loadsReactNativeItself };

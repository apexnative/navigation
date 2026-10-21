// @ts-check
'use strict';

const { codeOf } = require('../source');

/** @typedef {import('./types').StepResult} StepResult */

const HOW_TO = `The screens set the status bar (the statusBar option), so the app doesn't render React Native's
StatusBar: it needs UIViewControllerBasedStatusBarAppearance set to NO, and setup sets it to YES. Remove from App.tsx:

  <StatusBar … />
  StatusBar.setBarStyle(…) and other calls of StatusBar`;

// The template's status bar, its color scheme, and the imports only they use.
const STATUS_BAR =
  /^[ \t]*<StatusBar\s+barStyle=\{\s*isDarkMode\s*\?\s*'light-content'\s*:\s*'dark-content'\s*\}\s*\/>[ \t]*\r?\n/m;
const DARK_MODE =
  /^[ \t]*const\s+isDarkMode\s*=\s*useColorScheme\(\)\s*===\s*'dark';?[ \t]*\r?\n(?:[ \t]*\r?\n)?/m;
const REACT_NATIVE = /^import\s*\{([^}]*)\}\s*from\s*'react-native';?[ \t]*(\r?\n)?/m;

/**
 * App.tsx: no StatusBar of React Native, the screens set the status bar.
 * @param {string} text
 * @returns {StepResult}
 */
function planApp(text) {
  if (!uses(text, 'StatusBar')) return { status: 'done', note: 'already set up' };
  let next = text.replace(STATUS_BAR, '');
  if (uses(next, 'StatusBar')) {
    return { status: 'manual', note: 'StatusBar of its own, see below', howTo: HOW_TO };
  }
  if (count(next, 'isDarkMode') === 1) next = next.replace(DARK_MODE, '');
  next = removeImports(next, ['StatusBar', 'useColorScheme']);
  return { status: 'change', next, note: 'status bar set by the screens' };
}

/**
 * Whether the code uses the name, the imports left out.
 * @param {string} text
 * @param {string} name
 */
function uses(text, name) {
  return count(codeOf(text).replace(/^import\s[^;]*;/gm, ''), name) > 0;
}

/**
 * @param {string} text
 * @param {string} name
 */
function count(text, name) {
  return (text.match(new RegExp(`\\b${name}\\b`, 'g')) ?? []).length;
}

/**
 * The text with the names nothing uses any more out of its import of react-native, and without the import when none
 * is left. A multi-line import stays multi-line.
 * @param {string} text
 * @param {string[]} names
 */
function removeImports(text, names) {
  const found = REACT_NATIVE.exec(text);
  if (!found) return text;
  const imported = found[1]
    .split(',')
    .map((name) => name.trim())
    .filter(Boolean);
  const kept = imported.filter((name) => !names.includes(name) || uses(text, name));
  if (kept.length === imported.length) return text;
  if (kept.length === 0) return text.replace(REACT_NATIVE, '');
  const list = found[1].includes('\n')
    ? `{\n${kept.map((name) => `  ${name},\n`).join('')}}`
    : `{ ${kept.join(', ')} }`;
  return text.replace(REACT_NATIVE, `import ${list} from 'react-native';${found[2] ?? ''}`);
}

module.exports = { planApp, HOW_TO };

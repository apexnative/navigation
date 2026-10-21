// @ts-check
'use strict';

/** @typedef {import('./types').StepResult} StepResult */

const HOW_TO = `Let each screen set the status bar (the statusBar option):

  <key>UIViewControllerBasedStatusBarAppearance</key>
  <true/>`;

const KEY = /(<key>UIViewControllerBasedStatusBarAppearance<\/key>\s*)<(true|false)\s*\/>/;

/**
 * Info.plist: the status bar follows the screen on top.
 * @param {string} text
 * @returns {StepResult}
 */
function planInfoPlist(text) {
  const found = KEY.exec(text);
  if (found?.[2] === 'true') return { status: 'done', note: 'already set up' };
  if (found)
    return {
      status: 'change',
      next: text.replace(KEY, '$1<true/>'),
      note: 'status bar per screen',
    };
  // The key goes last in the top dictionary, indented as its other keys.
  const end = /\n<\/dict>\s*<\/plist>\s*$/.exec(text);
  if (!end) return { status: 'manual', note: 'changed by hand, see below', howTo: HOW_TO };
  const indent = /\n([ \t]+)<key>/.exec(text)?.[1] ?? '\t';
  const key = `\n${indent}<key>UIViewControllerBasedStatusBarAppearance</key>\n${indent}<true/>`;
  return {
    status: 'change',
    next: text.slice(0, end.index) + key + text.slice(end.index),
    note: 'status bar per screen',
  };
}

module.exports = { planInfoPlist };

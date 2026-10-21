// @ts-check
'use strict';

/** @typedef {import('./types').StepResult} StepResult */

const APPLICATION = /<application\b[^>]*>/;
const FLAG = /android:enableOnBackInvokedCallback\s*=\s*(["'])(.*?)\1/;
const ATTRIBUTE = 'android:enableOnBackInvokedCallback="true"';

const HOW_TO = `Add ${ATTRIBUTE} to the <application> element of AndroidManifest.xml: the system then plays its back animations, such as back to the home screen from the first screen.`;

/**
 * AndroidManifest.xml: the app takes part in predictive back, so the system plays its back animations, back to the home
 * screen from the first screen among them. Android 16 turns it on for an app that targets API 36; an app that targets
 * less opts in here. A value the app set itself stays.
 * @param {string} text
 * @returns {StepResult}
 */
function planManifest(text) {
  const tag = APPLICATION.exec(text)?.[0];
  if (!tag) return { status: 'manual', note: 'no <application> element, see below', howTo: HOW_TO };
  const value = FLAG.exec(tag)?.[2];
  if (value === 'true') return { status: 'done', note: 'already set up' };
  if (value !== undefined) return { status: 'done', note: "the app's own setting" };
  // On a line of its own before the tag closes, as the template has its attributes.
  const end = tag.endsWith('/>') ? '/>' : '>';
  const head = tag.slice(0, -end.length).trimEnd();
  const indent = /\n([ \t]+)\S[^\n]*$/.exec(head)?.[1];
  const attribute = indent ? `\n${indent}${ATTRIBUTE}` : ` ${ATTRIBUTE}`;
  return {
    status: 'change',
    // A function, so a `$` in the app's own attributes stays as it is.
    next: text.replace(tag, () => `${head}${attribute}${end}`),
    note: 'predictive back',
  };
}

module.exports = { planManifest, HOW_TO };

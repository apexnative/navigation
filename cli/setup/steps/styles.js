// @ts-check
'use strict';

/** @typedef {import('./types').StepResult} StepResult */

const APP_THEME = /(<style\s+name="AppTheme"\s+parent=")([^"]*)(")/;

// The theme of React Native's template: AppCompat, light or dark with the system, with no Material 3 colors. A
// light-only theme stays: Theme.ApexNavigation would make the app follow dark mode.
const TEMPLATE_PARENT = 'Theme.AppCompat.DayNight.NoActionBar';

/**
 * styles.xml: the app's theme inherits Theme.ApexNavigation, Material 3 in neutral grays. A theme of the app's own
 * stays: the navigation takes its Material 3 colors, or its own neutral ones when it has none.
 * @param {string} text
 * @returns {StepResult}
 */
function planStyles(text) {
  const parent = APP_THEME.exec(text)?.[2];
  if (parent === 'Theme.ApexNavigation') return { status: 'done', note: 'already set up' };
  if (parent !== TEMPLATE_PARENT) return { status: 'done', note: "the app's own theme" };
  return {
    status: 'change',
    next: text.replace(APP_THEME, '$1Theme.ApexNavigation$3'),
    note: 'Theme.ApexNavigation',
  };
}

module.exports = { planStyles };

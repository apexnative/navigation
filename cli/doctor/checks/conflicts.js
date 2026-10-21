// @ts-check
'use strict';

const { dependencies } = require('../../app');

/** @typedef {import('../types').Check} Check */
/** @typedef {import('../types').Context} Context */

// Navigation libraries of their own: their screens don't know about this navigation's, nor its about theirs.
const OTHER_NAVIGATION = [/^@react-navigation\//, /^react-native-navigation$/];

/**
 * Another navigation in the app, and Expo.
 * @param {Context} context
 * @returns {Check[]}
 */
function conflictChecks({ root }) {
  const names = dependencies(root);
  /** @type {Check[]} */
  const checks = [];
  const others = names.filter((name) => OTHER_NAVIGATION.some((pattern) => pattern.test(name)));
  if (others.length > 0) {
    checks.push({
      group: 'conflicts',
      title: 'Another navigation',
      status: 'warn',
      detail: `${others.join(', ')}: two navigations in one app don't see each other's screens`,
    });
  }
  if (names.includes('expo')) {
    checks.push({
      group: 'conflicts',
      title: 'Expo',
      status: 'warn',
      detail: "Expo makes the native files, and setup doesn't change them yet",
    });
  }
  if (checks.length === 0) checks.push({ group: 'conflicts', title: 'None', status: 'ok' });
  return checks;
}

module.exports = { conflictChecks };

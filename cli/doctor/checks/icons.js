// @ts-check
'use strict';

const { readConfig } = require('../../config');
const { readColorHash } = require('../../icons/android');
const { readIconSet } = require('../../icons/ios');
const { iconsHash, resolveIconSettings } = require('../../icons/settings');

/** @typedef {import('../types').Check} Check */
/** @typedef {import('../../project').Project} Project */

const ICONS = { label: 'npx apex-navigation icons', run: /** @type {const} */ ('icons') };

/**
 * The app icon: made from its settings in package.json on both platforms; without settings, an iOS icon set with no
 * image, which the App Store refuses.
 * @param {string} root
 * @param {Project} project
 * @returns {Check[]}
 */
function iconChecks(root, project) {
  const saved = readConfig(root).icons;
  const set = readIconSet(project);
  if (!saved) {
    if (set && set.images === 0) {
      return [
        {
          group: 'app',
          title: 'App icon',
          status: 'warn',
          detail: 'the iOS icon set has no image: the App Store refuses the app',
          fix: { label: 'npx apex-navigation icons --logo assets/logo.png --background "#FFFFFF"' },
        },
      ];
    }
    return [];
  }
  const resolved = resolveIconSettings(root, {}, saved);
  if ('errors' in resolved) {
    return [
      {
        group: 'app',
        title: 'App icon',
        status: 'warn',
        detail: resolved.errors[0],
        fix: { label: 'npx apex-navigation icons' },
      },
    ];
  }
  const hash = iconsHash(root, resolved.settings);
  const stale = [
    ...(project.infoPlist && set?.hash !== hash ? ['iOS'] : []),
    ...(project.manifest && readColorHash(project) !== hash ? ['Android'] : []),
  ];
  if (stale.length === 0) {
    return [{ group: 'app', title: 'App icon', status: 'ok', detail: 'made from its settings' }];
  }
  return [
    {
      group: 'app',
      title: 'App icon',
      status: 'warn',
      detail: `${stale.join(' and ')}: not made from the settings in package.json`,
      fix: ICONS,
    },
  ];
}

module.exports = { iconChecks };

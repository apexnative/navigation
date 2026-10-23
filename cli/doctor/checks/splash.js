// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { readConfig } = require('../../config');
const { THEME } = require('../../splash/android');
const { STORYBOARD } = require('../../splash/ios');
const { resolveSettings, splashHash } = require('../../splash/settings');

/** @typedef {import('../types').Check} Check */
/** @typedef {import('../../project').Project} Project */

const SPLASH = { label: 'npx apex-navigation splash' };
// Android 12+ shows the logo in a circle of this diameter.
const CIRCLE = 192;
const LOGO_HEIGHT = /<dimen name="apex_navigation_splash_logo_height">([\d.]+)dp<\/dimen>/;

/**
 * The native splash: made from its settings in package.json on both platforms, its logo whole on Android 12+. Without
 * settings there is nothing to check: the navigation keeps a copy of a storyboard and of `UILaunchScreen` alike.
 * @param {string} root
 * @param {Project} project
 * @returns {Check[]}
 */
function splashChecks(root, project) {
  const saved = readConfig(root).splash;
  if (!saved) return [];
  const plist = project.infoPlist && fs.readFileSync(project.infoPlist, 'utf8');
  const resolved = resolveSettings(root, {}, saved);
  if ('errors' in resolved) {
    return [
      { group: 'app', title: 'Splash', status: 'warn', detail: resolved.errors[0], fix: SPLASH },
    ];
  }
  const { settings } = resolved;
  const hash = splashHash(root, settings);

  /** @type {string[]} */
  const stale = [];
  if (project.infoPlist && plist) {
    const assets = path.join(path.dirname(project.infoPlist), 'Images.xcassets');
    const made =
      fs.existsSync(path.join(assets, `SplashLogo-${hash}.imageset`)) &&
      new RegExp(`<key>UILaunchStoryboardName</key>\\s*<string>${STORYBOARD}</string>`).test(plist);
    if (!made) stale.push('iOS');
  }
  const values =
    project.manifest &&
    path.join(path.dirname(project.manifest), 'res', 'values', 'apex_navigation_splash.xml');
  const valuesText = values && fs.existsSync(values) ? fs.readFileSync(values, 'utf8') : undefined;
  if (project.manifest) {
    const made =
      valuesText?.includes(`(${hash})`) &&
      fs.readFileSync(project.manifest, 'utf8').includes(`android:theme="@style/${THEME}"`);
    if (!made) stale.push('Android');
  }

  /** @type {Check[]} */
  const checks = [
    stale.length === 0
      ? { group: 'app', title: 'Splash', status: 'ok', detail: 'made from its settings' }
      : {
          group: 'app',
          title: 'Splash',
          status: 'warn',
          detail: `${stale.join(' and ')}: not made from the settings in package.json`,
          fix: SPLASH,
        },
  ];
  const height = Number(LOGO_HEIGHT.exec(valuesText ?? '')?.[1]);
  if (height > 0 && Math.hypot(settings.logoWidth, height) > CIRCLE) {
    checks.push({
      group: 'app',
      title: 'Splash logo',
      status: 'warn',
      detail: `Android 12+ shows it in a circle of ${CIRCLE} dp: its corners are cut`,
      fix: { label: 'A smaller --logo-width, or a logo with room around it' },
    });
  }
  return checks;
}

module.exports = { splashChecks };

// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');

/** @typedef {'swiftui' | 'uikit'} IosKind */
/** @typedef {'compose' | 'views'} AndroidKind */
/**
 * The CLI's settings, under `"apexNavigation"` in the app's package.json:
 * - screensDir: where `screen` puts new screens, from the app's root;
 * - nativeScreens: what each native screen is made with on each platform, by its native name; codegen creates the
 *   screens from it;
 * - splash: what `splash` makes the native splash from;
 * - icons: what `icons` makes the app icon from.
 * @typedef {{
 *   screensDir?: string;
 *   nativeScreens?: Record<string, { ios?: IosKind; android?: AndroidKind }>;
 *   splash?: import('./splash/settings').SplashSettings;
 *   icons?: Partial<{ logo: string; image: string; background: string; darkLogo: string; logoSize: number }>;
 * }} Config
 */

const KEY = 'apexNavigation';

/**
 * @param {string} root
 * @returns {Config}
 */
function readConfig(root) {
  try {
    const config = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'))[KEY];
    return typeof config === 'object' && config !== null ? config : {};
  } catch {
    return {};
  }
}

/**
 * Where new screens go: the folder of the settings, else `src/screens` (or `screens` in an app without `src`).
 * @param {string} root
 * @param {Config} config
 */
function screensDir(root, config) {
  if (config.screensDir) return config.screensDir;
  return fs.existsSync(path.join(root, 'src')) ? 'src/screens' : 'screens';
}

/**
 * The change of package.json that stores the settings `update` makes; `done` when they are there already. The file
 * keeps its indentation.
 * @param {string} root
 * @param {(config: Config) => Config} update
 * @returns {import('./changes').FileChange}
 */
function configChange(root, update) {
  const file = path.join(root, 'package.json');
  const before = fs.readFileSync(file, 'utf8');
  const json = JSON.parse(before);
  const current = json[KEY] ?? {};
  const next = update(current);
  if (JSON.stringify(next) === JSON.stringify(current)) {
    return { name: 'package.json', platform: 'js', file, status: 'done', note: 'settings kept' };
  }
  json[KEY] = next;
  const indent = /^([ \t]+)"/m.exec(before)?.[1] ?? '  ';
  return {
    name: 'package.json',
    platform: 'js',
    file,
    status: 'change',
    before,
    next: JSON.stringify(json, null, indent) + '\n',
    note: `"${KEY}" settings`,
  };
}

module.exports = { readConfig, screensDir, configChange };

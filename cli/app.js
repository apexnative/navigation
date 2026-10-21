// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { exec } = require('./exec');

/** The oldest React Native the navigation supports. */
const MINIMUM = [0, 85];

/** The newest React Native the navigation was checked with: `init` makes apps of its newest patch. */
const TESTED = [0, 87];

/** The name the navigation is installed under. */
const PACKAGE = '@apexnative/navigation';

/**
 * The folder of a package installed for the app: in its node_modules, or in one further up (a monorepo hoists them).
 * Not `require.resolve`: from inside this package it finds this package, not the app's.
 * @param {string} root
 * @param {string} name
 * @returns {string | undefined}
 */
function packageDir(root, name) {
  for (let dir = path.resolve(root); ; dir = path.dirname(dir)) {
    const candidate = path.join(dir, 'node_modules', name);
    if (fs.existsSync(path.join(candidate, 'package.json'))) return candidate;
    if (path.dirname(dir) === dir) return undefined;
  }
}

/**
 * The version of a package installed for the app; undefined without it.
 * @param {string} root
 * @param {string} name
 * @returns {string | undefined}
 */
function installedVersion(root, name) {
  const dir = packageDir(root, name);
  if (!dir) return undefined;
  try {
    return JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8')).version;
  } catch {
    return undefined;
  }
}

/**
 * The version of React Native the app has installed; undefined without it.
 * @param {string} root
 */
function reactNativeVersion(root) {
  return installedVersion(root, 'react-native');
}

/**
 * @param {string} version such as `0.87.1` or `0.88.0-rc.2`
 */
function isSupported(version) {
  const [major, minor] = version.split(/[.-]/).map(Number);
  return major > MINIMUM[0] || (major === MINIMUM[0] && minor >= MINIMUM[1]);
}

/**
 * The names of the app's dependencies, dev ones too.
 * @param {string} root
 * @returns {string[]}
 */
function dependencies(root) {
  try {
    const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
    return [...Object.keys(pkg.dependencies ?? {}), ...Object.keys(pkg.devDependencies ?? {})];
  } catch {
    return [];
  }
}

/**
 * An Expo app: Expo makes its native files itself.
 * @param {string} root
 */
function usesExpo(root) {
  return dependencies(root).includes('expo');
}

/**
 * The files with changes git hasn't committed; none outside a git repository.
 * @param {string} root
 * @param {string[]} files
 * @param {import('./exec').Exec} [run]
 * @returns {string[]}
 */
function uncommitted(root, files, run = exec) {
  const result = run('git', ['status', '--porcelain', '--', ...files], { cwd: root });
  if (!result.ok) return [];
  return result.stdout
    .split('\n')
    .filter(Boolean)
    .map((line) => line.slice(3));
}

module.exports = {
  MINIMUM,
  TESTED,
  PACKAGE,
  dependencies,
  installedVersion,
  packageDir,
  isSupported,
  reactNativeVersion,
  uncommitted,
  usesExpo,
};

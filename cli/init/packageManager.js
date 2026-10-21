// @ts-check
'use strict';

/** The package managers React Native's init knows. */
const MANAGERS = /** @type {const} */ (['npm', 'yarn', 'bun']);

/** @typedef {typeof MANAGERS[number]} PackageManager */

/**
 * The package manager the command was run with (`npx`, `yarn dlx`, `bunx`), from the user agent it gives its scripts;
 * npm when there is none, or one React Native's init doesn't know (pnpm).
 * @param {NodeJS.ProcessEnv} env
 * @returns {{ manager: PackageManager; unknown?: string }} `unknown`: the manager it was run with, when not one of these
 */
function packageManagerOf(env) {
  const name = /^([a-z]+)\//.exec(env.npm_config_user_agent ?? '')?.[1];
  if (name === undefined) return { manager: 'npm' };
  const known = MANAGERS.find((manager) => manager === name);
  return known ? { manager: known } : { manager: 'npm', unknown: name };
}

/**
 * @param {string} name
 * @returns {name is PackageManager}
 */
function isPackageManager(name) {
  return MANAGERS.some((manager) => manager === name);
}

/**
 * The command that installs the app's dependencies.
 * @param {PackageManager} manager
 * @returns {[string, string[]]}
 */
function installCommand(manager) {
  return [manager, ['install']];
}

/**
 * How to run a script of the app's package.json, such as `npm run ios` or `yarn ios`.
 * @param {PackageManager} manager
 * @param {string} script
 */
function runScript(manager, script) {
  return manager === 'yarn' ? `yarn ${script}` : `${manager} run ${script}`;
}

module.exports = { MANAGERS, packageManagerOf, isPackageManager, installCommand, runScript };

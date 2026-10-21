// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { exec } = require('./exec');

// Adding sharp downloads its binary: up to minutes.
const LONG = 20 * 60_000;

/**
 * sharp from the app's packages; undefined when the app doesn't have it. The commands that make images (`splash`,
 * `icons`) load it from the app, so the navigation itself doesn't depend on it.
 * @param {string} root
 * @returns {any}
 */
function loadSharp(root) {
  try {
    return require(require.resolve('sharp', { paths: [root] }));
  } catch {
    return undefined;
  }
}

/**
 * sharp, added to the app's dev dependencies after asking when the app doesn't have it; undefined when it isn't there.
 * A dry run and `--json` without `--yes` don't install.
 * @param {string} root
 * @param {{
 *   yes: boolean;
 *   dryRun: boolean;
 *   json: boolean;
 *   ui: import('./ui').UI;
 *   install: (manager: string, args: string[]) => boolean;
 *   what: string;
 * }} options `what` names the images in the question, such as `The splash images`
 * @returns {Promise<any>}
 */
async function requireSharp(root, { yes, dryRun, json, ui, install, what }) {
  const sharp = loadSharp(root);
  if (sharp || dryRun || (json && !yes)) return sharp;
  const [manager, ...args] = sharpInstallCommand(root);
  const confirmed =
    yes || (await ui.confirm(`${what} are made with sharp. Add it to the dev dependencies?`));
  if (!confirmed) return undefined;
  // Under --json the output is one document: the package manager prints into the result's pipe, not the terminal.
  const installed = json
    ? exec(manager, args, { cwd: root, timeout: LONG }).ok
    : install(manager, args);
  if (!installed) return undefined;
  return loadSharp(root);
}

/**
 * The command that adds sharp to the app's dev dependencies, with the package manager of its lockfile.
 * @param {string} root
 * @returns {string[]}
 */
function sharpInstallCommand(root) {
  const has = (/** @type {string} */ name) => fs.existsSync(path.join(root, name));
  if (has('yarn.lock')) return ['yarn', 'add', '--dev', 'sharp'];
  if (has('bun.lock') || has('bun.lockb')) return ['bun', 'add', '--dev', 'sharp'];
  if (has('pnpm-lock.yaml')) return ['pnpm', 'add', '--save-dev', 'sharp'];
  return ['npm', 'install', '--save-dev', 'sharp'];
}

module.exports = { loadSharp, requireSharp, sharpInstallCommand };

// @ts-check
'use strict';

const os = require('node:os');
const { PACKAGE, installedVersion, isSupported, reactNativeVersion } = require('../app');
const { exec } = require('../exec');
const { createUI } = require('../ui');
const { androidChecks } = require('./checks/android');
const { appChecks } = require('./checks/app');
const { conflictChecks } = require('./checks/conflicts');
const { deviceChecks } = require('./checks/devices');
const { iosChecks } = require('./checks/ios');
const { navigationChecks } = require('./checks/navigation');
const { runFixes } = require('./fix');

/** @typedef {import('./types').Check} Check */
/** @typedef {import('./types').Context} Context */

/** @type {[import('./types').Group, string][]} */
const GROUPS = [
  ['app', 'App'],
  ['navigation', 'Navigation'],
  ['android', 'Android'],
  ['ios', 'iOS'],
  ['devices', 'Devices'],
  ['conflicts', 'Conflicts'],
];

/**
 * Every check of the app.
 * @param {Context} context
 * @returns {Check[]}
 */
function collect(context) {
  return [
    ...appChecks(context),
    ...navigationChecks(context),
    ...androidChecks(context),
    ...iosChecks(context),
    ...deviceChecks(context),
    ...conflictChecks(context),
  ];
}

/**
 * Checks what the navigation needs in the app; with `fix`, makes the fixes it can, with `json`, prints the checks as
 * JSON for CI and tools.
 * @param {{
 *   root: string;
 *   fix: boolean;
 *   json: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   env?: NodeJS.ProcessEnv;
 *   platform?: NodeJS.Platform;
 *   home?: string;
 *   write?: (text: string) => void;
 * }} options
 * @returns {Promise<number>} the exit code: 1 when a check found a problem
 */
async function doctor({
  root,
  fix,
  json,
  ui = createUI(),
  run = exec,
  env = process.env,
  platform = process.platform,
  home = os.homedir(),
  write = (text) => process.stdout.write(text),
}) {
  if (fix && json) {
    ui.error('--fix asks before each fix: run it without --json.');
    return 1;
  }
  /** @type {Context} */
  const context = { root, run, env, platform, home };
  let checks = collect(context);
  const failed = () => checks.some((check) => check.status === 'error');

  if (json) {
    write(JSON.stringify({ ok: !failed(), checks }, null, 2) + '\n');
    return failed() ? 1 : 0;
  }

  const version = reactNativeVersion(root);
  const navigation = installedVersion(root, PACKAGE);
  ui.banner({
    command: 'doctor',
    facts: [
      version
        ? { text: `React Native ${version}`, ok: isSupported(version) }
        : { text: 'React Native not found' },
      { text: navigation ? `navigation ${navigation}` : 'navigation not installed' },
    ],
  });
  report(ui, checks);

  // A warning with a fix the CLI makes counts too: the typed commands, the status bar of the template.
  const fixable = () =>
    failed() || checks.some((check) => check.status === 'warn' && check.fix?.run !== undefined);
  if (fix && fixable()) {
    ui.line('');
    if (await runFixes(checks, { root, ui })) {
      checks = collect(context);
      ui.line('');
      ui.muted('  After the fixes');
      report(ui, checks);
    }
  }
  return failed() ? 1 : 0;
}

/**
 * The checks by group, then how many found something.
 * @param {Pick<import('../ui').UI, 'checks' | 'summary'>} ui
 * @param {Check[]} checks
 * @param {[import('./types').Group, string][]} [groups] the groups to show, with their titles
 */
function report(ui, checks, groups = GROUPS) {
  ui.checks(
    groups
      .map(([group, title]) => ({
        title,
        checks: checks.filter((check) => check.group === group),
      }))
      .filter(({ checks: inGroup }) => inGroup.length > 0)
  );
  ui.summary({
    errors: checks.filter(({ status }) => status === 'error').length,
    warnings: checks.filter(({ status }) => status === 'warn').length,
    passed: checks.filter(({ status }) => status === 'ok').length,
  });
}

module.exports = { doctor, collect, report, GROUPS };

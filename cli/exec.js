// @ts-check
'use strict';

const { spawnSync } = require('node:child_process');

/**
 * Runs a program and returns what it printed; `ok` is false when it couldn't run or failed. The CLI asks `git`,
 * `java`, `xcodebuild`, `pod` and the React Native CLI this way, and tests give it a stand-in.
 * @typedef {(command: string, args: string[], options?: { cwd?: string; timeout?: number }) =>
 *   { ok: boolean; stdout: string; stderr: string }} Exec
 */

/** @type {Exec} */
function exec(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd,
    encoding: 'utf8',
    timeout: options.timeout ?? 60_000,
  });
  return { ok: result.status === 0, stdout: result.stdout ?? '', stderr: result.stderr ?? '' };
}

/**
 * Runs a program with the terminal as its output, for the long ones the user watches (`pod install`); true when it
 * succeeded.
 * @param {string} command
 * @param {string[]} args
 * @param {{ cwd?: string }} [options]
 */
function execInTerminal(command, args, options = {}) {
  return spawnSync(command, args, { cwd: options.cwd, stdio: 'inherit' }).status === 0;
}

module.exports = { exec, execInTerminal };

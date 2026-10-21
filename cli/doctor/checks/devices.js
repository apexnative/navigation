// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { sdkDir } = require('./android');

/** @typedef {import('../types').Check} Check */
/** @typedef {import('../types').Context} Context */

/**
 * What the app runs on: a simulator with the iOS of Xcode's SDK, and adb with the devices it sees.
 * @param {Context} context
 * @returns {Check[]}
 */
function deviceChecks(context) {
  return [...simulatorChecks(context), ...adbChecks(context)];
}

/**
 * A simulator runtime of the iOS version Xcode builds for: `run-ios` needs one. macOS only.
 * @param {Context} context
 * @returns {Check[]}
 */
function simulatorChecks({ root, run, platform }) {
  if (platform !== 'darwin' || !fs.existsSync(path.join(root, 'ios'))) return [];
  const sdk = run('xcrun', ['--sdk', 'iphonesimulator', '--show-sdk-version']);
  // Without Xcode the iOS group says so already.
  if (!sdk.ok) return [];
  const sdkVersion = sdk.stdout.trim();
  const major = sdkVersion.split('.')[0];
  /** @type {{ version?: string; platform?: string; name?: string; isAvailable?: boolean }[]} */
  let runtimes = [];
  try {
    runtimes =
      JSON.parse(run('xcrun', ['simctl', 'list', 'runtimes', '--json']).stdout).runtimes ?? [];
  } catch {
    runtimes = [];
  }
  const found = runtimes.find(
    (runtime) =>
      runtime.isAvailable !== false &&
      (runtime.platform ?? runtime.name ?? '').startsWith('iOS') &&
      String(runtime.version).split('.')[0] === major
  );
  if (found) {
    return [
      { group: 'devices', title: 'iOS simulator', status: 'ok', detail: `iOS ${found.version}` },
    ];
  }
  return [
    {
      group: 'devices',
      title: 'iOS simulator',
      status: 'warn',
      detail: `none with iOS ${major}, the SDK of Xcode`,
      fix: { label: `Xcode → Settings → Components → iOS ${sdkVersion}` },
    },
  ];
}

/**
 * adb, on the PATH or in the SDK's platform-tools, and the devices it sees.
 * @param {Context} context
 * @returns {Check[]}
 */
function adbChecks(context) {
  const android = path.join(context.root, 'android');
  if (!fs.existsSync(android)) return [];
  const inSdk = path.join(sdkDir(context, android), 'platform-tools', 'adb');
  let result = context.run('adb', ['devices']);
  if (!result.ok) result = context.run(inSdk, ['devices']);
  if (!result.ok) {
    return [
      {
        group: 'devices',
        title: 'adb',
        status: 'warn',
        detail: 'not found',
        fix: { label: 'sdkmanager "platform-tools"' },
      },
    ];
  }
  const count = result.stdout.split('\n').filter((line) => /\tdevice\s*$/.test(line)).length;
  return [
    {
      group: 'devices',
      title: 'adb',
      status: 'ok',
      detail:
        count === 0
          ? 'no device connected'
          : `${count} ${count === 1 ? 'device' : 'devices'} connected`,
    },
  ];
}

module.exports = { deviceChecks };

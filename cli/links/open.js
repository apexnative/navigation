// @ts-check
'use strict';

const path = require('node:path');

/**
 * Where a link was opened, or why it wasn't on that platform.
 * @typedef {{ platform: 'ios' | 'android'; device?: string; ok: boolean; error?: { code: string; message: string } }}
 *   Opened
 */

/**
 * Opens a link in the app on the booted iOS simulator, as the system does when the user taps it. `cold` quits the
 * app first, so the link launches it.
 * @param {{
 *   url: string;
 *   bundleId: string | undefined;
 *   cold: boolean;
 *   run: import('../exec').Exec;
 * }} options
 * @returns {Opened}
 */
function openOnIos({ url, bundleId, cold, run }) {
  const booted = run('xcrun', ['simctl', 'list', 'devices', 'booted', '--json']);
  /** @type {string | undefined} */
  let device;
  try {
    const devices = Object.values(JSON.parse(booted.stdout).devices ?? {}).flat();
    device = /** @type {{ name?: string } | undefined} */ (devices[0])?.name;
  } catch {
    device = undefined;
  }
  if (!booted.ok || !device) {
    return {
      platform: 'ios',
      ok: false,
      error: {
        code: 'no-device',
        message: 'No iOS simulator is booted: start one, and the app on it.',
      },
    };
  }
  // The app may not run: then there is nothing to quit.
  if (cold && bundleId) run('xcrun', ['simctl', 'terminate', 'booted', bundleId]);
  const opened = run('xcrun', ['simctl', 'openurl', 'booted', url]);
  if (!opened.ok) {
    return {
      platform: 'ios',
      device,
      ok: false,
      error: { code: 'open-failed', message: `simctl openurl failed: ${opened.stderr.trim()}` },
    };
  }
  return { platform: 'ios', device, ok: true };
}

/**
 * Opens a link in the app on the connected Android device or emulator, as the system does when the user taps it. With
 * several, `ANDROID_SERIAL` picks one, as adb itself does. `cold` stops the app first, so the link launches it.
 * @param {{
 *   url: string;
 *   packageName: string | undefined;
 *   cold: boolean;
 *   env: NodeJS.ProcessEnv;
 *   run: import('../exec').Exec;
 * }} options
 * @returns {Opened}
 */
function openOnAndroid({ url, packageName, cold, env, run }) {
  const sdk = env.ANDROID_HOME ?? env.ANDROID_SDK_ROOT;
  const adb = sdk ? path.join(sdk, 'platform-tools', 'adb') : 'adb';
  const listed = run(adb, ['devices']);
  const devices = listed.ok
    ? listed.stdout
        .split('\n')
        .slice(1)
        .flatMap((line) => {
          const [serial, state] = line.trim().split(/\s+/);
          return state === 'device' ? [serial] : [];
        })
    : [];
  const serial = env.ANDROID_SERIAL ?? (devices.length === 1 ? devices[0] : undefined);
  if (!serial) {
    return {
      platform: 'android',
      ok: false,
      error:
        devices.length > 1
          ? {
              code: 'several-devices',
              message: `${devices.length} Android devices are connected: pick one with ANDROID_SERIAL=<serial>.`,
            }
          : {
              code: 'no-device',
              message: 'No Android device or emulator is connected (adb devices).',
            },
    };
  }
  if (cold && packageName) run(adb, ['-s', serial, 'shell', 'am', 'force-stop', packageName]);
  // adb hands the words to the device's shell as one line: the URL in single quotes keeps its & and ? as they are.
  const quoted = `'${url.replace(/'/g, "'\\''")}'`;
  const args = ['-s', serial, 'shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW'];
  args.push('-d', quoted);
  if (packageName) args.push(packageName);
  const opened = run(adb, args);
  // am prints its errors to stdout and still exits with 0.
  const failed = !opened.ok || /^Error:/m.test(opened.stdout);
  if (failed) {
    const reason = (/^Error:.*$/m.exec(opened.stdout)?.[0] ?? opened.stderr).trim();
    return {
      platform: 'android',
      device: serial,
      ok: false,
      error: { code: 'open-failed', message: `am start failed: ${reason}` },
    };
  }
  return { platform: 'android', device: serial, ok: true };
}

module.exports = { openOnIos, openOnAndroid };

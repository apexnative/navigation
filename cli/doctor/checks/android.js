// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');

/** @typedef {import('../types').Check} Check */
/** @typedef {import('../types').Context} Context */

// The oldest Android the navigation runs on.
const MIN_SDK = 24;

/**
 * JDK 17, the Android SDK the app builds with, its minSdk, and how edge-to-edge works in it.
 * @param {Context} context
 * @returns {Check[]}
 */
function androidChecks(context) {
  const android = path.join(context.root, 'android');
  const gradle = read(path.join(android, 'build.gradle'));
  if (gradle === undefined) {
    return [
      { group: 'android', title: 'Android project', status: 'info', detail: 'none in android/' },
    ];
  }
  const ext = (/** @type {string} */ name) =>
    new RegExp(String.raw`\b${name}\s*=\s*"?([\w.]+)"?`).exec(gradle)?.[1];
  return [
    jdkCheck(context),
    sdkCheck(context, android, {
      platform: ext('compileSdkVersion'),
      buildTools: ext('buildToolsVersion'),
      ndk: ext('ndkVersion'),
    }),
    minSdkCheck(Number(ext('minSdkVersion'))),
    edgeToEdgeCheck(
      read(path.join(android, 'gradle.properties')) ?? '',
      Number(ext('targetSdkVersion'))
    ),
  ];
}

/**
 * React Native builds Android with JDK 17.
 * @param {Context} context
 * @returns {Check}
 */
function jdkCheck({ run, env, platform, jdk = 'active' }) {
  const java = env.JAVA_HOME ? path.join(env.JAVA_HOME, 'bin', 'java') : 'java';
  const result = run(java, ['-version']);
  // `java -version` prints to stderr: `openjdk version "17.0.12" …`, or `"1.8.0_402"` for Java 8.
  const found = /version "(?:1\.)?(\d+)/.exec(result.stderr + result.stdout)?.[1];
  const major = found ? Number(found) : undefined;
  if (major === 17) return { group: 'android', title: 'JDK 17', status: 'ok' };
  const jdk17 = platform === 'darwin' ? run('/usr/libexec/java_home', ['-v', '17']) : undefined;
  // A repository that picks JDK 17 per command (scripts/android-env.sh) needs it installed, not in use.
  if (jdk17?.ok && jdk === 'installed') {
    return {
      group: 'android',
      title: 'JDK 17',
      status: 'ok',
      detail: 'scripts/android-env.sh uses it',
    };
  }
  if (jdk17?.ok) {
    return {
      group: 'android',
      title: 'JDK 17',
      status: 'warn',
      detail: `installed, but ${major ? `JDK ${major}` : 'another one'} is in use`,
      fix: { label: 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' },
    };
  }
  return {
    group: 'android',
    title: 'JDK 17',
    status: major && major > 17 ? 'warn' : 'error',
    detail: major ? `JDK ${major} found; React Native builds with JDK 17` : 'not found',
    fix: { label: 'Install JDK 17, such as Zulu 17, and set JAVA_HOME to it' },
  };
}

/**
 * The SDK platform, build tools and NDK of the versions the app's build.gradle names.
 * @param {Context} context
 * @param {string} android
 * @param {{ platform?: string; buildTools?: string; ndk?: string }} versions
 * @returns {Check}
 */
function sdkCheck(context, android, versions) {
  const sdk = sdkDir(context, android);
  if (!fs.existsSync(sdk)) {
    return {
      group: 'android',
      title: 'Android SDK',
      status: 'error',
      detail: `not found at ${sdk}`,
      fix: { label: 'Install it with Android Studio, or set ANDROID_HOME' },
    };
  }
  // A platform can come with a minor version in its folder name, such as android-37.0.
  const platforms = list(path.join(sdk, 'platforms'));
  const hasPlatform = (/** @type {string} */ version) =>
    platforms.some(
      (name) => name === `android-${version}` || name.startsWith(`android-${version}.`)
    );
  const missing = [
    versions.platform &&
      !hasPlatform(versions.platform) &&
      `platforms;android-${versions.platform}`,
    versions.buildTools &&
      !list(path.join(sdk, 'build-tools')).includes(versions.buildTools) &&
      `build-tools;${versions.buildTools}`,
    versions.ndk && !list(path.join(sdk, 'ndk')).includes(versions.ndk) && `ndk;${versions.ndk}`,
  ].filter(/** @returns {name is string} */ (name) => typeof name === 'string');
  if (missing.length > 0) {
    return {
      group: 'android',
      title: 'Android SDK',
      status: 'error',
      detail: `missing ${missing.join(', ')}`,
      fix: { label: `sdkmanager ${missing.map((name) => `"${name}"`).join(' ')}` },
    };
  }
  const parts = [
    versions.platform && `SDK ${versions.platform}`,
    versions.buildTools && `build tools ${versions.buildTools}`,
    versions.ndk && `NDK ${versions.ndk}`,
  ].filter(Boolean);
  return { group: 'android', title: 'Android SDK', status: 'ok', detail: parts.join(', ') };
}

/**
 * Where the Android SDK is: ANDROID_HOME, local.properties of the app, or where Android Studio puts it.
 * @param {Context} context
 * @param {string} android the app's android/ folder
 */
function sdkDir({ env, platform, home }, android) {
  const localSdk = /^sdk\.dir\s*=\s*(.+)$/m.exec(
    read(path.join(android, 'local.properties')) ?? ''
  )?.[1];
  return (
    env.ANDROID_HOME ??
    env.ANDROID_SDK_ROOT ??
    localSdk?.trim() ??
    (platform === 'darwin'
      ? path.join(home, 'Library', 'Android', 'sdk')
      : platform === 'win32'
        ? path.join(env.LOCALAPPDATA ?? home, 'Android', 'Sdk')
        : path.join(home, 'Android', 'Sdk'))
  );
}

/**
 * @param {number} minSdk
 * @returns {Check}
 */
function minSdkCheck(minSdk) {
  if (!minSdk || minSdk >= MIN_SDK) {
    return {
      group: 'android',
      title: 'minSdk',
      status: 'ok',
      detail: minSdk ? String(minSdk) : undefined,
    };
  }
  return {
    group: 'android',
    title: 'minSdk',
    status: 'error',
    detail: `${minSdk}: the navigation needs ${MIN_SDK}`,
    fix: { label: `minSdkVersion = ${MIN_SDK} in android/build.gradle` },
  };
}

/**
 * Where edge-to-edge is on: every version with the flag, else where Android requires it.
 * @param {string} properties gradle.properties
 * @param {number} targetSdk
 * @returns {Check}
 */
function edgeToEdgeCheck(properties, targetSdk) {
  const flag = /^\s*edgeToEdgeEnabled\s*=\s*true\s*$/m.test(properties);
  const detail = flag
    ? 'every Android version (edgeToEdgeEnabled=true)'
    : targetSdk >= 35
      ? `Android 15 and newer (targetSdk ${targetSdk})`
      : 'off: targetSdk is below 35 and edgeToEdgeEnabled is false';
  return { group: 'android', title: 'Edge-to-edge', status: 'info', detail };
}

/**
 * The names in a folder; none when it isn't there.
 * @param {string} dir
 * @returns {string[]}
 */
function list(dir) {
  try {
    return fs.readdirSync(dir);
  } catch {
    return [];
  }
}

/**
 * @param {string} file
 * @returns {string | undefined}
 */
function read(file) {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return undefined;
  }
}

module.exports = { androidChecks, sdkDir };

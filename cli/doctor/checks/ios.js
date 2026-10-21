// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { PACKAGE, installedVersion, packageDir } = require('../../app');

/** @typedef {import('../types').Check} Check */
/** @typedef {import('../types').Context} Context */

const PODS = { label: 'cd ios && pod install', run: /** @type {const} */ ('pods') };

// The oldest Xcode with the iOS SDK the navigation builds with.
const MIN_XCODE = 26;

/**
 * Xcode, CocoaPods, the pods of the installed navigation, and the oldest iOS the app runs on. macOS only.
 * @param {Context} context
 * @returns {Check[]}
 */
function iosChecks(context) {
  if (context.platform !== 'darwin') return [];
  const ios = path.join(context.root, 'ios');
  const podfile = read(path.join(ios, 'Podfile'));
  if (podfile === undefined) {
    return [{ group: 'ios', title: 'iOS project', status: 'info', detail: 'none in ios/' }];
  }
  return [
    xcodeCheck(context),
    cocoaPodsCheck(context),
    podsCheck(context.root, ios),
    deploymentTargetCheck(context.root, podfile),
  ];
}

/**
 * @param {Context} context
 * @returns {Check}
 */
function xcodeCheck({ run }) {
  const result = run('xcodebuild', ['-version']);
  if (!result.ok) {
    return {
      group: 'ios',
      title: 'Xcode',
      status: 'error',
      detail: 'not found',
      fix: { label: 'Install Xcode, then: sudo xcode-select -s /Applications/Xcode.app' },
    };
  }
  const version = result.stdout.split('\n')[0].replace(/^Xcode\s*/, '');
  // The navigation uses the iOS 26 SDK (Liquid Glass and the rest): an older Xcode doesn't build it.
  if (Number(version.split('.')[0]) < MIN_XCODE) {
    return {
      group: 'ios',
      title: 'Xcode',
      status: 'error',
      detail: `${version}: the navigation builds with Xcode ${MIN_XCODE} or newer`,
      fix: { label: `Install Xcode ${MIN_XCODE} or newer` },
    };
  }
  return { group: 'ios', title: 'Xcode', status: 'ok', detail: version };
}

/**
 * CocoaPods through Bundler when the app pins it in a Gemfile, as the React Native template does.
 * @param {Context} context
 * @returns {Check}
 */
function cocoaPodsCheck({ root, run }) {
  const bundler = fs.existsSync(path.join(root, 'Gemfile'));
  const result = bundler
    ? run('bundle', ['exec', 'pod', '--version'], { cwd: root })
    : run('pod', ['--version'], { cwd: root });
  if (result.ok)
    return { group: 'ios', title: 'CocoaPods', status: 'ok', detail: result.stdout.trim() };
  return {
    group: 'ios',
    title: 'CocoaPods',
    status: 'error',
    detail: 'not found',
    fix: { label: bundler ? 'bundle install' : 'gem install cocoapods' },
  };
}

/**
 * The pods are installed, and installed with the navigation the app has now.
 * @param {string} root
 * @param {string} ios
 * @returns {Check}
 */
function podsCheck(root, ios) {
  const lock = read(path.join(ios, 'Podfile.lock'));
  const manifest = read(path.join(ios, 'Pods', 'Manifest.lock'));
  if (lock === undefined || manifest === undefined) {
    return { group: 'ios', title: 'Pods', status: 'error', detail: 'not installed', fix: PODS };
  }
  const inPods = /^\s*- ApexNavigation \(([^)]+)\)/m.exec(lock)?.[1];
  const installed = installedVersion(root, PACKAGE);
  if (inPods && installed && inPods !== installed) {
    return {
      group: 'ios',
      title: 'Pods out of date',
      status: 'error',
      detail: `navigation ${inPods} in Pods, ${installed} installed`,
      fix: PODS,
    };
  }
  if (lock !== manifest) {
    return {
      group: 'ios',
      title: 'Pods out of date',
      status: 'error',
      detail: 'Podfile.lock changed',
      fix: PODS,
    };
  }
  return {
    group: 'ios',
    title: 'Pods',
    status: 'ok',
    detail: inPods ? `navigation ${inPods}` : undefined,
  };
}

/**
 * The iOS the Podfile targets is no older than React Native's oldest.
 * @param {string} root
 * @param {string} podfile
 * @returns {Check}
 */
function deploymentTargetCheck(root, podfile) {
  const platform = /^\s*platform\s+:ios\s*,\s*(.+)$/m.exec(podfile)?.[1]?.trim();
  if (!platform || platform.includes('min_ios_version_supported')) {
    return { group: 'ios', title: 'iOS version', status: 'ok', detail: "React Native's minimum" };
  }
  const version = /['"]([\d.]+)['"]/.exec(platform)?.[1];
  const minimum = reactNativeMinimumIOS(root);
  if (version && minimum && compare(version, minimum) < 0) {
    return {
      group: 'ios',
      title: 'iOS version',
      status: 'error',
      detail: `${version}: React Native needs ${minimum}`,
      fix: { label: 'platform :ios, min_ios_version_supported in the Podfile' },
    };
  }
  return { group: 'ios', title: 'iOS version', status: 'ok', detail: version };
}

/**
 * The oldest iOS React Native supports, from its CocoaPods scripts.
 * @param {string} root
 * @returns {string | undefined}
 */
function reactNativeMinimumIOS(root) {
  const reactNative = packageDir(root, 'react-native');
  if (!reactNative) return undefined;
  const helpers = read(path.join(reactNative, 'scripts', 'cocoapods', 'helpers.rb')) ?? '';
  return /def self\.min_ios_version_supported\s+return\s+'([\d.]+)'/.exec(helpers)?.[1];
}

/**
 * @param {string} a
 * @param {string} b
 */
function compare(a, b) {
  const [x, y] = [a, b].map((version) => version.split('.').map(Number));
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    if ((x[i] ?? 0) !== (y[i] ?? 0)) return (x[i] ?? 0) - (y[i] ?? 0);
  }
  return 0;
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

module.exports = { iosChecks };

// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { info } = require('..');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');

/** A copy of the app after setup, with React Native, the navigation, a lockfile, a Podfile and the Gradle wrapper. */
function app() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-info-'));
  fs.cpSync(FIXTURE, root, { recursive: true });
  const put = (/** @type {string} */ file, /** @type {string} */ text) => {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), text);
  };
  put('node_modules/react-native/package.json', '{ "version": "0.87.1" }');
  put('node_modules/@apexnative/navigation/package.json', '{ "version": "1.0.0" }');
  fs.symlinkSync(
    path.dirname(require.resolve('typescript/package.json')),
    path.join(root, 'node_modules', 'typescript')
  );
  put('yarn.lock', '');
  // An Android SDK with two platforms.
  put('sdk/platforms/android-36/package.xml', '');
  put('sdk/platforms/android-37.0/package.xml', '');
  put('ios/Podfile', "platform :ios, '15.1'\n");
  put(
    'android/gradle/wrapper/gradle-wrapper.properties',
    'distributionUrl=https\\://services.gradle.org/distributions/gradle-9.0.0-bin.zip\n'
  );
  return root;
}

/** The programs of a Mac set up for React Native. */
const run = (/** @type {string} */ command) => {
  /** @type {Record<string, string>} */
  const stdout = {
    sw_vers: '27.0\n',
    yarn: '4.9.1\n',
    xcodebuild: 'Xcode 27.0\nBuild version 27A266a\n',
    pod: '1.16.2\n',
  };
  const name = path.basename(command);
  if (name === 'java')
    return { ok: true, stdout: '', stderr: 'openjdk version "17.0.12" 2024-07-16' };
  return name in stdout
    ? { ok: true, stdout: stdout[name], stderr: '' }
    : { ok: false, stdout: '', stderr: '' };
};

/**
 * @param {string} root
 * @param {boolean} json
 */
async function print(root, json) {
  let text = '';
  const code = await info({
    root,
    json,
    run,
    env: { ANDROID_HOME: path.join(root, 'sdk') },
    platform: 'darwin',
    home: os.tmpdir(),
    write: (chunk) => (text += chunk),
  });
  return { code, text };
}

describe('info', () => {
  it('prints Markdown to paste into an issue', async () => {
    const { code, text } = await print(app(), false);
    expect(code).toBe(0);
    expect(text).toContain('- Node: ');
    expect(text).toContain(' · yarn 4.9.1\n');
    expect(text).toContain('- React Native: 0.87.1 · New Architecture\n');
    expect(text).toContain('- @apexnative/navigation: 1.0.0 · a React root per screen\n');
    expect(text).toContain('- iOS: Xcode 27.0 · CocoaPods 1.16.2 · deployment target 15.1\n');
    expect(text).toMatch(
      /- Android: JDK 17\.0\.12 · Android SDK 37\.0 · compileSdk \S+ · minSdk \S+ · targetSdk \S+ · Gradle 9\.0\.0\n/
    );
    expect(text).toMatch(/^- OS: macOS 27\.0 \(/);
  });

  it('prints one JSON document, with null for what it could not find', async () => {
    const root = app();
    fs.rmSync(path.join(root, 'yarn.lock'));
    const { code, text } = await print(root, true);
    expect(code).toBe(0);
    const result = JSON.parse(text);
    expect(result).toMatchObject({
      command: 'info',
      ok: true,
      info: {
        packageManager: null,
        reactNative: '0.87.1',
        newArchitecture: true,
        navigation: '1.0.0',
        mode: 'root-per-screen',
        expo: false,
        ios: { xcode: '27.0', cocoapods: '1.16.2', deploymentTarget: '15.1' },
      },
    });
  });

  it('fails outside an app', async () => {
    const { code, text } = await print(fs.mkdtempSync(path.join(os.tmpdir(), 'apex-none-')), true);
    expect(code).toBe(1);
    expect(JSON.parse(text).errors[0].code).toBe('app-missing');
  });
});

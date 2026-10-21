// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { doctor } = require('..');

const fixtures = path.join(__dirname, '..', '..', 'setup', '__fixtures__');

// The temporary folders of the tests, removed after them.
const created = /** @type {string[]} */ ([]);
afterAll(() => created.forEach((dir) => fs.rmSync(dir, { recursive: true, force: true })));

/**
 * A React Native app in a temporary folder: the template's files, set up or not, and the packages it has.
 * @param {{ setUp?: boolean; dependencies?: Record<string, string>; podsVersion?: string; edit?: (root: string) => void }} [options]
 */
function makeApp({ setUp = false, dependencies = {}, podsVersion = '1.0.0', edit } = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-doctor-'));
  created.push(root);
  const from = path.join(fixtures, setUp ? 'setup-done' : 'template-0.87');
  const put = (/** @type {string} */ file, /** @type {string} */ text) => {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), text);
  };
  const copy = (/** @type {string} */ name, /** @type {string} */ to) =>
    put(to, fs.readFileSync(path.join(from, name), 'utf8'));
  copy('MainApplication.kt', 'android/app/src/main/java/com/helloworld/MainApplication.kt');
  copy('MainActivity.kt', 'android/app/src/main/java/com/helloworld/MainActivity.kt');
  copy('styles.xml', 'android/app/src/main/res/values/styles.xml');
  copy('AndroidManifest.xml', 'android/app/src/main/AndroidManifest.xml');
  copy('AppDelegate.swift', 'ios/HelloWorld/AppDelegate.swift');
  copy('Info.plist', 'ios/HelloWorld/Info.plist');
  copy('index.js', 'index.js');
  if (setUp) copy('Navigation.ts', 'Navigation.ts');
  copy('App.tsx', 'App.tsx');
  put(
    'package.json',
    JSON.stringify({
      name: 'helloworld',
      dependencies: { 'react-native': '0.87.1', ...dependencies },
    })
  );
  put('node_modules/react-native/package.json', '{"name":"react-native","version":"0.87.1"}');
  put(
    'node_modules/@apexnative/navigation/package.json',
    '{"name":"@apexnative/navigation","version":"1.0.0"}'
  );
  put(
    'android/build.gradle',
    'buildscript {\n  ext {\n    buildToolsVersion = "37.0.0"\n    minSdkVersion = 24\n' +
      '    compileSdkVersion = 37\n    targetSdkVersion = 36\n    ndkVersion = "27.1.12297006"\n  }\n}\n'
  );
  put('android/gradle.properties', 'newArchEnabled=true\nedgeToEdgeEnabled=true\n');
  put('ios/Podfile', 'platform :ios, min_ios_version_supported\n');
  const lock = `PODS:\n  - ApexNavigation (${podsVersion}):\n    - hermes-engine\n`;
  put('ios/Podfile.lock', lock);
  put('ios/Pods/Manifest.lock', lock);
  edit?.(root);
  return root;
}

/** An Android SDK with what the app's build.gradle names, a platform folder with its minor version. */
function makeSdk({ ndk = true } = {}) {
  const sdk = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-sdk-'));
  created.push(sdk);
  fs.mkdirSync(path.join(sdk, 'platforms', 'android-37.0'), { recursive: true });
  fs.mkdirSync(path.join(sdk, 'build-tools', '37.0.0'), { recursive: true });
  if (ndk) fs.mkdirSync(path.join(sdk, 'ndk', '27.1.12297006'), { recursive: true });
  return sdk;
}

/**
 * The programs of a machine set up for React Native, with some of them answering otherwise.
 * @param {Record<string, { ok: boolean; stdout?: string; stderr?: string }>} [answers]
 * @returns {import('../../exec').Exec}
 */
function machine(answers = {}) {
  /** @type {Record<string, { ok: boolean; stdout?: string; stderr?: string }>} */
  const defaults = {
    java: { ok: true, stderr: 'openjdk version "17.0.12" 2024-07-16' },
    xcodebuild: { ok: true, stdout: 'Xcode 26.0\nBuild version 17A324' },
    pod: { ok: true, stdout: '1.16.2\n' },
    bundle: { ok: true, stdout: '1.16.2\n' },
    xcrun: { ok: true, stdout: '26.0\n' },
    adb: { ok: true, stdout: 'List of devices attached\n0A1B2C3D4E5F\tdevice\n\n' },
    '/usr/libexec/java_home': {
      ok: true,
      stdout: '/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home',
    },
  };
  return (command) => {
    const answer =
      answers[path.basename(command)] ??
      answers[command] ??
      defaults[path.basename(command)] ??
      defaults[command];
    return Object.assign({ ok: false, stdout: '', stderr: '' }, answer);
  };
}

/**
 * The checks of doctor as JSON, and its exit code.
 * @param {string} root
 * @param {{ run?: import('../../exec').Exec; sdk?: string }} [options]
 */
async function check(root, { run = machine(), sdk = makeSdk() } = {}) {
  let output = '';
  const code = await doctor({
    root,
    fix: false,
    json: true,
    run,
    env: { ANDROID_HOME: sdk },
    platform: 'darwin',
    home: os.tmpdir(),
    write: (text) => (output += text),
  });
  const report = JSON.parse(output);
  /** @param {string} title */
  const find = (title) => report.checks.find((/** @type {any} */ item) => item.title === title);
  return { code, report, find };
}

describe('doctor', () => {
  it('finds an app not set up yet, and setup as the fix', async () => {
    const { code, find } = await check(makeApp());
    expect(code).toBe(1);
    expect(find('Native files')).toMatchObject({ status: 'error', fix: { run: 'setup' } });
    expect(find('Entry point')).toMatchObject({ status: 'error', fix: { run: 'setup' } });
    expect(find('Status bar')).toMatchObject({
      status: 'warn',
      detail: "App.tsx renders React Native's StatusBar, the screens set it",
      fix: { run: 'setup' },
    });
  });

  it('passes an app set up on a machine set up', async () => {
    const { code, report } = await check(makeApp({ setUp: true }));
    expect(report.checks.filter((/** @type {any} */ item) => item.status === 'error')).toEqual([]);
    expect(report.ok).toBe(true);
    expect(code).toBe(0);
    expect(report.checks.find((/** @type {any} */ item) => item.title === 'Status bar')).toBe(
      undefined
    );
  });

  it('tells a JavaScript app that its screen names are plain strings', async () => {
    const { find } = await check(makeApp({ setUp: true }));
    expect(find('Typed commands')).toMatchObject({ status: 'info' });
  });

  it('finds the typed commands missing, with setup to make them', async () => {
    const typed = (/** @type {string} */ root) => {
      fs.writeFileSync(path.join(root, 'tsconfig.json'), '{}\n');
      fs.symlinkSync(
        path.dirname(require.resolve('typescript/package.json')),
        path.join(root, 'node_modules', 'typescript')
      );
    };
    const root = makeApp({ setUp: true, edit: typed });
    const { code, find } = await check(root);
    expect(find('Typed commands')).toMatchObject({ status: 'warn', fix: { run: 'setup' } });
    // A warning: the app builds and runs.
    expect(code).toBe(0);
    fs.writeFileSync(
      path.join(root, 'navigation-env.d.ts'),
      [
        "// Made by apex-navigation setup: the types of the app's screens",
        "// for the navigation's commands. Don't edit.",
        "declare module '@apexnative/navigation' {",
        '  interface Register {',
        "    app: typeof import('./Navigation').Navigation;",
        '  }',
        '}',
        'export {};',
        '',
      ].join('\n')
    );
    expect((await check(root)).find('Typed commands')).toMatchObject({ status: 'ok' });
  });

  it('finds pods of an older navigation', async () => {
    const { find } = await check(makeApp({ setUp: true, podsVersion: '0.9.0' }));
    expect(find('Pods out of date')).toMatchObject({
      status: 'error',
      detail: 'navigation 0.9.0 in Pods, 1.0.0 installed',
      fix: { run: 'pods' },
    });
  });

  it('finds React Native loaded twice, not a comment that mentions it', async () => {
    const file = 'android/app/src/main/java/com/helloworld/MainApplication.kt';
    const withCall = makeApp({
      setUp: true,
      edit: (root) =>
        fs.appendFileSync(
          path.join(root, file),
          '\nfun start(app: android.app.Application) { loadReactNative(app) }\n'
        ),
    });
    expect((await check(withCall)).find('React Native loads twice')).toMatchObject({
      status: 'error',
    });
    const withComment = makeApp({
      setUp: true,
      edit: (root) =>
        fs.appendFileSync(path.join(root, file), '\n// No loadReactNative(this) here.\n'),
    });
    const { find } = await check(withComment);
    expect(find('React Native loads twice')).toBeUndefined();
    expect(find('Native files')).toMatchObject({ status: 'ok' });
  });

  it('warns about predictive back, not an error', async () => {
    const template = fs.readFileSync(
      path.join(fixtures, 'template-0.87', 'AndroidManifest.xml'),
      'utf8'
    );
    const app = makeApp({
      setUp: true,
      edit: (root) =>
        fs.writeFileSync(path.join(root, 'android/app/src/main/AndroidManifest.xml'), template),
    });
    const { find } = await check(app);
    expect(find('Native files')).toMatchObject({ status: 'ok' });
    expect(find('Back animation')).toMatchObject({ status: 'warn', fix: { run: 'setup' } });
  });

  it("doesn't need the app's theme", async () => {
    const noTheme = makeApp({
      setUp: true,
      edit: (root) => fs.rmSync(path.join(root, 'android/app/src/main/res/values/styles.xml')),
    });
    const { find } = await check(noTheme);
    expect(find('Native files')).toMatchObject({ status: 'ok' });
  });

  it('tells JDK 17 installed but not in use from no JDK 17 at all', async () => {
    const app = makeApp({ setUp: true });
    const jdk18 = { java: { ok: true, stderr: 'java version "18.0.2"' } };
    expect((await check(app, { run: machine(jdk18) })).find('JDK 17')).toMatchObject({
      status: 'warn',
    });
    const none = machine({ java: { ok: false }, '/usr/libexec/java_home': { ok: false } });
    expect((await check(app, { run: none })).find('JDK 17')).toMatchObject({ status: 'error' });
  });

  it('needs Xcode 26 or newer', async () => {
    const app = makeApp({ setUp: true });
    const xcode16 = { xcodebuild: { ok: true, stdout: 'Xcode 16.4\nBuild version 16F6' } };
    expect((await check(app, { run: machine(xcode16) })).find('Xcode')).toMatchObject({
      status: 'error',
      detail: '16.4: the navigation builds with Xcode 26 or newer',
    });
    const xcode27 = { xcodebuild: { ok: true, stdout: 'Xcode 27.0\nBuild version 18A100' } };
    expect((await check(app, { run: machine(xcode27) })).find('Xcode')).toMatchObject({
      status: 'ok',
      detail: '27.0',
    });
  });

  it('finds a simulator of the iOS of Xcode and the devices adb sees', async () => {
    const runtimes = (/** @type {string} */ version) => ({
      ok: true,
      stdout: JSON.stringify({
        runtimes: [{ name: `iOS ${version}`, version, platform: 'iOS', isAvailable: true }],
      }),
      stderr: '',
    });
    /** @type {import('../../exec').Exec} */
    const withRuntime = (command, args) =>
      command === 'xcrun' && args[0] === 'simctl' ? runtimes('26.5') : machine()(command, args);
    const { find } = await check(makeApp({ setUp: true }), { run: withRuntime });
    expect(find('iOS simulator')).toMatchObject({ status: 'ok', detail: 'iOS 26.5' });
    expect(find('adb')).toMatchObject({ status: 'ok', detail: '1 device connected' });

    /** @type {import('../../exec').Exec} */
    const olderRuntime = (command, args) =>
      command === 'xcrun' && args[0] === 'simctl'
        ? runtimes('18.6')
        : machine({ adb: { ok: false } })(command, args);
    const older = await check(makeApp({ setUp: true }), { run: olderRuntime });
    expect(older.find('iOS simulator')).toMatchObject({ status: 'warn' });
    expect(older.find('adb')).toMatchObject({ status: 'warn', detail: 'not found' });
  });

  it('takes JDK 17 installed as enough where scripts pick it per command', async () => {
    const { collect } = require('..');
    const jdk18 = machine({ java: { ok: true, stderr: 'java version "18.0.2"' } });
    const context = {
      root: makeApp({ setUp: true }),
      run: jdk18,
      env: { ANDROID_HOME: makeSdk() },
      platform: /** @type {NodeJS.Platform} */ ('darwin'),
      home: os.tmpdir(),
    };
    const jdkOf = (/** @type {any} */ extra) =>
      collect({ ...context, ...extra }).find((item) => item.title === 'JDK 17');
    expect(jdkOf({})).toMatchObject({ status: 'warn' });
    expect(jdkOf({ jdk: 'installed' })).toMatchObject({
      status: 'ok',
      detail: 'scripts/android-env.sh uses it',
    });
  });

  it('names the SDK packages to install', async () => {
    const { find } = await check(makeApp({ setUp: true }), { sdk: makeSdk({ ndk: false }) });
    expect(find('Android SDK')).toMatchObject({
      status: 'error',
      detail: 'missing ndk;27.1.12297006',
      fix: { label: 'sdkmanager "ndk;27.1.12297006"' },
    });
  });

  it('warns of another navigation and of Expo', async () => {
    const { find } = await check(
      makeApp({
        setUp: true,
        dependencies: { '@react-navigation/native': '7.0.0', expo: '55.0.0' },
      })
    );
    expect(find('Another navigation')).toMatchObject({ status: 'warn' });
    expect(find('Expo')).toMatchObject({ status: 'warn' });
    expect(find('Native files')).toMatchObject({ status: 'info' });
  });

  it("doesn't ask questions while printing JSON", async () => {
    const errors = /** @type {string[]} */ ([]);
    const ui = /** @type {any} */ ({ error: (/** @type {string} */ text) => errors.push(text) });
    expect(await doctor({ root: makeApp(), fix: true, json: true, ui })).toBe(1);
    expect(errors).toEqual(['--fix asks before each fix: run it without --json.']);
  });
});

// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { setup } = require('..');
const { createUI } = require('../../ui');

const TEMPLATE = path.join(__dirname, '..', '__fixtures__', 'template-0.87');

/**
 * React Native's template as its init makes it, with a Podfile, React Native installed and, if given, the pods.
 * @param {{ lock?: string }} [options] `lock`: the Podfile.lock the app has
 */
function app({ lock } = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-setup-'));
  const copy = (/** @type {string} */ name, /** @type {string} */ to) => {
    fs.mkdirSync(path.dirname(path.join(root, to)), { recursive: true });
    fs.copyFileSync(path.join(TEMPLATE, name), path.join(root, to));
  };
  copy('MainApplication.kt', 'android/app/src/main/java/com/myapp/MainApplication.kt');
  copy('MainActivity.kt', 'android/app/src/main/java/com/myapp/MainActivity.kt');
  copy('AppDelegate.swift', 'ios/MyApp/AppDelegate.swift');
  copy('Info.plist', 'ios/MyApp/Info.plist');
  copy('index.js', 'index.js');
  fs.writeFileSync(path.join(root, 'ios', 'Podfile'), "platform :ios, '15.1'\n");
  if (lock !== undefined) fs.writeFileSync(path.join(root, 'ios', 'Podfile.lock'), lock);
  fs.mkdirSync(path.join(root, 'node_modules', 'react-native'), { recursive: true });
  fs.writeFileSync(
    path.join(root, 'node_modules', 'react-native', 'package.json'),
    '{ "version": "0.87.1" }'
  );
  fs.writeFileSync(path.join(root, 'package.json'), '{ "name": "MyApp" }\n');
  return root;
}

/**
 * Runs setup with --json --yes; `pods` says whether pod install succeeds.
 * @param {string} root
 * @param {{ platform?: NodeJS.Platform; skipPods?: boolean; pods?: boolean }} [options]
 */
async function run(root, { platform = 'darwin', skipPods = false, pods = true } = {}) {
  /** @type {string[]} */
  const calls = [];
  let text = '';
  const out = {
    isTTY: false,
    columns: 80,
    write: (/** @type {string} */ chunk) => (text += chunk),
  };
  const original = process.stdout.write;
  process.stdout.write = /** @type {any} */ (
    (/** @type {string} */ chunk) => {
      text += chunk;
      return true;
    }
  );
  try {
    const code = await setup({
      root,
      yes: true,
      dryRun: false,
      skipPods,
      json: true,
      ui: createUI({ out, level: 'none' }),
      platform,
      run: (program, args, options = {}) => {
        calls.push(
          `${[program, ...args].join(' ')} in ${path.relative(root, options.cwd ?? root)}`
        );
        return {
          ok: program !== 'pod' || pods,
          stdout: '',
          stderr: pods ? '' : 'CocoaPods failed',
        };
      },
    });
    return { code, result: JSON.parse(text), calls };
  } finally {
    process.stdout.write = original;
  }
}

describe('setup and the pods', () => {
  it('installs the pods on a Mac once it has written the files', async () => {
    const { code, result, calls } = await run(app());
    expect(code).toBe(0);
    expect(result.applied).toBe(true);
    expect(calls).toContain('pod install in ios');
    expect(result.next).toEqual(['npx react-native run-ios · run-android']);
  });

  it('installs them through Bundler when the app has a Gemfile', async () => {
    const root = app();
    fs.writeFileSync(path.join(root, 'Gemfile'), "source 'https://rubygems.org'\n");
    const { calls } = await run(root);
    expect(calls).toEqual(
      expect.arrayContaining(['bundle install in ios', 'bundle exec pod install in ios'])
    );
  });

  it('leaves them for later with --skip-pods', async () => {
    const { code, result, calls } = await run(app(), { skipPods: true });
    expect(code).toBe(0);
    expect(calls.filter((call) => call.includes('pod'))).toEqual([]);
    expect(result.next[0]).toBe('cd ios && pod install && cd ..');
  });

  it('says to run them on a Mac elsewhere', async () => {
    const { code, result, calls } = await run(app(), { platform: 'linux' });
    expect(code).toBe(0);
    expect(calls.filter((call) => call.includes('pod'))).toEqual([]);
    expect(result.next[0]).toBe('Run pod install in ios/ on a Mac.');
  });

  it('ends with 1 when pod install fails, with the end of its output', async () => {
    const { code, result } = await run(app(), { pods: false });
    expect(code).toBe(1);
    expect(result.errors).toEqual([
      { code: 'pods-failed', message: 'cd ios && pod install && cd .. failed:\nCocoaPods failed' },
    ]);
    expect(result.next[0]).toBe('cd ios && pod install && cd ..');
  });

  it("doesn't install them when nothing of iOS changed and they have the navigation", async () => {
    const root = app({ lock: 'PODS:\n  - ApexNavigation (1.0.0)\n' });
    // The iOS files set up already: setup changes only JavaScript.
    const done = path.join(__dirname, '..', '__fixtures__', 'setup-done');
    fs.copyFileSync(
      path.join(done, 'AppDelegate.swift'),
      path.join(root, 'ios', 'MyApp', 'AppDelegate.swift')
    );
    fs.copyFileSync(path.join(done, 'Info.plist'), path.join(root, 'ios', 'MyApp', 'Info.plist'));
    const { result, calls } = await run(root);
    expect(result.applied).toBe(true);
    expect(calls.filter((call) => call.includes('pod'))).toEqual([]);
  });
});

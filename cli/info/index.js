// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { PACKAGE, installedVersion, isSupported, reactNativeVersion, usesExpo } = require('../app');
const { sdkDir } = require('../doctor/checks/android');
const { exec } = require('../exec');
const { findProject } = require('../project');
const { findConfig, loadTypeScript, navigationModule } = require('../typescript');

/**
 * What the app is made with and what builds it, for an issue: each value as its program or file says it, `null` where
 * the CLI couldn't find it; `android.sdk` is the newest SDK platform installed. `mode`: `one-tree` when the app renders every screen in one React tree (`appProvider`),
 * `root-per-screen` otherwise.
 * @typedef {{
 *   os: string;
 *   node: string;
 *   packageManager: string | null;
 *   reactNative: string | null;
 *   newArchitecture: boolean;
 *   navigation: string | null;
 *   mode: 'one-tree' | 'root-per-screen' | null;
 *   expo: boolean;
 *   ios: { xcode: string | null; cocoapods: string | null; deploymentTarget: string | null } | null;
 *   android: {
 *     jdk: string | null;
 *     sdk: string | null;
 *     compileSdk: string | null;
 *     minSdk: string | null;
 *     targetSdk: string | null;
 *     gradle: string | null;
 *   } | null;
 * }} Info
 */

/**
 * Prints what the app is made with and what builds it: Markdown to paste into an issue, or with `json` one JSON
 * document. Changes nothing.
 * @param {{
 *   root: string;
 *   json: boolean;
 *   run?: import('../exec').Exec;
 *   env?: NodeJS.ProcessEnv;
 *   platform?: NodeJS.Platform;
 *   home?: string;
 *   write?: (text: string) => void;
 * }} options
 * @returns {Promise<number>} the exit code: 1 outside an app
 */
async function info({
  root,
  json,
  run = exec,
  env = process.env,
  platform = process.platform,
  home = os.homedir(),
  write = (text) => process.stdout.write(text),
}) {
  if (!fs.existsSync(path.join(root, 'package.json'))) {
    const message = 'No package.json here: run the command in the root of your app.';
    if (json) {
      write(
        JSON.stringify(
          { command: 'info', ok: false, errors: [{ code: 'app-missing', message }] },
          null,
          2
        ) + '\n'
      );
    } else {
      process.stderr.write(message + '\n');
    }
    return 1;
  }
  const found = collect({ root, run, env, platform, home });
  if (json) {
    write(JSON.stringify({ command: 'info', ok: true, info: found }, null, 2) + '\n');
  } else {
    write(markdown(found));
  }
  return 0;
}

/**
 * @param {{
 *   root: string;
 *   run: import('../exec').Exec;
 *   env: NodeJS.ProcessEnv;
 *   platform: NodeJS.Platform;
 *   home: string;
 * }} context
 * @returns {Info}
 */
function collect({ root, run, env, platform, home }) {
  const reactNative = reactNativeVersion(root) ?? null;
  // The React Native CLI takes a few seconds to say where the app's files are: asked once.
  const project = findProject(root, run);
  const ios = path.join(root, 'ios');
  const android = path.join(root, 'android');
  return {
    os: system(run, platform),
    node: process.versions.node,
    packageManager: packageManager(root, run),
    reactNative,
    // The navigation runs on the New Architecture only, the one React Native has had alone since 0.82.
    newArchitecture: reactNative !== null && isSupported(reactNative),
    navigation: installedVersion(root, PACKAGE) ?? null,
    mode: mode(project),
    expo: usesExpo(root),
    ios:
      platform === 'darwin' && fs.existsSync(path.join(ios, 'Podfile'))
        ? {
            xcode: firstLine(run('xcodebuild', ['-version']))?.replace(/^Xcode\s*/, '') ?? null,
            cocoapods: firstLine(
              fs.existsSync(path.join(root, 'Gemfile'))
                ? run('bundle', ['exec', 'pod', '--version'], { cwd: root })
                : run('pod', ['--version'], { cwd: root })
            ),
            deploymentTarget: deploymentTarget(project),
          }
        : null,
    android: fs.existsSync(path.join(android, 'build.gradle'))
      ? androidInfo({ root, run, env, platform, home }, android)
      : null,
  };
}

/**
 * The system and its processor, such as `macOS 27.0 (arm64)`.
 * @param {import('../exec').Exec} run
 * @param {NodeJS.Platform} platform
 */
function system(run, platform) {
  if (platform === 'darwin') {
    const version = firstLine(run('sw_vers', ['-productVersion']));
    return `macOS ${version ?? os.release()} (${os.arch()})`;
  }
  return `${platform === 'win32' ? 'Windows' : os.type()} ${os.release()} (${os.arch()})`;
}

/**
 * The package manager of the app's lock file, with the version that runs here.
 * @param {string} root
 * @param {import('../exec').Exec} run
 * @returns {string | null}
 */
function packageManager(root, run) {
  const managers = /** @type {const} */ ([
    ['yarn.lock', 'yarn'],
    ['pnpm-lock.yaml', 'pnpm'],
    ['bun.lock', 'bun'],
    ['bun.lockb', 'bun'],
    ['package-lock.json', 'npm'],
  ]);
  const manager = managers.find(([lock]) => fs.existsSync(path.join(root, lock)))?.[1];
  if (!manager) return null;
  const version = firstLine(run(manager, ['--version'], { cwd: root }));
  return version ? `${manager} ${version}` : manager;
}

/**
 * How the app renders its screens, from `appProvider` of `createNavigation`; null when the CLI can't read the app's
 * code.
 * @param {import('../project').Project} project
 * @returns {Info['mode']}
 */
function mode(project) {
  const ts = loadTypeScript(project.root);
  const entry = project.entry;
  const navigation = ts && entry && navigationModule(ts, entry);
  if (!ts || !navigation) return null;
  return findConfig(ts, navigation, 'appProvider') ? 'one-tree' : 'root-per-screen';
}

/**
 * The oldest iOS the app runs on: the app target's in the Xcode project.
 * @param {import('../project').Project} project
 * @returns {string | null}
 */
function deploymentTarget(project) {
  const text = project.xcodeProject ? read(project.xcodeProject) : undefined;
  return (text && /IPHONEOS_DEPLOYMENT_TARGET = ([\d.]+);/.exec(text)?.[1]) ?? null;
}

/**
 * @param {{
 *   root: string;
 *   run: import('../exec').Exec;
 *   env: NodeJS.ProcessEnv;
 *   platform: NodeJS.Platform;
 *   home: string;
 * }} context
 * @param {string} android the app's android/ folder
 * @returns {Info['android']}
 */
function androidInfo(context, android) {
  const gradle = read(path.join(android, 'build.gradle')) ?? '';
  const ext = (/** @type {string} */ name) =>
    new RegExp(String.raw`\b${name}\s*=\s*"?([\w.]+)"?`).exec(gradle)?.[1] ?? null;
  const java = context.env.JAVA_HOME ? path.join(context.env.JAVA_HOME, 'bin', 'java') : 'java';
  const javaResult = context.run(java, ['-version']);
  // `java -version` prints to stderr: `openjdk version "17.0.12" …`.
  const jdk = javaResult.ok
    ? (/version "([^"]+)"/.exec(javaResult.stderr || javaResult.stdout)?.[1] ?? null)
    : null;
  const sdk = sdkDir(context, android);
  const wrapper = read(path.join(android, 'gradle', 'wrapper', 'gradle-wrapper.properties')) ?? '';
  return {
    jdk,
    sdk: newestPlatform(sdk),
    compileSdk: ext('compileSdkVersion'),
    minSdk: ext('minSdkVersion'),
    targetSdk: ext('targetSdkVersion'),
    gradle: /gradle-([\d.]+)-(?:bin|all)\.zip/.exec(wrapper)?.[1] ?? null,
  };
}

/**
 * The newest SDK platform installed, such as `37` or `36.1`; null without the SDK.
 * @param {string} sdk the folder of the Android SDK
 * @returns {string | null}
 */
function newestPlatform(sdk) {
  let names;
  try {
    names = fs.readdirSync(path.join(sdk, 'platforms'));
  } catch {
    return null;
  }
  const versions = names.flatMap((name) => /^android-([\d.]+)$/.exec(name)?.[1] ?? []);
  versions.sort((a, b) => Number(a) - Number(b));
  return versions.at(-1) ?? null;
}

/**
 * The lines to paste into an issue.
 * @param {Info} found
 * @returns {string}
 */
function markdown(found) {
  const or = (/** @type {string | null} */ value) => value ?? 'not found';
  const lines = [
    `- OS: ${found.os}`,
    `- Node: ${found.node}${found.packageManager ? ` · ${found.packageManager}` : ''}`,
    `- React Native: ${or(found.reactNative)}${found.newArchitecture ? ' · New Architecture' : ''}`,
    `- ${PACKAGE}: ${or(found.navigation)}${
      found.mode
        ? ` · ${found.mode === 'one-tree' ? 'one React tree' : 'a React root per screen'}`
        : ''
    }`,
    `- Expo: ${found.expo ? 'yes' : 'no'}`,
  ];
  if (found.ios) {
    lines.push(
      `- iOS: Xcode ${or(found.ios.xcode)} · CocoaPods ${or(found.ios.cocoapods)} · deployment target ${or(found.ios.deploymentTarget)}`
    );
  }
  if (found.android) {
    const { jdk, sdk, compileSdk, minSdk, targetSdk, gradle } = found.android;
    lines.push(
      `- Android: JDK ${or(jdk)} · Android SDK ${or(sdk)} · compileSdk ${or(compileSdk)} · minSdk ${or(minSdk)} · targetSdk ${or(targetSdk)} · Gradle ${or(gradle)}`
    );
  }
  return lines.join('\n') + '\n';
}

/**
 * The first line a program printed, when it ran.
 * @param {{ ok: boolean; stdout: string }} result
 * @returns {string | null}
 */
function firstLine(result) {
  const line = result.ok ? result.stdout.trim().split('\n')[0]?.trim() : '';
  return line || null;
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

module.exports = { info, collect, markdown };

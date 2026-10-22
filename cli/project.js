// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { exec } = require('./exec');

/**
 * The files of the app the CLI sets up, checks and adds to, where the app has them; `undefined` for one it couldn't
 * find. `styles` is the file of the app's Android theme, `styles.xml` or `themes.xml`; `manifest` its
 * `AndroidManifest.xml`. `sceneDelegate` is the `SceneDelegate.swift` next to `AppDelegate.swift`, which apps made
 * from React Native 0.88's template have. `xcodeProject` is the app's `project.pbxproj`; `androidPackage` the package
 * of its Kotlin code (the `namespace` of `android/app/build.gradle`), and `androidSources` the folder it is in.
 * @typedef {{
 *   root: string;
 *   mainApplication?: string;
 *   mainActivity?: string;
 *   styles?: string;
 *   manifest?: string;
 *   appDelegate?: string;
 *   sceneDelegate?: string;
 *   infoPlist?: string;
 *   entry?: string;
 *   app?: string;
 *   typescript: boolean;
 *   xcodeProject?: string;
 *   androidPackage?: string;
 *   androidSources?: string;
 *   appBuildGradle?: string;
 *   rootBuildGradle?: string;
 * }} Project
 */

// Folders the search never goes into.
const SKIP = new Set(['node_modules', 'Pods', 'build', 'DerivedData', '.gradle', '.git']);

/**
 * The app's files, at the paths the React Native CLI reports for its native projects (so a monorepo works too), or
 * at the template's paths without the CLI.
 * @param {string} root
 * @param {import('./exec').Exec} [run]
 * @returns {Project}
 */
function findProject(root, run = exec) {
  const config = reactNativeConfig(root, run);
  const android = config?.project?.android;
  const ios = config?.project?.ios;

  const androidDir = android?.sourceDir ?? path.join(root, 'android');
  const appDir = path.join(androidDir, android?.appName ?? 'app');
  const androidMain = path.join(appDir, 'src', 'main');
  const iosDir = ios?.sourceDir ?? path.join(root, 'ios');
  const xcodeName = ios?.xcodeProject?.name?.replace(/\.(xcworkspace|xcodeproj)$/, '');
  const appDelegate =
    existing(xcodeName && path.join(iosDir, xcodeName, 'AppDelegate.swift')) ??
    findFile(iosDir, 'AppDelegate.swift', 2);
  const mainApplication = findFile(androidMain, 'MainApplication.kt', 12);
  const appBuildGradle = gradleFile(appDir);

  return {
    root,
    mainApplication,
    mainActivity: findFile(androidMain, 'MainActivity.kt', 12),
    styles:
      existing(path.join(androidMain, 'res', 'values', 'styles.xml')) ??
      existing(path.join(androidMain, 'res', 'values', 'themes.xml')),
    manifest: existing(path.join(androidMain, 'AndroidManifest.xml')),
    appDelegate,
    sceneDelegate:
      appDelegate && existing(path.join(path.dirname(appDelegate), 'SceneDelegate.swift')),
    infoPlist: appDelegate && existing(path.join(path.dirname(appDelegate), 'Info.plist')),
    entry: ['index.js', 'index.ts', 'index.tsx']
      .map((name) => path.join(root, name))
      .find((file) => fs.existsSync(file)),
    app: ['App.tsx', 'App.ts', 'App.jsx', 'App.js']
      .map((name) => path.join(root, name))
      .find((file) => fs.existsSync(file)),
    typescript: ['App.tsx', 'App.ts', 'tsconfig.json'].some((name) =>
      fs.existsSync(path.join(root, name))
    ),
    xcodeProject: xcodeProjectFile(iosDir, xcodeName),
    androidPackage: androidPackage(appBuildGradle, mainApplication),
    androidSources:
      existing(path.join(androidMain, 'java')) ?? existing(path.join(androidMain, 'kotlin')),
    appBuildGradle,
    rootBuildGradle: gradleFile(androidDir),
  };
}

/**
 * The `project.pbxproj` of the app's Xcode project: the one the React Native CLI names, or the first in `ios/`.
 * @param {string} iosDir
 * @param {string | undefined} xcodeName
 * @returns {string | undefined}
 */
function xcodeProjectFile(iosDir, xcodeName) {
  const named = existing(
    xcodeName && path.join(iosDir, `${xcodeName}.xcodeproj`, 'project.pbxproj')
  );
  if (named) return named;
  let entries;
  try {
    entries = fs.readdirSync(iosDir);
  } catch {
    return undefined;
  }
  const project = entries.find((name) => name.endsWith('.xcodeproj') && name !== 'Pods.xcodeproj');
  return project && existing(path.join(iosDir, project, 'project.pbxproj'));
}

/**
 * The build file of a Gradle project, Groovy or Kotlin.
 * @param {string} dir
 * @returns {string | undefined}
 */
function gradleFile(dir) {
  return existing(path.join(dir, 'build.gradle')) ?? existing(path.join(dir, 'build.gradle.kts'));
}

/**
 * The package of the app's code: the `namespace` of its build file, else the package of MainApplication.kt.
 * @param {string | undefined} buildGradle
 * @param {string | undefined} mainApplication
 * @returns {string | undefined}
 */
function androidPackage(buildGradle, mainApplication) {
  const namespace = buildGradle && /\bnamespace\s*=?\s*["']([\w.]+)["']/.exec(read(buildGradle));
  if (namespace) return namespace[1];
  const declared = mainApplication && /^\s*package\s+([\w.]+)/m.exec(read(mainApplication));
  return declared ? declared[1] : undefined;
}

/**
 * @param {string} file
 * @returns {string}
 */
function read(file) {
  return fs.readFileSync(file, 'utf8');
}

/**
 * The React Native CLI's view of the app (`react-native config`); undefined without the CLI.
 * @param {string} root
 * @param {import('./exec').Exec} run
 * @returns {any}
 */
function reactNativeConfig(root, run) {
  const result = run('npx', ['--no-install', 'react-native', 'config'], { cwd: root });
  const output = result.ok ? result.stdout : '';
  const start = output.indexOf('{');
  if (start < 0) return undefined;
  try {
    return JSON.parse(output.slice(start));
  } catch {
    return undefined;
  }
}

/**
 * @param {string | undefined} file
 * @returns {string | undefined}
 */
function existing(file) {
  return file && fs.existsSync(file) ? file : undefined;
}

/**
 * The first file with the name under the folder, at most `depth` folders down.
 * @param {string} dir
 * @param {string} name
 * @param {number} depth
 * @returns {string | undefined}
 */
function findFile(dir, name, depth) {
  /** @type {fs.Dirent[]} */
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return undefined;
  }
  const file = entries.find((entry) => entry.isFile() && entry.name === name);
  if (file) return path.join(dir, file.name);
  if (depth === 0) return undefined;
  for (const entry of entries) {
    if (!entry.isDirectory() || SKIP.has(entry.name) || entry.name.endsWith('.xcodeproj')) continue;
    const found = findFile(path.join(dir, entry.name), name, depth - 1);
    if (found) return found;
  }
  return undefined;
}

module.exports = { findProject };

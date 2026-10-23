// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { withReactNativeLoaded } = require('../setup/steps/mainApplication');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('./report').ReportItem} ReportItem */

const DOCS = 'from-react-native-navigation';

/**
 * A rewrite of a file: each rule a pattern and what takes its place (`''` with a whole line drops the line), and the
 * places to report, which no rule can change.
 * @typedef {{
 *   rules: [RegExp, string][];
 *   report?: { pattern: RegExp; what: string; after: string; step: string }[];
 * }} Rewrite
 */

// Kotlin and Java: the package of the old library is ours, but for the package class, which the navigation links by
// itself (a second one would register it twice) and whose `react.` import never existed.
const ANDROID_CODE = {
  rules: /** @type {[RegExp, string][]} */ ([
    [/^[ \t]*import com\.reactnativenavigation(?:\.react)?\.NavigationPackage;?[ \t]*\n/gm, ''],
    [/^[ \t]*(?:packages\.)?add\(\s*(?:new\s+)?NavigationPackage\(\)\s*\);?[ \t]*\n/gm, ''],
    [/^[ \t]*RNNToggles\.TAB_BAR_TRANSLUCENCE\s+to\s+\w+,?[ \t]*\n/gm, ''],
    [/\bcom\.reactnativenavigation\./g, 'dev.apexnative.navigation.'],
    [/\bRNNToggles\b/g, 'ANNToggles'],
    [/\bRNNFeatureToggles\b/g, 'ANNFeatureToggles'],
    [/\bgetExternalComponents\(\)/g, 'getNativeScreens()'],
  ]),
  report: [
    {
      pattern: /\bonCatalystInstanceDestroy\b/,
      what: 'NavigationActivity has no onCatalystInstanceDestroy',
      after: 'the code that ran there goes to onDestroy, or goes',
      step: 'native',
    },
  ],
};

// The root build.gradle: the Kotlin versions the old library read; settings and the app's build: links of its
// installs before version 5.
const GRADLE = {
  rules: /** @type {[RegExp, string][]} */ ([
    [/^[ \t]*RNNKotlin(?:Version|Stdlib|CoroutinesCore)\s*=.*\n/gm, ''],
    [/^[ \t]*include\s*['"]:react-native-navigation['"].*\n/gm, ''],
    [/^[ \t]*project\(\s*['"]:react-native-navigation['"]\s*\)\.projectDir.*\n/gm, ''],
    [/^[ \t]*implementation\s+project\(\s*['"]:react-native-navigation['"]\s*\).*\n/gm, ''],
    [/^[ \t]*missingDimensionStrategy\s+["']RNN\.reactNativeVersion["'].*\n/gm, ''],
  ]),
};

// Swift: the module and the app delegate.
const SWIFT = {
  rules: /** @type {[RegExp, string][]} */ ([
    [/\bimport ReactNativeNavigation\b/g, 'import ApexNavigation'],
    [/\bRNNAppDelegate\b/g, 'ANNAppDelegate'],
    [/\bReactNativeNavigation\.(?=\w)/g, 'ApexNavigation.'],
  ]),
  report: [
    {
      pattern: /\bself\.(?:moduleName|initialProps|dependencyProvider)\b/,
      what: 'the app delegate of React Native 0.77–0.78',
      after:
        'self.reactNativeDelegate = ReactNativeDelegate() before super.application(…), as setup writes it',
      step: 'native',
    },
    {
      pattern: /\bRNN_RN_VERSION_\w+/,
      what: 'a version macro of the old library',
      after: 'the code of React Native 0.79 and newer alone',
      step: 'native',
    },
    {
      pattern: /\bregisterExternalComponent\(/,
      what: 'registerExternalComponent is gone',
      after: 'ApexNavigation.registerNativeScreen("Name") { context in … }',
      step: 'native-screens',
    },
  ],
};

// Objective-C: its headers, the app delegate and the class of the library.
const OBJC = {
  rules: /** @type {[RegExp, string][]} */ ([
    [
      /#import\s+["<](?:ReactNativeNavigation\/)?RNNAppDelegate\.h[">]/g,
      '#import <ApexNavigation/ANNAppDelegate.h>',
    ],
    [
      /#import\s+<ReactNativeNavigation\/ReactNativeNavigation\.h>/g,
      '#import <ApexNavigation/ApexNavigation.h>',
    ],
    [/\bRNNAppDelegate\b/g, 'ANNAppDelegate'],
    [/\[ReactNativeNavigation\s/g, '[ApexNavigation '],
  ]),
  report: [
    {
      pattern: /\bregisterExternalComponent:/,
      what: 'registerExternalComponent: is gone',
      after:
        '[ApexNavigation registerNativeScreen:creator:], or registerExternalHostComponent:callback:',
      step: 'native-screens',
    },
    ...SWIFT.report,
  ],
};

// The Podfile: the pod of an old install, and a dependency of the old library alone.
const PODFILE = {
  rules: /** @type {[RegExp, string][]} */ ([
    [/^[ \t]*pod\s+['"]ReactNativeNavigation['"].*\n/gm, ''],
    [/^[ \t]*pod\s+['"]HMSegmentedControl['"].*\n/gm, ''],
  ]),
};

/**
 * The native and build files of an app on react-native-navigation, rewritten for the navigation, with the places no
 * rewrite can change.
 * @param {import('../project').Project} project
 * @returns {{ changes: FileChange[]; report: ReportItem[] }}
 */
function planNative(project) {
  const root = project.root;
  /** @type {[string | undefined, Rewrite, string][]} */
  const files = [
    // MainApplication, MainActivity, and any other Kotlin or Java of the app that uses the old library.
    ...androidCode(project).map(
      (file) => /** @type {[string, Rewrite, string]} */ ([file, ANDROID_CODE, 'android'])
    ),
    [project.rootBuildGradle, GRADLE, 'android'],
    [project.appBuildGradle, GRADLE, 'android'],
    [existing(path.join(root, 'android', 'settings.gradle')), GRADLE, 'android'],
    [existing(path.join(root, 'ios', 'Podfile')), PODFILE, 'ios'],
  ];
  // The app's own code next to its AppDelegate: Swift, and Objective-C of an older app.
  // Found by its AppDelegate.swift; an app in Objective-C, by the name of its Xcode project.
  const xcodeName =
    project.xcodeProject && path.basename(path.dirname(project.xcodeProject), '.xcodeproj');
  const iosDir = project.appDelegate
    ? path.dirname(project.appDelegate)
    : xcodeName && existing(path.join(root, 'ios', xcodeName));
  if (iosDir && fs.existsSync(iosDir)) {
    for (const name of fs.readdirSync(iosDir)) {
      const file = path.join(iosDir, name);
      if (name.endsWith('.swift')) files.push([file, SWIFT, 'ios']);
      else if (/\.(h|m|mm)$/.test(name)) files.push([file, OBJC, 'ios']);
    }
  }
  /** @type {FileChange[]} */
  const changes = [];
  /** @type {ReportItem[]} */
  const report = [];
  for (const [file, rewrite, platform] of files) {
    if (!file || !fs.existsSync(file)) continue;
    const before = fs.readFileSync(file, 'utf8');
    let next = before;
    for (const [pattern, replacement] of rewrite.rules) next = next.replace(pattern, replacement);
    // react-native-navigation loaded React Native itself; the navigation's application leaves it to the template's
    // loadReactNative(this), which such an app doesn't have.
    if (file === project.mainApplication && /:\s*NavigationApplication\(/.test(next)) {
      const loaded = withReactNativeLoaded(next);
      if (loaded !== undefined) next = loaded;
      else {
        report.push({
          file: path.relative(root, file),
          line: 1,
          step: 'native',
          before: 'onCreate',
          after: 'super.onCreate() then loadReactNative(this), and no load of its own',
          what: 'React Native loads through loadReactNative(this) in onCreate: add it by hand',
          docs: `${DOCS}#native`,
        });
      }
    }
    if (next !== before) {
      changes.push({
        name: path.basename(file),
        platform,
        file,
        status: 'change',
        before,
        next,
        note: 'react-native-navigation → the navigation',
      });
    }
    for (const item of rewrite.report ?? []) {
      next.split('\n').forEach((line, index) => {
        if (!item.pattern.test(line)) return;
        report.push({
          file: path.relative(root, file),
          line: index + 1,
          step: item.step,
          before: line.trim(),
          after: item.after,
          what: item.what,
          docs: `${DOCS}#${item.step}`,
        });
      });
    }
  }

  // Android of the navigation builds with SDK 36: androidx it uses asks for it.
  // The line is that of the file as the command writes it.
  const rewritten = changes.find(({ file }) => file === project.rootBuildGradle)?.next;
  const gradle =
    rewritten ?? (project.rootBuildGradle && fs.readFileSync(project.rootBuildGradle, 'utf8'));
  const compileSdk = gradle && Number(/\bcompileSdkVersion\s*=\s*(\d+)/.exec(gradle)?.[1]);
  if (compileSdk && compileSdk < 36 && project.rootBuildGradle) {
    report.push({
      file: path.relative(root, project.rootBuildGradle),
      line: gradle.slice(0, gradle.search(/\bcompileSdkVersion\b/)).split('\n').length,
      step: 'native',
      before: `compileSdkVersion = ${compileSdk}`,
      after: 'compileSdkVersion = 36, or newer',
      what: 'the navigation builds with Android SDK 36',
      docs: `${DOCS}#native`,
    });
  }
  return { changes, report };
}

/**
 * The Kotlin and Java files of the Android app that name the old library, MainApplication and MainActivity first.
 * @param {import('../project').Project} project
 * @returns {string[]}
 */
function androidCode(project) {
  /** @type {string[]} */
  const found = [project.mainApplication, project.mainActivity].flatMap((file) =>
    file ? [file] : []
  );
  const visit = (/** @type {string} */ dir) => {
    /** @type {fs.Dirent[]} */
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const file = path.join(dir, entry.name);
      if (entry.isDirectory()) visit(file);
      else if (
        /\.(kt|java)$/.test(entry.name) &&
        !found.includes(file) &&
        fs.readFileSync(file, 'utf8').includes('com.reactnativenavigation')
      ) {
        found.push(file);
      }
    }
  };
  if (project.androidSources) visit(project.androidSources);
  return found;
}

/**
 * @param {string} file
 * @returns {string | undefined}
 */
function existing(file) {
  return fs.existsSync(file) ? file : undefined;
}

module.exports = { planNative };

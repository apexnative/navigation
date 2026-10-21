// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { createRun } = require('../changes');
const { isSupported, reactNativeVersion } = require('../app');
const { planCodegen } = require('../codegen');
const { configChange, readConfig, screensDir } = require('../config');
const { exec } = require('../exec');
const { composeChanges } = require('../gradle');
const { createUI } = require('../ui');
const { addScreen, findScreens, importPath } = require('../typescript');
const { openWorkspace } = require('../workspace');
const { addSourceFile } = require('../xcode/pbxproj');
const { composeScreen } = require('./templates/compose');
const { nativeDefinition } = require('./templates/native');
const { reactScreen } = require('./templates/react');
const { swiftUIScreen } = require('./templates/swiftui');
const { uiKitScreen } = require('./templates/uikit');
const { viewsScreen } = require('./templates/views');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('../config').IosKind} IosKind */
/** @typedef {import('../config').AndroidKind} AndroidKind */

const IOS_KINDS = ['swiftui', 'uikit'];
const ANDROID_KINDS = ['compose', 'views'];

/**
 * Adds a screen to the app: its file, and its line in `screens` of `createNavigation`. A native screen (`native`: both
 * platforms, SwiftUI and Compose; `ios` / `android`: that platform, of that kind) also gets its Swift and Kotlin files,
 * its kind in the settings, and the generated code of its props; the first Compose screen sets Compose up.
 * @param {{
 *   root: string;
 *   name: string | undefined;
 *   dir?: string;
 *   native?: boolean;
 *   ios?: string;
 *   android?: string;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 * }} options `dir` is where screens go, from the root, and is kept for the next ones
 * @returns {Promise<number>} the exit code
 */
async function screen({
  root,
  name,
  dir,
  native = false,
  ios: iosFlag,
  android: androidFlag,
  yes,
  dryRun,
  json = false,
  ui = createUI(),
  run = exec,
}) {
  const command = createRun({ command: 'screen', root, json, ui });
  const ios = iosFlag ?? (native ? 'swiftui' : undefined);
  const android = androidFlag ?? (native ? 'compose' : undefined);
  const version = reactNativeVersion(root);
  if (!json) {
    ui.banner({
      command: `screen ${name ?? ''}`.trim(),
      facts: [
        version
          ? { text: `React Native ${version}`, ok: isSupported(version) }
          : { text: 'React Native not found' },
        { text: ios || android ? [ios, android].filter(Boolean).join(' · ') : 'React' },
      ],
    });
  }
  if (!name || !/^[A-Z][A-Za-z0-9]*$/.test(name)) {
    return command.fail(
      'screen-name-invalid',
      'The name of a screen is one word in PascalCase, such as Profile: npx apex-navigation screen Profile'
    );
  }
  if (ios !== undefined && !IOS_KINDS.includes(ios)) {
    return command.fail('screen-kind-invalid', `--ios is ${IOS_KINDS.join(' or ')}, not ${ios}`);
  }
  if (android !== undefined && !ANDROID_KINDS.includes(android)) {
    return command.fail(
      'screen-kind-invalid',
      `--android is ${ANDROID_KINDS.join(' or ')}, not ${android}`
    );
  }
  const workspace = openWorkspace(root, run);
  if ('code' in workspace) return command.fail(workspace.code, workspace.message);
  const { ts, project, navigation } = workspace;

  const config = readConfig(root);
  const folder = dir ?? screensDir(root, config);
  const isNative = ios !== undefined || android !== undefined;
  const extension = isNative
    ? project.typescript
      ? 'ts'
      : 'js'
    : project.typescript
      ? 'tsx'
      : 'jsx';
  const file = path.join(root, folder, `${name}.${extension}`);

  /** @type {FileChange[]} */
  let changes;
  if (!isNative) {
    changes = [
      newFile(file, 'js', reactScreen(name, { typescript: project.typescript }), 'new screen'),
      registration(ts, navigation, name, file),
    ];
    if (dir !== undefined && dir !== config.screensDir) {
      changes.push(configChange(root, (current) => ({ ...current, screensDir: dir })));
    }
    // In an app with native screens their generated code has the typed commands of every screen: this one too.
    /** @type {Map<string, string>} */
    const overrides = new Map();
    for (const change of changes)
      if (change.file && change.next !== undefined) overrides.set(change.file, change.next);
    const generated = planCodegen(root, workspace, { overrides });
    if (generated.errors.length === 0) {
      changes = merge([
        ...changes,
        ...generated.changes.filter(({ status }) => status === 'change'),
      ]);
    }
  } else {
    const planned = planNative(root, workspace, {
      name,
      file,
      ios: /** @type {IosKind | undefined} */ (ios),
      android: /** @type {AndroidKind | undefined} */ (android),
      screensDir: dir !== undefined && dir !== config.screensDir ? dir : undefined,
    });
    if ('errors' in planned) return command.fail('codegen-types', planned.errors);
    changes = planned.changes;
  }
  return command.apply(changes, {
    yes,
    dryRun,
    done: `${name} is in the app: navigation.push('${name}')`,
    nothingToDo: `${name} is in the app already.`,
    next: isNative ? ['npx react-native run-ios · run-android'] : [],
  });
}

/**
 * The files of a native screen: its definition in TS and its line in screens, its Swift and Kotlin files (the Swift
 * one in the Xcode project), its kind in the settings, Compose for a first Compose screen, and the generated code with
 * the screen in it. The generated code is planned from the files as they will be, before anything is written.
 * @param {string} root
 * @param {import('../workspace').Workspace} workspace
 * @param {{ name: string; file: string; ios?: IosKind; android?: AndroidKind; screensDir?: string }} screen
 * @returns {{ changes: FileChange[] } | { errors: string[] }}
 */
function planNative(root, workspace, { name, file, ios, android, screensDir: newScreensDir }) {
  const { ts, project, navigation } = workspace;
  const relative = (/** @type {string} */ to) => path.relative(root, to);
  const iosFile =
    ios && project.appDelegate
      ? path.join(
          path.dirname(project.appDelegate),
          'Screens',
          `${name}${ios === 'swiftui' ? 'Screen' : 'ViewController'}.swift`
        )
      : undefined;
  const androidFile =
    android && project.androidSources && project.androidPackage
      ? path.join(
          project.androidSources,
          ...project.androidPackage.split('.'),
          'screens',
          `${name}Screen.kt`
        )
      : undefined;
  const definition = relative(file);

  /** @type {FileChange[]} */
  const changes = [
    newFile(
      file,
      'js',
      nativeDefinition(name, {
        ios: iosFile && relative(iosFile),
        android: androidFile && relative(androidFile),
        typescript: project.typescript,
      }),
      'new native screen'
    ),
    registration(ts, navigation, name, file),
  ];
  if (ios) {
    if (!iosFile) {
      changes.push(missing(`${name}.swift`, 'ios', "AppDelegate.swift wasn't found"));
    } else {
      changes.push(
        newFile(
          iosFile,
          'ios',
          ios === 'swiftui' ? swiftUIScreen(name, definition) : uiKitScreen(name, definition),
          ios === 'swiftui' ? 'SwiftUI' : 'UIKit'
        )
      );
      if (project.xcodeProject && project.appDelegate)
        changes.push(xcodeChange(project.xcodeProject, project.appDelegate, iosFile));
    }
  }
  if (android) {
    if (!androidFile || !project.androidPackage) {
      changes.push(missing(`${name}Screen.kt`, 'android', "the app's Kotlin package wasn't found"));
    } else {
      changes.push(
        newFile(
          androidFile,
          'android',
          android === 'compose'
            ? composeScreen(name, project.androidPackage, definition)
            : viewsScreen(name, project.androidPackage, definition),
          android === 'compose' ? 'Jetpack Compose' : 'Android views'
        )
      );
      if (android === 'compose') changes.push(...composeChanges(project));
    }
  }
  const settings = configChange(root, (current) => ({
    ...current,
    ...(newScreensDir && { screensDir: newScreensDir }),
    nativeScreens: {
      ...current.nativeScreens,
      [name]: {
        ...current.nativeScreens?.[name],
        ...(ios && { ios }),
        ...(android && { android }),
      },
    },
  }));
  changes.push(settings);

  // The generated code, from the files as they will be.
  /** @type {Map<string, string>} */
  const overrides = new Map();
  for (const change of changes)
    if (change.file && change.next !== undefined) overrides.set(change.file, change.next);
  const config = settings.next ? JSON.parse(settings.next).apexNavigation : readConfig(root);
  const generated = planCodegen(root, workspace, { overrides, kinds: config.nativeScreens });
  if (generated.errors.length > 0) return { errors: generated.errors };
  return { changes: merge([...changes, ...generated.changes]) };
}

/**
 * A new file; `done` when the app has it already, which the command leaves as it is.
 * @param {string} file
 * @param {string} platform
 * @param {string} next
 * @param {string} note
 * @returns {FileChange}
 */
function newFile(file, platform, next, note) {
  const name = path.basename(file);
  if (fs.existsSync(file)) return { name, platform, file, status: 'done', note: 'exists already' };
  return { name, platform, file, status: 'create', next, note };
}

/**
 * The screen's Swift file in the app's target, next to AppDelegate.swift.
 * @param {string} xcodeProject
 * @param {string} appDelegate
 * @param {string} file
 * @returns {FileChange}
 */
function xcodeChange(xcodeProject, appDelegate, file) {
  const before = fs.readFileSync(xcodeProject, 'utf8');
  const result = addSourceFile(before, {
    anchor: 'AppDelegate.swift',
    anchorFile: appDelegate,
    file,
  });
  const base = { name: 'project.pbxproj', platform: 'ios', file: xcodeProject };
  if (result.status === 'done')
    return { ...base, status: 'done', note: `${path.basename(file)} in the target` };
  if (result.status === 'manual') {
    return {
      ...base,
      status: 'manual',
      note: `${result.reason}, see below`,
      howTo: `In Xcode, add ${path.basename(file)} to the app's target.`,
    };
  }
  return { ...base, status: 'change', before, next: result.next, note: `+ ${path.basename(file)}` };
}

/**
 * @param {string} name
 * @param {string} platform
 * @param {string} reason
 * @returns {FileChange}
 */
function missing(name, platform, reason) {
  return {
    name,
    platform,
    status: 'manual',
    note: `${reason}, see below`,
    howTo: `The CLI didn't find where the ${platform === 'ios' ? 'iOS' : 'Android'} code of the app is: run it in the app's root.`,
  };
}

/**
 * The changes of one file as one: the text before the first, the text after the last.
 * @param {FileChange[]} changes
 * @returns {FileChange[]}
 */
function merge(changes) {
  /** @type {FileChange[]} */
  const merged = [];
  for (const change of changes) {
    const same = change.file ? merged.find((other) => other.file === change.file) : undefined;
    if (!same) {
      merged.push(change);
      continue;
    }
    if (change.next === undefined) continue;
    const status = same.status === 'create' ? 'create' : 'change';
    Object.assign(same, { status, next: change.next, note: `${same.note}, ${change.note}` });
  }
  return merged;
}

/**
 * The screen's line in `screens`, with its import; a change by hand when the CLI can't find them.
 * @param {import('../typescript').TS} ts
 * @param {string} navigation the module that calls createNavigation
 * @param {string} name
 * @param {string} file the screen's file
 * @returns {FileChange}
 */
function registration(ts, navigation, name, file) {
  const value = `${name}Screen`;
  const screens = findScreens(ts, navigation);
  if (!screens) {
    return {
      name: path.basename(navigation),
      platform: 'js',
      file: navigation,
      status: 'manual',
      note: "screens weren't found, see below",
      howTo: [
        `Add the screen to screens of createNavigation:`,
        `  import { ${value} } from '${importPath(navigation, file)}';`,
        `  screens: { ..., ${name}: ${value} },`,
      ].join('\n'),
    };
  }
  const before = fs.readFileSync(screens.file, 'utf8');
  const result = addScreen(ts, screens, {
    name,
    value,
    importFrom: importPath(screens.file, file),
  });
  if (result.status === 'done') {
    return {
      name: path.basename(screens.file),
      platform: 'js',
      file: screens.file,
      status: 'done',
      note: `${name} is in screens already`,
    };
  }
  return {
    name: path.basename(screens.file),
    platform: 'js',
    file: screens.file,
    status: 'change',
    before,
    next: result.next,
    note: `+ ${name} in screens`,
  };
}

module.exports = { screen };

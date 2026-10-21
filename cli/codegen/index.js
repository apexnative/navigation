// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { createRun } = require('../changes');
const { readConfig } = require('../config');
const { exec } = require('../exec');
const { createUI } = require('../ui');
const { openWorkspace } = require('../workspace');
const { addSourceFile } = require('../xcode/pbxproj');
const { kotlinFile, PACKAGE } = require('./kotlin');
const { createProgram, readCommandScreens, readNativeScreens } = require('./read');
const { swiftFile } = require('./swift');

/** @typedef {import('../changes').FileChange} FileChange */

const SWIFT_FILE = 'ApexNavigation.generated.swift';
const KOTLIN_FILE = 'ApexNavigationGenerated.kt';

/**
 * The generated files of the app's native screens, as they should be now: next to AppDelegate.swift (in the Xcode
 * project too) and in the package the navigation finds on Android. None for an app without native screens, unless
 * the files are there already (then they are made again, without the screens that went).
 * @param {string} root
 * @param {import('../workspace').Workspace} workspace
 * @param {{ overrides?: Map<string, string>; kinds?: import('../config').Config['nativeScreens'] }} [options]
 *   `overrides`: files as a command is about to write them (by absolute path), read in place of the disk; `kinds`: the
 *   native screens' kinds, in place of the settings
 * @returns {{ changes: FileChange[]; screens: string[]; errors: string[]; skipped?: string[] }} `errors`: types with
 *   no native form, as `file:line: message`; `skipped`: the screens left out of the typed commands, and why
 */
function planCodegen(
  root,
  { ts, project, navigation },
  { overrides = new Map(), kinds: given } = {}
) {
  const program = createProgram(ts, root, navigation, overrides);
  const { screens, errors } = readNativeScreens(ts, program, root);
  if (errors.length > 0) {
    return {
      changes: [],
      screens: [],
      errors: errors.map(({ file, line, message }) => `${file}:${line}: ${message}`),
    };
  }
  const names = screens.map(({ nativeName }) => nativeName);
  const twice = names.filter((name, index) => names.indexOf(name) !== index);
  if (twice.length > 0) {
    return {
      changes: [],
      screens: [],
      errors: [...new Set(twice)].map(
        (name) => `Two native screens are registered as "${name}": a name goes once`
      ),
    };
  }
  const kinds = given ?? readConfig(root).nativeScreens ?? {};
  // Every screen of the app, for the typed commands of native code: only with native screens, which have them.
  const commands =
    screens.length > 0 ? readCommandScreens(ts, program, navigation) : { screens: [], skipped: [] };
  const textOf = (/** @type {string} */ file) =>
    overrides.get(file) ?? (fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : undefined);
  /** @type {FileChange[]} */
  const changes = [];

  if (project.appDelegate) {
    const file = path.join(path.dirname(project.appDelegate), SWIFT_FILE);
    if (screens.length > 0 || fs.existsSync(file)) {
      changes.push(
        generated(
          file,
          textOf(file),
          'ios',
          swiftFile(
            screens.map((screen) => ({ screen, kind: kinds[screen.nativeName]?.ios })),
            commands.screens
          )
        )
      );
      const xcodeText = project.xcodeProject && textOf(project.xcodeProject);
      if (project.xcodeProject && xcodeText !== undefined) {
        changes.push(xcodeChange(project.xcodeProject, xcodeText, project.appDelegate, file));
      }
    }
  }
  if (project.androidSources && project.androidPackage) {
    const file = path.join(project.androidSources, ...PACKAGE.split('.'), KOTLIN_FILE);
    if (screens.length > 0 || fs.existsSync(file)) {
      const text = kotlinFile(
        screens.map((screen) => ({ screen, kind: kinds[screen.nativeName]?.android })),
        { appPackage: project.androidPackage, commands: commands.screens }
      );
      changes.push(generated(file, textOf(file), 'android', text));
    }
  }
  return {
    changes,
    screens: names,
    errors: [],
    skipped: commands.skipped.map(
      ({ name, reason }) =>
        `${name} has no typed command, native code opens it by its name: ${reason}`
    ),
  };
}

/**
 * A generated file: made again whole each time.
 * @param {string} file
 * @param {string | undefined} before its text now; none when it isn't there
 * @param {string} platform
 * @param {string} next
 * @returns {FileChange}
 */
function generated(file, before, platform, next) {
  const name = path.basename(file);
  if (before === undefined) {
    return { name, platform, file, status: 'create', next, note: 'generated' };
  }
  if (before === next) return { name, platform, file, status: 'done', note: 'up to date' };
  return { name, platform, file, status: 'change', before, next, note: 'generated again' };
}

/**
 * The generated Swift file in the app's target, next to AppDelegate.swift.
 * @param {string} xcodeProject
 * @param {string} before the project's text now
 * @param {string} appDelegate
 * @param {string} file
 * @returns {FileChange}
 */
function xcodeChange(xcodeProject, before, appDelegate, file) {
  const result = addSourceFile(before, {
    anchor: 'AppDelegate.swift',
    anchorFile: appDelegate,
    file,
  });
  const name = 'project.pbxproj';
  if (result.status === 'done')
    return {
      name,
      platform: 'ios',
      file: xcodeProject,
      status: 'done',
      note: `${SWIFT_FILE} in the target`,
    };
  if (result.status === 'manual') {
    return {
      name,
      platform: 'ios',
      file: xcodeProject,
      status: 'manual',
      note: `${result.reason}, see below`,
      howTo: `In Xcode, add ${path.relative(path.dirname(path.dirname(xcodeProject)), file)} to the app's target.`,
    };
  }
  return {
    name,
    platform: 'ios',
    file: xcodeProject,
    status: 'change',
    before,
    next: result.next,
    note: `+ ${SWIFT_FILE}`,
  };
}

/**
 * Generates the native side of the app's native screens from their props in TypeScript.
 * @param {{
 *   root: string;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   header?: boolean;
 * }} options `header: false` leaves the banner out, as when doctor runs codegen to fix what it found
 * @returns {Promise<number>} the exit code
 */
async function codegen({
  root,
  yes,
  dryRun,
  json = false,
  ui = createUI(),
  run = exec,
  header = true,
}) {
  const command = createRun({ command: 'codegen', root, json, ui });
  if (header && !json) ui.banner({ command: 'codegen', facts: [{ text: 'Swift · Kotlin' }] });
  const workspace = openWorkspace(root, run);
  if ('code' in workspace) return command.fail(workspace.code, workspace.message);
  const { changes, screens, errors, skipped = [] } = planCodegen(root, workspace);
  if (errors.length > 0) return command.fail('codegen-types', errors);
  for (const message of skipped) command.warn(message);
  if (changes.length === 0) {
    if (json) return command.apply([], { yes, dryRun });
    ui.done('The app has no native screens: nothing to generate.');
    return 0;
  }
  return command.apply(changes, {
    yes,
    dryRun,
    done: `Generated for ${screens.length} native ${screens.length === 1 ? 'screen' : 'screens'}`,
    nothingToDo: 'Up to date.',
  });
}

module.exports = { codegen, planCodegen };

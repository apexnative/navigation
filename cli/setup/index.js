// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { createRun } = require('../changes');
const { createUI } = require('../ui');
const { isSupported, reactNativeVersion, usesExpo, MINIMUM } = require('../app');
const { exec, execInTerminal } = require('../exec');
const { installPods, podsCommand } = require('../pods');
const { findProject } = require('../project');
const { loadTypeScript, navigationImport } = require('../typescript');
const { planApp, HOW_TO: APP_HOW_TO } = require('./steps/app');
const { planAppDelegate } = require('./steps/appDelegate');
const { planEntry, navigationFile, HOW_TO: ENTRY_HOW_TO } = require('./steps/entry');
const { planEnv, targetOf } = require('./steps/env');
const { planInfoPlist } = require('./steps/infoPlist');
const { planMainActivity } = require('./steps/mainActivity');
const { planSceneDelegate } = require('./steps/sceneDelegate');
const { planMainApplication } = require('./steps/mainApplication');
const { planManifest } = require('./steps/manifest');
const { planStyles } = require('./steps/styles');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('../project').Project} Project */

/**
 * @param {string} name
 * @param {string} platform
 * @param {string | undefined} file
 * @param {(text: string) => import('./steps/types').StepResult} plan
 * @param {string} howTo what to do when the file isn't there
 * @returns {FileChange}
 */
function planFile(name, platform, file, plan, howTo) {
  if (!file) return { name, platform, status: 'manual', note: 'not found, see below', howTo };
  const before = fs.readFileSync(file, 'utf8');
  return { name: path.basename(file), platform, file, before, ...plan(before) };
}

/**
 * Every file setup changes or creates.
 * @param {Project} project
 * @returns {FileChange[]}
 */
function planProject(project) {
  const notFound = (/** @type {string} */ what) =>
    `${what} wasn't found where the React Native CLI looks for it.`;
  const plans = [
    planFile(
      'MainApplication.kt',
      'android',
      project.mainApplication,
      planMainApplication,
      notFound('MainApplication.kt')
    ),
    planFile(
      'MainActivity.kt',
      'android',
      project.mainActivity,
      planMainActivity,
      notFound('MainActivity.kt')
    ),
    // The navigation works without predictive back: a manifest that isn't where the CLI looks is left out.
    ...(project.manifest
      ? [
          planFile(
            'AndroidManifest.xml',
            'android',
            project.manifest,
            planManifest,
            notFound('AndroidManifest.xml')
          ),
        ]
      : []),
    // The app's theme is optional: without one the navigation's views take its own neutral theme.
    ...(project.styles
      ? [planFile('styles.xml', 'android', project.styles, planStyles, notFound('styles.xml'))]
      : []),
    planFile(
      'AppDelegate.swift',
      'ios',
      project.appDelegate,
      planAppDelegate,
      notFound('AppDelegate.swift')
    ),
    // React Native 0.88's template starts React Native in its scene delegate; the earlier templates have none.
    ...(project.sceneDelegate
      ? [
          planFile(
            'SceneDelegate.swift',
            'ios',
            project.sceneDelegate,
            planSceneDelegate,
            notFound('SceneDelegate.swift')
          ),
        ]
      : []),
    planFile('Info.plist', 'ios', project.infoPlist, planInfoPlist, notFound('Info.plist')),
    planFile('index.js', 'js', project.entry, planEntry, ENTRY_HOW_TO),
  ];
  // The file the new entry point imports.
  const entry = plans[plans.length - 1];
  /** @type {import('./steps/env').NavigationTarget | undefined} */
  let navigation;
  const ts = loadTypeScript(project.root);
  if (entry.status === 'change') {
    const name = project.typescript ? 'Navigation.ts' : 'Navigation.js';
    const file = path.join(project.root, name);
    if (fs.existsSync(file)) {
      plans[plans.length - 1] = {
        ...entry,
        status: 'manual',
        note: `${name} exists already, see below`,
        howTo: ENTRY_HOW_TO,
        next: undefined,
      };
    } else {
      plans.push({
        name,
        platform: 'js',
        file,
        status: 'create',
        next: navigationFile(),
        note: 'new file',
      });
      // The entry point setup writes imports the navigation from it, with no context of its own yet.
      navigation = { file, exported: 'Navigation', context: false };
    }
  } else if (entry.status === 'done' && ts && project.entry) {
    const imported = navigationImport(ts, project.entry);
    if (imported) navigation = targetOf(ts, imported);
  }
  plans.push(...planEnv(project.root, navigation, ts));
  // The template's App renders React Native's status bar; one moved elsewhere or written anew has nothing to change.
  if (project.app) plans.push(planFile('App.tsx', 'js', project.app, planApp, APP_HOW_TO));
  return plans;
}

/**
 * Whether setup's changes need the pods installed again: it changed a file of the iOS app, or the pods were installed
 * before the navigation was.
 * @param {string} root
 * @param {FileChange[]} changes
 */
function needsPods(root, changes) {
  if (!fs.existsSync(path.join(root, 'ios', 'Podfile'))) return false;
  const changedIos = changes.some(
    ({ platform, status }) => platform === 'ios' && ['change', 'create'].includes(status)
  );
  let lock = '';
  try {
    lock = fs.readFileSync(path.join(root, 'ios', 'Podfile.lock'), 'utf8');
  } catch {
    // No pods installed yet.
  }
  return changedIos || !/^\s*- ApexNavigation \(/m.test(lock);
}

/**
 * Sets up the app: its native files, its entry point and the types of its commands, then, on a Mac, the pods.
 * @param {{
 *   root: string;
 *   yes: boolean;
 *   dryRun: boolean;
 *   skipPods?: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   header?: boolean;
 *   platform?: NodeJS.Platform;
 *   run?: import('../exec').Exec;
 *   inTerminal?: typeof execInTerminal;
 * }} options `header: false` leaves the banner out, as when doctor runs setup to fix what it found; `json` prints
 *   the result for tools instead of talking to the terminal; `skipPods` leaves the pods for later.
 * @returns {Promise<number>} the exit code
 */
async function setup({
  root,
  yes,
  dryRun,
  skipPods = false,
  json = false,
  ui = createUI(),
  header = true,
  platform = process.platform,
  run: runProgram = exec,
  inTerminal = execInTerminal,
}) {
  const run = createRun({ command: 'setup', root, json, ui });
  const version = reactNativeVersion(root);
  if (header && !json) {
    ui.banner({
      command: 'setup',
      facts: [
        version
          ? { text: `React Native ${version}`, ok: isSupported(version) }
          : { text: 'React Native not found' },
        { text: 'iOS · Android' },
      ],
    });
  }
  if (!version) {
    return run.fail(
      'react-native-missing',
      "React Native isn't installed here. Run the command in the root of your app."
    );
  }
  if (!isSupported(version)) {
    return run.fail(
      'react-native-unsupported',
      `React Native ${version} is too old: the navigation needs ${MINIMUM.join('.')} or newer.`
    );
  }
  if (usesExpo(root)) {
    return run.fail(
      'expo-unsupported',
      "This is an Expo app: Expo makes its native files itself, so setup doesn't change them."
    );
  }

  const changes = planProject(findProject(root, runProgram));
  return run.apply(changes, {
    yes,
    dryRun,
    nothingToDo: 'Everything is set up.',
    next: ['npx react-native run-ios · run-android'],
    afterWrite(result) {
      if (!needsPods(root, changes)) return;
      const command = `cd ios && ${podsCommand(root).join(' && ')} && cd ..`;
      if (platform !== 'darwin') {
        result.next.unshift('Run pod install in ios/ on a Mac.');
        return;
      }
      if (skipPods) {
        result.next.unshift(command);
        return;
      }
      if (!json) {
        ui.line('');
        ui.done(`Pods   ${podsCommand(root).join(' && ')}`);
        ui.line('');
      }
      const pods = installPods({ root, json, run: runProgram, inTerminal });
      if (pods.ok) return;
      result.ok = false;
      result.errors.push({ code: 'pods-failed', message: `${pods.command} failed${pods.output}` });
      if (!json) ui.error(`pod install failed${pods.output}`);
      result.next.unshift(pods.command);
    },
  });
}

module.exports = { setup, planProject, needsPods };

// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const {
  MINIMUM,
  PACKAGE,
  installedVersion,
  isSupported,
  reactNativeVersion,
  usesExpo,
} = require('../../app');
const { mainActivity } = require('../../links/native');
const { findProject } = require('../../project');
const { codeOf } = require('../../setup/source');
const { loadsReactNativeItself } = require('../../setup/steps/mainApplication');
const { planCodegen } = require('../../codegen');
const { readConfig } = require('../../config');
const { planProject } = require('../../setup');
const { ENV_FILE } = require('../../setup/steps/env');
const { openWorkspace } = require('../../workspace');
const { iconChecks } = require('./icons');
const { splashChecks } = require('./splash');

/** @typedef {import('../types').Check} Check */
/** @typedef {import('../types').Context} Context */

const SETUP = { label: 'npx apex-navigation setup', run: /** @type {const} */ ('setup') };
const GENERATED = ['ApexNavigation.generated.swift', 'ApexNavigationGenerated.kt'];
const CODEGEN = { label: 'npx apex-navigation codegen', run: /** @type {const} */ ('codegen') };
// The changes the main activity must handle itself; the template's value has them all.
const CONFIG_CHANGES = [
  'orientation',
  'screenSize',
  'smallestScreenSize',
  'screenLayout',
  'uiMode',
];
const TEMPLATE_CONFIG_CHANGES =
  'keyboard|keyboardHidden|orientation|screenLayout|screenSize|smallestScreenSize|uiMode';

/**
 * React Native, the navigation, and the files setup sets up.
 * @param {Context} context
 * @returns {Check[]}
 */
function appChecks({ root, run }) {
  /** @type {Check[]} */
  const checks = [];
  const version = reactNativeVersion(root);
  if (!version) {
    checks.push({
      group: 'app',
      title: 'React Native',
      status: 'error',
      detail: 'not installed here',
      fix: { label: 'Run the command in the root of your app' },
    });
    return checks;
  }
  checks.push(
    isSupported(version)
      ? {
          group: 'app',
          title: 'React Native',
          status: 'ok',
          detail: `${version}, New Architecture`,
        }
      : {
          group: 'app',
          title: 'React Native',
          status: 'error',
          detail: `${version}: the navigation needs ${MINIMUM.join('.')} or newer`,
          fix: { label: `Upgrade React Native to ${MINIMUM.join('.')} or newer` },
        }
  );
  const navigation = installedVersion(root, PACKAGE);
  checks.push(
    navigation
      ? { group: 'app', title: 'Navigation', status: 'ok', detail: navigation }
      : {
          group: 'app',
          title: 'Navigation',
          status: 'error',
          detail: 'not installed',
          fix: { label: `npm install ${PACKAGE}` },
        }
  );
  if (usesExpo(root)) {
    checks.push({
      group: 'app',
      title: 'Native files',
      status: 'info',
      detail: "Expo makes them; setup doesn't change them yet",
    });
    return checks;
  }

  const project = findProject(root, run);
  const plans = planProject(project);
  const app = plans.find(({ file }) => file !== undefined && file === project.app);
  const metro = plans.find(({ file }) => file !== undefined && file === project.metroConfig);
  const manifest = plans.find(({ file }) => file !== undefined && file === project.manifest);
  const native = plans.filter((plan) => plan.platform !== 'js' && plan !== manifest);
  const types = plans.filter(({ name }) => name === ENV_FILE || name === 'tsconfig.json');
  const entry = plans.filter(
    (plan) => plan.platform === 'js' && plan !== app && plan !== metro && !types.includes(plan)
  );
  checks.push(filesCheck('Native files', native), filesCheck('Entry point', entry));
  const typed = typesCheck(root, types);
  if (typed) checks.push(typed);
  // React Native's StatusBar logs an error on iOS on every render once the screens set the status bar.
  if (app && app.status !== 'done') {
    checks.push({
      group: 'app',
      title: 'Status bar',
      status: 'warn',
      detail: `${app.name} renders React Native's StatusBar, the screens set it`,
      fix: app.status === 'manual' ? { label: `Remove StatusBar from ${app.name}` } : SETUP,
    });
  }

  // Without the navigation's Metro setup an edit that isn't a boundary of Fast Refresh reloads the app.
  if (metro && metro.status !== 'done') {
    checks.push({
      group: 'app',
      title: 'Fast Refresh',
      status: 'warn',
      detail: `${metro.name} doesn't use withApexNavigation: some edits reload the app`,
      fix:
        metro.status === 'manual'
          ? { label: 'Wrap the config metro.config.js exports in withApexNavigation' }
          : SETUP,
    });
  }

  // Predictive back is optional: without it the navigation works, and Android plays no back animation of its own.
  if (manifest && manifest.status !== 'done') {
    checks.push({
      group: 'app',
      title: 'Back animation',
      status: 'warn',
      detail: `${manifest.name} doesn't turn on predictive back: no system back animation on Android`,
      fix:
        manifest.status === 'manual'
          ? { label: 'Add android:enableOnBackInvokedCallback="true" to <application>' }
          : SETUP,
    });
  }

  const configChanges = configChangesCheck(project.manifest);
  if (configChanges) checks.push(configChanges);

  checks.push(...splashChecks(root, project), ...iconChecks(root, project));

  // NavigationApplication leaves the load of React Native to the template's call: without it the app closes on start,
  // and so it does with a second load of the app's own next to it.
  const application = project.mainApplication && fs.readFileSync(project.mainApplication, 'utf8');
  if (application && /:\s*NavigationApplication\(/.test(application)) {
    const loads = /\bloadReactNative\s*\(/.test(codeOf(application));
    if (!loads) {
      checks.push({
        group: 'app',
        title: 'React Native not loaded',
        status: 'error',
        detail: "MainApplication.kt doesn't call loadReactNative(this) in onCreate",
        fix: SETUP,
      });
    } else if (loadsReactNativeItself(application)) {
      checks.push({
        group: 'app',
        title: 'React Native loads twice',
        status: 'error',
        detail:
          'MainApplication.kt loads React Native itself (SoLoader.init or the New Architecture entry point) next to loadReactNative(this)',
        fix: SETUP,
      });
    }
  }
  const generated = generatedCheck(root, run);
  if (generated) checks.push(generated);
  return checks;
}

/**
 * Whether the main activity handles the changes of its window itself, as the React Native template has it. Without
 * one of them Android recreates the activity on a rotation, a resize, a fold or a light/dark switch: every screen goes
 * with it and the navigation starts again from its root. Nothing without a manifest or a main activity.
 * @param {string | undefined} manifest
 * @returns {Check | undefined}
 */
function configChangesCheck(manifest) {
  if (!manifest || !fs.existsSync(manifest)) return undefined;
  const activity = mainActivity(fs.readFileSync(manifest, 'utf8'));
  if (!activity) return undefined;
  const value = /android:configChanges\s*=\s*(?:"([^"]*)"|'([^']*)')/.exec(activity.text);
  const declared = (value?.[1] ?? value?.[2] ?? '').split('|').map((change) => change.trim());
  const missing = CONFIG_CHANGES.filter((change) => !declared.includes(change));
  if (missing.length === 0) return undefined;
  return {
    group: 'app',
    title: 'Config changes',
    status: 'warn',
    detail: `the main activity doesn't handle ${missing.join(', ')}: Android recreates it, and every screen with it`,
    fix: { label: `Set android:configChanges="${TEMPLATE_CONFIG_CHANGES}" on the main activity` },
  };
}

/**
 * One line for a few files: set up, or which need setup or a change by hand.
 * @param {string} title
 * @param {import('../../changes').FileChange[]} plans
 * @returns {Check}
 */
function filesCheck(title, plans) {
  const names = (/** @type {string[]} */ statuses) =>
    plans.filter(({ status }) => statuses.includes(status)).map(({ name }) => name);
  const pending = names(['change', 'create']);
  const manual = names(['manual']);
  if (pending.length === 0 && manual.length === 0) {
    return {
      group: 'app',
      title,
      status: 'ok',
      detail: plans.map(({ name }) => name).join(' · '),
    };
  }
  if (manual.length > 0) {
    return {
      group: 'app',
      title,
      status: 'error',
      detail: `changed by hand: ${manual.join(', ')}`,
      fix: { label: 'npx apex-navigation setup shows what to change' },
    };
  }
  return {
    group: 'app',
    title,
    status: 'error',
    detail: `not set up: ${pending.join(', ')}`,
    fix: SETUP,
  };
}

/**
 * Whether the commands are typed with the app's screens: navigation-env.d.ts is there, points to the navigation, has
 * its context, and TypeScript reads it. Nothing while the entry point isn't set up (its own check says so).
 * @param {string} root
 * @param {import('../../changes').FileChange[]} plans setup's plans of navigation-env.d.ts and tsconfig.json
 * @returns {Check | undefined}
 */
function typesCheck(root, plans) {
  if (!fs.existsSync(path.join(root, 'tsconfig.json'))) {
    return {
      group: 'app',
      title: 'Typed commands',
      status: 'info',
      detail: "a JavaScript app: screen names and props aren't checked",
    };
  }
  const env = plans.find(({ name }) => name === ENV_FILE);
  if (!env) return undefined;
  const include = plans.find(({ name }) => name === 'tsconfig.json');
  /** @type {string | undefined} */
  let problem;
  if (env.status === 'create') problem = `no ${ENV_FILE}: names and props aren't checked`;
  else if (env.status === 'change') problem = `${ENV_FILE} is out of date: ${env.note}`;
  else if (env.status === 'manual') problem = `${ENV_FILE} was written by hand`;
  else if (include) problem = `tsconfig.json leaves ${ENV_FILE} out`;
  if (!problem) {
    return {
      group: 'app',
      title: 'Typed commands',
      status: 'ok',
      detail:
        env.file && /\bcontext:/.test(fs.readFileSync(env.file, 'utf8'))
          ? `${ENV_FILE} · context`
          : ENV_FILE,
    };
  }
  const byHand = env.status === 'manual' || include?.status === 'manual';
  return {
    group: 'app',
    title: 'Typed commands',
    status: 'warn',
    detail: problem,
    fix: byHand ? { label: 'npx apex-navigation setup shows what to change' } : SETUP,
  };
}

/**
 * Whether the Swift and Kotlin code `codegen` makes of the native screens is as their props in TypeScript are now:
 * a native screen added, or a prop changed, since it last ran. Only in an app that uses codegen (it has the generated
 * files, or native screens `screen` made); none in one that registers its native screens by hand, or that the CLI
 * can't read (the checks above say why).
 * @param {string} root
 * @param {import('../../exec').Exec} run
 * @returns {Check | undefined}
 */
function generatedCheck(root, run) {
  const workspace = openWorkspace(root, run);
  if ('code' in workspace) return undefined;
  const { changes, screens, errors } = planCodegen(root, workspace);
  if (errors.length > 0) {
    return {
      group: 'app',
      title: 'Native screens',
      status: 'error',
      detail: errors.length > 1 ? `${errors[0]} (and ${errors.length - 1} more)` : errors[0],
      fix: {
        label:
          'Give these props types Swift and Kotlin have; npx apex-navigation codegen lists them',
      },
    };
  }
  // The app has the generated files already, or native screens `screen` made.
  const usesCodegen =
    changes.some(({ name, status }) => GENERATED.includes(name) && status !== 'create') ||
    Object.keys(readConfig(root).nativeScreens ?? {}).length > 0;
  if (changes.length === 0 || !usesCodegen) return undefined;
  const stale = changes.filter(({ status }) => status !== 'done').map(({ name }) => name);
  if (stale.length === 0) {
    return {
      group: 'app',
      title: 'Native screens',
      status: 'ok',
      detail: `${screens.length} · generated code up to date`,
    };
  }
  // Only the typed commands behind, after a React screen changed: the native screens still build and run.
  const commandsOnly = changes
    .filter(({ status }) => status !== 'done')
    .every(
      ({ before, next }) =>
        before !== undefined &&
        next !== undefined &&
        withoutCommands(before) === withoutCommands(next)
    );
  if (commandsOnly) {
    return {
      group: 'app',
      title: 'Native screens',
      status: 'warn',
      detail: `the typed commands are older than the screens: ${stale.join(', ')}`,
      fix: CODEGEN,
    };
  }
  return {
    group: 'app',
    title: 'Native screens',
    status: 'error',
    detail: `the generated code is older than their props: ${stale.join(', ')}`,
    fix: CODEGEN,
  };
}

/**
 * The generated code without its typed commands, the part after their heading.
 * @param {string} text
 */
function withoutCommands(text) {
  const start = text.search(/^\/\/ (?:MARK: - )?The screens of the app$/m);
  return start < 0 ? text : text.slice(0, start);
}

module.exports = { appChecks, generatedCheck };

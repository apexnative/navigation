// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const {
  MINIMUM,
  PACKAGE,
  dependencies,
  isSupported,
  reactNativeVersion,
  usesExpo,
} = require('../app');
const { createRun } = require('../changes');
const { exec, execInTerminal } = require('../exec');
const { installPods } = require('../pods');
const { findProject } = require('../project');
const { planProject } = require('../setup');
const { planEnv } = require('../setup/steps/env');
const { planManifest } = require('../setup/steps/manifest');
const { planStyles } = require('../setup/steps/styles');
const { loadTypeScript } = require('../typescript');
const { createUI } = require('../ui');
const { planEntry } = require('./entry');
const { planNative } = require('./native');
const { codeFiles, reportCode } = require('./report');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('./report').ReportItem} ReportItem */
/** @typedef {'react-native-navigation' | 'react-navigation'} Source */

// Installing the dependencies after the swap takes minutes; under --json nothing shows how far it is.
const LONG = 20 * 60_000;
const DOCS = 'https://navigation.apexnative.dev/docs/migration';

/**
 * Moves an app to the navigation. From react-native-navigation: the package, the native files, the entry point and
 * its registered screens and first root; from React Navigation: the package and setup, the app as the first screen.
 * The code left to change by hand is the report, place by place, with the step of the migration page; while it has
 * places the command exits with 1, as any command with files left to change by hand.
 * @param {{
 *   root: string;
 *   from?: string;
 *   yes: boolean;
 *   dryRun: boolean;
 *   skipPods?: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   inTerminal?: typeof execInTerminal;
 *   platform?: NodeJS.Platform;
 * }} options `from`: the library the app moves from, by default the one in its package.json
 * @returns {Promise<number>} the exit code
 */
async function migrate({
  root,
  from,
  yes,
  dryRun,
  skipPods = false,
  json = false,
  ui = createUI(),
  run = exec,
  inTerminal = execInTerminal,
  platform = process.platform,
}) {
  const command = createRun({ command: 'migrate', root, json, ui });
  const version = reactNativeVersion(root);
  const source = sourceOf(root, from);
  if (!json) {
    ui.banner({
      command: 'migrate',
      facts: [
        version
          ? { text: `React Native ${version}`, ok: isSupported(version) }
          : { text: 'React Native not found' },
        { text: source ? `from ${source}` : 'nothing to move from' },
      ],
    });
  }
  if (!version) {
    return command.fail(
      'react-native-missing',
      "React Native isn't installed here. Run the command in the root of your app."
    );
  }
  if (!isSupported(version)) {
    return command.fail(
      'react-native-unsupported',
      `React Native ${version} is too old: the navigation needs ${MINIMUM.join('.')} or newer. Upgrade React Native first, then move.`
    );
  }
  if (usesExpo(root)) {
    return command.fail(
      'expo-unsupported',
      "This is an Expo app: Expo makes its native files itself, so migrate doesn't change them."
    );
  }
  if (from !== undefined && from !== 'react-native-navigation' && from !== 'react-navigation') {
    return command.fail(
      'from-invalid',
      `--from is react-native-navigation or react-navigation, not ${from}.`
    );
  }
  if (!source) {
    return command.fail(
      'nothing-to-migrate',
      'package.json has neither react-native-navigation nor @react-navigation/*. To see what is left of a move, run it again with --from react-native-navigation or --from react-navigation.'
    );
  }
  const ts = loadTypeScript(root);
  if (!ts) {
    return command.fail(
      'typescript-missing',
      "TypeScript isn't installed here: the CLI reads the app's code with it (npm install -D typescript)."
    );
  }

  const project = findProject(root, run);
  /** @type {FileChange[]} */
  const changes = [packageChange(root, source)];
  /** @type {ReportItem[]} */
  let report;
  if (source === 'react-native-navigation') {
    const native = planNative(project);
    changes.push(...native.changes);
    const entry = project.entry && planEntry(ts, root, project.entry, codeFiles(root));
    /** @type {Set<string>} */
    let moved = new Set();
    if (entry && 'changes' in entry) {
      changes.push(...entry.changes);
      moved = entry.moved;
      changes.push(
        ...planEnv(
          root,
          {
            file: /** @type {string} */ (entry.changes[1].file),
            exported: 'Navigation',
            context: false,
          },
          ts
        )
      );
    } else {
      command.warn(
        `The screens and the first root stay for the report: ${entry ? entry.reason : 'no index.js'}.`
      );
    }
    // What setup does besides: predictive back, and the theme of the navigation's views.
    for (const [
      file,
      plan,
    ] of /** @type {[string | undefined, (text: string) => import('../setup/steps/types').StepResult][]} */ ([
      [project.manifest, planManifest],
      [project.styles, planStyles],
    ])) {
      if (!file) continue;
      const before = fs.readFileSync(file, 'utf8');
      const step = plan(before);
      if (step.status === 'change')
        changes.push({ name: path.basename(file), platform: 'android', file, before, ...step });
    }
    report = [...native.report, ...reportCode(root, 'react-native-navigation', moved)];
  } else {
    // A React Navigation app is a plain React Native app: setup makes it the first screen of the navigation.
    changes.push(...planProject(project));
    report = reportCode(root, 'react-navigation');
  }

  changes.push(...manualFiles(root, report));
  Object.assign(command.result, { from: source, report });
  const guide = `${DOCS}/from-${source}`;
  return command.apply(changes, {
    yes,
    dryRun,
    done: 'Moved what the CLI can.',
    nothingToDo: 'Nothing left to move.',
    next: [
      `The places above, step by step: ${guide}`,
      'npx apex-navigation doctor',
      'npx react-native run-ios · run-android',
    ],
    afterWrite(result) {
      const install = installCommand(root);
      // The dependencies are installed again only when package.json changed.
      const installed =
        changes[0].status === 'done' ||
        (json
          ? run(install[0], install.slice(1), { cwd: root, timeout: LONG }).ok
          : inTerminal(install[0], install.slice(1), { cwd: root }));
      if (!installed) {
        result.ok = false;
        result.errors.push({ code: 'install-failed', message: `${install.join(' ')} failed` });
        result.next.unshift(install.join(' '));
        return;
      }
      if (platform !== 'darwin' || skipPods || !fs.existsSync(path.join(root, 'ios', 'Podfile')))
        return;
      const pods = installPods({ root, json, run, inTerminal });
      if (!pods.ok) {
        result.ok = false;
        result.errors.push({
          code: 'pods-failed',
          message: `${pods.command} failed${pods.output}`,
        });
        result.next.unshift(pods.command);
      }
    },
  });
}

/**
 * The library the app moves from: the one asked for, else the one in its dependencies.
 * @param {string} root
 * @param {string | undefined} from
 * @returns {Source | undefined}
 */
function sourceOf(root, from) {
  if (from === 'react-native-navigation' || from === 'react-navigation') return from;
  const names = dependencies(root);
  if (names.includes('react-native-navigation')) return 'react-native-navigation';
  if (names.some((name) => name.startsWith('@react-navigation/'))) return 'react-navigation';
  return undefined;
}

/**
 * package.json with the navigation in its dependencies at the version of this CLI, in their order; from
 * react-native-navigation, without it. The packages of React Navigation stay until its last step.
 * @param {string} root
 * @param {Source} source
 * @returns {FileChange}
 */
function packageChange(root, source) {
  const file = path.join(root, 'package.json');
  const before = fs.readFileSync(file, 'utf8');
  const json = JSON.parse(before);
  const version = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', '..', 'package.json'), 'utf8')
  ).version;
  const next = { ...json.dependencies, [PACKAGE]: `^${version}` };
  if (source === 'react-native-navigation') delete next['react-native-navigation'];
  json.dependencies = Object.fromEntries(
    Object.keys(next)
      .sort()
      .map((key) => [key, next[key]])
  );
  const indent = /^[ \t]+/m.exec(before)?.[0] ?? '  ';
  const written = JSON.stringify(json, null, indent) + '\n';
  // A second run, after the swap: nothing to change, and nothing to install.
  if (JSON.stringify(JSON.parse(before)) === JSON.stringify(json)) {
    return { name: 'package.json', platform: 'js', file, status: 'done', note: `${PACKAGE} in it` };
  }
  return {
    name: 'package.json',
    platform: 'js',
    file,
    status: 'change',
    before,
    next: written,
    note:
      source === 'react-native-navigation'
        ? `react-native-navigation → ${PACKAGE}`
        : `${PACKAGE} ${version}`,
  };
}

/**
 * The report as files left to change by hand: one per file, each place a line with its new form and its step.
 * @param {string} root
 * @param {ReportItem[]} report
 * @returns {FileChange[]}
 */
function manualFiles(root, report) {
  /** @type {Map<string, ReportItem[]>} */
  const byFile = new Map();
  for (const item of report) byFile.set(item.file, [...(byFile.get(item.file) ?? []), item]);
  return [...byFile].map(([file, items]) => ({
    name: path.basename(file),
    platform: /^(ios|android)\//.test(file) ? file.split('/')[0] : 'js',
    file: path.join(root, file),
    status: 'manual',
    note: `${items.length} ${items.length === 1 ? 'place' : 'places'} to move`,
    howTo: items
      .map(
        (item) =>
          `${String(item.line).padStart(4)}  ${item.before}\n      → ${item.after}  (${item.docs})`
      )
      .join('\n'),
  }));
}

/**
 * The install of the app's dependencies with the package manager of its lockfile.
 * @param {string} root
 * @returns {string[]}
 */
function installCommand(root) {
  const has = (/** @type {string} */ name) => fs.existsSync(path.join(root, name));
  if (has('yarn.lock')) return ['yarn', 'install'];
  if (has('bun.lock') || has('bun.lockb')) return ['bun', 'install'];
  if (has('pnpm-lock.yaml')) return ['pnpm', 'install'];
  return ['npm', 'install'];
}

module.exports = { migrate, sourceOf };

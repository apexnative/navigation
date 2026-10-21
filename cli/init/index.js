// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { MINIMUM, PACKAGE, TESTED, isSupported } = require('../app');
const { createRun } = require('../changes');
const { androidChecks } = require('../doctor/checks/android');
const { iosChecks } = require('../doctor/checks/ios');
const { exec, execInTerminal } = require('../exec');
const { installPods, podsCommand } = require('../pods');
const { findProject } = require('../project');
const { planProject } = require('../setup');
const { ENV_FILE, planEnv } = require('../setup/steps/env');
const { loadTypeScript } = require('../typescript');
const { createUI } = require('../ui');
const {
  installCommand,
  isPackageManager,
  packageManagerOf,
  runScript,
} = require('./packageManager');
const { TEMPLATES, planStarter } = require('./starter');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('../doctor/types').Check} Check */

// React Native's own init, at the version asked for. Its package and its program are named apart: under `npx --package`
// (how this CLI may be run) npm hands the package down to a nested npx, which would take a bare name for a program.
const REACT_NATIVE_CLI = '@react-native-community/cli@latest';
const REACT_NATIVE_CLI_BIN = 'rnc-cli';
// Making the app and installing its dependencies take minutes; under --json nothing shows how far they are.
const LONG = 20 * 60_000;

/**
 * Makes a new React Native app with the navigation set up: React Native's init at the newest patch of the version the
 * navigation was checked with, the navigation in its dependencies, setup with a starter of two screens in place of the
 * template's App, one install, pods on macOS, then the checks of this machine for iOS and Android.
 * @param {{
 *   cwd: string;
 *   name: string | undefined;
 *   version?: string;
 *   pm?: string;
 *   directory?: string;
 *   packageName?: string;
 *   title?: string;
 *   template?: string;
 *   skipGitInit?: boolean;
 *   skipInstall?: boolean;
 *   skipPods?: boolean;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   inTerminal?: typeof execInTerminal;
 *   env?: NodeJS.ProcessEnv;
 *   platform?: NodeJS.Platform;
 *   home?: string;
 *   write?: (text: string) => void;
 * }} options `version`: a version of React Native, a range or a tag; `pm`: npm, yarn or bun, the one the command was
 *   run with by default
 * @returns {Promise<number>} the exit code
 */
async function init(options) {
  const {
    cwd,
    name,
    json = false,
    ui = createUI(),
    run = exec,
    inTerminal = execInTerminal,
    env = process.env,
    platform = process.platform,
    home = os.homedir(),
    write = (text) => process.stdout.write(text),
  } = options;
  const detected = packageManagerOf(env);
  const manager = options.pm ?? detected.manager;
  const root = path.resolve(cwd, options.directory ?? name ?? '.');
  const folder = path.relative(cwd, root) || '.';

  // Under --json the run says nothing: init prints one document at the end, with what it found on the way.
  // The files of a new app need no diffs in the terminal: --json has them.
  const command = createRun({
    command: 'init',
    root,
    json,
    ui: { ...ui, diff() {} },
    out: { write: () => true },
  });
  /** @type {{ reactNative?: string; packageManager: string; checks: Check[] }} */
  const found = { packageManager: manager, checks: [] };
  const finish = (/** @type {number} */ code) => {
    if (json) write(JSON.stringify({ ...command.result, ...found }, null, 2) + '\n');
    return code;
  };
  const fail = (/** @type {string} */ code, /** @type {string} */ message) =>
    finish(command.fail(code, message));

  if (!json) {
    ui.banner({
      command: `init ${name ?? ''}`.trim(),
      facts: [
        { text: `React Native ${options.version ?? TESTED.join('.')}` },
        { text: manager },
        { text: options.template ?? 'stack' },
      ],
    });
  }
  if (!name || !/^[A-Za-z][A-Za-z0-9]*$/.test(name)) {
    return fail(
      'init-name-invalid',
      `The name of the app is one word of letters and digits that starts with a letter, such as MyApp: npx ${PACKAGE} init MyApp`
    );
  }
  if (!isPackageManager(manager)) {
    return fail('init-pm-invalid', `--pm is npm, yarn or bun, not ${manager}`);
  }
  const template = options.template ?? 'stack';
  if (!isTemplate(template)) {
    return fail('template-unknown', `--template is ${TEMPLATES.join(', ')}, not ${template}`);
  }
  if (options.dryRun) {
    return fail(
      'init-dry-run',
      'init makes a new app, so there is nothing to show before it is made: run it without --dry-run.'
    );
  }
  // A tool can't answer a question, and init makes a whole app: it says so with --yes.
  if (json && !options.yes) {
    return fail('init-needs-yes', 'With --json, init makes the app only with --yes.');
  }
  if (fs.existsSync(root) && fs.readdirSync(root).length > 0) {
    return fail(
      'init-directory-exists',
      `${folder} exists and isn't empty: give the app another name, or --directory.`
    );
  }
  if (detected.unknown && options.pm === undefined) {
    command.warn(
      `React Native's init doesn't know ${detected.unknown}: the app uses npm (--pm yarn or bun picks another).`
    );
  }

  const spec = options.version ?? `~${TESTED.join('.')}.0`;
  const version = resolveVersion(run, spec);
  if (!version) {
    return fail(
      'react-native-version-unknown',
      `npm couldn't say which React Native ${spec} is: is the network there, and is the version right?`
    );
  }
  if (!isSupported(version)) {
    return fail(
      'react-native-unsupported',
      `React Native ${version} is too old: the navigation needs ${MINIMUM.join('.')} or newer.`
    );
  }
  if (compareVersions(version, `${TESTED.join('.')}.999`) > 0) {
    command.warn(
      `React Native ${version} is newer than ${TESTED.join('.')}, the one the navigation was checked with: setup may leave files to change by hand.`
    );
  }
  found.reactNative = version;

  // 1. React Native's app, without its install: the navigation goes in first, and everything installs once.
  step(`React Native ${version}`, `${REACT_NATIVE_CLI} init`);
  const args = [
    '--yes',
    `--package=${REACT_NATIVE_CLI}`,
    '--',
    REACT_NATIVE_CLI_BIN,
    'init',
    name,
    '--version',
    version,
    '--pm',
    manager,
    '--skip-install',
    '--install-pods',
    'false',
  ];
  if (options.directory) args.push('--directory', options.directory);
  if (options.packageName) args.push('--package-name', options.packageName);
  if (options.title) args.push('--title', options.title);
  if (options.skipGitInit) args.push('--skip-git-init');
  const made = long('npx', args, cwd);
  if (!made.ok || !fs.existsSync(path.join(root, 'package.json'))) {
    return fail('react-native-init-failed', `React Native's init failed${made.output}`);
  }

  // 2. The navigation in package.json, setup, and the starter in place of the template's App.
  const starters = {
    stack: 'Home and Details',
    tabs: 'Home, Profile and Settings in tabs',
    auth: 'Login, then the tabs',
  };
  step('The navigation', `package.json, setup, ${starters[template]}`);
  const changes = planFiles(root, run, template);
  const applied = await command.apply(changes, {
    yes: true,
    dryRun: false,
    done: 'The app is set up for the navigation.',
  });
  // The app is made either way: what is left by hand is in the files above.
  if (!command.result.applied) return finish(applied);

  // 3. One install, then the pods with the navigation's.
  const scripts = {
    install: installCommand(manager),
    ios: runScript(manager, 'ios'),
    android: runScript(manager, 'android'),
  };
  const next = [`cd ${folder}`];
  let installed = false;
  if (options.skipInstall) {
    next.push(scripts.install.flat().join(' '));
  } else {
    step('Dependencies', scripts.install.flat().join(' '));
    const [program, programArgs] = scripts.install;
    const result = long(program, programArgs, root);
    installed = result.ok;
    if (!installed) {
      command.result.ok = false;
      command.result.errors.push({
        code: 'install-failed',
        message: `${scripts.install.flat().join(' ')} failed${result.output}`,
      });
      if (!json) ui.error(`${scripts.install.flat().join(' ')} failed: its output is above.`);
      next.push(scripts.install.flat().join(' '));
    }
  }
  if (platform === 'darwin' && !options.skipPods && installed) {
    step('Pods', podsCommand(root).join(' && '));
    const pods = installPods({ root, json, run, inTerminal });
    if (!pods.ok) {
      command.result.ok = false;
      command.result.errors.push({
        code: 'pods-failed',
        message: `${pods.command} failed${pods.output}`,
      });
      if (!json) ui.error(`pod install failed${pods.output}`);
      next.push(pods.command);
    }
  } else if (platform === 'darwin') {
    next.push(`cd ios && ${podsCommand(root).join(' && ')} && cd ..`);
  }
  next.push(`${scripts.ios} · ${scripts.android}`);
  command.result.next = next;

  // 4. What this machine still needs to build the app.
  const context = { root, run, env, platform, home };
  found.checks = [...androidChecks(context), ...iosChecks(context)];
  const problems = found.checks.filter(({ status }) => status === 'error' || status === 'warn');
  if (!json) {
    ui.line('');
    if (problems.length > 0) {
      ui.muted('  Left to do on this machine');
      ui.checks(
        [
          ['android', 'Android'],
          ['ios', 'iOS'],
        ]
          .map(([group, title]) => ({
            title,
            checks: problems.filter((check) => check.group === group),
          }))
          .filter(({ checks }) => checks.length > 0)
      );
    }
    const left = options.skipInstall || problems.some(({ status }) => status === 'error');
    if (!command.result.ok) ui.warn(`${name} is made, with the problems above left.`);
    else if (left) ui.done(`${name} is made: what is left is above and below.`);
    else ui.done(`${name} is ready.`);
    ui.next(next);
  }
  return finish(command.result.ok ? 0 : 1);

  /**
   * The header of a step in the terminal.
   * @param {string} title
   * @param {string} detail
   */
  function step(title, detail) {
    if (json) return;
    ui.line('');
    ui.done(`${title}   ${detail}`);
    ui.line('');
  }

  /**
   * A program that takes long: its output in the terminal, or kept for the result under --json.
   * @param {string} program
   * @param {string[]} programArgs
   * @param {string} where
   * @returns {{ ok: boolean; output: string }} `output`: the end of what it printed, after a colon, or that it is
   *   above
   */
  function long(program, programArgs, where) {
    if (!json) {
      const ok = inTerminal(program, programArgs, { cwd: where });
      return { ok, output: ok ? '' : ': its output is above.' };
    }
    const result = run(program, programArgs, { cwd: where, timeout: LONG });
    const tail = (result.stderr || result.stdout).trim().split('\n').slice(-5).join('\n');
    return { ok: result.ok, output: tail ? `:\n${tail}` : '.' };
  }
}

/**
 * The files of the new app: the navigation in its dependencies, setup's changes, and the starter of the template in
 * place of the template's App. Without the template's entry point (a newer React Native), setup's own plan stays as
 * it is, its App the first screen.
 * @param {string} root
 * @param {import('../exec').Exec} run
 * @param {import('./starter').Template} [template]
 * @returns {FileChange[]}
 */
function planFiles(root, run, template = 'stack') {
  const project = findProject(root, run);
  const setup = planProject(project);
  const entry = setup.find(({ file }) => file === project.entry);
  const files = [dependencyChange(root)];
  if (entry?.status !== 'change') return [...files, ...setup];
  const navigation = path.join(root, 'Navigation.ts');
  // The starter's Navigation.ts of auth exports the type of its context: the types file registers it too.
  const types = planEnv(
    root,
    { file: navigation, exported: 'Navigation', context: template === 'auth' },
    loadTypeScript(root)
  );
  return [
    ...files,
    // Setup's Navigation.ts, App.tsx and types make way for the starter's.
    ...setup.filter(
      ({ file, name }) =>
        file !== navigation && file !== project.app && name !== ENV_FILE && name !== 'tsconfig.json'
    ),
    ...types,
    ...planStarter(root, project.app, template),
  ];
}

/**
 * @param {string} name
 * @returns {name is import('./starter').Template}
 */
function isTemplate(name) {
  return /** @type {readonly string[]} */ (TEMPLATES).includes(name);
}

/**
 * package.json with the navigation in its dependencies, in their order, at the version of this CLI.
 * @param {string} root
 * @returns {FileChange}
 */
function dependencyChange(root) {
  const file = path.join(root, 'package.json');
  const before = fs.readFileSync(file, 'utf8');
  const json = JSON.parse(before);
  const version = JSON.parse(
    fs.readFileSync(path.join(__dirname, '..', '..', 'package.json'), 'utf8')
  ).version;
  const dependencies = { ...json.dependencies, [PACKAGE]: `^${version}` };
  json.dependencies = Object.fromEntries(
    Object.keys(dependencies)
      .sort()
      .map((key) => [key, dependencies[key]])
  );
  const indent = /^[ \t]+/m.exec(before)?.[0] ?? '  ';
  return {
    name: 'package.json',
    platform: 'js',
    file,
    status: 'change',
    before,
    next: JSON.stringify(json, null, indent) + '\n',
    note: `${PACKAGE} ${version}`,
  };
}

/**
 * The newest React Native of a version, a range or a tag, as npm says; undefined when it can't say.
 * @param {import('../exec').Exec} run
 * @param {string} spec
 * @returns {string | undefined}
 */
function resolveVersion(run, spec) {
  const result = run('npm', ['view', `react-native@${spec}`, 'version', '--json'], {
    timeout: 60_000,
  });
  if (!result.ok || result.stdout.trim() === '') return undefined;
  try {
    /** @type {unknown} */
    const parsed = JSON.parse(result.stdout);
    const versions = (Array.isArray(parsed) ? parsed : [parsed]).filter(
      (version) => typeof version === 'string'
    );
    return versions.sort(compareVersions).at(-1);
  } catch {
    return undefined;
  }
}

/**
 * Compares the numbers of two versions, such as `0.87.2` and `0.87.10`; a pre-release by its numbers only.
 * @param {string} a
 * @param {string} b
 */
function compareVersions(a, b) {
  const parts = (/** @type {string} */ version) =>
    version
      .split('-')[0]
      .split('.')
      .map((part) => Number(part) || 0);
  const [left, right] = [parts(a), parts(b)];
  for (let index = 0; index < 3; index++) {
    const difference = (left[index] ?? 0) - (right[index] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

module.exports = { init, planFiles, resolveVersion };

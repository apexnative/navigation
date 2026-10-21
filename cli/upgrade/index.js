// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { PACKAGE, installedVersion, packageDir, reactNativeVersion } = require('../app');
const { readConfig } = require('../config');
const { exec, execInTerminal } = require('../exec');
const { installPods, podsCommand } = require('../pods');
const { findProject } = require('../project');
const { createUI } = require('../ui');
const { notesBetween } = require('./changelog');
const { compare, satisfies } = require('./versions');

/** @typedef {import('./changelog').Notes} Notes */

// Installing a package, and the setup that follows, take minutes; under --json nothing shows how far they are.
const LONG = 20 * 60_000;
const RELEASES = 'https://github.com/apexnative/navigation/releases';

/**
 * What `upgrade` came to: the versions, each step with whether it went through, what changed between the versions,
 * and the problems.
 * @typedef {{
 *   command: 'upgrade';
 *   ok: boolean;
 *   from?: string;
 *   to?: string;
 *   steps: { name: string; ok: boolean; detail?: string; result?: unknown }[];
 *   notes?: Notes;
 *   warnings: string[];
 *   errors: { code: string; message: string }[];
 *   next: string[];
 * }} UpgradeResult
 */

/**
 * Upgrades the navigation of the app: the new version from the registry, when the app's React Native is in its
 * range; then that version's own setup (the native files it needs now), its codegen for native screens, the pods
 * once, and what changed between the versions from its CHANGELOG.md, breaking changes first. React Native itself is
 * not upgraded.
 * @param {{
 *   root: string;
 *   version?: string;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   inTerminal?: typeof execInTerminal;
 *   platform?: NodeJS.Platform;
 *   write?: (text: string) => void;
 * }} options `version`: a version, a range or a tag (`latest` by default)
 * @returns {Promise<number>} the exit code
 */
async function upgrade({
  root,
  version: spec = 'latest',
  yes,
  dryRun,
  json = false,
  ui = createUI(),
  run = exec,
  inTerminal = execInTerminal,
  platform = process.platform,
  write = (text) => process.stdout.write(text),
}) {
  /** @type {UpgradeResult} */
  const result = { command: 'upgrade', ok: true, steps: [], warnings: [], errors: [], next: [] };
  const finish = () => {
    if (json) write(JSON.stringify(result, null, 2) + '\n');
    return result.ok ? 0 : 1;
  };
  const fail = (/** @type {string} */ code, /** @type {string} */ message) => {
    result.ok = false;
    result.errors.push({ code, message });
    if (!json) ui.error(message);
    return finish();
  };
  const warn = (/** @type {string} */ message) => {
    result.warnings.push(message);
    if (!json) ui.warn(message);
  };

  const reactNative = reactNativeVersion(root);
  const from = installedVersion(root, PACKAGE);
  if (!json) {
    ui.banner({
      command: 'upgrade',
      facts: [
        { text: reactNative ? `React Native ${reactNative}` : 'React Native not found' },
        { text: from ? `navigation ${from}` : 'navigation not installed' },
      ],
    });
  }
  if (!reactNative) {
    return fail(
      'react-native-missing',
      "React Native isn't installed here. Run the command in the root of your app."
    );
  }
  if (!from) {
    return fail(
      'navigation-missing',
      `${PACKAGE} isn't installed here: install it, then run npx apex-navigation setup.`
    );
  }
  result.from = from;

  // 1. The version asked for, and the React Native it takes.
  const found = registryVersion(run, spec);
  if ('code' in found) return fail(found.code, found.message);
  const to = found.version;
  result.to = to;
  const range = found.reactNative;
  if (range && !satisfies(reactNative, range)) {
    return fail(
      'react-native-unsupported',
      `${PACKAGE} ${to} takes React Native ${range}, the app has ${reactNative}: upgrade React Native first, https://react-native-community.github.io/upgrade-helper/?from=${reactNative}`
    );
  }
  if (compare(to, from) === 0 || (spec === 'latest' && compare(to, from) < 0)) {
    if (!json) ui.done(`${PACKAGE} ${from} is up to date.`);
    return finish();
  }
  if (compare(to, from) < 0) warn(`${to} is older than ${from}: this goes back a version.`);

  // 2. The plan, asked.
  const install = installCommand(root, `${PACKAGE}@${to}`);
  const nativeScreens = usesNativeScreens(root, run);
  const pods = platform === 'darwin' && fs.existsSync(path.join(root, 'ios', 'Podfile'));
  const plan = [
    install.join(' '),
    `setup of ${to}: the native files this version needs`,
    ...(nativeScreens ? [`codegen of ${to}: the generated code of the native screens`] : []),
    ...(pods ? [podsCommand(root).join(' && ')] : []),
  ];
  if (!json) {
    ui.line(`  ${from} → ${to}`);
    plan.forEach((step, index) => ui.muted(`  ${index + 1}. ${step}`));
    ui.line('');
  }
  if (dryRun) {
    result.steps = plan.map((name) => ({ name, ok: true, detail: 'not run: dry run' }));
    if (!json) ui.muted('  Dry run: nothing changed.');
    return finish();
  }
  if (json && !yes) {
    result.steps = plan.map((name) => ({ name, ok: true, detail: 'not run' }));
    result.warnings.push('Nothing changed: run with --yes to upgrade.');
    return finish();
  }
  if (!yes && !(await ui.confirm(`Upgrade to ${to}?`))) {
    ui.muted('  Nothing changed.');
    return finish();
  }

  // 3. The package. The rest is the new version's own CLI: its setup knows what its native side needs.
  const installed = long(install[0], install.slice(1));
  result.steps.push({
    name: install.join(' '),
    ok: installed.ok,
    detail: installed.output || undefined,
  });
  if (!installed.ok) {
    return fail('install-failed', `${install.join(' ')} failed${installed.output}`);
  }
  const dir = packageDir(root, PACKAGE);
  const cli = dir && path.join(dir, 'cli', 'index.js');
  if (!cli || !fs.existsSync(cli)) {
    return fail('cli-missing', `${PACKAGE} ${to} has no CLI at ${cli ?? 'node_modules'}.`);
  }
  const flags = [...(yes ? ['--yes'] : []), ...(json ? ['--json'] : [])];
  const steps = [['setup', '--skip-pods'], ...(nativeScreens ? [['codegen']] : [])];
  for (const [name, ...args] of steps) {
    if (!json) {
      ui.line('');
      ui.done(`${name} of ${to}`);
    }
    const step = long(process.execPath, [cli, name, ...args, ...flags]);
    /** @type {unknown} */
    let parsed;
    if (json) {
      try {
        parsed = JSON.parse(step.stdout);
      } catch {
        parsed = undefined;
      }
    }
    result.steps.push({ name: `${name} of ${to}`, ok: step.ok, result: parsed });
    if (!step.ok) {
      result.ok = false;
      result.errors.push({
        code: `${name}-failed`,
        message: `${name} of ${to} left files to change by hand, or failed: npx apex-navigation ${name} shows them.`,
      });
    }
  }
  if (pods) {
    if (!json) {
      ui.line('');
      ui.done(`Pods   ${podsCommand(root).join(' && ')}`);
    }
    const installedPods = installPods({ root, json, run, inTerminal });
    result.steps.push({
      name: 'pod install',
      ok: installedPods.ok,
      detail: installedPods.output || undefined,
    });
    if (!installedPods.ok) {
      result.ok = false;
      result.errors.push({
        code: 'pods-failed',
        message: `${installedPods.command} failed${installedPods.output}`,
      });
      result.next.push(installedPods.command);
    }
  }

  // 4. What changed, breaking changes first.
  const changelog = path.join(dir, 'CHANGELOG.md');
  if (fs.existsSync(changelog)) {
    result.notes = notesBetween(fs.readFileSync(changelog, 'utf8'), from, to);
    if (!json) printNotes(ui, result.notes);
  } else {
    result.next.push(`What changed: ${RELEASES}`);
  }
  result.next.push('npx react-native run-ios · run-android');
  if (!json) {
    ui.line('');
    if (result.ok) ui.done(`${PACKAGE} ${to}.`);
    else ui.warn(`${PACKAGE} ${to}, with the problems above left.`);
    ui.next(result.next);
  }
  return finish();

  /**
   * A program that takes long: its output in the terminal, or kept for the result under --json.
   * @param {string} program
   * @param {string[]} args
   * @returns {{ ok: boolean; output: string; stdout: string }}
   */
  function long(program, args) {
    if (!json) {
      const ok = inTerminal(program, args, { cwd: root });
      return { ok, output: ok ? '' : ': its output is above.', stdout: '' };
    }
    const done = run(program, args, { cwd: root, timeout: LONG });
    const tail = (done.stderr || done.stdout).trim().split('\n').slice(-5).join('\n');
    return { ok: done.ok, output: done.ok ? '' : tail ? `:\n${tail}` : '.', stdout: done.stdout };
  }
}

/**
 * The newest version of the navigation a version, range or tag names, with the React Native range it takes.
 * @param {import('../exec').Exec} run
 * @param {string} spec
 * @returns {{ version: string; reactNative?: string } | { code: string; message: string }}
 */
function registryVersion(run, spec) {
  const asked = run(
    'npm',
    ['view', `${PACKAGE}@${spec}`, 'version', 'peerDependencies', '--json'],
    {
      timeout: 60_000,
    }
  );
  if (!asked.ok) {
    return /E404|No match found/.test(asked.stderr + asked.stdout)
      ? { code: 'version-unknown', message: `npm has no ${PACKAGE}@${spec}.` }
      : {
          code: 'registry-unreachable',
          message: `npm couldn't say which ${PACKAGE}@${spec} is: is the network there?`,
        };
  }
  try {
    /** @type {unknown} */
    const parsed = JSON.parse(asked.stdout);
    // One version gives an object; a range that takes several gives a list of them.
    const entries =
      /** @type {{ version?: string; peerDependencies?: Record<string, string> }[]} */ (
        Array.isArray(parsed) ? parsed : [parsed]
      ).filter((entry) => typeof entry?.version === 'string');
    const newest = entries.sort((a, b) => compare(String(a.version), String(b.version))).at(-1);
    if (!newest?.version) throw new Error('no version');
    return { version: newest.version, reactNative: newest.peerDependencies?.['react-native'] };
  } catch {
    return { code: 'version-unknown', message: `npm has no ${PACKAGE}@${spec}.` };
  }
}

/**
 * The command that installs a package with the app's package manager, the one of its lockfile.
 * @param {string} root
 * @param {string} spec such as `@apexnative/navigation@1.2.0`
 * @returns {string[]}
 */
function installCommand(root, spec) {
  const has = (/** @type {string} */ name) => fs.existsSync(path.join(root, name));
  if (has('yarn.lock')) return ['yarn', 'add', spec];
  if (has('bun.lock') || has('bun.lockb')) return ['bun', 'add', spec];
  if (has('pnpm-lock.yaml')) return ['pnpm', 'add', spec];
  return ['npm', 'install', spec];
}

/**
 * Whether the app has native screens made by the CLI: their settings, or the code codegen generated.
 * @param {string} root
 * @param {import('../exec').Exec} run
 */
function usesNativeScreens(root, run) {
  if (Object.keys(readConfig(root).nativeScreens ?? {}).length > 0) return true;
  const { appDelegate, androidSources } = findProject(root, run);
  return Boolean(
    (appDelegate &&
      fs.existsSync(path.join(path.dirname(appDelegate), 'ApexNavigation.generated.swift'))) ||
    (androidSources &&
      fs
        .readdirSync(androidSources, { recursive: true })
        .some((file) => String(file).endsWith('ApexNavigationGenerated.kt')))
  );
}

/**
 * The notes in the terminal: the breaking changes of each version, then everything, newest first.
 * @param {import('../ui').UI} ui
 * @param {Notes} notes
 */
function printNotes(ui, notes) {
  if (notes.notes.length === 0) return;
  ui.line('');
  if (notes.breaking.length > 0) {
    ui.warn('Breaking changes');
    for (const { version, text } of notes.breaking) {
      ui.line(`  ${version}`);
      text.split('\n').forEach((line) => ui.line(`    ${line}`));
    }
    ui.line('');
  }
  ui.muted('  What changed');
  for (const { version, text } of notes.notes) {
    ui.line(`  ${version}`);
    text.split('\n').forEach((line) => ui.muted(`    ${line}`));
  }
}

module.exports = { upgrade, registryVersion, installCommand };

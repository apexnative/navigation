// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { upgrade } = require('..');
const { notesBetween } = require('../changelog');
const { compare, satisfies } = require('../versions');
const { createUI } = require('../../ui');

const PACKAGE = path.join('node_modules', '@apexnative', 'navigation');
const CHANGELOG = `# Changelog

## [1.2.0](https://example.com) (2026-11-01)

### ⚠ BREAKING CHANGES

* drop the old option

### Features

* a new command

## [1.1.0](https://example.com) (2026-10-20)

### Bug Fixes

* a fix

# [1.0.0](https://example.com) (2026-10-01)

* the first
`;

/** An app with React Native and the navigation 1.0.0, whose new CLI prints what it ran. */
function app() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-upgrade-'));
  const put = (/** @type {string} */ file, /** @type {string} */ text) => {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), text);
  };
  put('package.json', '{ "name": "MyApp" }\n');
  put('yarn.lock', '');
  put('node_modules/react-native/package.json', '{ "version": "0.87.1" }');
  put(path.join(PACKAGE, 'package.json'), '{ "version": "1.0.0" }');
  return { root, put };
}

/**
 * The registry and the package manager: npm says the version, yarn installs it with its changelog, node runs its CLI.
 * @param {(file: string, text: string) => void} put
 * @param {{ range?: string; registry?: boolean; changelog?: boolean }} [options]
 */
function machine(put, { range = '>=0.85.0', registry = true, changelog = true } = {}) {
  /** @type {string[]} */
  const calls = [];
  /** @type {import('../../exec').Exec} */
  const run = (program, args) => {
    calls.push(
      [
        path.basename(program),
        ...args.map((arg) => (path.isAbsolute(arg) ? path.basename(arg) : arg)),
      ].join(' ')
    );
    if (program === 'npm') {
      return registry
        ? {
            ok: true,
            stdout: JSON.stringify({
              version: '1.2.0',
              peerDependencies: { 'react-native': range },
            }),
            stderr: '',
          }
        : { ok: false, stdout: '', stderr: 'ENOTFOUND registry.npmjs.org' };
    }
    if (program === 'yarn') {
      put(path.join(PACKAGE, 'package.json'), '{ "version": "1.2.0" }');
      put(path.join(PACKAGE, 'cli', 'index.js'), '');
      if (changelog) put(path.join(PACKAGE, 'CHANGELOG.md'), CHANGELOG);
      return { ok: true, stdout: '', stderr: '' };
    }
    return { ok: true, stdout: JSON.stringify({ command: args[1], ok: true }), stderr: '' };
  };
  return { run, calls };
}

/**
 * @param {string} root
 * @param {import('../../exec').Exec} run
 * @param {{ version?: string; yes?: boolean; dryRun?: boolean }} [options]
 */
async function json(root, run, { version, yes = true, dryRun = false } = {}) {
  let text = '';
  const out = { isTTY: false, columns: 80, write: () => true };
  const code = await upgrade({
    root,
    version,
    yes,
    dryRun,
    json: true,
    ui: createUI({ out, level: 'none' }),
    run,
    platform: 'linux',
    write: (chunk) => (text += chunk),
  });
  return { code, result: JSON.parse(text) };
}

describe('the versions', () => {
  it.each([
    ['0.87.1', '>=0.85.0', true],
    ['0.84.9', '>=0.85.0', false],
    ['0.87.1', '^0.87.0', true],
    ['0.88.0', '^0.87.0', false],
    ['1.4.0', '^1.2.0', true],
    ['2.0.0', '^1.2.0', false],
    ['0.87.5', '~0.87.1', true],
    ['0.88.0', '~0.87.1', false],
    ['0.87.1', '0.85 - 0.87', true],
    ['0.88.0', '>=0.85 <0.88', false],
    ['0.90.0-rc.1', '>=0.85.0', true],
    ['0.87.1', '0.86.x || 0.87.x', true],
    ['0.87.1', '*', true],
  ])('%s in %s: %s', (version, range, expected) => {
    expect(satisfies(version, range)).toBe(expected);
  });

  it('put a pre-release before its release', () => {
    expect(compare('1.0.0-alpha.0', '1.0.0')).toBeLessThan(0);
    expect(compare('0.87.10', '0.87.2')).toBeGreaterThan(0);
    // Identifiers of a pre-release as numbers: alpha.10 is after alpha.9.
    expect(compare('1.0.0-alpha.10', '1.0.0-alpha.9')).toBeGreaterThan(0);
    expect(compare('1.0.0-alpha.1', '1.0.0-beta.0')).toBeLessThan(0);
    expect(compare('1.0.0-alpha', '1.0.0-alpha.1')).toBeLessThan(0);
  });
});

describe('the changelog', () => {
  it('gives the versions after the app has, newest first, the breaking changes apart', () => {
    expect(notesBetween(CHANGELOG, '1.0.0', '1.2.0')).toEqual({
      breaking: [{ version: '1.2.0', text: '* drop the old option' }],
      notes: [
        {
          version: '1.2.0',
          text: '### ⚠ BREAKING CHANGES\n\n* drop the old option\n\n### Features\n\n* a new command',
        },
        { version: '1.1.0', text: '### Bug Fixes\n\n* a fix' },
      ],
    });
  });
});

describe('upgrade', () => {
  it('installs the version, runs its own setup, and gives what changed', async () => {
    const { root, put } = app();
    const { run, calls } = machine(put);
    const { code, result } = await json(root, run);
    expect(code).toBe(0);
    expect(result).toMatchObject({ ok: true, from: '1.0.0', to: '1.2.0' });
    // The React Native CLI says where the native files are, to tell whether the app has native screens.
    expect(calls).toEqual([
      'npm view @apexnative/navigation@latest version peerDependencies --json',
      'npx --no-install react-native config',
      'yarn add @apexnative/navigation@1.2.0',
      `${path.basename(process.execPath)} index.js setup --skip-pods --yes --json`,
    ]);
    expect(result.steps.map((/** @type {any} */ step) => [step.name, step.ok])).toEqual([
      ['yarn add @apexnative/navigation@1.2.0', true],
      ['setup of 1.2.0', true],
    ]);
    expect(result.notes.breaking).toEqual([{ version: '1.2.0', text: '* drop the old option' }]);
  });

  it('links the releases when the version has no changelog', async () => {
    const { root, put } = app();
    const { result } = await json(root, machine(put, { changelog: false }).run);
    expect(result.next[0]).toBe('What changed: https://github.com/apexnative/navigation/releases');
  });

  it('stops when the React Native of the app is out of the range of the version', async () => {
    const { root, put } = app();
    const { code, result } = await json(root, machine(put, { range: '>=0.88.0' }).run);
    expect(code).toBe(1);
    expect(result.errors[0].code).toBe('react-native-unsupported');
  });

  it('says the app is up to date, and shows the plan on a dry run', async () => {
    const { root, put } = app();
    put(path.join(PACKAGE, 'package.json'), '{ "version": "1.2.0" }');
    const same = await json(root, machine(put).run);
    expect(same).toMatchObject({ code: 0, result: { ok: true, steps: [] } });
    put(path.join(PACKAGE, 'package.json'), '{ "version": "1.0.0" }');
    const { run, calls } = machine(put);
    const dry = await json(root, run, { dryRun: true });
    expect(dry.result.steps.map((/** @type {any} */ step) => step.detail)).toEqual([
      'not run: dry run',
      'not run: dry run',
    ]);
    expect(calls.filter((call) => call.startsWith('yarn'))).toEqual([]);
  });

  it('fails without the registry', async () => {
    const { root, put } = app();
    const { code, result } = await json(root, machine(put, { registry: false }).run);
    expect(code).toBe(1);
    expect(result.errors[0].code).toBe('registry-unreachable');
  });
});

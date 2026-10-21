#!/usr/bin/env node
// Checks the machine of a contributor: the repository's own tools, then the Showcase as any app is checked by
// `apex-navigation doctor`, so the checks of JDK, the Android SDK, Xcode, pods and devices live in one place (cli/).
// Usage: yarn doctor [--json]
import { existsSync, readFileSync, statSync } from 'node:fs';
import { createRequire } from 'node:module';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { parseArgs } from 'node:util';

const require = createRequire(import.meta.url);
const { packageDir } = require('../cli/app');
const { collect, report } = require('../cli/doctor');
const { exec } = require('../cli/exec');
const { createUI } = require('../cli/ui');

const root = new URL('..', import.meta.url).pathname;
const example = join(root, 'example');

/** @type {[string, string][]} */
const GROUPS = [
  ['repository', 'Repository'],
  ['app', 'Showcase'],
  ['android', 'Android'],
  ['ios', 'iOS'],
  ['devices', 'Devices'],
];

const read = (file) =>
  existsSync(join(root, file)) ? readFileSync(join(root, file), 'utf8') : undefined;
const check = (title, status, detail, fix) => ({
  group: 'repository',
  title,
  status,
  ...(detail && { detail }),
  ...(fix && { fix: { label: fix } }),
});

/** Whether a version is in an npm range such as `^22.13.0 || >= 26.0.0` (the forms React Native's engines use). */
function satisfies(version, range) {
  const parse = (text) => (/(\d+)\.(\d+)\.(\d+)/.exec(text) ?? []).slice(1, 4).map(Number);
  const [major, minor, patch] = parse(version);
  const atLeast = (other) =>
    major !== other[0]
      ? major > other[0]
      : minor !== other[1]
        ? minor > other[1]
        : patch >= other[2];
  return range.split('||').some((part) => {
    const bound = parse(part);
    if (part.trim().startsWith('^')) return major === bound[0] && atLeast(bound);
    return atLeast(bound);
  });
}

/** Node: in the range React Native needs; `.nvmrc` is the one CI uses. */
function nodeCheck() {
  const nvmrc = read('.nvmrc')?.trim();
  const reactNative = packageDir(example, 'react-native');
  const range = reactNative
    ? JSON.parse(readFileSync(join(reactNative, 'package.json'), 'utf8')).engines?.node
    : undefined;
  const detail = `${process.version} (.nvmrc ${nvmrc ?? 'missing'})`;
  if (!range || satisfies(process.version, range)) return check('Node', 'ok', detail);
  return check(
    'Node',
    'error',
    `${detail}: React Native needs ${range}`,
    `nvm install ${nvmrc ?? '22'}`
  );
}

/** Yarn: the release `packageManager` pins, which .yarnrc.yml runs from the repository root. */
function yarnCheck() {
  const expected = JSON.parse(read('package.json')).packageManager?.replace(/^yarn@/, '');
  const result = exec('yarn', ['--version'], { cwd: root });
  const actual = result.ok ? result.stdout.trim() : undefined;
  if (actual === expected) return check('Yarn', 'ok', actual);
  return check(
    'Yarn',
    'error',
    `${actual ?? 'not found'}, ${expected} expected`,
    'Run Yarn from the repository root: .yarnrc.yml points to the pinned release'
  );
}

/** The dependencies are installed, and since yarn.lock last changed. */
function dependenciesCheck() {
  const state = join(root, 'node_modules', '.yarn-state.yml');
  if (!existsSync(state)) return check('Dependencies', 'error', 'not installed', 'yarn');
  if (statSync(state).mtimeMs < statSync(join(root, 'yarn.lock')).mtimeMs) {
    return check('Dependencies', 'error', 'yarn.lock is newer than node_modules', 'yarn');
  }
  return check('Dependencies', 'ok', 'installed');
}

/** Ruby, Bundler and the Showcase's gems (CocoaPods among them), for its pods. macOS only. */
function rubyChecks() {
  if (process.platform !== 'darwin') return [];
  const expected = read('.ruby-version')?.trim();
  const ruby = exec('ruby', ['-e', 'print RUBY_VERSION']);
  const version = ruby.ok ? ruby.stdout.trim() : undefined;
  const bundler = exec('ruby', ['-e', 'require "bundler"; print Bundler::VERSION']);
  const gems = exec('bundle', ['check'], { cwd: example });
  return [
    !version
      ? check('Ruby', 'error', 'not found', `rbenv install ${expected}`)
      : expected && version !== expected
        ? check(
            'Ruby',
            'warn',
            `${version}, .ruby-version has ${expected}`,
            `rbenv install ${expected}`
          )
        : check('Ruby', 'ok', version),
    bundler.ok && Number(bundler.stdout.split('.')[0]) >= 2
      ? check('Bundler', 'ok', bundler.stdout.trim())
      : check(
          'Bundler',
          'error',
          bundler.ok ? bundler.stdout.trim() : 'not found',
          'gem install bundler'
        ),
    gems.ok
      ? check('Gems', 'ok', 'installed')
      : check('Gems', 'error', 'not installed', 'cd example && bundle install'),
  ];
}

/** Watchman: Metro watches files faster with it. */
function watchmanCheck() {
  const result = exec('watchman', ['--version']);
  return result.ok
    ? check('Watchman', 'ok', result.stdout.trim())
    : check('Watchman', 'info', 'not installed: optional, brew install watchman');
}

const { values } = parseArgs({ options: { json: { type: 'boolean' } } });

// The Showcase as any app, but with JDK 17 picked per command (scripts/android-env.sh) and the navigation from
// source: it isn't in node_modules, and conflicts with other libraries don't apply to it.
const showcase = collect({
  root: example,
  run: exec,
  env: process.env,
  platform: process.platform,
  home: homedir(),
  jdk: 'installed',
})
  .filter((item) => item.group !== 'conflicts')
  .map((item) =>
    item.group === 'app' && item.title === 'Navigation'
      ? { ...item, status: 'ok', detail: 'from source (src/)', fix: undefined }
      : item
  );

const checks = [
  nodeCheck(),
  yarnCheck(),
  dependenciesCheck(),
  ...rubyChecks(),
  watchmanCheck(),
  ...showcase,
];
const failed = checks.some((item) => item.status === 'error');

if (values.json) {
  process.stdout.write(JSON.stringify({ ok: !failed, checks }, null, 2) + '\n');
} else {
  const ui = createUI();
  ui.banner({ command: 'doctor', facts: [{ text: 'repository' }, { text: 'Showcase' }] });
  report(ui, checks, GROUPS);
}
process.exitCode = failed ? 1 : 0;

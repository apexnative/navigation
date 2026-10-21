// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { readConfig, screensDir } = require('../../config');
const { planAndroid, planIos } = require('../../links');
const { declaredLinks } = require('../../links/native');
const { readLinking } = require('../../links/prefixes');
const {
  findConfig,
  findScreens,
  keysOf,
  propertyValue,
  resolveImport,
} = require('../../typescript');
const { openWorkspace } = require('../../workspace');

/** @typedef {import('../types').Check} Check */
/** @typedef {import('../types').Context} Context */
/** @typedef {import('../../workspace').Workspace} Workspace */

const LINKS = { label: 'npx apex-navigation links', run: /** @type {const} */ ('links') };
// Folders of an app that hold no code of its own.
const SKIP = new Set([
  'node_modules',
  'ios',
  'android',
  'build',
  'Pods',
  '.git',
  'vendor',
  'coverage',
]);
const SOURCE = /\.(tsx?|jsx?)$/;
// The commands that name a screen first.
const COMMANDS = ['push', 'replace', 'showModal', 'showInColumn'];
// At most this many places are listed in a check.
const LISTED = 5;

/**
 * The navigation of the app, from its code: its screens, its links on the native side, and, in a JavaScript app,
 * screen names the commands give. None when the CLI can't read the app's code: the App group says why.
 * @param {Context} context
 * @returns {Check[]}
 */
function navigationChecks({ root, run }) {
  const workspace = openWorkspace(root, run);
  if ('code' in workspace) return [];
  const names = screenNames(workspace);
  /** @type {Check[]} */
  const checks = [screensCheck(root, workspace, names)];
  checks.push(...linksChecks(workspace));
  if (!fs.existsSync(path.join(root, 'tsconfig.json'))) {
    const named = screenNamesCheck(root, names.all);
    if (named) checks.push(named);
  }
  return checks;
}

/**
 * The names of the screens: those of `screens`, and of each group, with the names given twice.
 * @param {Workspace} workspace
 */
function screenNames({ ts, navigation }) {
  const screens = findScreens(ts, navigation);
  /** @type {{ name: string; where: string }[]} */
  const listed = (screens ? keysOf(ts, { ...screens, node: screens.object }) : []).map((name) => ({
    name,
    where: 'screens',
  }));
  const groups = findConfig(ts, navigation, 'groups');
  if (groups) {
    for (const group of keysOf(ts, groups)) {
      const value = propertyValue(ts, groups, group);
      const groupScreens = value && propertyValue(ts, value, 'screens');
      for (const name of groupScreens ? keysOf(ts, groupScreens) : []) {
        listed.push({ name, where: `the group ${group}` });
      }
    }
  }
  const seen = new Map();
  /** @type {string[]} */
  const twice = [];
  for (const { name, where } of listed) {
    if (seen.has(name)) twice.push(`${name} in ${seen.get(name)} and in ${where}`);
    else seen.set(name, where);
  }
  return { all: new Set(seen.keys()), twice, count: seen.size };
}

/**
 * One name per screen, and every screen file in `screens`: a file of the screens' folder with a `defineScreen` or
 * `nativeScreen` export that no file of the app imports was likely made and never added.
 * @param {string} root
 * @param {Workspace} workspace
 * @param {ReturnType<typeof screenNames>} names
 * @returns {Check}
 */
function screensCheck(root, workspace, names) {
  if (names.twice.length > 0) {
    return {
      group: 'navigation',
      title: 'Screens',
      status: 'error',
      detail: `named twice: ${names.twice.slice(0, LISTED).join('; ')}`,
      fix: { label: 'Give each screen a name of its own' },
    };
  }
  const dir = path.join(root, screensDir(root, readConfig(root)));
  const files = sourceFiles(root);
  const imported = new Set(files.flatMap((file) => importsOf(file)));
  const unused = sourceFiles(dir)
    .filter(
      (file) => !imported.has(file) && /[=]\s*(defineScreen|nativeScreen)\s*\(/.test(read(file))
    )
    .map((file) => path.relative(root, file));
  if (unused.length > 0) {
    return {
      group: 'navigation',
      title: 'Screens',
      status: 'warn',
      detail: `not in screens: ${unused.slice(0, LISTED).join(', ')}${unused.length > LISTED ? ` and ${unused.length - LISTED} more` : ''}`,
      fix: { label: 'Add them to screens of createNavigation, or delete them' },
    };
  }
  return { group: 'navigation', title: 'Screens', status: 'ok', detail: `${names.count}` };
}

/**
 * The links of `linking` on the native side: each prefix declared on both platforms, the declarations no prefix has,
 * and an Android activity that takes new links in itself.
 * @param {Workspace} workspace
 * @returns {Check[]}
 */
function linksChecks({ ts, navigation, project }) {
  const linking = readLinking(ts, navigation);
  if ('code' in linking) {
    return linking.code === 'no-linking'
      ? []
      : [{ group: 'navigation', title: 'Links', status: 'info', detail: linking.message }];
  }
  const schemes = [
    ...new Set(linking.prefixes.filter(({ web }) => !web).map(({ scheme }) => scheme)),
  ];
  const web = linking.prefixes.filter(({ web: isWeb }) => isWeb);
  const hosts = [...new Set(web.map(({ host }) => host))];
  /** @type {string[]} */
  const singleTask = [];
  const plans = [
    ...planIos(project, schemes, hosts),
    ...planAndroid(project, schemes, web, (message) => singleTask.push(message)),
  ];
  const missing = plans.filter(({ status }) => status !== 'done').map(({ name }) => name);
  /** @type {Check[]} */
  const checks = [
    missing.length === 0
      ? {
          group: 'navigation',
          title: 'Links',
          status: 'ok',
          detail: linking.prefixes.map(({ text }) => text).join(' · '),
        }
      : {
          group: 'navigation',
          title: 'Links',
          status: 'warn',
          detail: `not declared on the native side: ${missing.join(', ')}`,
          fix: LINKS,
        },
  ];
  const declared = declaredLinks(project);
  const extra = [
    ...declared.schemes
      .filter((scheme) => !schemes.includes(scheme))
      .map((scheme) => `${scheme}://`),
    ...declared.hosts.filter((host) => !hosts.includes(host)),
  ];
  if (extra.length > 0) {
    checks.push({
      group: 'navigation',
      title: 'Links',
      status: 'info',
      detail: `declared with no prefix in linking: ${[...new Set(extra)].join(', ')}`,
    });
  }
  if (singleTask.length > 0) {
    checks.push({
      group: 'navigation',
      title: 'Links on Android',
      status: 'warn',
      detail: "the main activity isn't singleTask: a link starts another copy of it",
      fix: { label: 'android:launchMode="singleTask" on the main activity of AndroidManifest.xml' },
    });
  }
  return checks;
}

/**
 * In a JavaScript app, where no type checks them, the screen names the commands give that aren't screens.
 * @param {string} root
 * @param {Set<string>} names
 * @returns {Check | undefined}
 */
function screenNamesCheck(root, names) {
  // On the navigation only: `navigation.push('…')`, `nav.replace('…')`, not `errors.push('…')`.
  const call = new RegExp(
    String.raw`\b(?:navigation|nav|Navigation)\??\.(?:${COMMANDS.join('|')})\(\s*['"]([A-Za-z_$][\w$]*)['"]`,
    'g'
  );
  /** @type {string[]} */
  const unknown = [];
  for (const file of sourceFiles(root)) {
    const text = read(file);
    for (const found of text.matchAll(call)) {
      if (names.has(found[1])) continue;
      const line = text.slice(0, found.index).split('\n').length;
      unknown.push(`${found[1]} at ${path.relative(root, file)}:${line}`);
    }
  }
  if (unknown.length === 0) return undefined;
  return {
    group: 'navigation',
    title: 'Screen names',
    status: 'warn',
    detail: `not screens: ${unknown.slice(0, LISTED).join(', ')}${unknown.length > LISTED ? ` and ${unknown.length - LISTED} more` : ''}`,
    fix: { label: 'Use a name of screens, or add the screen' },
  };
}

/**
 * The files of the app's own code under a folder.
 * @param {string} dir
 * @returns {string[]}
 */
function sourceFiles(dir) {
  /** @type {string[]} */
  const files = [];
  /** @type {fs.Dirent[]} */
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return files;
  }
  for (const entry of entries) {
    if (entry.name.startsWith('.') || SKIP.has(entry.name) || entry.name === '__tests__') continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...sourceFiles(full));
    else if (SOURCE.test(entry.name) && !entry.name.endsWith('.d.ts')) files.push(full);
  }
  return files;
}

/**
 * The files of the app a file imports: `import … from`, `export … from`, `import()` and `require()` of relative
 * paths.
 * @param {string} file
 * @returns {string[]}
 */
function importsOf(file) {
  const text = read(file);
  const specifiers = [
    ...text.matchAll(/\bfrom\s*['"](\.[^'"]+)['"]/g),
    ...text.matchAll(/\b(?:import|require)\(\s*['"](\.[^'"]+)['"]\s*\)/g),
  ].map((found) => found[1]);
  return specifiers.flatMap((specifier) => {
    const target = resolveImport(file, specifier);
    return target ? [target] : [];
  });
}

/**
 * @param {string} file
 * @returns {string}
 */
function read(file) {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return '';
  }
}

module.exports = { navigationChecks };

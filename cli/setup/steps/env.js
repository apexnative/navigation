// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { exportsType, importPath } = require('../../typescript');

/** @typedef {import('../../changes').FileChange} FileChange */
/** @typedef {import('../../typescript').TS} TS */

/** The file that types the commands with the app's screens. */
const ENV_FILE = 'navigation-env.d.ts';

const HEADER = "// Made by apex-navigation setup: the types of the app's screens";

/**
 * Where the navigation is: its module, the name it is exported under (`default` for a default export), and whether
 * the module exports the type of the app's context, `AppContext`.
 * @typedef {{ file: string; exported: string; context: boolean }} NavigationTarget
 */

/**
 * The text of navigation-env.d.ts for the navigation of the app.
 * @param {string} root
 * @param {NavigationTarget} navigation
 * @returns {string}
 */
function envText(root, { file, exported, context }) {
  const module = importPath(path.join(root, ENV_FILE), file);
  const lines = [
    HEADER,
    "// for the navigation's commands. Don't edit.",
    "declare module '@apexnative/navigation' {",
    '  interface Register {',
    `    app: typeof import('${module}').${exported};`,
    ...(context ? [`    context: import('${module}').AppContext;`] : []),
    '  }',
    '}',
    'export {};',
  ];
  return lines.join('\n') + '\n';
}

/**
 * The navigation of an app whose entry point is set up: from the import of the entry point, read with the app's
 * TypeScript.
 * @param {TS} ts
 * @param {{ file: string; exported: string }} imported
 * @returns {NavigationTarget}
 */
function targetOf(ts, imported) {
  return { ...imported, context: exportsType(ts, imported.file, 'AppContext') };
}

/**
 * navigation-env.d.ts and, when its `include` leaves the file out, tsconfig.json: the commands typed with the app's
 * screens. Nothing in an app without tsconfig.json: its names are plain strings.
 * @param {string} root
 * @param {NavigationTarget | undefined} navigation undefined when the entry point isn't set up, so nothing says where
 *   the navigation is yet
 * @param {TS | undefined} ts the app's TypeScript, for a tsconfig.json with comments
 * @returns {FileChange[]}
 */
function planEnv(root, navigation, ts) {
  const tsconfig = path.join(root, 'tsconfig.json');
  if (!fs.existsSync(tsconfig) || !navigation) return [];
  const file = path.join(root, ENV_FILE);
  const next = envText(root, navigation);
  /** @type {FileChange[]} */
  const changes = [];
  if (!fs.existsSync(file)) {
    changes.push({
      name: ENV_FILE,
      platform: 'js',
      file,
      status: 'create',
      next,
      note: 'typed commands',
    });
  } else {
    const before = fs.readFileSync(file, 'utf8');
    if (before === next) {
      changes.push({
        name: ENV_FILE,
        platform: 'js',
        file,
        status: 'done',
        note: 'already set up',
      });
    } else if (before.startsWith(HEADER)) {
      changes.push({
        name: ENV_FILE,
        platform: 'js',
        file,
        status: 'change',
        before,
        next,
        note: navigation.context ? 'the navigation and its context' : 'where the navigation is',
      });
    } else {
      changes.push({
        name: ENV_FILE,
        platform: 'js',
        file,
        status: 'manual',
        note: 'written by hand, see below',
        howTo: `Make ${ENV_FILE} what setup writes, or delete it and run setup again:\n\n${next}`,
      });
    }
  }
  const include = planInclude(tsconfig, ts);
  if (include) changes.push(include);
  return changes;
}

/**
 * tsconfig.json with navigation-env.d.ts in its `include`, when it has one that leaves the file out; undefined when
 * TypeScript takes the file already.
 * @param {string} tsconfig
 * @param {TS | undefined} ts
 * @returns {FileChange | undefined}
 */
function planInclude(tsconfig, ts) {
  const before = fs.readFileSync(tsconfig, 'utf8');
  const config = readJson(before, ts);
  const howTo = `Add "${ENV_FILE}" to "include" of tsconfig.json, so TypeScript reads it.`;
  if (!config) {
    return {
      name: 'tsconfig.json',
      platform: 'js',
      file: tsconfig,
      status: 'manual',
      note: "couldn't be read",
      howTo,
    };
  }
  // Without `include`, TypeScript takes every file of the folder; with `files` only, none but those.
  if (!Array.isArray(config.include)) {
    if (!Array.isArray(config.files) || config.files.includes(ENV_FILE)) return undefined;
    return {
      name: 'tsconfig.json',
      platform: 'js',
      file: tsconfig,
      status: 'manual',
      note: 'lists its files',
      howTo: `Add "${ENV_FILE}" to "files" of tsconfig.json, so TypeScript reads it.`,
    };
  }
  if (config.include.some((/** @type {unknown} */ pattern) => takes(pattern, ENV_FILE))) {
    return undefined;
  }
  const next = addToArray(before, 'include', ENV_FILE);
  if (!next) {
    return {
      name: 'tsconfig.json',
      platform: 'js',
      file: tsconfig,
      status: 'manual',
      note: 'written in a way the CLI keeps',
      howTo,
    };
  }
  return {
    name: 'tsconfig.json',
    platform: 'js',
    file: tsconfig,
    status: 'change',
    before,
    next,
    note: `${ENV_FILE} in include`,
  };
}

/**
 * A tsconfig.json as an object: plain JSON, or with comments and trailing commas through the app's TypeScript.
 * @param {string} text
 * @param {TS | undefined} ts
 * @returns {any}
 */
function readJson(text, ts) {
  try {
    return JSON.parse(text);
  } catch {
    if (!ts) return undefined;
    const { config, error } = ts.parseConfigFileTextToJson('tsconfig.json', text);
    return error ? undefined : config;
  }
}

/**
 * Whether a pattern of `include` takes a file of the app's root: TypeScript's wildcards (`*`, `?`, and `**` for any folders), or a
 * folder and what is under it.
 * @param {unknown} pattern
 * @param {string} file
 */
function takes(pattern, file) {
  if (typeof pattern !== 'string') return false;
  const normalized = pattern.replace(/^\.\//, '');
  if (!/[*?]/.test(normalized)) {
    return normalized === file || normalized === '.' || normalized === '';
  }
  const source = normalized
    .split(/(\*\*\/|\*|\?)/)
    .map((part) => {
      if (part === '**/') return '(?:[^/]+/)*';
      if (part === '*') return '[^/]*';
      if (part === '?') return '[^/]';
      return part.replace(/[.+^${}()|[\]\\]/g, '\\$&');
    })
    .join('');
  return new RegExp(`^${source}$`).test(file);
}

/**
 * The text of a JSON file with a string added last to the array of a top key, written as the array is: on one line, or
 * one item per line with their indentation. Undefined when the array isn't found in the text.
 * @param {string} text
 * @param {string} key
 * @param {string} item
 * @returns {string | undefined}
 */
function addToArray(text, key, item) {
  const found = new RegExp(String.raw`"${key}"\s*:\s*\[([^\]]*)\]`).exec(text);
  if (!found) return undefined;
  const [whole, inside] = found;
  const start = found.index;
  const quoted = JSON.stringify(item);
  /** @type {string} */
  let array;
  if (!inside.includes('\n') || inside.trim() === '') {
    const items = inside.trim().replace(/,$/, '');
    array = `[${items ? `${items}, ` : ''}${quoted}]`;
  } else {
    const lines = inside.split('\n');
    const last = [...lines].reverse().find((line) => line.trim() !== '') ?? '';
    const indent = /^[ \t]*/.exec(last)?.[0] ?? '  ';
    const closing = /\n([ \t]*)$/.exec(inside)?.[1] ?? '';
    const items = inside.trimEnd().replace(/,$/, '');
    array = `[${items},\n${indent}${quoted}\n${closing}]`;
  }
  const head = whole.slice(0, whole.indexOf('['));
  return text.slice(0, start) + head + array + text.slice(start + whole.length);
}

module.exports = { ENV_FILE, planEnv, envText, targetOf, takes, addToArray };

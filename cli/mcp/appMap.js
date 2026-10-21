// @ts-check
'use strict';

const path = require('node:path');
const { createProgram, readCommandScreens } = require('../codegen/read');
const { readLinking } = require('../links/prefixes');
const { findConfig, keysOf } = require('../typescript');
const { openWorkspace } = require('../workspace');

/** @typedef {import('../codegen/read').Model} Model */
/** @typedef {import('../codegen/read').CommandScreenModel} CommandScreenModel */

/**
 * The map of the app's navigation, from its code, for an agent that is about to change it: the screens with their
 * props as TypeScript writes them, the roots, the links, and how the screens render.
 * @typedef {{
 *   navigation: string;
 *   mode: 'one-tree' | 'root-per-screen';
 *   screens: {
 *     name: string;
 *     kind: 'react' | 'native';
 *     props: { name: string; type: string; optional: boolean }[];
 *     note?: string;
 *   }[];
 *   root?: string;
 *   roots?: Record<string, string>;
 *   initialRoot?: string;
 *   linking?: { prefixes: string[]; routes: string[] } | { error: string };
 * }} AppMap
 */

/**
 * The map of the app, or why the CLI can't read its code.
 * @param {string} root
 * @param {import('../exec').Exec} run
 * @returns {AppMap | { error: string }}
 */
function appMap(root, run) {
  const workspace = openWorkspace(root, run);
  if ('code' in workspace) return { error: workspace.message };
  const { ts, navigation } = workspace;
  const { screens, skipped } = readCommandScreens(
    ts,
    createProgram(ts, root, navigation),
    navigation
  );
  const text = (/** @type {string} */ key) => {
    const value = findConfig(ts, navigation, key);
    return value ? value.node.getText(value.source) : undefined;
  };
  const roots = findConfig(ts, navigation, 'roots');
  const linking = readLinking(ts, navigation);
  return {
    navigation: path.relative(root, navigation),
    mode: findConfig(ts, navigation, 'appProvider') ? 'one-tree' : 'root-per-screen',
    screens: [
      ...screens.map((screen) => ({
        name: screen.name,
        kind: screen.native ? /** @type {const} */ ('native') : /** @type {const} */ ('react'),
        props: [
          ...screen.fields.map((field) => ({
            name: field.name,
            type: typeText(field.type, screen),
            optional: field.optional,
          })),
          ...screen.functions.map((name) => ({ name, type: 'function', optional: true })),
        ],
      })),
      ...skipped.map(({ name, native, reason }) => ({
        name,
        kind: native ? /** @type {const} */ ('native') : /** @type {const} */ ('react'),
        props: [],
        note: `props not read: ${reason}`,
      })),
    ],
    ...(text('root') && { root: text('root') }),
    ...(roots && {
      roots: Object.fromEntries(
        keysOf(ts, roots).map((name) => {
          const value =
            ts.isObjectLiteralExpression(roots.node) &&
            roots.node.properties.find((property) => property.name?.getText(roots.source) === name);
          return [name, value ? value.getText(roots.source).replace(/^[^:]+:\s*/, '') : ''];
        })
      ),
    }),
    ...(text('initialRoot') && { initialRoot: text('initialRoot') }),
    ...('code' in linking
      ? linking.code === 'no-linking'
        ? {}
        : { linking: { error: linking.message } }
      : {
          linking: {
            prefixes: linking.prefixes.map(({ text: prefix }) => prefix),
            routes: linking.routes,
          },
        }),
  };
}

/**
 * A prop's type as TypeScript writes it.
 * @param {Model} model
 * @param {CommandScreenModel} screen
 * @returns {string}
 */
function typeText(model, screen) {
  switch (model.kind) {
    case 'enum': {
      const values = screen.enums.find(({ name }) => name === model.name)?.values ?? [];
      return values.map((value) => JSON.stringify(value)).join(' | ') || 'string';
    }
    case 'object': {
      const object = screen.objects.find(({ name }) => name === model.name);
      return object
        ? `{ ${object.fields.map((field) => `${field.name}${field.optional ? '?' : ''}: ${typeText(field.type, screen)}`).join('; ')} }`
        : 'object';
    }
    case 'array':
      return `${typeText(model.element, screen)}[]`;
    case 'function':
      return 'function';
    default:
      return model.kind;
  }
}

module.exports = { appMap };

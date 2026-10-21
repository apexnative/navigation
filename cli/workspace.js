// @ts-check
'use strict';

const { isSupported, reactNativeVersion, MINIMUM } = require('./app');
const { exec } = require('./exec');
const { findProject } = require('./project');
const { loadTypeScript, navigationModule } = require('./typescript');

/**
 * What a command that reads and adds to the app's code works with: its TypeScript, its files, and the module of its
 * navigation (the one the entry point registers).
 * @typedef {{
 *   ts: import('./typescript').TS;
 *   project: import('./project').Project;
 *   navigation: string;
 * }} Workspace
 */

/**
 * The app the command runs in, or the reason it can't: React Native missing or too old, no TypeScript, or an entry
 * point that doesn't register the navigation (setup first).
 * @param {string} root
 * @param {import('./exec').Exec} [run]
 * @returns {Workspace | { code: string; message: string }}
 */
function openWorkspace(root, run = exec) {
  const version = reactNativeVersion(root);
  if (!version) {
    return {
      code: 'react-native-missing',
      message: "React Native isn't installed here. Run the command in the root of your app.",
    };
  }
  if (!isSupported(version)) {
    return {
      code: 'react-native-unsupported',
      message: `React Native ${version} is too old: the navigation needs ${MINIMUM.join('.')} or newer.`,
    };
  }
  const ts = loadTypeScript(root);
  if (!ts) {
    return {
      code: 'typescript-missing',
      message:
        "TypeScript isn't installed here: the CLI reads the app's code with it (npm install -D typescript).",
    };
  }
  const project = findProject(root, run);
  const navigation = project.entry && navigationModule(ts, project.entry);
  if (!navigation) {
    return {
      code: 'navigation-missing',
      message:
        "The entry point doesn't register the navigation: run npx apex-navigation setup first.",
    };
  }
  return { ts, project, navigation };
}

module.exports = { openWorkspace };

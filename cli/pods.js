// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { exec, execInTerminal } = require('./exec');

// pod install of a new app downloads React Native's pods: minutes, with nothing shown under --json.
const LONG = 20 * 60_000;

/**
 * The commands that install the pods in ios/: through Bundler when the app has a Gemfile, as React Native's template
 * does.
 * @param {string} root
 * @returns {string[]}
 */
function podsCommand(root) {
  return fs.existsSync(path.join(root, 'Gemfile'))
    ? ['bundle install', 'bundle exec pod install']
    : ['pod install'];
}

/**
 * Installs the pods in ios/. In the terminal their output shows as it comes; under `json` it is kept, and the end of
 * it goes into the result.
 * @param {{
 *   root: string;
 *   json: boolean;
 *   run?: import('./exec').Exec;
 *   inTerminal?: typeof execInTerminal;
 * }} options
 * @returns {{ ok: boolean; command: string; output: string }} `command`: the line to run by hand; `output`: after a
 *   failure, the end of what it printed after a colon, or that it is above
 */
function installPods({ root, json, run = exec, inTerminal = execInTerminal }) {
  const lines = podsCommand(root);
  const ios = path.join(root, 'ios');
  const command = `cd ios && ${lines.join(' && ')} && cd ..`;
  for (const line of lines) {
    const [program, ...args] = line.split(' ');
    if (!json) {
      if (!inTerminal(program, args, { cwd: ios })) {
        return { ok: false, command, output: ': its output is above.' };
      }
      continue;
    }
    const result = run(program, args, { cwd: ios, timeout: LONG });
    if (!result.ok) {
      const tail = (result.stderr || result.stdout).trim().split('\n').slice(-5).join('\n');
      return { ok: false, command, output: tail ? `:\n${tail}` : '.' };
    }
  }
  return { ok: true, command, output: '' };
}

module.exports = { podsCommand, installPods };

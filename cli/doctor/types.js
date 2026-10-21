// @ts-check
'use strict';

/**
 * One finding of doctor:
 * - ok: as the navigation needs it;
 * - warn: works, but likely to cause trouble;
 * - error: the app won't build or start as it is; `fix` says how to fix it, and `run` names what `--fix` can do;
 * - info: how something works in this app, nothing to fix.
 * @typedef {'repository' | 'app' | 'navigation' | 'android' | 'ios' | 'devices' | 'conflicts'} Group
 * @typedef {'ok' | 'warn' | 'error' | 'info'} Status
 * @typedef {'setup' | 'codegen' | 'pods' | 'links' | 'icons'} FixRun
 * @typedef {{ group: Group; title: string; status: Status; detail?: string; fix?: { label: string; run?: FixRun } }}
 *   Check
 */

/**
 * What the checks look at: the app, the machine, and how to ask its programs. `jdk`: 'active' (an app) needs JDK 17
 * in use; 'installed' (this repository, whose scripts pick it per command) needs it installed.
 * @typedef {{
 *   root: string;
 *   run: import('../exec').Exec;
 *   env: NodeJS.ProcessEnv;
 *   platform: NodeJS.Platform;
 *   home: string;
 *   jdk?: 'active' | 'installed';
 * }} Context
 */

module.exports = {};

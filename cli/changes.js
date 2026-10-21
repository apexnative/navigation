// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { uncommitted } = require('./app');
const { unifiedDiff } = require('./diff');

/**
 * A file a command looked at and what happens to it:
 * - create: a new file, `next` is its text, or `image` its bytes;
 * - change: `before` becomes `next`, or the file becomes `image`;
 * - remove: the file or folder goes, `before` is its text;
 * - done: it is as the command would make it already;
 * - manual: the command can't make the change (the file was changed by hand, or wasn't found); `howTo` says what to do.
 * @typedef {{
 *   name: string;
 *   platform: string;
 *   file?: string;
 *   status: 'create' | 'change' | 'remove' | 'done' | 'manual';
 *   note: string;
 *   before?: string;
 *   next?: string;
 *   image?: Buffer;
 *   howTo?: string;
 * }} FileChange
 */

/**
 * What a command came to, the same for every command; `--json` prints it for CI, tools and AI agents.
 * - ok: nothing stopped the command and nothing is left to change by hand;
 * - applied: the changes were written (not on a dry run, a refusal, or `--json` without `--yes`);
 * - files: every file the command looked at, a change with its unified diff from the app's root;
 * - errors: what stopped the command, each with a code that tells it apart;
 * - next: what to run after the changes.
 * @typedef {{
 *   command: string;
 *   ok: boolean;
 *   applied: boolean;
 *   files: {
 *     name: string;
 *     platform: string;
 *     file?: string;
 *     status: FileChange['status'];
 *     note: string;
 *     diff?: string;
 *     howTo?: string;
 *   }[];
 *   warnings: string[];
 *   errors: { code: string; message: string }[];
 *   next: string[];
 * }} Result
 */

/** @typedef {ReturnType<typeof import('./ui').createUI>} UI */

/**
 * The run of a command that changes files. In text mode it talks to the terminal as it goes; with `json` it says
 * nothing and prints its result once, at the end, so the output is one JSON document.
 * @param {{
 *   command: string;
 *   root: string;
 *   json?: boolean;
 *   ui: UI;
 *   out?: { write(text: string): unknown };
 * }} options
 */
function createRun({ command, root, json = false, ui, out = process.stdout }) {
  /** @type {Result} */
  const result = {
    command,
    ok: true,
    applied: false,
    files: [],
    warnings: [],
    errors: [],
    next: [],
  };

  /** @param {number} code */
  function finish(code) {
    if (json) out.write(JSON.stringify(result, null, 2) + '\n');
    return code;
  }

  /** @param {string} message */
  function warn(message) {
    result.warnings.push(message);
    if (!json) ui.warn(message);
  }

  return {
    result,
    json,

    /**
     * A problem that stops the command, or several of one kind: its exit code is 1.
     * @param {string} code tells the problem apart, such as `react-native-missing`
     * @param {string | string[]} message
     */
    fail(code, message) {
      result.ok = false;
      for (const text of Array.isArray(message) ? message : [message]) {
        result.errors.push({ code, message: text });
        if (!json) ui.error(text);
      }
      return finish(1);
    },

    warn,

    /**
     * Shows the files and the changes, asks unless `yes`, and writes them; a dry run only shows them.
     * @param {FileChange[]} changes
     * @param {{
     *   yes: boolean;
     *   dryRun: boolean;
     *   next?: string[];
     *   done?: string;
     *   nothingToDo?: string;
     *   afterWrite?: (result: Result) => void | Promise<void>;
     * }} options `done` and `nothingToDo` are what it says at the end; `afterWrite` runs once the files are written,
     *   before the end is said, and may add to the result (`setup` installs the pods there)
     * @returns {Promise<number>} the exit code: 1 when files are left to change by hand or `afterWrite` found a
     *   problem, else 0
     */
    async apply(
      changes,
      { yes, dryRun, next = [], done = 'Done', nothingToDo = 'Nothing to change.', afterWrite }
    ) {
      const pending = changes.filter(({ status }) =>
        ['change', 'create', 'remove'].includes(status)
      );
      const manual = changes.filter(({ status }) => status === 'manual');
      const relative = (/** @type {string | undefined} */ file) =>
        file && path.relative(root, file);
      result.files = changes.map(
        ({ name, platform, file, status, note, before = '', next: after, image, howTo }) => ({
          name,
          platform,
          ...(file && { file: relative(file) }),
          status,
          note,
          // An image has no diff.
          ...(!image &&
            (after !== undefined || status === 'remove') && {
              diff: unifiedDiff(relative(file) ?? name, before, after ?? ''),
            }),
          ...(howTo && { howTo }),
        })
      );
      result.ok = manual.length === 0;
      const left = manual.length > 0 ? 1 : 0;

      if (!json) {
        ui.files(
          changes.map(({ status, name, platform, note }) => ({
            kind: status,
            file: name,
            platform,
            note,
          }))
        );
        for (const { name, file, howTo = '' } of manual) {
          ui.warn(`${name}${file ? `  ${relative(file)}` : ''}`);
          howTo.split('\n').forEach((line) => ui.muted(`    ${line}`));
          ui.line('');
        }
      }
      if (pending.length === 0) {
        if (!json) {
          if (manual.length > 0) ui.warn('The files above need a change by hand.');
          else ui.done(nothingToDo);
        }
        return finish(left);
      }

      if (!json) {
        for (const { name, before = '', next: after = '', image } of pending) {
          if (!image) ui.diff(name, before, after);
        }
      }
      if (dryRun) {
        if (!json) ui.muted('  Dry run: nothing written.');
        return finish(0);
      }
      // A tool can't answer a question: without --yes it gets the plan, and nothing is written.
      if (json && !yes) {
        result.warnings.push('Nothing written: run with --yes to apply the changes.');
        return finish(0);
      }

      const files = pending.flatMap(({ file }) => (file ? [file] : []));
      const dirty = uncommitted(root, files);
      if (dirty.length > 0) warn(`Not committed yet: ${dirty.join(', ')}`);
      if (!json) ui.rule();
      const confirmed =
        yes ||
        (await ui.confirm(
          `Apply ${pending.length} ${pending.length === 1 ? 'change' : 'changes'}?`
        ));
      if (!confirmed) {
        ui.muted(
          process.stdin.isTTY
            ? '  Nothing written.'
            : '  Nothing written. Run with --yes to apply without asking.'
        );
        return finish(0);
      }

      for (const { file, status, next: after = '', image } of pending) {
        if (!file) continue;
        if (status === 'remove') {
          // A folder too: an asset set of the iOS catalog.
          fs.rmSync(file, { force: true, recursive: true });
          continue;
        }
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, image ?? after);
      }
      result.applied = true;
      result.next = [...next];
      if (afterWrite) await afterWrite(result);
      if (!json) {
        ui.line('');
        if (!result.ok && manual.length === 0) ui.warn('Written, with the problem above left.');
        else ui.done(manual.length > 0 ? 'Done, apart from the files above.' : done);
        if (result.next.length > 0) ui.next(result.next);
      }
      return finish(result.ok ? 0 : 1);
    },
  };
}

module.exports = { createRun };

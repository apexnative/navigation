// @ts-check
'use strict';

const { execInTerminal } = require('../exec');
const { installPods } = require('../pods');
const { codegen } = require('../codegen');
const { icons } = require('../icons');
const { links } = require('../links');
const { setup } = require('../setup');

/** @typedef {import('./types').Check} Check */

/**
 * Makes the fixes doctor can make, asking before each: setup shows its changes and asks, pod install runs in the
 * terminal. The rest stays for the user, as the checks print it.
 * @param {Check[]} checks
 * @param {{
 *   root: string;
 *   ui: ReturnType<typeof import('../ui').createUI>;
 *   inTerminal?: typeof execInTerminal;
 *   interactive?: boolean;
 * }} options
 * @returns {Promise<boolean>} whether it ran any
 */
async function runFixes(
  checks,
  {
    root,
    ui,
    inTerminal = execInTerminal,
    interactive = Boolean(process.stdin.isTTY && process.stdout.isTTY),
  }
) {
  if (!interactive) {
    ui.muted('  --fix asks before each fix, so it needs a terminal.');
    return false;
  }
  // A warning with a fix the CLI can make is made too: the typed commands, the status bar of the template.
  const runs = new Set(
    checks.flatMap((check) =>
      (check.status === 'error' || check.status === 'warn') && check.fix?.run ? [check.fix.run] : []
    )
  );
  if (runs.size === 0) {
    ui.muted('  Nothing --fix can do: the fixes above are yours to make.');
    return false;
  }
  // Setup first: the app's files come before its pods. When the pods need a fix of their own, they are installed once,
  // after the rest, not by setup too.
  if (runs.has('setup')) {
    await setup({ root, yes: false, dryRun: false, ui, header: false, skipPods: runs.has('pods') });
  }
  // The links of linking on the native side, and the icons from their settings.
  if (runs.has('links')) await links({ root, yes: false, dryRun: false, ui });
  if (runs.has('icons')) await icons({ root, options: {}, yes: false, dryRun: false, ui });
  // The generated code of the native screens, from their props as they are now.
  if (runs.has('codegen')) await codegen({ root, yes: false, dryRun: false, ui, header: false });
  if (runs.has('pods') && (await ui.confirm('Run pod install?'))) {
    ui.line('');
    const pods = installPods({ root, json: false, inTerminal });
    if (!pods.ok) ui.error(`pod install failed${pods.output}`);
  }
  return true;
}

module.exports = { runFixes };

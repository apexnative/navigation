// @ts-check
'use strict';

/**
 * What a step does with a file:
 * - change: the file looks like the template, `next` is the file set up;
 * - done: the file is set up already;
 * - manual: the file was changed by hand and the step can't tell how; `howTo` says what to change.
 * @typedef {{ status: 'change'; next: string; note: string }
 *   | { status: 'done'; note: string }
 *   | { status: 'manual'; note: string; howTo: string }} StepResult
 */

module.exports = {};

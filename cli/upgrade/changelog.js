// @ts-check
'use strict';

const { compare } = require('./versions');

/**
 * What changed between two versions, from a CHANGELOG.md as release-it writes it (conventional changelog: a heading
 * per version, `## [1.2.0](…) (date)`, and in it `### BREAKING CHANGES`, `### Features`, `### Bug Fixes`).
 * @typedef {{ breaking: { version: string; text: string }[]; notes: { version: string; text: string }[] }} Notes
 */

const HEADING = /^#{1,2} \[?v?(\d+\.\d+\.\d+(?:-[\w.]+)?)\]?.*$/gm;
const BREAKING = /^###\s+(?:⚠\s*)?BREAKING CHANGES?\s*$/im;

/**
 * The sections of the versions after `from`, up to `to`, newest first; the breaking changes of each apart.
 * @param {string} text the changelog
 * @param {string} from the version the app has
 * @param {string} to the version it upgrades to
 * @returns {Notes}
 */
function notesBetween(text, from, to) {
  const headings = [...text.matchAll(HEADING)];
  /** @type {Notes} */
  const notes = { breaking: [], notes: [] };
  headings.forEach((heading, index) => {
    const version = heading[1];
    if (compare(version, from) <= 0 || compare(version, to) > 0) return;
    const start = /** @type {number} */ (heading.index) + heading[0].length;
    const end = headings[index + 1]?.index ?? text.length;
    const body = text.slice(start, end).trim();
    notes.notes.push({ version, text: body });
    const breaking = BREAKING.exec(body);
    if (breaking) {
      const after = body.slice(breaking.index + breaking[0].length);
      const next = /^###\s/m.exec(after);
      notes.breaking.push({ version, text: (next ? after.slice(0, next.index) : after).trim() });
    }
  });
  notes.notes.sort((a, b) => compare(b.version, a.version));
  notes.breaking.sort((a, b) => compare(b.version, a.version));
  return notes;
}

module.exports = { notesBetween };

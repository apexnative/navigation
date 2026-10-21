// @ts-check
'use strict';

/**
 * @typedef {{ type: ' ' | '-' | '+'; text: string }} DiffLine
 */

/**
 * The lines of `after` against those of `before`: kept, removed and added, in order (longest common subsequence;
 * the files this compares are a few dozen lines).
 * @param {string} before
 * @param {string} after
 * @returns {DiffLine[]}
 */
function diffLines(before, after) {
  const a = linesOf(before);
  const b = linesOf(after);
  // common[i][j]: the length of the longest common subsequence of a[i..] and b[j..].
  const common = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0));
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      common[i][j] =
        a[i] === b[j] ? common[i + 1][j + 1] + 1 : Math.max(common[i + 1][j], common[i][j + 1]);
    }
  }
  /** @type {DiffLine[]} */
  const lines = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      lines.push({ type: ' ', text: a[i] });
      i++;
      j++;
    } else if (common[i + 1][j] >= common[i][j + 1]) {
      lines.push({ type: '-', text: a[i++] });
    } else {
      lines.push({ type: '+', text: b[j++] });
    }
  }
  while (i < a.length) lines.push({ type: '-', text: a[i++] });
  while (j < b.length) lines.push({ type: '+', text: b[j++] });
  return lines;
}

/**
 * The changed lines with `context` kept lines around them; `null` marks the lines left out between two groups.
 * @param {DiffLine[]} lines
 * @param {number} [context]
 * @returns {(DiffLine | null)[]}
 */
function hunks(lines, context = 2) {
  const keep = lines.map(() => false);
  lines.forEach((line, index) => {
    if (line.type === ' ') return;
    for (
      let k = Math.max(0, index - context);
      k <= Math.min(lines.length - 1, index + context);
      k++
    )
      keep[k] = true;
  });
  /** @type {(DiffLine | null)[]} */
  const shown = [];
  lines.forEach((line, index) => {
    if (keep[index]) shown.push(line);
    else if (shown.length > 0 && shown[shown.length - 1] !== null) shown.push(null);
  });
  if (shown[shown.length - 1] === null) shown.pop();
  return shown;
}

/**
 * A unified diff of a file, as git prints it, for tools and AI agents (`--json`): `context` kept lines around each group
 * of changes, with the line numbers of both texts. Empty when nothing changed.
 * @param {string} name the file's path, for the headers
 * @param {string} before empty for a new file
 * @param {string} after
 * @param {number} [context]
 * @returns {string}
 */
function unifiedDiff(name, before, after, context = 3) {
  // Each line with its number in the old and in the new text.
  let oldLine = 1;
  let newLine = 1;
  const lines = diffLines(before, after).map((line) => {
    const numbered = { ...line, old: oldLine, new: newLine };
    if (line.type !== '+') oldLine++;
    if (line.type !== '-') newLine++;
    return numbered;
  });
  const changed = lines.flatMap((line, index) => (line.type === ' ' ? [] : [index]));
  if (changed.length === 0) return '';
  /** @type {[number, number][]} */
  const groups = [];
  const around = (/** @type {number} */ index) =>
    /** @type {[number, number]} */ ([
      Math.max(0, index - context),
      Math.min(lines.length - 1, index + context),
    ]);
  let [start, end] = around(changed[0]);
  for (const index of changed.slice(1)) {
    const [from, to] = around(index);
    if (from <= end + 1) {
      end = to;
    } else {
      groups.push([start, end]);
      [start, end] = [from, to];
    }
  }
  groups.push([start, end]);
  // A new file comes from nothing, a removed one goes to nothing.
  const out = [
    before === '' ? '--- /dev/null' : `--- a/${name}`,
    after === '' ? '+++ /dev/null' : `+++ b/${name}`,
  ];
  for (const [from, to] of groups) {
    const group = lines.slice(from, to + 1);
    const inOld = group.filter((line) => line.type !== '+');
    const inNew = group.filter((line) => line.type !== '-');
    // An empty side starts at the line before, as git writes it.
    const oldStart = inOld.length > 0 ? inOld[0].old : group[0].old - 1;
    const newStart = inNew.length > 0 ? inNew[0].new : group[0].new - 1;
    out.push(`@@ -${oldStart},${inOld.length} +${newStart},${inNew.length} @@`);
    for (const line of group) out.push(`${line.type}${line.text}`);
  }
  return out.join('\n') + '\n';
}

/**
 * The lines of a text without the empty one after its last line break; none in an empty text, so a new file is
 * added whole.
 * @param {string} text
 */
function linesOf(text) {
  return text === '' ? [] : text.replace(/\n$/, '').split('\n');
}

module.exports = { diffLines, hunks, unifiedDiff };

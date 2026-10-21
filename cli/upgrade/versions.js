// @ts-check
'use strict';

/**
 * The numbers of a version, such as `0.87.1` or `1.2.0-beta.3`; undefined for one that isn't a version.
 * @param {string} version
 * @returns {{ numbers: number[]; pre: string } | undefined}
 */
function parse(version) {
  const found = /^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([\w.]+))?$/.exec(version.trim());
  if (!found) return undefined;
  return {
    numbers: [Number(found[1]), Number(found[2] ?? 0), Number(found[3] ?? 0)],
    pre: found[4] ?? '',
  };
}

/**
 * Compares two versions: negative when `a` is older. A pre-release is older than its release.
 * @param {string} a
 * @param {string} b
 */
function compare(a, b) {
  const [left, right] = [parse(a), parse(b)];
  if (!left || !right) return 0;
  for (let index = 0; index < 3; index++) {
    const difference = left.numbers[index] - right.numbers[index];
    if (difference !== 0) return difference;
  }
  if (left.pre === right.pre) return 0;
  if (!left.pre) return 1;
  if (!right.pre) return -1;
  // Identifier by identifier: numbers as numbers, below words; fewer identifiers first (alpha.10 after alpha.9).
  const [a1, b1] = [left.pre.split('.'), right.pre.split('.')];
  for (let index = 0; index < Math.max(a1.length, b1.length); index++) {
    const [x, y] = [a1[index], b1[index]];
    if (x === undefined) return -1;
    if (y === undefined) return 1;
    if (x === y) continue;
    const [xn, yn] = [/^\d+$/.test(x), /^\d+$/.test(y)];
    if (xn && yn) return Number(x) - Number(y);
    if (xn !== yn) return xn ? -1 : 1;
    return x < y ? -1 : 1;
  }
  return 0;
}

/**
 * Whether a version is in a range of npm's ranges: comparators (`>=0.85.0 <0.90`), `^` and `~`, `x` and `*`, alternatives
 * joined by `||`, and `a - b`. A pre-release is compared by its numbers, as a peer range of a library means it.
 * @param {string} version
 * @param {string} range
 */
function satisfies(version, range) {
  const release = version.split('-')[0];
  return range.split('||').some((part) => {
    const hyphen = /^\s*(\S+)\s+-\s+(\S+)\s*$/.exec(part);
    const comparators = hyphen
      ? [`>=${hyphen[1]}`, `<=${hyphen[2]}`]
      : part.trim().split(/\s+/).filter(Boolean);
    return comparators.every((comparator) => matches(release, comparator));
  });
}

/**
 * @param {string} version a release, `x.y.z`
 * @param {string} comparator
 */
function matches(version, comparator) {
  const found = /^(>=|<=|>|<|=|\^|~)?\s*v?([\dxX*]+(?:\.[\dxX*]+){0,2})?$/.exec(comparator);
  if (!found) return false;
  const [, operator = '', target = '*'] = found;
  const parts = target.split('.');
  const wild = parts.findIndex((part) => /^[xX*]$/.test(part));
  const given = wild < 0 ? parts.length : wild;
  const numbers = [0, 1, 2].map((index) => (index < given ? Number(parts[index]) : 0));
  const base = numbers.join('.');
  if (given === 0) return true;
  // A partial version is a range of its own: `0.87` is from 0.87.0 to the next minor.
  const upper = (/** @type {number} */ at) =>
    [...numbers.slice(0, at), numbers[at] + 1, ...[0, 0].slice(0, 2 - at)].join('.');
  switch (operator) {
    case '>=':
      return compare(version, base) >= 0;
    case '>':
      return given < 3 ? compare(version, upper(given - 1)) >= 0 : compare(version, base) > 0;
    case '<=':
      return given < 3 ? compare(version, upper(given - 1)) < 0 : compare(version, base) <= 0;
    case '<':
      return compare(version, base) < 0;
    case '^': {
      // The first number that isn't 0 stays: ^0.87.1 is below 0.88.0, ^1.2.0 below 2.0.0.
      const at = numbers[0] !== 0 || given === 1 ? 0 : numbers[1] !== 0 || given === 2 ? 1 : 2;
      return compare(version, base) >= 0 && compare(version, upper(at)) < 0;
    }
    case '~':
      return compare(version, base) >= 0 && compare(version, upper(given >= 2 ? 1 : 0)) < 0;
    default:
      return given < 3
        ? compare(version, base) >= 0 && compare(version, upper(given - 1)) < 0
        : compare(version, base) === 0;
  }
}

module.exports = { compare, satisfies, parse };

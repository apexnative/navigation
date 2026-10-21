// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');

/** @typedef {import('../changes').FileChange} FileChange */

/**
 * A file the command makes, text or an image: `done` when it is so already. Its name is the file with its folder, as
 * the splash's files have the same names in several folders (`drawable-xhdpi`, `SplashLogo-….imageset`).
 * @param {'ios' | 'android'} platform
 * @param {string} file
 * @param {string | Buffer} next
 * @param {string} note
 * @returns {FileChange}
 */
function madeFile(platform, file, next, note) {
  const name = path.join(path.basename(path.dirname(file)), path.basename(file));
  const exists = fs.existsSync(file);
  if (typeof next !== 'string') {
    if (exists && fs.readFileSync(file).equals(next)) {
      return { name, platform, file, status: 'done', note };
    }
    return { name, platform, file, status: exists ? 'change' : 'create', image: next, note };
  }
  const before = exists ? fs.readFileSync(file, 'utf8') : undefined;
  if (before === next) return { name, platform, file, status: 'done', note };
  return before === undefined
    ? { name, platform, file, status: 'create', next, note }
    : { name, platform, file, status: 'change', before, next, note };
}

/**
 * A file or folder of an older splash that goes.
 * @param {'ios' | 'android'} platform
 * @param {string} file
 * @returns {FileChange}
 */
function oldFile(platform, file) {
  return {
    name: path.join(path.basename(path.dirname(file)), path.basename(file)),
    platform,
    file,
    status: 'remove',
    note: 'an older splash',
  };
}

module.exports = { madeFile, oldFile };

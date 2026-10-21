// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { madeFile, oldFile } = require('../splash/files');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('./images').IconImages} IconImages */

const NAMES = [
  'ic_launcher',
  'ic_launcher_round',
  'ic_launcher_foreground',
  'ic_launcher_monochrome',
];
const COLOR_FILE = 'ic_launcher_background.xml';

/**
 * The Android icons made anew: the adaptive icon (Android 8+) of a color, the foreground and the monochrome layer
 * (themed icons, Android 13+), and the square and round icons of Android 7 and older, per density. WebP icons of the
 * same names go, as two files of one name don't build.
 * @param {import('../project').Project} project
 * @param {IconImages} images
 * @param {string} background `#RRGGBB`
 * @param {string} hash
 * @returns {FileChange[]}
 */
function planAndroidIcons(project, images, background, hash) {
  if (!project.manifest) return [];
  const res = path.join(path.dirname(project.manifest), 'res');
  /** @type {FileChange[]} */
  const changes = [];
  const monochrome = images.android.every((density) => density.monochrome);
  for (const { density, foreground, monochrome: layer, legacy, round } of images.android) {
    const dir = path.join(res, `mipmap-${density}`);
    changes.push(
      madeFile('android', path.join(dir, 'ic_launcher.png'), legacy, 'Android 7 and older'),
      madeFile('android', path.join(dir, 'ic_launcher_round.png'), round, 'round, Android 7'),
      madeFile('android', path.join(dir, 'ic_launcher_foreground.png'), foreground, 'adaptive icon')
    );
    if (layer) {
      changes.push(
        madeFile('android', path.join(dir, 'ic_launcher_monochrome.png'), layer, 'themed icon')
      );
    } else if (fs.existsSync(path.join(dir, 'ic_launcher_monochrome.png'))) {
      changes.push(oldFile('android', path.join(dir, 'ic_launcher_monochrome.png')));
    }
    for (const name of NAMES) {
      const webp = path.join(dir, `${name}.webp`);
      if (fs.existsSync(webp))
        changes.push({ ...oldFile('android', webp), note: 'a PNG takes its place' });
    }
  }
  const adaptive = adaptiveIcon(monochrome);
  for (const name of ['ic_launcher.xml', 'ic_launcher_round.xml']) {
    changes.push(
      madeFile('android', path.join(res, 'mipmap-anydpi-v26', name), adaptive, 'adaptive icon')
    );
  }
  changes.push(colorChange(res, background, hash));
  const manifest = manifestChange(project.manifest);
  if (manifest) changes.push(manifest);
  return changes;
}

/**
 * The adaptive icon's file: its color, its foreground and, unless the icon is a ready image, its monochrome layer.
 * @param {boolean} monochrome
 */
function adaptiveIcon(monochrome) {
  return [
    '<?xml version="1.0" encoding="utf-8"?>',
    '<!-- Made by apex-navigation icons. -->',
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
    '    <background android:drawable="@color/ic_launcher_background" />',
    '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />',
    ...(monochrome ? ['    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />'] : []),
    '</adaptive-icon>',
    '',
  ].join('\n');
}

/**
 * The color behind the adaptive icon, with the hash of what the icon is made from. A color of that name in another
 * values file would build twice: that file is left to change by hand.
 * @param {string} res
 * @param {string} background
 * @param {string} hash
 * @returns {FileChange}
 */
function colorChange(res, background, hash) {
  const values = path.join(res, 'values');
  const elsewhere = fs.existsSync(values)
    ? fs
        .readdirSync(values)
        .filter((name) => name !== COLOR_FILE && name.endsWith('.xml'))
        .find((name) =>
          /<color\s+name="ic_launcher_background"/.test(
            fs.readFileSync(path.join(values, name), 'utf8')
          )
        )
    : undefined;
  if (elsewhere) {
    return {
      name: elsewhere,
      platform: 'android',
      file: path.join(values, elsewhere),
      status: 'manual',
      note: 'has the color ic_launcher_background, see below',
      howTo: `Remove <color name="ic_launcher_background"> from values/${elsewhere}: the icon's color is in values/${COLOR_FILE} now.`,
    };
  }
  const text = [
    '<?xml version="1.0" encoding="utf-8"?>',
    '<resources>',
    `    <!-- Made by apex-navigation icons (${hash}). -->`,
    `    <color name="ic_launcher_background">${background}</color>`,
    '</resources>',
    '',
  ].join('\n');
  return madeFile('android', path.join(values, COLOR_FILE), text, background);
}

/**
 * The manifest with the app's icons on `<application>`, when it names others or none; undefined when it names these.
 * @param {string} file
 * @returns {FileChange | undefined}
 */
function manifestChange(file) {
  const before = fs.readFileSync(file, 'utf8');
  const tag = /<application\b[^>]*>/.exec(before)?.[0];
  if (!tag) return undefined;
  let next = tag;
  for (const [attribute, value] of [
    ['android:icon', '@mipmap/ic_launcher'],
    ['android:roundIcon', '@mipmap/ic_launcher_round'],
  ]) {
    const pattern = new RegExp(String.raw`${attribute}\s*=\s*"[^"]*"`);
    if (pattern.test(next)) next = next.replace(pattern, `${attribute}="${value}"`);
    else {
      const end = next.endsWith('/>') ? '/>' : '>';
      const head = next.slice(0, -end.length).trimEnd();
      const indent = /\n([ \t]+)\S[^\n]*$/.exec(head)?.[1];
      next = `${head}${indent ? `\n${indent}` : ' '}${attribute}="${value}"${end}`;
    }
  }
  if (next === tag) return undefined;
  return {
    name: 'AndroidManifest.xml',
    platform: 'android',
    file,
    status: 'change',
    before,
    // A function, so a `$` in the app's own attributes stays as it is.
    next: before.replace(tag, () => next),
    note: 'the icons of the app',
  };
}

/**
 * The hash in the color file the command made; undefined for icons made otherwise.
 * @param {import('../project').Project} project
 * @returns {string | undefined}
 */
function readColorHash(project) {
  if (!project.manifest) return undefined;
  const file = path.join(path.dirname(project.manifest), 'res', 'values', COLOR_FILE);
  if (!fs.existsSync(file)) return undefined;
  return /Made by apex-navigation icons \(([0-9a-f]+)\)/.exec(fs.readFileSync(file, 'utf8'))?.[1];
}

module.exports = { planAndroidIcons, readColorHash };

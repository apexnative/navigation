// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { projectInfoPlist } = require('../links/identity');
const { madeFile, oldFile } = require('../splash/files');
const { buildSetting } = require('../xcode/pbxproj');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('./images').IconImages} IconImages */

/** The author of an icon set the command made, with the hash of what it was made from after it. */
const AUTHOR = 'apex-navigation icons';

/**
 * The icon set of the iOS app: the one the target names (`AppIcon` in the template), in the app's asset catalog.
 * @param {import('../project').Project} project
 * @returns {string | undefined}
 */
function iconSetDir(project) {
  if (!project.infoPlist) return undefined;
  const pbxproj =
    project.xcodeProject && fs.existsSync(project.xcodeProject)
      ? fs.readFileSync(project.xcodeProject, 'utf8')
      : '';
  const infoPlist = projectInfoPlist(project);
  const name =
    (infoPlist &&
      buildSetting(pbxproj, infoPlist, 'ASSETCATALOG_COMPILER_APPICON_NAME').find(Boolean)) ??
    'AppIcon';
  return path.join(path.dirname(project.infoPlist), 'Images.xcassets', `${name}.appiconset`);
}

/**
 * The iOS icon set made anew: one 1024 px image for iPhone and iPad (Xcode makes the other sizes), with its dark and
 * tinted versions; the files of the set before go.
 * @param {import('../project').Project} project
 * @param {IconImages} images
 * @param {string} hash
 * @returns {FileChange[]}
 */
function planIosIcons(project, images, hash) {
  const dir = iconSetDir(project);
  if (!dir) return [];
  const entry = (/** @type {string} */ filename, /** @type {string | undefined} */ appearance) => ({
    ...(appearance && { appearances: [{ appearance: 'luminosity', value: appearance }] }),
    filename,
    idiom: 'universal',
    platform: 'ios',
    size: '1024x1024',
  });
  const files = [
    { name: 'AppIcon.png', image: images.ios.light, note: 'the icon, 1024 px' },
    ...(images.ios.dark
      ? [{ name: 'AppIcon-Dark.png', image: images.ios.dark, note: 'dark', appearance: 'dark' }]
      : []),
    ...(images.ios.tinted
      ? [
          {
            name: 'AppIcon-Tinted.png',
            image: images.ios.tinted,
            note: 'tinted',
            appearance: 'tinted',
          },
        ]
      : []),
  ];
  const contents = {
    images: files.map(({ name, appearance }) => entry(name, appearance)),
    info: { author: `${AUTHOR} ${hash}`, version: 1 },
  };
  const kept = new Set(['Contents.json', ...files.map(({ name }) => name)]);
  const old = fs.existsSync(dir)
    ? fs
        .readdirSync(dir)
        .filter((name) => !kept.has(name))
        .map((name) => oldFile('ios', path.join(dir, name)))
    : [];
  return [
    ...files.map(({ name, image, note }) => madeFile('ios', path.join(dir, name), image, note)),
    madeFile(
      'ios',
      path.join(dir, 'Contents.json'),
      JSON.stringify(contents, null, 2) + '\n',
      'one size for iPhone and iPad'
    ),
    ...old.map((change) => ({ ...change, note: 'an older icon' })),
  ];
}

/**
 * The hash in the iOS icon set the command made; undefined for a set made otherwise.
 * @param {import('../project').Project} project
 * @returns {{ hash?: string; images: number } | undefined} `images`: how many entries of the set have a file
 */
function readIconSet(project) {
  const dir = iconSetDir(project);
  const file = dir && path.join(dir, 'Contents.json');
  if (!file || !fs.existsSync(file)) return undefined;
  try {
    const contents = JSON.parse(fs.readFileSync(file, 'utf8'));
    const author = String(contents.info?.author ?? '');
    return {
      hash: author.startsWith(AUTHOR) ? author.slice(AUTHOR.length).trim() : undefined,
      images: (contents.images ?? []).filter((/** @type {any} */ image) => image.filename).length,
    };
  } catch {
    return undefined;
  }
}

module.exports = { planIosIcons, readIconSet, iconSetDir };

// @ts-check
'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { normalizeColor } = require('../splash/settings');

/**
 * What the app icon is made from, under `apexNavigation.icons` in the app's package.json, paths from the app's root:
 * - from a logo: the logo (PNG, JPEG, WebP or SVG on a transparent background), the color behind it, a logo for the
 *   dark appearance, and the logo's width in percent of the visible icon (iOS: the whole icon; Android: the 72 dp of
 *   the adaptive icon the mask shows);
 * - from a ready square icon: the image, and the color that fills its transparent parts.
 * @typedef {{ logo: string; background: string; darkLogo?: string; logoSize: number }
 *   | { image: string; background: string }} IconSettings
 */

/**
 * The options of `icons`, as the command line gives them.
 * @typedef {{ logo?: string; image?: string; background?: string; darkLogo?: string; logoSize?: string }} IconOptions
 */

const DEFAULT_BACKGROUND = '#FFFFFF';
const DEFAULT_LOGO_SIZE = 60;
const IMAGE = /\.(png|jpe?g|webp|svg)$/i;
// Changes when the files the command makes change, so that doctor asks for them again after an update of the CLI.
const FORMAT = 1;

/**
 * The settings: the options over the saved ones of the same kind (a logo or an image), or what is wrong with them.
 * @param {string} root
 * @param {IconOptions} options
 * @param {Partial<{ logo: string; image: string; background: string; darkLogo: string; logoSize: number }>} [saved]
 * @returns {{ settings: IconSettings } | { errors: string[] }}
 */
function resolveIconSettings(root, options, saved = {}) {
  /** @type {string[]} */
  const errors = [];
  if (options.logo && options.image) {
    return { errors: ['--logo and --image make the icon two ways: give one of them'] };
  }
  const fromImage = options.image !== undefined || (!options.logo && saved.image !== undefined);
  // Settings of the other kind don't carry over: a logo's dark logo means nothing to an image.
  const kept = fromImage === (saved.image !== undefined) ? saved : {};
  const background = normalizeColor(options.background ?? kept.background ?? DEFAULT_BACKGROUND);
  if (background === null || background === undefined) {
    errors.push('--background is a color such as #FFFFFF');
  }

  if (fromImage) {
    const image = options.image ?? kept.image;
    checkFile(root, '--image', image, errors);
    if (errors.length > 0 || !image || !background) return { errors };
    return { settings: { image: relative(root, image), background } };
  }

  const logo = options.logo ?? kept.logo;
  const darkLogo = options.darkLogo ?? kept.darkLogo;
  const logoSize = Number(options.logoSize ?? kept.logoSize ?? DEFAULT_LOGO_SIZE);
  if (!logo)
    errors.push('The icon needs a logo, --logo assets/logo.png, or a ready icon, --image icon.png');
  checkFile(root, '--logo', logo, errors);
  checkFile(root, '--dark-logo', darkLogo, errors);
  if (!Number.isFinite(logoSize) || logoSize < 20 || logoSize > 100) {
    errors.push('--logo-size is a percent of the icon from 20 to 100');
  }
  if (errors.length > 0 || !logo || !background) return { errors };
  return {
    settings: {
      logo: relative(root, logo),
      background,
      ...(darkLogo && { darkLogo: relative(root, darkLogo) }),
      logoSize,
    },
  };
}

/**
 * A short hash of what the icon is made from: the settings and the bytes of the images, not where they are. The
 * files the command makes carry it, and doctor compares.
 * @param {string} root
 * @param {IconSettings} settings
 */
function iconsHash(root, settings) {
  const hash = crypto.createHash('sha256');
  const files = 'image' in settings ? [settings.image] : [settings.logo, settings.darkLogo];
  const { background } = settings;
  const logoSize = 'logoSize' in settings ? settings.logoSize : undefined;
  hash.update(JSON.stringify({ FORMAT, background, logoSize, image: 'image' in settings }));
  for (const file of files) if (file) hash.update(fs.readFileSync(path.resolve(root, file)));
  return hash.digest('hex').slice(0, 8);
}

/**
 * @param {string} root
 * @param {string} flag
 * @param {string | undefined} file
 * @param {string[]} errors
 */
function checkFile(root, flag, file, errors) {
  if (!file) return;
  if (!IMAGE.test(file)) errors.push(`${flag} is a PNG, JPEG, WebP or SVG file, not ${file}`);
  else if (!fs.existsSync(path.resolve(root, file))) errors.push(`${flag}: there is no ${file}`);
}

/**
 * A path from the app's root, with forward slashes, as package.json keeps it.
 * @param {string} root
 * @param {string} file
 */
function relative(root, file) {
  return path.relative(root, path.resolve(root, file)).split(path.sep).join('/');
}

module.exports = { resolveIconSettings, iconsHash };

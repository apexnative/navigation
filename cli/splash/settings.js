// @ts-check
'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

/**
 * What the splash is made from, under `apexNavigation.splash` in the app's package.json: the logos (PNG, JPEG, WebP or
 * SVG, from the app's root), the background colors (`#RGB` or `#RRGGBB`), and the logo's width in points (iOS) and dp
 * (Android). Without the dark ones, dark mode shows the light ones.
 * @typedef {{
 *   logo: string;
 *   background: string;
 *   darkLogo?: string;
 *   darkBackground?: string;
 *   logoWidth: number;
 * }} SplashSettings
 */

/**
 * The options of `splash`, as the command line gives them.
 * @typedef {{
 *   logo?: string;
 *   background?: string;
 *   darkLogo?: string;
 *   darkBackground?: string;
 *   logoWidth?: string;
 * }} SplashOptions
 */

const DEFAULT_BACKGROUND = '#FFFFFF';
const DEFAULT_LOGO_WIDTH = 100;
// Android 12+ shows the logo on a canvas of 288 dp: a wider or taller one doesn't fit.
const CANVAS = 288;
const COLOR = /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i;
const IMAGE = /\.(png|jpe?g|webp|svg)$/i;
// Changes when the files the command makes change, so that doctor asks for them again after an update of the CLI.
const FORMAT = 1;

/**
 * The settings: the options over the saved ones, or what is wrong with them.
 * @param {string} root
 * @param {SplashOptions} options
 * @param {Partial<SplashSettings>} [saved]
 * @returns {{ settings: SplashSettings } | { errors: string[] }}
 */
function resolveSettings(root, options, saved = {}) {
  /** @type {string[]} */
  const errors = [];
  const logo = options.logo ?? saved.logo;
  const darkLogo = options.darkLogo ?? saved.darkLogo;
  const background = normalizeColor(options.background ?? saved.background ?? DEFAULT_BACKGROUND);
  const darkBackground = normalizeColor(options.darkBackground ?? saved.darkBackground);
  const logoWidth = Number(options.logoWidth ?? saved.logoWidth ?? DEFAULT_LOGO_WIDTH);

  if (!logo) errors.push('The splash needs a logo: --logo assets/logo.png');
  for (const [flag, file] of [
    ['--logo', logo],
    ['--dark-logo', darkLogo],
  ]) {
    if (!file) continue;
    if (!IMAGE.test(file)) errors.push(`${flag} is a PNG, JPEG, WebP or SVG file, not ${file}`);
    else if (!fs.existsSync(path.resolve(root, file))) errors.push(`${flag}: there is no ${file}`);
  }
  for (const [flag, color] of [
    ['--background', background],
    ['--dark-background', darkBackground],
  ]) {
    if (color === null) errors.push(`${flag} is a color such as #FFFFFF`);
  }
  if (!Number.isFinite(logoWidth) || logoWidth <= 0 || logoWidth > CANVAS) {
    errors.push(`--logo-width is a number of points from 1 to ${CANVAS}`);
  }
  if (errors.length > 0 || !logo || !background || darkBackground === null) {
    return { errors };
  }
  return {
    settings: {
      logo: toPosix(path.relative(root, path.resolve(root, logo))),
      background,
      ...(darkLogo && { darkLogo: toPosix(path.relative(root, path.resolve(root, darkLogo))) }),
      ...(darkBackground && { darkBackground }),
      logoWidth,
    },
  };
}

/**
 * A short hash of what the splash is made from: the settings and the bytes of the logos, not where they are. The
 * names of the iOS assets carry it, since iOS keeps a launch screen it has shown until the names change; doctor
 * compares it with the files.
 * @param {string} root
 * @param {SplashSettings} settings
 */
function splashHash(root, settings) {
  const hash = crypto.createHash('sha256');
  const { background, darkBackground, logoWidth, darkLogo } = settings;
  hash.update(JSON.stringify({ FORMAT, background, darkBackground, logoWidth, dark: !!darkLogo }));
  hash.update(fs.readFileSync(path.resolve(root, settings.logo)));
  if (darkLogo) hash.update(fs.readFileSync(path.resolve(root, darkLogo)));
  return hash.digest('hex').slice(0, 8);
}

/**
 * A color as `#RRGGBB` in capitals; undefined for none, null for one that isn't a color.
 * @param {string | undefined} color
 * @returns {string | undefined | null}
 */
function normalizeColor(color) {
  if (color === undefined) return undefined;
  if (!COLOR.test(color)) return null;
  const digits = color.slice(1);
  const full = digits.length === 3 ? [...digits].map((digit) => digit + digit).join('') : digits;
  return `#${full.toUpperCase()}`;
}

/**
 * The red, green and blue of a `#RRGGBB` color, from 0 to 255.
 * @param {string} color
 */
function rgb(color) {
  return {
    red: parseInt(color.slice(1, 3), 16),
    green: parseInt(color.slice(3, 5), 16),
    blue: parseInt(color.slice(5, 7), 16),
  };
}

/**
 * A size for the native files: at most two decimals, without trailing zeros.
 * @param {number} value
 */
function size(value) {
  return String(Math.round(value * 100) / 100);
}

/** @param {string} file */
function toPosix(file) {
  return file.split(path.sep).join('/');
}

module.exports = { resolveSettings, splashHash, normalizeColor, rgb, size, CANVAS };

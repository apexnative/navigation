// @ts-check
'use strict';

const path = require('node:path');
const { CANVAS } = require('./settings');

/** @typedef {import('./settings').SplashSettings} SplashSettings */

/**
 * The images of the splash: the logo for iOS at 1x, 2x and 3x; for Android on the canvas of its splash for each
 * density; the dark logo the same way. `height` is the logo's height in points (dp) at its width.
 * @typedef {{
 *   height: number;
 *   ios: { scale: number; light: Buffer; dark?: Buffer }[];
 *   android: { density: string; light: Buffer; dark?: Buffer }[];
 * }} SplashImages
 */

/** @typedef {(root: string, settings: SplashSettings) => Promise<SplashImages>} MakeImages */

const IOS_SCALES = [1, 2, 3];
const DENSITIES = /** @type {const} */ ([
  ['mdpi', 1],
  ['hdpi', 1.5],
  ['xhdpi', 2],
  ['xxhdpi', 3],
  ['xxxhdpi', 4],
]);
// An SVG is drawn at the size it goes to, up to this density: sharp draws it at 72 dpi otherwise, and scales that up.
const MAX_SVG_DENSITY = 2400;

/**
 * Makes the images with sharp. Throws when a logo can't be read, or doesn't fit the canvas of Android's splash at its
 * width.
 * @param {any} sharp
 * @returns {MakeImages}
 */
function imageMaker(sharp) {
  return async (root, settings) => {
    const light = path.resolve(root, settings.logo);
    const dark = settings.darkLogo && path.resolve(root, settings.darkLogo);
    const { width, height } = await sharp(light).metadata();
    const logoHeight = (settings.logoWidth * height) / width;
    if (logoHeight > CANVAS) {
      throw new Error(
        `At ${settings.logoWidth} points wide the logo is ${Math.round(logoHeight)} high: Android's splash takes at most ${CANVAS}. Make --logo-width smaller.`
      );
    }
    if (dark) {
      const other = await sharp(dark).metadata();
      if (Math.abs(other.height / other.width - height / width) > 0.01 * (height / width)) {
        throw new Error(
          'The dark logo has other proportions than the logo: the splash keeps one size for both.'
        );
      }
    }
    /** @param {string} file @param {number} scale */
    const logoAt = (file, scale) => resize(sharp, file, Math.round(settings.logoWidth * scale));
    /** @param {string} file @param {number} density */
    const canvasAt = async (file, density) =>
      onCanvas(sharp, await logoAt(file, density), Math.round(CANVAS * density));
    return {
      height: logoHeight,
      ios: await Promise.all(
        IOS_SCALES.map(async (scale) => ({
          scale,
          light: await logoAt(light, scale),
          ...(dark && { dark: await logoAt(dark, scale) }),
        }))
      ),
      android: await Promise.all(
        DENSITIES.map(async ([density, factor]) => ({
          density,
          light: await canvasAt(light, factor),
          ...(dark && { dark: await canvasAt(dark, factor) }),
        }))
      ),
    };
  };
}

/**
 * The logo as a PNG `width` pixels wide.
 * @param {any} sharp
 * @param {string} file
 * @param {number} width
 * @returns {Promise<Buffer>}
 */
async function resize(sharp, file, width) {
  const metadata = await sharp(file).metadata();
  const density =
    metadata.format === 'svg'
      ? Math.min(MAX_SVG_DENSITY, Math.max(72, (72 * width) / metadata.width))
      : undefined;
  return sharp(file, density ? { density } : {})
    .resize({ width })
    .png()
    .toBuffer();
}

/**
 * The logo in the middle of a transparent square canvas, `size` pixels a side.
 * @param {any} sharp
 * @param {Buffer} logo
 * @param {number} size
 * @returns {Promise<Buffer>}
 */
function onCanvas(sharp, logo, size) {
  return sharp({
    create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
  })
    .composite([{ input: logo, gravity: 'center' }])
    .png()
    .toBuffer();
}

module.exports = { imageMaker };

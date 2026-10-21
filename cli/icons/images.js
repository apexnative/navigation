// @ts-check
'use strict';

const path = require('node:path');

/** @typedef {import('./settings').IconSettings} IconSettings */

/**
 * The images of the app icon:
 * - iOS, 1024 px: `light` the icon, no transparency; `dark` and `tinted` the logo on a transparent background, in
 *   color and in gray (none for a ready icon: iOS makes them);
 * - Android, per density: the adaptive icon's `foreground` (108 dp) and `monochrome` layers (none for a ready icon),
 *   and the icons of Android 7 and older, square and round (48 dp).
 * `logo` is the logo's size in dp in the adaptive icon, for the check of its safe circle.
 * @typedef {{
 *   ios: { light: Buffer; dark?: Buffer; tinted?: Buffer };
 *   android: { density: string; foreground: Buffer; monochrome?: Buffer; legacy: Buffer; round: Buffer }[];
 *   logo?: { width: number; height: number };
 * }} IconImages
 */

/** @typedef {(root: string, settings: IconSettings) => Promise<IconImages>} MakeIcons */

const IOS_SIZE = 1024;
// The adaptive icon's layers, the part of them the mask shows, and the icons before Android 8, in dp.
const LAYER = 108;
const VIEWPORT = 72;
const LEGACY = 48;
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
 * Makes the images with sharp. Throws when an image can't be read.
 * @param {any} sharp
 * @returns {MakeIcons}
 */
function iconMaker(sharp) {
  return async (root, settings) => {
    const background = settings.background;
    if ('image' in settings) {
      const image = path.resolve(root, settings.image);
      return {
        ios: { light: await flatten(sharp, await fitted(sharp, image, IOS_SIZE), background) },
        android: await Promise.all(
          DENSITIES.map(async ([density, factor]) => {
            const legacy = await fitted(sharp, image, Math.round(LEGACY * factor));
            return {
              density,
              foreground: await onSquare(
                sharp,
                Math.round(LAYER * factor),
                await fitted(sharp, image, Math.round(VIEWPORT * factor))
              ),
              legacy,
              round: await circle(sharp, legacy),
            };
          })
        ),
      };
    }

    const logo = path.resolve(root, settings.logo);
    const darkLogo = settings.darkLogo ? path.resolve(root, settings.darkLogo) : logo;
    const share = settings.logoSize / 100;
    const iosLogo = Math.round(IOS_SIZE * share);
    const darkIos = await fitted(sharp, darkLogo, iosLogo);
    const { width = 1, height = 1 } = await sharp(await fitted(sharp, logo, 1000)).metadata();
    const box = VIEWPORT * share;
    return {
      ios: {
        light: await flatten(
          sharp,
          await onSquare(sharp, IOS_SIZE, await fitted(sharp, logo, iosLogo)),
          background
        ),
        dark: await onSquare(sharp, IOS_SIZE, darkIos),
        tinted: await onSquare(
          sharp,
          IOS_SIZE,
          await sharp(darkIos).modulate({ saturation: 0 }).png().toBuffer()
        ),
      },
      android: await Promise.all(
        DENSITIES.map(async ([density, factor]) => {
          const layerLogo = await fitted(sharp, logo, Math.round(box * factor));
          const legacyLogo = await fitted(sharp, logo, Math.round(LEGACY * share * factor));
          const legacy = await flatten(
            sharp,
            await onSquare(sharp, Math.round(LEGACY * factor), legacyLogo),
            background
          );
          return {
            density,
            foreground: await onSquare(sharp, Math.round(LAYER * factor), layerLogo),
            monochrome: await onSquare(
              sharp,
              Math.round(LAYER * factor),
              await silhouette(sharp, layerLogo)
            ),
            legacy,
            round: await circle(sharp, legacy),
          };
        })
      ),
      logo:
        width >= height
          ? { width: box, height: (box * height) / width }
          : { width: (box * width) / height, height: box },
    };
  };
}

/**
 * The image as a PNG that fits a square of `size` pixels, its proportions kept.
 * @param {any} sharp
 * @param {string} file
 * @param {number} size
 * @returns {Promise<Buffer>}
 */
async function fitted(sharp, file, size) {
  const metadata = await sharp(file).metadata();
  const density =
    metadata.format === 'svg'
      ? Math.min(
          MAX_SVG_DENSITY,
          Math.max(72, (72 * size) / Math.max(metadata.width, metadata.height))
        )
      : undefined;
  return sharp(file, density ? { density } : {})
    .resize({ width: size, height: size, fit: 'inside' })
    .png()
    .toBuffer();
}

/**
 * The image in the middle of a transparent square, `size` pixels a side.
 * @param {any} sharp
 * @param {Buffer} image
 * @param {number} size
 * @returns {Promise<Buffer>}
 */
function onSquare(sharp, size, image) {
  return sharp({
    create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
  })
    .composite([{ input: image, gravity: 'center' }])
    .png()
    .toBuffer();
}

/**
 * The image on the color, with no transparency left: the App Store refuses an icon with an alpha channel.
 * @param {any} sharp
 * @param {Buffer} image
 * @param {string} color `#RRGGBB`
 * @returns {Promise<Buffer>}
 */
function flatten(sharp, image, color) {
  return sharp(image).flatten({ background: color }).removeAlpha().png().toBuffer();
}

/**
 * The shape of the image in white, its transparency kept: the layer Android tints with the colors of the theme.
 * @param {any} sharp
 * @param {Buffer} image
 * @returns {Promise<Buffer>}
 */
async function silhouette(sharp, image) {
  const { width, height } = await sharp(image).metadata();
  const alpha = await sharp(image).ensureAlpha().extractChannel(3).toBuffer();
  return sharp({ create: { width, height, channels: 3, background: '#FFFFFF' } })
    .joinChannel(alpha)
    .png()
    .toBuffer();
}

/**
 * The image cut to the circle it fills.
 * @param {any} sharp
 * @param {Buffer} image
 * @returns {Promise<Buffer>}
 */
async function circle(sharp, image) {
  const { width } = await sharp(image).metadata();
  const mask = Buffer.from(
    `<svg width="${width}" height="${width}"><circle cx="${width / 2}" cy="${width / 2}" r="${width / 2}" fill="#fff"/></svg>`
  );
  return sharp(image)
    .ensureAlpha()
    .composite([{ input: mask, blend: 'dest-in' }])
    .png()
    .toBuffer();
}

module.exports = { iconMaker, DENSITIES };

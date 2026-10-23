// @ts-check
'use strict';

const fs = require('node:fs');
const { createRun } = require('../changes');
const { configChange, readConfig } = require('../config');
const { exec, execInTerminal } = require('../exec');
const { findProject } = require('../project');
const { createUI } = require('../ui');
const { planAndroid } = require('./android');
const { requireSharp, sharpInstallCommand } = require('../images');
const { imageMaker } = require('./images');
const { planIos } = require('./ios');
const { resolveSettings, splashHash } = require('./settings');

/** @typedef {import('./settings').SplashOptions} SplashOptions */
/** @typedef {import('./images').MakeImages} MakeImages */

// Android 12+ shows the logo in a circle of this diameter: the corners of a larger logo are cut.
const CIRCLE = 192;

/**
 * Makes the native splash of the app from a logo and colors, light and dark: a storyboard and assets on iOS, the
 * images, colors and a splash theme on Android. The settings go into package.json, so a run without options makes it
 * again from them. The images are made with sharp, which the command offers to add to the app's dev dependencies.
 * @param {{
 *   root: string;
 *   options: SplashOptions;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   install?: (manager: string, args: string[]) => boolean;
 *   makeImages?: MakeImages;
 * }} options `install` runs the package manager, `makeImages` makes the images (both replaced in tests)
 * @returns {Promise<number>} the exit code
 */
async function splash({
  root,
  options,
  yes,
  dryRun,
  json = false,
  ui = createUI(),
  run = exec,
  install = (manager, args) => execInTerminal(manager, args, { cwd: root }),
  makeImages,
}) {
  const command = createRun({ command: 'splash', root, json, ui });
  if (!json) ui.banner({ command: 'splash', facts: [{ text: 'iOS · Android' }] });

  const config = readConfig(root);
  const resolved = resolveSettings(root, options, config.splash);
  if ('errors' in resolved) return command.fail('splash-settings', resolved.errors);
  const { settings } = resolved;

  if (!makeImages) {
    const sharp = await requireSharp(root, {
      yes,
      dryRun,
      json,
      ui,
      install,
      what: 'The splash images',
    });
    if (!sharp) {
      return command.fail(
        'sharp-missing',
        `The splash command makes its images with sharp: ${sharpInstallCommand(root).join(' ')}, then run it again.`
      );
    }
    makeImages = imageMaker(sharp);
  }
  let images;
  try {
    images = await makeImages(root, settings);
  } catch (error) {
    return command.fail('splash-image', error instanceof Error ? error.message : String(error));
  }

  const hash = splashHash(root, settings);
  const project = findProject(root, run);
  if (Math.hypot(settings.logoWidth, images.height) > CIRCLE) {
    command.warn(
      `Android 12+ shows the logo in a circle of ${CIRCLE} dp: its corners are cut at ${settings.logoWidth} × ${Math.round(images.height)}.`
    );
  }
  const plist = project.infoPlist && fs.readFileSync(project.infoPlist, 'utf8');
  if (plist?.includes('<key>UILaunchScreen</key>')) {
    command.warn(
      'Info.plist has UILaunchScreen too: iOS shows it instead of the storyboard; remove it, so that iOS shows the storyboard.'
    );
  }
  return command.apply(
    [
      ...planIos(project, settings, images, hash),
      ...planAndroid(project, settings, images, hash),
      configChange(root, (current) => ({ ...current, splash: settings })),
    ],
    {
      yes,
      dryRun,
      done: 'The splash is made: build the app again to see it.',
      nothingToDo: 'The splash is made already.',
      next: ['npx react-native run-ios · run-android'],
    }
  );
}

module.exports = { splash };

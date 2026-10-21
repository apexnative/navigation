// @ts-check
'use strict';

const { createRun } = require('../changes');
const { configChange, readConfig } = require('../config');
const { exec, execInTerminal } = require('../exec');
const { requireSharp, sharpInstallCommand } = require('../images');
const { findProject } = require('../project');
const { createUI } = require('../ui');
const { planAndroidIcons } = require('./android');
const { iconMaker } = require('./images');
const { planIosIcons } = require('./ios');
const { iconsHash, resolveIconSettings } = require('./settings');

/** @typedef {import('./settings').IconOptions} IconOptions */
/** @typedef {import('./images').MakeIcons} MakeIcons */

// The safe zone of Android's adaptive icon: a circle of this diameter in dp, which no mask cuts.
const SAFE_CIRCLE = 66;

/**
 * Makes the app icon of both platforms from a logo and a color, or from a ready square icon: the iOS icon set (one
 * size, dark and tinted), the Android adaptive and themed icons and those of Android 7. The settings go into
 * package.json, so a run without options makes the icon again from them. The images are made with sharp, which the
 * command offers to add to the app's dev dependencies.
 * @param {{
 *   root: string;
 *   options: IconOptions;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   install?: (manager: string, args: string[]) => boolean;
 *   makeIcons?: MakeIcons;
 * }} options `install` runs the package manager, `makeIcons` makes the images (both replaced in tests)
 * @returns {Promise<number>} the exit code
 */
async function icons({
  root,
  options,
  yes,
  dryRun,
  json = false,
  ui = createUI(),
  run = exec,
  install = (manager, args) => execInTerminal(manager, args, { cwd: root }),
  makeIcons,
}) {
  const command = createRun({ command: 'icons', root, json, ui });
  if (!json) ui.banner({ command: 'icons', facts: [{ text: 'iOS · Android' }] });

  const resolved = resolveIconSettings(root, options, readConfig(root).icons);
  if ('errors' in resolved) return command.fail('icons-settings', resolved.errors);
  const { settings } = resolved;

  if (!makeIcons) {
    const sharp = await requireSharp(root, { yes, dryRun, json, ui, install, what: 'The icons' });
    if (!sharp) {
      return command.fail(
        'sharp-missing',
        `The icons command makes its images with sharp: ${sharpInstallCommand(root).join(' ')}, then run it again.`
      );
    }
    makeIcons = iconMaker(sharp);
  }
  let images;
  try {
    images = await makeIcons(root, settings);
  } catch (error) {
    return command.fail('icons-image', error instanceof Error ? error.message : String(error));
  }
  if (images.logo && Math.hypot(images.logo.width, images.logo.height) > SAFE_CIRCLE) {
    command.warn(
      `The logo's corners leave the safe circle of Android's icon (${SAFE_CIRCLE} dp): a round mask may cut them. Make --logo-size smaller.`
    );
  }

  const hash = iconsHash(root, settings);
  const project = findProject(root, run);
  return command.apply(
    [
      ...planIosIcons(project, images, hash),
      ...planAndroidIcons(project, images, settings.background, hash),
      configChange(root, (current) => ({ ...current, icons: settings })),
    ],
    {
      yes,
      dryRun,
      done: 'The icons are made: build the app again to see them.',
      nothingToDo: 'The icons are made already.',
      next: ['npx react-native run-ios · run-android'],
    }
  );
}

module.exports = { icons };

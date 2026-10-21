// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { requireSharp } = require('../images');
const { createUI } = require('../ui');

/** An app without sharp, and a UI that answers the question with `answer`. */
function setup(/** @type {boolean} */ answer) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-images-'));
  const ui = createUI({ out: { isTTY: false, columns: 80, write: () => true }, level: 'none' });
  ui.confirm = async () => answer;
  const install = jest.fn(() => true);
  return { root, ui, install };
}

describe('requireSharp', () => {
  it('installs nothing when the user says no', async () => {
    const { root, ui, install } = setup(false);
    const sharp = await requireSharp(root, {
      yes: false,
      dryRun: false,
      json: false,
      ui,
      install,
      what: 'The splash images',
    });
    expect(sharp).toBeUndefined();
    expect(install).not.toHaveBeenCalled();
  });

  it('installs with the package manager of the lockfile when the user says yes', async () => {
    const { root, ui, install } = setup(true);
    fs.writeFileSync(path.join(root, 'yarn.lock'), '');
    await requireSharp(root, {
      yes: false,
      dryRun: false,
      json: false,
      ui,
      install,
      what: 'The splash images',
    });
    expect(install).toHaveBeenCalledWith('yarn', ['add', '--dev', 'sharp']);
  });

  it('neither asks nor installs on a dry run', async () => {
    const { root, ui, install } = setup(true);
    const confirm = jest.spyOn(ui, 'confirm');
    await requireSharp(root, {
      yes: false,
      dryRun: true,
      json: false,
      ui,
      install,
      what: 'The splash images',
    });
    expect(confirm).not.toHaveBeenCalled();
    expect(install).not.toHaveBeenCalled();
  });
});

// @ts-check
'use strict';

const { Buffer } = require('node:buffer');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { icons } = require('..');
const { iconChecks } = require('../../doctor/checks/icons');
const { findProject } = require('../../project');
const { createUI } = require('../../ui');
const { resolveIconSettings } = require('../settings');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const TEMPLATE = path.join(__dirname, '..', '..', 'setup', '__fixtures__', 'template-0.87');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });
const SET = path.join('ios', 'HelloWorld', 'Images.xcassets', 'AppIcon.appiconset');
const RES = path.join('android', 'app', 'src', 'main', 'res');

/** A copy of the app with the template's Info.plist, icon set and manifest, and a logo. */
function app() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-icons-'));
  fs.cpSync(FIXTURE, root, { recursive: true });
  fs.copyFileSync(
    path.join(TEMPLATE, 'Info.plist'),
    path.join(root, 'ios', 'HelloWorld', 'Info.plist')
  );
  fs.mkdirSync(path.join(root, SET), { recursive: true });
  // The template's set: the old list of iPhone sizes, no images.
  fs.writeFileSync(
    path.join(root, SET, 'Contents.json'),
    '{"images":[{"idiom":"iphone","scale":"2x","size":"20x20"}],"info":{"author":"xcode","version":1}}\n'
  );
  fs.copyFileSync(
    path.join(TEMPLATE, 'AndroidManifest.xml'),
    path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml')
  );
  fs.mkdirSync(path.join(root, 'assets'));
  fs.writeFileSync(path.join(root, 'assets', 'logo.png'), 'logo');
  return root;
}

/**
 * Images as the command lays them out, without sharp: bytes that say which they are.
 * @type {import('../images').MakeIcons}
 */
async function makeIcons(_root, settings) {
  const image = (/** @type {string} */ name) => Buffer.from(name);
  const fromLogo = 'logo' in settings;
  return {
    ios: {
      light: image('light'),
      ...(fromLogo && { dark: image('dark'), tinted: image('tinted') }),
    },
    android: ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'].map((density) => ({
      density,
      foreground: image(`foreground ${density}`),
      ...(fromLogo && { monochrome: image(`monochrome ${density}`) }),
      legacy: image(`legacy ${density}`),
      round: image(`round ${density}`),
    })),
    ...(fromLogo && { logo: { width: 43.2, height: 27 } }),
  };
}

/** Runs the command in the app, printing JSON. */
async function run(
  /** @type {string} */ root,
  /** @type {import('../settings').IconOptions} */ options,
  { yes = true } = {}
) {
  let text = '';
  const out = {
    isTTY: false,
    columns: 80,
    write: (/** @type {string} */ chunk) => (text += chunk),
  };
  const original = process.stdout.write;
  process.stdout.write = /** @type {any} */ (
    (/** @type {string} */ chunk) => {
      text += chunk;
      return true;
    }
  );
  try {
    const code = await icons({
      root,
      options,
      yes,
      dryRun: false,
      json: true,
      ui: createUI({ out, level: 'none' }),
      run: noCli,
      makeIcons,
    });
    return { code, result: JSON.parse(text) };
  } finally {
    process.stdout.write = original;
  }
}

describe('the settings of the icons', () => {
  it('take a logo with its color and size, or a ready icon', () => {
    const root = app();
    expect(resolveIconSettings(root, { logo: 'assets/logo.png', background: '#f3e9d2' })).toEqual({
      settings: { logo: 'assets/logo.png', background: '#F3E9D2', logoSize: 60 },
    });
    expect(resolveIconSettings(root, { image: 'assets/logo.png' })).toEqual({
      settings: { image: 'assets/logo.png', background: '#FFFFFF' },
    });
  });

  it('say what is wrong with them', () => {
    const root = app();
    expect(resolveIconSettings(root, {})).toEqual({
      errors: ['The icon needs a logo, --logo assets/logo.png, or a ready icon, --image icon.png'],
    });
    expect(
      resolveIconSettings(root, { logo: 'assets/none.png', background: 'red', logoSize: '5' })
    ).toEqual({
      errors: [
        '--background is a color such as #FFFFFF',
        '--logo: there is no assets/none.png',
        '--logo-size is a percent of the icon from 20 to 100',
      ],
    });
    expect(resolveIconSettings(root, { logo: 'a.png', image: 'b.png' })).toEqual({
      errors: ['--logo and --image make the icon two ways: give one of them'],
    });
  });

  it('keep the saved ones of the same kind only', () => {
    const root = app();
    const saved = {
      logo: 'assets/logo.png',
      background: '#000000',
      darkLogo: 'assets/logo.png',
      logoSize: 50,
    };
    expect(resolveIconSettings(root, { background: '#FFFFFF' }, saved)).toEqual({
      settings: { ...saved, background: '#FFFFFF' },
    });
    expect(resolveIconSettings(root, { image: 'assets/logo.png' }, saved)).toEqual({
      settings: { image: 'assets/logo.png', background: '#FFFFFF' },
    });
  });
});

describe('icons', () => {
  it('makes the icon set of iOS and the icons of Android, and nothing the second time', async () => {
    const root = app();
    fs.mkdirSync(path.join(root, RES, 'mipmap-hdpi'), { recursive: true });
    fs.writeFileSync(path.join(root, RES, 'mipmap-hdpi', 'ic_launcher.webp'), 'old');
    const { code, result } = await run(root, { logo: 'assets/logo.png', background: '#F3E9D2' });
    expect(code).toBe(0);
    const contents = JSON.parse(fs.readFileSync(path.join(root, SET, 'Contents.json'), 'utf8'));
    expect(
      contents.images.map((/** @type {any} */ image) => [
        image.filename,
        image.appearances?.[0]?.value,
      ])
    ).toEqual([
      ['AppIcon.png', undefined],
      ['AppIcon-Dark.png', 'dark'],
      ['AppIcon-Tinted.png', 'tinted'],
    ]);
    expect(contents.info.author).toMatch(/^apex-navigation icons [0-9a-f]{8}$/);
    expect(fs.readFileSync(path.join(root, SET, 'AppIcon.png'), 'utf8')).toBe('light');
    expect(
      fs.readFileSync(path.join(root, RES, 'mipmap-xxxhdpi', 'ic_launcher_monochrome.png'), 'utf8')
    ).toBe('monochrome xxxhdpi');
    expect(fs.existsSync(path.join(root, RES, 'mipmap-hdpi', 'ic_launcher.webp'))).toBe(false);
    expect(
      fs.readFileSync(path.join(root, RES, 'mipmap-anydpi-v26', 'ic_launcher.xml'), 'utf8')
    ).toContain('<monochrome android:drawable="@mipmap/ic_launcher_monochrome" />');
    expect(
      fs.readFileSync(path.join(root, RES, 'values', 'ic_launcher_background.xml'), 'utf8')
    ).toContain('<color name="ic_launcher_background">#F3E9D2</color>');
    expect(
      JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).apexNavigation.icons
    ).toEqual({
      logo: 'assets/logo.png',
      background: '#F3E9D2',
      logoSize: 60,
    });
    expect(result.warnings).toEqual([]);

    const again = await run(root, {});
    expect(again.result.files.every((/** @type {any} */ file) => file.status === 'done')).toBe(
      true
    );
  });

  it('makes a ready icon without dark, tinted or monochrome versions', async () => {
    const root = app();
    await run(root, { image: 'assets/logo.png' });
    const contents = JSON.parse(fs.readFileSync(path.join(root, SET, 'Contents.json'), 'utf8'));
    expect(contents.images).toHaveLength(1);
    expect(
      fs.readFileSync(path.join(root, RES, 'mipmap-anydpi-v26', 'ic_launcher.xml'), 'utf8')
    ).not.toContain('monochrome');
  });

  it('leaves a color of the same name in another values file to the app', async () => {
    const root = app();
    fs.mkdirSync(path.join(root, RES, 'values'), { recursive: true });
    fs.writeFileSync(
      path.join(root, RES, 'values', 'colors.xml'),
      '<resources><color name="ic_launcher_background">#000000</color></resources>\n'
    );
    const { code, result } = await run(root, { logo: 'assets/logo.png' });
    expect(code).toBe(1);
    expect(result.files.find((/** @type {any} */ file) => file.status === 'manual')).toMatchObject({
      name: 'colors.xml',
    });
  });
});

describe('the app icon in doctor', () => {
  it('warns of an iOS icon set with no image, and of icons out of date', async () => {
    const root = app();
    expect(iconChecks(root, findProject(root, noCli))).toEqual([
      expect.objectContaining({ title: 'App icon', status: 'warn' }),
    ]);
    await run(root, { logo: 'assets/logo.png' });
    expect(iconChecks(root, findProject(root, noCli))).toEqual([
      expect.objectContaining({ status: 'ok', detail: 'made from its settings' }),
    ]);
    fs.writeFileSync(path.join(root, 'assets', 'logo.png'), 'a new logo');
    expect(iconChecks(root, findProject(root, noCli))).toEqual([
      expect.objectContaining({
        status: 'warn',
        detail: 'iOS and Android: not made from the settings in package.json',
        fix: { label: 'npx apex-navigation icons', run: 'icons' },
      }),
    ]);
  });
});

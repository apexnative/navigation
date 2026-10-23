// @ts-check
'use strict';

const { Buffer } = require('node:buffer');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { splash } = require('..');
const { splashChecks } = require('../../doctor/checks/splash');
const { findProject } = require('../../project');
const { createUI } = require('../../ui');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const TEMPLATE = path.join(__dirname, '..', '..', 'setup', '__fixtures__', 'template-0.87');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });
const IOS = path.join('ios', 'HelloWorld');
const RES = path.join('android', 'app', 'src', 'main', 'res');

/** A copy of the app with the template's Info.plist, asset catalog and manifest, and two logos. */
function app() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-splash-'));
  fs.cpSync(FIXTURE, root, { recursive: true });
  fs.copyFileSync(path.join(TEMPLATE, 'Info.plist'), path.join(root, IOS, 'Info.plist'));
  fs.mkdirSync(path.join(root, IOS, 'Images.xcassets'));
  fs.writeFileSync(path.join(root, IOS, 'Images.xcassets', 'Contents.json'), '{}\n');
  fs.copyFileSync(
    path.join(TEMPLATE, 'AndroidManifest.xml'),
    path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml')
  );
  fs.mkdirSync(path.join(root, 'assets'));
  fs.writeFileSync(path.join(root, 'assets', 'logo.png'), 'light');
  fs.writeFileSync(path.join(root, 'assets', 'logo-dark.png'), 'dark');
  return root;
}

/**
 * Images as the command lays them out, without sharp: a logo three times as wide as it is high.
 * @type {import('../images').MakeImages}
 */
async function makeImages(root, settings) {
  const bytes = (/** @type {string} */ file, /** @type {string} */ at) =>
    Buffer.from(`${fs.readFileSync(path.resolve(root, file), 'utf8')} ${at}`);
  return {
    height: settings.logoWidth / 3,
    ios: [1, 2, 3].map((scale) => ({
      scale,
      light: bytes(settings.logo, `@${scale}x`),
      ...(settings.darkLogo && { dark: bytes(settings.darkLogo, `@${scale}x`) }),
    })),
    android: ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'].map((density) => ({
      density,
      light: bytes(settings.logo, density),
      ...(settings.darkLogo && { dark: bytes(settings.darkLogo, density) }),
    })),
  };
}

/** Runs the command in the app, printing JSON. */
async function run(
  /** @type {string} */ root,
  /** @type {Partial<Parameters<typeof splash>[0]>} */ options
) {
  let text = '';
  const out = {
    isTTY: false,
    columns: 80,
    write: (/** @type {string} */ chunk) => (text += chunk),
  };
  const ui = createUI({ out, level: 'none' });
  const original = process.stdout.write;
  process.stdout.write = /** @type {any} */ (
    (/** @type {string} */ chunk) => {
      text += chunk;
      return true;
    }
  );
  try {
    const code = await splash({
      root,
      options: {},
      yes: true,
      dryRun: false,
      json: true,
      ui,
      run: noCli,
      makeImages,
      ...options,
    });
    return { code, result: JSON.parse(text) };
  } finally {
    process.stdout.write = original;
  }
}

const read = (/** @type {string} */ root, /** @type {string} */ file) =>
  fs.readFileSync(path.join(root, file), 'utf8');

const LIGHT_AND_DARK = {
  logo: 'assets/logo.png',
  darkLogo: 'assets/logo-dark.png',
  background: '#fff',
  darkBackground: '#101418',
  logoWidth: '120',
};

describe('splash', () => {
  it('makes the splash on iOS and Android and keeps its settings', async () => {
    const root = app();
    const { code, result } = await run(root, { options: LIGHT_AND_DARK });
    expect(code).toBe(0);
    expect(result).toMatchObject({ command: 'splash', ok: true, applied: true, errors: [] });

    const sets = fs.readdirSync(path.join(root, IOS, 'Images.xcassets'));
    const logoSet = sets.find((name) => name.startsWith('SplashLogo-'));
    expect(logoSet).toMatch(/^SplashLogo-[0-9a-f]{8}\.imageset$/);
    const hash = /** @type {string} */ (logoSet).slice('SplashLogo-'.length, -'.imageset'.length);
    expect(sets).toContain(`SplashBackground-${hash}.colorset`);
    expect(
      fs
        .readdirSync(path.join(root, IOS, 'Images.xcassets', /** @type {string} */ (logoSet)))
        .sort()
    ).toEqual([
      'Contents.json',
      'logo-dark.png',
      'logo-dark@2x.png',
      'logo-dark@3x.png',
      'logo.png',
      'logo@2x.png',
      'logo@3x.png',
    ]);
    const storyboard = read(root, path.join(IOS, 'SplashScreen.storyboard'));
    expect(storyboard).toContain(`image="SplashLogo-${hash}"`);
    expect(storyboard).toContain(`<color key="backgroundColor" name="SplashBackground-${hash}"/>`);
    expect(storyboard).toContain('<constraint firstAttribute="width" constant="120"');
    expect(storyboard).toContain('<constraint firstAttribute="height" constant="40"');

    const plist = read(root, path.join(IOS, 'Info.plist'));
    expect(plist).toContain('<key>UILaunchStoryboardName</key>\n\t<string>SplashScreen</string>');
    expect(plist).toContain(
      '<key>ApexNavigationSplashLogo</key>\n\t<dict>\n\t\t<key>height</key>\n\t\t<real>40</real>\n\t\t<key>width</key>\n\t\t<real>120</real>\n\t</dict>'
    );
    const project = read(root, path.join('ios', 'HelloWorld.xcodeproj', 'project.pbxproj'));
    expect(project).toMatch(
      /\/\* SplashScreen\.storyboard in Resources \*\/ = \{isa = PBXBuildFile;/
    );
    expect(project).toMatch(
      /lastKnownFileType = file\.storyboard; name = SplashScreen\.storyboard; path = HelloWorld\/SplashScreen\.storyboard;/
    );

    for (const density of ['mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi']) {
      expect(
        fs.existsSync(
          path.join(root, RES, `drawable-${density}`, 'apex_navigation_splash_logo.png')
        )
      ).toBe(true);
      expect(
        fs.existsSync(
          path.join(root, RES, `drawable-night-${density}`, 'apex_navigation_splash_logo.png')
        )
      ).toBe(true);
    }
    const values = read(root, path.join(RES, 'values', 'apex_navigation_splash.xml'));
    expect(values).toContain(`(${hash})`);
    expect(values).toContain('<color name="apex_navigation_splash_background">#FFFFFF</color>');
    expect(values).toContain('<dimen name="apex_navigation_splash_logo_height">40dp</dimen>');
    expect(values).toContain('<style name="ApexNavigationSplash" parent="AppTheme">');
    expect(values).toContain('<item name="apexNavigationPostSplashTheme">@style/AppTheme</item>');
    expect(read(root, path.join(RES, 'values-v31', 'apex_navigation_splash.xml'))).toContain(
      '<item name="android:windowSplashScreenAnimatedIcon">@drawable/apex_navigation_splash_logo</item>'
    );
    expect(read(root, path.join(RES, 'values-night', 'apex_navigation_splash.xml'))).toContain(
      '#101418'
    );
    expect(read(root, path.join('android', 'app', 'src', 'main', 'AndroidManifest.xml'))).toContain(
      '        android:exported="true"\n        android:theme="@style/ApexNavigationSplash">'
    );

    expect(JSON.parse(read(root, 'package.json')).apexNavigation.splash).toEqual({
      logo: 'assets/logo.png',
      background: '#FFFFFF',
      darkLogo: 'assets/logo-dark.png',
      darkBackground: '#101418',
      logoWidth: 120,
    });
  });

  it('changes nothing when it runs again from its settings', async () => {
    const root = app();
    await run(root, { options: LIGHT_AND_DARK });
    const { code, result } = await run(root, {});
    expect(code).toBe(0);
    expect(
      new Set(result.files.map((/** @type {{ status: string }} */ file) => file.status))
    ).toEqual(new Set(['done']));
  });

  it('renames the iOS assets for a new logo and removes the old ones', async () => {
    const root = app();
    await run(root, { options: LIGHT_AND_DARK });
    const before = fs.readdirSync(path.join(root, IOS, 'Images.xcassets'));
    fs.writeFileSync(path.join(root, 'assets', 'logo.png'), 'new light');
    await run(root, {});
    const after = fs.readdirSync(path.join(root, IOS, 'Images.xcassets'));
    expect(after).toHaveLength(before.length);
    expect(after.filter((name) => before.includes(name))).toEqual(['Contents.json']);
  });

  it('removes the dark files when the dark logo and background go', async () => {
    const root = app();
    await run(root, { options: LIGHT_AND_DARK });
    const packageJson = JSON.parse(read(root, 'package.json'));
    delete packageJson.apexNavigation.splash.darkLogo;
    delete packageJson.apexNavigation.splash.darkBackground;
    fs.writeFileSync(path.join(root, 'package.json'), JSON.stringify(packageJson));
    await run(root, {});
    expect(
      fs.existsSync(path.join(root, RES, 'drawable-night-xhdpi', 'apex_navigation_splash_logo.png'))
    ).toBe(false);
    expect(fs.existsSync(path.join(root, RES, 'values-night', 'apex_navigation_splash.xml'))).toBe(
      false
    );
  });

  it('keeps the theme of the activity its own as the theme after the splash', async () => {
    const root = app();
    const manifest = path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');
    fs.writeFileSync(
      manifest,
      read(root, path.relative(root, manifest)).replace(
        'android:exported="true"',
        'android:exported="true"\n        android:theme="@style/Main"'
      )
    );
    await run(root, { options: { logo: 'assets/logo.png' } });
    expect(fs.readFileSync(manifest, 'utf8')).not.toContain('@style/Main');
    expect(read(root, path.join(RES, 'values', 'apex_navigation_splash.xml'))).toContain(
      '<item name="apexNavigationPostSplashTheme">@style/Main</item>'
    );
    // Run again, the activity has the splash theme now: the theme after it stays the activity's.
    await run(root, { options: { background: '#000' } });
    expect(read(root, path.join(RES, 'values', 'apex_navigation_splash.xml'))).toContain(
      '<item name="apexNavigationPostSplashTheme">@style/Main</item>'
    );
  });

  it.each([
    [{}, 'The splash needs a logo: --logo assets/logo.png'],
    [{ logo: 'assets/missing.png' }, '--logo: there is no assets/missing.png'],
    [{ logo: 'assets/logo.gif' }, '--logo is a PNG, JPEG, WebP or SVG file, not assets/logo.gif'],
    [{ logo: 'assets/logo.png', background: 'white' }, '--background is a color such as #FFFFFF'],
    [
      { logo: 'assets/logo.png', logoWidth: '300' },
      '--logo-width is a number of points from 1 to 288',
    ],
  ])('refuses %j', async (options, message) => {
    const { code, result } = await run(app(), { options });
    expect(code).toBe(1);
    expect(result.errors).toEqual([{ code: 'splash-settings', message }]);
  });

  it('asks for sharp when the app has none and a tool runs it without --yes', async () => {
    const { code, result } = await run(app(), {
      options: { logo: 'assets/logo.png' },
      yes: false,
      makeImages: undefined,
    });
    expect(code).toBe(1);
    expect(result.errors[0]).toMatchObject({ code: 'sharp-missing' });
    expect(result.errors[0].message).toContain('npm install --save-dev sharp');
  });

  it('warns when the logo is too large for the circle of Android 12+', async () => {
    const { result } = await run(app(), { options: { logo: 'assets/logo.png', logoWidth: '200' } });
    expect(result.warnings).toContain(
      'Android 12+ shows the logo in a circle of 192 dp: its corners are cut at 200 × 67.'
    );
  });
});

describe('doctor', () => {
  const checks = (/** @type {string} */ root) => splashChecks(root, findProject(root, noCli));

  it('finds the splash made from its settings', async () => {
    const root = app();
    await run(root, { options: LIGHT_AND_DARK });
    expect(checks(root)).toEqual([
      { group: 'app', title: 'Splash', status: 'ok', detail: 'made from its settings' },
    ]);
  });

  it('finds a splash older than its settings', async () => {
    const root = app();
    await run(root, { options: LIGHT_AND_DARK });
    fs.writeFileSync(path.join(root, 'assets', 'logo.png'), 'new light');
    expect(checks(root)).toEqual([
      {
        group: 'app',
        title: 'Splash',
        status: 'warn',
        detail: 'iOS and Android: not made from the settings in package.json',
        fix: { label: 'npx apex-navigation splash' },
      },
    ]);
  });

  it('says nothing about UILaunchScreen without a storyboard: the navigation copies it too', () => {
    const root = app();
    const plist = path.join(root, IOS, 'Info.plist');
    fs.writeFileSync(
      plist,
      fs
        .readFileSync(plist, 'utf8')
        .replace(
          /<key>UILaunchStoryboardName<\/key>\s*<string>LaunchScreen<\/string>/,
          '<key>UILaunchScreen</key>\n\t<dict/>'
        )
    );
    expect(checks(root)).toEqual([]);
  });

  it('says nothing about an app without a splash of the CLI and with a storyboard', () => {
    expect(checks(app())).toEqual([]);
  });
});

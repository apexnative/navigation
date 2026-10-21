// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { madeFile, oldFile } = require('./files');
const { size } = require('./settings');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('../project').Project} Project */
/** @typedef {import('./settings').SplashSettings} SplashSettings */
/** @typedef {import('./images').SplashImages} SplashImages */

const THEME = 'ApexNavigationSplash';
const LOGO = 'apex_navigation_splash_logo';
const VALUES = 'apex_navigation_splash.xml';
const ACTIVITY = /<activity\b[^>]*android:name\s*=\s*"(?:[\w.]*\.)?MainActivity"[^>]*>/;
const APPLICATION = /<application\b[^>]*>/;
const THEME_ATTRIBUTE = /android:theme\s*=\s*"@style\/([\w.]+)"/;
const POST_THEME = /<item name="apexNavigationPostSplashTheme">@style\/([\w.]+)<\/item>/;

/**
 * The Android files of the splash: the logo on the canvas of the system splash for each density, light and dark; the
 * colors and the logo size; a splash theme over the app's theme, with the system splash's attributes on Android 12+
 * and a window background of the color and the logo before; the main activity starts with it, and the navigation
 * switches to the app's theme as the activity is created.
 * @param {Project} project
 * @param {SplashSettings} settings
 * @param {SplashImages} images
 * @param {string} hash
 * @returns {FileChange[]}
 */
function planAndroid(project, settings, images, hash) {
  if (!project.manifest) {
    return [
      {
        name: 'Android app',
        platform: 'android',
        status: 'manual',
        note: 'not found',
        howTo: 'Run the command in the root of a React Native app with an android folder.',
      },
    ];
  }
  const res = path.join(path.dirname(project.manifest), 'res');
  const manifest = fs.readFileSync(project.manifest, 'utf8');
  const appTheme = findAppTheme(manifest, path.join(res, 'values', VALUES));
  if (!appTheme) {
    return [
      {
        name: 'AndroidManifest.xml',
        platform: 'android',
        file: project.manifest,
        status: 'manual',
        note: 'no theme, see below',
        howTo: `The splash theme goes over the app's theme: give <application> or MainActivity an android:theme.`,
      },
    ];
  }
  /** @type {FileChange[]} */
  const changes = [
    ...images.android.flatMap(({ density, light, dark: darkLogo }) => {
      const nightFile = path.join(res, `drawable-night-${density}`, `${LOGO}.png`);
      return [
        madeFile('android', path.join(res, `drawable-${density}`, `${LOGO}.png`), light, 'image'),
        ...(darkLogo
          ? [madeFile('android', nightFile, darkLogo, 'image')]
          : fs.existsSync(nightFile)
            ? [oldFile('android', nightFile)]
            : []),
      ];
    }),
    madeFile(
      'android',
      path.join(res, 'drawable', 'apex_navigation_splash.xml'),
      WINDOW_BACKGROUND,
      'before Android 12'
    ),
    madeFile(
      'android',
      path.join(res, 'values', VALUES),
      valuesText(settings, images.height, appTheme, hash),
      'colors, logo size, theme'
    ),
    madeFile('android', path.join(res, 'values-v31', VALUES), v31Text(appTheme), 'Android 12+'),
    ...nightValues(path.join(res, 'values-night', VALUES), settings.darkBackground),
    manifestChange(project.manifest, manifest),
  ];
  return changes;
}

/**
 * The app's theme the splash theme goes over and the activity switches to: the one an earlier run wrote, else the
 * main activity's own, else the application's.
 * @param {string} manifest
 * @param {string} values
 * @returns {string | undefined}
 */
function findAppTheme(manifest, values) {
  const written = fs.existsSync(values) ? POST_THEME.exec(fs.readFileSync(values, 'utf8')) : null;
  if (written) return written[1];
  const activity = ACTIVITY.exec(manifest)?.[0];
  const own = activity && THEME_ATTRIBUTE.exec(activity)?.[1];
  if (own && own !== THEME) return own;
  const application = APPLICATION.exec(manifest)?.[0];
  return (application && THEME_ATTRIBUTE.exec(application)?.[1]) || undefined;
}

// The window background of the splash theme, before Android 12: the color, the logo in the middle at its size.
const WINDOW_BACKGROUND = `<?xml version="1.0" encoding="utf-8"?>
<!-- Made by apex-navigation splash: the splash before Android 12. -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android" android:opacity="opaque">
    <item android:drawable="@color/apex_navigation_splash_background" />
    <item>
        <bitmap android:gravity="center" android:src="@drawable/${LOGO}" />
    </item>
</layer-list>
`;

/**
 * The colors, the logo size the navigation reads, and the splash theme.
 * @param {SplashSettings} settings
 * @param {number} height
 * @param {string} appTheme
 * @param {string} hash
 */
function valuesText(settings, height, appTheme, hash) {
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- Made by apex-navigation splash from "apexNavigation.splash" in package.json (${hash}): run it again to change it. -->
<resources>
    <color name="apex_navigation_splash_background">${settings.background}</color>
    <dimen name="apex_navigation_splash_logo_width">${size(settings.logoWidth)}dp</dimen>
    <dimen name="apex_navigation_splash_logo_height">${size(height)}dp</dimen>
    <style name="${THEME}" parent="${appTheme}">
${themeItems(appTheme, false)}
    </style>
</resources>
`;
}

/**
 * Android 12+: the same theme with the system splash's attributes. A style in values-v31 replaces the whole one.
 * @param {string} appTheme
 */
function v31Text(appTheme) {
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- Made by apex-navigation splash: the system splash of Android 12+. -->
<resources>
    <style name="${THEME}" parent="${appTheme}">
${themeItems(appTheme, true)}
    </style>
</resources>
`;
}

/**
 * @param {string} appTheme
 * @param {boolean} systemSplash
 */
function themeItems(appTheme, systemSplash) {
  return [
    '<item name="android:windowBackground">@drawable/apex_navigation_splash</item>',
    ...(systemSplash
      ? [
          '<item name="android:windowSplashScreenBackground">@color/apex_navigation_splash_background</item>',
          `<item name="android:windowSplashScreenAnimatedIcon">@drawable/${LOGO}</item>`,
        ]
      : []),
    `<item name="apexNavigationPostSplashTheme">@style/${appTheme}</item>`,
    '<item name="apexNavigationSplashBackground">@color/apex_navigation_splash_background</item>',
    `<item name="apexNavigationSplashLogo">@drawable/${LOGO}</item>`,
  ]
    .map((item) => `        ${item}`)
    .join('\n');
}

/**
 * The dark background, or the file of an older splash that had one.
 * @param {string} file
 * @param {string | undefined} darkBackground
 * @returns {FileChange[]}
 */
function nightValues(file, darkBackground) {
  if (!darkBackground) return fs.existsSync(file) ? [oldFile('android', file)] : [];
  const text = `<?xml version="1.0" encoding="utf-8"?>
<!-- Made by apex-navigation splash: the background in dark mode. -->
<resources>
    <color name="apex_navigation_splash_background">${darkBackground}</color>
</resources>
`;
  return [madeFile('android', file, text, 'dark background')];
}

/**
 * AndroidManifest.xml: the main activity starts with the splash theme.
 * @param {string} file
 * @param {string} before
 * @returns {FileChange}
 */
function manifestChange(file, before) {
  const name = 'AndroidManifest.xml';
  const activity = ACTIVITY.exec(before)?.[0];
  if (!activity) {
    return {
      name,
      platform: 'android',
      file,
      status: 'manual',
      note: 'no MainActivity, see below',
      howTo: `Give the activity that starts the app android:theme="@style/${THEME}".`,
    };
  }
  const attribute = `android:theme="@style/${THEME}"`;
  if (activity.includes(attribute)) {
    return { name, platform: 'android', file, status: 'done', note: 'the splash theme already' };
  }
  let tag;
  if (THEME_ATTRIBUTE.test(activity)) {
    tag = activity.replace(THEME_ATTRIBUTE, attribute);
  } else {
    // On a line of its own before the tag closes, as the template has its attributes.
    const end = activity.endsWith('/>') ? '/>' : '>';
    const head = activity.slice(0, -end.length).trimEnd();
    const indent = /\n([ \t]+)\S[^\n]*$/.exec(head)?.[1];
    tag = `${head}${indent ? `\n${indent}` : ' '}${attribute}${end}`;
  }
  return {
    name,
    platform: 'android',
    file,
    status: 'change',
    before,
    // A function, so a `$` in the app's own attributes stays as it is.
    next: before.replace(activity, () => tag),
    note: 'MainActivity starts with the splash',
  };
}

module.exports = { planAndroid, THEME };

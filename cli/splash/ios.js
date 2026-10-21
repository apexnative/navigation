// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { addResourceFile } = require('../xcode/pbxproj');
const { madeFile, oldFile } = require('./files');
const { rgb, size } = require('./settings');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('../project').Project} Project */
/** @typedef {import('./settings').SplashSettings} SplashSettings */
/** @typedef {import('./images').SplashImages} SplashImages */

const STORYBOARD = 'SplashScreen';
const LOGO_SET = /^SplashLogo-[0-9a-f]{8}\.imageset$/;
const COLOR_SET = /^SplashBackground-[0-9a-f]{8}\.colorset$/;
const DARK = [{ appearance: 'luminosity', value: 'dark' }];

/**
 * The iOS files of the splash: a storyboard with the logo in the middle of the screen on the background color; the
 * logo and the color in the asset catalog under names with the hash, the ones of an older splash removed; the
 * storyboard in the Xcode project; Info.plist pointing at it, with the logo size for the navigation.
 * @param {Project} project
 * @param {SplashSettings} settings
 * @param {SplashImages} images
 * @param {string} hash
 * @returns {FileChange[]}
 */
function planIos(project, settings, images, hash) {
  if (!project.infoPlist || !project.xcodeProject) {
    return [
      {
        name: 'iOS app',
        platform: 'ios',
        status: 'manual',
        note: 'not found',
        howTo: 'Run the command in the root of a React Native app with an ios folder.',
      },
    ];
  }
  const appDir = path.dirname(project.infoPlist);
  const assets = path.join(appDir, 'Images.xcassets');
  if (!fs.existsSync(assets)) {
    return [
      {
        name: 'Images.xcassets',
        platform: 'ios',
        status: 'manual',
        note: 'not found',
        howTo: `The splash puts its logo and color into the asset catalog next to Info.plist: ${assets}`,
      },
    ];
  }
  const logoSet = `SplashLogo-${hash}`;
  const colorSet = `SplashBackground-${hash}`;
  const storyboard = path.join(appDir, `${STORYBOARD}.storyboard`);

  /** @type {FileChange[]} */
  const changes = [
    madeFile(
      'ios',
      storyboard,
      storyboardText(logoSet, colorSet, settings, images.height),
      'the splash'
    ),
    ...images.ios.flatMap(({ scale, light, dark }) => [
      madeFile(
        'ios',
        path.join(assets, `${logoSet}.imageset`, logoName(scale, false)),
        light,
        'image'
      ),
      ...(dark
        ? [
            madeFile(
              'ios',
              path.join(assets, `${logoSet}.imageset`, logoName(scale, true)),
              dark,
              'image'
            ),
          ]
        : []),
    ]),
    madeFile(
      'ios',
      path.join(assets, `${logoSet}.imageset`, 'Contents.json'),
      imageSetContents(images, !!settings.darkLogo),
      'the logo'
    ),
    madeFile(
      'ios',
      path.join(assets, `${colorSet}.colorset`, 'Contents.json'),
      colorSetContents(settings),
      'the background'
    ),
    // The sets of an older splash: iOS would find the old launch screen under their names.
    ...fs
      .readdirSync(assets)
      .filter(
        (name) =>
          (LOGO_SET.test(name) && name !== `${logoSet}.imageset`) ||
          (COLOR_SET.test(name) && name !== `${colorSet}.colorset`)
      )

      .map((name) => oldFile('ios', path.join(assets, name))),
    infoPlistChange(project.infoPlist, settings.logoWidth, images.height),
    projectChange(project.xcodeProject, appDir, storyboard),
  ];
  return changes;
}

/**
 * The storyboard: the logo in the middle of the whole screen at its size, on the background color. The middle of the
 * screen, not of its safe area, as the navigation's splash in React Native centers there too.
 * @param {string} logoSet
 * @param {string} colorSet
 * @param {SplashSettings} settings
 * @param {number} height
 */
function storyboardText(logoSet, colorSet, settings, height) {
  const width = size(settings.logoWidth);
  const logoHeight = size(height);
  const { red, green, blue } = rgb(settings.background);
  const x = size((375 - settings.logoWidth) / 2);
  const y = size((667 - height) / 2);
  return `<?xml version="1.0" encoding="UTF-8"?>
<!-- Made by apex-navigation splash from "apexNavigation.splash" in package.json: run it again to change it. -->
<document type="com.apple.InterfaceBuilder3.CocoaTouch.Storyboard.XIB" version="3.0" toolsVersion="15702" targetRuntime="iOS.CocoaTouch" propertyAccessControl="none" useAutolayout="YES" launchScreen="YES" useTraitCollections="YES" useSafeAreas="YES" colorMatched="YES" initialViewController="Spl-Vc-001">
    <device id="retina4_7" orientation="portrait" appearance="light"/>
    <dependencies>
        <deployment identifier="iOS"/>
        <plugIn identifier="com.apple.InterfaceBuilder.IBCocoaTouchPlugin" version="15704"/>
        <capability name="Named colors" minToolsVersion="9.0"/>
        <capability name="Safe area layout guides" minToolsVersion="9.0"/>
        <capability name="documents saved in the Xcode 8 format" minToolsVersion="8.0"/>
    </dependencies>
    <scenes>
        <scene sceneID="Spl-Sc-001">
            <objects>
                <viewController id="Spl-Vc-001" sceneMemberID="viewController">
                    <view key="view" contentMode="scaleToFill" id="Spl-Vw-001">
                        <rect key="frame" x="0.0" y="0.0" width="375" height="667"/>
                        <autoresizingMask key="autoresizingMask" widthSizable="YES" heightSizable="YES"/>
                        <subviews>
                            <imageView userInteractionEnabled="NO" contentMode="scaleAspectFit" image="${logoSet}" translatesAutoresizingMaskIntoConstraints="NO" id="Spl-Lg-001">
                                <rect key="frame" x="${x}" y="${y}" width="${width}" height="${logoHeight}"/>
                                <constraints>
                                    <constraint firstAttribute="width" constant="${width}" id="Spl-Lg-c01"/>
                                    <constraint firstAttribute="height" constant="${logoHeight}" id="Spl-Lg-c02"/>
                                </constraints>
                            </imageView>
                        </subviews>
                        <viewLayoutGuide key="safeArea" id="Spl-Sa-001"/>
                        <color key="backgroundColor" name="${colorSet}"/>
                        <constraints>
                            <constraint firstItem="Spl-Lg-001" firstAttribute="centerX" secondItem="Spl-Vw-001" secondAttribute="centerX" id="Spl-Lg-c03"/>
                            <constraint firstItem="Spl-Lg-001" firstAttribute="centerY" secondItem="Spl-Vw-001" secondAttribute="centerY" id="Spl-Lg-c04"/>
                        </constraints>
                    </view>
                </viewController>
                <placeholder placeholderIdentifier="IBFirstResponder" id="Spl-Fr-001" userLabel="First Responder" sceneMemberID="firstResponder"/>
            </objects>
            <point key="canvasLocation" x="0.0" y="0.0"/>
        </scene>
    </scenes>
    <resources>
        <image name="${logoSet}" width="${width}" height="${logoHeight}"/>
        <namedColor name="${colorSet}">
            <color red="${size(red / 255)}" green="${size(green / 255)}" blue="${size(blue / 255)}" alpha="1" colorSpace="custom" customColorSpace="sRGB"/>
        </namedColor>
    </resources>
</document>
`;
}

/**
 * @param {number} scale
 * @param {boolean} dark
 */
function logoName(scale, dark) {
  return `logo${dark ? '-dark' : ''}${scale === 1 ? '' : `@${scale}x`}.png`;
}

/**
 * @param {SplashImages} images
 * @param {boolean} dark
 */
function imageSetContents(images, dark) {
  const entries = images.ios.flatMap(({ scale }) => [
    { filename: logoName(scale, false), idiom: 'universal', scale: `${scale}x` },
    ...(dark
      ? [
          {
            appearances: DARK,
            filename: logoName(scale, true),
            idiom: 'universal',
            scale: `${scale}x`,
          },
        ]
      : []),
  ]);
  return contents({ images: entries });
}

/** @param {SplashSettings} settings */
function colorSetContents(settings) {
  /** @param {string} color */
  const component = (color) => {
    const { red, green, blue } = rgb(color);
    /** @param {number} value */
    const hex = (value) => `0x${value.toString(16).toUpperCase().padStart(2, '0')}`;
    return {
      color: {
        'color-space': 'srgb',
        components: { alpha: '1.000', blue: hex(blue), green: hex(green), red: hex(red) },
      },
      idiom: 'universal',
    };
  };
  return contents({
    colors: [
      component(settings.background),
      ...(settings.darkBackground
        ? [{ appearances: DARK, ...component(settings.darkBackground) }]
        : []),
    ],
  });
}

/**
 * A Contents.json of the asset catalog, as Xcode writes it.
 * @param {object} value
 */
function contents(value) {
  return JSON.stringify({ ...value, info: { author: 'xcode', version: 1 } }, null, 2) + '\n';
}

/**
 * Info.plist: the launch screen is the splash's storyboard, and the navigation reads the logo size.
 * @param {string} file
 * @param {number} width
 * @param {number} height
 * @returns {FileChange}
 */
function infoPlistChange(file, width, height) {
  const before = fs.readFileSync(file, 'utf8');
  const indent = /\n([ \t]+)<key>/.exec(before)?.[1] ?? '\t';
  let next = setPlistValue(before, 'UILaunchStoryboardName', `<string>${STORYBOARD}</string>`);
  next = setPlistValue(
    next,
    'ApexNavigationSplashLogo',
    `<dict>\n${indent}${indent}<key>height</key>\n${indent}${indent}<real>${size(height)}</real>\n` +
      `${indent}${indent}<key>width</key>\n${indent}${indent}<real>${size(width)}</real>\n${indent}</dict>`
  );
  if (next === undefined) {
    return {
      name: 'Info.plist',
      platform: 'ios',
      file,
      status: 'manual',
      note: 'changed by hand, see below',
      howTo: `Set UILaunchStoryboardName to ${STORYBOARD}, and ApexNavigationSplashLogo to a dictionary with the logo's width (${size(width)}) and height (${size(height)}).`,
    };
  }
  return next === before
    ? { name: 'Info.plist', platform: 'ios', file, status: 'done', note: 'the splash already' }
    : {
        name: 'Info.plist',
        platform: 'ios',
        file,
        status: 'change',
        before,
        next,
        note: 'launch screen: the splash',
      };
}

/**
 * The plist with a key of its top dictionary set to a value (a string or a dictionary): in place when it is there,
 * else last, indented as the other keys. Undefined for a plist the CLI can't read.
 * @param {string | undefined} text
 * @param {string} key
 * @param {string} value
 * @returns {string | undefined}
 */
function setPlistValue(text, key, value) {
  if (text === undefined) return undefined;
  const existing = new RegExp(
    String.raw`(<key>${key}</key>\s*)(<string>[^<]*</string>|<dict>[\s\S]*?</dict>|<dict/>)`
  );
  if (existing.test(text)) return text.replace(existing, (_, head) => head + value);
  const end = /\n<\/dict>\s*<\/plist>\s*$/.exec(text);
  if (!end) return undefined;
  const indent = /\n([ \t]+)<key>/.exec(text)?.[1] ?? '\t';
  return `${text.slice(0, end.index)}\n${indent}<key>${key}</key>\n${indent}${value}${text.slice(end.index)}`;
}

/**
 * The Xcode project builds the storyboard into the app, next to the template's launch screen or the asset catalog.
 * @param {string} file
 * @param {string} appDir
 * @param {string} storyboard
 * @returns {FileChange}
 */
function projectChange(file, appDir, storyboard) {
  const before = fs.readFileSync(file, 'utf8');
  const name = 'project.pbxproj';
  const anchor = ['LaunchScreen.storyboard', 'Images.xcassets'].find((candidate) =>
    before.includes(`/* ${candidate} in Resources */`)
  );
  const added = anchor
    ? addResourceFile(before, {
        anchor,
        anchorFile: path.join(appDir, anchor),
        file: storyboard,
      })
    : { status: /** @type {const} */ ('manual'), reason: 'no resources to put it next to' };
  if (added.status === 'done') {
    return {
      name,
      platform: 'ios',
      file,
      status: 'done',
      note: `${STORYBOARD}.storyboard already`,
    };
  }
  if (added.status === 'manual') {
    return {
      name,
      platform: 'ios',
      file,
      status: 'manual',
      note: added.reason,
      howTo: `Add ${STORYBOARD}.storyboard to the app's target in Xcode (Copy Bundle Resources).`,
    };
  }
  return {
    name,
    platform: 'ios',
    file,
    status: 'change',
    before,
    next: added.next,
    note: `${STORYBOARD}.storyboard`,
  };
}

module.exports = { planIos, setPlistValue, STORYBOARD };

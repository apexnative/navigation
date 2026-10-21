// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { planApp } = require('../steps/app');
const { planAppDelegate } = require('../steps/appDelegate');
const { planEntry } = require('../steps/entry');
const { planInfoPlist } = require('../steps/infoPlist');
const { planMainActivity } = require('../steps/mainActivity');
const { planMainApplication } = require('../steps/mainApplication');
const { planManifest } = require('../steps/manifest');
const { planStyles } = require('../steps/styles');

const fixtures = path.join(__dirname, '..', '__fixtures__');
const read = (/** @type {string} */ dir, /** @type {string} */ name) =>
  fs.readFileSync(path.join(fixtures, dir, name), 'utf8');

const STEPS = /** @type {const} */ ([
  ['MainApplication.kt', planMainApplication],
  ['MainActivity.kt', planMainActivity],
  ['AndroidManifest.xml', planManifest],
  ['styles.xml', planStyles],
  ['AppDelegate.swift', planAppDelegate],
  ['Info.plist', planInfoPlist],
  ['index.js', planEntry],
  ['App.tsx', planApp],
]);

describe.each(['template-0.85', 'template-0.87'])('the React Native template (%s)', (template) => {
  it.each(STEPS)('sets up %s', (name, plan) => {
    const result = plan(read(template, name));
    expect(result.status).toBe('change');
    expect(result.status === 'change' && result.next).toBe(read('setup-done', name));
  });

  it.each(STEPS)('leaves %s alone once it is set up', (name, plan) => {
    expect(plan(read('setup-done', name)).status).toBe('done');
  });
});

describe('a file changed by hand', () => {
  const template = (/** @type {string} */ name) => read('template-0.87', name);

  it('keeps the code of its own in onCreate', () => {
    const text = template('MainApplication.kt').replace(
      '    loadReactNative(this)\n',
      '    loadReactNative(this)\n    Analytics.start(this)\n'
    );
    const result = planMainApplication(text);
    expect(result.status).toBe('change');
    const next = result.status === 'change' ? result.next : '';
    expect(next).toContain(
      'override fun onCreate() {\n    super.onCreate()\n    Analytics.start(this)\n  }'
    );
    expect(next).not.toContain('loadReactNative');
  });

  it("can't tell how to move a delegate of the activity's own", () => {
    const text = template('MainActivity.kt').replace(
      'DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)',
      'object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {}'
    );
    expect(planMainActivity(text).status).toBe('manual');
  });

  it('keeps the code of its own in the launch', () => {
    const text = template('AppDelegate.swift').replace(
      '    let delegate = ReactNativeDelegate()\n',
      '    FirebaseApp.configure()\n    let delegate = ReactNativeDelegate()\n'
    );
    const result = planAppDelegate(text);
    expect(result.status).toBe('change');
    expect(result.status === 'change' && result.next).toContain(
      '    FirebaseApp.configure()\n    self.reactNativeDelegate = ReactNativeDelegate()\n    return super.application('
    );
  });

  it("can't tell how to move code that uses the template's window", () => {
    const text = template('AppDelegate.swift').replace(
      '    return true\n',
      '    window?.tintColor = .systemRed\n    return true\n'
    );
    expect(planAppDelegate(text).status).toBe('manual');
  });

  it('adds the status bar key when the plist has none', () => {
    const text = template('Info.plist').replace(
      /\t<key>UIViewControllerBasedStatusBarAppearance<\/key>\n\t<false\/>\n/,
      ''
    );
    const result = planInfoPlist(text);
    expect(result.status).toBe('change');
    expect(result.status === 'change' && result.next).toBe(read('setup-done', 'Info.plist'));
  });

  it("can't tell how to start an entry point of the app's own", () => {
    const text = template('index.js').replace('() => App', '() => withStore(App)');
    expect(planEntry(text).status).toBe('manual');
  });

  it('keeps the color scheme and the imports the app uses elsewhere', () => {
    const text = template('App.tsx')
      .replace(
        "import { StatusBar, StyleSheet, useColorScheme, View } from 'react-native';",
        "import {\n  StatusBar,\n  StyleSheet,\n  useColorScheme,\n  View,\n} from 'react-native';"
      )
      .replace('      <AppContent />', '      <AppContent dark={isDarkMode} />');
    const result = planApp(text);
    expect(result.status).toBe('change');
    const next = result.status === 'change' ? result.next : '';
    expect(next).toContain(
      "import {\n  StyleSheet,\n  useColorScheme,\n  View,\n} from 'react-native';"
    );
    expect(next).toContain("  const isDarkMode = useColorScheme() === 'dark';\n");
    expect(next).not.toContain('StatusBar');
  });

  it("keeps the app's own predictive back setting", () => {
    const text = template('AndroidManifest.xml').replace(
      '<application\n',
      '<application\n      android:enableOnBackInvokedCallback="false"\n'
    );
    expect(planManifest(text)).toEqual({ status: 'done', note: "the app's own setting" });
  });

  it("sees the app's own setting in single quotes", () => {
    const text = template('AndroidManifest.xml').replace(
      '<application\n',
      "<application\n      android:enableOnBackInvokedCallback='false'\n"
    );
    expect(planManifest(text).status).toBe('done');
  });

  it('keeps a dollar sign in the attributes of the application', () => {
    const text = template('AndroidManifest.xml').replace(
      'android:label="@string/app_name"',
      () => 'android:label="Pay $$ now"'
    );
    const result = planManifest(text);
    expect(result.status === 'change' && result.next).toContain('android:label="Pay $$ now"');
  });

  it("keeps a theme of the app's own", () => {
    const text = template('styles.xml').replace(
      'Theme.AppCompat.DayNight.NoActionBar',
      'Theme.Material3.DayNight.NoActionBar'
    );
    expect(planStyles(text)).toEqual({ status: 'done', note: "the app's own theme" });
  });

  it("can't tell what to do with a status bar of the app's own", () => {
    const text = template('App.tsx').replace(
      "barStyle={isDarkMode ? 'light-content' : 'dark-content'}",
      'hidden'
    );
    expect(planApp(text).status).toBe('manual');
  });
});

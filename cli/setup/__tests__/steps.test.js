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
const { planMetro } = require('../steps/metro');
const { planSceneDelegate } = require('../steps/sceneDelegate');
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
  ['metro.config.js', planMetro],
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

// React Native 0.88 starts React Native in a scene delegate, which its Info.plist names; the other files are as before.
describe('the React Native template (template-0.88)', () => {
  const STEPS_0_88 = /** @type {const} */ ([...STEPS, ['SceneDelegate.swift', planSceneDelegate]]);
  const done = (/** @type {string} */ name) =>
    fs.existsSync(path.join(fixtures, 'setup-done-0.88', name))
      ? read('setup-done-0.88', name)
      : read('setup-done', name);

  it.each(STEPS_0_88)('sets up %s', (name, plan) => {
    const result = plan(read('template-0.88', name));
    expect(result.status).toBe('change');
    expect(result.status === 'change' && result.next).toBe(done(name));
  });

  it.each(STEPS_0_88)('leaves %s alone once it is set up', (name, plan) => {
    expect(plan(done(name)).status).toBe('done');
  });

  it('keeps the code of its own in the launch', () => {
    const text = read('template-0.88', 'AppDelegate.swift').replace(
      '    true\n',
      '    FirebaseApp.configure()\n    return true\n'
    );
    const result = planAppDelegate(text);
    expect(result.status).toBe('change');
    expect(result.status === 'change' && result.next).toContain(
      '    FirebaseApp.configure()\n    self.reactNativeDelegate = ReactNativeDelegate()\n    return super.application('
    );
  });

  it("can't tell how to move code of the app's own in the scene delegate", () => {
    const text = read('template-0.88', 'SceneDelegate.swift').replace(
      '    window = UIWindow(windowScene: windowScene)\n',
      '    window = UIWindow(windowScene: windowScene)\n    window?.tintColor = .systemRed\n'
    );
    expect(planSceneDelegate(text).status).toBe('manual');
  });

  it('sees a scene delegate of its own that moves the window into its scene', () => {
    const text =
      'class SceneDelegate: UIResponder, UIWindowSceneDelegate {\n' +
      '  func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {\n' +
      '    window = ApexNavigation.attach(to: scene as! UIWindowScene)\n  }\n}\n';
    expect(planSceneDelegate(text).status).toBe('done');
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
      'override fun onCreate() {\n    super.onCreate()\n    loadReactNative(this)\n    Analytics.start(this)\n  }'
    );
  });

  it('adds loadReactNative(this) to an application of the navigation without it', () => {
    const done = read('setup-done', 'MainApplication.kt');
    const without = done
      .replace('    loadReactNative(this)\n', '')
      .replace('import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative\n', '');
    const result = planMainApplication(without);
    expect(result.status === 'change' && result.next).toBe(done);
    const noOnCreate = without.replace(/\n\n {2}override fun onCreate\(\) \{[\s\S]*?\n {2}\}/, '');
    const added = planMainApplication(noOnCreate);
    expect(added.status === 'change' && added.next).toBe(done);
  });

  it('drops a load of its own when it adds loadReactNative(this)', () => {
    const done = read('setup-done', 'MainApplication.kt');
    const own = done
      .replace(
        'import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative\n',
        'import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load\n'
      )
      .replace(
        '    loadReactNative(this)\n',
        '    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {\n      load()\n    }\n'
      );
    const result = planMainApplication(own);
    expect(result.status === 'change' && result.next).toBe(done);
    const twice = done.replace(
      '    loadReactNative(this)\n',
      '    SoLoader.init(this, OpenSourceMergedSoMapping)\n    loadReactNative(this)\n'
    );
    const fixed = planMainApplication(twice);
    expect(fixed.status === 'change' && fixed.next).toBe(done);
  });

  it('drops the template entry point with its comment, and its import', () => {
    const done = read('setup-done', 'MainApplication.kt');
    const own = done
      .replace(
        'import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative\n',
        'import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load\n'
      )
      .replace(
        '    loadReactNative(this)\n',
        '    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {\n' +
          '      // If you opted-in for the New Architecture, we load the native entry point for this app.\n' +
          '      load()\n    }\n'
      );
    const result = planMainApplication(own);
    expect(result.status === 'change' && result.next).toBe(done);
  });

  it('leaves a load with arguments to the author', () => {
    const done = read('setup-done', 'MainApplication.kt');
    const text = done
      .replace(
        'import com.facebook.react.ReactHost\n',
        'import com.facebook.react.ReactHost\nimport com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load\n'
      )
      .replace(
        '    loadReactNative(this)\n',
        '    loadReactNative(this)\n    load(bridgelessEnabled = true)\n'
      );
    expect(planMainApplication(text).status).toBe('manual');
  });

  it('keeps a function of its own named load', () => {
    const done = read('setup-done', 'MainApplication.kt');
    const text = done.replace(
      '    loadReactNative(this)\n',
      '    loadReactNative(this)\n    load()\n'
    );
    expect(planMainApplication(text).status).toBe('done');
  });

  it('adds onCreate inside the class, past what follows it', () => {
    const done = read('setup-done', 'MainApplication.kt');
    const without = done
      .replace(/\n\n {2}override fun onCreate\(\) \{[\s\S]*?\n {2}\}/, '')
      .replace('import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative\n', '');
    const result = planMainApplication(`${without}\n// The end of the application {}\n`);
    expect(result.status === 'change' && result.next).toBe(
      `${done}\n// The end of the application {}\n`
    );
  });

  it("doesn't count a brace in a string when it adds onCreate", () => {
    const done = read('setup-done', 'MainApplication.kt');
    const without = done
      .replace(/\n\n {2}override fun onCreate\(\) \{[\s\S]*?\n {2}\}/, '')
      .replace('import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative\n', '')
      .replace('  override val reactHost', '  val brace = "}"\n\n  override val reactHost');
    const result = planMainApplication(without);
    expect(result.status === 'change' && result.next).toBe(
      done.replace('  override val reactHost', '  val brace = "}"\n\n  override val reactHost')
    );
  });

  it("can't tell where to load React Native in an onCreate without super.onCreate()", () => {
    const text = read('setup-done', 'MainApplication.kt').replace(
      '    super.onCreate()\n    loadReactNative(this)\n',
      '    Analytics.start(this)\n'
    );
    expect(planMainApplication(text).status).toBe('manual');
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

describe('metro.config.js', () => {
  it('wraps a config exported at the end, after the requires', () => {
    const result = planMetro("const x = require('x');\n\nmodule.exports = x({});\n");
    expect(result.status === 'change' && result.next).toBe(
      "const x = require('x');\nconst { withApexNavigation } = require('@apexnative/navigation/metro');\n\n" +
        'module.exports = withApexNavigation(x({}));\n'
    );
  });

  it("can't tell how to wrap a config changed after it is exported", () => {
    const text = 'module.exports = {};\nmodule.exports.resolver = {};\n';
    expect(planMetro(text).status).toBe('manual');
  });
});

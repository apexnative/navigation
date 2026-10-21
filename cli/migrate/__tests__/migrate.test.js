// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { migrate } = require('..');
const { createUI } = require('../../ui');

const TEMPLATE = path.join(__dirname, '..', '..', 'setup', '__fixtures__', 'template-0.87');

/** An app on react-native-navigation, as its install writes the native files, on React Native 0.87. */
const RNN = {
  'package.json': JSON.stringify(
    {
      name: 'MyApp',
      dependencies: {
        react: '19.2.0',
        'react-native': '0.87.1',
        'react-native-navigation': '^8.8.9',
      },
    },
    null,
    2
  ),
  'android/build.gradle':
    'buildscript {\n    ext {\n        kotlinVersion = "2.0.21"\n        RNNKotlinVersion = kotlinVersion\n        compileSdkVersion = 35\n    }\n}\n',
  'android/app/src/main/java/com/myapp/MainApplication.kt': `package com.myapp

import com.facebook.react.PackageList
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.reactnativenavigation.NavigationApplication
import com.reactnativenavigation.RNNToggles
import com.reactnativenavigation.react.NavigationPackage
import com.reactnativenavigation.react.NavigationReactNativeHost

class MainApplication : NavigationApplication(mapOf(
    RNNToggles.TOP_BAR_COLOR_ANIMATION__PUSH to true,
    RNNToggles.TAB_BAR_TRANSLUCENCE to true,
)) {
    override val reactNativeHost: ReactNativeHost = object : NavigationReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> {
            val packages = PackageList(this).packages
            packages.add(NavigationPackage())
            return packages
        }
    }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)
}
`,
  'android/app/src/main/java/com/myapp/MainActivity.kt':
    'package com.myapp\n\nimport com.reactnativenavigation.NavigationActivity\n\nclass MainActivity : NavigationActivity()\n',
  'ios/MyApp/AppDelegate.swift': `import UIKit
import React
import ReactNativeNavigation
import ReactAppDependencyProvider

@main
class AppDelegate: RNNAppDelegate {
  override func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
    self.reactNativeDelegate = ReactNativeDelegate()
    super.application(application, didFinishLaunchingWithOptions: launchOptions)
    return true
  }
}
`,
  'ios/Podfile': "platform :ios, '15.1'\ntarget 'MyApp' do\n  pod 'HMSegmentedControl'\nend\n",
  'index.js': `/**
 * @format
 */
import { Navigation } from 'react-native-navigation';
import './src/polyfills';
import { HomeScreen } from './src/screens/Home';
import { Details } from './src/screens/Details';

Navigation.registerComponent('Home', () => HomeScreen);
Navigation.registerComponent('com.myapp.Details', () => Details);

Navigation.events().registerAppLaunchedListener(() => {
  Navigation.setRoot({
    root: {
      bottomTabs: {
        children: [
          { stack: { children: [{ component: { name: 'Home' } }], options: { bottomTab: { text: 'Home' } } } },
          { component: { name: 'com.myapp.Details' } },
        ],
      },
    },
  });
});
`,
  'src/screens/Home.tsx': `import { Navigation } from 'react-native-navigation';

export function HomeScreen(props: { componentId: string }) {
  const open = () => Navigation.push(props.componentId, { component: { name: 'com.myapp.Details' } });
  return open && null;
}
HomeScreen.options = { topBar: { visible: true } };
`,
  'src/screens/Details.tsx': 'export function Details() {\n  return null;\n}\n',
  'src/polyfills.ts': '',
  'tsconfig.json': '{}\n',
};

/**
 * An app in a temporary folder, with React Native and TypeScript where the CLI looks for them.
 * @param {Record<string, string>} files
 */
function app(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-migrate-'));
  const put = (/** @type {string} */ file, /** @type {string} */ text) => {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), text);
  };
  for (const [file, text] of Object.entries(files)) put(file, text);
  put(
    'android/app/src/main/AndroidManifest.xml',
    fs.readFileSync(path.join(TEMPLATE, 'AndroidManifest.xml'), 'utf8')
  );
  put('ios/MyApp/Info.plist', fs.readFileSync(path.join(TEMPLATE, 'Info.plist'), 'utf8'));
  put('node_modules/react-native/package.json', '{ "version": "0.87.1" }');
  fs.symlinkSync(
    path.dirname(require.resolve('typescript/package.json')),
    path.join(root, 'node_modules', 'typescript')
  );
  return root;
}

/**
 * Runs migrate with --json; the install of the dependencies and the pods succeed.
 * @param {string} root
 * @param {{ yes?: boolean; from?: string }} [options]
 */
async function run(root, { yes = true, from } = {}) {
  /** @type {string[]} */
  const calls = [];
  let text = '';
  const out = { isTTY: false, columns: 80, write: () => true };
  const original = process.stdout.write;
  process.stdout.write = /** @type {any} */ (
    (/** @type {string} */ chunk) => {
      text += chunk;
      return true;
    }
  );
  try {
    const code = await migrate({
      root,
      from,
      yes,
      dryRun: false,
      json: true,
      ui: createUI({ out, level: 'none' }),
      platform: 'darwin',
      run: (program, args) => {
        calls.push([program, ...args].join(' '));
        return { ok: program !== 'npx', stdout: '', stderr: '' };
      },
    });
    return { code, result: JSON.parse(text), calls };
  } finally {
    process.stdout.write = original;
  }
}

/**
 * @param {string} root
 * @param {string} file
 */
const read = (root, file) => fs.readFileSync(path.join(root, file), 'utf8');

describe('migrate from react-native-navigation', () => {
  it('moves the package, the native files, the screens and the first root', async () => {
    const root = app(RNN);
    const { code, result, calls } = await run(root);
    expect(result.from).toBe('react-native-navigation');
    expect(JSON.parse(read(root, 'package.json')).dependencies).toEqual({
      '@apexnative/navigation': expect.stringMatching(/^\^/),
      react: '19.2.0',
      'react-native': '0.87.1',
    });
    const application = read(root, 'android/app/src/main/java/com/myapp/MainApplication.kt');
    expect(application).toContain('import dev.apexnative.navigation.NavigationApplication');
    expect(application).toContain('ANNToggles.TOP_BAR_COLOR_ANIMATION__PUSH to true,');
    expect(application).not.toMatch(/NavigationPackage|TAB_BAR_TRANSLUCENCE|reactnativenavigation/);
    expect(read(root, 'android/app/src/main/java/com/myapp/MainActivity.kt')).toContain(
      'import dev.apexnative.navigation.NavigationActivity'
    );
    expect(read(root, 'android/build.gradle')).not.toContain('RNNKotlinVersion');
    expect(read(root, 'ios/MyApp/AppDelegate.swift')).toContain(
      'import ApexNavigation\nimport ReactAppDependencyProvider\n\n@main\nclass AppDelegate: ANNAppDelegate {'
    );
    expect(read(root, 'ios/Podfile')).not.toContain('HMSegmentedControl');
    expect(read(root, 'Navigation.ts')).toBe(
      [
        "import { createNavigation, Tabs, Tab } from '@apexnative/navigation';",
        "import { HomeScreen } from './src/screens/Home';",
        "import { Details } from './src/screens/Details';",
        '',
        '// The screens of registerComponent, under the names the app gave them, and the root of its setRoot.',
        'export const Navigation = createNavigation({',
        '  screens: {',
        '    Home: HomeScreen,',
        "    'com.myapp.Details': Details,",
        '  },',
        '  root: Tabs(',
        "    Tab('Home').label('Home'),",
        "    Tab('com.myapp.Details'),",
        '  ),',
        '});',
        '',
      ].join('\n')
    );
    expect(read(root, 'index.js')).toBe(
      [
        '/**',
        ' * @format',
        ' */',
        '',
        "import './src/polyfills';",
        "import { AppRegistry } from '@apexnative/navigation';",
        "import { name } from './app.json';",
        "import { Navigation } from './Navigation';",
        '',
        'AppRegistry.registerComponent(name, Navigation);',
        '',
      ].join('\n')
    );
    expect(fs.existsSync(path.join(root, 'navigation-env.d.ts'))).toBe(true);
    // The install of the dependencies, then the pods once.
    expect(calls).toEqual(expect.arrayContaining(['npm install', 'pod install']));
    // What is left is the report: exit 1 while it has places.
    expect(code).toBe(1);
    expect(
      result.report.map((/** @type {any} */ item) => [item.file, item.line, item.step])
    ).toEqual([
      ['android/build.gradle', 4, 'native'],
      [path.join('src', 'screens', 'Home.tsx'), 3, 'commands'],
      [path.join('src', 'screens', 'Home.tsx'), 4, 'commands'],
      [path.join('src', 'screens', 'Home.tsx'), 7, 'options'],
    ]);
  });

  it('leaves the entry point to the report when the screens are registered elsewhere', async () => {
    const root = app({
      ...RNN,
      'src/screens/index.ts':
        "import { Navigation } from 'react-native-navigation';\nimport { Details } from './Details';\nexport function registerScreens() {\n  Navigation.registerComponent('com.myapp.Details', () => Details);\n}\n",
    });
    const { result } = await run(root, { yes: false });
    expect(result.warnings[0]).toBe(
      `The screens and the first root stay for the report: screens are registered in ${path.join('src', 'screens', 'index.ts')} too.`
    );
    expect(
      result.files.find((/** @type {any} */ file) => file.name === 'Navigation.ts')
    ).toBeUndefined();
    expect(result.report.filter((/** @type {any} */ item) => item.step === 'screens')).toHaveLength(
      3
    );
  });
});

describe('the move of the entry point', () => {
  it('keeps the comments of what stays', async () => {
    const root = app({
      ...RNN,
      'index.js': RNN['index.js'].replace(
        "import './src/polyfills';",
        "// Before anything else.\nimport './src/polyfills';"
      ),
    });
    await run(root, { yes: true });
    expect(read(root, 'index.js')).toContain("// Before anything else.\nimport './src/polyfills';");
  });

  it.each([
    [
      'a component index.js makes itself',
      (/** @type {string} */ text) =>
        text.replace(
          "Navigation.registerComponent('Home', () => HomeScreen);",
          "const Local = () => null;\nNavigation.registerComponent('Home', () => Local);"
        ),
      "the screens use Local, which index.js doesn't import",
    ],
    [
      'the old library used besides',
      (/** @type {string} */ text) => `${text}Navigation.setDefaultOptions({});\n`,
      'index.js uses Navigation of react-native-navigation besides the screens and the root',
    ],
    [
      'props of a component in the root',
      (/** @type {string} */ text) =>
        text.replace(
          "{ component: { name: 'com.myapp.Details' } }",
          "{ component: { name: 'com.myapp.Details', passProps: { id: 1 } } }"
        ),
      'the launched listener does more than one setRoot of stacks, tabs and screens',
    ],
  ])('stays for the report with %s', async (_case, change, reason) => {
    const root = app({ ...RNN, 'index.js': change(RNN['index.js']) });
    const { result } = await run(root, { yes: false });
    expect(result.warnings[0]).toBe(
      `The screens and the first root stay for the report: ${reason}.`
    );
    expect(
      result.files.find((/** @type {any} */ file) => file.name === 'Navigation.ts')
    ).toBeUndefined();
  });
});

describe('migrate again, and the Java of an app', () => {
  it('rewrites every Android file that uses the old library, and installs nothing the second time', async () => {
    const root = app({
      ...RNN,
      'android/app/src/main/java/com/myapp/FragmentCreator.java':
        'package com.myapp;\n\nimport com.reactnativenavigation.viewcontrollers.externalcomponent.ExternalComponentCreator;\n\npublic class FragmentCreator {}\n',
    });
    await run(root);
    expect(read(root, 'android/app/src/main/java/com/myapp/FragmentCreator.java')).toContain(
      'import dev.apexnative.navigation.viewcontrollers.externalcomponent.ExternalComponentCreator;'
    );
    const again = await run(root, { from: 'react-native-navigation' });
    expect(
      again.result.files.find((/** @type {any} */ file) => file.name === 'package.json')
    ).toMatchObject({
      status: 'done',
    });
    expect(again.calls).not.toContain('npm install');
  });
});

describe('migrate from React Navigation', () => {
  it('sets the app up as the first screen, with the rest as the report', async () => {
    const root = app({
      'package.json': JSON.stringify({
        name: 'MyApp',
        dependencies: { 'react-native': '0.87.1', '@react-navigation/native': '^7.0.0' },
      }),
      'index.js': fs.readFileSync(path.join(TEMPLATE, 'index.js'), 'utf8'),
      'App.tsx': [
        "import { NavigationContainer } from '@react-navigation/native';",
        "import { createNativeStackNavigator } from '@react-navigation/native-stack';",
        'const Stack = createNativeStackNavigator();',
        'export default function App() {',
        '  return null;',
        '}',
        '',
      ].join('\n'),
      'tsconfig.json': '{}\n',
    });
    const { result } = await run(root, { yes: false });
    expect(result.from).toBe('react-navigation');
    expect(result.files.map((/** @type {any} */ file) => file.name)).toEqual(
      expect.arrayContaining(['package.json', 'index.js', 'Navigation.ts', 'App.tsx'])
    );
    expect(result.report.map((/** @type {any} */ item) => item.step)).toEqual([
      'register',
      'root',
      'root',
    ]);
  });

  it('fails in an app on neither', async () => {
    const root = app({ 'package.json': '{ "name": "MyApp" }', 'tsconfig.json': '{}\n' });
    const { code, result } = await run(root);
    expect(code).toBe(1);
    expect(result.errors[0].code).toBe('nothing-to-migrate');
  });
});

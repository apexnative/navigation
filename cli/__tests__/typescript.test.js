// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const ts = require('typescript');
const { configChange, readConfig, screensDir } = require('../config');
const { findProject } = require('../project');
const {
  addScreen,
  exportsType,
  findConfig,
  findScreens,
  importPath,
  keysOf,
  navigationImport,
  navigationModule,
  propertyValue,
  stringsOf,
} = require('../typescript');

const FIXTURE = path.join(__dirname, '..', '__fixtures__', 'app-0.87');
// Without the React Native CLI the project is found at the template's paths.
const noCli = () => ({ ok: false, stdout: '', stderr: '' });

/** A copy of the app after setup, with files written over it. */
function app(/** @type {Record<string, string>} */ files = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-app-'));
  fs.cpSync(FIXTURE, root, { recursive: true });
  for (const [name, text] of Object.entries(files)) fs.writeFileSync(path.join(root, name), text);
  return root;
}

/** The screens' file after adding Profile. */
function withProfile(/** @type {string} */ root) {
  const screens = findScreens(ts, path.join(root, 'Navigation.ts'));
  if (!screens) throw new Error('no screens');
  const result = addScreen(ts, screens, {
    name: 'Profile',
    value: 'ProfileScreen',
    importFrom: importPath(screens.file, path.join(root, 'src', 'screens', 'Profile.tsx')),
  });
  return { file: screens.file, result };
}

describe('the app the CLI works on', () => {
  it('has its Xcode project and its Android package where the template puts them', () => {
    const project = findProject(FIXTURE, noCli);
    expect(path.relative(FIXTURE, project.xcodeProject ?? '')).toBe(
      path.join('ios', 'HelloWorld.xcodeproj', 'project.pbxproj')
    );
    expect(project.androidPackage).toBe('com.helloworld');
    expect(path.relative(FIXTURE, project.androidSources ?? '')).toBe(
      path.join('android', 'app', 'src', 'main', 'java')
    );
    expect(path.relative(FIXTURE, project.appBuildGradle ?? '')).toBe(
      path.join('android', 'app', 'build.gradle')
    );
    expect(path.relative(FIXTURE, project.rootBuildGradle ?? '')).toBe(
      path.join('android', 'build.gradle')
    );
  });

  it('finds the navigation through the entry point', () => {
    expect(navigationModule(ts, path.join(FIXTURE, 'index.js'))).toBe(
      path.join(FIXTURE, 'Navigation.ts')
    );
  });
});

describe('the config of the navigation', () => {
  it('tells the export the entry point registers', () => {
    expect(navigationImport(ts, path.join(FIXTURE, 'index.js'))).toEqual({
      file: path.join(FIXTURE, 'Navigation.ts'),
      exported: 'Navigation',
    });
  });

  it('reads a key written there or in a const, with its strings and keys', () => {
    const root = app({
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        "const prefixes = ['myapp://', 'https://example.com'] as const;",
        'export const Navigation = createNavigation({',
        "  screens: {}, root: 'Home',",
        "  linking: { prefixes, routes: { 'user/:id': 'Profile', '': 'Home' } },",
        '});',
        '',
      ].join('\n'),
    });
    const linking = findConfig(ts, path.join(root, 'Navigation.ts'), 'linking');
    if (!linking) throw new Error('no linking');
    const prefixes = propertyValue(ts, linking, 'prefixes');
    const routes = propertyValue(ts, linking, 'routes');
    expect(prefixes && stringsOf(ts, prefixes)).toEqual(['myapp://', 'https://example.com']);
    expect(routes && keysOf(ts, routes)).toEqual(['user/:id', '']);
    expect(findConfig(ts, path.join(root, 'Navigation.ts'), 'appProvider')).toBeUndefined();
  });

  it('tells whether the module exports a type', () => {
    const root = app({
      'Navigation.ts':
        'export type AppContext = { user: string };\ninterface Hidden {}\nexport type { Hidden };\n',
    });
    const file = path.join(root, 'Navigation.ts');
    expect(exportsType(ts, file, 'AppContext')).toBe(true);
    expect(exportsType(ts, file, 'Hidden')).toBe(true);
    expect(exportsType(ts, file, 'User')).toBe(false);
  });
});

describe('a screen added to the screens', () => {
  it('goes on the line of the others, with its import', () => {
    const root = app();
    const { result } = withProfile(root);
    expect(result).toEqual({
      status: 'change',
      next: [
        "import { createNavigation } from '@apexnative/navigation';",
        "import App from './App';",
        "import { ProfileScreen } from './src/screens/Profile';",
        '',
        'export const Navigation = createNavigation({',
        '  screens: { Home: App, Profile: ProfileScreen },',
        "  root: 'Home',",
        '});',
        '',
      ].join('\n'),
    });
  });

  it('goes on a line of its own when the others are', () => {
    const root = app({
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        "import App from './App';",
        '',
        'export const Navigation = createNavigation({',
        '  screens: {',
        '    Home: App,',
        '  },',
        "  root: 'Home',",
        '});',
        '',
      ].join('\n'),
    });
    const { result } = withProfile(root);
    expect(result.status === 'change' && result.next).toContain(
      '  screens: {\n    Home: App,\n    Profile: ProfileScreen,\n  },'
    );
  });

  it('goes into the file the screens are imported from', () => {
    const root = app({
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        "import { screens } from './screens';",
        '',
        "export const Navigation = createNavigation({ screens, root: 'Home' });",
        '',
      ].join('\n'),
      'screens.ts': [
        "import App from './App';",
        '',
        'export const screens = { Home: App };',
        '',
      ].join('\n'),
    });
    const { file, result } = withProfile(root);
    expect(file).toBe(path.join(root, 'screens.ts'));
    expect(result.status === 'change' && result.next).toBe(
      [
        "import App from './App';",
        "import { ProfileScreen } from './src/screens/Profile';",
        '',
        'export const screens = { Home: App, Profile: ProfileScreen };',
        '',
      ].join('\n')
    );
  });

  it('breaks a line that would be too long, one screen a line', () => {
    const root = app({
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        '',
        'export const Navigation = createNavigation({',
        '  screens: { Home: HomeScreen, Settings: SettingsScreen, Details: DetailsScreen, Login: LoginScreen },',
        "  root: 'Home',",
        '});',
        '',
      ].join('\n'),
    });
    const { result } = withProfile(root);
    expect(result.status === 'change' && result.next).toContain(
      [
        '  screens: {',
        '    Home: HomeScreen,',
        '    Settings: SettingsScreen,',
        '    Details: DetailsScreen,',
        '    Login: LoginScreen,',
        '    Profile: ProfileScreen,',
        '  },',
      ].join('\n')
    );
  });

  it('is there already when the screens have the name', () => {
    const root = app({
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        "import { ProfileScreen } from './src/screens/Profile';",
        '',
        "export const Navigation = createNavigation({ screens: { Profile: ProfileScreen }, root: 'Profile' });",
        '',
      ].join('\n'),
    });
    expect(withProfile(root).result).toEqual({ status: 'done' });
  });
});

describe('the settings of the CLI', () => {
  it('start empty, with screens in src/screens when the app has src', () => {
    const root = app();
    expect(readConfig(root)).toEqual({});
    expect(screensDir(root, {})).toBe('screens');
    fs.mkdirSync(path.join(root, 'src'));
    expect(screensDir(root, {})).toBe('src/screens');
    expect(screensDir(root, { screensDir: 'app/screens' })).toBe('app/screens');
  });

  it('are kept in package.json, with its indentation', () => {
    const root = app();
    const change = configChange(root, (config) => ({ ...config, screensDir: 'app/screens' }));
    expect(change.status).toBe('change');
    expect(JSON.parse(change.next ?? '').apexNavigation).toEqual({ screensDir: 'app/screens' });
    expect(change.next).toMatch(/^\{\n {2}"name": "HelloWorld"/);
    fs.writeFileSync(path.join(root, 'package.json'), change.next ?? '');
    expect(configChange(root, (config) => ({ ...config, screensDir: 'app/screens' })).status).toBe(
      'done'
    );
  });
});

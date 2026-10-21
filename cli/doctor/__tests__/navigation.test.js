// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { navigationChecks } = require('../checks/navigation');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const TEMPLATE = path.join(__dirname, '..', '..', 'setup', '__fixtures__', 'template-0.87');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });

/**
 * The app after setup with the template's Info.plist and manifest, and files written over it.
 * @param {Record<string, string>} files
 * @param {{ typescript?: boolean }} [options] `typescript: false` takes its tsconfig.json away
 */
function app(files, { typescript = true } = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-checks-'));
  fs.cpSync(FIXTURE, root, { recursive: true });
  const put = (/** @type {string} */ file, /** @type {string} */ text) => {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), text);
  };
  put('ios/HelloWorld/Info.plist', fs.readFileSync(path.join(TEMPLATE, 'Info.plist'), 'utf8'));
  put(
    'android/app/src/main/AndroidManifest.xml',
    fs.readFileSync(path.join(TEMPLATE, 'AndroidManifest.xml'), 'utf8')
  );
  put('node_modules/react-native/package.json', '{ "version": "0.87.1" }');
  fs.symlinkSync(
    path.dirname(require.resolve('typescript/package.json')),
    path.join(root, 'node_modules', 'typescript')
  );
  if (!typescript) fs.rmSync(path.join(root, 'tsconfig.json'));
  for (const [file, text] of Object.entries(files)) put(file, text);
  return root;
}

/** @param {string} root */
const checks = (root) =>
  navigationChecks({ root, run: noCli, env: {}, platform: 'darwin', home: os.tmpdir() });

const SCREEN = (/** @type {string} */ name) =>
  `import { defineScreen } from '@apexnative/navigation';\nfunction ${name}() { return null; }\nexport const ${name}Screen = defineScreen({ component: ${name} });\n`;

describe('the Navigation group of doctor', () => {
  it('counts the screens and finds a screen file nothing imports', () => {
    const root = app({
      'src/screens/Profile.tsx': SCREEN('Profile'),
      'src/screens/Settings.tsx': SCREEN('Settings'),
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        "import App from './App';",
        "import { ProfileScreen } from './src/screens/Profile';",
        "export const Navigation = createNavigation({ screens: { Home: App, Profile: ProfileScreen }, root: 'Home' });",
        '',
      ].join('\n'),
    });
    expect(checks(root)[0]).toMatchObject({
      title: 'Screens',
      status: 'warn',
      detail: `not in screens: ${path.join('src', 'screens', 'Settings.tsx')}`,
    });
  });

  it('finds a name given twice in the screens and a group', () => {
    const root = app({
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        "import App from './App';",
        'export const Navigation = createNavigation({',
        '  screens: { Home: App },',
        '  groups: { signedOut: { screens: { Home: App } } },',
        "  root: 'Home',",
        '});',
        '',
      ].join('\n'),
    });
    expect(checks(root)[0]).toMatchObject({
      status: 'error',
      detail: 'named twice: Home in screens and in the group signedOut',
    });
  });

  it('finds links of linking not declared on the native side, with links as the fix', () => {
    const root = app({
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        "import App from './App';",
        'export const Navigation = createNavigation({',
        "  screens: { Home: App }, root: 'Home',",
        "  linking: { prefixes: ['myapp://'], routes: { '': 'Home' } },",
        '});',
        '',
      ].join('\n'),
    });
    expect(checks(root).find(({ title }) => title === 'Links')).toMatchObject({
      status: 'warn',
      detail: 'not declared on the native side: Info.plist, AndroidManifest.xml',
      fix: { run: 'links' },
    });
  });

  it('tells declarations with no prefix in linking', () => {
    const plist = fs
      .readFileSync(path.join(TEMPLATE, 'Info.plist'), 'utf8')
      .replace(
        '</dict>\n</plist>',
        '\t<key>CFBundleURLTypes</key>\n\t<array>\n\t\t<dict>\n\t\t\t<key>CFBundleURLSchemes</key>\n\t\t\t<array>\n\t\t\t\t<string>fb123</string>\n\t\t\t</array>\n\t\t</dict>\n\t</array>\n</dict>\n</plist>'
      );
    const root = app({
      'ios/HelloWorld/Info.plist': plist,
      'Navigation.ts': [
        "import { createNavigation } from '@apexnative/navigation';",
        "import App from './App';",
        "export const Navigation = createNavigation({ screens: { Home: App }, root: 'Home', linking: { prefixes: ['myapp://'], routes: {} } });",
        '',
      ].join('\n'),
    });
    expect(checks(root)).toContainEqual(
      expect.objectContaining({
        title: 'Links',
        status: 'info',
        detail: 'declared with no prefix in linking: fb123://',
      })
    );
  });

  it('finds screen names that are no screens in a JavaScript app', () => {
    const root = app(
      {
        'src/Home.js':
          "export function open(navigation, errors) {\n  navigation.push('Profle');\n  navigation.push('Home');\n  errors.push('Required');\n}\n",
      },
      { typescript: false }
    );
    expect(checks(root).find(({ title }) => title === 'Screen names')).toMatchObject({
      status: 'warn',
      detail: `not screens: Profle at ${path.join('src', 'Home.js')}:2`,
    });
  });
});

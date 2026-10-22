// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const ts = require('typescript');
const { planFiles } = require('..');

const REPOSITORY = path.join(__dirname, '..', '..', '..');
const TEMPLATE = path.join(REPOSITORY, 'cli', 'setup', '__fixtures__', 'template-0.88');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });

/** React Native's template as its init makes it, with a tsconfig.json. */
function template() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-templates-'));
  const copy = (/** @type {string} */ name, /** @type {string} */ to) => {
    fs.mkdirSync(path.dirname(path.join(root, to)), { recursive: true });
    fs.copyFileSync(path.join(TEMPLATE, name), path.join(root, to));
  };
  copy('MainApplication.kt', 'android/app/src/main/java/com/myapp/MainApplication.kt');
  copy('MainActivity.kt', 'android/app/src/main/java/com/myapp/MainActivity.kt');
  copy('AppDelegate.swift', 'ios/MyApp/AppDelegate.swift');
  copy('SceneDelegate.swift', 'ios/MyApp/SceneDelegate.swift');
  copy('Info.plist', 'ios/MyApp/Info.plist');
  copy('index.js', 'index.js');
  copy('App.tsx', 'App.tsx');
  fs.writeFileSync(path.join(root, 'tsconfig.json'), '{}\n');
  fs.writeFileSync(
    path.join(root, 'package.json'),
    JSON.stringify({ name: 'MyApp', dependencies: { 'react-native': '0.88.0' } }) + '\n'
  );
  fs.mkdirSync(path.join(root, 'node_modules'));
  // The packages the starter's code imports, as the app installs them.
  for (const name of ['typescript', 'react', 'react-native', '@types', '@react-native']) {
    fs.symlinkSync(
      path.join(REPOSITORY, 'node_modules', name),
      path.join(root, 'node_modules', name)
    );
  }
  return root;
}

/**
 * The starter of a template written into the app.
 * @param {'stack' | 'tabs' | 'auth'} name
 */
function write(name) {
  const root = template();
  const changes = planFiles(root, noCli, name);
  for (const change of changes) {
    if (!change.file || change.next === undefined) continue;
    fs.mkdirSync(path.dirname(change.file), { recursive: true });
    fs.writeFileSync(change.file, change.next);
  }
  const names = changes
    .filter(({ status }) => status === 'create')
    .map(({ file }) => path.relative(root, file ?? ''));
  return { root, names };
}

/**
 * The type errors of the starter's code, the navigation from its sources.
 * @param {string} root
 */
function typeErrors(root) {
  const files = [
    'Navigation.ts',
    'navigation-env.d.ts',
    ...fs
      .readdirSync(path.join(root, 'src', 'screens'))
      .map((name) => path.join('src', 'screens', name)),
  ];
  if (fs.existsSync(path.join(root, 'src', 'session.ts')))
    files.push(path.join('src', 'session.ts'));
  const program = ts.createProgram(
    files.map((file) => path.join(root, file)),
    {
      strict: true,
      noEmit: true,
      skipLibCheck: true,
      jsx: ts.JsxEmit.ReactNative,
      module: ts.ModuleKind.ESNext,
      moduleResolution: ts.ModuleResolutionKind.Bundler,
      target: ts.ScriptTarget.ESNext,
      customConditions: ['react-native', 'react-native-legacy-deep-imports'],
      types: [],
      paths: { '@apexnative/navigation': [path.join(REPOSITORY, 'src', 'index.ts')] },
    }
  );
  return ts
    .getPreEmitDiagnostics(program)
    .filter(({ file }) => file?.fileName.startsWith(root))
    .map(
      ({ file, messageText }) =>
        `${path.relative(root, file?.fileName ?? '')}: ${ts.flattenDiagnosticMessageText(messageText, '\n')}`
    );
}

describe('the starters of init', () => {
  it('stack: Home and Details', () => {
    const { names } = write('stack');
    expect(names).toEqual([
      'navigation-env.d.ts',
      'Navigation.ts',
      path.join('src', 'screens', 'Home.tsx'),
      path.join('src', 'screens', 'Details.tsx'),
    ]);
  });

  it('tabs: Home, Profile and Settings, with their icons for Android, typed', () => {
    const { root, names } = write('tabs');
    expect(names).toEqual(
      expect.arrayContaining([
        path.join('src', 'screens', 'Profile.tsx'),
        path.join('src', 'screens', 'Settings.tsx'),
        path.join('android', 'app', 'src', 'main', 'res', 'drawable', 'ic_person.xml'),
      ])
    );
    expect(fs.readFileSync(path.join(root, 'Navigation.ts'), 'utf8')).toContain(
      "Tab('Home').label('Home').icon({ ios: 'house', android: 'ic_home' }),"
    );
    expect(
      fs.readFileSync(
        path.join(root, 'android', 'app', 'src', 'main', 'res', 'drawable', 'ic_settings.xml'),
        'utf8'
      )
    ).toContain('<!-- Material Symbols, Apache License 2.0 -->');
    expect(typeErrors(root)).toEqual([]);
  });

  it('auth: Login, then the tabs, the user in the context, typed', () => {
    const { root, names } = write('auth');
    expect(names).toEqual(
      expect.arrayContaining([
        path.join('src', 'session.ts'),
        path.join('src', 'screens', 'Login.tsx'),
      ])
    );
    expect(fs.readFileSync(path.join(root, 'navigation-env.d.ts'), 'utf8')).toContain(
      "context: import('./Navigation').AppContext;"
    );
    expect(typeErrors(root)).toEqual([]);
  });
});

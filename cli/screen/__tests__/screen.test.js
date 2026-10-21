// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { screen } = require('..');
const { createUI } = require('../../ui');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });

/** A copy of the app after setup, with React Native and TypeScript where the CLI looks for them. */
function app() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-screen-'));
  fs.cpSync(FIXTURE, root, { recursive: true });
  fs.mkdirSync(path.join(root, 'node_modules', 'react-native'), { recursive: true });
  fs.writeFileSync(
    path.join(root, 'node_modules', 'react-native', 'package.json'),
    '{ "version": "0.87.1" }'
  );
  fs.symlinkSync(
    path.dirname(require.resolve('typescript/package.json')),
    path.join(root, 'node_modules', 'typescript')
  );
  return root;
}

/** Runs the command in the app, printing JSON. */
async function run(
  /** @type {string} */ root,
  /** @type {Partial<Parameters<typeof screen>[0]>} */ options
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
    const code = await screen({
      root,
      name: 'Profile',
      yes: true,
      dryRun: false,
      json: true,
      ui,
      run: noCli,
      ...options,
    });
    return { code, result: JSON.parse(text) };
  } finally {
    process.stdout.write = original;
  }
}

describe('screen', () => {
  it('adds a React screen and its line in screens', async () => {
    const root = app();
    const { code, result } = await run(root, {});
    expect(code).toBe(0);
    expect(result).toMatchObject({ command: 'screen', ok: true, applied: true });
    expect(
      result.files.map((/** @type {{ file: string; status: string }} */ file) => [
        file.file,
        file.status,
      ])
    ).toEqual([
      [path.join('screens', 'Profile.tsx'), 'create'],
      ['Navigation.ts', 'change'],
    ]);
    const screenFile = fs.readFileSync(path.join(root, 'screens', 'Profile.tsx'), 'utf8');
    expect(screenFile).toContain('export interface ProfileProps {');
    expect(screenFile).toContain('export const ProfileScreen = defineScreen({');
    // The text is seen on a dark screen too.
    expect(screenFile).toContain("? PlatformColor('label')");
    const navigation = fs.readFileSync(path.join(root, 'Navigation.ts'), 'utf8');
    expect(navigation).toContain("import { ProfileScreen } from './screens/Profile';");
    expect(navigation).toContain('screens: { Home: App, Profile: ProfileScreen },');
  });

  it('puts it where --dir says, and keeps the folder for the next screens', async () => {
    const root = app();
    await run(root, { dir: 'app/screens' });
    expect(fs.existsSync(path.join(root, 'app', 'screens', 'Profile.tsx'))).toBe(true);
    expect(
      JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).apexNavigation
    ).toEqual({
      screensDir: 'app/screens',
    });
    await run(root, { name: 'Settings' });
    expect(fs.existsSync(path.join(root, 'app', 'screens', 'Settings.tsx'))).toBe(true);
  });

  it('has nothing to do the second time', async () => {
    const root = app();
    await run(root, {});
    const { code, result } = await run(root, {});
    expect(code).toBe(0);
    expect(result.files.map((/** @type {{ status: string }} */ file) => file.status)).toEqual([
      'done',
      'done',
    ]);
  });

  it('takes a name in PascalCase only', async () => {
    const { code, result } = await run(app(), { name: 'profile-screen' });
    expect(code).toBe(1);
    expect(result.errors[0].code).toBe('screen-name-invalid');
  });
});

describe('screen of a native screen', () => {
  /** A copy of the app whose types resolve to the navigation's source, as codegen reads them. */
  function nativeApp() {
    const root = app();
    fs.writeFileSync(
      path.join(root, 'tsconfig.json'),
      JSON.stringify({
        compilerOptions: {
          strict: true,
          jsx: 'react-jsx',
          module: 'esnext',
          moduleResolution: 'bundler',
          skipLibCheck: true,
          paths: {
            '@apexnative/navigation': [path.join(__dirname, '..', '..', '..', 'src', 'index.ts')],
          },
        },
      })
    );
    return root;
  }

  it('adds SwiftUI and Compose with --native: the files, Compose, the settings and the generated code', async () => {
    const root = nativeApp();
    const { code, result } = await run(root, { native: true });
    expect(code).toBe(0);
    expect(
      result.files.map((/** @type {{ name: string; status: string }} */ file) => [
        file.name,
        file.status,
      ])
    ).toEqual([
      ['Profile.ts', 'create'],
      ['Navigation.ts', 'change'],
      ['ProfileScreen.swift', 'create'],
      ['project.pbxproj', 'change'],
      ['ProfileScreen.kt', 'create'],
      [path.join('android', 'build.gradle'), 'change'],
      [path.join('android', 'app', 'build.gradle'), 'change'],
      ['package.json', 'change'],
      ['ApexNavigation.generated.swift', 'create'],
      ['ApexNavigationGenerated.kt', 'create'],
    ]);
    const generatedSwift = fs.readFileSync(
      path.join(root, 'ios', 'HelloWorld', 'ApexNavigation.generated.swift'),
      'utf8'
    );
    expect(generatedSwift).toContain('struct ProfileProps {');
    expect(generatedSwift).toContain('ApexNavigation.registerNativeScreen("Profile")');
    const project = fs.readFileSync(
      path.join(root, 'ios', 'HelloWorld.xcodeproj', 'project.pbxproj'),
      'utf8'
    );
    expect(project).toContain('path = HelloWorld/Screens/ProfileScreen.swift;');
    expect(project).toContain('path = HelloWorld/ApexNavigation.generated.swift;');
    expect(fs.readFileSync(path.join(root, 'android', 'app', 'build.gradle'), 'utf8')).toContain(
      'apply plugin: "org.jetbrains.kotlin.plugin.compose"'
    );
    expect(
      JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).apexNavigation
    ).toEqual({
      nativeScreens: { Profile: { ios: 'swiftui', android: 'compose' } },
    });
    expect(
      fs.readFileSync(path.join(root, 'screens', 'ProfileScreen.ts'.replace('Screen', '')), 'utf8')
    ).toContain("export const ProfileScreen = nativeScreen<ProfileProps>('Profile', {");
  });

  it('adds only the platform it names, of that kind', async () => {
    const root = nativeApp();
    await run(root, { ios: 'uikit' });
    expect(
      fs.existsSync(path.join(root, 'ios', 'HelloWorld', 'Screens', 'ProfileViewController.swift'))
    ).toBe(true);
    expect(
      fs.existsSync(
        path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'helloworld', 'screens')
      )
    ).toBe(false);
    expect(
      fs.readFileSync(
        path.join(root, 'ios', 'HelloWorld', 'ApexNavigation.generated.swift'),
        'utf8'
      )
    ).toContain('ProfileViewController(screen: NativeScreen(context, props: ProfileProps.init))');
    expect(fs.readFileSync(path.join(root, 'android', 'build.gradle'), 'utf8')).not.toContain(
      'compose'
    );
  });

  it('has nothing to do the second time', async () => {
    const root = nativeApp();
    await run(root, { native: true });
    const { result } = await run(root, { native: true });
    expect(
      result.files.every((/** @type {{ status: string }} */ file) => file.status === 'done')
    ).toBe(true);
  });

  it('takes the kinds it knows', async () => {
    const { code, result } = await run(nativeApp(), { ios: 'storyboard' });
    expect(code).toBe(1);
    expect(result.errors[0]).toEqual({
      code: 'screen-kind-invalid',
      message: '--ios is swiftui or uikit, not storyboard',
    });
  });
});

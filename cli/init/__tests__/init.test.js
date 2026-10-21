// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { init, resolveVersion } = require('..');
const { createUI } = require('../../ui');

const TEMPLATE = path.join(__dirname, '..', '..', 'setup', '__fixtures__', 'template-0.87');
const VERSION = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', '..', '..', 'package.json'), 'utf8')
).version;

/**
 * React Native's template in the folder, as its init makes it.
 * @param {string} root
 */
function makeTemplate(root) {
  const copy = (/** @type {string} */ name, /** @type {string} */ to) => {
    fs.mkdirSync(path.dirname(path.join(root, to)), { recursive: true });
    fs.copyFileSync(path.join(TEMPLATE, name), path.join(root, to));
  };
  copy('MainApplication.kt', 'android/app/src/main/java/com/myapp/MainApplication.kt');
  copy('MainActivity.kt', 'android/app/src/main/java/com/myapp/MainActivity.kt');
  copy('styles.xml', 'android/app/src/main/res/values/styles.xml');
  copy('AppDelegate.swift', 'ios/MyApp/AppDelegate.swift');
  copy('Info.plist', 'ios/MyApp/Info.plist');
  copy('index.js', 'index.js');
  copy('App.tsx', 'App.tsx');
  fs.mkdirSync(path.join(root, '__tests__'));
  fs.writeFileSync(
    path.join(root, '__tests__', 'App.test.tsx'),
    "import App from '../App';\n\ntest('renders correctly', () => {});\n"
  );
  fs.writeFileSync(path.join(root, 'Gemfile'), "source 'https://rubygems.org'\n");
  fs.writeFileSync(path.join(root, 'tsconfig.json'), '{}\n');
  fs.writeFileSync(
    path.join(root, 'package.json'),
    JSON.stringify(
      { name: 'MyApp', dependencies: { react: '19.2.0', 'react-native': '0.87.10' } },
      null,
      2
    ) + '\n'
  );
}

/**
 * A folder to make the app in, and the programs init runs: npm says the versions, React Native's init makes the
 * template, the rest succeeds; the machine's checks find nothing installed.
 * @param {{ fail?: string }} [options] `fail`: the program, with its first argument, that fails
 */
function machine({ fail } = {}) {
  const cwd = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-init-'));
  /** @type {{ command: string; cwd?: string }[]} */
  const calls = [];
  /** @type {import('../../exec').Exec} */
  const run = (program, args, options = {}) => {
    const command = [program, ...args].join(' ');
    calls.push({ command, cwd: options.cwd });
    if (fail && command.startsWith(fail)) return { ok: false, stdout: '', stderr: 'it failed' };
    if (program === 'npm' && args[0] === 'view') {
      // A range has several versions, a version itself.
      const spec = args[1].slice('react-native@'.length);
      const stdout = spec.startsWith('~')
        ? '["0.87.0", "0.87.10", "0.87.2"]'
        : JSON.stringify(spec);
      return { ok: true, stdout, stderr: '' };
    }
    if (program === 'npx' && args.includes('init')) {
      const directory = args.indexOf('--directory');
      makeTemplate(path.join(cwd, directory >= 0 ? args[directory + 1] : 'MyApp'));
      return { ok: true, stdout: '', stderr: '' };
    }
    if (['npm', 'yarn', 'bun', 'bundle'].includes(program))
      return { ok: true, stdout: '', stderr: '' };
    return { ok: false, stdout: '', stderr: '' };
  };
  return { cwd, run, calls };
}

/**
 * Runs init with --json --yes on the machine, and gives its exit code and its document.
 * @param {ReturnType<typeof machine>} on
 * @param {Partial<Parameters<typeof init>[0]>} [options]
 */
async function initJson(on, options = {}) {
  let output = '';
  const code = await init({
    cwd: on.cwd,
    name: 'MyApp',
    yes: true,
    dryRun: false,
    json: true,
    run: on.run,
    env: {},
    platform: 'darwin',
    home: on.cwd,
    ui: createUI({ out: { write: () => true }, level: 'none' }),
    write: (text) => (output += text),
    ...options,
  });
  return { code, result: JSON.parse(output) };
}

describe('init', () => {
  it('makes the app with the navigation set up, Home and Details in place of App, installed once', async () => {
    const on = machine();
    const { code, result } = await initJson(on);
    const root = path.join(on.cwd, 'MyApp');
    expect(code).toBe(0);
    expect(result).toMatchObject({ command: 'init', ok: true, applied: true, errors: [] });
    expect(result).toMatchObject({ reactNative: '0.87.10', packageManager: 'npm' });

    expect(on.calls.map(({ command }) => command)).toEqual(
      expect.arrayContaining([
        'npm view react-native@~0.87.0 version --json',
        'npx --yes --package=@react-native-community/cli@latest -- rnc-cli init MyApp --version 0.87.10 --pm npm --skip-install --install-pods false',
        'npm install',
        'bundle install',
        'bundle exec pod install',
      ])
    );
    expect(on.calls.find(({ command }) => command === 'npm install')?.cwd).toBe(root);
    expect(on.calls.find(({ command }) => command === 'bundle exec pod install')?.cwd).toBe(
      path.join(root, 'ios')
    );

    const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
    expect(Object.entries(pkg.dependencies)).toEqual([
      ['@apexnative/navigation', `^${VERSION}`],
      ['react', '19.2.0'],
      ['react-native', '0.87.10'],
    ]);
    expect(fs.readFileSync(path.join(root, 'index.js'), 'utf8')).toContain(
      "import { Navigation } from './Navigation';"
    );
    expect(fs.readFileSync(path.join(root, 'Navigation.ts'), 'utf8')).toContain(
      'screens: { Home: HomeScreen, Details: DetailsScreen },'
    );
    // The types of the commands are in navigation-env.d.ts, not in Navigation.ts.
    expect(fs.readFileSync(path.join(root, 'Navigation.ts'), 'utf8')).not.toContain('Register');
    expect(fs.readFileSync(path.join(root, 'navigation-env.d.ts'), 'utf8')).toContain(
      "app: typeof import('./Navigation').Navigation;"
    );
    expect(fs.readFileSync(path.join(root, 'src', 'screens', 'Home.tsx'), 'utf8')).toContain(
      "navigation.push('Details', { props: { title: 'Details' } })"
    );
    expect(fs.existsSync(path.join(root, 'src', 'screens', 'Details.tsx'))).toBe(true);
    expect(fs.existsSync(path.join(root, 'App.tsx'))).toBe(false);
    expect(
      result.files.map((/** @type {{ name: string; status: string }} */ file) => [
        file.name,
        file.status,
      ])
    ).toEqual([
      ['package.json', 'change'],
      ['MainApplication.kt', 'change'],
      ['MainActivity.kt', 'change'],
      ['styles.xml', 'change'],
      ['AppDelegate.swift', 'change'],
      ['Info.plist', 'change'],
      ['index.js', 'change'],
      ['navigation-env.d.ts', 'create'],
      ['Navigation.ts', 'create'],
      ['Home.tsx', 'create'],
      ['Details.tsx', 'create'],
      ['App.tsx', 'remove'],
      ['App.test.tsx', 'remove'],
    ]);
    expect(fs.existsSync(path.join(root, '__tests__', 'App.test.tsx'))).toBe(false);
    expect(result.next).toEqual(['cd MyApp', 'npm run ios · npm run android']);
    // The checks of the machine for Android and iOS: the template here has no Gradle or Podfile to look at.
    expect(result.checks.map((/** @type {{ group: string }} */ check) => check.group)).toEqual([
      'android',
      'ios',
    ]);
  });

  it('takes the package manager it was run with, npm for one React Native has no init for', async () => {
    const yarn = machine();
    const { result } = await initJson(yarn, {
      env: { npm_config_user_agent: 'yarn/4.9.0 npm/? node/v22.14.0 darwin arm64' },
    });
    expect(result.packageManager).toBe('yarn');
    expect(yarn.calls.map(({ command }) => command)).toContain('yarn install');
    expect(result.next).toContain('yarn ios · yarn android');

    const pnpm = machine();
    const other = await initJson(pnpm, {
      env: { npm_config_user_agent: 'pnpm/10.0.0 npm/? node/v22.14.0 darwin arm64' },
    });
    expect(other.result.packageManager).toBe('npm');
    expect(other.result.warnings).toEqual([
      "React Native's init doesn't know pnpm: the app uses npm (--pm yarn or bun picks another).",
    ]);
  });

  it('leaves the install and the pods for later', async () => {
    const on = machine();
    const { code, result } = await initJson(on, { skipInstall: true });
    expect(code).toBe(0);
    expect(on.calls.map(({ command }) => command)).not.toContain('npm install');
    expect(result.next).toEqual([
      'cd MyApp',
      'npm install',
      'cd ios && bundle install && bundle exec pod install && cd ..',
      'npm run ios · npm run android',
    ]);
  });

  it('says what failed, the app made all the same', async () => {
    const on = machine({ fail: 'npm install' });
    const { code, result } = await initJson(on);
    expect(code).toBe(1);
    expect(result.applied).toBe(true);
    expect(result.errors).toEqual([
      { code: 'install-failed', message: 'npm install failed:\nit failed' },
    ]);
    expect(on.calls.map(({ command }) => command)).not.toContain('bundle exec pod install');
  });

  it('warns of a React Native newer than the one the navigation was checked with', async () => {
    const on = machine();
    const { result } = await initJson(on, { version: '0.88.0' });
    expect(result.warnings).toEqual([
      'React Native 0.88.0 is newer than 0.87, the one the navigation was checked with: setup may leave files to change by hand.',
    ]);
  });

  it.each([
    [{ name: 'my-app' }, 'init-name-invalid'],
    [{ pm: 'pnpm' }, 'init-pm-invalid'],
    [{ dryRun: true }, 'init-dry-run'],
    [{ yes: false }, 'init-needs-yes'],
  ])('stops before it makes anything: %j', async (options, error) => {
    const on = machine();
    const { code, result } = await initJson(on, options);
    expect(code).toBe(1);
    expect(result.errors.map((/** @type {{ code: string }} */ item) => item.code)).toEqual([error]);
    expect(on.calls).toEqual([]);
  });

  it("doesn't make an app in a folder that has files", async () => {
    const on = machine();
    fs.mkdirSync(path.join(on.cwd, 'MyApp'));
    fs.writeFileSync(path.join(on.cwd, 'MyApp', 'notes.txt'), '');
    const { result } = await initJson(on);
    expect(result.errors[0].code).toBe('init-directory-exists');
  });
});

describe('the version of React Native', () => {
  it('is the newest one npm has of the range, by its numbers', () => {
    const on = machine();
    expect(resolveVersion(on.run, '~0.87.0')).toBe('0.87.10');
  });

  it("is unknown when npm can't say", () => {
    const on = machine({ fail: 'npm view' });
    expect(resolveVersion(on.run, '~0.87.0')).toBeUndefined();
  });
});

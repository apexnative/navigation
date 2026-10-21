// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const ts = require('typescript');
const { planCodegen } = require('..');
const { openWorkspace } = require('../../workspace');
const { createProgram, readNativeScreens } = require('../read');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const LIBRARY = path.join(__dirname, '..', '..', '..', 'src', 'index.ts');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });

const PROFILE = `import { nativeScreen } from '@apexnative/navigation';

export interface ProfileProps {
  /** The id of the user. @int */
  id: number;
  name: string;
  role?: 'admin' | 'member';
  tags: string[];
}

export const ProfileScreen = nativeScreen<ProfileProps>('Profile', {
  useProps: ({ id }) => ({
    user: { name: \`User \${id}\`, avatar: '' },
    save: (name: string) => Promise.resolve(name.length > 0),
    close: () => {},
  }),
});
`;

/** A copy of the app after setup with a native screen, its types resolving to the navigation's source. */
function app(/** @type {Record<string, string>} */ files = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-codegen-'));
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
  fs.writeFileSync(
    path.join(root, 'tsconfig.json'),
    JSON.stringify({
      compilerOptions: {
        strict: true,
        jsx: 'react-jsx',
        module: 'esnext',
        moduleResolution: 'bundler',
        skipLibCheck: true,
        paths: { '@apexnative/navigation': [LIBRARY] },
      },
    })
  );
  fs.mkdirSync(path.join(root, 'screens'));
  fs.writeFileSync(path.join(root, 'screens', 'Profile.ts'), PROFILE);
  fs.writeFileSync(
    path.join(root, 'Navigation.ts'),
    [
      "import { createNavigation } from '@apexnative/navigation';",
      "import App from './App';",
      "import { ProfileScreen } from './screens/Profile';",
      '',
      "export const Navigation = createNavigation({ screens: { Home: App, Profile: ProfileScreen }, root: 'Home' });",
      '',
    ].join('\n')
  );
  for (const [name, text] of Object.entries(files)) fs.writeFileSync(path.join(root, name), text);
  return root;
}

/** @param {string} root */
function workspace(root) {
  const opened = openWorkspace(root, noCli);
  if ('code' in opened) throw new Error(opened.message);
  return opened;
}

/** @param {string} root */
function read(root) {
  const { navigation } = workspace(root);
  return readNativeScreens(ts, createProgram(ts, root, navigation), root);
}

describe('the native screens read from TypeScript', () => {
  it('have the props of the command with what useProps returns', () => {
    const { screens, errors } = read(app());
    expect(errors).toEqual([]);
    expect(screens).toHaveLength(1);
    const [profile] = screens;
    expect(profile.nativeName).toBe('Profile');
    expect(profile.file).toBe(path.join('screens', 'Profile.ts'));
    expect(
      profile.fields.map(({ name, type, optional }) => ({ name, kind: type.kind, optional }))
    ).toEqual([
      { name: 'id', kind: 'number', optional: false },
      { name: 'name', kind: 'string', optional: false },
      { name: 'role', kind: 'enum', optional: true },
      { name: 'tags', kind: 'array', optional: false },
      { name: 'user', kind: 'object', optional: false },
      { name: 'save', kind: 'function', optional: false },
      { name: 'close', kind: 'function', optional: false },
    ]);
    expect(profile.fields[0]).toMatchObject({
      type: { kind: 'number', int: true },
      doc: 'The id of the user.',
    });
    expect(profile.fields[5].type).toEqual({
      kind: 'function',
      parameters: [{ name: 'name', type: { kind: 'string' } }],
      result: { kind: 'boolean' },
    });
    expect(profile.enums).toEqual([{ name: 'Role', values: ['admin', 'member'] }]);
    expect(profile.objects).toEqual([
      {
        name: 'User',
        fields: [
          { name: 'name', type: { kind: 'string' }, optional: false },
          { name: 'avatar', type: { kind: 'string' }, optional: false },
        ],
      },
    ]);
  });

  it('point at the line of a prop with no native type', () => {
    const root = app();
    fs.writeFileSync(
      path.join(root, 'screens', 'Profile.ts'),
      [
        "import { nativeScreen } from '@apexnative/navigation';",
        '',
        'export const ProfileScreen = nativeScreen<{',
        '  born: Date;',
        '  data: any;',
        '  nested: { onTap: () => void };',
        "}>('Profile');",
        '',
      ].join('\n')
    );
    expect(read(root).errors).toEqual([
      {
        file: path.join('screens', 'Profile.ts'),
        line: 4,
        message: 'born: Date has no plain-data form; pass a string or numbers',
      },
      {
        file: path.join('screens', 'Profile.ts'),
        line: 5,
        message: 'data: any and unknown have no native type; give the prop a type',
      },
      {
        file: path.join('screens', 'Profile.ts'),
        line: 6,
        message: 'onTap: only the props themselves can be functions, not what is in them',
      },
    ]);
  });
});

describe('the generated files', () => {
  it('are made next to AppDelegate.swift, in the Xcode project, and in the package the navigation finds', () => {
    const root = app();
    const { changes, screens, errors } = planCodegen(root, workspace(root));
    expect(errors).toEqual([]);
    expect(screens).toEqual(['Profile']);
    expect(changes.map(({ file, status }) => [path.relative(root, file ?? ''), status])).toEqual([
      [path.join('ios', 'HelloWorld', 'ApexNavigation.generated.swift'), 'create'],
      [path.join('ios', 'HelloWorld.xcodeproj', 'project.pbxproj'), 'change'],
      [
        path.join(
          'android',
          'app',
          'src',
          'main',
          'java',
          'dev',
          'apexnative',
          'navigation',
          'generated',
          'ApexNavigationGenerated.kt'
        ),
        'create',
      ],
    ]);
    const [swift, , kotlin] = changes;
    expect(swift.next).toContain('struct ProfileProps {');
    expect(swift.next).toContain('  enum Role: String, CaseIterable {');
    expect(swift.next).toContain('  let id: Int');
    expect(swift.next).toContain('  let role: Role?');
    expect(swift.next).toContain('  let save: (String) async throws -> Bool');
    expect(swift.next).toContain(
      '    save = { name in try await decodeBool(context.call("save", arguments: [name])) }'
    );
    expect(kotlin.next).toContain('package dev.apexnative.navigation.generated');
    expect(kotlin.next).toContain('data class ProfileProps(');
    expect(kotlin.next).toContain('  val save: suspend (String) -> Boolean,');
    expect(kotlin.next).toContain('  enum class Role(val value: String) {');
    // Without a kind in the settings, a screen has its types but isn't registered.
    expect(swift.next).toContain('// None: `npx apex-navigation screen <Name> --native` adds one.');
  });

  it('register the screens the settings say how to make', () => {
    const root = app();
    const json = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
    json.apexNavigation = { nativeScreens: { Profile: { ios: 'swiftui', android: 'compose' } } };
    fs.writeFileSync(path.join(root, 'package.json'), JSON.stringify(json));
    const [swift, , kotlin] = planCodegen(root, workspace(root)).changes;
    expect(swift.next).toContain(
      '      MainActor.assumeIsolated { UIHostingController(rootView: ProfileScreen(screen: NativeScreen(context, props: ProfileProps.init))) }'
    );
    expect(kotlin.next).toContain(
      '      ComposeView(activity).apply { setContent { com.helloworld.screens.ProfileScreen(NativeScreen(context, ::ProfileProps)) } }'
    );
  });

  it('are up to date the second time', () => {
    const root = app();
    for (const { file, next } of planCodegen(root, workspace(root)).changes) {
      if (!file || !next) continue;
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, next);
    }
    expect(planCodegen(root, workspace(root)).changes.map(({ status }) => status)).toEqual([
      'done',
      'done',
      'done',
    ]);
  });
});

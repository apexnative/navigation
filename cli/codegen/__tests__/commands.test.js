// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const ts = require('typescript');
const { planCodegen } = require('..');
const { createProgram, readCommandScreens } = require('../read');
const { openWorkspace } = require('../../workspace');
const { generatedCheck } = require('../../doctor/checks/app');
const { screen } = require('../../screen');
const { createUI } = require('../../ui');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const LIBRARY = path.join(__dirname, '..', '..', '..', 'src', 'index.ts');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });

const FILES = {
  'screens/Profile.ts': `import { nativeScreen } from '@apexnative/navigation';

export interface ProfileProps {
  /** @int */
  id: number;
  role?: 'admin' | 'member';
}

export const ProfileScreen = nativeScreen<ProfileProps>('Profile', {
  useProps: () => ({ save: (name: string) => Promise.resolve(name.length > 0) }),
});
`,
  'screens/Details.tsx': `import { defineScreen } from '@apexnative/navigation';

export interface DetailsProps {
  title?: string;
  address: { city: string; zip?: string };
  tab?: 'general' | 'privacy';
  tags: string[];
  onDone?: () => void;
}

function Details(_props: DetailsProps) {
  return null;
}

export const DetailsScreen = defineScreen({ component: Details });
`,
  'screens/Login.tsx': `export function Login(_props: { onDone: () => void }) {
  return null;
}
`,
  'Navigation.ts': `import { createNavigation } from '@apexnative/navigation';
import App from './App';
import { DetailsScreen } from './screens/Details';
import { Login } from './screens/Login';
import { ProfileScreen } from './screens/Profile';

export const Navigation = createNavigation({
  screens: { Home: App, Details: DetailsScreen, Profile: ProfileScreen, Login },
  root: 'Home',
});
`,
};

/** A copy of the app after setup with a native screen and React screens with props, typed from the sources. */
function app() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-commands-'));
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
  for (const [file, text] of Object.entries(FILES)) {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), text);
  }
  const workspace = openWorkspace(root, noCli);
  if ('code' in workspace) throw new Error(workspace.message);
  return { root, workspace };
}

describe('the typed commands of native code', () => {
  it('keep apart what Swift and Kotlin would take for their own names', () => {
    const { swiftCommands, kotlinCommands } = require('../commands');
    /** @type {import('../read').CommandScreenModel[]} */
    const screens = [
      {
        name: 'Map',
        native: false,
        fields: [
          { name: 'name', type: { kind: 'string' }, optional: false },
          { name: 'type', type: { kind: 'enum', name: 'TypeValue' }, optional: true },
          { name: 'map', type: { kind: 'object', name: 'Map' }, optional: true },
        ],
        functions: [],
        objects: [
          {
            name: 'Map',
            fields: [{ name: 'zoom', type: { kind: 'number', int: true }, optional: false }],
          },
        ],
        enums: [{ name: 'TypeValue', values: ['roads', 'satellite'] }],
      },
      { name: '2FA', native: false, fields: [], functions: [], objects: [], enums: [] },
    ];
    const swift = swiftCommands(screens);
    // The type named as the screen gets a suffix, so Screens.Map.Map doesn't hide the namespace.
    expect(swift).toContain(
      '  case map(name: Swift.String, type: Screens.Map.TypeValue? = nil, map: Screens.Map.MapValue? = nil)'
    );
    expect(swift).toContain('    struct MapValue {');
    expect(swift).toContain('  case _2FA');
    const kotlin = kotlinCommands(screens);
    expect(kotlin).toContain('  data class Map(\n    val name: kotlin.String,');
    expect(kotlin).toContain('    val map: MapValue? = null,');
    expect(kotlin).toContain('  object _2FA : Screens()');
    expect(kotlin).not.toMatch(/[^.]\bMap</);
  });

  it('read every screen with the props a command gives it', () => {
    const { root, workspace } = app();
    const { screens, skipped } = readCommandScreens(
      ts,
      createProgram(ts, root, workspace.navigation),
      workspace.navigation
    );
    expect(
      screens.map(({ name, native, fields, functions }) => ({
        name,
        native,
        fields: fields.map(
          (field) => `${field.name}${field.optional ? '?' : ''}: ${field.type.kind}`
        ),
        functions,
      }))
    ).toEqual([
      { name: 'Home', native: false, fields: [], functions: [] },
      {
        name: 'Details',
        native: false,
        fields: ['title?: string', 'address: object', 'tab?: enum', 'tags: array'],
        functions: ['onDone'],
      },
      { name: 'Profile', native: true, fields: ['id: number', 'role?: enum'], functions: [] },
    ]);
    expect(skipped).toEqual([
      {
        name: 'Login',
        native: false,
        reason: "its prop onDone is a function, which native code can't give",
      },
    ]);
  });

  it('leave out a screen whose names in Swift and Kotlin another has, and rename a type Swift reserves', () => {
    const { root } = app();
    fs.writeFileSync(
      path.join(root, 'screens', 'Login.tsx'),
      "export function Login(_props: { type: 'email' | 'phone' }) {\n  return null;\n}\n"
    );
    fs.writeFileSync(
      path.join(root, 'Navigation.ts'),
      FILES['Navigation.ts'].replace(
        'ProfileScreen, Login }',
        'ProfileScreen, Login, login: Login }'
      )
    );
    const opened = openWorkspace(root, noCli);
    if ('code' in opened) throw new Error(opened.message);
    const { screens, skipped } = readCommandScreens(
      ts,
      createProgram(ts, root, opened.navigation),
      opened.navigation
    );
    expect(screens.find(({ name }) => name === 'Login')?.enums).toEqual([
      { name: 'TypeValue', values: ['email', 'phone'] },
    ]);
    expect(skipped).toEqual([
      { name: 'login', native: false, reason: 'its name in Swift and Kotlin is that of Login' },
    ]);
  });

  it('are generated in Swift and Kotlin, and the screens left out are warnings', () => {
    const { root, workspace } = app();
    const { changes, skipped } = planCodegen(root, workspace);
    const text = (/** @type {string} */ name) =>
      changes.find((change) => change.name === name)?.next ?? '';
    const swift = text('ApexNavigation.generated.swift');
    expect(swift).toContain(
      '  case details(title: Swift.String? = nil, address: Screens.Details.Address, tab: Screens.Details.Tab? = nil, tags: [Swift.String])'
    );
    expect(swift).toContain('  case profile(id: Swift.Int, role: Screens.Profile.Role? = nil)');
    expect(swift).toContain(
      '    case let .details(title, address, tab, tags): return presentFields(["title": title, "address": address.json, "tab": tab.map { $0.rawValue }, "tags": tags])'
    );
    expect(swift).toContain('  func push(_ screen: Screens) async throws -> Swift.String {');
    const kotlin = text('ApexNavigationGenerated.kt');
    expect(kotlin).toContain('  object Home : Screens()');
    expect(kotlin).toContain(
      '  data class Profile(\n    val id: kotlin.Int,\n    val role: Role? = null,\n  ) : Screens() {'
    );
    expect(kotlin).toContain(
      '    is Screens.Profile -> presentFields("id" to id, "role" to role?.let { it.value })'
    );
    expect(kotlin).toContain(
      'suspend fun ScreenNavigation.showModal(screen: Screens): kotlin.String =\n  showModal(screen.jsName, screen.jsProps)'
    );
    expect(skipped).toEqual([
      "Login has no typed command, native code opens it by its name: its prop onDone is a function, which native code can't give",
    ]);
  });

  it('follow a React screen added by the CLI, and doctor warns when only they are behind', async () => {
    const { root, workspace } = app();
    for (const change of planCodegen(root, workspace).changes) {
      if (!change.file || change.next === undefined) continue;
      fs.mkdirSync(path.dirname(change.file), { recursive: true });
      fs.writeFileSync(change.file, change.next);
    }
    expect(generatedCheck(root, noCli)).toMatchObject({ status: 'ok' });

    // A React screen with props, added by hand: only the typed commands are behind.
    fs.writeFileSync(
      path.join(root, 'screens', 'Login.tsx'),
      'export function Login(_props: { email?: string }) {\n  return null;\n}\n'
    );
    expect(generatedCheck(root, noCli)).toMatchObject({
      status: 'warn',
      fix: { run: 'codegen' },
    });

    let text = '';
    const original = process.stdout.write;
    process.stdout.write = /** @type {any} */ (
      (/** @type {string} */ chunk) => {
        text += chunk;
        return true;
      }
    );
    try {
      await screen({
        root,
        name: 'Settings',
        yes: true,
        dryRun: false,
        json: true,
        ui: createUI({ out: { isTTY: false, columns: 80, write: () => true }, level: 'none' }),
        run: noCli,
      });
    } finally {
      process.stdout.write = original;
    }
    const names = JSON.parse(text).files.map(
      (/** @type {any} */ file) => `${file.name}:${file.status}`
    );
    expect(names).toEqual(expect.arrayContaining(['ApexNavigation.generated.swift:change']));
    expect(
      fs.readFileSync(
        path.join(root, 'ios', 'HelloWorld', 'ApexNavigation.generated.swift'),
        'utf8'
      )
    ).toContain('  case settings');
  });
});

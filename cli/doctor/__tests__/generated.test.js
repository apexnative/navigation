// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { planCodegen } = require('../../codegen');
const { generatedCheck } = require('../checks/app');
const { openWorkspace } = require('../../workspace');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const LIBRARY = path.join(__dirname, '..', '..', '..', 'src', 'index.ts');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });

/**
 * A copy of the app after setup whose types resolve to the navigation's source; `native` adds a native screen.
 * @param {{ native: boolean }} options
 */
function app({ native }) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-doctor-'));
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
        skipLibCheck: true,
        paths: { '@apexnative/navigation': [LIBRARY] },
      },
    })
  );
  if (native) {
    fs.writeFileSync(
      path.join(root, 'Profile.ts'),
      "import { nativeScreen } from '@apexnative/navigation';\n\nexport const ProfileScreen = nativeScreen<{ name: string }>('Profile');\n"
    );
    fs.writeFileSync(
      path.join(root, 'Navigation.ts'),
      [
        "import { createNavigation } from '@apexnative/navigation';",
        "import { ProfileScreen } from './Profile';",
        '',
        "export const Navigation = createNavigation({ screens: { Profile: ProfileScreen }, root: 'Profile' });",
        '',
      ].join('\n')
    );
  }
  return root;
}

describe('the check of the generated code of native screens', () => {
  it('has nothing to say in an app without native screens', () => {
    expect(generatedCheck(app({ native: false }), noCli)).toBeUndefined();
  });

  it('has nothing to say in an app that registers its native screens by hand', () => {
    expect(generatedCheck(app({ native: true }), noCli)).toBeUndefined();
  });

  it('finds a native screen with no generated code, and codegen fixes it', () => {
    const root = app({ native: true });
    // The screen was made by `screen`: the settings have its kinds.
    const json = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
    json.apexNavigation = { nativeScreens: { Profile: { ios: 'swiftui' } } };
    fs.writeFileSync(path.join(root, 'package.json'), JSON.stringify(json));
    expect(generatedCheck(root, noCli)).toMatchObject({
      status: 'error',
      detail:
        'the generated code is older than their props: ApexNavigation.generated.swift, project.pbxproj, ApexNavigationGenerated.kt',
      fix: { label: 'npx apex-navigation codegen', run: 'codegen' },
    });
    const opened = openWorkspace(root, noCli);
    if ('code' in opened) throw new Error(opened.message);
    for (const { file, next } of planCodegen(root, opened).changes) {
      if (!file || next === undefined) continue;
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, next);
    }
    expect(generatedCheck(root, noCli)).toMatchObject({
      status: 'ok',
      detail: '1 · generated code up to date',
    });
  });
});

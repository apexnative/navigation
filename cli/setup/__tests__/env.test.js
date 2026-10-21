// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const ts = require('typescript');
const { ENV_FILE, addToArray, planEnv, takes } = require('../steps/env');

/** An app root with a tsconfig.json, and the navigation module it points to. */
function app(/** @type {string | null} */ tsconfig = '{}\n') {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-env-'));
  if (tsconfig !== null) fs.writeFileSync(path.join(root, 'tsconfig.json'), tsconfig);
  const navigation = {
    file: path.join(root, 'Navigation.ts'),
    exported: 'Navigation',
    context: false,
  };
  return { root, navigation };
}

const TEXT = `// Made by apex-navigation setup: the types of the app's screens
// for the navigation's commands. Don't edit.
declare module '@apexnative/navigation' {
  interface Register {
    app: typeof import('./Navigation').Navigation;
  }
}
export {};
`;

describe('navigation-env.d.ts', () => {
  it('is made in an app with a tsconfig.json', () => {
    const { root, navigation } = app();
    expect(planEnv(root, navigation, ts)).toEqual([
      expect.objectContaining({ name: ENV_FILE, status: 'create', next: TEXT }),
    ]);
  });

  it("isn't made in a JavaScript app", () => {
    const { root, navigation } = app(null);
    expect(planEnv(root, navigation, ts)).toEqual([]);
  });

  it('registers the context when the navigation exports AppContext', () => {
    const { root, navigation } = app();
    const [change] = planEnv(root, { ...navigation, context: true }, ts);
    expect(change.next).toContain("    context: import('./Navigation').AppContext;\n");
  });

  it('points to a navigation module elsewhere, and to its default export', () => {
    const { root } = app();
    const file = path.join(root, 'src', 'navigation.tsx');
    const [change] = planEnv(root, { file, exported: 'default', context: false }, ts);
    expect(change.next).toContain("app: typeof import('./src/navigation').default;");
  });

  it('is done when it is so already, and made again when it is ours but out of date', () => {
    const { root, navigation } = app();
    fs.writeFileSync(path.join(root, ENV_FILE), TEXT);
    expect(planEnv(root, navigation, ts)[0].status).toBe('done');
    const [change] = planEnv(root, { ...navigation, context: true }, ts);
    expect(change).toMatchObject({ status: 'change', note: 'the navigation and its context' });
  });

  it('is left to the app when it wrote the file itself', () => {
    const { root, navigation } = app();
    fs.writeFileSync(path.join(root, ENV_FILE), 'declare module "x";\n');
    expect(planEnv(root, navigation, ts)[0]).toMatchObject({ status: 'manual' });
  });

  it("goes into the include of tsconfig.json when it doesn't take it", () => {
    const { root, navigation } = app('{\n  "include": ["src"]\n}\n');
    const [, include] = planEnv(root, navigation, ts);
    expect(include).toMatchObject({
      name: 'tsconfig.json',
      status: 'change',
      next: '{\n  "include": ["src", "navigation-env.d.ts"]\n}\n',
    });
  });

  it('reads a tsconfig.json with comments through TypeScript', () => {
    const { root, navigation } = app('{\n  // The app.\n  "include": ["**/*.ts"],\n}\n');
    expect(planEnv(root, navigation, ts)).toHaveLength(1);
  });

  it('is left to the app when tsconfig.json lists its files', () => {
    const { root, navigation } = app('{ "files": ["index.ts"] }\n');
    expect(planEnv(root, navigation, ts)[1]).toMatchObject({
      status: 'manual',
      note: 'lists its files',
    });
  });
});

describe('a pattern of include', () => {
  it.each([
    ['**/*.ts', true],
    ['*.ts', true],
    ['./**/*', true],
    ['src', false],
    ['src/**/*.ts', false],
    ['navigation-env.d.ts', true],
    ['.', true],
    ['*.tsx', false],
  ])('%s takes the file: %s', (pattern, expected) => {
    expect(takes(pattern, ENV_FILE)).toBe(expected);
  });
});

describe('an item added to a JSON array', () => {
  it('goes on its line, or on a line of its own', () => {
    expect(addToArray('{ "include": [] }', 'include', 'x')).toBe('{ "include": ["x"] }');
    expect(
      addToArray('{\n  "include": [\n    "src",\n    "index.ts"\n  ]\n}', 'include', 'x')
    ).toBe('{\n  "include": [\n    "src",\n    "index.ts",\n    "x"\n  ]\n}');
  });
});

// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createRun } = require('../changes');
const { unifiedDiff } = require('../diff');
const { createUI } = require('../ui');

function capture() {
  let text = '';
  const out = {
    isTTY: false,
    columns: 80,
    write: (/** @type {string} */ chunk) => (text += chunk),
  };
  return { ui: createUI({ out, level: 'none' }), out, output: () => text };
}

/** An app folder with one file, and the changes to it and to a new one. */
function app() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-changes-'));
  const existing = path.join(root, 'App.tsx');
  fs.writeFileSync(existing, 'one\ntwo\nthree\n');
  /** @type {import('../changes').FileChange[]} */
  const changes = [
    {
      name: 'App.tsx',
      platform: 'js',
      file: existing,
      status: 'change',
      note: 'one line',
      before: 'one\ntwo\nthree\n',
      next: 'one\n2\nthree\n',
    },
    {
      name: 'Profile.tsx',
      platform: 'js',
      file: path.join(root, 'src', 'screens', 'Profile.tsx'),
      status: 'create',
      note: 'new file',
      next: 'export {};\n',
    },
  ];
  return { root, existing, changes };
}

describe('a unified diff', () => {
  it('numbers the lines of both texts, as git does', () => {
    expect(unifiedDiff('App.tsx', 'one\ntwo\nthree\n', 'one\n2\nthree\n')).toBe(
      '--- a/App.tsx\n+++ b/App.tsx\n@@ -1,3 +1,3 @@\n one\n-two\n+2\n three\n'
    );
  });

  it('adds a new file whole', () => {
    expect(unifiedDiff('New.ts', '', 'a\nb\n')).toBe(
      '--- /dev/null\n+++ b/New.ts\n@@ -0,0 +1,2 @@\n+a\n+b\n'
    );
  });

  it('takes a removed file away whole', () => {
    expect(unifiedDiff('Old.ts', 'a\nb\n', '')).toBe(
      '--- a/Old.ts\n+++ /dev/null\n@@ -1,2 +0,0 @@\n-a\n-b\n'
    );
  });

  it('is empty when nothing changed', () => {
    expect(unifiedDiff('Same.ts', 'a\n', 'a\n')).toBe('');
  });
});

describe('the run of a command', () => {
  it('shows a dry run and writes nothing', async () => {
    const { root, existing, changes } = app();
    const { ui, output } = capture();
    const code = await createRun({ command: 'test', root, ui }).apply(changes, {
      yes: false,
      dryRun: true,
    });
    expect(code).toBe(0);
    expect(fs.readFileSync(existing, 'utf8')).toBe('one\ntwo\nthree\n');
    expect(output()).toContain('Dry run: nothing written.');
  });

  it('writes with --yes, folders of new files too', async () => {
    const { root, existing, changes } = app();
    const { ui } = capture();
    const code = await createRun({ command: 'test', root, ui }).apply(changes, {
      yes: true,
      dryRun: false,
    });
    expect(code).toBe(0);
    expect(fs.readFileSync(existing, 'utf8')).toBe('one\n2\nthree\n');
    expect(fs.readFileSync(path.join(root, 'src', 'screens', 'Profile.tsx'), 'utf8')).toBe(
      'export {};\n'
    );
  });

  it('removes a file, with its diff in the result', async () => {
    const { root, existing } = app();
    const { ui, out, output } = capture();
    await createRun({ command: 'test', root, json: true, ui, out }).apply(
      [
        {
          name: 'App.tsx',
          platform: 'js',
          file: existing,
          status: 'remove',
          note: 'removed',
          before: 'one\ntwo\nthree\n',
        },
      ],
      { yes: true, dryRun: false }
    );
    expect(fs.existsSync(existing)).toBe(false);
    expect(JSON.parse(output()).files[0]).toMatchObject({
      status: 'remove',
      diff: '--- a/App.tsx\n+++ /dev/null\n@@ -1,3 +0,0 @@\n-one\n-two\n-three\n',
    });
  });

  it('prints one JSON document and writes nothing without --yes', async () => {
    const { root, existing, changes } = app();
    const { ui, out, output } = capture();
    await createRun({ command: 'test', root, json: true, ui, out }).apply(changes, {
      yes: false,
      dryRun: false,
    });
    const result = JSON.parse(output());
    expect(result).toMatchObject({ command: 'test', ok: true, applied: false, errors: [] });
    expect(result.warnings).toEqual(['Nothing written: run with --yes to apply the changes.']);
    expect(result.files.map((/** @type {{ file: string }} */ file) => file.file)).toEqual([
      'App.tsx',
      path.join('src', 'screens', 'Profile.tsx'),
    ]);
    expect(result.files[1].diff).toContain('+++ b/src/screens/Profile.tsx');
    expect(fs.readFileSync(existing, 'utf8')).toBe('one\ntwo\nthree\n');
  });

  it('applies with --json --yes, and says what to run next', async () => {
    const { root, changes } = app();
    const { ui, out, output } = capture();
    await createRun({ command: 'test', root, json: true, ui, out }).apply(changes, {
      yes: true,
      dryRun: false,
      next: ['cd ios && pod install'],
    });
    expect(JSON.parse(output())).toMatchObject({ applied: true, next: ['cd ios && pod install'] });
  });

  it('ends with 1 when a file is left to change by hand', async () => {
    const { root } = app();
    const { ui, out, output } = capture();
    const code = await createRun({ command: 'test', root, json: true, ui, out }).apply(
      [
        {
          name: 'AppDelegate.swift',
          platform: 'ios',
          status: 'manual',
          note: 'changed by hand',
          howTo: 'Do it.',
        },
      ],
      { yes: true, dryRun: false }
    );
    expect(code).toBe(1);
    expect(JSON.parse(output())).toMatchObject({
      ok: false,
      files: [{ status: 'manual', howTo: 'Do it.' }],
    });
  });

  it('runs what follows the writing before it ends, which may add to the result', async () => {
    const { root, existing, changes } = app();
    const { ui, out, output } = capture();
    /** @type {string[]} */
    const seen = [];
    const code = await createRun({ command: 'test', root, json: true, ui, out }).apply(changes, {
      yes: true,
      dryRun: false,
      next: ['npx react-native run-ios'],
      afterWrite(result) {
        seen.push(fs.readFileSync(existing, 'utf8'));
        result.ok = false;
        result.errors.push({ code: 'pods-failed', message: 'pod install failed' });
        result.next.unshift('cd ios && pod install && cd ..');
      },
    });
    expect(seen).toEqual(['one\n2\nthree\n']);
    expect(code).toBe(1);
    expect(JSON.parse(output())).toMatchObject({
      ok: false,
      applied: true,
      errors: [{ code: 'pods-failed' }],
      next: ['cd ios && pod install && cd ..', 'npx react-native run-ios'],
    });
  });

  it("doesn't run what follows the writing on a dry run", async () => {
    const { root, changes } = app();
    const { ui } = capture();
    const afterWrite = jest.fn();
    await createRun({ command: 'test', root, ui }).apply(changes, {
      yes: false,
      dryRun: true,
      afterWrite,
    });
    expect(afterWrite).not.toHaveBeenCalled();
  });

  it('stops with an error code tools can tell apart', () => {
    const { ui, out, output } = capture();
    const code = createRun({ command: 'test', root: os.tmpdir(), json: true, ui, out }).fail(
      'react-native-missing',
      "React Native isn't installed here."
    );
    expect(code).toBe(1);
    expect(JSON.parse(output()).errors).toEqual([
      { code: 'react-native-missing', message: "React Native isn't installed here." },
    ]);
  });
});

// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { links, linksFiles, linksOpen } = require('..');
const { components, toFingerprint } = require('../files');
const { planEntitlements, planIntentFilters, planUrlTypes } = require('../native');
const { parsePrefix } = require('../prefixes');
const { createUI } = require('../../ui');

const FIXTURE = path.join(__dirname, '..', '..', '__fixtures__', 'app-0.87');
const TEMPLATE = path.join(__dirname, '..', '..', 'setup', '__fixtures__', 'template-0.87');
const MANIFEST = fs.readFileSync(path.join(TEMPLATE, 'AndroidManifest.xml'), 'utf8');
const PLIST = fs.readFileSync(path.join(TEMPLATE, 'Info.plist'), 'utf8');
const noCli = () => ({ ok: false, stdout: '', stderr: '' });

const NAVIGATION = `import { createNavigation } from '@apexnative/navigation';
import App from './App';

export const Navigation = createNavigation({
  screens: { Home: App },
  root: 'Home',
  linking: {
    prefixes: ['myapp://', 'https://example.com'],
    routes: { 'user/:userId': 'Home', 'settings/:tab?': 'Home', '': 'Home' },
  },
});
`;

/** The app after setup with its Info.plist, its manifest, and the links of `navigation`. */
function app(navigation = NAVIGATION) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-links-'));
  fs.cpSync(FIXTURE, root, { recursive: true });
  const put = (/** @type {string} */ file, /** @type {string} */ text) => {
    fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
    fs.writeFileSync(path.join(root, file), text);
  };
  put('ios/HelloWorld/Info.plist', PLIST);
  put('android/app/src/main/AndroidManifest.xml', MANIFEST);
  put('node_modules/react-native/package.json', '{ "version": "0.87.1" }');
  put('Navigation.ts', navigation);
  fs.symlinkSync(
    path.dirname(require.resolve('typescript/package.json')),
    path.join(root, 'node_modules', 'typescript')
  );
  return root;
}

/**
 * Runs a links command with --json and returns its exit code and result.
 * @param {(ui: ReturnType<typeof createUI>, write: (text: string) => void) => Promise<number>} command
 */
async function json(command) {
  let text = '';
  const out = {
    isTTY: false,
    columns: 80,
    write: (/** @type {string} */ chunk) => (text += chunk),
  };
  const original = process.stdout.write;
  process.stdout.write = /** @type {any} */ (
    (/** @type {string} */ chunk) => {
      text += chunk;
      return true;
    }
  );
  try {
    const code = await command(createUI({ out, level: 'none' }), (chunk) => (text += chunk));
    return { code, result: JSON.parse(text) };
  } finally {
    process.stdout.write = original;
  }
}

describe('the prefixes', () => {
  it('are schemes of the app or web sites, with the start of their paths', () => {
    expect(parsePrefix('myapp://')).toMatchObject({ scheme: 'myapp', host: '', web: false });
    expect(parsePrefix('https://Example.com/app/')).toMatchObject({
      scheme: 'https',
      host: 'example.com',
      path: '/app',
      web: true,
    });
    expect(parsePrefix('not a prefix')).toBeUndefined();
  });
});

describe('the native declarations', () => {
  it('add the schemes to Info.plist, in a new entry for those it misses', () => {
    const first = planUrlTypes(PLIST, ['myapp']);
    if (first.status !== 'change') throw new Error(first.status);
    expect(first.next).toContain(
      '\t<key>CFBundleURLTypes</key>\n\t<array>\n\t\t<dict>\n\t\t\t<key>CFBundleURLName</key>\n\t\t\t<string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>\n\t\t\t<key>CFBundleURLSchemes</key>\n\t\t\t<array>\n\t\t\t\t<string>myapp</string>\n\t\t\t</array>\n\t\t</dict>\n\t</array>\n</dict>'
    );
    expect(planUrlTypes(first.next, ['myapp']).status).toBe('done');
    const second = planUrlTypes(first.next, ['myapp', 'other']);
    if (second.status !== 'change') throw new Error(second.status);
    expect(second.next.match(/<key>CFBundleURLSchemes<\/key>/g)).toHaveLength(2);
    expect(second.note).toBe('schemes other');
  });

  it('add the sites to the entitlements, a new file or the one there is', () => {
    const made = planEntitlements(undefined, ['example.com']);
    if (made.status !== 'change') throw new Error(made.status);
    expect(made.next).toContain('<string>applinks:example.com</string>');
    expect(planEntitlements(made.next, ['example.com']).status).toBe('done');
    const more = planEntitlements(made.next, ['example.com', 'www.example.com']);
    expect(more).toMatchObject({ status: 'change', note: 'applinks:www.example.com' });
  });

  it('add a filter per scheme and one verified filter for the sites to the main activity', () => {
    const result = planIntentFilters(MANIFEST, {
      schemes: ['myapp'],
      web: [{ scheme: 'https', host: 'example.com' }],
    });
    if (result.status !== 'change') throw new Error(result.status);
    expect(result.singleTask).toBe(true);
    expect(result.next).toContain('<data android:scheme="myapp" />');
    expect(result.next).toContain(
      '<intent-filter android:autoVerify="true">\n            <action android:name="android.intent.action.VIEW" />'
    );
    expect(
      planIntentFilters(result.next, {
        schemes: ['myapp'],
        web: [{ scheme: 'https', host: 'example.com' }],
      }).status
    ).toBe('done');
    const another = planIntentFilters(result.next, {
      schemes: [],
      web: [{ scheme: 'https', host: 'www.example.com' }],
    });
    if (another.status !== 'change') throw new Error(another.status);
    // Into the verified filter there is, not a second one.
    expect(another.next.match(/autoVerify/g)).toHaveLength(1);
    expect(another.next).toContain('<data android:host="www.example.com" />');
  });

  it('fill a list written empty, and take a site once for http and https', () => {
    const empty = PLIST.replace(
      '</dict>\n</plist>',
      '\t<key>CFBundleURLTypes</key>\n\t<array/>\n</dict>\n</plist>'
    );
    const result = planUrlTypes(empty, ['myapp']);
    if (result.status !== 'change') throw new Error(result.status);
    expect(result.next.match(/<key>CFBundleURLTypes<\/key>/g)).toHaveLength(1);
    expect(result.next).toContain('<string>myapp</string>');
    const sites = planIntentFilters(MANIFEST, {
      schemes: [],
      web: [
        { scheme: 'https', host: 'example.com' },
        { scheme: 'http', host: 'example.com' },
      ],
    });
    if (sites.status !== 'change') throw new Error(sites.status);
    expect(sites.next.match(/android:host="example.com"/g)).toHaveLength(1);
  });

  it("tell an activity that isn't singleTask", () => {
    const standard = MANIFEST.replace(
      'android:launchMode="singleTask"',
      'android:launchMode="standard"'
    );
    expect(planIntentFilters(standard, { schemes: ['myapp'], web: [] }).singleTask).toBe(false);
  });
});

describe('links', () => {
  it('declares the links of linking on both platforms, and nothing the second time', async () => {
    const root = app();
    const run = () =>
      json((ui) => links({ root, yes: true, dryRun: false, json: true, ui, run: noCli }));
    const { code, result } = await run();
    expect(code).toBe(0);
    expect(result.files.map((/** @type {any} */ file) => `${file.name}:${file.status}`)).toEqual([
      'Info.plist:change',
      'HelloWorld.entitlements:create',
      'project.pbxproj:change',
      'AndroidManifest.xml:change',
    ]);
    const again = await run();
    expect(again.result.files.map((/** @type {any} */ file) => file.status)).toEqual([
      'done',
      'done',
      'done',
      'done',
    ]);
  });

  it('warns of a prefix with a path', async () => {
    const root = app(NAVIGATION.replace("'https://example.com'", "'https://example.com/app'"));
    const { result } = await json((ui) =>
      links({ root, yes: false, dryRun: true, json: true, ui, run: noCli })
    );
    expect(result.warnings[0]).toContain(
      'https://example.com/app: Android opens every page of example.com'
    );
  });

  it('fails without linking, and with prefixes it cannot read', async () => {
    const none = await json((ui) =>
      links({
        root: app(NAVIGATION.replace(/ {2}linking: \{[\s\S]*?\n {2}\},\n/, '')),
        yes: true,
        dryRun: false,
        json: true,
        ui,
        run: noCli,
      })
    );
    expect(none).toMatchObject({ code: 1, result: { errors: [{ code: 'no-linking' }] } });
    const computed = await json((ui) =>
      links({
        root: app(
          NAVIGATION.replace(
            "prefixes: ['myapp://', 'https://example.com']",
            'prefixes: [`${scheme}://`]'
          )
        ),
        yes: true,
        dryRun: false,
        json: true,
        ui,
        run: noCli,
      })
    );
    expect(computed.result.errors[0].code).toBe('prefixes-unreadable');
  });
});

describe('links files', () => {
  /** A machine whose keytool reads the debug key. */
  const keytool = (/** @type {string} */ command) =>
    path.basename(command) === 'keytool'
      ? { ok: true, stdout: `\t SHA256: ${'AB:'.repeat(31)}AB\n`, stderr: '' }
      : noCli();

  it('makes the files of the site from the routes and the keys', async () => {
    const root = app();
    fs.writeFileSync(path.join(root, 'android', 'app', 'debug.keystore'), '');
    const { code, result } = await json((ui) =>
      linksFiles({
        root,
        teamId: 'ABCDE12345',
        fingerprints: ['cd'.repeat(32)],
        yes: true,
        dryRun: false,
        json: true,
        ui,
        run: keytool,
        env: {},
        home: os.tmpdir(),
      })
    );
    expect(code).toBe(0);
    const aasa = JSON.parse(
      fs.readFileSync(path.join(root, '.well-known', 'apple-app-site-association'), 'utf8')
    );
    expect(aasa.applinks.details[0]).toEqual({
      appIDs: ['ABCDE12345.org.reactjs.native.example.HelloWorld'],
      components: [{ '/': '/user/*' }, { '/': '/settings/*' }, { '/': '/settings' }, { '/': '/' }],
    });
    const assetlinks = JSON.parse(
      fs.readFileSync(path.join(root, '.well-known', 'assetlinks.json'), 'utf8')
    );
    expect(assetlinks[0].target).toEqual({
      namespace: 'android_app',
      package_name: 'com.helloworld',
      sha256_cert_fingerprints: [`${'AB:'.repeat(31)}AB`, `${'CD:'.repeat(31)}CD`],
    });
    expect(result.next[0]).toBe(
      'Serve .well-known/apple-app-site-association at https://example.com/.well-known/apple-app-site-association'
    );
  });

  it('needs the team of the app, and fingerprints that are fingerprints', async () => {
    const root = app();
    const team = await json((ui) =>
      linksFiles({
        root,
        yes: true,
        dryRun: false,
        json: true,
        ui,
        run: noCli,
        env: {},
        home: os.tmpdir(),
      })
    );
    expect(team.result.errors[0].code).toBe('team-id-missing');
    const wrong = await json((ui) =>
      linksFiles({
        root,
        platform: 'android',
        fingerprints: ['12:34'],
        yes: true,
        dryRun: false,
        json: true,
        ui,
        run: noCli,
        env: {},
        home: os.tmpdir(),
      })
    );
    expect(wrong.result.errors[0].code).toBe('fingerprint-invalid');
  });

  it('take a route parameter as any characters of the path', () => {
    const prefix = /** @type {import('../prefixes').Prefix} */ (
      parsePrefix('https://example.com/app')
    );
    expect(components([prefix], ['chat/:id/messages', ''])).toEqual([
      { '/': '/app/chat/*/messages' },
      { '/': '/app' },
    ]);
    expect(toFingerprint('ab'.repeat(32))).toBe(`${'AB:'.repeat(31)}AB`);
  });
});

describe('links open', () => {
  /**
   * A machine with a booted simulator and the devices adb lists.
   * @param {string[]} devices
   */
  function machine(devices) {
    /** @type {string[]} */
    const calls = [];
    /** @type {import('../../exec').Exec} */
    const run = (command, args) => {
      const line = [path.basename(command), ...args].join(' ');
      calls.push(line);
      if (line.startsWith('xcrun simctl list')) {
        return {
          ok: true,
          stdout: JSON.stringify({ devices: { ios: [{ name: 'iPhone 17' }] } }),
          stderr: '',
        };
      }
      if (line === 'adb devices') {
        return {
          ok: true,
          stdout: `List of devices attached\n${devices.map((serial) => `${serial}\tdevice`).join('\n')}\n`,
          stderr: '',
        };
      }
      return { ok: true, stdout: 'Status: ok\n', stderr: '' };
    };
    return { run, calls };
  }

  it('opens the link on both platforms, cold', async () => {
    const root = app();
    const { run, calls } = machine(['A1']);
    const { code, result } = await json((ui, write) =>
      linksOpen({
        root,
        url: 'myapp://user/42?tab=a&b=c',
        cold: true,
        json: true,
        ui,
        run,
        env: {},
        host: 'darwin',
        write,
      })
    );
    expect(code).toBe(0);
    expect(
      result.opened.map((/** @type {any} */ opened) => [opened.platform, opened.device, opened.ok])
    ).toEqual([
      ['ios', 'iPhone 17', true],
      ['android', 'A1', true],
    ]);
    expect(calls).toEqual(
      expect.arrayContaining([
        'xcrun simctl terminate booted org.reactjs.native.example.HelloWorld',
        'xcrun simctl openurl booted myapp://user/42?tab=a&b=c',
        'adb -s A1 shell am force-stop com.helloworld',
        "adb -s A1 shell am start -W -a android.intent.action.VIEW -d 'myapp://user/42?tab=a&b=c' com.helloworld",
      ])
    );
  });

  it('asks to pick one of several devices', async () => {
    const { run } = machine(['A1', 'B2']);
    const { code, result } = await json((ui, write) =>
      linksOpen({
        root: app(),
        url: 'myapp://',
        platform: 'android',
        cold: false,
        json: true,
        ui,
        run,
        env: {},
        host: 'darwin',
        write,
      })
    );
    expect(code).toBe(1);
    expect(result.errors[0].code).toBe('several-devices');
  });

  it("warns of a link that isn't the app's", async () => {
    const { run } = machine(['A1']);
    const { result } = await json((ui, write) =>
      linksOpen({
        root: app(),
        url: 'other://x',
        platform: 'android',
        cold: false,
        json: true,
        ui,
        run,
        env: {},
        host: 'darwin',
        write,
      })
    );
    expect(result.warnings[0]).toBe(
      'other://x starts with none of the prefixes of linking: the system may open it elsewhere.'
    );
  });
});

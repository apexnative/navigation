// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { isSupported, reactNativeVersion, usesExpo } = require('../app');
const { createRun } = require('../changes');
const { exec } = require('../exec');
const { createUI } = require('../ui');
const { openWorkspace } = require('../workspace');
const { addGroupFile, buildSetting, setBuildSetting } = require('../xcode/pbxproj');
const { appSiteAssociation, assetLinks, debugFingerprint, toFingerprint } = require('./files');
const { applicationId, bundleId, developmentTeam, projectInfoPlist } = require('./identity');
const { planEntitlements, planIntentFilters, planUrlTypes } = require('./native');
const { openOnAndroid, openOnIos } = require('./open');
const { isAppLink, readLinking } = require('./prefixes');

/** @typedef {import('../changes').FileChange} FileChange */
/** @typedef {import('../project').Project} Project */
/** @typedef {import('./prefixes').Linking} Linking */

/**
 * Declares the links of `linking` on the native side: the app's schemes in Info.plist and the manifest, its web sites
 * in the entitlements (universal links) and in a verified filter of the manifest (App Links). It only adds: a prefix
 * taken out of the code leaves its declaration.
 * @param {{
 *   root: string;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 * }} options
 * @returns {Promise<number>} the exit code
 */
async function links({ root, yes, dryRun, json = false, ui = createUI(), run = exec }) {
  const command = createRun({ command: 'links', root, json, ui });
  banner(ui, json, root, 'links');
  const read = readApp(root, run);
  if ('code' in read) return command.fail(read.code, read.message);
  const { project, linking } = read;

  const schemes = unique(linking.prefixes.filter(({ web }) => !web).map(({ scheme }) => scheme));
  const web = uniqueBy(
    linking.prefixes
      .filter(({ web: isWeb }) => isWeb)
      .map(({ scheme, host }) => ({ scheme, host })),
    ({ scheme, host }) => `${scheme}://${host}`
  );
  if (schemes.length === 0 && web.length === 0) {
    return command.fail('no-prefixes', 'The prefixes of linking have no scheme://… to declare.');
  }
  for (const prefix of linking.prefixes.filter(({ web: isWeb, path: start }) => isWeb && start)) {
    command.warn(
      `${prefix.text}: Android opens every page of ${prefix.host} in the app, and the links no route takes go to unmatched; iOS opens only the paths of apple-app-site-association (links files).`
    );
  }

  /** @type {FileChange[]} */
  const changes = [
    ...planIos(project, schemes, unique(web.map(({ host }) => host))),
    ...planAndroid(project, schemes, web, (message) => command.warn(message)),
  ];
  const example = linking.prefixes[0].text.replace(/\/*$/, '/');
  return command.apply(changes, {
    yes,
    dryRun,
    nothingToDo: 'The links are declared.',
    next: [
      'Build the app again: the links are in its native files.',
      `npx apex-navigation links open ${example}…`,
      ...(web.length > 0 ? ['npx apex-navigation links files: the files your site serves'] : []),
    ],
  });
}

/**
 * The iOS files of the links: Info.plist for the schemes; for the sites the entitlements and, when the app target
 * names none yet, the Xcode project.
 * @param {Project} project
 * @param {string[]} schemes
 * @param {string[]} hosts
 * @returns {FileChange[]}
 */
function planIos(project, schemes, hosts) {
  if (!fs.existsSync(path.join(project.root, 'ios'))) return [];
  /** @type {FileChange[]} */
  const changes = [];
  if (!project.infoPlist || !project.xcodeProject) {
    return [
      {
        name: 'Info.plist',
        platform: 'ios',
        status: 'manual',
        note: "the app's Info.plist wasn't found",
        howTo: 'Declare the links in Xcode, as the Deep links guide shows (Declare the links).',
      },
    ];
  }
  if (schemes.length > 0) {
    const before = fs.readFileSync(project.infoPlist, 'utf8');
    changes.push({
      name: 'Info.plist',
      platform: 'ios',
      file: project.infoPlist,
      before,
      ...planUrlTypes(before, schemes),
    });
  }
  if (hosts.length === 0) return changes;

  const sourceRoot = path.dirname(path.dirname(project.xcodeProject));
  const infoPlist = /** @type {string} */ (projectInfoPlist(project));
  const pbxproj = fs.readFileSync(project.xcodeProject, 'utf8');
  const named = buildSetting(pbxproj, infoPlist, 'CODE_SIGN_ENTITLEMENTS').find(Boolean);
  const appFolder = path.dirname(project.infoPlist);
  const file = named
    ? path.join(sourceRoot, named.replace(/^\$\(SRCROOT\)\//, ''))
    : path.join(appFolder, `${path.basename(appFolder)}.entitlements`);
  const existing = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : undefined;
  const entitlements = planEntitlements(existing, hosts);
  changes.push({
    name: path.basename(file),
    platform: 'ios',
    file,
    before: existing ?? '',
    ...entitlements,
    ...(existing === undefined && entitlements.status === 'change' && { status: 'create' }),
  });

  // The target names the file in every configuration, and the project lists it next to Info.plist, as Xcode does.
  const value = named ?? path.relative(sourceRoot, file).split(path.sep).join('/');
  const setting = setBuildSetting(pbxproj, { infoPlist, key: 'CODE_SIGN_ENTITLEMENTS', value });
  const withSetting = setting.status === 'change' ? setting.next : pbxproj;
  const listed =
    setting.status === 'manual'
      ? setting
      : addGroupFile(withSetting, { anchor: 'Info.plist', anchorFile: project.infoPlist, file });
  const howTo = `In Xcode, Signing & Capabilities of the app target: + Capability, Associated Domains, with ${hosts
    .map((host) => `applinks:${host}`)
    .join(', ')}.`;
  if (setting.status === 'manual' || listed.status === 'manual') {
    changes.push({
      name: 'project.pbxproj',
      platform: 'ios',
      file: project.xcodeProject,
      status: 'manual',
      note: listed.status === 'manual' ? listed.reason : 'changed by hand',
      howTo,
    });
  } else if (setting.status === 'change' || listed.status === 'change') {
    changes.push({
      name: 'project.pbxproj',
      platform: 'ios',
      file: project.xcodeProject,
      status: 'change',
      before: pbxproj,
      next: listed.status === 'change' ? listed.next : withSetting,
      note: `CODE_SIGN_ENTITLEMENTS = ${value}`,
    });
  } else {
    changes.push({
      name: 'project.pbxproj',
      platform: 'ios',
      file: project.xcodeProject,
      status: 'done',
      note: `CODE_SIGN_ENTITLEMENTS = ${value}`,
    });
  }
  return changes;
}

/**
 * The manifest with the links on the main activity. A main activity that isn't `singleTask` gets a warning: each link
 * would start another copy of it, not reach the one the user sees.
 * @param {Project} project
 * @param {string[]} schemes
 * @param {{ scheme: string; host: string }[]} web
 * @param {(message: string) => void} warn
 * @returns {FileChange[]}
 */
function planAndroid(project, schemes, web, warn) {
  if (!fs.existsSync(path.join(project.root, 'android'))) return [];
  if (!project.manifest) {
    return [
      {
        name: 'AndroidManifest.xml',
        platform: 'android',
        status: 'manual',
        note: "wasn't found",
        howTo:
          'Declare the links on the main activity, as the Deep links guide shows (Declare the links).',
      },
    ];
  }
  const before = fs.readFileSync(project.manifest, 'utf8');
  const { singleTask, ...plan } = planIntentFilters(before, { schemes, web });
  if (singleTask === false) {
    warn(
      'The main activity of AndroidManifest.xml isn\'t android:launchMode="singleTask": a link would start another copy of it instead of reaching the app on screen.'
    );
  }
  return [
    { name: 'AndroidManifest.xml', platform: 'android', file: project.manifest, before, ...plan },
  ];
}

/**
 * Opens a link as a tap would: on the booted iOS simulator and the connected Android device, or the platform asked.
 * Without a platform, one with no device is left out; the command fails when none opened it.
 * @param {{
 *   root: string;
 *   url: string | undefined;
 *   platform?: string;
 *   cold: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   env?: NodeJS.ProcessEnv;
 *   host?: NodeJS.Platform;
 *   write?: (text: string) => void;
 * }} options `host` is the system the command runs on
 * @returns {Promise<number>} the exit code
 */
async function linksOpen({
  root,
  url,
  platform,
  cold,
  json = false,
  ui = createUI(),
  run = exec,
  env = process.env,
  host = process.platform,
  write = (text) => process.stdout.write(text),
}) {
  /** @type {{ command: string; ok: boolean; url?: string; opened: import('./open').Opened[]; warnings: string[]; errors: { code: string; message: string }[] }} */
  const result = { command: 'links open', ok: true, url, opened: [], warnings: [], errors: [] };
  const finish = () => {
    if (json) write(JSON.stringify(result, null, 2) + '\n');
    return result.ok ? 0 : 1;
  };
  const fail = (/** @type {string} */ code, /** @type {string} */ message) => {
    result.ok = false;
    result.errors.push({ code, message });
    if (!json) ui.error(message);
    return finish();
  };
  if (!url) {
    return fail(
      'url-missing',
      'Give the link to open: npx apex-navigation links open myapp://user/42'
    );
  }
  if (platform !== undefined && platform !== 'ios' && platform !== 'android') {
    return fail('platform-invalid', `--platform is ios or android, not ${platform}.`);
  }
  const read = readApp(root, run);
  if ('code' in read && read.code !== 'no-linking' && read.code !== 'prefixes-unreadable') {
    return fail(read.code, read.message);
  }
  if (!('code' in read) && !isAppLink(url, read.linking.prefixes)) {
    const message = `${url} starts with none of the prefixes of linking: the system may open it elsewhere.`;
    result.warnings.push(message);
    if (!json) ui.warn(message);
  }
  const project = 'code' in read ? undefined : read.project;
  const platforms = platform
    ? [platform]
    : [
        ...(host === 'darwin' && fs.existsSync(path.join(root, 'ios')) ? ['ios'] : []),
        ...(fs.existsSync(path.join(root, 'android')) ? ['android'] : []),
      ];
  for (const each of platforms) {
    const opened =
      each === 'ios'
        ? host === 'darwin'
          ? openOnIos({ url, bundleId: project && bundleId(project, 'Debug'), cold, run })
          : {
              platform: /** @type {const} */ ('ios'),
              ok: false,
              error: { code: 'not-mac', message: 'The iOS simulator runs on a Mac only.' },
            }
        : openOnAndroid({ url, packageName: project && applicationId(project), cold, env, run });
    result.opened.push(opened);
  }
  const done = result.opened.filter(({ ok }) => ok);
  // Asked for one platform, or none opened it: each reason counts. Otherwise a platform without a device is a note.
  const counted = platform || done.length === 0 ? result.opened : [];
  for (const { error } of counted) if (error) result.errors.push(error);
  result.ok = done.length > 0 && result.errors.length === 0;
  if (!json) {
    for (const opened of result.opened) {
      const where = `${opened.platform === 'ios' ? 'iOS' : 'Android'}${opened.device ? `, ${opened.device}` : ''}`;
      if (opened.ok) ui.done(`Opened on ${where}${cold ? ', cold' : ''}`);
      else if (counted.includes(opened)) ui.error(`${where}: ${opened.error?.message}`);
      else ui.muted(`  ${where}: ${opened.error?.message}`);
    }
    if (platforms.length === 0) ui.error('The app has no ios/ or android/ folder here.');
  }
  if (platforms.length === 0) result.ok = false;
  return finish();
}

/**
 * Writes the files the app's web site serves so the systems open its links in the app: apple-app-site-association
 * (iOS, from the team, the bundle id and the routes) and assetlinks.json (Android, from the application id and the
 * fingerprints of the signing keys).
 * @param {{
 *   root: string;
 *   teamId?: string;
 *   fingerprints?: string[];
 *   out?: string;
 *   platform?: string;
 *   yes: boolean;
 *   dryRun: boolean;
 *   json?: boolean;
 *   ui?: ReturnType<typeof createUI>;
 *   run?: import('../exec').Exec;
 *   env?: NodeJS.ProcessEnv;
 *   home?: string;
 * }} options `out`: the folder, from the app's root (`.well-known` by default)
 * @returns {Promise<number>} the exit code
 */
async function linksFiles({
  root,
  teamId,
  fingerprints = [],
  out = '.well-known',
  platform,
  yes,
  dryRun,
  json = false,
  ui = createUI(),
  run = exec,
  env = process.env,
  home = os.homedir(),
}) {
  const command = createRun({ command: 'links files', root, json, ui });
  banner(ui, json, root, 'links files');
  if (platform !== undefined && platform !== 'ios' && platform !== 'android') {
    return command.fail('platform-invalid', `--platform is ios or android, not ${platform}.`);
  }
  const read = readApp(root, run);
  if ('code' in read) return command.fail(read.code, read.message);
  const { project, linking } = read;
  const sites = linking.prefixes.filter(({ scheme }) => scheme === 'https');
  if (sites.length === 0) {
    return command.fail(
      'no-web-prefixes',
      'linking has no https:// prefix: these files are for the links of a web site.'
    );
  }
  const folder = path.resolve(root, out);
  /** @type {FileChange[]} */
  const changes = [];

  if (platform !== 'android') {
    const id = bundleId(project);
    if (!id)
      return command.fail(
        'bundle-id-missing',
        "The bundle id of the iOS app wasn't found in its Xcode project."
      );
    const team = teamId ?? developmentTeam(project);
    if (!team || !/^[A-Z0-9]{10}$/.test(team)) {
      return command.fail(
        'team-id-missing',
        team
          ? `${team} isn't a team id: it is 10 letters and digits, as Membership details of the Apple Developer account show.`
          : 'The Xcode project names no team: give the team id of the Apple Developer account, --team-id ABCDE12345.'
      );
    }
    changes.push(
      fileChange(
        path.join(folder, 'apple-app-site-association'),
        appSiteAssociation({ teamId: team, bundleId: id, prefixes: sites, routes: linking.routes }),
        `${team}.${id}`
      )
    );
  }

  if (platform !== 'ios') {
    const packageName = applicationId(project);
    if (!packageName) {
      return command.fail(
        'application-id-missing',
        "applicationId wasn't found in android/app/build.gradle."
      );
    }
    const given = fingerprints.map((text) => ({ text, fingerprint: toFingerprint(text) }));
    const invalid = given.filter(({ fingerprint }) => !fingerprint).map(({ text }) => text);
    if (invalid.length > 0) {
      return command.fail(
        'fingerprint-invalid',
        invalid.map(
          (text) => `${text} isn't a SHA-256 fingerprint: 32 bytes in hex, such as AB:CD:…`
        )
      );
    }
    const debug = debugFingerprint({ root, home, env, run });
    if (!debug) {
      command.warn(
        'No debug key was read (no keystore, or no JDK): only the --fingerprint keys are in assetlinks.json.'
      );
    }
    const keys = unique([
      ...(debug ? [debug] : []),
      .../** @type {string[]} */ (given.map(({ fingerprint }) => fingerprint)),
    ]);
    if (keys.length === 0) {
      return command.fail(
        'fingerprint-missing',
        'No key to put in assetlinks.json: give --fingerprint, the SHA-256 of the release key.'
      );
    }
    changes.push(
      fileChange(
        path.join(folder, 'assetlinks.json'),
        assetLinks({ packageName, fingerprints: keys }),
        `${packageName}, ${keys.length} ${keys.length === 1 ? 'key' : 'keys'}`
      )
    );
  }

  const hosts = unique(sites.map(({ host }) => host));
  return command.apply(changes, {
    yes,
    dryRun,
    nothingToDo: 'The files are up to date.',
    next: [
      ...hosts.flatMap((host) =>
        changes.map(
          ({ name }) =>
            `Serve ${path.relative(root, path.join(folder, name))} at https://${host}/.well-known/${name}`
        )
      ),
      'Over HTTPS, with no redirect. The release key of Google Play signing: Play Console, Test and release, App signing.',
    ],
  });
}

/**
 * The app and its links, or why the command can't read them.
 * @param {string} root
 * @param {import('../exec').Exec} run
 * @returns {{ project: Project; linking: Linking } | { code: string; message: string }}
 */
function readApp(root, run) {
  if (usesExpo(root)) {
    return {
      code: 'expo-unsupported',
      message:
        'This is an Expo app: Expo declares its links itself (scheme and associatedDomains in app.json).',
    };
  }
  const workspace = openWorkspace(root, run);
  if ('code' in workspace) return workspace;
  const linking = readLinking(workspace.ts, workspace.navigation);
  if ('code' in linking) {
    return {
      code: linking.code,
      message: linking.howTo ? `${linking.message}\n\n${linking.howTo}` : linking.message,
    };
  }
  return { project: workspace.project, linking };
}

/**
 * A file the command writes whole.
 * @param {string} file
 * @param {string} next
 * @param {string} note
 * @returns {FileChange}
 */
function fileChange(file, next, note) {
  const name = path.basename(file);
  if (!fs.existsSync(file)) return { name, platform: 'web', file, status: 'create', next, note };
  const before = fs.readFileSync(file, 'utf8');
  return before === next
    ? { name, platform: 'web', file, status: 'done', note }
    : { name, platform: 'web', file, status: 'change', before, next, note };
}

/**
 * The header of the command in the terminal.
 * @param {import('../ui').UI} ui
 * @param {boolean} json
 * @param {string} root
 * @param {string} name
 */
function banner(ui, json, root, name) {
  if (json) return;
  const version = reactNativeVersion(root);
  ui.banner({
    command: name,
    facts: [
      version
        ? { text: `React Native ${version}`, ok: isSupported(version) }
        : { text: 'React Native not found' },
      { text: 'iOS · Android' },
    ],
  });
}

/**
 * @template Item
 * @param {Item[]} items
 * @returns {Item[]}
 */
function unique(items) {
  return [...new Set(items)];
}

/**
 * @template Item
 * @param {Item[]} items
 * @param {(item: Item) => string} key
 * @returns {Item[]}
 */
function uniqueBy(items, key) {
  const seen = new Set();
  return items.filter((item) => {
    const value = key(item);
    if (seen.has(value)) return false;
    seen.add(value);
    return true;
  });
}

module.exports = { links, linksOpen, linksFiles, planIos, planAndroid };

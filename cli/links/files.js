// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');

/** @typedef {import('./prefixes').Prefix} Prefix */

/**
 * The paths of the site iOS opens in the app, as `components` of apple-app-site-association: each route under the
 * path of each web prefix, a `:param` as `*`, an optional last one with and without it. `*` takes any characters,
 * slashes too, so a path with more parts than the route opens the app as well, and goes to `unmatched` there.
 * @param {Prefix[]} prefixes the web prefixes
 * @param {string[]} routes the route patterns of linking
 * @returns {{ '/': string }[]}
 */
function components(prefixes, routes) {
  const paths = new Set();
  for (const prefix of prefixes) {
    if (routes.length === 0) {
      paths.add(`${prefix.path}/*`);
      continue;
    }
    for (const route of routes) {
      const parts = route.split('/').filter(Boolean);
      const last = parts.at(-1);
      const optional = last?.startsWith(':') && last.endsWith('?');
      const written = parts.map((part) => (part.startsWith(':') ? '*' : part));
      paths.add(`${prefix.path}/${written.join('/')}`);
      if (optional) paths.add(`${prefix.path}/${written.slice(0, -1).join('/')}`);
    }
  }
  return [...paths].map((value) => ({ '/': value.replace(/\/+$/, '') || '/' }));
}

/**
 * apple-app-site-association: the app may open these paths of the site.
 * @param {{ teamId: string; bundleId: string; prefixes: Prefix[]; routes: string[] }} app
 * @returns {string}
 */
function appSiteAssociation({ teamId, bundleId, prefixes, routes }) {
  const details = [{ appIDs: [`${teamId}.${bundleId}`], components: components(prefixes, routes) }];
  return JSON.stringify({ applinks: { details } }, null, 2) + '\n';
}

/**
 * assetlinks.json: the app signed with these keys may open every link of the site.
 * @param {{ packageName: string; fingerprints: string[] }} app
 * @returns {string}
 */
function assetLinks({ packageName, fingerprints }) {
  const statement = {
    relation: ['delegate_permission/common.handle_all_urls'],
    target: {
      namespace: 'android_app',
      package_name: packageName,
      sha256_cert_fingerprints: fingerprints,
    },
  };
  return JSON.stringify([statement], null, 2) + '\n';
}

/**
 * A SHA-256 fingerprint as assetlinks.json writes it, `AB:CD:…` in capitals; undefined when it isn't 32 bytes in hex.
 * @param {string} text with or without colons
 * @returns {string | undefined}
 */
function toFingerprint(text) {
  const hex = text.replace(/[:\s]/g, '').toUpperCase();
  if (!/^[0-9A-F]{64}$/.test(hex)) return undefined;
  return hex.match(/../g)?.join(':');
}

/**
 * The SHA-256 fingerprint of the key debug builds are signed with: the app's `android/app/debug.keystore`, as the
 * template has it (its release builds too, until the app has a key of its own), else the machine's. Undefined
 * without a keystore or a JDK.
 * @param {{
 *   root: string;
 *   home: string;
 *   env: NodeJS.ProcessEnv;
 *   run: import('../exec').Exec;
 * }} context
 * @returns {string | undefined}
 */
function debugFingerprint({ root, home, env, run }) {
  const keystore = [
    path.join(root, 'android', 'app', 'debug.keystore'),
    path.join(home, '.android', 'debug.keystore'),
  ].find((file) => fs.existsSync(file));
  if (!keystore) return undefined;
  const keytool = env.JAVA_HOME ? path.join(env.JAVA_HOME, 'bin', 'keytool') : 'keytool';
  const result = run(keytool, [
    '-list',
    '-v',
    '-keystore',
    keystore,
    '-alias',
    'androiddebugkey',
    '-storepass',
    'android',
    '-keypass',
    'android',
  ]);
  const found = result.ok ? /SHA256:\s*([0-9A-F:]+)/i.exec(result.stdout)?.[1] : undefined;
  return found && toFingerprint(found);
}

module.exports = { components, appSiteAssociation, assetLinks, toFingerprint, debugFingerprint };

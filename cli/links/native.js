// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { buildSetting } = require('../xcode/pbxproj');
const { projectInfoPlist } = require('./identity');

/** @typedef {import('../setup/steps/types').StepResult} StepResult */

/**
 * Info.plist with each scheme of the app in `CFBundleURLTypes`: a new entry for the schemes it doesn't list yet,
 * named by the bundle id.
 * @param {string} text
 * @param {string[]} schemes such as `myapp`, without `://`
 * @returns {StepResult}
 */
function planUrlTypes(text, schemes) {
  // An empty list written as <array/>: opened, so the entry goes inside.
  text = openEmptyArray(text, 'CFBundleURLTypes');
  const howTo = `Add the schemes to Info.plist:\n\n${urlTypesKey('  ', '  ', schemes)}`;
  const unit = /\n(\t| +)<key>/.exec(text)?.[1] ?? '\t';
  const key = /<key>CFBundleURLTypes<\/key>\s*<array>/.exec(text);
  if (!key) {
    // A new key, last in the top dictionary.
    const end = /\n<\/dict>\s*<\/plist>\s*$/.exec(text);
    if (!end) return { status: 'manual', note: 'changed by hand, see below', howTo };
    return {
      status: 'change',
      next:
        text.slice(0, end.index) + '\n' + urlTypesKey(unit, unit, schemes) + text.slice(end.index),
      note: `schemes ${schemes.join(', ')}`,
    };
  }
  const start = key.index + key[0].length;
  const end = closingOf(text, start, 'array');
  if (end < 0) return { status: 'manual', note: 'changed by hand, see below', howTo };
  const types = text.slice(start, end);
  const listed = new Set();
  for (const found of types.matchAll(
    /<key>CFBundleURLSchemes<\/key>\s*<array>([\s\S]*?)<\/array>/g
  )) {
    for (const scheme of found[1].matchAll(/<string>([^<]*)<\/string>/g)) {
      listed.add(scheme[1].trim().toLowerCase());
    }
  }
  const missing = schemes.filter((scheme) => !listed.has(scheme));
  if (missing.length === 0) return { status: 'done', note: 'schemes declared' };
  // The new entry goes last in the list, one level in from the list's own line.
  const lineIndent = /\n([ \t]*)<key>CFBundleURLTypes<\/key>/.exec(text)?.[1] ?? unit;
  const entry = urlTypeEntry(lineIndent + unit, unit, missing);
  const closing = text.lastIndexOf('\n', end) + 1;
  return {
    status: 'change',
    next: text.slice(0, closing) + entry + '\n' + text.slice(closing),
    note: `schemes ${missing.join(', ')}`,
  };
}

/**
 * The `CFBundleURLTypes` key with one entry for the schemes.
 * @param {string} indent of the key's line
 * @param {string} unit one level of indentation
 * @param {string[]} schemes
 */
function urlTypesKey(indent, unit, schemes) {
  return [
    `${indent}<key>CFBundleURLTypes</key>`,
    `${indent}<array>`,
    urlTypeEntry(indent + unit, unit, schemes),
    `${indent}</array>`,
  ].join('\n');
}

/**
 * One entry of `CFBundleURLTypes`.
 * @param {string} indent of the entry's line
 * @param {string} unit
 * @param {string[]} schemes
 */
function urlTypeEntry(indent, unit, schemes) {
  const inner = indent + unit;
  return [
    `${indent}<dict>`,
    `${inner}<key>CFBundleURLName</key>`,
    `${inner}<string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>`,
    `${inner}<key>CFBundleURLSchemes</key>`,
    `${inner}<array>`,
    ...schemes.map((scheme) => `${inner}${unit}<string>${scheme}</string>`),
    `${inner}</array>`,
    `${indent}</dict>`,
  ].join('\n');
}

/**
 * The entitlements of the app with each site in its associated domains, `applinks:<host>`: the app's file, or a new one
 * when `text` is undefined.
 * @param {string | undefined} text
 * @param {string[]} hosts
 * @returns {StepResult}
 */
function planEntitlements(text, hosts) {
  if (text !== undefined) text = openEmptyArray(text, 'com.apple.developer.associated-domains');
  const domains = hosts.map((host) => `applinks:${host}`);
  if (text === undefined) {
    return {
      status: 'change',
      next: [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">',
        '<plist version="1.0">',
        '<dict>',
        '\t<key>com.apple.developer.associated-domains</key>',
        '\t<array>',
        ...domains.map((domain) => `\t\t<string>${domain}</string>`),
        '\t</array>',
        '</dict>',
        '</plist>',
        '',
      ].join('\n'),
      note: domains.join(', '),
    };
  }
  const howTo = `Add to the app's entitlements:\n\n  <key>com.apple.developer.associated-domains</key>\n  <array>\n${domains
    .map((domain) => `    <string>${domain}</string>`)
    .join('\n')}\n  </array>`;
  const unit = /\n(\t| +)<key>/.exec(text)?.[1] ?? '\t';
  const key = /<key>com\.apple\.developer\.associated-domains<\/key>\s*<array>/.exec(text);
  if (!key) {
    const end = /\n<\/dict>\s*<\/plist>\s*$/.exec(text);
    if (!end) return { status: 'manual', note: 'changed by hand, see below', howTo };
    const added = [
      `${unit}<key>com.apple.developer.associated-domains</key>`,
      `${unit}<array>`,
      ...domains.map((domain) => `${unit}${unit}<string>${domain}</string>`),
      `${unit}</array>`,
    ].join('\n');
    return {
      status: 'change',
      next: text.slice(0, end.index) + '\n' + added + text.slice(end.index),
      note: domains.join(', '),
    };
  }
  const start = key.index + key[0].length;
  const end = text.indexOf('</array>', start);
  const listed = new Set(
    [...text.slice(start, end).matchAll(/<string>([^<]*)<\/string>/g)].map((found) =>
      found[1].trim()
    )
  );
  const missing = domains.filter((domain) => !listed.has(domain));
  if (missing.length === 0) return { status: 'done', note: 'sites declared' };
  const lineIndent =
    /\n([ \t]*)<key>com\.apple\.developer\.associated-domains<\/key>/.exec(text)?.[1] ?? unit;
  const closing = text.lastIndexOf('\n', end) + 1;
  return {
    status: 'change',
    next:
      text.slice(0, closing) +
      missing.map((domain) => `${lineIndent}${unit}<string>${domain}</string>\n`).join('') +
      text.slice(closing),
    note: missing.join(', '),
  };
}

/**
 * AndroidManifest.xml with the links on the app's main activity: a filter per scheme, and one verified filter
 * (`autoVerify`) for the sites, apart, as another scheme in it stops the verification. Filters the activity has keep
 * what they have; a site missing from its verified filter is added there.
 * @param {string} text
 * @param {{ schemes: string[]; web: { scheme: string; host: string }[] }} links
 * @returns {StepResult & { singleTask?: boolean }} `singleTask`: whether the activity takes new links in itself
 */
function planIntentFilters(text, { schemes, web }) {
  const howTo = `Add to the main activity of AndroidManifest.xml:\n\n${[
    ...schemes.map((scheme) => schemeFilter('  ', '  ', scheme)),
    ...(web.length > 0 ? [webFilter('  ', '  ', web)] : []),
  ].join('\n')}`;
  const activity = mainActivity(text);
  if (!activity) return { status: 'manual', note: 'no main activity, see below', howTo };
  const singleTask = /android:launchMode\s*=\s*"singleTask"/.test(activity.text);
  const filterIndent = /\n([ \t]*)<intent-filter/.exec(activity.text)?.[1] ?? '        ';
  const unit = /\n([ \t]*)<action/.exec(activity.text)?.[1]?.slice(filterIndent.length) || '    ';

  let body = activity.text;
  const added = [];
  // Schemes: one filter each, unless the activity names the scheme already.
  for (const scheme of schemes) {
    if (new RegExp(String.raw`android:scheme\s*=\s*"${escape(scheme)}"`, 'i').test(body)) continue;
    body = beforeClosing(body, schemeFilter(filterIndent, unit, scheme));
    added.push(scheme);
  }
  // Sites: into the verified filter the activity has, or a new one.
  if (web.length > 0) {
    const verified =
      /<intent-filter\b[^>]*android:autoVerify\s*=\s*"true"[^>]*>[\s\S]*?<\/intent-filter>/.exec(
        body
      );
    if (verified) {
      const filter = verified[0];
      const dataIndent = /\n([ \t]*)<(?:data|action)\b/.exec(filter)?.[1] ?? filterIndent + unit;
      const lines = [];
      for (const scheme of new Set(web.map((site) => site.scheme))) {
        if (!new RegExp(String.raw`android:scheme\s*=\s*"${scheme}"`).test(filter)) {
          lines.push(`${dataIndent}<data android:scheme="${scheme}" />`);
        }
      }
      for (const host of new Set(web.map((site) => site.host))) {
        if (!new RegExp(String.raw`android:host\s*=\s*"${escape(host)}"`, 'i').test(filter)) {
          lines.push(`${dataIndent}<data android:host="${host}" />`);
          added.push(host);
        }
      }
      if (lines.length > 0) {
        const closing = filter.lastIndexOf('\n');
        const next = filter.slice(0, closing) + '\n' + lines.join('\n') + filter.slice(closing);
        body = body.replace(filter, () => next);
      }
    } else {
      body = beforeClosing(body, webFilter(filterIndent, unit, web));
      added.push(...web.map(({ host }) => host));
    }
  }
  if (added.length === 0) return { status: 'done', note: 'links declared', singleTask };
  return {
    status: 'change',
    next: text.slice(0, activity.start) + body + text.slice(activity.end),
    note: `links ${added.join(', ')}`,
    singleTask,
  };
}

/**
 * The activity the launcher starts: the one with the `MAIN` action and the `LAUNCHER` category.
 * @param {string} text
 * @returns {{ start: number; end: number; text: string } | undefined}
 */
function mainActivity(text) {
  for (const found of text.matchAll(/<activity\b[\s\S]*?<\/activity>/g)) {
    if (
      /android\.intent\.action\.MAIN/.test(found[0]) &&
      /android\.intent\.category\.LAUNCHER/.test(found[0])
    ) {
      const start = /** @type {number} */ (found.index);
      return { start, end: start + found[0].length, text: found[0] };
    }
  }
  return undefined;
}

/**
 * The activity's text with lines before its closing tag.
 * @param {string} activity
 * @param {string} lines
 */
function beforeClosing(activity, lines) {
  const closing = activity.lastIndexOf('\n', activity.lastIndexOf('</activity>'));
  return activity.slice(0, closing) + '\n' + lines + activity.slice(closing);
}

/**
 * @param {string} indent
 * @param {string} unit
 * @param {string} scheme
 */
function schemeFilter(indent, unit, scheme) {
  const inner = indent + unit;
  return [
    `${indent}<intent-filter>`,
    `${inner}<action android:name="android.intent.action.VIEW" />`,
    `${inner}<category android:name="android.intent.category.DEFAULT" />`,
    `${inner}<category android:name="android.intent.category.BROWSABLE" />`,
    `${inner}<data android:scheme="${scheme}" />`,
    `${indent}</intent-filter>`,
  ].join('\n');
}

/**
 * @param {string} indent
 * @param {string} unit
 * @param {{ scheme: string; host: string }[]} web
 */
function webFilter(indent, unit, web) {
  const inner = indent + unit;
  return [
    `${indent}<intent-filter android:autoVerify="true">`,
    `${inner}<action android:name="android.intent.action.VIEW" />`,
    `${inner}<category android:name="android.intent.category.DEFAULT" />`,
    `${inner}<category android:name="android.intent.category.BROWSABLE" />`,
    ...[...new Set(web.map(({ scheme }) => scheme))].map(
      (scheme) => `${inner}<data android:scheme="${scheme}" />`
    ),
    ...[...new Set(web.map(({ host }) => host))].map(
      (host) => `${inner}<data android:host="${host}" />`
    ),
    `${indent}</intent-filter>`,
  ].join('\n');
}

/**
 * Where the element that opens before `start` closes: the position of its closing tag, counting the same elements
 * inside it; -1 when it doesn't close.
 * @param {string} text
 * @param {number} start just after the opening tag
 * @param {string} tag
 */
function closingOf(text, start, tag) {
  const pattern = new RegExp(String.raw`<(/?)${tag}\b[^>]*?(/?)>`, 'g');
  pattern.lastIndex = start;
  let depth = 1;
  for (let found = pattern.exec(text); found; found = pattern.exec(text)) {
    if (found[2] === '/') continue;
    depth += found[1] === '/' ? -1 : 1;
    if (depth === 0) return found.index;
  }
  return -1;
}

/**
 * What the app declares on the native side: the schemes of Info.plist and of the main activity's VIEW filters, and the
 * sites of the entitlements and of the activity's http(s) filters.
 * @param {import('../project').Project} project
 * @returns {{ schemes: string[]; hosts: string[] }}
 */
function declaredLinks(project) {
  const schemes = new Set();
  const hosts = new Set();
  const plist = read(project.infoPlist);
  for (const found of plist.matchAll(
    /<key>CFBundleURLSchemes<\/key>\s*<array>([\s\S]*?)<\/array>/g
  )) {
    for (const scheme of found[1].matchAll(/<string>([^<]*)<\/string>/g)) {
      schemes.add(scheme[1].trim().toLowerCase());
    }
  }
  const infoPlist = projectInfoPlist(project);
  const named =
    infoPlist && project.xcodeProject
      ? buildSetting(read(project.xcodeProject), infoPlist, 'CODE_SIGN_ENTITLEMENTS').find(Boolean)
      : undefined;
  if (named && project.xcodeProject) {
    const sourceRoot = path.dirname(path.dirname(project.xcodeProject));
    const entitlements = read(path.join(sourceRoot, named.replace(/^\$\(SRCROOT\)\//, '')));
    for (const found of entitlements.matchAll(/<string>applinks:([^<?]+)[^<]*<\/string>/g)) {
      hosts.add(found[1].trim().toLowerCase());
    }
  }
  const activity = mainActivity(read(project.manifest));
  for (const filter of activity?.text.matchAll(/<intent-filter\b[\s\S]*?<\/intent-filter>/g) ??
    []) {
    if (!/android\.intent\.action\.VIEW/.test(filter[0])) continue;
    const filterSchemes = [...filter[0].matchAll(/android:scheme\s*=\s*"([^"]+)"/g)].map((found) =>
      found[1].toLowerCase()
    );
    for (const scheme of filterSchemes)
      if (scheme !== 'http' && scheme !== 'https') schemes.add(scheme);
    if (filterSchemes.some((scheme) => scheme === 'http' || scheme === 'https')) {
      for (const host of filter[0].matchAll(/android:host\s*=\s*"([^"]+)"/g)) {
        hosts.add(host[1].toLowerCase());
      }
    }
  }
  return { schemes: [...schemes], hosts: [...hosts] };
}

/**
 * @param {string | undefined} file
 * @returns {string}
 */
function read(file) {
  if (!file) return '';
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return '';
  }
}

/**
 * The plist with the array of a key written open, `<array>` and `</array>`, when it is `<array/>`.
 * @param {string} text
 * @param {string} key
 */
function openEmptyArray(text, key) {
  return text.replace(
    new RegExp(String.raw`(\n([ \t]*)<key>${escape(key)}<\/key>\s*)<array\s*\/>`),
    (_match, head, indent) => `${head}<array>\n${indent}</array>`
  );
}

/** @param {string} text */
function escape(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

module.exports = { planUrlTypes, planEntitlements, planIntentFilters, mainActivity, declaredLinks };

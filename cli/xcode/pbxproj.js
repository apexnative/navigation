// @ts-check
'use strict';

const crypto = require('node:crypto');
const path = require('node:path');

/**
 * What adding a file to the Xcode project came to: the project's new text, the file there already, or a project the
 * CLI can't read (changed by hand), with the reason.
 * @typedef {{ status: 'change'; next: string } | { status: 'done' } | { status: 'manual'; reason: string }} AddResult
 */

/**
 * The project with a file added next to a file it has (the app's AppDelegate.swift, its LaunchScreen.storyboard): in
 * the same group, and in the same build phase of the same target, `Sources` or `Resources`. Its path is written as
 * the anchor's is, from the group's folder.
 * @param {string} text the project.pbxproj
 * @param {{
 *   anchor: string;
 *   anchorFile: string;
 *   file: string;
 *   newId?: (taken: string) => string;
 *   phase?: 'Sources' | 'Resources';
 * }} options `anchor` is the name the project lists the anchor under, `anchorFile` where it is on disk, `file` where
 *   the new file is; `newId` makes an object id the text doesn't have (random by default).
 * @returns {AddResult}
 */
function addSourceFile(text, { anchor, anchorFile, file, newId = randomId, phase = 'Sources' }) {
  const ref = new RegExp(
    String.raw`^\s*([0-9A-F]{24}) /\* ${escape(anchor)} \*/ = \{isa = PBXFileReference;([^\n]*)\};$`,
    'm'
  ).exec(text);
  if (!ref) return { status: 'manual', reason: `${anchor} isn't in the project` };
  const [refLine, refId, attributes] = ref;
  const anchorPath = attribute(attributes, 'path') ?? anchor;

  // The folder the group's paths start from: where the anchor is, less the path the project gives it.
  const relativeAnchor = anchorPath.split('/').join(path.sep);
  const base = anchorFile.endsWith(relativeAnchor)
    ? anchorFile.slice(0, anchorFile.length - relativeAnchor.length)
    : path.dirname(anchorFile);
  const filePath = path.relative(base, file).split(path.sep).join('/');
  const name = path.basename(file);
  if (
    new RegExp(String.raw`isa = PBXFileReference;[^\n]*\bpath = ${escape(quote(filePath))};`).test(
      text
    )
  ) {
    return { status: 'done' };
  }

  const groupChild = new RegExp(String.raw`^(\s*)${refId} /\* ${escape(anchor)} \*/,$`, 'm').exec(
    text
  );
  const build = new RegExp(
    String.raw`^\s*([0-9A-F]{24}) /\* ${escape(anchor)} in ${phase} \*/ = \{isa = PBXBuildFile; fileRef = ${refId}\b[^\n]*$`,
    'm'
  ).exec(text);
  if (!groupChild || !build)
    return { status: 'manual', reason: `${anchor} has no group or target` };
  const [buildLine, buildId] = build;
  const sourcesChild = new RegExp(
    String.raw`^(\s*)${buildId} /\* ${escape(anchor)} in ${phase} \*/,$`,
    'm'
  ).exec(text);
  if (!sourcesChild) return { status: 'manual', reason: `${anchor} isn't built by a target` };

  const fileId = newId(text);
  const buildFileId = newId(text + fileId);
  const indent = /^\s*/.exec(refLine)?.[0] ?? '\t\t';
  const fileRef =
    `${indent}${fileId} /* ${name} */ = {isa = PBXFileReference; lastKnownFileType = ${fileType(name)}; ` +
    (filePath === name ? '' : `name = ${quote(name)}; `) +
    `path = ${quote(filePath)}; sourceTree = "<group>"; };`;
  const buildFile = `${indent}${buildFileId} /* ${name} in ${phase} */ = {isa = PBXBuildFile; fileRef = ${fileId} /* ${name} */; };`;

  let next = text;
  next = insertAfter(next, buildLine, buildFile);
  next = insertAfter(next, refLine, fileRef);
  next = insertAfter(next, groupChild[0], `${groupChild[1]}${fileId} /* ${name} */,`);
  next = insertAfter(
    next,
    sourcesChild[0],
    `${sourcesChild[1]}${buildFileId} /* ${name} in ${phase} */,`
  );
  return { status: 'change', next };
}

/**
 * The project with a resource added next to one it has, such as a storyboard next to LaunchScreen.storyboard.
 * @param {string} text the project.pbxproj
 * @param {{ anchor: string; anchorFile: string; file: string; newId?: (taken: string) => string }} options
 * @returns {AddResult}
 */
function addResourceFile(text, options) {
  return addSourceFile(text, { ...options, phase: 'Resources' });
}

/**
 * The project with a file in the group of one it has, built by no target: a file a build setting names, such as the
 * app's entitlements next to its Info.plist. Its path is written as the anchor's is, from the group's folder.
 * @param {string} text the project.pbxproj
 * @param {{ anchor: string; anchorFile: string; file: string; newId?: (taken: string) => string }} options as
 *   `addSourceFile`
 * @returns {AddResult}
 */
function addGroupFile(text, { anchor, anchorFile, file, newId = randomId }) {
  // The anchor's own reference: an older project has an Info.plist of its tests target too.
  const refs = [
    ...text.matchAll(
      new RegExp(
        String.raw`^\s*([0-9A-F]{24}) /\* ${escape(anchor)} \*/ = \{isa = PBXFileReference;([^\n]*)\};$`,
        'gm'
      )
    ),
  ];
  // The one whose path ends the anchor's file the longest way: `HelloWorld/Info.plist` over the tests' `Info.plist`.
  const pathOf = (/** @type {RegExpMatchArray} */ candidate) =>
    (attribute(candidate[2], 'path') ?? anchor).split('/').join(path.sep);
  const ref =
    refs
      .filter((candidate) => anchorFile.endsWith(pathOf(candidate)))
      .sort((a, b) => pathOf(b).length - pathOf(a).length)[0] ?? refs[0];
  if (!ref) return { status: 'manual', reason: `${anchor} isn't in the project` };
  const [refLine, refId, attributes] = ref;
  const anchorPath = attribute(attributes, 'path') ?? anchor;
  const relativeAnchor = anchorPath.split('/').join(path.sep);
  const base = anchorFile.endsWith(relativeAnchor)
    ? anchorFile.slice(0, anchorFile.length - relativeAnchor.length)
    : path.dirname(anchorFile);
  const filePath = path.relative(base, file).split(path.sep).join('/');
  const name = path.basename(file);
  if (
    new RegExp(String.raw`isa = PBXFileReference;[^\n]*\bpath = ${escape(quote(filePath))};`).test(
      text
    )
  ) {
    return { status: 'done' };
  }
  const groupChild = new RegExp(String.raw`^(\s*)${refId} /\* ${escape(anchor)} \*/,$`, 'm').exec(
    text
  );
  if (!groupChild) return { status: 'manual', reason: `${anchor} has no group` };
  const fileId = newId(text);
  const indent = /^\s*/.exec(refLine)?.[0] ?? '\t\t';
  const fileRef =
    `${indent}${fileId} /* ${name} */ = {isa = PBXFileReference; lastKnownFileType = ${fileType(name)}; ` +
    (filePath === name ? '' : `name = ${quote(name)}; `) +
    `path = ${quote(filePath)}; sourceTree = "<group>"; };`;
  let next = insertAfter(text, refLine, fileRef);
  next = insertAfter(next, groupChild[0], `${groupChild[1]}${fileId} /* ${name} */,`);
  return { status: 'change', next };
}

/**
 * The build settings of the app's target: one block per configuration (Debug, Release), told from the other targets'
 * by its `INFOPLIST_FILE`.
 * @param {string} text the project.pbxproj
 * @param {string} infoPlist the app's Info.plist as the project names it, such as `HelloWorld/Info.plist`
 * @returns {{ start: number; end: number; body: string; name: string | undefined; indent: string }[]} where each
 *   block's settings are in the text, the name of its configuration, and the indentation of its lines
 */
function appSettings(text, infoPlist) {
  const blocks = [];
  const pattern = /buildSettings = \{\n([\s\S]*?)\n[ \t]*\};/g;
  for (let found = pattern.exec(text); found; found = pattern.exec(text)) {
    const body = found[1];
    const plist = /^\s*INFOPLIST_FILE = ("(?:[^"\\]|\\.)*"|[^;]+);$/m.exec(body)?.[1];
    if (!plist || unquote(plist).replace(/^\$\(SRCROOT\)\//, '') !== infoPlist) continue;
    const start = found.index + found[0].indexOf('\n') + 1;
    // The name of the configuration follows its settings: `name = Debug;`.
    const name = /^\s*name = ("?)([^";]+)\1;/m.exec(text.slice(found.index + found[0].length))?.[2];
    blocks.push({
      start,
      end: start + body.length,
      body,
      name,
      indent: /^([ \t]*)\S/m.exec(body)?.[1] ?? '\t\t\t\t',
    });
  }
  return blocks;
}

/**
 * The value of a build setting in one configuration of the app target, such as `Debug`; undefined when it isn't set
 * there, or the target has no such configuration.
 * @param {string} text the project.pbxproj
 * @param {string} infoPlist the app's Info.plist as the project names it
 * @param {string} key
 * @param {string} configuration
 * @returns {string | undefined}
 */
function buildSettingOf(text, infoPlist, key, configuration) {
  const block = appSettings(text, infoPlist).find(({ name }) => name === configuration);
  const value =
    block &&
    new RegExp(String.raw`^\s*${key} = ("(?:[^"\\]|\\.)*"|[^;]+);$`, 'm').exec(block.body)?.[1];
  return value ? unquote(value) : undefined;
}

/**
 * The values a build setting has in the app target's configurations, without quotes; none where it isn't set.
 * @param {string} text the project.pbxproj
 * @param {string} infoPlist the app's Info.plist as the project names it
 * @param {string} key such as `CODE_SIGN_ENTITLEMENTS`
 * @returns {(string | undefined)[]} one per configuration
 */
function buildSetting(text, infoPlist, key) {
  return appSettings(text, infoPlist).map(({ body }) => {
    const value = new RegExp(String.raw`^\s*${key} = ("(?:[^"\\]|\\.)*"|[^;]+);$`, 'm').exec(
      body
    )?.[1];
    return value === undefined ? undefined : unquote(value);
  });
}

/**
 * The project with a build setting of the app's target set in each of its configurations, in the alphabetical place
 * Xcode keeps the keys in. `done` when every configuration has the value; `manual` when one has another value, or the
 * target isn't found.
 * @param {string} text the project.pbxproj
 * @param {{ infoPlist: string; key: string; value: string }} setting
 * @returns {AddResult}
 */
function setBuildSetting(text, { infoPlist, key, value }) {
  const blocks = appSettings(text, infoPlist);
  if (blocks.length === 0) {
    return { status: 'manual', reason: `no target builds ${infoPlist}` };
  }
  const values = buildSetting(text, infoPlist, key);
  const other = values.find((current) => current !== undefined && current !== value);
  if (other !== undefined) return { status: 'manual', reason: `${key} is ${other} already` };
  if (values.every((current) => current === value)) return { status: 'done' };
  let next = text;
  // From the last block, so the positions of the ones before stay right.
  for (const block of [...blocks].reverse()) {
    if (new RegExp(String.raw`^\s*${key} = `, 'm').test(block.body)) continue;
    const lines = block.body.split('\n');
    const line = `${block.indent}${key} = ${quote(value)};`;
    // Before the first top key that sorts after it; the lines of a list inside belong to the key above them.
    const at = lines.findIndex((current) => {
      const name = new RegExp(String.raw`^${block.indent}([A-Z0-9_]+)(?:\[[^\]]*\])? = `).exec(
        current
      )?.[1];
      return name !== undefined && name > key;
    });
    lines.splice(at < 0 ? lines.length : at, 0, line);
    next = next.slice(0, block.start) + lines.join('\n') + next.slice(block.end);
  }
  return { status: 'change', next };
}

/**
 * A value of the project without its quotes.
 * @param {string} value
 */
function unquote(value) {
  const trimmed = value.trim();
  return trimmed.startsWith('"') ? trimmed.slice(1, -1).replace(/\\(.)/g, '$1') : trimmed;
}

/**
 * The text with a line after the first line that is `line`.
 * @param {string} text
 * @param {string} line
 * @param {string} added
 */
function insertAfter(text, line, added) {
  const at = text.indexOf(line) + line.length;
  return text.slice(0, at) + '\n' + added + text.slice(at);
}

/**
 * A value of an object's attributes, without its quotes.
 * @param {string} attributes
 * @param {string} key
 * @returns {string | undefined}
 */
function attribute(attributes, key) {
  const found = new RegExp(String.raw`\b${key} = ("(?:[^"\\]|\\.)*"|[^;]+);`).exec(attributes);
  if (!found) return undefined;
  const value = found[1];
  return value.startsWith('"') ? value.slice(1, -1).replace(/\\(.)/g, '$1') : value;
}

/**
 * A value as the project writes it: quoted unless it is a plain path.
 * @param {string} value
 */
function quote(value) {
  return /^[\w./-]+$/.test(value) ? value : `"${value.replace(/["\\]/g, '\\$&')}"`;
}

/** @param {string} name */
function fileType(name) {
  if (name.endsWith('.swift')) return 'sourcecode.swift';
  if (name.endsWith('.m')) return 'sourcecode.c.objc';
  if (name.endsWith('.mm')) return 'sourcecode.cpp.objcpp';
  if (name.endsWith('.storyboard')) return 'file.storyboard';
  if (name.endsWith('.entitlements')) return 'text.plist.entitlements';
  return 'text';
}

/**
 * An object id of the project: 24 hex digits, not in the text.
 * @param {string} taken
 */
function randomId(taken) {
  for (;;) {
    const id = crypto.randomBytes(12).toString('hex').toUpperCase();
    if (!taken.includes(id)) return id;
  }
}

/** @param {string} text */
function escape(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

module.exports = {
  addSourceFile,
  addResourceFile,
  addGroupFile,
  buildSetting,
  buildSettingOf,
  setBuildSetting,
};

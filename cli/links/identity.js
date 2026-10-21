// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { buildSetting, buildSettingOf } = require('../xcode/pbxproj');

/**
 * The app's Info.plist as its Xcode project names it, from the project's folder: `HelloWorld/Info.plist`.
 * @param {import('../project').Project} project
 * @returns {string | undefined}
 */
function projectInfoPlist(project) {
  if (!project.xcodeProject || !project.infoPlist) return undefined;
  const sourceRoot = path.dirname(path.dirname(project.xcodeProject));
  return path.relative(sourceRoot, project.infoPlist).split(path.sep).join('/');
}

/**
 * The bundle id of the iOS app, as a configuration of it makes it (Release by default; the simulator runs Debug):
 * `$(PRODUCT_NAME)` in it filled in, as the template's `org.reactjs.native.example.$(PRODUCT_NAME:rfc1034identifier)` is.
 * @param {import('../project').Project} project
 * @param {string} [configuration]
 * @returns {string | undefined}
 */
function bundleId(project, configuration = 'Release') {
  const text = read(project.xcodeProject);
  const infoPlist = projectInfoPlist(project);
  if (!text || !infoPlist) return undefined;
  const setting = (/** @type {string} */ key) =>
    buildSettingOf(text, infoPlist, key, configuration) ??
    buildSetting(text, infoPlist, key).at(-1);
  const id = setting('PRODUCT_BUNDLE_IDENTIFIER');
  const name = setting('PRODUCT_NAME') ?? path.basename(path.dirname(project.infoPlist ?? ''));
  return id
    ?.replace(/\$\(PRODUCT_NAME:rfc1034identifier\)/g, name.replace(/[^A-Za-z0-9.-]/g, '-'))
    .replace(/\$\(PRODUCT_NAME\)/g, name);
}

/**
 * The team the iOS app is signed for, when the project names one.
 * @param {import('../project').Project} project
 * @returns {string | undefined}
 */
function developmentTeam(project) {
  const text = read(project.xcodeProject);
  const infoPlist = projectInfoPlist(project);
  if (!text || !infoPlist) return undefined;
  return buildSetting(text, infoPlist, 'DEVELOPMENT_TEAM').find(Boolean);
}

/**
 * The id of the Android app: `applicationId` of its build file.
 * @param {import('../project').Project} project
 * @returns {string | undefined}
 */
function applicationId(project) {
  const text = read(project.appBuildGradle);
  return (text && /\bapplicationId\s*=?\s*["']([\w.]+)["']/.exec(text)?.[1]) ?? undefined;
}

/**
 * @param {string | undefined} file
 * @returns {string | undefined}
 */
function read(file) {
  if (!file) return undefined;
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return undefined;
  }
}

module.exports = { projectInfoPlist, bundleId, developmentTeam, applicationId };

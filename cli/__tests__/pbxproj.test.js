// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const {
  addGroupFile,
  addSourceFile,
  buildSetting,
  buildSettingOf,
  setBuildSetting,
} = require('../xcode/pbxproj');

const IOS = path.join(__dirname, '..', '__fixtures__', 'app-0.87', 'ios');
const PROJECT = fs.readFileSync(path.join(IOS, 'HelloWorld.xcodeproj', 'project.pbxproj'), 'utf8');

/** Ids in order, as a test needs them the same each time. */
function ids() {
  const next = [
    'AAAAAAAAAAAAAAAAAAAAAAA1',
    'AAAAAAAAAAAAAAAAAAAAAAA2',
    'AAAAAAAAAAAAAAAAAAAAAAA3',
    'AAAAAAAAAAAAAAAAAAAAAAA4',
  ];
  return () => /** @type {string} */ (next.shift());
}

const addProfile = (/** @type {string} */ text, newId = ids()) =>
  addSourceFile(text, {
    anchor: 'AppDelegate.swift',
    anchorFile: path.join(IOS, 'HelloWorld', 'AppDelegate.swift'),
    file: path.join(IOS, 'HelloWorld', 'Screens', 'ProfileScreen.swift'),
    newId,
  });

describe('a source file added to the Xcode project', () => {
  it('is in the group of AppDelegate.swift and built by its target, with its path from the group', () => {
    const result = addProfile(PROJECT);
    if (result.status !== 'change') throw new Error(result.status);
    const added = result.next
      .split('\n')
      .filter((line) => line.includes('ProfileScreen.swift'))
      .map((line) => line.trim());
    expect(added).toEqual([
      'AAAAAAAAAAAAAAAAAAAAAAA2 /* ProfileScreen.swift in Sources */ = {isa = PBXBuildFile; fileRef = AAAAAAAAAAAAAAAAAAAAAAA1 /* ProfileScreen.swift */; };',
      'AAAAAAAAAAAAAAAAAAAAAAA1 /* ProfileScreen.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; name = ProfileScreen.swift; path = HelloWorld/Screens/ProfileScreen.swift; sourceTree = "<group>"; };',
      'AAAAAAAAAAAAAAAAAAAAAAA1 /* ProfileScreen.swift */,',
      'AAAAAAAAAAAAAAAAAAAAAAA2 /* ProfileScreen.swift in Sources */,',
    ]);
    // Next to AppDelegate.swift in the group's children and in the target's sources.
    expect(result.next).toMatch(
      /AppDelegate\.swift \*\/,\n\t+AAAAAAAAAAAAAAAAAAAAAAA1 \/\* ProfileScreen\.swift \*\/,/
    );
    expect(result.next).toMatch(
      /AppDelegate\.swift in Sources \*\/,\n\t+AAAAAAAAAAAAAAAAAAAAAAA2 \/\* ProfileScreen\.swift in Sources \*\/,/
    );
  });

  it('is there already the second time', () => {
    const result = addProfile(PROJECT);
    if (result.status !== 'change') throw new Error(result.status);
    expect(addProfile(result.next)).toEqual({ status: 'done' });
  });

  it("is left to the user when the project doesn't list AppDelegate.swift as the template does", () => {
    const changed = PROJECT.replace(/AppDelegate\.swift/g, 'Main.swift');
    expect(addProfile(changed)).toEqual({
      status: 'manual',
      reason: "AppDelegate.swift isn't in the project",
    });
  });

  it('makes ids the project does not have', () => {
    const result = addSourceFile(PROJECT, {
      anchor: 'AppDelegate.swift',
      anchorFile: path.join(IOS, 'HelloWorld', 'AppDelegate.swift'),
      file: path.join(IOS, 'HelloWorld', 'ApexNavigation.generated.swift'),
    });
    if (result.status !== 'change') throw new Error(result.status);
    const added = result.next
      .split('\n')
      .filter((line) => line.includes('ApexNavigation.generated.swift'));
    const newIds = new Set(added.flatMap((line) => line.match(/[0-9A-F]{24}/g) ?? []));
    expect(newIds.size).toBe(2);
    for (const id of newIds) expect(PROJECT.includes(id)).toBe(false);
  });
});

describe('the build settings of the app target', () => {
  const setting = { infoPlist: 'HelloWorld/Info.plist', key: 'CODE_SIGN_ENTITLEMENTS' };

  it('are set in each configuration, where Xcode keeps the key', () => {
    const result = setBuildSetting(PROJECT, {
      ...setting,
      value: 'HelloWorld/HelloWorld.entitlements',
    });
    if (result.status !== 'change') throw new Error(result.status);
    expect(buildSetting(result.next, 'HelloWorld/Info.plist', 'CODE_SIGN_ENTITLEMENTS')).toEqual([
      'HelloWorld/HelloWorld.entitlements',
      'HelloWorld/HelloWorld.entitlements',
    ]);
    // In the alphabetical place: after CLANG_ENABLE_MODULES, before CURRENT_PROJECT_VERSION.
    expect(result.next).toContain(
      'CLANG_ENABLE_MODULES = YES;\n\t\t\t\tCODE_SIGN_ENTITLEMENTS = HelloWorld/HelloWorld.entitlements;\n\t\t\t\tCURRENT_PROJECT_VERSION = 1;'
    );
    expect(
      setBuildSetting(result.next, { ...setting, value: 'HelloWorld/HelloWorld.entitlements' })
    ).toEqual({
      status: 'done',
    });
  });

  it('are left to the app when it has another value', () => {
    const result = setBuildSetting(PROJECT, { ...setting, value: 'A.entitlements' });
    if (result.status !== 'change') throw new Error(result.status);
    expect(setBuildSetting(result.next, { ...setting, value: 'B.entitlements' })).toEqual({
      status: 'manual',
      reason: 'CODE_SIGN_ENTITLEMENTS is A.entitlements already',
    });
  });

  it('read the values of each configuration', () => {
    expect(buildSetting(PROJECT, 'HelloWorld/Info.plist', 'PRODUCT_NAME')).toEqual([
      'HelloWorld',
      'HelloWorld',
    ]);
    expect(buildSetting(PROJECT, 'HelloWorld/Info.plist', 'DEVELOPMENT_TEAM')).toEqual([
      undefined,
      undefined,
    ]);
  });
});

describe('a file in the group of the app', () => {
  it('is listed next to Info.plist, built by no target', () => {
    const result = addGroupFile(PROJECT, {
      anchor: 'Info.plist',
      anchorFile: path.join(IOS, 'HelloWorld', 'Info.plist'),
      file: path.join(IOS, 'HelloWorld', 'HelloWorld.entitlements'),
      newId: ids(),
    });
    if (result.status !== 'change') throw new Error(result.status);
    const added = result.next
      .split('\n')
      .filter((line) => line.includes('HelloWorld.entitlements'))
      .map((line) => line.trim());
    expect(added).toEqual([
      'AAAAAAAAAAAAAAAAAAAAAAA1 /* HelloWorld.entitlements */ = {isa = PBXFileReference; lastKnownFileType = text.plist.entitlements; name = HelloWorld.entitlements; path = HelloWorld/HelloWorld.entitlements; sourceTree = "<group>"; };',
      'AAAAAAAAAAAAAAAAAAAAAAA1 /* HelloWorld.entitlements */,',
    ]);
  });
});

describe('the app target among others', () => {
  // An older template: the tests target has an Info.plist of its own, listed first, and a Debug bundle id of its own.
  const OLDER = PROJECT.replace(
    '/* Begin PBXFileReference section */\n',
    '/* Begin PBXFileReference section */\n\t\t00E356F01AD99517003FC87E /* Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = "<group>"; };\n'
  );

  it("lists a file next to the app's own Info.plist", () => {
    const result = addGroupFile(OLDER, {
      anchor: 'Info.plist',
      anchorFile: path.join(IOS, 'HelloWorld', 'Info.plist'),
      file: path.join(IOS, 'HelloWorld', 'HelloWorld.entitlements'),
      newId: ids(),
    });
    if (result.status !== 'change') throw new Error(result.status);
    expect(result.next).toContain('path = HelloWorld/HelloWorld.entitlements;');
  });

  it('reads a setting of one configuration', () => {
    const debug = PROJECT.replace(
      'PRODUCT_BUNDLE_IDENTIFIER = "org.reactjs.native.example.$(PRODUCT_NAME:rfc1034identifier)";',
      'PRODUCT_BUNDLE_IDENTIFIER = org.example.dev;'
    );
    expect(
      buildSettingOf(debug, 'HelloWorld/Info.plist', 'PRODUCT_BUNDLE_IDENTIFIER', 'Debug')
    ).toBe('org.example.dev');
    expect(
      buildSettingOf(debug, 'HelloWorld/Info.plist', 'PRODUCT_BUNDLE_IDENTIFIER', 'Release')
    ).toBe('org.reactjs.native.example.$(PRODUCT_NAME:rfc1034identifier)');
  });
});

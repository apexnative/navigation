// @ts-check
'use strict';

const { addImport } = require('../source');

/** @typedef {import('./types').StepResult} StepResult */

const HOW_TO = `Extend ANNAppDelegate. It creates the window and React Native and starts them; the app gives it
its delegate (the bundle URL) before calling super.

  import ApexNavigation

  @main
  class AppDelegate: ANNAppDelegate {
    override func application(
      _ application: UIApplication,
      didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
      self.reactNativeDelegate = ReactNativeDelegate()
      return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
  }

ReactNativeDelegate is the app's RCTDefaultReactNativeFactoryDelegate, which gives the bundle URL (bundleURL()); in
React Native 0.88's template that method is in SceneDelegate.`;

// Lines of the template's launch: ANNAppDelegate does all of it.
const TEMPLATE_LINES = [
  /^[ \t]*var\s+window\s*:\s*UIWindow\?[ \t]*\n/m,
  /^[ \t]*var\s+reactNativeDelegate\s*:\s*ReactNativeDelegate\?[ \t]*\n/m,
  /^[ \t]*var\s+reactNativeFactory\s*:\s*RCTReactNativeFactory\?[ \t]*\n/m,
  /^[ \t]*let\s+factory\s*=\s*RCTReactNativeFactory\(delegate:\s*delegate\)[ \t]*\n/m,
  /^[ \t]*delegate\.dependencyProvider\s*=\s*RCTAppDependencyProvider\(\)[ \t]*\n/m,
  /^[ \t]*reactNativeDelegate\s*=\s*delegate[ \t]*\n/m,
  /^[ \t]*reactNativeFactory\s*=\s*factory[ \t]*\n/m,
  /^[ \t]*window\s*=\s*UIWindow\(frame:\s*UIScreen\.main\.bounds\)[ \t]*\n/m,
  /^[ \t]*factory\.startReactNative\(\s*withModuleName:\s*"[^"]*"\s*,\s*in:\s*window\s*,\s*launchOptions:\s*launchOptions\s*\)[ \t]*\n/m,
];

const HEADER = /class\s+(\w+)\s*:\s*UIResponder\s*,\s*UIApplicationDelegate\s*\{/;
const LAUNCH =
  /^([ \t]*)func(\s+application\(\s*_\s+application\s*:\s*UIApplication\s*,\s*didFinishLaunchingWithOptions)/m;
const LAUNCH_OVERRIDE =
  /^[ \t]*override\s+func\s+application\(\s*_\s+application\s*:\s*UIApplication\s*,\s*didFinishLaunchingWithOptions/m;
const DELEGATE = /^([ \t]*)let\s+delegate\s*=\s*ReactNativeDelegate\(\)[ \t]*$/m;
const RETURN_TRUE = /^([ \t]*)return\s+true[ \t]*$/m;

// React Native 0.88's template starts React Native in its scene delegate: the app delegate only returns true. The
// bundle URL moves into a delegate of React Native's factory, as the earlier templates had it.
const REACT_NATIVE_DELEGATE = `class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func bundleURL() -> URL? {
    #if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
    #else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }
}
`;
const RETURN_TRUE_OR_TRUE = /^([ \t]*)(?:return\s+)?true[ \t]*$/m;

/**
 * AppDelegate.swift: the app delegate becomes an ANNAppDelegate.
 * @param {string} text
 * @returns {StepResult}
 */
function planAppDelegate(text) {
  if (/class\s+\w+\s*:\s*ANNAppDelegate\b/.test(text))
    return { status: 'done', note: 'already set up' };
  const manual = /** @type {StepResult} */ ({
    status: 'manual',
    note: 'changed by hand, see below',
    howTo: HOW_TO,
  });
  if (!HEADER.test(text) || !LAUNCH.test(text)) return manual;
  let next = DELEGATE.test(text) ? fromFactoryLaunch(text) : fromSceneLaunch(text);
  if (next === undefined) return manual;

  // The dependency provider is ANNAppDelegate's now.
  if (!/\bRCTAppDependencyProvider\b/.test(next)) {
    next = next.replace(/^import\s+ReactAppDependencyProvider[ \t]*\n/m, '');
  }
  next = addImport(next, 'import ApexNavigation', { sorted: false });
  return { status: 'change', next, note: 'ANNAppDelegate' };
}

/**
 * The app delegate of the templates before React Native 0.88, which makes React Native's factory and window in its
 * launch; undefined when the launch has code of the app's own that uses them.
 * @param {string} text
 * @returns {string | undefined}
 */
function fromFactoryLaunch(text) {
  let next = text
    .replace(HEADER, 'class $1: ANNAppDelegate {')
    .replace(LAUNCH, '$1override func$2');
  for (const line of TEMPLATE_LINES) {
    if (!line.test(next)) return undefined;
    next = next.replace(line, '');
  }
  next = next.replace(DELEGATE, '$1self.reactNativeDelegate = ReactNativeDelegate()');

  // The launch now ends in super's, which starts React Native.
  const launchAt = next.search(LAUNCH_OVERRIDE);
  const returnAt = next.slice(launchAt).search(RETURN_TRUE);
  if (returnAt < 0) return undefined;
  const body = next.slice(launchAt, launchAt + returnAt).replace(/self\.reactNativeDelegate/g, '');
  // Code of the app's own that used the template's factory, delegate or window: it has to move by hand.
  if (/\b(factory|delegate|window)\b/.test(body)) return undefined;
  const rest = next
    .slice(launchAt + returnAt)
    .replace(
      RETURN_TRUE,
      '$1return super.application(application, didFinishLaunchingWithOptions: launchOptions)'
    );
  next = next.slice(0, launchAt + returnAt) + rest;

  // The blank lines the template's launch leaves behind.
  return next
    .replace(/(class\s+\w+:\s*ANNAppDelegate\s*\{\n)(?:[ \t]*\n)+/, '$1')
    .replace(/(self\.reactNativeDelegate = ReactNativeDelegate\(\)\n)(?:[ \t]*\n)+/, '$1')
    .replace(/\n(?:[ \t]*\n)+([ \t]*return super\.application\()/, '\n$1');
}

/**
 * The app delegate of React Native 0.88's template, whose launch only returns true; undefined when the app delegate
 * makes React Native or a window of its own.
 * @param {string} text
 * @returns {string | undefined}
 */
function fromSceneLaunch(text) {
  if (/\b(RCTReactNativeFactory|ReactNativeDelegate|window)\b/.test(text)) return undefined;
  let next = text
    .replace(HEADER, 'class $1: ANNAppDelegate {')
    .replace(LAUNCH, '$1override func$2');
  const launchAt = next.search(LAUNCH_OVERRIDE);
  const returnAt = next.slice(launchAt).search(RETURN_TRUE_OR_TRUE);
  if (returnAt < 0) return undefined;
  const rest = next
    .slice(launchAt + returnAt)
    .replace(
      RETURN_TRUE_OR_TRUE,
      '$1self.reactNativeDelegate = ReactNativeDelegate()\n' +
        '$1return super.application(application, didFinishLaunchingWithOptions: launchOptions)'
    );
  next = `${next.slice(0, launchAt + returnAt)}${rest.trimEnd()}\n\n${REACT_NATIVE_DELEGATE}`;
  for (const module of ['React', 'React_RCTAppDelegate']) {
    next = addImport(next, `import ${module}`, { sorted: false });
  }
  return next;
}

module.exports = { planAppDelegate };

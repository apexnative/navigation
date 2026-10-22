// @ts-check
'use strict';

/** @typedef {import('./types').StepResult} StepResult */

const HOW_TO = `Let ANNSceneDelegate take the app's scene: it moves the navigation's window into the scene and passes the
scene's links to the app delegate. React Native starts in ANNAppDelegate, not here.

  import ApexNavigation
  import UIKit

  class SceneDelegate: ANNSceneDelegate {}

A scene delegate of the app's own calls ApexNavigation.attach(to:) in scene(_:willConnectTo:options:) instead, as the
deep links guide shows.`;

const SET_UP = `import ApexNavigation
import UIKit

/// ANNSceneDelegate moves the navigation's window into the app's scene and passes the scene's links to the app delegate.
class SceneDelegate: ANNSceneDelegate {}
`;

// React Native 0.88's template, without its spaces: the scene delegate that makes React Native's factory and starts
// it in the scene's window.
const TEMPLATE = `import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import UIKit

class SceneDelegate: RCTDefaultReactNativeFactoryDelegate, UIWindowSceneDelegate {
  var window: UIWindow?
  var reactNativeFactory: RCTReactNativeFactory?

  func scene(
    _ scene: UIScene,
    willConnectTo session: UISceneSession,
    options connectionOptions: UIScene.ConnectionOptions
  ) {
    guard let windowScene = scene as? UIWindowScene else {
      return
    }

    dependencyProvider = RCTAppDependencyProvider()
    reactNativeFactory = RCTReactNativeFactory(delegate: self)
    window = UIWindow(windowScene: windowScene)

    #if DEBUG
    let devMenuConfiguration = RCTDevMenuConfiguration(
      devMenuEnabled: true,
      shakeGestureEnabled: true,
      keyboardShortcutsEnabled: true
    )
    reactNativeFactory?.devMenuConfiguration = devMenuConfiguration
    #endif

    reactNativeFactory?.startReactNative(
      withModuleName: "",
      in: window,
      connectionOptions: connectionOptions
    )
  }

  func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    RCTLinkingManager.scene(scene, openURLContexts: URLContexts)
  }

  func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
    RCTLinkingManager.scene(scene, continue: userActivity)
  }

  override func bundleURL() -> URL? {
    #if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
    #else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }
}
`;

/**
 * The text without its spaces and the app's name, to compare with the template.
 * @param {string} text
 */
function shape(text) {
  return text.replace(/withModuleName:\s*"[^"]*"/, 'withModuleName:""').replace(/\s+/g, '');
}

/**
 * SceneDelegate.swift of React Native 0.88 and newer: the scene delegate becomes an ANNSceneDelegate, since
 * ANNAppDelegate starts React Native and owns the window. Only the template's file changes; one with code of the app's
 * own is changed by hand.
 * @param {string} text
 * @returns {StepResult}
 */
function planSceneDelegate(text) {
  if (
    /class\s+\w+\s*:\s*ANNSceneDelegate\b/.test(text) ||
    /\bApexNavigation\.attach\s*\(/.test(text)
  )
    return { status: 'done', note: 'already set up' };
  if (shape(text) !== shape(TEMPLATE))
    return { status: 'manual', note: 'changed by hand, see below', howTo: HOW_TO };
  return { status: 'change', next: SET_UP, note: 'ANNSceneDelegate' };
}

module.exports = { planSceneDelegate };

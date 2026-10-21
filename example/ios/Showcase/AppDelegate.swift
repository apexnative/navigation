import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import ApexNavigation
import SwiftUI
import UIKit

/// ANNAppDelegate (@apexnative/navigation) creates the React Native factory/host,
/// bootstraps ApexNavigation and owns the key window. Screens are driven from JS via Navigation.setRoot().
@main
class AppDelegate: ANNAppDelegate {

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    self.reactNativeDelegate = ReactNativeDelegate()

    // Native screens, which `screens` names with `nativeScreen` (section 18).
    ApexNavigation.registerNativeScreen("NativeProfileScreen") { context in
      // UIKit, written in Objective-C.
      NativeProfileViewController(context: context)
    }
    ApexNavigation.registerNativeScreen("SwiftUIProfileScreen") { context in
      UIHostingController(rootView: SwiftUIProfileView(context: context))
    }
    ApexNavigation.registerNativeScreen("UsePropsDemoScreen") { context in
      UIHostingController(rootView: UsePropsDemoView(screen: ScreenProps(context)))
    }
    writeSampleDocument()

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  /// The file of the title menu demo (2.10): `topBar.title.document.url` is `~/Documents/Quarterly Report.txt`.
  private func writeSampleDocument() {
    let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    let url = documents.appendingPathComponent("Quarterly Report.txt")
    if !FileManager.default.fileExists(atPath: url.path) {
      try? "Revenue grew 12 % in the third quarter.\n".write(to: url, atomically: true, encoding: .utf8)
    }
  }
}

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    self.bundleURL()
  }

  override func bundleURL() -> URL? {
#if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
  }
}

#import <UIKit/UIKit.h>

@class RCTDefaultReactNativeFactoryDelegate;
@class RCTReactNativeFactory;

NS_ASSUME_NONNULL_BEGIN

/**
 * The app delegate of an app on the navigation. It makes React Native's factory and host with `reactNativeDelegate`,
 * starts the navigation and owns its window; the screens come from JS (`setRoot`). An app's delegate inherits it,
 * sets `reactNativeDelegate` (its subclass of `RCTDefaultReactNativeFactoryDelegate`, which gives the bundle URL) and
 * registers its native screens in `application(_:didFinishLaunchingWithOptions:)` before calling `super`.
 *
 * ```swift
 * @main
 * class AppDelegate: ANNAppDelegate {
 *   override func application(
 *     _ application: UIApplication,
 *     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
 *   ) -> Bool {
 *     self.reactNativeDelegate = ReactNativeDelegate()
 *     return super.application(application, didFinishLaunchingWithOptions: launchOptions)
 *   }
 * }
 * ```
 */
@interface ANNAppDelegate : UIResponder <UIApplicationDelegate>

/// The navigation's window.
@property(nonatomic, strong, nullable) UIWindow *window;

/// Gives React Native the bundle URL and the app's settings; set it before `super`'s
/// `application:didFinishLaunchingWithOptions:`.
@property(nonatomic, strong, nullable) RCTDefaultReactNativeFactoryDelegate *reactNativeDelegate;

/// React Native's factory, made at launch with `reactNativeDelegate`.
@property(nonatomic, strong, nullable) RCTReactNativeFactory *reactNativeFactory;

/**
 * Hands a deep link to React Native's `Linking`; the link that launched the app is the latest one dispatched by the
 * time its scene connected. Any thread; nil does nothing.
 */
- (void)dispatchDeepLinkURL:(nullable NSURL *)url NS_SWIFT_NAME(dispatchDeepLinkURL(_:));

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(nullable NSDictionary<UIApplicationLaunchOptionsKey, id> *)launchOptions;

/// `ANNSceneDelegate` for the app's scene, unless the Info.plist names a scene delegate of its own.
- (UISceneConfiguration *)application:(UIApplication *)application
    configurationForConnectingSceneSession:(UISceneSession *)connectingSceneSession
                                   options:(UISceneConnectionOptions *)options;

/// The URL goes to `dispatchDeepLinkURL:`.
- (BOOL)application:(UIApplication *)application
            openURL:(NSURL *)url
            options:(NSDictionary<UIApplicationOpenURLOptionsKey, id> *)options;

/// A web link (`NSUserActivityTypeBrowsingWeb`) goes to `dispatchDeepLinkURL:`; other activities are not taken.
- (BOOL)application:(UIApplication *)application
    continueUserActivity:(NSUserActivity *)userActivity
      restorationHandler:
          (void (^)(NSArray<id<UIUserActivityRestoring>> *_Nullable restorableObjects))restorationHandler;

@end

NS_ASSUME_NONNULL_END

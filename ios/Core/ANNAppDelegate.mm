#import "ANNAppDelegate.h"
#import "ANNDeepLinkQueue.h"
#import "ANNSceneDelegate.h"
#import "ApexNavigation+Internal.h"
#import <React/RCTBridge.h>
#import <React/RCTLog.h>
#import <React/RCTUtils.h>
#import <ReactAppDependencyProvider/RCTAppDependencyProvider.h>
#import <ReactCommon/RCTHost.h>

// The version of React Native the app builds with (`REACT_NATIVE_VERSION_MINOR`, written by React Native's release
// script): the calls React Native 0.87 removed are made only below it.
#if __has_include(<cxxreact/ReactNativeVersion.h>)
#import <cxxreact/ReactNativeVersion.h>
#endif
#if defined(REACT_NATIVE_VERSION_MAJOR) && REACT_NATIVE_VERSION_MAJOR == 0 && \
    REACT_NATIVE_VERSION_MINOR < 87
#define ANN_REACT_NATIVE_BEFORE_0_87 1
#if __has_include(<React-RCTAppDelegate/RCTAppSetupUtils.h>)
#import <React-RCTAppDelegate/RCTAppSetupUtils.h>
#elif __has_include(<React_RCTAppDelegate/RCTAppSetupUtils.h>)
#import <React_RCTAppDelegate/RCTAppSetupUtils.h>
#else
#import <React/RCTAppSetupUtils.h>
#endif
#endif

#if __has_include(<React-RCTAppDelegate/RCTReactNativeFactory.h>)
#import <React-RCTAppDelegate/RCTDefaultReactNativeFactoryDelegate.h>
#import <React-RCTAppDelegate/RCTReactNativeFactory.h>
#elif __has_include(<React_RCTAppDelegate/RCTReactNativeFactory.h>)
#import <React_RCTAppDelegate/RCTDefaultReactNativeFactoryDelegate.h>
#import <React_RCTAppDelegate/RCTReactNativeFactory.h>
#else
#import <React/RCTDefaultReactNativeFactoryDelegate.h>
#import <React/RCTReactNativeFactory.h>
#endif

// The app declares the scene life cycle: its window shows once the scene connects (`ANNSceneDelegate`).
static BOOL ANNAppUsesScenes(void) {
    return [NSBundle.mainBundle objectForInfoDictionaryKey:@"UIApplicationSceneManifest"] != nil;
}

@implementation ANNAppDelegate

// React Native starts with no surface of its own: the navigation shows every screen. The host starts at once and loads
// the bundle while the window is made; the first root comes from JavaScript.
- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(NSDictionary<UIApplicationLaunchOptionsKey, id> *)launchOptions {
    RCTDefaultReactNativeFactoryDelegate *delegate = self.reactNativeDelegate;
    if (!delegate) {
        RCTLogError(@"[ApexNavigation] ANNAppDelegate: set reactNativeDelegate before application:"
                    @"didFinishLaunchingWithOptions: of ANNAppDelegate runs.");
        return YES;
    }
#ifdef ANN_REACT_NATIVE_BEFORE_0_87
    RCTAppSetupPrepareApp(application, YES);
    RCTEnableTurboModuleInteropBridgeProxy(YES);
#endif
#ifndef RCT_REMOVE_LEGACY_MODULE_INTEROP
    // The app's modules written for the bridge keep working as TurboModules.
    RCTEnableTurboModuleInterop(YES);
#endif
    // The codegen's provider of the app's modules and components, generated for the app.
    if (!delegate.dependencyProvider) {
        delegate.dependencyProvider = [RCTAppDependencyProvider new];
    }
    self.reactNativeFactory = [[RCTReactNativeFactory alloc] initWithDelegate:delegate];
    RCTRootViewFactory *rootViewFactory = self.reactNativeFactory.rootViewFactory;
    RCTHost *host = [rootViewFactory createReactHost:launchOptions];
    rootViewFactory.reactHost = host;
    [ApexNavigation bootstrapWithHost:host];
    self.window = ApexNavigation.mainWindow;
    if (!ANNAppUsesScenes()) {
        [self.window makeKeyAndVisible];
    }
    return YES;
}

- (UISceneConfiguration *)application:(UIApplication *)application
    configurationForConnectingSceneSession:(UISceneSession *)connectingSceneSession
                                   options:(UISceneConnectionOptions *)options {
    UISceneConfiguration *configuration = [connectingSceneSession.configuration copy];
    // Only the app's own scene takes the navigation window: an external display's scene doesn't.
    BOOL isAppScene =
        [connectingSceneSession.role isEqualToString:UIWindowSceneSessionRoleApplication];
    if (!configuration.delegateClass && isAppScene) {
        configuration.delegateClass = ANNSceneDelegate.class;
    }
    return configuration;
}

// From any thread (a push SDK calls from its own): the queue lives on the main queue.
- (void)dispatchDeepLinkURL:(NSURL *)url {
    if (!url) {
        return;
    }
    RCTExecuteOnMainQueue(^{
      [ANNDeepLinkQueue dispatchURL:url];
    });
}

- (BOOL)application:(UIApplication *)application
            openURL:(NSURL *)url
            options:(NSDictionary<UIApplicationOpenURLOptionsKey, id> *)options {
    [self dispatchDeepLinkURL:url];
    return YES;
}

- (BOOL)application:(UIApplication *)application
    continueUserActivity:(NSUserActivity *)userActivity
      restorationHandler:(void (^)(NSArray<id<UIUserActivityRestoring>> *restorableObjects))restorationHandler {
    if (![userActivity.activityType isEqualToString:NSUserActivityTypeBrowsingWeb]) {
        return NO;
    }
    [self dispatchDeepLinkURL:userActivity.webpageURL];
    return YES;
}

@end

#import "ApexNavigation+Internal.h"
#import "ANNAppDirection.h"
#import "ANNAssert.h"
#import "ANNDeepLinkQueue.h"
#import "ANNModalHostViewComponentView.h"
#import "ANNNodeRegistry.h"
#import "ANNOverlayCoordinator.h"
#import "ANNPresentationGate.h"
#import "ANNReloadCoordinator.h"
#import "ANNSlotHost.h"
#import "ANNSplashViewController.h"
#import "ANNTabsPreloadWindow.h"
#import "ANNUtils.h"
#import <React/RCTLog.h>
#import <ReactCommon/RCTHost.h>

// Main queue, but the registrations of native screens: an app may register from any thread, before the start.
static RCTHost *ANNHost;
static UIWindow *ANNMainWindow;
static BOOL ANNBootstrapped;
// The emitter of the JavaScript that runs now; a new one once the generation grew. Any thread.
static ANNEventEmitter *ANNCurrentEventEmitter;
static NSMutableDictionary<NSString *, ANNNativeScreenCreator> *ANNNativeScreenCreators;

// The scene of the app's main window, when one is connected already: the active one first.
static UIWindowScene *ANNConnectedWindowScene(void) {
    UIWindowScene *found = nil;
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if (![scene isKindOfClass:UIWindowScene.class] ||
            ![scene.session.role isEqualToString:UIWindowSceneSessionRoleApplication]) {
            continue;
        }
        if (!found || scene.activationState == UISceneActivationStateForegroundActive) {
            found = (UIWindowScene *)scene;
        }
    }
    return found;
}

@implementation ApexNavigation

+ (void)initialize {
    if (self == ApexNavigation.class) {
        ANNNativeScreenCreators = [NSMutableDictionary new];
    }
}

// Once, on the main queue: the generated native screens before any other part, then the core's parts, the queue of the
// links, the `<Modal>` replacement before any React content, the window with the copy of the launch screen, and the
// watch of the bundles.
+ (void)bootstrapWithHost:(RCTHost *)host {
    ANNAssertMainQueue();
    if (ANNBootstrapped) {
        return;
    }
    ANNBootstrapped = YES;
    [self registerGeneratedScreens];
    ANNHost = host;
    // The links that come before React content wait for it; the launch link waits for the scene. Here, not in
    // `ANNAppDelegate`, so an app delegate of the app's own keeps its launch link.
    [ANNDeepLinkQueue start];
    // React Native lets a class with the descriptor of `ModalHostView` stand in for its own.
    [ANNModalHostViewComponentView registerForModalHostView];
    [self makeMainWindowInScene:nil];
    // Every bundle of the host, the first one too, starts the navigation of its JavaScript.
    [ANNReloadCoordinator start];
}

// The class `codegen` writes, found by its runtime name; an app without it has none.
+ (void)registerGeneratedScreens {
    Class generated = NSClassFromString(@"ApexNavigationGeneratedScreens");
    SEL registerScreens = NSSelectorFromString(@"registerScreens");
    if (![generated respondsToSelector:registerScreens]) {
        return;
    }
    IMP implementation = [generated methodForSelector:registerScreens];
    ((void (*)(id, SEL))implementation)(generated, registerScreens);
}

// The app delegate's window when it has one; else a new one, in the main scene when one is connected, else at the size
// of the device's screen (`ANNUtils.mainScreen`: `UIScreen.mainScreen` only while the window has no scene). The
// delegate keeps it when it takes a window. Its root is the copy of the launch screen. `scene` is the scene the window
// is made for (`attachToWindowScene:` before the start); nil looks for one.
+ (void)makeMainWindowInScene:(UIWindowScene *)scene {
    if (ANNMainWindow) {
        return;
    }
    id<UIApplicationDelegate> delegate = UIApplication.sharedApplication.delegate;
    UIWindow *window = [delegate respondsToSelector:@selector(window)] ? delegate.window : nil;
    if (!window) {
        scene = scene ?: ANNConnectedWindowScene();
        // `initWithFrame:` is deprecated from iOS 26 on and warns only with a deployment target from 26: without a
        // scene there is no other way to make a window before the scene connects.
        window = scene ? [[UIWindow alloc] initWithWindowScene:scene]
                       : [[UIWindow alloc] initWithFrame:ANNUtils.mainScreen.bounds];
        if ([delegate respondsToSelector:@selector(setWindow:)]) {
            delegate.window = window;
        }
    }
    ANNMainWindow = window;
    UIViewController *launchScreenCopy =
        [ANNSplashViewController launchScreenCopyWithBounds:window.bounds];
    if (launchScreenCopy) {
        window.rootViewController = launchScreenCopy;
    }
}

+ (void)registerNativeScreen:(NSString *)name creator:(ANNNativeScreenCreator)creator {
    @synchronized(ApexNavigation.class) {
#if DEBUG
        if (ANNNativeScreenCreators[name]) {
            RCTLogWarn(@"[ApexNavigation] registerNativeScreen: '%@' is registered again; "
                       @"the last one counts.",
                       name);
        }
#endif
        ANNNativeScreenCreators[name] = [creator copy];
    }
}

+ (ANNNativeScreenCreator)nativeScreenCreatorForName:(NSString *)name {
    @synchronized(ApexNavigation.class) {
        return ANNNativeScreenCreators[name];
    }
}

+ (BOOL)hasNativeScreenNamed:(NSString *)name {
    @synchronized(ApexNavigation.class) {
        return ANNNativeScreenCreators[name] != nil;
    }
}

+ (RCTHost *)getHost {
    return ANNHost;
}

// The node by its id: shown, or made and waiting to show.
+ (UIViewController *)findViewController:(NSString *)componentId {
    ANNAssertMainQueue();
    return [ANNNodeRegistry nodeForComponentId:componentId];
}

// The window keeps what it shows and moves into the scene at the scene's size; the links module knows the launch links
// after this turn of the main queue.
+ (UIWindow *)attachToWindowScene:(UIWindowScene *)windowScene {
    ANNAssertMainQueue();
    // A scene that connects before the start (an app on its own delegate) gets the window the start would make.
    [self makeMainWindowInScene:windowScene];
    ANNMainWindow.windowScene = windowScene;
    ANNMainWindow.frame = windowScene.coordinateSpace.bounds;
    [ANNMainWindow makeKeyAndVisible];
    // The tabs' preload windows, the overlay windows and the slot host's window made before the scene go where the
    // main window is.
    [ANNTabsPreloadWindow mainWindowSceneDidConnect:windowScene];
    [ANNOverlayCoordinator mainWindowSceneDidConnect:windowScene];
    [ANNSlotHost mainWindowSceneDidConnect:windowScene];
    // A direction the app set before the window had a scene takes the scene now.
    [ANNAppDirection windowSceneDidConnect];
    // A modal asked for before the window showed presents now (N2).
    [ANNPresentationGate pump];
    [ANNDeepLinkQueue windowSceneDidConnect];
    return ANNMainWindow;
}

#pragma mark - Internal

+ (UIWindow *)mainWindow {
    return ANNMainWindow;
}

// A node or a content made from now on keeps this emitter: once the JavaScript goes, it stays silent.
+ (ANNEventEmitter *)eventEmitter {
    @synchronized(ApexNavigation.class) {
        if (!ANNCurrentEventEmitter.isCurrent) {
            ANNCurrentEventEmitter = [ANNEventEmitter new];
        }
        return ANNCurrentEventEmitter;
    }
}

@end

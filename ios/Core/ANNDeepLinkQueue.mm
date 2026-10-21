#import "ANNDeepLinkQueue.h"
#import "ANNAssert.h"
#import <React/RCTLinkingManager.h>
#import <React/RCTRootView.h>
#import <UIKit/UIKit.h>

// Main queue only.
static BOOL ANNDeepLinkQueueStarted = NO;
static NSMutableArray<NSURL *> *ANNPendingDeepLinkURLs;
// React content appeared: `RCTLinkingManager` exists and JS listens to `url` events.
static BOOL ANNReactContentAppeared = NO;
// Every URL that launched the app was dispatched.
static BOOL ANNLaunchLinksKnown = NO;
static BOOL ANNLaunchLinkTaken = NO;
// A `takeLaunchLink:` that waits for the launch links. Until it gets one, the queue keeps its URLs even once React
// content appeared, so the launch link doesn't reach `Linking` first.
static ANNLaunchLinkCompletion ANNWaitingLaunchLinkCompletion;

@implementation ANNDeepLinkQueue

+ (void)initialize {
    if (self == ANNDeepLinkQueue.class) {
        ANNPendingDeepLinkURLs = [NSMutableArray new];
    }
}

+ (void)start {
    ANNAssertMainQueue();
    if (ANNDeepLinkQueueStarted) {
        return;
    }
    ANNDeepLinkQueueStarted = YES;
    // Fabric's root view posts it once a surface mounted its first content. The library is bridgeless only, so the
    // bridge's `RCTJavaScriptDidLoadNotification` never comes.
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector:@selector(reactContentDidAppear:)
                                               name:RCTContentDidAppearNotification
                                             object:nil];
    // Without the scene life cycle no scene connects: UIKit hands the launch URL to the app delegate before the app
    // becomes active.
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector:@selector(applicationDidBecomeActive:)
                                               name:UIApplicationDidBecomeActiveNotification
                                             object:nil];
}

+ (void)dispatchURL:(NSURL *)url {
    ANNAssertMainQueue();
    if (ANNReactContentAppeared && ANNWaitingLaunchLinkCompletion == nil) {
        [self openURL:url];
        return;
    }
    [ANNPendingDeepLinkURLs addObject:url];
}

+ (void)windowSceneDidConnect {
    ANNAssertMainQueue();
    // The scene delegate forwards the scene's connection options (its URLs and user activities) in the same
    // `scene:willConnectToSession:options:` that attaches the window, before or after attaching it. The next turn of
    // the main queue comes after that call, with all of them queued.
    dispatch_async(dispatch_get_main_queue(), ^{
      [self launchLinksKnown];
    });
}

+ (void)takeLaunchLink:(ANNLaunchLinkCompletion)completion {
    ANNAssertMainQueue();
    if (!ANNDeepLinkQueueStarted || ANNLaunchLinkTaken) {
        completion(nil);
        return;
    }
    if (!ANNLaunchLinksKnown) {
        // Launched in the background (a silent push, a background fetch), the app connects no scene and doesn't
        // become active until the user opens it, if ever; a wait would hold the first root as long. No link launched
        // it: one the user opens later reaches `Linking` as a `url` event, as links to a running app do. A normal
        // launch on the scene life cycle is in the background too until its scene connects, which UIKit does right
        // after `didFinishLaunching`; should JavaScript ask before that, its link comes as a `url` event as well.
        if (UIApplication.sharedApplication.applicationState == UIApplicationStateBackground) {
            ANNLaunchLinkTaken = YES;
            completion(nil);
            return;
        }
        // The newest call waits: when JavaScript reloaded while a call waited, the old JavaScript is gone.
        ANNLaunchLinkCompletion replaced = ANNWaitingLaunchLinkCompletion;
        ANNWaitingLaunchLinkCompletion = [completion copy];
        if (replaced != nil) {
            replaced(nil);
        }
        return;
    }
    [self giveLaunchLink:completion];
}

#pragma mark - private

+ (void)reactContentDidAppear:(NSNotification *)notification {
    ANNReactContentAppeared = YES;
    if (ANNWaitingLaunchLinkCompletion != nil) {
        return;
    }
    NSArray<NSURL *> *urls = [ANNPendingDeepLinkURLs copy];
    [ANNPendingDeepLinkURLs removeAllObjects];
    for (NSURL *url in urls) {
        [self openURL:url];
    }
}

+ (void)applicationDidBecomeActive:(NSNotification *)notification {
    [self launchLinksKnown];
}

+ (void)launchLinksKnown {
    if (ANNLaunchLinksKnown) {
        return;
    }
    ANNLaunchLinksKnown = YES;
    ANNLaunchLinkCompletion waiting = ANNWaitingLaunchLinkCompletion;
    ANNWaitingLaunchLinkCompletion = nil;
    if (waiting != nil) {
        [self giveLaunchLink:waiting];
    }
}

// One link opens the app. The links queued before it are dropped: opened after it, they would take the app elsewhere.
+ (void)giveLaunchLink:(ANNLaunchLinkCompletion)completion {
    ANNLaunchLinkTaken = YES;
    NSURL *url = ANNPendingDeepLinkURLs.lastObject;
    [ANNPendingDeepLinkURLs removeAllObjects];
    completion(url);
}

+ (void)openURL:(NSURL *)url {
    [RCTLinkingManager application:UIApplication.sharedApplication openURL:url options:@{}];
}

@end

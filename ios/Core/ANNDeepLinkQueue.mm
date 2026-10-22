#import "ANNDeepLinkQueue.h"
#import "ANNAssert.h"
#import "ANNEventEmitter.h"
#import "ApexNavigation+Internal.h"
#import <React/RCTLinkingManager.h>
#import <React/RCTRootView.h>
#import <UIKit/UIKit.h>

// A link on its way to `Linking`, with the scene's own context or user activity it came in when it came from a scene.
@interface ANNDeepLink : NSObject
@property(nonatomic, readonly) NSURL *url;
@property(nonatomic, readonly, nullable) UIOpenURLContext *context;
@property(nonatomic, readonly, nullable) NSUserActivity *activity;
@property(nonatomic, readonly, weak, nullable) UIScene *scene;
@end

@implementation ANNDeepLink

- (instancetype)initWithURL:(NSURL *)url
                    context:(UIOpenURLContext *)context
                   activity:(NSUserActivity *)activity
                      scene:(UIScene *)scene {
    if (self = [super init]) {
        _url = url;
        _context = context;
        _activity = activity;
        _scene = scene;
    }
    return self;
}

@end

// Main queue only.
static BOOL ANNDeepLinkQueueStarted = NO;
static NSMutableArray<ANNDeepLink *> *ANNPendingDeepLinks;
// The scene's link the scene delegate is handing to the app delegate: the app delegate dispatches its URL.
static ANNDeepLink *ANNForwardedSceneLink;
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
        ANNPendingDeepLinks = [NSMutableArray new];
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
    // The URL of the scene's link keeps its context; one the app delegate changed is a URL of its own.
    ANNDeepLink *link = [ANNForwardedSceneLink.url isEqual:url]
                            ? ANNForwardedSceneLink
                            : [[ANNDeepLink alloc] initWithURL:url context:nil activity:nil scene:nil];
    if (ANNReactContentAppeared && ANNWaitingLaunchLinkCompletion == nil) {
        [self openLink:link];
        return;
    }
    [ANNPendingDeepLinks addObject:link];
}

+ (void)forwardURLContext:(UIOpenURLContext *)context
                  ofScene:(UIScene *)scene
                  through:(NS_NOESCAPE dispatch_block_t)forward {
    [self forwardLink:[[ANNDeepLink alloc] initWithURL:context.URL context:context activity:nil scene:scene]
              through:forward];
}

+ (void)forwardUserActivity:(NSUserActivity *)activity
                    ofScene:(UIScene *)scene
                    through:(NS_NOESCAPE dispatch_block_t)forward {
    if (activity.webpageURL == nil) {
        forward();
        return;
    }
    [self forwardLink:[[ANNDeepLink alloc] initWithURL:activity.webpageURL context:nil activity:activity scene:scene]
              through:forward];
}

+ (void)openURLContext:(UIOpenURLContext *)context ofScene:(UIScene *)scene {
    [self openLink:[[ANNDeepLink alloc] initWithURL:context.URL context:context activity:nil scene:scene]];
}

+ (void)openUserActivity:(NSUserActivity *)activity ofScene:(UIScene *)scene {
    if (activity.webpageURL == nil) {
        return;
    }
    [self openLink:[[ANNDeepLink alloc] initWithURL:activity.webpageURL context:nil activity:activity scene:scene]];
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
    NSArray<ANNDeepLink *> *links = [ANNPendingDeepLinks copy];
    [ANNPendingDeepLinks removeAllObjects];
    for (ANNDeepLink *link in links) {
        [self openLink:link];
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
    NSURL *url = ANNPendingDeepLinks.lastObject.url;
    [ANNPendingDeepLinks removeAllObjects];
    completion(url);
}

+ (void)forwardLink:(ANNDeepLink *)link through:(NS_NOESCAPE dispatch_block_t)forward {
    ANNAssertMainQueue();
    ANNDeepLink *outer = ANNForwardedSceneLink;
    ANNForwardedSceneLink = link;
    forward();
    ANNForwardedSceneLink = outer;
}

// React Native 0.88 and newer take a scene's links through its scene methods: in an app whose Info.plist names a scene
// delegate, its app delegate methods take none. The earlier versions have only the app delegate methods.
+ (void)openLink:(ANNDeepLink *)link {
    UIScene *scene = link.scene;
    if (scene != nil && link.context != nil) {
        SEL openContexts = NSSelectorFromString(@"scene:openURLContexts:");
        if ([RCTLinkingManager respondsToSelector:openContexts]) {
            IMP implementation = [RCTLinkingManager methodForSelector:openContexts];
            ((void (*)(id, SEL, UIScene *, NSSet<UIOpenURLContext *> *))implementation)(
                RCTLinkingManager.class, openContexts, scene, [NSSet setWithObject:link.context]);
            return;
        }
    }
    if (scene != nil && link.activity != nil) {
        SEL continueActivity = NSSelectorFromString(@"scene:continueUserActivity:");
        if ([RCTLinkingManager respondsToSelector:continueActivity]) {
            IMP implementation = [RCTLinkingManager methodForSelector:continueActivity];
            ((void (*)(id, SEL, UIScene *, NSUserActivity *))implementation)(
                RCTLinkingManager.class, continueActivity, scene, link.activity);
            return;
        }
    }
    // A link of the app's own (`dispatchDeepLinkURL`) has no scene: on React Native 0.88 and newer, with a scene
    // delegate in Info.plist, `Linking` doesn't take it from here. JavaScript hands it to `Linking` itself.
    if (![RCTLinkingManager application:UIApplication.sharedApplication openURL:link.url options:@{}]) {
        [ApexNavigation.eventEmitter sendDeepLinkDispatched:link.url];
    }
}

@end

#import "ANNReloadCoordinator.h"
#import "ANNAssert.h"
#import "ANNErrorTexts.h"
#import "ANNHinge.h"
#import "ANNModalCoordinator.h"
#import "ANNNativeScreenCalls.h"
#import "ANNNavigationState.h"
#import "ANNNodeRegistry.h"
#import "ANNOverlayCoordinator.h"
#import "ANNPresentationGate.h"
#import "ANNPreviewInteraction.h"
#import "ANNPreviewPush.h"
#import "ANNRootCoordinator.h"
#import "ANNScreenEnvironment.h"
#import "ANNScreenReadiness.h"
#import "ANNSplashViewController.h"
#import "ANNSplitViewCommands.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTConstants.h>
#import <React/RCTLog.h>
#import <React/RCTReloadCommand.h>
#import <ReactCommon/RCTHost.h>
#import <React/RCTUtils.h>
#import <atomic>

// How long the screens of the JavaScript that went stay before they are destroyed: React Native restarts the surfaces
// of attached roots after a reload and holds them until they started; a surface released earlier is released while it
// runs.
static const NSTimeInterval ANNReleaseAfterReloadDelay = 2;

// Any thread.
static std::atomic<NSUInteger> ANNGeneration{0};
// Main queue.
// Any thread (read).
static std::atomic<NSUInteger> ANNBundleCount{0};
static BOOL ANNWatchingBundles;

@implementation ANNReloadCoordinator

+ (NSUInteger)generation {
    return ANNGeneration.load();
}

+ (NSUInteger)bundleCount {
    return ANNBundleCount.load();
}

// React Native's bridgeless instance (`RCTInstance`) posts `RCTJavaScriptDidLoadNotification` on the main queue once
// a bundle ran, the first one of the process too, with itself as the object. The host doesn't give its instance, but
// both answer the same surface presenter: a bundle of another host is not ours.
//
// A reload asked for through React Native's reload command (the dev menu, Command-R, `DevSettings.reload`) posts
// `RCTTriggerReloadCommandNotification` before the old instance goes: the generation grows there at once, so the old
// JavaScript's queued work and answers stop before the next bundle. A reload of the host from native code
// (`[RCTHost reload]`) and one from the debugger post nothing first; for them the number grows at the next bundle.
+ (void)start {
    ANNAssertMainQueue();
    if (ANNWatchingBundles) {
        return;
    }
    ANNWatchingBundles = YES;
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector:@selector(bundleDidLoad:)
                                               name:RCTJavaScriptDidLoadNotification
                                             object:nil];
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector:@selector(reloadWasAskedFor:)
                                               name:RCTTriggerReloadCommandNotification
                                             object:nil];
}

// Any thread: the reload command may come from the JavaScript's own queue.
+ (void)reloadWasAskedFor:(NSNotification *)notification {
    ANNGeneration++;
}

+ (void)bundleDidLoad:(NSNotification *)notification {
    id instance = notification.object;
    RCTExecuteOnMainQueue(^{
      RCTSurfacePresenter *ours = [ApexNavigation getHost].surfacePresenter;
      // The object is typed nowhere public: its getter is asked for by name.
      if (ours && [instance respondsToSelector:@selector(surfacePresenter)] &&
          [instance valueForKey:@"surfacePresenter"] != ours) {
          return;
      }
      [self didLoadBundle];
    });
}

// The order is the contract's: the old navigation goes, the state is forgotten, then the new JavaScript hears
// `AppLaunched` and starts its navigation.
+ (void)didLoadBundle {
    if (ANNBundleCount++ > 0) {
        // The work of the old JavaScript still queued, and its answers, do nothing from now on (again, when the reload
        // command grew the number already). The new JavaScript sends its first command after `AppLaunched` below.
        ANNGeneration++;
        [self resetAfterReload];
    }
    [ANNNavigationState reset];
    [ApexNavigation.eventEmitter sendAppLaunched];
}

+ (void)resetAfterReload {
    // The calls waiting for the old JavaScript fail, each naming itself and its screen.
    [ANNNativeScreenCalls failAllCalls:^NSString *(NSString *name, NSString *componentId) {
      return [ANNErrorTexts nativeCallReloaded:name componentId:componentId];
    }];
    // The previews go without telling the old JavaScript; their screens still run their React views. Those still
    // looking for their views stop and go the same way.
    [self releaseAfterReload:[ANNPreviewInteraction removeAllPreviewsAfterReload]];
    [self releaseAfterReload:[ANNPreviewPush takeSearchingForReload]];
    // The new JavaScript asks for the hinge again with its own hooks.
    [ANNHinge setUpdatesEnabled:NO eventEmitter:nil];
    // Before the registry is cleared: the layouts to take away are found there too.
    [self resetNavigationAfterReload];
    [ANNNodeRegistry clear];
}

// The root of the window, the modals (their coordinator's records, never a walk of UIKit's chain), the overlays
// and what the root coordinator holds (a pending root, the root a running transition replaces) are the old screens.
// Nothing of them tells the new JavaScript anything: their emitters belong to the old one. They stay 2 s and are
// destroyed then (`releaseAfterReload:`).
+ (void)resetNavigationAfterReload {
    UIWindow *window = ApexNavigation.mainWindow;
    UIViewController *root = window.rootViewController;
    NSMutableArray<UIViewController *> *oldScreens = [NSMutableArray new];
    // 1. The old screens.
    if (![ANNRootCoordinator isSplash:root]) {
        [oldScreens addObject:root];
    }
    // 2. The modals go without animation and without `ANN.ModalDismissed`: the new JavaScript never showed them. Their
    // coordinator gives them, stops their waits, takes their waiting views out of the window (step 5: they leave the
    // layers that wait to show) and forgets its list; UIKit's chain over the root is dismissed through the presentation
    // gate, after a running transition.
    [oldScreens addObjectsFromArray:[ANNModalCoordinator takeAllForReload]];
    // The pending `setRoot` of the old JavaScript gets no answer.
    [oldScreens addObjectsFromArray:[ANNRootCoordinator takeNodesForReload]];
    [self dismissPresentedOver:root];
    // 3. The overlays' windows hide at once; their screens join `oldScreens`; the main window is key and seen by
    // VoiceOver again. The previews went already (`resetAfterReload`).
    [oldScreens addObjectsFromArray:[ANNOverlayCoordinator takeAllForReload]];
    // 4. The bar components and the tabs' cells and accessories have no store to empty: each screen and each tabs node
    // keeps its own roots in its registry, so a new node never gets an old root by its id, and the old roots stop with
    // their old nodes after the hold. The loop updates are off already.
    // 5. The native screens of the old screens are silent by their emitter. The modals and overlays waiting to show
    // left what waits for them in steps 2 and 3; the screens that wait to show in a split view's column leave it here,
    // without an answer.
    [oldScreens addObjectsFromArray:[ANNSplitViewCommands takeAllForReload]];
    // The new JavaScript counts its ids anew: nothing the safe area views of the old screens said stays ready for a new
    // screen with the same id. The old surfaces are stopped or silent. The same for what the old screens kept for
    // JavaScript, their environment: insets and padding, regions and arrangements (an old screen still in a window
    // writes no insets or regions again: its emitter is not current).
    [ANNScreenReadiness reset];
    [ANNScreenEnvironment removeAll];
    // 6. The window shows the blank screen, which counts as the splash: a reload is not a launch. The
    // reset runs from the second bundle of the process on.
    if (window) {
        window.rootViewController = [ANNSplashViewController blankScreen];
    }
    // 7.
    [self releaseAfterReload:oldScreens];
}

// UIKit drops a dismissal asked for while a presentation or dismissal runs: the old chain goes as a step of the
// presentation gate, after what runs, from the old root (no longer the window's once the blank screen replaced it).
+ (void)dismissPresentedOver:(UIViewController *)root {
    if (!root.presentedViewController) {
        return;
    }
    ANNGateStep *step = [[ANNGateStep alloc] initWithRun:^(dispatch_block_t done) {
      if (!root.presentedViewController) {
          done();
          return;
      }
      [root dismissViewControllerAnimated:NO completion:done];
    }];
    step.transitionRoot = ^UIViewController * {
      return root;
    };
    [ANNPresentationGate enqueue:step];
}

// The screens stay until React Native restarted their surfaces in the new runtime, then each is destroyed on the main
// queue: its surface stops, and its events go nowhere (its emitter is of the old JavaScript).
+ (void)releaseAfterReload:(NSArray<UIViewController *> *)screens {
    if (screens.count == 0) {
        return;
    }
    int64_t delay = (int64_t)(ANNReleaseAfterReloadDelay * NSEC_PER_SEC);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
      for (UIViewController *screen in screens) {
          @try {
              [screen destroy];
          } @catch (NSException *exception) {
              RCTLogWarn(@"[ApexNavigation] could not destroy a screen of the JavaScript that "
                         @"reloaded: %@",
                         exception.reason ?: exception.name);
          }
      }
    });
}

@end

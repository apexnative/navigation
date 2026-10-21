#import "ANNRootCoordinator.h"
#import "ANNAnimationRules.h"
#import "ANNAppDirection.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNComponentViewController.h"
#import "ANNErrorTexts.h"
#import "ANNFrameDriver.h"
#import "ANNLayoutFactory.h"
#import "ANNLayoutTreeOptions.h"
#import "ANNLayoutValidator.h"
#import "ANNModalCoordinator.h"
#import "ANNNavigationState.h"
#import "ANNNodeRegistry.h"
#import "ANNOverlayCoordinator.h"
#import "ANNParking.h"
#import "ANNPendingLayers.h"
#import "ANNPresentationGate.h"
#import "ANNRenderWait.h"
#import "ANNSplashViewController.h"
#import "ANNSystemUI.h"
#import "ANNViewTracks.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"

// How long a root waits for its screens to render when `animations.setRoot.enter.waitForRender` is not set: from the
// start of the wait, for all of them together.
static const NSTimeInterval ANNRootRenderLimit = 0.5;

// The default replacement of a root over another root: a snapshot of the window as it was dissolves over the new one.
static const NSTimeInterval ANNRootDissolveDuration = 0.3;

typedef NS_ENUM(NSInteger, ANNRootShowState) {
    // The root is made and renders; it waits for its screens, then for its turn at the presentation gate.
    ANNRootShowStateWaiting,
    // The root is the window's root; the root it replaced still goes (the dissolve). A custom animation is in the
    // layers' state from the replace on.
    ANNRootShowStateTransition,
    // The root is up; the modals, then the overlays of its layout show one by one before the answer. A newer `setRoot`
    // then rejects this one: the one waiting now never shows, the rest are never made, those shown stay over this root,
    // which stays the current one.
    ANNRootShowStateLayers,
    // Shown, superseded, failed, or taken by a reload: nothing more happens to it.
    ANNRootShowStateEnded,
};

// One `setRoot` on its way: its root, its answer and how far it got.
@interface ANNRootShow : NSObject
@property(nonatomic, strong) UIViewController *root;
@property(nonatomic, copy) NSString *rootId;
@property(nonatomic, strong) ANNCommandAnswer *answer;
@property(nonatomic) ANNRootShowState state;
// The layout and the options of all its trees (step 2): its modals and overlays are made from them after the root.
@property(nonatomic, strong) ANNLayoutRoot *layout;
@property(nonatomic, copy) NSDictionary<NSString *, ANNNavigationOptions *> *optionsById;
// The modal or the overlay of the layout that shows now, and its place in the chain.
@property(nonatomic, strong, nullable) ANNModal *chainModal;
@property(nonatomic, strong, nullable) ANNOverlay *chainOverlay;
@property(nonatomic) NSUInteger chainIndex;
// The chain holds the coalesced state updates: one state goes, before the answer.
@property(nonatomic) BOOL holdsState;
// The render wait (`waitForRenderOf:`): the root's waited screens, and their bar components within the root's limit
// when `waitForRender` is unset, or with `true` as a phase after the content with its own 0.5 s.
@property(nonatomic, strong, nullable) ANNRenderWait *renderWait;
// Its replacement waits for its turn at the gate: a presentation running in the window ends first (patch 618).
@property(nonatomic, strong, nullable) ANNGateStep *gateStep;
// The root the transition replaces, with the library modals over it: they go at its end.
@property(nonatomic, copy) NSArray<UIViewController *> *replaced;
// The nodes whose appearance the transition reports itself (`driveAppearanceOf:`), while `driving`.
@property(nonatomic, copy) NSArray<UIViewController *> *driven;
@property(nonatomic) BOOL driving;
@property(nonatomic, strong, nullable) UIView *snapshot;
// The root is the window's root: the old root's modals went before it (`startTransitionOf:done:`).
@property(nonatomic) BOOL installed;
// The root's view waits in the window under the current root (`park:`).
@property(nonatomic, strong, readonly) ANNParking *parking;
// Its own animation: `enter` on the new root's view and `exit` on the snapshot under it, by one frame driver; the
// answer waits for its end and for the layout's modals and overlays.
@property(nonatomic) BOOL custom;
@property(nonatomic, strong, nullable) ANNFrameDriver *driver;
@property(nonatomic, copy, nullable) NSArray<ANNViewTracks *> *tracks;
@property(nonatomic) BOOL animating;
@property(nonatomic) BOOL layersShown;
@end

@implementation ANNRootShow

- (instancetype)init {
    if (self = [super init]) {
        _parking = [ANNParking new];
    }
    return self;
}

@end

// Main queue.
static ANNRootShow *ANNPendingShow;
// The show whose own animation runs on the main window's root now.
static __weak ANNRootShow *ANNAnimatingShow;

@implementation ANNRootCoordinator

#pragma mark - setRoot

+ (void)setRoot:(ANNLayoutRoot *)layout answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIWindow *window = ApexNavigation.mainWindow;

    // Step 1, the direction of the app (`layout.direction` of the default options): React Native's at once, the scene's
    // once the window has one.
    [ANNAppDirection applyDefaultOptionsDirection];

    // Step 2: every value of every tree, before any view.
    NSString *rejection = nil;
    NSDictionary<NSString *, ANNNavigationOptions *> *optionsById =
        [ANNLayoutTreeOptions optionsOfTrees:layout.trees rejection:&rejection];
    if (!optionsById) {
        [answer reject:rejection];
        return;
    }

    // Step 3: an id of the modals or overlays that names a node which shows and stays. The modals go with the old root
    // (their ids count as shown, but they go); only the overlays stay, so a shown node is allowed when it goes with the
    // old root, or when it is in the shown overlay a layout's overlay replaces (N5).
    for (ANNLayoutNode *tree in [layout.modals arrayByAddingObjectsFromArray:layout.overlays]) {
        BOOL replacesOverlay = [layout.overlays containsObject:tree];
        NSString *treeId = tree.nodeId;
        BOOL (^goesWithOldRoot)(ANNLayoutNode *) = ^BOOL(ANNLayoutNode *node) {
          UIViewController *shown = [ANNNodeRegistry nodeForComponentId:node.nodeId];
          NSString *overlayId = [ANNOverlayCoordinator overlayRootIdOf:shown];
          return !overlayId || (replacesOverlay && [overlayId isEqualToString:treeId]);
        };
        NSString *shownId = [ANNLayoutValidator firstShownIdIn:tree allowed:goesWithOldRoot];
        if (shownId) {
            [answer reject:[ANNErrorTexts alreadyShown:shownId]];
            return;
        }
    }

    // Step 4: what the core makes, in every tree of the layout: a modal or an overlay of a type without a maker rejects
    // the whole `setRoot`, as the root's type does. Every type has one now: the guard stays.
    if (![ANNLayoutFactory canMakeTrees:layout.trees]) {
        [answer reject:[ANNErrorTexts notDoneYet:@"setRoot"]];
        return;
    }

    // Step 5: last wins.
    [self supersedePending];

    // Step 6: the root renders at once, at the window's size, so its surfaces start with their size. A maker that fails
    // (a native screen not created) restores the modals too.
    UIViewController *root = nil;
    @try {
        root = [ANNLayoutFactory nodeFromLayout:layout.root options:optionsById];
        [root render];
        root.view.frame = window.bounds;
        [root.view setNeedsLayout];
        [root.view layoutIfNeeded];
    } @catch (NSException *exception) {
        [root destroy];
        // A superseded `setRoot` handed the old root's modals to this one: nothing takes them now.
        [ANNModalCoordinator restoreTakenForNewRoot];
        @throw;
    }
    ANNRootShow *show = [ANNRootShow new];
    show.root = root;
    show.rootId = layout.root.nodeId;
    show.answer = answer;
    show.layout = layout;
    show.optionsById = optionsById;
    show.state = ANNRootShowStateWaiting;
    ANNPendingShow = show;
    // Accepted (`setRoot` step 3): the old root's modals go with it; commands by their ids find nothing.
    [ANNModalCoordinator takeForNewRoot];
    [self park:show];

    // Step 7. The show is pending and parked already: a failure at the start of its wait goes through its guard.
    [self protect:show
             step:^{
               [ANNRootCoordinator waitForRenderOf:show];
             }];
}

#pragma mark - The protection of the steps that come later

// The steps that run after the command's body (a ready callback, the limit, the turn at the gate, the end of the
// dissolve, the chain of the layout's modals) are outside the runner's protection: a failure there rejects the
// `setRoot` with the runner's text, and leaves neither a pending show nor a driven node behind. A root that never
// became the window's root is destroyed, as a failed modal's or overlay's.
+ (void)protect:(ANNRootShow *)show step:(dispatch_block_t)step {
    @try {
        step();
    } @catch (NSException *exception) {
        BOOL neverInstalled =
            show.state == ANNRootShowStateWaiting ||
            (show.state == ANNRootShowStateTransition && !show.installed &&
             ApexNavigation.mainWindow.rootViewController != show.root);
        if (ANNPendingShow == show) {
            ANNPendingShow = nil;
        }
        if (show.state == ANNRootShowStateWaiting) {
            [self stopWaitingOf:show];
            // The `setRoot` ends without replacing the root.
            [ANNModalCoordinator restoreTakenForNewRoot];
        }
        [self stopLayersOf:show];
        [self unpark:show];
        if (neverInstalled) {
            if (show.state == ANNRootShowStateTransition) {
                // The old root's modals were taken out of their records at the replacement: they go, as when a
                // newer `setRoot` supersedes this one there; the old root stays the window's root.
                NSMutableArray<UIViewController *> *modals = [show.replaced mutableCopy];
                [modals removeObjectIdenticalTo:ApexNavigation.mainWindow.rootViewController];
                [self destroyLayers:modals];
            }
            [show.root destroy];
        }
        show.state = ANNRootShowStateEnded;
        [self stopAnimationOf:show];
        [show.snapshot.layer removeAllAnimations];
        [show.snapshot removeFromSuperview];
        show.snapshot = nil;
        [self stopDriving:show];
        NSString *what = [NSString stringWithFormat:@"show the root '%@'", show.rootId];
        NSString *reason = exception.reason.length > 0 ? exception.reason : exception.name;
        [show.answer reject:[ANNErrorTexts couldNot:what reason:reason]];
    }
}

+ (void)stopDriving:(ANNRootShow *)show {
    if (show.driving) {
        show.driving = NO;
        [ANNAppearanceDispatcher stopDrivingAppearanceOf:show.driven];
    }
}

#pragma mark - Where the root waits

// The screens of a waiting root render only once their safe area views are laid out in a window: the root's view waits
// in the window, at its size, under the current root's view (`ANNParking`). The command drives its appearance
// meanwhile. From here until it shows or goes, the root is a layer that waits to show (`ANNPendingLayers`): a stack in
// it counts as off screen, and a stack command in it tells the wait. Nothing waits longer than before: the wait is the
// command's own.
+ (void)park:(ANNRootShow *)show {
    __weak ANNRootShow *weakShow = show;
    [ANNPendingLayers add:show.root
        onShownScreensChanged:^{
          [ANNRootCoordinator shownScreensChangedOf:weakShow];
        }];
    UIWindow *window = ApexNavigation.mainWindow;
    if (!window) {
        return;
    }
    [show.parking park:show.root inView:window];
}

// The root's view leaves the window as it was: before the root becomes the window's root, and on every path that ends
// a waiting root (superseded, destroyed, a reload, a failure). The root becomes the window's root in the same turn, so
// UIKit's departure ends here (`ANNParking unpark:endingDeparture:`): a stack UIKit took for disappeared would give the
// screens it pushes neither their appearance nor their safe area (their content under the bar).
+ (void)unpark:(ANNRootShow *)show {
    [ANNPendingLayers remove:show.root];
    [show.parking unpark:show.root endingDeparture:YES];
}

#pragma mark - The wait

// `waitForRender: false` waits for nothing; `true` waits for every screen the root waits for as long as it takes, then
// for their bar components at most 0.5 s; unset, for the screens and their bar components together at most
// `ANNRootRenderLimit` from the start, then the screens still waited count as rendered (`markReady`). A screen whose
// content is ready already counts at once. The rule for content of unknown readiness is off for a root.
+ (void)waitForRenderOf:(ANNRootShow *)show {
    ANNNavigationOptions *resolved = [show.root resolveOptionsWithDefault];
    Bool *waitForRender = resolved.animations.setRoot.enter.waitForRender;
    if (waitForRender.hasValue && !waitForRender.get) {
        [self renderWaitEnded:show];
        return;
    }
    __weak ANNRootShow *weakShow = show;
    BOOL contentFirst = waitForRender.hasValue;
    // Within the root's limit the bar components have no limits of their own; their phase has its own 0.5 s.
    ANNBarWaitLimits withContent = {0, 0};
    ANNBarWaitLimits ownPhase = {ANNRootRenderLimit, ANNRootRenderLimit};
    ANNRenderWait *wait = [[ANNRenderWait alloc]
        initWithContent:ANNRenderWaitContentEachScreen
                   node:nil
                screens:^NSArray<UIViewController *> * {
                  return weakShow.root.waitedComponentViewControllers ?: @[];
                }
                  limit:contentFirst ? 0 : ANNRootRenderLimit
                    bar:contentFirst ? ANNRenderWaitBarAfterContent : ANNRenderWaitBarWithContent
              barLimits:contentFirst ? ownPhase : withContent];
    // A screen the user won't see at once (an unselected tab's) never holds the root longer than the limit, also with
    // `waitForRender: true`.
    wait.mountLimit = ANNRootRenderLimit;
    wait.protect = ^(dispatch_block_t step) {
      [ANNRootCoordinator protect:weakShow step:step];
    };
    wait.ended = ^{
      ANNRootShow *rendered = weakShow;
      if (rendered) {
          [ANNRootCoordinator renderWaitEnded:rendered];
      }
    };
    show.renderWait = wait;
    [wait start];
}

// A stack command inside the waiting root changed its top: the screen the wait held may be gone (destroyed, its
// callback never comes), and the new top is what the root will show. The wait takes the root's waited screens again
// with the same limit; in its bar phase it waits for the new top's bar components, with their own limit again.
+ (void)shownScreensChangedOf:(ANNRootShow *)show {
    ANNAssertMainQueue();
    if (!show || show != ANNPendingShow || show.state != ANNRootShowStateWaiting ||
        !show.renderWait.isWaiting) {
        return;
    }
    [self protect:show
             step:^{
               [show.renderWait retakeScreens:!show.root.nodeHelper.destroyed];
             }];
}

// The wait ends: its callbacks, its limit and its turn at the gate do nothing from now on.
+ (void)stopWaitingOf:(ANNRootShow *)show {
    [show.renderWait cancel];
    if (show.gateStep) {
        [ANNPresentationGate drop:show.gateStep];
        show.gateStep = nil;
    }
}

// The root rendered (or its limit passed). It goes up only for the JavaScript that sent it: a wait that ends after a
// reload leaves the root to the reset. Then it waits for its turn at the presentation gate: a presentation or a
// dismissal running in the window ends first, as do the modals' steps asked before it (UIKit would end them on the root
// that goes). No timed wait.
+ (void)renderWaitEnded:(ANNRootShow *)show {
    if (show != ANNPendingShow || show.state != ANNRootShowStateWaiting || !show.answer.isCurrent) {
        return;
    }
    if (show.root.nodeHelper.destroyed) {
        ANNPendingShow = nil;
        show.state = ANNRootShowStateEnded;
        [self unpark:show];
        // The old root's modals are what they were.
        [ANNModalCoordinator restoreTakenForNewRoot];
        [show.answer reject:[ANNErrorTexts rootDestroyed:show.rootId]];
        return;
    }
    if (show.gateStep) {
        return;
    }
    __weak ANNRootShow *weakShow = show;
    ANNGateStep *step = [[ANNGateStep alloc] initWithRun:^(dispatch_block_t done) {
      ANNRootShow *waiting = weakShow;
      if (!waiting || waiting != ANNPendingShow || waiting.state != ANNRootShowStateWaiting ||
          !waiting.answer.isCurrent) {
          done();
          return;
      }
      waiting.gateStep = nil;
      [ANNRootCoordinator protect:waiting
                             step:^{
                               [ANNRootCoordinator startTransitionOf:waiting done:done];
                             }];
      // A step that failed leaves the gate free (`done` runs once).
      if (waiting.state != ANNRootShowStateTransition) {
          done();
      }
    }];
    show.gateStep = step;
    [ANNPresentationGate enqueue:step];
}

+ (id<UIViewControllerTransitionCoordinator>)runningPresentationTransitionOver:
    (UIViewController *)root {
    for (UIViewController *presented = root.presentedViewController; presented;
         presented = presented.presentedViewController) {
        if (presented.isBeingPresented || presented.isBeingDismissed) {
            id<UIViewControllerTransitionCoordinator> coordinator = presented.transitionCoordinator;
            if (coordinator) {
                return coordinator;
            }
        }
    }
    return nil;
}

#pragma mark - The transition

// The old root and the modals over it go in the root's transition (the list of modals is reset here, over the splash
// too). UIKit doesn't close the modals of a root it no longer shows: they are dismissed without animation now, under
// the window's snapshot, before the new root is the window's. The gate is free once the new root is in place.
+ (void)startTransitionOf:(ANNRootShow *)show done:(dispatch_block_t)done {
    UIWindow *window = ApexNavigation.mainWindow;
    UIViewController *old = window.rootViewController;
    BOOL overSplash = [self isSplash:old];
    NSMutableArray<UIViewController *> *replaced = [NSMutableArray new];
    if (!overSplash && old.isLayoutNode) {
        [replaced addObject:old];
    }
    [replaced addObjectsFromArray:[ANNModalCoordinator takeAtReplacement]];
    show.replaced = replaced;
    show.driven = [show.replaced arrayByAddingObject:show.root];

    ANNEnterExitScreenAnimationOptions *animation =
        [show.root resolveOptionsWithDefault].animations.setRoot;
    BOOL enterDisabled = [ANNAnimationRules sideIsDisabled:animation.enter];
    // Its own `enter` / `exit` plays over the snapshot of the window, also over the splash (a side with `enabled:
    // false` doesn't play). In a window nobody sees (hidden, no scene yet, or in the background) the root replaces at
    // once, as it does over the splash without its own (patch 616).
    BOOL custom = [ANNAnimationRules rootPlays:animation];
    UIWindowScene *scene = window.windowScene;
    BOOL seen = !window.hidden && scene != nil &&
                scene.activationState != UISceneActivationStateBackground;
    BOOL dissolves = !overSplash && !enterDisabled && !custom && seen;
    show.custom = custom && seen;
    UIView *snapshot =
        dissolves || show.custom ? [window snapshotViewAfterScreenUpdates:NO] : nil;

    show.state = ANNRootShowStateTransition;
    // It shows from here: a stack in it counts as on screen.
    [ANNPendingLayers remove:show.root];
    // The old root's screens let go of the keyboard as their transition starts, before their destroy
    // (`blurOnUnmount`): it doesn't hang over the transition.
    for (UIViewController *layer in show.replaced) {
        [ANNComponentViewController nodeWillLeave:layer];
    }
    // UIKit calls the appearance of the old and the new root while the root is replaced, or on the next turn: the
    // command reports them in its order instead.
    show.driving = YES;
    [ANNAppearanceDispatcher driveAppearanceOf:show.driven];
    [ANNAppearanceDispatcher beginTransitionShowing:@[ show.root ]];
    if (snapshot) {
        // The snapshot takes the touches while it fades: a tap on the old screen doesn't reach the new one.
        snapshot.frame = window.bounds;
        snapshot.userInteractionEnabled = YES;
        [window addSubview:snapshot];
        show.snapshot = snapshot;
    }
    __weak ANNRootShow *weakShow = show;
    void (^replace)(void) = ^{
      ANNRootShow *replacing = weakShow;
      if (replacing && replacing.state == ANNRootShowStateTransition) {
          [ANNRootCoordinator protect:replacing
                                 step:^{
                                   [ANNRootCoordinator replaceRootOf:replacing];
                                 }];
      }
      done();
    };
    if (old.presentedViewController) {
        [old dismissViewControllerAnimated:NO
                                completion:^{
                                  replace();
                                }];
    } else {
        replace();
    }
}

// The new root becomes the window's root; the snapshot of the old one dissolves over it, or it shows at once.
+ (void)replaceRootOf:(ANNRootShow *)show {
    UIWindow *window = ApexNavigation.mainWindow;
    UIView *snapshot = show.snapshot;
    [self unpark:show];
    window.rootViewController = show.root;
    show.installed = YES;
    // The new root's system UI; with its own animation the orientation waits for the end.
    [ANNSystemUI setNeedsUpdate];
    if (show.custom) {
        if (show.answer.isCurrent) {
            [self startAnimationOf:show];
            return;
        }
        // The JavaScript went meanwhile: nothing plays, the reset takes what is left.
        [snapshot removeFromSuperview];
        show.snapshot = nil;
        [self endTransitionOf:show];
        return;
    }
    if (!snapshot) {
        [self endTransitionOf:show];
        return;
    }
    [window bringSubviewToFront:snapshot];
    __weak ANNRootShow *weakShow = show;
    [UIView animateWithDuration:ANNRootDissolveDuration
        delay:0
        options:UIViewAnimationOptionCurveEaseInOut
        animations:^{
          snapshot.alpha = 0;
        }
        completion:^(BOOL finished) {
          ANNRootShow *dissolved = weakShow;
          if (!dissolved) {
              return;
          }
          [ANNRootCoordinator protect:dissolved
                                 step:^{
                                   [ANNRootCoordinator endTransitionOf:dissolved];
                                 }];
        }];
}

#pragma mark - Its own animation

// The new root is the window's root at once with the snapshot of the old window under it; `enter` moves the new root's
// view, `exit` the snapshot. The old root goes and the new one appears now (their `DidDisappear` and `DidAppear` before
// the layout's modals), and the modals and overlays of the layout show now, during the animation; the state and the
// answer wait until both the animation and the layers ended.
+ (void)startAnimationOf:(ANNRootShow *)show {
    UIWindow *window = ApexNavigation.mainWindow;
    UIView *snapshot = show.snapshot;
    UIView *rootView = show.root.view;
    if (snapshot && rootView.superview == window) {
        [window insertSubview:snapshot belowSubview:rootView];
    } else if (snapshot) {
        [window insertSubview:snapshot atIndex:0];
    }
    ANNEnterExitScreenAnimationOptions *animation =
        [show.root resolveOptionsWithDefault].animations.setRoot;
    NSMutableArray<ANNViewTracks *> *tracks = [NSMutableArray new];
    ANNViewAnimationOptions *enter = [ANNAnimationRules playingSide:animation.enter];
    ANNViewAnimationOptions *exit = [ANNAnimationRules playingSide:animation.exit];
    if (enter && rootView) {
        [tracks addObject:[[ANNViewTracks alloc] initWithView:rootView
                                                         side:enter
                                                  alphaBefore:rootView.alpha]];
    }
    if (exit && snapshot) {
        [tracks addObject:[[ANNViewTracks alloc] initWithView:snapshot
                                                         side:exit
                                                  alphaBefore:snapshot.alpha]];
    }
    NSTimeInterval length =
        MAX([ANNAnimationRules lengthOfSide:enter], [ANNAnimationRules lengthOfSide:exit]);
    show.tracks = tracks;
    show.animating = YES;
    ANNAnimatingShow = show;
    show.driver = [[ANNFrameDriver alloc] initWithClients:tracks length:length];
    // The layers' state from here on: the chain may start and a newer `setRoot` stops it.
    show.state = ANNRootShowStateLayers;
    [self holdStateFor:show];
    @try {
        [self destroyLayers:show.replaced];
        [ANNAppearanceDispatcher endSwitchHiding:@[] showing:@[ show.root ] events:nil];
    } @finally {
        [self stopDriving:show];
    }
    // The old root is let go now, with its stopped surfaces: held through the animation, they would be restarted by
    // React Native at a reload and released while they run.
    show.replaced = @[];
    show.driven = @[ show.root ];
    __weak ANNRootShow *weakShow = show;
    [show.driver startWithDone:^{
      ANNRootShow *animated = weakShow;
      if (animated) {
          [ANNRootCoordinator protect:animated
                                 step:^{
                                   [ANNRootCoordinator animationEndedOf:animated];
                                 }];
      }
    }];
    [self showLayerOf:show index:0];
}

+ (void)animationEndedOf:(ANNRootShow *)show {
    if (!show.animating) {
        return;
    }
    [self stopAnimationOf:show];
    [self finishLayersOf:show];
}

// The animation stops at its end state (its end, a newer `setRoot`, a reload, a failure): the views get back what they
// had and the snapshot goes.
+ (void)stopAnimationOf:(ANNRootShow *)show {
    if (!show.animating) {
        return;
    }
    show.animating = NO;
    [show.driver cancel];
    show.driver = nil;
    for (ANNViewTracks *tracks in show.tracks) {
        [tracks restore];
    }
    show.tracks = nil;
    // UIKit works out the safe area of the window's root view from where the view is drawn: moved away from the top
    // edge by `enter` (from the bottom, scaled down) it has no top inset, and putting the transform back doesn't make
    // UIKit work it out again. A layout now gives the root, its bars and its screens the window's safe area back.
    UIView *rootView = show.root.viewIfLoaded;
    [rootView setNeedsLayout];
    [rootView layoutIfNeeded];
    [show.snapshot removeFromSuperview];
    show.snapshot = nil;
    if (ANNAnimatingShow == show) {
        ANNAnimatingShow = nil;
    }
    // The orientation the owner held during the animation is asked now (`layout.orientation`: after the transition).
    [ANNSystemUI setNeedsUpdateOfGroups:ANNSystemUIGroupOrientation];
}

+ (BOOL)rootAnimationRuns {
    ANNRootShow *show = ANNAnimatingShow;
    return show != nil && show.animating;
}

// The end of the transition: every screen of the old root and of the library modals over it that showed sends
// `ComponentDidDisappear`, then each layer is destroyed (it leaves the registry, its content stops,
// `NativeScreenReleased` / `SlotDestroyed`; no `ModalDismissed`), then `ComponentDidAppear` of the new root, the state,
// the promise with the root's id, `CommandCompleted`. A layout with modals or overlays shows them first: the state and
// the answer come once all of them showed.
//
// A dissolve that ends after the JavaScript went (a reload asked for, its bundle not loaded yet) destroys nothing: the
// show stays pending, and the reset takes the replaced root into its hold, so React Native's restart of their surfaces
// is never cut.
+ (void)endTransitionOf:(ANNRootShow *)show {
    if (show.state != ANNRootShowStateTransition) {
        return;
    }
    [show.snapshot removeFromSuperview];
    show.snapshot = nil;
    if (!show.answer.isCurrent) {
        return;
    }
    BOOL layers = show.layout.modals.count > 0 || show.layout.overlays.count > 0;
    show.state = layers ? ANNRootShowStateLayers : ANNRootShowStateEnded;
    if (layers) {
        [self holdStateFor:show];
    }
    if (!layers && ANNPendingShow == show) {
        ANNPendingShow = nil;
    }
    @try {
        [self destroyLayers:show.replaced];
        if (layers) {
            [ANNAppearanceDispatcher endSwitchHiding:@[] showing:@[ show.root ] events:nil];
        } else {
            [ANNAppearanceDispatcher endTransitionHiding:@[]
                                                 showing:@[ show.root ]
                                                  events:nil
                                                  answer:show.answer
                                                   value:show.rootId];
        }
    } @finally {
        [self stopDriving:show];
    }
    // As in `startAnimationOf:`: the old root is let go now, not once the layers showed.
    show.replaced = @[];
    show.driven = @[ show.root ];
    if (layers) {
        // After the dissolve, so that they don't come over the snapshot.
        [self showLayerOf:show index:0];
    }
}

+ (void)holdStateFor:(ANNRootShow *)show {
    if (!show.holdsState) {
        show.holdsState = YES;
        [ANNNavigationState holdUpdates];
    }
}

+ (void)releaseStateOf:(ANNRootShow *)show {
    if (show.holdsState) {
        show.holdsState = NO;
        [ANNNavigationState releaseUpdates];
    }
}

// Every `DidDisappear` of the layers before any of them is destroyed (the events of the content that stops come after
// all of them).
+ (void)destroyLayers:(NSArray<UIViewController *> *)layers {
    for (UIViewController *layer in layers) {
        [ANNAppearanceDispatcher nodeDidDisappear:layer];
    }
    for (UIViewController *layer in layers) {
        [layer destroy];
    }
}

#pragma mark - The modals and overlays of the layout

// One by one, bottom up: each modal is made now, waits and presents through the gate, without animation but the last,
// which has its `animations.showModal`; then each overlay. A failure of one is logged and skipped.
+ (void)showLayerOf:(ANNRootShow *)show index:(NSUInteger)index {
    if (show != ANNPendingShow || show.state != ANNRootShowStateLayers || !show.answer.isCurrent) {
        return;
    }
    show.chainIndex = index;
    show.chainModal = nil;
    show.chainOverlay = nil;
    NSArray<ANNLayoutNode *> *modals = show.layout.modals;
    NSArray<ANNLayoutNode *> *overlays = show.layout.overlays;
    __weak ANNRootShow *weakShow = show;
    void (^next)(BOOL) = ^(BOOL shown) {
      ANNRootShow *showing = weakShow;
      if (showing && showing.chainIndex == index) {
          [ANNRootCoordinator protect:showing
                                 step:^{
                                   [ANNRootCoordinator showLayerOf:showing index:index + 1];
                                 }];
      }
    };
    if (index < modals.count) {
        BOOL last = index + 1 == modals.count;
        ANNModal *modal = [ANNModalCoordinator showChainModal:modals[index]
                                                      options:show.optionsById
                                                     animated:last
                                                        ended:next];
        if (show.chainIndex == index) {
            show.chainModal = modal;
        }
        return;
    }
    NSUInteger overlayIndex = index - modals.count;
    if (overlayIndex < overlays.count) {
        ANNOverlay *overlay = [ANNOverlayCoordinator showChainOverlay:overlays[overlayIndex]
                                                              options:show.optionsById
                                                                ended:next];
        if (show.chainIndex == index) {
            show.chainOverlay = overlay;
        }
        return;
    }
    show.layersShown = YES;
    [self finishLayersOf:show];
}

// The state and the answer once the layers showed and, with its own animation, the animation ended.
+ (void)finishLayersOf:(ANNRootShow *)show {
    if (show != ANNPendingShow || show.state != ANNRootShowStateLayers || !show.layersShown ||
        show.animating) {
        return;
    }
    show.state = ANNRootShowStateEnded;
    ANNPendingShow = nil;
    [self releaseStateOf:show];
    [show.answer resolve:show.rootId];
}

// The chain stops: the modal or the overlay waiting now never shows, the rest are never made.
+ (void)stopLayersOf:(ANNRootShow *)show {
    ANNModal *modal = show.chainModal;
    ANNOverlay *overlay = show.chainOverlay;
    show.chainIndex = NSNotFound;
    show.chainModal = nil;
    show.chainOverlay = nil;
    [self releaseStateOf:show];
    if (modal) {
        [ANNModalCoordinator cancelChainModal:modal];
    }
    if (overlay) {
        [ANNOverlayCoordinator cancelChainOverlay:overlay];
    }
}

#pragma mark - Last wins

// A pending root that waits is destroyed now: it never appeared, so nothing is sent for it; the old root's modals it
// took stay taken, by the newer `setRoot`. A pending root in its transition ends it at once, without its `DidAppear`
// and its answer: the root it replaced goes now, and it stays as the current root until the new one replaces it (its
// screens whose `WillAppear` went get their `DidDisappear` then). A pending root that shows the modals and overlays of
// its layout stays with those shown; the one waiting is cancelled, the rest are never made.
+ (void)supersedePending {
    ANNRootShow *pending = ANNPendingShow;
    if (!pending) {
        return;
    }
    ANNPendingShow = nil;
    ANNRootShowState state = pending.state;
    pending.state = ANNRootShowStateEnded;
    if (state == ANNRootShowStateWaiting) {
        [self stopWaitingOf:pending];
        [self unpark:pending];
        [pending.root destroy];
    } else if (state == ANNRootShowStateTransition) {
        [pending.snapshot.layer removeAllAnimations];
        [pending.snapshot removeFromSuperview];
        pending.snapshot = nil;
        @try {
            if (pending.installed) {
                [self destroyLayers:pending.replaced];
            } else {
                // Superseded while the old root's modals went, before it became the window's root: it never shows,
                // and the old root stays the window's root (the newer `setRoot` replaces it); its modals go.
                UIViewController *current = ApexNavigation.mainWindow.rootViewController;
                NSMutableArray<UIViewController *> *modals = [pending.replaced mutableCopy];
                [modals removeObjectIdenticalTo:current];
                [self destroyLayers:modals];
                [self unpark:pending];
                [pending.root destroy];
            }
        } @finally {
            [self stopDriving:pending];
        }
    } else if (state == ANNRootShowStateLayers) {
        // Its own animation ends at its end state; the layers shown stay.
        [self stopAnimationOf:pending];
        [self stopLayersOf:pending];
    }
    [pending.answer reject:[ANNErrorTexts setRootReplaced:pending.rootId]];
}

#pragma mark - The splash, the reload

+ (BOOL)isSplash:(UIViewController *)viewController {
    return !viewController || [viewController isKindOfClass:ANNSplashViewController.class];
}

+ (NSArray<UIViewController *> *)takeNodesForReload {
    ANNAssertMainQueue();
    ANNRootShow *pending = ANNPendingShow;
    ANNPendingShow = nil;
    if (!pending) {
        return @[];
    }
    ANNRootShowState state = pending.state;
    pending.state = ANNRootShowStateEnded;
    if (state == ANNRootShowStateWaiting) {
        [self stopWaitingOf:pending];
        [self unpark:pending];
        return @[ pending.root ];
    }
    if (state == ANNRootShowStateTransition) {
        [pending.snapshot.layer removeAllAnimations];
        [pending.snapshot removeFromSuperview];
        pending.snapshot = nil;
        [self stopDriving:pending];
        if (!pending.installed) {
            [self unpark:pending];
            return [pending.replaced arrayByAddingObject:pending.root];
        }
        return pending.replaced;
    }
    // The modals and overlays of its chain go with the coordinators' own lists (N4: no answer).
    [self stopAnimationOf:pending];
    pending.chainIndex = NSNotFound;
    pending.chainModal = nil;
    pending.chainOverlay = nil;
    [self releaseStateOf:pending];
    return @[];
}

@end

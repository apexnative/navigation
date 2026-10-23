#import "ANNModalCoordinator.h"
#import "ANNAnimationRules.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNCommandRunner.h"
#import "ANNComponentViewController.h"
#import "ANNErrorTexts.h"
#import "ANNLayoutFactory.h"
#import "ANNLayoutTreeOptions.h"
#import "ANNLayoutValidator.h"
#import "ANNModal.h"
#import "ANNModalPresentation.h"
#import "ANNModalSizePresenter.h"
#import "ANNModalTransitions.h"
#import "ANNNavigationState.h"
#import "ANNNodeRegistry.h"
#import "ANNOptionsReader.h"
#import "ANNParking.h"
#import "ANNPendingLayers.h"
#import "ANNPresentationGate.h"
#import "ANNRenderWait.h"
#import "ANNSheetPresenter.h"
#import "ANNSystemUI.h"
#import "ANNTransitionPlan.h"
#import "ANNZoomAnchor.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"
#import <QuartzCore/QuartzCore.h>
#import <React/RCTLog.h>
#import <React/RCTUtils.h>

// How long a modal waits for its screens when `animations.showModal.enter.waitForRender` is not set: from the start of
// the wait, for the screens and their bar components together.
static const NSTimeInterval ANNModalRenderLimit = 0.5;

// UIKit runs some completions inside a block of its animation queue (the end of a zoom): the end steps, which stop
// React surfaces, run on the main queue.
static void ANNOnMainQueue(dispatch_block_t block) {
    if (RCTIsMainQueue()) {
        block();
    } else {
        dispatch_async(dispatch_get_main_queue(), block);
    }
}

// One `dismissAllModals`: the modals it takes, those that count in its event, the drags it waits for.
@interface ANNModalBatch : NSObject
@property(nonatomic, strong, nullable) ANNCommandAnswer *answer;
@property(nonatomic) BOOL animated;
// Bottom to top.
@property(nonatomic, strong) NSMutableArray<ANNModal *> *records;
@property(nonatomic, strong) NSHashTable<ANNModal *> *counted;
@property(nonatomic) NSUInteger dragsPending;
@property(nonatomic, strong, nullable) ANNGateStep *step;
// A second `dismissAllModals` while this one runs answers at its end (N6).
@property(nonatomic, strong) NSMutableArray<ANNCommandAnswer *> *waiters;
@end

@implementation ANNModalBatch

- (instancetype)init {
    if (self = [super init]) {
        _records = [NSMutableArray new];
        _counted = [NSHashTable weakObjectsHashTable];
        _waiters = [NSMutableArray new];
    }
    return self;
}

@end

// Main queue. The records bottom to top, the running batches, and the ids of modals the user closed: a `dismissModal`
// with one of them completes without an event until a new node takes the id or JavaScript reloads.
static NSMutableArray<ANNModal *> *ANNModals;
static NSMutableArray<ANNModalBatch *> *ANNBatches;
static NSMutableArray<NSDictionary *> *ANNClosedByUser;

static NSString *const ANNClosedIdsKey = @"ids";
static NSString *const ANNClosedRootIdKey = @"rootId";

// The state UIKit, the user and the navigation state see: a modal that goes with the old root is still on screen.
static ANNModalState ANNSeenState(ANNModal *modal) {
    return modal.state == ANNModalStateWithOldRoot ? modal.stateBeforeRoot : modal.state;
}

// Presenting, shown or dragged: on screen.
static BOOL ANNIsOnScreen(ANNModal *modal) {
    ANNModalState state = ANNSeenState(modal);
    return state == ANNModalStatePresenting || state == ANNModalStateShown ||
           state == ANNModalStateGesture;
}

// The view of the node is in a window that shows (N2: UIKit presents only from there).
static BOOL ANNIsInShownWindow(UIViewController *node) {
    UIWindow *window = node.viewIfLoaded.window;
    return window != nil && !window.hidden;
}

// Every id of the node's tree.
static void ANNCollectIds(UIViewController *node, NSMutableSet<NSString *> *ids) {
    NSString *componentId = node.layoutInfo.componentId;
    if (componentId) {
        [ids addObject:componentId];
    }
    for (UIViewController *child in node.layoutChildren) {
        ANNCollectIds(child, ids);
    }
}

@implementation ANNModalCoordinator

+ (void)initialize {
    if (self == ANNModalCoordinator.class) {
        ANNModals = [NSMutableArray new];
        ANNBatches = [NSMutableArray new];
        ANNClosedByUser = [NSMutableArray new];
    }
}

#pragma mark - showModal

+ (void)showModal:(ANNLayoutNode *)tree answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    // Before any node: a type without a maker (none is left: the guard stays for safety), an id that shows already
    // (the nodes of modals that go with the old root count), a value of the options that can't be read.
    if (![ANNLayoutFactory canMakeTrees:@[ tree ]]) {
        [answer reject:[ANNErrorTexts notDoneYet:@"showModal"]];
        return;
    }
    NSString *shownId = [ANNLayoutValidator firstShownIdIn:tree allowed:nil];
    if (shownId) {
        [answer reject:[ANNErrorTexts alreadyShown:shownId]];
        return;
    }
    NSString *rejection = nil;
    NSDictionary<NSString *, ANNNavigationOptions *> *optionsById =
        [ANNLayoutTreeOptions optionsOfTree:tree rejection:&rejection];
    if (!optionsById) {
        [answer reject:rejection];
        return;
    }
    ANNModal *modal = [self makeModal:tree options:optionsById];
    modal.showAnswer = answer;
    // The record is listed and parked already: a failure at the start of its wait goes as a cancel.
    [self protect:modal
             step:^{
               [ANNModalCoordinator startWaitOf:modal];
             }];
}

+ (ANNModal *)showChainModal:(ANNLayoutNode *)tree
                     options:(NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById
                    animated:(BOOL)animated
                       ended:(void (^)(BOOL shown))ended {
    ANNAssertMainQueue();
    ANNModal *modal = nil;
    @try {
        modal = [self makeModal:tree options:optionsById];
    } @catch (NSException *exception) {
        [self logChainFailureOf:tree.nodeId reason:exception.reason ?: exception.name];
        ended(NO);
        return nil;
    }
    modal.chainStep = ended;
    modal.animatedOverride = animated ? nil : @NO;
    [self protect:modal
             step:^{
               [ANNModalCoordinator startWaitOf:modal];
             }];
    return modal;
}

+ (void)logChainFailureOf:(NSString *)rootId reason:(NSString *)reason {
    NSString *what = [NSString stringWithFormat:@"show the screen '%@'", rootId];
    [ANNCommandRunner logFailure:@"setRoot" text:[ANNErrorTexts couldNot:what reason:reason]];
}

// The tree is made, renders at once at the window's size and lays out, so that its surfaces start with their size, as
// the root's does; then the record is the top of the stack of modals at once (`showModal` step 2): findable and
// closable. Its view waits in the window, unseen, and it is a layer that waits to show (`ANNPendingLayers`): a stack in
// it runs its commands at once and tells the wait of its new top.
+ (ANNModal *)makeModal:(ANNLayoutNode *)tree
                options:(NSDictionary<NSString *, ANNNavigationOptions *> *)optionsById {
    UIWindow *window = ApexNavigation.mainWindow;
    UIViewController *root = [ANNLayoutFactory nodeFromLayout:tree options:optionsById];
    @try {
        [root render];
        if (window) {
            root.view.frame = window.bounds;
        }
        [root.view setNeedsLayout];
        [root.view layoutIfNeeded];
    } @catch (NSException *exception) {
        [root destroy];
        @throw;
    }
    ANNModal *modal = [[ANNModal alloc] initWithRoot:root rootId:tree.nodeId];
    [ANNModals addObject:modal];
    __weak ANNModal *weakModal = modal;
    [ANNPendingLayers add:root
        onShownScreensChanged:^{
          [ANNModalCoordinator shownScreensChangedOf:weakModal];
        }];
    if (window) {
        [modal.parking park:root inView:window];
    }
    return modal;
}

#pragma mark - The wait

// The root's rule (`ANNRootCoordinator`): `waitForRender: false` nothing; `true` the waited screens as long as it
// takes, then their bar components at most 0.5 s; unset, both together at most 0.5 s from the start, then
// `markReady`. A screen the user won't see at once (an unselected tab's) never holds the modal longer than 0.5 s.
+ (void)startWaitOf:(ANNModal *)modal {
    ANNNavigationOptions *resolved = [modal.root resolveOptionsWithDefault];
    Bool *waitForRender = resolved.animations.showModal.enter.waitForRender;
    if (waitForRender.hasValue && !waitForRender.get) {
        [self waitEndedOf:modal];
        return;
    }
    __weak ANNModal *weakModal = modal;
    BOOL contentFirst = waitForRender.hasValue;
    modal.waitStarted = contentFirst ? 0 : CACurrentMediaTime();
    ANNBarWaitLimits withContent = {0, 0};
    ANNBarWaitLimits ownPhase = {ANNModalRenderLimit, ANNModalRenderLimit};
    ANNRenderWait *wait = [[ANNRenderWait alloc]
        initWithContent:ANNRenderWaitContentEachScreen
                   node:nil
                screens:^NSArray<UIViewController *> * {
                  return weakModal.root.waitedComponentViewControllers ?: @[];
                }
                  limit:contentFirst ? 0 : ANNModalRenderLimit
                    bar:contentFirst ? ANNRenderWaitBarAfterContent : ANNRenderWaitBarWithContent
              barLimits:contentFirst ? ownPhase : withContent];
    wait.mountLimit = ANNModalRenderLimit;
    wait.protect = ^(dispatch_block_t step) {
      [ANNModalCoordinator protect:weakModal step:step];
    };
    wait.ended = ^{
      ANNModal *rendered = weakModal;
      if (rendered) {
          [ANNModalCoordinator waitEndedOf:rendered];
      }
    };
    modal.wait = wait;
    [wait start];
}

// A stack command inside the waiting modal changed its top: the wait takes the modal's waited screens again.
+ (void)shownScreensChangedOf:(ANNModal *)modal {
    ANNAssertMainQueue();
    if (!modal || modal.state != ANNModalStateWaiting || !modal.wait.isWaiting) {
        return;
    }
    [self protect:modal
             step:^{
               [modal.wait retakeScreens:!modal.root.nodeHelper.destroyed];
             }];
}

// The steps after the command's body are outside the runner's protection: a failure while the modal waits rejects its
// show and it goes as a cancel; later, it is logged.
+ (void)protect:(ANNModal *)modal step:(dispatch_block_t)step {
    @try {
        step();
    } @catch (NSException *exception) {
        NSString *reason = exception.reason.length > 0 ? exception.reason : exception.name;
        if (modal && modal.state == ANNModalStateWaiting) {
            [self failShowOf:modal reason:reason];
        } else {
            RCTLogWarn(@"[ApexNavigation] %@",
                       [ANNErrorTexts couldNot:@"show a modal" reason:reason]);
        }
    }
}

// The end steps of a record (its show presented, its dismissal ended, closed by the user) run inside UIKit's
// completions and delegate calls, outside the runner's protection, in any state: a failure there is logged in one line,
// answers what of the record still waits for an answer (its show, a chain step, its dismissals:
// `endFailedOf:showing:exception:`), and its drive stops. The gate's `done` ran before the step, as without a failure.
+ (void)protectEndOf:(ANNModal *)modal showing:(BOOL)showing step:(dispatch_block_t)step {
    @try {
        step();
    } @catch (NSException *exception) {
        NSString *format = showing ? @"show the screen '%@'" : @"dismiss the modal '%@'";
        NSString *what = modal ? [NSString stringWithFormat:format, modal.rootId]
                               : (showing ? @"show a modal" : @"dismiss a modal");
        NSString *reason = exception.reason.length > 0 ? exception.reason : exception.name;
        RCTLogWarn(@"[ApexNavigation] %@", [ANNErrorTexts couldNot:what reason:reason]);
        [self endFailedOf:modal showing:showing exception:exception];
    }
}

// A failed end step of `modal`: its show rejects. After a failed show the dismissals that wait for it don't fail with
// it, as on Android: on a modal that shows they run now and resolve once it went (one still `Presenting` shows: UIKit's
// completion ran); on one that goes another way (a `dismissAllModals`, with the old root) or went already they resolve
// with its root's id; on one in another transition (a drag, its own dismissal) they wait for that transition's end.
// A failed dismissal rejects itself and the dismissals behind it.
+ (void)endFailedOf:(ANNModal *)modal showing:(BOOL)showing exception:(NSException *)exception {
    if (!modal) {
        return;
    }
    [self stopDriving:modal];
    NSString *show = [NSString stringWithFormat:@"show the screen '%@'", modal.rootId];
    [modal.showAnswer reject:[ANNCommandRunner rejectionOf:exception what:show]];
    ANNModalState state = modal.state;
    BOOL goesAnotherWay = (state == ANNModalStateDismissing && modal.batch) ||
                          state == ANNModalStateWithOldRoot ||
                          state == ANNModalStateClosedBelow || state == ANNModalStateGone;
    if (showing && state == ANNModalStatePresenting) {
        modal.state = ANNModalStateShown;
    }
    if (showing && modal.state == ANNModalStateShown) {
        [self runPendingOf:modal];
    } else if (showing && goesAnotherWay) {
        [modal.dismissal.answer resolve:modal.rootId];
        [self resolvePendingOf:modal];
    } else if (!showing) {
        NSString *dismiss = [NSString stringWithFormat:@"dismiss the modal '%@'", modal.rootId];
        NSString *dismissText = [ANNCommandRunner rejectionOf:exception what:dismiss];
        [modal.dismissal.answer reject:dismissText];
        NSArray<ANNModalDismissal *> *pending = modal.pending.copy;
        [modal.pending removeAllObjects];
        for (ANNModalDismissal *waiting in pending) {
            [waiting.answer reject:dismissText];
        }
    }
    // A `setRoot` chain goes on (its own steps are guarded by the root coordinator).
    void (^chainStep)(BOOL) = modal.chainStep;
    modal.chainStep = nil;
    if (chainStep) {
        chainStep(NO);
    }
}

// The batch's end runs inside UIKit's completion: a failure is logged in one line and rejects the answers still open
// (the batch's, those that wait for its end, its records').
+ (void)protectBatch:(ANNModalBatch *)batch step:(dispatch_block_t)step {
    @try {
        step();
    } @catch (NSException *exception) {
        NSString *reason = exception.reason.length > 0 ? exception.reason : exception.name;
        RCTLogWarn(@"[ApexNavigation] %@",
                   [ANNErrorTexts couldNot:@"dismiss all modals" reason:reason]);
        if (!batch) {
            return;
        }
        NSString *text = [ANNCommandRunner rejectionOf:exception what:@"dismiss all modals"];
        [batch.answer reject:text];
        for (ANNCommandAnswer *waiter in batch.waiters) {
            [waiter reject:text];
        }
        for (ANNModal *modal in batch.records.copy) {
            [self endFailedOf:modal showing:NO exception:exception];
        }
    }
}

+ (void)waitEndedOf:(ANNModal *)modal {
    if (modal.state != ANNModalStateWaiting) {
        return;
    }
    modal.ready = YES;
    [self presentReadyModals];
}

// N1: a ready modal presents only after every older waiting one presented or went, so that UIKit's chain keeps the
// order of the records.
+ (void)presentReadyModals {
    for (ANNModal *modal in ANNModals.copy) {
        if (modal.state != ANNModalStateWaiting) {
            continue;
        }
        if (!modal.ready) {
            return;
        }
        if (!modal.step) {
            [self queuePresentOf:modal];
        }
    }
}

#pragma mark - The present

// The topmost controller of the main window's chain that is not being dismissed: a modal closed below and a foreign
// controller count; before the first root, the splash.
+ (UIViewController *)presentingController {
    UIViewController *top = ApexNavigation.mainWindow.rootViewController;
    while (top.presentedViewController && !top.presentedViewController.isBeingDismissed) {
        top = top.presentedViewController;
    }
    return top;
}

+ (void)queuePresentOf:(ANNModal *)modal {
    __weak ANNModal *weakModal = modal;
    ANNGateStep *step = [[ANNGateStep alloc] initWithRun:^(dispatch_block_t done) {
      [ANNModalCoordinator present:weakModal done:done];
    }];
    // N2: UIKit presents only from a controller in a shown window; the step waits for the scene or the first root.
    step.canRun = ^BOOL {
      UIViewController *presenting = [ANNModalCoordinator presentingController];
      return presenting != nil && ANNIsInShownWindow(presenting);
    };
    modal.step = step;
    [ANNPresentationGate enqueue:step];
}

+ (void)present:(ANNModal *)modal done:(dispatch_block_t)done {
    if (!modal || modal.state != ANNModalStateWaiting) {
        done();
        return;
    }
    modal.step = nil;
    UIViewController *root = modal.root;
    if (root.nodeHelper.destroyed) {
        [self cancel:modal rejection:nil dismissal:nil sendsEvent:NO];
        done();
        return;
    }
    UIViewController *windowRoot = ApexNavigation.mainWindow.rootViewController;
    UIViewController *presenting = [self presentingController];
    @try {
        // The options at the present: a first render's options of the screen count.
        ANNNavigationOptions *resolved = [root resolveOptionsWithDefault];
        ANNModalPresentation *presentation =
            [[ANNModalPresentation alloc] initWithOptions:resolved animated:modal.animatedOverride];
        NSString *problem = [presentation fitToPresenting:presenting windowRoot:windowRoot];
        if (problem) {
            [self failShowOf:modal reason:problem];
            done();
            return;
        }
        modal.presentation = presentation;
        // The popover's source is looked for on the layout the user sees.
        UIViewController *presentingLayout = [self visibleLayout] ?: presenting;
        modal.layerUnder = [self layerUnder:modal];
        // The custom show's delegate, before the presentation controller is made (UIKit reads it at the present).
        [self setUpShowOf:modal resolved:resolved presentingLayout:presentingLayout];
        [presentation prepare:root
                      options:resolved
                     delegate:modal
             presentingLayout:presentingLayout];
        // The command reports the modal's appearance itself, from before its view leaves the parking: UIKit's callbacks
        // would come at its own moments.
        [self drive:modal nodes:@[ root ]];
        modal.state = ANNModalStatePresenting;
        [ANNPendingLayers remove:root];
        [ANNAppearanceDispatcher beginTransitionShowing:@[ root ]];
        // UIKit shows the view in the same turn: its departure from the window ends here.
        [modal.parking unpark:root endingDeparture:YES];
        __weak ANNModal *weakModal = modal;
        [presenting presentViewController:root
                                 animated:presentation.animated
                               completion:^{
                                 // A zoom's show ends in a block of UIKit's animation queue.
                                 ANNOnMainQueue(^{
                                   done();
                                   ANNModal *presented = weakModal;
                                   [ANNModalCoordinator protectEndOf:presented
                                                             showing:YES
                                                                step:^{
                                                                  [ANNModalCoordinator
                                                                      presented:presented];
                                                                }];
                                 });
                               }];
    } @catch (NSException *exception) {
        NSString *reason = exception.reason.length > 0 ? exception.reason : exception.name;
        if (!root.presentingViewController) {
            // UIKit didn't take it: the show is rejected and the modal goes as a cancel.
            [self stopDriving:modal];
            modal.state = ANNModalStateWaiting;
            [self failShowOf:modal reason:reason];
            done();
        } else {
            // UIKit took it: its completion still ends the show. The gate is free either way (a running transition
            // still holds the next step back).
            RCTLogWarn(@"[ApexNavigation] %@",
                       [ANNErrorTexts couldNot:@"show a modal" reason:reason]);
            done();
        }
    }
}

// The custom show: its plan and the delegate that gives it to UIKit; without one, the iOS 18 zoom when `showModal.zoom`
// names a view of the shown screens (in a split view every visible column). Own animations win over the zoom.
+ (void)setUpShowOf:(ANNModal *)modal
            resolved:(ANNNavigationOptions *)resolved
    presentingLayout:(UIViewController *)presentingLayout {
    ANNModalPresentation *presentation = modal.presentation;
    UIViewController *root = modal.root;
    // What the modal's own wait left of its 0.5 s; after a wait without limit the targets 0.5 s.
    NSTimeInterval limit = [ANNAnimationRules customWaitLimitAfterWaitStartedAt:modal.waitStarted
                                                                  commandLimit:ANNModalRenderLimit];
    ANNTransitionPlan *plan = [presentation showPlanOf:root under:modal.layerUnder waitLimit:limit];
    if (plan) {
        if (!modal.transitions) {
            modal.transitions = [ANNModalTransitions new];
        }
        modal.transitions.showPlan = plan;
        root.transitioningDelegate = modal.transitions;
        return;
    }
    if (@available(iOS 18.0, *)) {
        ANNModalAnimationOptions *showModal = resolved.animations.showModal;
        NSString *fromId = [showModal.zoom.fromId withDefault:nil];
        if (!presentation.animated || [ANNAnimationRules modalIsOwn:showModal] ||
            ![showModal.zoom.enabled withDefault:YES] || ![fromId isKindOfClass:NSString.class] ||
            fromId.length == 0) {
            return;
        }
        __weak UIViewController *weakLayout = presentingLayout;
        [ANNZoomAnchor zoom:root
                   fromId:fromId
            sourceScreens:^NSArray<UIViewController *> * {
              UIViewController *layout = weakLayout;
              return layout ? layout.shownComponentViewControllers : @[];
            }];
    }
}

// The show couldn't be made: its answer rejects (a `setRoot` chain logs it and goes on) and the modal goes as a cancel.
+ (void)failShowOf:(ANNModal *)modal reason:(NSString *)reason {
    NSString *what = [NSString stringWithFormat:@"show the screen '%@'", modal.rootId];
    [self cancel:modal
         rejection:[ANNErrorTexts couldNot:what reason:reason]
         dismissal:nil
        sendsEvent:NO];
}

// The show's end: `DidDisappear` of the layer under when UIKit took it out of the window, `DidAppear` of the modal, the
// state, the answer. A modal of a `setRoot` chain sends no state of its own: the chain goes on. Then the size again and
// the hold; then its first waiting command. A modal that joined a `dismissAllModals` while it presented answers its
// show and stays in the batch.
+ (void)presented:(ANNModal *)modal {
    if (!modal) {
        return;
    }
    ANNModalState state = modal.state;
    BOOL inBatch = state == ANNModalStateDismissing && modal.batch != nil;
    BOOL withOldRoot =
        state == ANNModalStateWithOldRoot && modal.stateBeforeRoot == ANNModalStatePresenting;
    if (state != ANNModalStatePresenting && !inBatch && !withOldRoot) {
        [self stopDriving:modal];
        return;
    }
    if (withOldRoot) {
        modal.stateBeforeRoot = ANNModalStateShown;
    } else if (!inBatch) {
        modal.state = ANNModalStateShown;
    }
    UIViewController *root = modal.root;
    UIViewController *under = modal.layerUnder;
    modal.coversUnder = under != nil && under.viewIfLoaded.window == nil;
    NSArray<UIViewController *> *hidden = modal.coversUnder ? @[ under ] : @[];
    @try {
        // The chain step stays on the record until its events went: a failure there tells the chain
        // (`endFailedOf:showing:exception:`).
        void (^chainStep)(BOOL) = modal.chainStep;
        if (chainStep) {
            [ANNAppearanceDispatcher endSwitchHiding:hidden showing:@[ root ] events:nil];
            modal.chainStep = nil;
            chainStep(YES);
        } else {
            [ANNAppearanceDispatcher endTransitionHiding:hidden
                                                 showing:@[ root ]
                                                  events:nil
                                                  answer:modal.showAnswer
                                                   value:modal.rootId];
        }
    } @finally {
        [self stopDriving:modal];
    }
    [ANNZoomAnchor transitionEndedOf:root];
    [ANNModalSizePresenter applyOptions:[root resolveOptionsWithDefault].modal toModal:root];
    [ANNModalPresentation applyHoldTo:root];
    if (modal.presentation.isSheet) {
        modal.knownDetent = [self knownDetentOf:modal];
    }
    if (modal.state == ANNModalStateShown) {
        [self runPendingOf:modal];
    }
}

#pragma mark - The layers

// The layer under a modal: the root of the nearest modal below it that is on screen, else the window's root when it is
// a node (none over the splash).
+ (UIViewController *)layerUnder:(ANNModal *)modal {
    NSUInteger index = [ANNModals indexOfObjectIdenticalTo:modal];
    if (index == NSNotFound) {
        index = ANNModals.count;
    }
    for (NSUInteger i = index; i > 0; i--) {
        ANNModal *below = ANNModals[i - 1];
        if (ANNIsOnScreen(below)) {
            return below.root;
        }
    }
    UIViewController *root = ApexNavigation.mainWindow.rootViewController;
    return root.isLayoutNode ? root : nil;
}

// The layer under had disappeared: the modal or a modal closed below between them covered it.
+ (BOOL)layerUnder:(UIViewController *)under hadDisappearedFor:(ANNModal *)modal {
    if (!under) {
        return NO;
    }
    if (modal.coversUnder) {
        return YES;
    }
    NSUInteger index = [ANNModals indexOfObjectIdenticalTo:modal];
    for (NSUInteger i = index; i != NSNotFound && i > 0; i--) {
        ANNModal *below = ANNModals[i - 1];
        if (below.root == under) {
            break;
        }
        if (below.state == ANNModalStateClosedBelow && below.coversUnder) {
            return YES;
        }
    }
    return NO;
}

// A modal is the top when no modal above it waits, presents, shows or is dragged (`showModal` step 2; one being
// dismissed doesn't count).
+ (BOOL)isTop:(ANNModal *)modal {
    NSUInteger index = [ANNModals indexOfObjectIdenticalTo:modal];
    if (index == NSNotFound) {
        return NO;
    }
    for (NSUInteger i = index + 1; i < ANNModals.count; i++) {
        ANNModal *above = ANNModals[i];
        ANNModalState state = ANNSeenState(above);
        if (state == ANNModalStateWaiting || state == ANNModalStatePresenting ||
            state == ANNModalStateShown || state == ANNModalStateGesture) {
            return NO;
        }
        // One whose dismissal waits for its turn at the gate is still in UIKit's chain above.
        if (state == ANNModalStateDismissing && !above.dismissalStarted && !above.batch) {
            return NO;
        }
    }
    return YES;
}

// The modals closed below right under `modal` (waiting ones, which UIKit doesn't show, are passed over): one dismissal
// from the lowest one's presenting controller takes them all.
+ (NSArray<ANNModal *> *)closedBelowUnder:(ANNModal *)modal {
    NSMutableArray<ANNModal *> *closed = [NSMutableArray new];
    NSUInteger index = [ANNModals indexOfObjectIdenticalTo:modal];
    for (NSUInteger i = index; i != NSNotFound && i > 0; i--) {
        ANNModal *below = ANNModals[i - 1];
        if (below.state == ANNModalStateWaiting) {
            continue;
        }
        if (below.state != ANNModalStateClosedBelow) {
            break;
        }
        [closed addObject:below];
    }
    return closed;
}

+ (void)drive:(ANNModal *)modal nodes:(NSArray<UIViewController *> *)nodes {
    [self stopDriving:modal];
    modal.driven = nodes;
    [ANNAppearanceDispatcher driveAppearanceOf:nodes];
}

+ (void)stopDriving:(ANNModal *)modal {
    NSArray<UIViewController *> *driven = modal.driven;
    if (driven) {
        modal.driven = nil;
        [ANNAppearanceDispatcher stopDrivingAppearanceOf:driven];
    }
}

+ (void)remove:(ANNModal *)modal {
    modal.state = ANNModalStateGone;
    [ANNModals removeObjectIdenticalTo:modal];
    // UIKit removed it (or is about to): its container is UIKit's again as it was.
    modal.hiddenContainer.hidden = NO;
    modal.hiddenContainer = nil;
}

+ (void)resolvePendingOf:(ANNModal *)modal {
    NSArray<ANNModalDismissal *> *pending = modal.pending.copy;
    [modal.pending removeAllObjects];
    for (ANNModalDismissal *dismissal in pending) {
        [dismissal.answer resolve:modal.rootId];
    }
}

#pragma mark - The cancel

// A modal that waits goes without showing: its wait stops, its view leaves the window, its root is destroyed without
// events (nothing appeared), the layer under is not touched. Its show resolves with its root's id (or rejects with
// `rejection`); a chain is told. A cancel by `dismissModal` sends one `ModalDismissed` after the show's answer, then
// that dismissal resolves.
+ (void)cancel:(ANNModal *)modal
     rejection:(NSString *)rejection
     dismissal:(ANNModalDismissal *)dismissal
    sendsEvent:(BOOL)sendsEvent {
    [self stopWaitOf:modal];
    [self remove:modal];
    UIViewController *root = modal.root;
    @try {
        ANNCommandAnswer *showAnswer = modal.showAnswer;
        void (^chainStep)(BOOL) = modal.chainStep;
        modal.chainStep = nil;
        if (rejection) {
            [showAnswer reject:rejection];
            if (chainStep) {
                [ANNCommandRunner logFailure:@"setRoot" text:rejection];
            }
        } else {
            [showAnswer resolve:modal.rootId];
        }
        if (sendsEvent && !modal.reported) {
            modal.reported = YES;
            [ANNAppearanceDispatcher modalDismissed:root count:1];
        }
        [root destroy];
        [dismissal.answer resolve:modal.rootId];
        [self resolvePendingOf:modal];
        if (chainStep) {
            chainStep(NO);
        }
    } @finally {
        [self cleanTopOfChain];
        [self presentReadyModals];
    }
}

// What a waiting modal holds goes: its wait, its place in the window, its turn at the gate.
+ (void)stopWaitOf:(ANNModal *)modal {
    [modal.wait cancel];
    modal.wait = nil;
    [ANNPendingLayers remove:modal.root];
    [modal.parking unpark:modal.root endingDeparture:YES];
    if (modal.step) {
        [ANNPresentationGate drop:modal.step];
        modal.step = nil;
    }
    [self stopDriving:modal];
}

+ (void)cancelChainModal:(ANNModal *)modal {
    ANNAssertMainQueue();
    if (modal.state != ANNModalStateWaiting) {
        return;
    }
    modal.chainStep = nil;
    [self cancel:modal rejection:nil dismissal:nil sendsEvent:NO];
}

#pragma mark - dismissModal

+ (void)dismissModal:(NSString *)componentId
             options:(NSDictionary *)options
              answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    UIViewController *node = [ANNNodeRegistry nodeForComponentId:componentId];
    ANNModal *modal = nil;
    if (node) {
        modal = [self modalOf:node];
        if (!modal) {
            [answer reject:[ANNErrorTexts notInModal:componentId]];
            return;
        }
    } else {
        // The nodes of a modal closed below or going with the old root may be destroyed: their ids are kept.
        for (ANNModal *candidate in ANNModals) {
            if ((candidate.state == ANNModalStateClosedBelow ||
                 candidate.state == ANNModalStateWithOldRoot) &&
                [candidate.ids containsObject:componentId]) {
                modal = candidate;
                break;
            }
        }
        if (!modal) {
            // Closed by the user: it just completes, without an event.
            for (NSDictionary *closed in ANNClosedByUser.reverseObjectEnumerator) {
                if ([closed[ANNClosedIdsKey] containsObject:componentId]) {
                    [answer resolve:closed[ANNClosedRootIdKey]];
                    return;
                }
            }
            [answer reject:[ANNErrorTexts screenNotShown:componentId]];
            return;
        }
    }
    // Commands by the ids of the modals that go with the old root find nothing.
    if (modal.state == ANNModalStateWithOldRoot) {
        [answer reject:[ANNErrorTexts screenNotShown:componentId]];
        return;
    }
    // The last modal over no root (shown before any `setRoot`) stays: nothing would be left to show, as on Android.
    if ([self isLastWithoutRoot:modal]) {
        [answer reject:[ANNErrorTexts modalWithoutRoot:componentId]];
        return;
    }
    // Step 1: the options, read on the main queue; one that can't be read rejects before anything changes. They merge
    // into the modal's visible screen as `mergeOptions` would.
    if (options.count > 0) {
        NSString *unreadablePath = nil;
        ANNNavigationOptions *merge = [ANNOptionsReader optionsFrom:options
                                                     unreadablePath:&unreadablePath];
        if (unreadablePath) {
            [answer reject:[ANNErrorTexts unreadableOption:unreadablePath componentId:componentId]];
            return;
        }
        UIViewController *screen =
            [ANNAppearanceDispatcher visibleScreenOf:modal.root] ?: modal.root;
        if (!screen.nodeHelper.destroyed) {
            [CATransaction begin];
            @try {
                [screen mergeOptions:merge];
            } @finally {
                [CATransaction commit];
            }
            [self optionsMerged:merge into:screen];
            [ANNSystemUI optionsMerged:merge];
        }
    }
    // Step 2: the animation from the command's screen with its parents and the defaults, read now.
    ANNModalDismissal *dismissal = [ANNModalDismissal new];
    dismissal.answer = answer;
    dismissal.animated = YES;
    if (node && !node.nodeHelper.destroyed) {
        // `exit.enabled: false` turns the dismissal's animation off; its own animation plays when the dismissal starts.
        ANNModalAnimationOptions *animation =
            [node resolveOptionsWithDefault].animations.dismissModal;
        dismissal.animated = ![ANNAnimationRules sideIsDisabled:animation.exit];
        dismissal.animation = animation;
    }
    [self dismiss:modal with:dismissal];
}

// No root in the window (the splash, or nothing yet) and no other modal that shows or comes: `modal` is all a dismissal
// would take away. One closed below, going with the old root or gone takes nothing away, so it doesn't count.
+ (BOOL)isLastWithoutRoot:(ANNModal *)modal {
    if (modal.state == ANNModalStateClosedBelow || modal.state == ANNModalStateGone ||
        ApexNavigation.mainWindow.rootViewController.isLayoutNode) {
        return NO;
    }
    for (ANNModal *other in ANNModals) {
        if (other != modal && other.state != ANNModalStateClosedBelow &&
            other.state != ANNModalStateWithOldRoot && other.state != ANNModalStateGone) {
            return NO;
        }
    }
    return YES;
}

// The modal of `node`: the record whose root is the top of `node`'s layout.
+ (ANNModal *)modalOf:(UIViewController *)node {
    UIViewController *top = node;
    for (UIViewController *parent = node.layoutParent; parent; parent = parent.layoutParent) {
        top = parent;
    }
    for (ANNModal *modal in ANNModals) {
        if (modal.root == top && modal.state != ANNModalStateGone) {
            return modal;
        }
    }
    return nil;
}
+ (void)dismiss:(ANNModal *)modal with:(ANNModalDismissal *)dismissal {
    switch (modal.state) {
        case ANNModalStateWaiting:
            [self cancel:modal rejection:nil dismissal:dismissal sendsEvent:YES];
            break;
        case ANNModalStatePresenting:
        case ANNModalStateGesture:
        case ANNModalStateDismissing:
            // It waits for the running transition: no second event.
            [modal.pending addObject:dismissal];
            break;
        case ANNModalStateShown:
            [self startDismissing:modal with:dismissal];
            break;
        case ANNModalStateClosedBelow:
            // It went already; the command just completes.
            [dismissal.answer resolve:modal.rootId];
            break;
        case ANNModalStateWithOldRoot:
        case ANNModalStateGone:
            [dismissal.answer resolve:modal.rootId];
            break;
    }
}

// An accepted dismissal makes the modal `Dismissing` at once; only UIKit's call waits for the gate. When it runs, the
// modal's place decides: the top goes with its animation, any other closes below.
+ (void)startDismissing:(ANNModal *)modal with:(ANNModalDismissal *)dismissal {
    modal.state = ANNModalStateDismissing;
    modal.dismissal = dismissal;
    modal.dismissalStarted = NO;
    __weak ANNModal *weakModal = modal;
    ANNGateStep *step = [[ANNGateStep alloc] initWithRun:^(dispatch_block_t done) {
      [ANNModalCoordinator runDismissalOf:weakModal done:done];
    }];
    modal.step = step;
    [ANNPresentationGate enqueue:step];
}

// The modal's first waiting command runs as in `Shown` (after its show, after a cancelled drag); the others wait for
// its end.
+ (void)runPendingOf:(ANNModal *)modal {
    if (modal.state != ANNModalStateShown || modal.pending.count == 0) {
        return;
    }
    ANNModalDismissal *first = modal.pending.firstObject;
    [modal.pending removeObjectAtIndex:0];
    [self startDismissing:modal with:first];
}

+ (void)runDismissalOf:(ANNModal *)modal done:(dispatch_block_t)done {
    if (!modal || modal.state != ANNModalStateDismissing || modal.batch || modal.dismissalStarted) {
        done();
        return;
    }
    modal.step = nil;
    @try {
        if (![self isTop:modal]) {
            [self closeBelow:modal];
            done();
            return;
        }
        [self startDismissalOf:modal done:done];
    } @catch (NSException *exception) {
        NSString *what = [NSString stringWithFormat:@"dismiss the modal '%@'", modal.rootId];
        RCTLogWarn(@"[ApexNavigation] %@",
                   [ANNErrorTexts couldNot:what reason:exception.reason ?: exception.name]);
        // The dismissal still open answers, and so do the dismissals waiting behind it (an answer given already
        // stays as it was).
        [self endFailedOf:modal showing:NO exception:exception];
        done();
    }
}

#pragma mark - Closed below

// The modal's view hides (it is never unloaded), `DidDisappear` of its screens that showed, `ModalDismissed` at once,
// its content stops, the answer. Its controller stays in UIKit's chain until UIKit removes it with the modals above it.
+ (void)closeBelow:(ANNModal *)modal {
    UIViewController *root = modal.root;
    NSMutableSet<NSString *> *ids = [NSMutableSet new];
    ANNCollectIds(root, ids);
    modal.ids = ids;
    modal.state = ANNModalStateClosedBelow;
    modal.reported = YES;
    root.viewIfLoaded.hidden = YES;
    // A sheet's card and shadow are UIKit's views around the root's: the presentation's public container hides too;
    // nothing is unloaded.
    UIView *container = root.presentationController.containerView;
    if (container && !container.hidden) {
        container.hidden = YES;
        modal.hiddenContainer = container;
    }
    ANNModalDismissal *dismissal = modal.dismissal;
    modal.dismissal = nil;
    @try {
        [ANNComponentViewController nodeWillLeave:root];
        [ANNAppearanceDispatcher endTransitionHiding:@[ root ]
                                             showing:@[]
                                              events:^{
                                                [ANNAppearanceDispatcher modalDismissed:root count:1];
                                                [root destroy];
                                              }
                                              answer:dismissal.answer
                                               value:modal.rootId];
    } @catch (NSException *exception) {
        // Back on the record: `runDismissalOf:` rejects its answer if it is still open.
        if (!modal.dismissal) {
            modal.dismissal = dismissal;
        }
        @throw;
    }
    [self resolvePendingOf:modal];
}

#pragma mark - The dismissal

// At the start: `blurOnUnmount`, `WillAppear` of the layer under when it had disappeared; the command reports the
// appearance of what it moves. UIKit is asked from the presenting controller of the lowest modal closed below right
// under it, so that one dismissal takes them too; a search controller the modal shows goes first, without animation
// (`dismissModal` step 3). A modal UIKit doesn't hold any more (N3) ends at once.
+ (void)startDismissalOf:(ANNModal *)modal done:(dispatch_block_t)done {
    UIViewController *root = modal.root;
    modal.dismissalStarted = YES;
    UIViewController *under = [self layerUnder:modal];
    BOOL revealed = [self layerUnder:under hadDisappearedFor:modal];
    NSArray<ANNModal *> *closedBelow = [self closedBelowUnder:modal];
    UIViewController *from = closedBelow.count > 0
                                 ? closedBelow.lastObject.root.presentingViewController
                                 : root.presentingViewController;
    [ANNComponentViewController nodeWillLeave:root];
    NSMutableArray<UIViewController *> *driven = [NSMutableArray arrayWithObject:root];
    for (ANNModal *closed in closedBelow) {
        [driven addObject:closed.root];
    }
    if (revealed) {
        [driven addObject:under];
    }
    [self drive:modal nodes:driven];
    if (revealed) {
        [ANNAppearanceDispatcher beginTransitionShowing:@[ under ]];
    }
    __weak ANNModal *weakModal = modal;
    void (^ended)(void) = ^{
      ANNModal *dismissed = weakModal;
      [ANNModalCoordinator protectEndOf:dismissed
                                showing:NO
                                   step:^{
                                     [ANNModalCoordinator dismissalEndedOf:dismissed
                                                                     under:revealed ? under : nil
                                                               closedBelow:closedBelow];
                                   }];
    };
    if (!from) {
        ended();
        done();
        return;
    }
    BOOL animated = modal.dismissal ? modal.dismissal.animated : YES;
    ANNTransitionPlan *plan =
        animated ? [modal.presentation dismissPlanOf:root
                                           animation:modal.dismissal.animation
                                               under:under]
                 : nil;
    void (^dismiss)(void) = ^{
      [ANNModalCoordinator markCoreDismissalOf:root plan:plan record:weakModal];
      [from dismissViewControllerAnimated:animated
                               completion:^{
                                 ANNOnMainQueue(^{
                                   [ANNModalCoordinator coreDismissalEndedOf:weakModal];
                                   done();
                                   ended();
                                 });
                               }];
    };
    UIViewController *search = [self presentedSearchControllerIn:root];
    if (search.presentingViewController) {
        [search.presentingViewController dismissViewControllerAnimated:NO
                                                            completion:^{
                                                              dismiss();
                                                            }];
    } else {
        dismiss();
    }
}

// Right before the core's own dismissal: the plan of its custom animation goes to the modal's transitioning delegate,
// which UIKit asks now, and the delegate knows the dismissal is the core's (the user's swipe gets UIKit's).
+ (void)markCoreDismissalOf:(UIViewController *)root
                       plan:(ANNTransitionPlan *)plan
                     record:(ANNModal *)modal {
    if (!modal || (!plan && !modal.transitions)) {
        return;
    }
    if (!modal.transitions) {
        modal.transitions = [ANNModalTransitions new];
    }
    modal.transitions.dismissPlan = plan;
    modal.transitions.coreDismissal = YES;
    if (plan && root.transitioningDelegate != modal.transitions) {
        root.transitioningDelegate = modal.transitions;
    }
    // The own `exit` wins over the show's zoom: UIKit plays the zoom back instead of asking the delegate while the
    // modal keeps it, so a core dismissal with its own plan drops it (as a command's own pop does). The user's swipe
    // keeps UIKit's zoom.
    if (@available(iOS 18.0, *)) {
        if (plan && root.preferredTransition) {
            root.preferredTransition = nil;
        }
    }
}

+ (void)coreDismissalEndedOf:(ANNModal *)modal {
    modal.transitions.coreDismissal = NO;
    modal.transitions.dismissPlan = nil;
}

// A search controller UIKit presents inside the modal (a screen with a search bar defines the presentation context).
+ (UIViewController *)presentedSearchControllerIn:(UIViewController *)node {
    UISearchController *search = node.navigationItem.searchController;
    if (search.presentingViewController && !search.isBeingDismissed) {
        return search;
    }
    for (UIViewController *child in node.layoutChildren) {
        UIViewController *found = [self presentedSearchControllerIn:child];
        if (found) {
            return found;
        }
    }
    return nil;
}

// At the end: `DidDisappear` of its screens, `DidAppear` of the layer under when it had disappeared, the state,
// `ModalDismissed` unless it went already, its content stops, the answer; its waiting commands resolve; the modals
// closed below that UIKit took with it go.
+ (void)dismissalEndedOf:(ANNModal *)modal
                   under:(UIViewController *)under
             closedBelow:(NSArray<ANNModal *> *)closedBelow {
    if (!modal || modal.state != ANNModalStateDismissing) {
        [self stopDriving:modal];
        return;
    }
    if (modal.batch) {
        // A `dismissAllModals` took it while UIKit dismissed it: UIKit removed it, the batch reports it.
        [self stopDriving:modal];
        [self queueBatchIfReady:modal.batch];
        return;
    }
    [self remove:modal];
    for (ANNModal *closed in closedBelow) {
        [self remove:closed];
    }
    UIViewController *root = modal.root;
    [ANNZoomAnchor transitionEndedOf:root];
    ANNModalDismissal *dismissal = modal.dismissal;
    modal.dismissal = nil;
    @try {
        [ANNAppearanceDispatcher endTransitionHiding:@[ root ]
                                             showing:under ? @[ under ] : @[]
                                              events:^{
                                                if (!modal.reported) {
                                                    modal.reported = YES;
                                                    [ANNAppearanceDispatcher modalDismissed:root
                                                                                      count:1];
                                                }
                                                [root destroy];
                                              }
                                              answer:dismissal.answer
                                               value:modal.rootId];
    } @catch (NSException *exception) {
        // The dismissal goes back on the record so that the end step's guard rejects its answer if it is still open.
        if (!modal.dismissal) {
            modal.dismissal = dismissal;
        }
        @throw;
    } @finally {
        [self stopDriving:modal];
        [self resolvePendingOf:modal];
        [self cleanTopOfChain];
        [self presentReadyModals];
    }
}

#pragma mark - The top of UIKit's chain

// After a modal left, the modals closed below that UIKit now shows on top (nothing above them but foreign
// controllers) are dismissed without animation from the presenting controller of the lowest of them: no invisible
// modal stays presented and takes touches. Decided when the step runs. `revealing`: the layer they kept from the user,
// shown in that dismissal; `then` runs at its end.
+ (void)cleanTopOfChain {
    [self cleanTopOfChainRevealing:nil then:nil];
}

+ (void)cleanTopOfChainRevealing:(UIViewController *)revealing then:(dispatch_block_t)then {
    BOOL closedBelow = NO;
    for (ANNModal *modal in ANNModals) {
        if (modal.state == ANNModalStateClosedBelow && !modal.batch) {
            closedBelow = YES;
            break;
        }
    }
    if (!closedBelow) {
        if (then) {
            then();
        }
        return;
    }
    __weak UIViewController *weakRevealing = revealing;
    ANNGateStep *step = [[ANNGateStep alloc] initWithRun:^(dispatch_block_t done) {
      [ANNModalCoordinator runCleanRevealing:weakRevealing then:then done:done];
    }];
    [ANNPresentationGate enqueue:step];
}

+ (void)runCleanRevealing:(UIViewController *)revealing
                     then:(dispatch_block_t)then
                     done:(dispatch_block_t)done {
    NSMutableArray<ANNModal *> *top = [NSMutableArray new];
    for (ANNModal *modal in ANNModals.reverseObjectEnumerator) {
        if (modal.state == ANNModalStateWaiting) {
            continue;
        }
        if (modal.state != ANNModalStateClosedBelow || modal.batch) {
            break;
        }
        [top addObject:modal];
    }
    UIViewController *from = top.lastObject.root.presentingViewController;
    if (!from) {
        for (ANNModal *modal in top) {
            [self remove:modal];
        }
        done();
        if (then) {
            then();
        }
        return;
    }
    NSMutableArray<UIViewController *> *driven = [NSMutableArray new];
    for (ANNModal *modal in top) {
        [driven addObject:modal.root];
    }
    if (revealing) {
        [driven addObject:revealing];
    }
    [ANNAppearanceDispatcher driveAppearanceOf:driven];
    if (revealing) {
        [ANNAppearanceDispatcher beginTransitionShowing:@[ revealing ]];
    }
    [from dismissViewControllerAnimated:NO
                             completion:^{
                               for (ANNModal *modal in top) {
                                   [ANNModalCoordinator remove:modal];
                               }
                               done();
                               @try {
                                   if (then) {
                                       then();
                                   }
                               } @finally {
                                   [ANNAppearanceDispatcher stopDrivingAppearanceOf:driven];
                               }
                             }];
}

#pragma mark - The user

// UIKit's delegate call: guarded as the record's end steps (the drag sends the appearance events of what it moves).
+ (void)modalWillBeDraggedAway:(ANNModal *)modal {
    ANNAssertMainQueue();
    [self protectEndOf:modal
               showing:NO
                  step:^{
                    [ANNModalCoordinator willBeDraggedAway:modal];
                  }];
}

+ (void)willBeDraggedAway:(ANNModal *)modal {
    ANNModalState state = ANNSeenState(modal);
    BOOL dismissingLater = modal.state == ANNModalStateDismissing && !modal.dismissalStarted &&
                           !modal.batch;
    if (state != ANNModalStateShown && !dismissingLater) {
        return;
    }
    if (dismissingLater) {
        // A dismissal whose UIKit call didn't start waits for the drag, as in `Shown`.
        if (modal.step) {
            [ANNPresentationGate drop:modal.step];
            modal.step = nil;
        }
        if (modal.dismissal) {
            [modal.pending insertObject:modal.dismissal atIndex:0];
            modal.dismissal = nil;
        }
        modal.state = ANNModalStateShown;
    }
    BOOL withOldRoot = modal.state == ANNModalStateWithOldRoot;
    if (withOldRoot) {
        modal.stateBeforeRoot = ANNModalStateGesture;
    } else {
        modal.state = ANNModalStateGesture;
    }
    // The drag ends cancelled: the coordinator's completion says so. When it promises none, the next turn looks. Both
    // are set before the drive, so a failure in the drive still leaves the record a way out of `Gesture`.
    __weak ANNModal *weakModal = modal;
    id<UIViewControllerTransitionCoordinator> coordinator = modal.root.transitionCoordinator;
    BOOL queued = [coordinator
        animateAlongsideTransition:nil
                        completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
                          if (context.isCancelled) {
                              [ANNModalCoordinator dragCancelledOf:weakModal];
                          }
                        }];
    if (!queued) {
        dispatch_async(dispatch_get_main_queue(), ^{
          ANNModal *dragged = weakModal;
          if (dragged && !dragged.root.isBeingDismissed && dragged.root.presentingViewController) {
              [ANNModalCoordinator dragCancelledOf:dragged];
          }
        });
    }
    if (!withOldRoot) {
        [self driveDragOf:modal];
    }
}

// The drag reveals the layer under a modal that covered it (no modal closed below between them): the command reports
// both from now on, as the stack does for its swipe; UIKit's own callbacks of the layer under would send its
// `DidAppear` before the modal's `DidDisappear`.
+ (void)driveDragOf:(ANNModal *)modal {
    UIViewController *under = [self layerUnder:modal];
    if (modal.driven || !under || [self closedBelowUnder:modal].count > 0 ||
        ![self layerUnder:under hadDisappearedFor:modal]) {
        return;
    }
    modal.dragRevealed = under;
    [self drive:modal nodes:@[ modal.root, under ]];
    [ANNAppearanceDispatcher beginTransitionShowing:@[ under ]];
}

// A drag ended without closing: the layer under goes again (as a cancelled swipe of a stack).
+ (void)endDragDriveOf:(ANNModal *)modal {
    UIViewController *under = modal.dragRevealed;
    modal.dragRevealed = nil;
    if (!modal.driven) {
        return;
    }
    @try {
        if (under) {
            [ANNAppearanceDispatcher endTransitionHiding:@[ under ]
                                                 showing:@[]
                                                  events:nil
                                                  answer:nil
                                                   value:nil];
        }
    } @finally {
        [self stopDriving:modal];
    }
}

// From the transition coordinator's completion or the next turn, outside the runner's protection: guarded as the
// record's end steps (`DidDisappear` of the layer under goes from here).
+ (void)dragCancelledOf:(ANNModal *)modal {
    [self protectEndOf:modal
               showing:NO
                  step:^{
                    [ANNModalCoordinator cancelDragOf:modal];
                  }];
}

+ (void)cancelDragOf:(ANNModal *)modal {
    if (!modal || ANNSeenState(modal) != ANNModalStateGesture) {
        return;
    }
    @try {
        [self endDragDriveOf:modal];
    } @catch (NSException *exception) {
        // The drag is over all the same: the record leaves `Gesture`, so a later command of it and a batch that
        // waited for the drag go on. The guard logs the failure and rejects the dismissals that waited for the drag.
        [self dragEndedOf:modal runPending:NO];
        @throw;
    }
    [self dragEndedOf:modal runPending:YES];
}

+ (void)dragEndedOf:(ANNModal *)modal runPending:(BOOL)runPending {
    if (modal.state == ANNModalStateWithOldRoot) {
        modal.stateBeforeRoot = ANNModalStateShown;
        return;
    }
    if (modal.takenWithDrag) {
        modal.takenWithDrag = NO;
        modal.stateBeforeRoot = ANNModalStateShown;
        modal.state = ANNModalStateWithOldRoot;
        [self collectIdsOf:modal];
        // A `dismissAllModals` that waited for this drag doesn't take a modal that goes with the old root (as at its
        // start): the record leaves the batch, which goes on without it.
        ANNModalBatch *waited = modal.batch;
        if (waited) {
            modal.batch = nil;
            [waited.records removeObjectIdenticalTo:modal];
            [waited.counted removeObject:modal];
            [self batchDragEnded:waited];
        }
        return;
    }
    modal.state = ANNModalStateShown;
    ANNModalBatch *batch = modal.batch;
    if (batch) {
        // It joins the batch that waited for its drag.
        modal.state = ANNModalStateDismissing;
        [batch.counted addObject:modal];
        [self batchDragEnded:batch];
        return;
    }
    if (runPending) {
        [self runPendingOf:modal];
    }
}

// UIKit closed it for the user (a sheet swiped down, the dim or the outside of a popover tapped). The end steps of a
// dismissal without an answer: the layer under comes back when it had disappeared; behind modals closed below, only
// once UIKit removed them too.
+ (void)modalWasClosedByUser:(ANNModal *)modal {
    ANNAssertMainQueue();
    [self protectEndOf:modal
               showing:NO
                  step:^{
                    [ANNModalCoordinator closedByUser:modal];
                  }];
}

+ (void)closedByUser:(ANNModal *)modal {
    if (!modal || modal.state == ANNModalStateGone || modal.state == ANNModalStateClosedBelow ||
        modal.state == ANNModalStateWaiting ||
        (modal.state == ANNModalStateDismissing && modal.dismissalStarted)) {
        return;
    }
    if (modal.step) {
        [ANNPresentationGate drop:modal.step];
        modal.step = nil;
    }
    if (modal.dismissal) {
        [modal.pending insertObject:modal.dismissal atIndex:0];
        modal.dismissal = nil;
    }
    // A drag a `dismissAllModals` waited for completed: it went by itself, not counted.
    ANNModalBatch *batch = modal.batch;
    BOOL batchWaitedForDrag = batch && modal.state == ANNModalStateGesture;
    if (batch) {
        modal.batch = nil;
        [batch.records removeObjectIdenticalTo:modal];
        [batch.counted removeObject:modal];
    }
    UIViewController *under = [self layerUnder:modal];
    BOOL revealed = [self layerUnder:under hadDisappearedFor:modal];
    BOOL behindClosed = [self closedBelowUnder:modal].count > 0;
    [self collectIdsOf:modal];
    [ANNClosedByUser addObject:@{ANNClosedIdsKey : modal.ids, ANNClosedRootIdKey : modal.rootId}];
    [self remove:modal];
    __weak UIViewController *weakUnder = under;
    BOOL deferred = revealed && behindClosed;
    // Also the `then` of the clean-up dismissal, inside UIKit's completion: guarded as the record's end step.
    dispatch_block_t end = ^{
      [ANNModalCoordinator protectEndOf:modal
                                showing:NO
                                   step:^{
                                     // The state waited for these end steps (after `DidAppear` of the layer under).
                                     if (deferred) {
                                         [ANNNavigationState releaseUpdates];
                                     }
                                     UIViewController *shown = revealed ? weakUnder : nil;
                                     [ANNModalCoordinator userCloseEndedOf:modal under:shown];
                                   }];
    };
    if (deferred) {
        [ANNNavigationState holdUpdates];
        [self cleanTopOfChainRevealing:under then:end];
    } else {
        end();
        [self cleanTopOfChain];
    }
    if (batchWaitedForDrag) {
        [self batchDragEnded:batch];
    } else if (batch) {
        [self queueBatchIfReady:batch];
    }
    [self presentReadyModals];
}

+ (void)userCloseEndedOf:(ANNModal *)modal under:(UIViewController *)under {
    UIViewController *root = modal.root;
    [ANNZoomAnchor transitionEndedOf:root];
    @try {
        [ANNAppearanceDispatcher endTransitionHiding:@[ root ]
                                             showing:under ? @[ under ] : @[]
                                              events:^{
                                                if (!modal.reported) {
                                                    modal.reported = YES;
                                                    [ANNAppearanceDispatcher modalDismissed:root
                                                                                      count:1];
                                                }
                                                [root destroy];
                                              }
                                              answer:nil
                                               value:nil];
    } @finally {
        // The drag's own report of the layer under ends with it.
        modal.dragRevealed = nil;
        [self stopDriving:modal];
        [self resolvePendingOf:modal];
    }
}

+ (void)collectIdsOf:(ANNModal *)modal {
    NSMutableSet<NSString *> *ids = [NSMutableSet new];
    ANNCollectIds(modal.root, ids);
    modal.ids = ids;
}

// The user tried to close a held modal.
+ (void)userTriedToClose:(ANNModal *)modal {
    ANNAssertMainQueue();
    ANNModalState state = ANNSeenState(modal);
    if (state != ANNModalStateShown && state != ANNModalStateGesture &&
        state != ANNModalStateDismissing) {
        return;
    }
    UIViewController *screen = [ANNAppearanceDispatcher visibleScreenOf:modal.root];
    NSString *componentId = screen.layoutInfo.componentId ?: modal.rootId;
    [modal.root.eventEmitter sendModalAttemptedToDismiss:componentId];
}

// The finger chose another detent. UIKit tells also of a drag that ends where the sheet stood (its selected identifier
// goes from none to the lowest): only a change is sent.
+ (void)modal:(ANNModal *)modal detentChanged:(id)detent {
    ANNAssertMainQueue();
    if (modal.state == ANNModalStateGone || modal.state == ANNModalStateClosedBelow) {
        return;
    }
    if ([detent isEqual:modal.knownDetent]) {
        return;
    }
    modal.knownDetent = detent;
    UIViewController *screen = [ANNAppearanceDispatcher visibleScreenOf:modal.root];
    NSString *componentId = screen.layoutInfo.componentId ?: modal.rootId;
    [modal.root.eventEmitter sendSheetDetentChanged:componentId detent:detent];
}

// The detent a sheet stands at by its options: `selectedDetent` when it is one of the detents, else the lowest (UIKit's
// rule). Heights: `medium` half the window, `large` the window, a number its points, `content` the content's measure
// (the window's height when nothing is measured yet); iOS 15 has only `medium` and `large`.
+ (id)knownDetentOf:(ANNModal *)modal {
    UIViewController *root = modal.root;
    ANNSheetOptions *sheet = [root resolveOptionsWithDefault].modal.sheet;
    NSArray *detents = sheet.detents.count > 0 ? sheet.detents : @[ @"large" ];
    id selected = [sheet.selectedDetent get];
    if (selected && [detents containsObject:selected]) {
        return selected;
    }
    CGFloat full = root.viewIfLoaded.window.bounds.size.height;
    if (full <= 0) {
        full = ApexNavigation.mainWindow.bounds.size.height;
    }
    id lowest = nil;
    CGFloat lowestHeight = CGFLOAT_MAX;
    for (id value in detents) {
        CGFloat height = 0;
        if ([value isEqual:@"medium"]) {
            height = full / 2;
        } else if ([value isEqual:@"large"]) {
            height = full;
        } else if (@available(iOS 16.0, *)) {
            if ([value isKindOfClass:NSNumber.class]) {
                height = MIN([value doubleValue], full);
            } else if ([value isEqual:@"content"]) {
                CGFloat content = [ANNSheetPresenter contentHeightOf:root
                                                               width:root.view.bounds.size.width];
                height = content > 0 ? MIN(content, full) : full;
            } else {
                continue;
            }
        } else {
            continue;
        }
        if (height < lowestHeight) {
            lowestHeight = height;
            lowest = value;
        }
    }
    return lowest ?: @"large";
}

#pragma mark - dismissAllModals

+ (void)dismissAllModals:(NSDictionary *)options answer:(ANNCommandAnswer *)answer {
    ANNAssertMainQueue();
    NSMutableArray<ANNModal *> *taken = [NSMutableArray new];
    for (ANNModal *modal in ANNModals) {
        if (modal.state == ANNModalStateGone || modal.state == ANNModalStateWithOldRoot ||
            modal.batch) {
            continue;
        }
        [taken addObject:modal];
    }
    // The options give only whether the top modal leaves with its animation; a value that can't be read rejects with
    // the id of the top modal's visible screen.
    BOOL animated = YES;
    if (options.count > 0 && taken.count > 0) {
        NSString *unreadablePath = nil;
        ANNNavigationOptions *read = [ANNOptionsReader optionsFrom:options
                                                    unreadablePath:&unreadablePath];
        if (unreadablePath) {
            UIViewController *root = taken.lastObject.root;
            UIViewController *screen = [ANNAppearanceDispatcher visibleScreenOf:root];
            NSString *componentId = screen.layoutInfo.componentId ?: taken.lastObject.rootId;
            [answer reject:[ANNErrorTexts unreadableOption:unreadablePath componentId:componentId]];
            return;
        }
        Bool *enabled = read.animations.dismissModal.exit.enabled;
        animated = !(enabled.hasValue && !enabled.get);
    }
    if (taken.count == 0) {
        // N6: a running batch still has its modals on screen; this one answers at its end.
        ANNModalBatch *running = ANNBatches.lastObject;
        if (running) {
            [running.waiters addObject:answer];
        } else {
            [answer resolve:@""];
        }
        return;
    }
    ANNModalBatch *batch = [ANNModalBatch new];
    batch.answer = answer;
    batch.animated = animated;
    [ANNBatches addObject:batch];
    for (ANNModal *modal in taken) {
        [self take:modal intoBatch:batch];
    }
    // The command doesn't wait for a show or a dismissal of its own animation that runs: it ends at its
    // end state now, as at a reload, and the batch's dismissal follows on the next turn. UIKit's own transition and
    // the user's drag still end first: UIKit drops a dismissal asked for while they run.
    for (ANNModal *modal in taken) {
        [modal.transitions cancel];
    }
    [self queueBatchIfReady:batch];
}
+ (void)take:(ANNModal *)modal intoBatch:(ANNModalBatch *)batch {
    modal.batch = batch;
    [batch.records addObject:modal];
    switch (modal.state) {
        case ANNModalStateWaiting:
            // Cancelled without its own event, counted; its root goes at the batch's end.
            [self stopWaitOf:modal];
            [modal.showAnswer resolve:modal.rootId];
            if (modal.chainStep) {
                void (^chainStep)(BOOL) = modal.chainStep;
                modal.chainStep = nil;
                chainStep(NO);
            }
            modal.state = ANNModalStateDismissing;
            modal.dismissalStarted = NO;
            [batch.counted addObject:modal];
            break;
        case ANNModalStatePresenting:
        case ANNModalStateShown:
            modal.state = ANNModalStateDismissing;
            [batch.counted addObject:modal];
            break;
        case ANNModalStateGesture:
            batch.dragsPending++;
            break;
        case ANNModalStateDismissing:
            // Its own command resolves at the batch's end; one whose UIKit dismissal runs ends with the batch too
            // (one event).
            if (modal.step) {
                [ANNPresentationGate drop:modal.step];
                modal.step = nil;
            }
            if (modal.dismissal) {
                [modal.pending addObject:modal.dismissal];
                modal.dismissal = nil;
            }
            if (!modal.reported) {
                [batch.counted addObject:modal];
            }
            break;
        case ANNModalStateClosedBelow:
        case ANNModalStateWithOldRoot:
        case ANNModalStateGone:
            break;
    }
}

// A drag the batch waited for ended.
+ (void)batchDragEnded:(ANNModalBatch *)batch {
    if (batch.dragsPending > 0) {
        batch.dragsPending--;
    }
    [self queueBatchIfReady:batch];
}

// No drag left to wait for: the batch's dismissal waits for the gate.
+ (void)queueBatchIfReady:(ANNModalBatch *)batch {
    if (batch.dragsPending > 0 || batch.step || ![ANNBatches containsObject:batch]) {
        return;
    }
    __weak ANNModalBatch *weakBatch = batch;
    ANNGateStep *step = [[ANNGateStep alloc] initWithRun:^(dispatch_block_t done) {
      [ANNModalCoordinator runBatch:weakBatch done:done];
    }];
    batch.step = step;
    [ANNPresentationGate enqueue:step];
}

// The top modal of the batch leaves with its animation (unless the options turn it off), every other at once: one
// UIKit dismissal from the presenting controller of the lowest of them. The layer under them appears at the start
// when it had disappeared.
+ (void)runBatch:(ANNModalBatch *)batch done:(dispatch_block_t)done {
    if (!batch) {
        done();
        return;
    }
    NSMutableArray<ANNModal *> *presented = [NSMutableArray new];
    for (ANNModal *modal in batch.records) {
        if (modal.state != ANNModalStateGone && modal.root.presentingViewController) {
            [presented addObject:modal];
        }
    }
    // The layer under the lowest modal of the batch still listed (also one UIKit dismissed already on its own
    // command: its dismissal began that layer's appearance).
    ANNModal *lowest = presented.firstObject;
    ANNModal *lowestListed = nil;
    BOOL revealed = NO;
    for (ANNModal *modal in batch.records) {
        if (modal.state == ANNModalStateGone) {
            continue;
        }
        lowestListed = lowestListed ?: modal;
        revealed = revealed || modal.coversUnder;
    }
    UIViewController *under = lowestListed ? [self layerUnder:lowestListed] : nil;
    revealed = revealed && under != nil;
    UIViewController *from = lowest.root.presentingViewController;
    NSMutableArray<UIViewController *> *driven = [NSMutableArray new];
    for (ANNModal *modal in batch.records) {
        [ANNComponentViewController nodeWillLeave:modal.root];
        [driven addObject:modal.root];
    }
    if (revealed) {
        [driven addObject:under];
    }
    [ANNAppearanceDispatcher driveAppearanceOf:driven];
    if (revealed) {
        [ANNAppearanceDispatcher beginTransitionShowing:@[ under ]];
    }
    __weak ANNModalBatch *weakBatch = batch;
    void (^ended)(void) = ^{
      ANNModalBatch *ending = weakBatch;
      [ANNModalCoordinator protectBatch:ending
                                   step:^{
                                     @try {
                                         [ANNModalCoordinator batchEnded:ending
                                                                   under:revealed ? under : nil];
                                     } @finally {
                                         [ANNAppearanceDispatcher stopDrivingAppearanceOf:driven];
                                     }
                                   }];
    };
    if (!from) {
        ended();
        done();
        return;
    }
    // The top modal plays its own dismissal (its `animations.dismissModal`), unless the command's options turned the
    // animation off.
    ANNModal *top = presented.lastObject;
    ANNTransitionPlan *plan =
        batch.animated && top
            ? [top.presentation dismissPlanOf:top.root
                                    animation:[top.root resolveOptionsWithDefault]
                                                  .animations.dismissModal
                                        under:under]
            : nil;
    [self markCoreDismissalOf:top.root plan:plan record:top];
    __weak ANNModal *weakTop = top;
    [from dismissViewControllerAnimated:batch.animated
                             completion:^{
                               ANNOnMainQueue(^{
                                 [ANNModalCoordinator coreDismissalEndedOf:weakTop];
                                 done();
                                 ended();
                               });
                             }];
}

// The end: `DidDisappear` of every modal's screens that showed, `DidAppear` of the layer under when it had disappeared,
// the state, one `ModalDismissed` with the number of modals not reported before and the visible screen of the topmost
// of them, every content stops, the answer `""`; the commands and shows that waited for a modal of the batch resolve
// with its root's id.
+ (void)batchEnded:(ANNModalBatch *)batch under:(UIViewController *)under {
    if (!batch || ![ANNBatches containsObject:batch]) {
        return;
    }
    [ANNBatches removeObjectIdenticalTo:batch];
    NSArray<ANNModal *> *records = batch.records.copy;
    NSMutableArray<UIViewController *> *hidden = [NSMutableArray new];
    ANNModal *topCounted = nil;
    NSUInteger count = 0;
    for (ANNModal *modal in records) {
        [self remove:modal];
        [ANNZoomAnchor transitionEndedOf:modal.root];
        [hidden addObject:modal.root];
        if ([batch.counted containsObject:modal]) {
            count++;
            topCounted = modal;
        }
    }
    @try {
        [ANNAppearanceDispatcher endTransitionHiding:hidden
                                             showing:under ? @[ under ] : @[]
                                              events:^{
                                                if (count > 0) {
                                                    [ANNAppearanceDispatcher
                                                        modalDismissed:topCounted.root
                                                                 count:count];
                                                }
                                                for (ANNModal *modal in records) {
                                                    modal.reported = YES;
                                                    [modal.root destroy];
                                                }
                                              }
                                              answer:batch.answer
                                               value:@""];
    } @finally {
        for (ANNModal *modal in records) {
            [modal.showAnswer resolve:modal.rootId];
            [modal.dismissal.answer resolve:modal.rootId];
            modal.dismissal = nil;
            [self resolvePendingOf:modal];
        }
        for (ANNCommandAnswer *waiter in batch.waiters) {
            [waiter resolve:@""];
        }
        [self cleanTopOfChain];
        [self presentReadyModals];
    }
}

#pragma mark - setRoot (rows 12 … 14)

+ (void)takeForNewRoot {
    ANNAssertMainQueue();
    for (ANNModal *modal in ANNModals.copy) {
        switch (modal.state) {
            case ANNModalStateWaiting:
                // It would only show over the root that goes: it never shows.
                [self cancel:modal rejection:nil dismissal:nil sendsEvent:NO];
                break;
            case ANNModalStatePresenting:
            case ANNModalStateShown:
            case ANNModalStateClosedBelow:
                // A modal closed below kept its ids when its nodes went.
                if (modal.state != ANNModalStateClosedBelow) {
                    [self collectIdsOf:modal];
                }
                modal.stateBeforeRoot = modal.state;
                modal.state = ANNModalStateWithOldRoot;
                break;
            case ANNModalStateGesture:
                modal.takenWithDrag = YES;
                break;
            case ANNModalStateDismissing:
            case ANNModalStateWithOldRoot:
            case ANNModalStateGone:
                break;
        }
    }
}

+ (void)restoreTakenForNewRoot {
    ANNAssertMainQueue();
    for (ANNModal *modal in ANNModals.copy) {
        if (modal.state == ANNModalStateWithOldRoot) {
            modal.state = modal.stateBeforeRoot;
            // A command that waited for its show or its drag runs now (row 14).
            [self runPendingOf:modal];
        }
        modal.takenWithDrag = NO;
    }
}

+ (NSArray<UIViewController *> *)takeAtReplacement {
    ANNAssertMainQueue();
    NSMutableArray<UIViewController *> *roots = [NSMutableArray new];
    NSMutableArray<ANNModal *> *taken = [NSMutableArray new];
    for (ANNModal *modal in ANNModals.copy) {
        if (modal.state == ANNModalStateWaiting) {
            [self cancel:modal rejection:nil dismissal:nil sendsEvent:NO];
            continue;
        }
        [self stopWaitOf:modal];
        [self remove:modal];
        [roots addObject:modal.root];
        [taken addObject:modal];
    }
    // Their commands and shows that still wait resolve with their roots' ids (2.6 g); the chain of the `setRoot` that
    // showed one was superseded already.
    for (ANNModal *modal in taken) {
        modal.chainStep = nil;
        [modal.showAnswer resolve:modal.rootId];
        [modal.dismissal.answer resolve:modal.rootId];
        modal.dismissal = nil;
        [self resolvePendingOf:modal];
    }
    NSArray<ANNModalBatch *> *batches = ANNBatches.copy;
    [ANNBatches removeAllObjects];
    for (ANNModalBatch *batch in batches) {
        if (batch.step) {
            [ANNPresentationGate drop:batch.step];
        }
        [batch.answer resolve:@""];
        for (ANNCommandAnswer *waiter in batch.waiters) {
            [waiter resolve:@""];
        }
    }
    return roots;
}

+ (NSArray<UIViewController *> *)takeAllForReload {
    ANNAssertMainQueue();
    NSMutableArray<UIViewController *> *roots = [NSMutableArray new];
    NSArray<ANNModal *> *modals = ANNModals.copy;
    for (ANNModal *modal in modals) {
        [self stopWaitOf:modal];
        modal.state = ANNModalStateGone;
        [roots addObject:modal.root];
    }
    [ANNModals removeAllObjects];
    // A custom transition running now ends at its end state (8), once the records are gone: the end steps its
    // completion would run find nothing (a reload answers nothing, N4).
    for (ANNModal *modal in modals) {
        [modal.transitions cancel];
    }
    for (ANNModalBatch *batch in ANNBatches) {
        if (batch.step) {
            [ANNPresentationGate drop:batch.step];
        }
    }
    [ANNBatches removeAllObjects];
    [ANNClosedByUser removeAllObjects];
    return roots;
}

#pragma mark - Questions

+ (UIViewController *)visibleLayout {
    ANNAssertMainQueue();
    for (ANNModal *modal in ANNModals.reverseObjectEnumerator) {
        // A modal being dismissed shows until its end steps remove it: a state asked for meanwhile still names it, and
        // the state of the layer under goes after its appearance.
        if (ANNIsOnScreen(modal) || ANNSeenState(modal) == ANNModalStateDismissing) {
            return modal.root;
        }
    }
    UIViewController *root = ApexNavigation.mainWindow.rootViewController;
    return root.isLayoutNode ? root : nil;
}

+ (BOOL)isWithOldRoot:(UIViewController *)node {
    ANNAssertMainQueue();
    if (ANNModals.count == 0) {
        return NO;
    }
    return [self modalOf:node].state == ANNModalStateWithOldRoot;
}

+ (NSArray<UIViewController *> *)modalRoots {
    ANNAssertMainQueue();
    NSMutableArray<UIViewController *> *roots = [NSMutableArray new];
    for (ANNModal *modal in ANNModals) {
        if (!modal.root.nodeHelper.destroyed) {
            [roots addObject:modal.root];
        }
    }
    return roots;
}

#pragma mark - What changes in a modal

// One call per merge: the modules find the presented root themselves and act only when it is presented: the sheet takes
// only the fields that came, the size its resolved options.
+ (void)optionsMerged:(ANNNavigationOptions *)options into:(UIViewController *)node {
    ANNAssertMainQueue();
    if (ANNModals.count == 0 || node.nodeHelper.destroyed) {
        return;
    }
    ANNModal *modal = [self modalOf:node];
    if (!modal || modal.state == ANNModalStateWaiting || modal.state == ANNModalStateGone) {
        return;
    }
    ANNModalOptions *merge = options.modal;
    if (merge.sheet.hasValue) {
        [ANNSheetPresenter mergeOptions:merge.sheet ofViewController:node];
        // A detent set from the options sends nothing and is the one the sheet stands at now; new detents without
        // the one it stood at move it to the selected or the lowest.
        if (modal.presentation.isSheet &&
            (merge.sheet.selectedDetent.hasValue ||
             (merge.sheet.detents && ![merge.sheet.detents containsObject:modal.knownDetent]))) {
            modal.knownDetent = [self knownDetentOf:modal];
        }
    }
    [ANNModalSizePresenter mergeOptions:merge ofViewController:node];
    if (merge.swipeToDismiss.hasValue) {
        [ANNModalPresentation applyHoldTo:modal.root];
    }
    if (merge.testID.hasValue || merge.sheet.testID.hasValue) {
        [ANNModalPresentation applyTestIDTo:modal.root sheet:modal.presentation.isSheet];
    }
}

+ (void)visibleScreenChangedIn:(UIViewController *)node {
    ANNAssertMainQueue();
    if (ANNModals.count == 0) {
        return;
    }
    ANNModal *modal = [self modalOf:node];
    if (!modal || modal.state == ANNModalStateWaiting || modal.state == ANNModalStateGone ||
        modal.state == ANNModalStateClosedBelow) {
        return;
    }
    [ANNModalPresentation applyHoldTo:modal.root];
}

@end

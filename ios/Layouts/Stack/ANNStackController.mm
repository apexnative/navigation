#import "ANNStackController.h"
#import "ANNAnimationRules.h"
#import "ANNAppDirection.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNComponentViewController.h"
#import "ANNElementFinder.h"
#import "ANNErrorTexts.h"
#import "ANNParking.h"
#import "ANNPendingLayers.h"
#import "ANNScreenBars.h"
#import "ANNSheetPresenter.h"
#import "ANNShownScreens.h"
#import "ANNStackAnimatingDelegate.h"
#import "ANNStackPopAnimationOptions.h"
#import "ANNStackPresenter.h"
#import "ANNSwipePopInteraction.h"
#import "ANNSystemUI.h"
#import "ANNTopBarPresenter.h"
#import "ANNTransitionAnimator.h"
#import "ANNTransitionPlan.h"
#import "ANNZoomAnchor.h"
#import "InteractivePopGestureDelegate.h"
#import <React/RCTLog.h>
#import <React/RCTUtils.h>

NSString *const ANNBackButtonDefaultId = @"ApexNavigation.back";

// How long a lower screen renders in the stack's view before it leaves it anyway; it waits for nobody.
static const NSTimeInterval ANNLowerScreenRenderLimit = 0.5;

// How many times the delegate's recompute at the end of an operation waits for a transition that still runs.
static const NSUInteger ANNGateRecomputeTries = 8;

// The ready handler key of a lower screen's render.
static NSString *const ANNLowerRenderReadyKey = @"ApexNavigation.lowerRender";

NSString *ANNStackKeyOfLayout(ANNLayoutNode *node) {
    if (node.key.length > 0) {
        return node.key;
    }
    if (node.type == ANNLayoutTypeArrangement) {
        NSString *primaryKey = node.children.firstObject.key;
        return primaryKey.length > 0 ? primaryKey : nil;
    }
    return nil;
}

// UIKit's navigation controller answers its bar as its delegate without saying so in its header: this makes its
// answer reachable from the stack's own.
@interface UINavigationController (ANNNavigationBarDelegate) <UINavigationBarDelegate>
@end

static BOOL ANNIsActive(UIGestureRecognizer *recognizer) {
    UIGestureRecognizerState state = recognizer.state;
    return state == UIGestureRecognizerStateBegan || state == UIGestureRecognizerStateChanged;
}

// How far from the leading edge of the stack's view the stack's own swipe back starts, in points: the strip the side
// menu leaves to the swipe back (`ANNSideMenuEdgeWidth`).
static const CGFloat ANNOwnSwipeEdgeWidth = 20;

@interface ANNStackController () <ANNStackDisplay, ANNStackAnimating>
@end

@implementation ANNStackController {
    NSMapTable<UIViewController *, NSString *> *_keys;
    // The views that wait in the stack's view (a push or a `setStackRoot` that waits, a lower screen that renders).
    ANNParking *_parking;
    // The lower screens to render once the stack shows, and those rendering now, each with its number.
    NSMutableArray<UIViewController *> *_lowerToRender;
    NSMapTable<UIViewController *, NSNumber *> *_lowerRendering;
    NSUInteger _lowerNumber;
    // The core's own calls to UIKit run: UIKit's calls into the overrides pass then.
    NSInteger _coreCalls;
    InteractivePopGestureDelegate *_edgeSwipeDelegate;
    InteractivePopGestureDelegate *_contentSwipeDelegate;
    // The delegates of UIKit's More navigation's swipes while it shows the stack's screens.
    InteractivePopGestureDelegate *_moreEdgeSwipeDelegate;
    InteractivePopGestureDelegate *_moreContentSwipeDelegate;
    __weak UITouch *_swipeTouch;
    __weak UITouch *_heldTouch;
    __weak UIViewController *_lastTop;
    // The UIKit transition the stack already took as the swipe or follows as one of UIKit's own.
    __weak id<UIViewControllerTransitionCoordinator> _seenTransition;
    // The screen the visibilities of the bars were last set for (the shared part of the bar).
    __weak UIViewController *_sharedBarScreen;
    // The lower screens whose render is being set up: a ready callback inside the setup waits for its end.
    NSHashTable<UIViewController *> *_lowerSettingUp;
    // UIKit's More navigation that showed the stack's screens last (the tab open from More).
    __weak UINavigationController *_moreNavigation;
    // The delegate that also offers UIKit a transition object, set only while a custom animation may be needed; the
    // queue's plan of the change UIKit starts next, until UIKit asks or the operation ends; the custom transition that
    // runs.
    ANNStackAnimatingDelegate *_animatingDelegate;
    ANNTransitionPlan *_pendingPlan;
    ANNStackChange _pendingChange;
    __weak ANNTransitionAnimator *_runningAnimator;
    // The interaction of the swipe back that plays the top's own pop, until UIKit asks for it.
    __weak ANNSwipePopInteraction *_swipeInteraction;
    // The stack's own swipe from the leading edge, on while the top's pop follows the finger: UIKit runs its swipe
    // without asking the delegate for a transition, so that one would only slide.
    UIPanGestureRecognizer *_ownSwipe;
    InteractivePopGestureDelegate *_ownSwipeDelegate;
    BOOL _gateUpdateScheduled;
    // The stack is made (its helper set up): its options can be resolved.
    BOOL _madeUp;
    // The direction the last push forced on the stack's view and bar, for a view that loads later.
    UISemanticContentAttribute _forcedDirection;
    // Whether the bar was hidden at the last layout: UIKit's own hides don't go through the setter (F6).
    BOOL _barSeenHidden;
    // The navigation whose bar the stack hid for the keyboard (`topBar.hideOnKeyboard`), until the keyboard closes.
    __weak UINavigationController *_keyboardHidBarOf;
}

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(NSArray<UIViewController *> *)childViewControllers {
    self = [super initWithNibName:nil bundle:nil];
    if (self) {
        _queue = [[ANNStackQueue alloc] initWithDisplay:self];
        _keys = [NSMapTable weakToStrongObjectsMapTable];
        _parking = [ANNParking new];
        _lowerToRender = [NSMutableArray new];
        _lowerRendering = [NSMapTable strongToStrongObjectsMapTable];
        _lowerSettingUp = [NSHashTable weakObjectsHashTable];
        // The stack is its own delegate, without the choice of an animation (the content swipe works); the animating
        // delegate takes its place only while a custom animation may be needed (`updateAnimationGateWith:`).
        _animatingDelegate = [[ANNStackAnimatingDelegate alloc] initWithStack:self];
        self.delegate = self;
        // `topBar.hideOnKeyboard`: the stack hides the bar as the keyboard shows and shows it again once it closed.
        [NSNotificationCenter.defaultCenter addObserver:self
                                               selector:@selector(keyboardWillShow:)
                                                   name:UIKeyboardWillShowNotification
                                                 object:nil];
        [NSNotificationCenter.defaultCenter addObserver:self
                                               selector:@selector(keyboardDidHide:)
                                                   name:UIKeyboardDidHideNotification
                                                 object:nil];
        NSArray<UIViewController *> *entries = childViewControllers ?: @[];
        if (entries.count > 0) {
            [_queue setInitialEntries:entries];
            [self stackChange:ANNStackChangeReplace toEntries:entries animated:NO plan:nil];
            for (NSUInteger index = 0; index < entries.count; index++) {
                [self stackEntryJoins:entries[index] first:index == 0];
            }
            // The top renders first and decides the readiness; the screens under it once it showed.
            [_lowerToRender
                addObjectsFromArray:[entries subarrayWithRange:NSMakeRange(0, entries.count - 1)]];
        }
    }
    [self.nodeHelper setUpWithLayoutInfo:layoutInfo
                                 creator:creator
                                 options:options
                          defaultOptions:defaultOptions
                               presenter:presenter
                            eventEmitter:eventEmitter];
    _madeUp = YES;
    return self;
}

#pragma mark - The view

- (void)viewDidLoad {
    [super viewDidLoad];
    // A push forced the direction before the view loaded: the view takes it now.
    if (_forcedDirection != UISemanticContentAttributeUnspecified) {
        self.view.semanticContentAttribute = _forcedDirection;
    }
    [self installSwipeBackDelegates];
}

// The bar shown or hidden by UIKit itself (`hidesBarsOnSwipe`, `hidesBarsOnTap`, the keyboard) lays the stack out
// again: the status bar of `hideWithTopBar` is asked again. UIKit's More navigation is not watched.
- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    BOOL hidden = [ANNSystemUI barHiddenOf:self];
    if (hidden != _barSeenHidden) {
        _barSeenHidden = hidden;
        [ANNSystemUI setNeedsUpdateOfGroups:ANNSystemUIGroupStatusBar];
    }
    [self swipeBackFollowsBar];
}

// Every explicit show or hide of the bar (the core's, the keyboard's return, an app's): the status bar of
// `hideWithTopBar` is asked again.
- (void)setNavigationBarHidden:(BOOL)hidden animated:(BOOL)animated {
    BOOL changed = hidden != self.navigationBarHidden;
    [super setNavigationBarHidden:hidden animated:animated];
    if (changed) {
        [ANNSystemUI setNeedsUpdateOfGroups:ANNSystemUIGroupStatusBar];
    }
    [self swipeBackFollowsBar];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self installSwipeBackDelegates];
    // The first top's own `animations.pop`: the delegate is right before the stack first shows.
    [self updateAnimationGateAfterMerge];
}

// The top showed: the lower screens of a stack made with several make their content now.
- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    // A stack off screen at a change of the scene's direction takes the new swipe edge as it shows.
    if ([ANNAppDirection navigationMissedSceneDirection:self]) {
        [self dropForcedDirection];
    }
    [self renderLowerEntriesWhenShown];
}

// A root's `viewDidAppear:` comes as the window takes it, while the root's transition still drives it (the dissolve):
// the top's `DidAppear` comes at that transition's end, and the lower screens after it.
- (void)renderLowerEntriesWhenShown {
    if (_lowerToRender.count == 0 || self.nodeHelper.destroyed || !self.viewIfLoaded.window) {
        return;
    }
    if (![ANNAppearanceDispatcher isAppearanceDriven:self]) {
        [self renderLowerEntriesNow];
        return;
    }
    __weak ANNStackController *weakSelf = self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.05 * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     [weakSelf renderLowerEntriesWhenShown];
                   });
}

// The stack left the screen (its tab, a full-screen modal over it): what waits for its screen runs now. Not when it
// goes with its container (a new root, a modal dismissed): its destroy rejects what waits then.
- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    if (![self goesWithItsContainer]) {
        [_queue stackWentOffScreen];
        // The tabs check the tab's `popToRootOnBlur` (disappeared and no longer selected).
        UIViewController *parent = self.layoutParent;
        if ([parent respondsToSelector:@selector(childDidDisappear:)]) {
            [(id<ANNLayoutProtocol>)parent childDidDisappear:self];
        }
    }
}

- (BOOL)goesWithItsContainer {
    if ([ANNAppearanceDispatcher isAppearanceDriven:self]) {
        return YES;
    }
    for (UIViewController *node = self; node; node = node.parentViewController) {
        if (node.isBeingDismissed || node.isMovingFromParentViewController) {
            return YES;
        }
    }
    return NO;
}

// Each swipe back gets the stack's delegate over UIKit's, with the bars' swipe waiting for it.
- (void)installSwipeBackDelegates {
    UIGestureRecognizer *barSwipe = self.barHideOnSwipeGestureRecognizer;
    UIGestureRecognizer *edge = self.interactivePopGestureRecognizer;
    _edgeSwipeDelegate = [InteractivePopGestureDelegate installOn:edge
                                                            stack:self
                                                          content:NO
                                                         barSwipe:barSwipe];
    if (@available(iOS 26.0, *)) {
        UIGestureRecognizer *content = self.interactiveContentPopGestureRecognizer;
        _contentSwipeDelegate = [InteractivePopGestureDelegate installOn:content
                                                                   stack:self
                                                                 content:YES
                                                                barSwipe:barSwipe];
    }
    if (!_ownSwipe) {
        _ownSwipe = [[UIPanGestureRecognizer alloc] initWithTarget:self
                                                            action:@selector(ownSwipeBack:)];
        _ownSwipe.enabled = _animatingDelegate.answersInteraction;
        [self.view addGestureRecognizer:_ownSwipe];
    }
    _ownSwipeDelegate = [InteractivePopGestureDelegate installOn:_ownSwipe
                                                           stack:self
                                                         content:NO
                                                        barSwipe:barSwipe];
    [self updateSwipeBack];
}

// UIKit's More navigation shows the stack's screens: its swipes take the stack's `popGesture` and hold, and its bars'
// swipe waits for them, as on the stack's own navigation.
- (void)installSwipeBackDelegatesOn:(UINavigationController *)moreNavigation {
    UIGestureRecognizer *barSwipe = moreNavigation.barHideOnSwipeGestureRecognizer;
    UIGestureRecognizer *edge = moreNavigation.interactivePopGestureRecognizer;
    _moreEdgeSwipeDelegate = [InteractivePopGestureDelegate installOn:edge
                                                                stack:self
                                                              content:NO
                                                             barSwipe:barSwipe];
    if (@available(iOS 26.0, *)) {
        UIGestureRecognizer *content = moreNavigation.interactiveContentPopGestureRecognizer;
        _moreContentSwipeDelegate = [InteractivePopGestureDelegate installOn:content
                                                                       stack:self
                                                                     content:YES
                                                                    barSwipe:barSwipe];
    }
    [self updateSwipeBack];
}

#pragma mark - Entries and keys

- (UIViewController *)entryOf:(UIViewController *)node {
    for (UIViewController *entry = node; entry; entry = entry.layoutParent) {
        if (entry.layoutParent == self) {
            return entry;
        }
    }
    return nil;
}

- (void)setKey:(NSString *)key ofEntry:(UIViewController *)entry {
    if (key.length > 0) {
        [_keys setObject:key forKey:entry];
    } else {
        [_keys removeObjectForKey:entry];
    }
}

- (UIViewController *)entryWithKey:(NSString *)key {
    for (UIViewController *entry in _queue.entries.reverseObjectEnumerator) {
        if ([[_keys objectForKey:entry] isEqualToString:key]) {
            return entry;
        }
    }
    return nil;
}

#pragma mark - ANNLayoutProtocol

// The top of what shows: a screen that waits for its render isn't the current child yet. Safe before the queue exists
// (the helper's setup asks it during init).
- (UIViewController *)getCurrentChild {
    return _queue.shown.lastObject;
}

- (NSArray<UIViewController *> *)layoutChildren {
    return _queue.heldNodes ?: @[];
}

// The top renders; the screens under it make their content once it showed. It takes no ready callback: the waits of a
// root and of a modal wait on the screens it shows.
- (void)render {
    [[self getCurrentChild] render];
}

- (UIViewController *)systemUIChild {
    return [self getCurrentChild];
}

// UIKit doesn't pass the deferred edges from a stack to its screen: the stack does.
- (UIViewController *)childViewControllerForScreenEdgesDeferringSystemGestures {
    UIViewController *top = [self getCurrentChild];
    return top ?: [super childViewControllerForScreenEdgesDeferringSystemGestures];
}

// The stack gives the status bar's questions to the screen on top, not to its bar's style (`statusBar.style`): UIKit's
// top when it is the stack's own, so that the status bar follows the screen a swipe back reveals along the swipe; else
// the stack's current child. The home indicator and the vertical bars UIKit passes itself (F13).
- (nullable UIViewController *)statusBarChild {
    UIViewController *top = self.topViewController;
    if (top && [self isOwnViewController:top]) {
        return top;
    }
    return [self systemUIChild];
}

- (UIViewController *)childViewControllerForStatusBarStyle {
    return [self statusBarChild];
}

- (UIViewController *)childViewControllerForStatusBarHidden {
    return [self statusBarChild];
}

// UIKit's navigation controller doesn't ask its top for the orientations (F3): the stack answers what its top
// answers, and its own resolved options while it has none.
- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    UIViewController *child = [self statusBarChild];
    if (child) {
        return child.supportedInterfaceOrientations;
    }
    ANNNodePresenter *presenter = self.presenter;
    if (presenter && _madeUp) {
        return [presenter supportedOrientationsForOptions:[self resolveOptionsWithDefault]];
    }
    return [super supportedInterfaceOrientations];
}

// The stack's own merge: its options, what it shows (the swipes, the screens' frames when a bar rule changed), then its
// parent without the groups the stack keeps.
- (void)mergeOptions:(ANNNavigationOptions *)options {
    [self.nodeHelper mergeOwnOptions:options];
    [self.presenter mergeOptions:options resolvedOptions:[self resolveOptionsWithDefault]];
    if (ANNOptionsChangeScreenBars(options)) {
        for (UIViewController *entry in _queue.shown) {
            [self updateRootLayoutOf:entry];
        }
    }
    // The top's `animations.pop` may change with the stack's options.
    if (options.animations.hasValue) {
        [self updateAnimationGateAfterMerge];
    }
    [self.layoutParent mergeChildOptions:[self optionsForParent:options] child:self];
}

// Only the top's merge reaches what the stack shows; every merge goes on up without the bar, the stack's animations and
// the FAB, which the stack takes from its top and doesn't pass on.
- (void)mergeChildOptions:(ANNNavigationOptions *)options child:(UIViewController *)child {
    UIViewController *top = [self getCurrentChild];
    if (top && (child == top || child == ANNStackOptionsScreen(top))) {
        if (options.popGesture.hasValue) {
            [self updateSwipeBack];
        }
        // The top's own item took its part of the merge; the parts the stack shares follow the top's resolved options
        // now, the visibilities only when the merge names them, and the screens lay out again when a bar showed or
        // hid (correction 8).
        [self applySharedBarAnimated:YES visibility:ANNSharedBarVisibilityOfMerge(options)];
        if (options.animations.hasValue) {
            [self updateAnimationGateAfterMerge];
        }
        // The stack's view takes the top's `layout.backgroundColor` over its own.
        if (options.layout.backgroundColor.hasValue) {
            [self applyBackground];
        }
    }
    [self.layoutParent mergeChildOptions:[self optionsForParent:options] child:self];
}

- (void)applyBackground {
    ANNNodePresenter *presenter = self.presenter;
    if ([presenter isKindOfClass:ANNStackPresenter.class]) {
        [(ANNStackPresenter *)presenter applyBackground:[self resolveOptionsWithDefault]];
    }
}

- (ANNNavigationOptions *)optionsForParent:(ANNNavigationOptions *)options {
    ANNNavigationOptions *up = [options copy];
    up.topBar = [ANNTopBarOptions new];
    up.animations = [ANNAnimationOptions new];
    up.fab = [ANNFabOptions new];
    return up;
}

// Once: the queue's waiting, running and queued commands reject, nothing waits any more, every node the stack holds is
// destroyed. A coordinator's completion that comes later finds its operation ended.
- (void)destroy {
    if (![self.nodeHelper markDestroyed]) {
        return;
    }
    _pendingPlan = nil;
    [self stopLowerRendering];
    NSArray<UIViewController *> *nodes = [_queue destroyQueue];
    // A custom transition running now ends at its end state and completes, so that UIKit isn't left half way (8); the
    // queue is gone first, so its end steps don't run.
    [_runningAnimator cancel];
    for (UIViewController *entry in [self parkedEntries]) {
        [self stackUnpark:entry];
    }
    [self.nodeHelper unregisterNode];
    for (UIViewController *node in nodes) {
        @try {
            [node destroy];
        } @catch (NSException *exception) {
            RCTLogWarn(@"[ApexNavigation] could not destroy the screen '%@': %@",
                       node.layoutInfo.componentId ?: @"", exception.reason ?: exception.name);
        }
    }
}

#pragma mark - The user's back

- (void)userGoBackTo:(UIViewController *)target {
    ANNAssertMainQueue();
    if (self.nodeHelper.destroyed || !self.eventEmitter.isCurrent) {
        return;
    }
    NSArray<UIViewController *> *entries = _queue.entries;
    // One entry (a waiting `setStackRoot` put one screen): the user's back does nothing here (C2).
    if (entries.count < 2) {
        return;
    }
    UIViewController *to = target ?: entries[entries.count - 2];
    if ([entries indexOfObjectIdenticalTo:to] == NSNotFound || to == entries.lastObject) {
        return;
    }
    if ([self sendHoldEvent]) {
        return;
    }
    [_queue cutTo:to answer:nil value:nil what:@"go back" componentId:nil source:nil silent:NO];
}

- (void)userPopToRoot {
    [self userGoBackTo:_queue.entries.firstObject];
}

- (void)popToRootSilently {
    ANNAssertMainQueue();
    NSArray<UIViewController *> *entries = _queue.entries;
    if (self.nodeHelper.destroyed || entries.count < 2) {
        return;
    }
    [_queue cutTo:entries.firstObject
           answer:nil
            value:nil
             what:@"go back"
      componentId:nil
           source:nil
           silent:YES];
}

// The hold: read at the attempt from the screen the user sees, with the options of the stack and the defaults; its
// event has the back button's own id or `ApexNavigation.back`.
- (BOOL)sendHoldEvent {
    UIViewController *top = [self getCurrentChild];
    if (!top) {
        return NO;
    }
    UIViewController *screen = ANNStackOptionsScreen(top);
    ANNTopBarBackButtonOptions *backButton = [screen resolveOptionsWithDefault].topBar.backButton;
    if ([backButton.popStackOnPress withDefault:YES]) {
        return NO;
    }
    NSString *buttonId = [backButton.identifier withDefault:nil];
    if (buttonId.length == 0) {
        buttonId = ANNBackButtonDefaultId;
    }
    NSString *componentId = screen.layoutInfo.componentId;
    if (componentId) {
        [screen.eventEmitter sendOnNavigationButtonPressed:componentId buttonId:buttonId];
    }
    return YES;
}

#pragma mark - UIKit's pops into the stack

- (BOOL)isSwipingBack {
    UINavigationController *navigation = self.shownNavigationController;
    if (navigation == self && ANNIsActive(_ownSwipe)) {
        return YES;
    }
    if (ANNIsActive(navigation.interactivePopGestureRecognizer)) {
        return YES;
    }
    if (@available(iOS 26.0, *)) {
        if (ANNIsActive(navigation.interactiveContentPopGestureRecognizer)) {
            return YES;
        }
    }
    return NO;
}

// A pop UIKit asks for itself (the history menu, the tab bar's second tap) whose removed view controllers are all
// entries of this stack is the user's back; the swipe UIKit runs and anything else (UIKit's split view moving its
// navigation controllers) go to UIKit unchanged.
- (BOOL)takesUIKitPopTo:(UIViewController *)target {
    if (_coreCalls > 0 || [self isSwipingBack] || [self stackRunningTransition]) {
        return NO;
    }
    NSArray<UIViewController *> *viewControllers = self.viewControllers;
    NSArray<UIViewController *> *shown = _queue.shown;
    NSUInteger index = [viewControllers indexOfObjectIdenticalTo:target];
    if (index == NSNotFound || index + 1 >= viewControllers.count) {
        return NO;
    }
    for (NSUInteger at = index; at < viewControllers.count; at++) {
        if ([shown indexOfObjectIdenticalTo:viewControllers[at]] == NSNotFound) {
            return NO;
        }
    }
    [self userGoBackTo:target];
    return YES;
}

// UIKit's interactive pop of the swipe comes here, at moments the gesture's state doesn't tell (the simulator showed a
// cancelled swipe taken as a back): `popViewControllerAnimated:` always goes to UIKit. An interactive transition it
// starts is the swipe; anything else is a pop of UIKit's own the stack follows (`followUIKitTransition`). The back
// button reaches the stack through `backAction` (iOS 16+) and `navigationBar:shouldPopItem:` (iOS 15), the history
// menu through `popToViewController:animated:`.
- (UIViewController *)popViewControllerAnimated:(BOOL)animated {
    UIViewController *popped = [super popViewControllerAnimated:animated];
    if (_coreCalls == 0) {
        [self followUIKitTransition];
    }
    return popped;
}

- (NSArray<UIViewController *> *)popToViewController:(UIViewController *)viewController
                                            animated:(BOOL)animated {
    if ([self takesUIKitPopTo:viewController]) {
        return nil;
    }
    NSArray<UIViewController *> *popped = [super popToViewController:viewController
                                                             animated:animated];
    if (_coreCalls == 0) {
        [self followUIKitTransition];
    }
    return popped;
}

- (NSArray<UIViewController *> *)popToRootViewControllerAnimated:(BOOL)animated {
    NSArray<UIViewController *> *viewControllers = self.viewControllers;
    if (viewControllers.count >= 2 && [self takesUIKitPopTo:viewControllers.firstObject]) {
        return nil;
    }
    NSArray<UIViewController *> *popped = [super popToRootViewControllerAnimated:animated];
    if (_coreCalls == 0) {
        [self followUIKitTransition];
    }
    return popped;
}

// A change of UIKit's list the core didn't ask for: an interactive one is the swipe, which the queue takes when at
// rest; any other (a swipe UIKit runs while the queue is busy, a pop of UIKit's own) is followed at its end, so the
// stack's lists lose what UIKit's list lost (`ScreenPopped`, destroy). Once per transition; without a transition at
// once.
- (void)followUIKitTransition {
    id<UIViewControllerTransitionCoordinator> coordinator = [self stackRunningTransition];
    if (!coordinator) {
        [self followUIKitList];
        return;
    }
    if (coordinator == _seenTransition) {
        return;
    }
    _seenTransition = coordinator;
    if (coordinator.isInteractive || [self isSwipingBack]) {
        UIViewController *from =
            [coordinator viewControllerForKey:UITransitionContextFromViewControllerKey];
        UIViewController *to =
            [coordinator viewControllerForKey:UITransitionContextToViewControllerKey];
        if (from && to && [_queue swipeBeganFrom:from revealing:to]) {
            // The revealed screen's will-appear ran; UIKit hasn't laid it out for the transition yet.
            [self swipeBackStarts:to leaving:from on:self];
            return;
        }
    }
    __weak ANNStackController *weakSelf = self;
    [self stackWhenTransitionEnds:^(BOOL cancelled) {
      ANNStackController *stack = weakSelf;
      if (stack && !stack.nodeHelper.destroyed) {
          [stack followUIKitList];
      }
    }];
}

// UIKit's list of the stack is what the stack shows; an empty list of a stack that has entries tells nothing (its
// screens are in UIKit's More navigation).
- (void)followUIKitList {
    NSArray<UIViewController *> *viewControllers = self.viewControllers;
    if (viewControllers.count == 0 && _queue.shown.count > 0) {
        return;
    }
    [_queue uikitShows:viewControllers];
}

#pragma mark - More

- (UINavigationController *)shownNavigationController {
    UIViewController *parent = _queue.shown.firstObject.parentViewController;
    if (parent && parent != self && !parent.isLayoutNode &&
        [parent isKindOfClass:UINavigationController.class]) {
        return (UINavigationController *)parent;
    }
    return self;
}

- (void)moreNavigationWillShow:(UINavigationController *)moreNavigation {
    _moreNavigation = moreNavigation;
    if (!self.nodeHelper.destroyed) {
        [self installSwipeBackDelegatesOn:moreNavigation];
        if ([ANNAppDirection navigationMissedSceneDirection:moreNavigation]) {
            [ANNAppDirection followSceneDirectionIn:moreNavigation];
        }
    }
    if (_coreCalls > 0 || self.nodeHelper.destroyed) {
        return;
    }
    // The stack's shared parts of the bar go onto the More navigation, which shows its screens now.
    [self applySharedBarIfTopChangedAnimated:YES];
    __weak ANNStackController *weakSelf = self;
    __weak UINavigationController *weakMore = moreNavigation;
    id<UIViewControllerTransitionCoordinator> coordinator = moreNavigation.transitionCoordinator;
    // From one of the stack's own screens it is the user's back or swipe inside the tab; from the More list it is UIKit
    // opening the tab, which with the tab API brings only the stack's first screen.
    UIViewController *from =
        [coordinator viewControllerForKey:UITransitionContextFromViewControllerKey];
    BOOL opens = ![self isOwnViewController:from];
    // The user's swipe back inside the tab: UIKit's More navigation runs it, with the tabs as its delegate.
    UIViewController *to = [coordinator viewControllerForKey:UITransitionContextToViewControllerKey];
    NSArray<UIViewController *> *shown = _queue.shown;
    if (coordinator.isInteractive && from && to &&
        [shown indexOfObjectIdenticalTo:from] != NSNotFound &&
        [shown indexOfObjectIdenticalTo:to] != NSNotFound) {
        [self swipeBackStarts:to leaving:from on:moreNavigation];
    }
    void (^follow)(BOOL) = ^(BOOL cancelled) {
      ANNStackController *stack = weakSelf;
      UINavigationController *more = weakMore;
      if (cancelled || !stack || !more) {
          return;
      }
      if (opens) {
          [stack showScreensOnMoreNavigation:more];
      } else {
          [stack followMoreNavigation:more];
      }
    };
    void (^completion)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          follow(context.isCancelled);
        };
    if (coordinator && [coordinator animateAlongsideTransition:nil completion:completion]) {
        return;
    }
    follow(NO);
}

- (void)forgetSharedBar {
    _sharedBarScreen = nil;
}

- (void)stackDidLeaveScreen {
    if (!self.nodeHelper.destroyed && !self.viewIfLoaded.window) {
        [_queue stackWentOffScreen];
    }
}

// UIKit opened the tab from the More list with the stack's first screen only: the screens the stack keeps above it go
// back onto the More navigation at once, so that the tab shows where the user left it.
- (void)showScreensOnMoreNavigation:(UINavigationController *)moreNavigation {
    NSArray<UIViewController *> *shown = _queue.shown;
    NSArray<UIViewController *> *viewControllers = moreNavigation.viewControllers;
    NSUInteger index = shown.firstObject
                           ? [viewControllers indexOfObjectIdenticalTo:shown.firstObject]
                           : NSNotFound;
    if (self.nodeHelper.destroyed || index == NSNotFound) {
        return;
    }
    NSUInteger onMore = viewControllers.count - index;
    if (onMore >= shown.count) {
        [self followMoreNavigation:moreNavigation];
        return;
    }
    for (NSUInteger offset = 0; offset < onMore; offset++) {
        if (viewControllers[index + offset] != shown[offset]) {
            [self followMoreNavigation:moreNavigation];
            return;
        }
    }
    NSArray<UIViewController *> *above =
        [shown subarrayWithRange:NSMakeRange(onMore, shown.count - onMore)];
    for (UIViewController *entry in above) {
        if (entry.parentViewController) {
            return;
        }
    }
    _coreCalls++;
    @try {
        [moreNavigation setViewControllers:[viewControllers arrayByAddingObjectsFromArray:above]
                                  animated:NO];
    } @finally {
        _coreCalls--;
    }
}

// A back or a swipe the user made inside the tab: the stack's first screen is still on the More navigation, and what
// left it leaves the stack (`ScreenPopped`, destroy). Anything else is UIKit taking the screens back: nothing changes.
- (void)followMoreNavigation:(UINavigationController *)moreNavigation {
    if (self.nodeHelper.destroyed) {
        return;
    }
    UIViewController *first = _queue.shown.firstObject;
    NSArray<UIViewController *> *viewControllers = moreNavigation.viewControllers;
    NSUInteger index = first ? [viewControllers indexOfObjectIdenticalTo:first] : NSNotFound;
    if (index == NSNotFound) {
        return;
    }
    NSRange range = NSMakeRange(index, viewControllers.count - index);
    [_queue uikitShows:[viewControllers subarrayWithRange:range]];
}

#pragma mark - UINavigationBarDelegate

// iOS 15 has no `backAction`: the back button's tap reaches the stack here. A pop of UIKit's own (the core's, the
// swipe, a history menu pop that went on) has taken its view controller off already, so the bar has more items than
// the stack has view controllers then; the tap is the case where it doesn't.
- (BOOL)navigationBar:(UINavigationBar *)navigationBar shouldPopItem:(UINavigationItem *)item {
    if (@available(iOS 16.0, *)) {
    } else {
        BOOL tapped = _coreCalls == 0 && ![self isSwipingBack] && ![self stackRunningTransition] &&
                      self.viewControllers.count >= navigationBar.items.count;
        if (tapped) {
            // The pop goes on the next turn, once the bar is done asking: one turn, not a wait.
            __weak ANNStackController *weakSelf = self;
            dispatch_async(dispatch_get_main_queue(), ^{
              [weakSelf userGoBackTo:nil];
            });
            return NO;
        }
    }
    SEL shouldPopItem = @selector(navigationBar:shouldPopItem:);
    if ([UINavigationController instancesRespondToSelector:shouldPopItem]) {
        return [super navigationBar:navigationBar shouldPopItem:item];
    }
    return YES;
}

#pragma mark - UINavigationControllerDelegate

// UIKit starts the interactive pop of the swipe here: from now to the coordinator's completion the swipe is the
// queue's operation. A pop the core itself asked for comes here too, inside its own call.
- (void)navigationController:(UINavigationController *)navigationController
      willShowViewController:(UIViewController *)viewController
                    animated:(BOOL)animated {
    if (_coreCalls > 0) {
        return;
    }
    [self followUIKitTransition];
}

// Each appearance of a top screen: what the stack shows follows its options (the swipes by its `popGesture`,
// `popGesture`; the bar's shared parts, at rest now).
- (void)navigationController:(UINavigationController *)navigationController
       didShowViewController:(UIViewController *)viewController
                    animated:(BOOL)animated {
    // UIKit may make the content swipe after the stack appeared.
    [self installSwipeBackDelegates];
    [self.presenter applyOptions:[self resolveOptionsWithDefault]];
}

#pragma mark - The custom transitions

// UIKit asks while it starts the change the queue asked for: the queue's plan when the operation and the arriving
// entry are the plan's (the back button and the commands go through the queue, so its plan covers them); the swipe
// back gets the top's own pop only with `animations.pop.interactive` (`swipeAnimatorFrom:to:`); nil for anything
// else — UIKit's own pops, a zoom alone.
- (id<UIViewControllerAnimatedTransitioning>)
    animatorForOperation:(UINavigationControllerOperation)operation
                    from:(UIViewController *)from
                      to:(UIViewController *)to {
    if ([self isSwipingBack]) {
        return operation == UINavigationControllerOperationPop
                   ? [self swipeAnimatorFrom:from to:to]
                   : nil;
    }
    ANNTransitionPlan *plan = _pendingPlan;
    if (!plan || operation == UINavigationControllerOperationNone || to != plan.toNode) {
        return nil;
    }
    BOOL matches = _pendingChange == ANNStackChangeReplace ||
                   (_pendingChange == ANNStackChangePush &&
                    operation == UINavigationControllerOperationPush) ||
                   (_pendingChange == ANNStackChangePop &&
                    operation == UINavigationControllerOperationPop);
    if (!matches) {
        return nil;
    }
    _pendingPlan = nil;
    ANNTransitionAnimator *animator = [[ANNTransitionAnimator alloc] initWithPlan:plan];
    _runningAnimator = animator;
    return animator;
}

// The stack's own swipe back of a top whose pop follows the finger (`animations.pop.interactive`, not
// `enabled: false`): the pop plays under it. Nil otherwise, also when UIKit won't ask for the interaction (the gate
// hasn't seen the option yet: a change of the default options) or the screen under hasn't settled
// (`revealedEntrySettled:`): UIKit asks for the transition before the queue takes the swipe and moves those, and the
// frames take the views as they are now.
- (nullable ANNTransitionAnimator *)swipeAnimatorFrom:(UIViewController *)from
                                                   to:(UIViewController *)to {
    if (!ANNIsActive(_ownSwipe) || !_animatingDelegate.answersInteraction ||
        ![self isOwnViewController:from] || ![self revealedEntrySettled:to]) {
        return nil;
    }
    ANNStackPopAnimationOptions *pop = [from resolveOptionsWithDefault].animations.pop;
    if (![ANNStackController popFollowsSwipe:pop]) {
        return nil;
    }
    ANNTransitionPlan *plan = [ANNTransitionPlan stackPlanOfKind:ANNTransitionKindPop
                                                       animation:pop
                                                   waitForRender:nil
                                                       waitLimit:0
                                                            from:from
                                                              to:to];
    if (!plan) {
        return nil;
    }
    ANNTransitionAnimator *animator = [[ANNTransitionAnimator alloc] initWithPlan:plan];
    // The interaction keeps itself while it follows the gesture; UIKit keeps it once it starts it.
    ANNSwipePopInteraction *interaction =
        [[ANNSwipePopInteraction alloc] initWithAnimator:animator recognizer:_ownSwipe view:self.view];
    _swipeInteraction = interaction;
    _runningAnimator = animator;
    return animator;
}

// The screen under is in place for the frames: its view made, not parked, not rendering as a lower screen.
- (BOOL)revealedEntrySettled:(nullable UIViewController *)revealed {
    return revealed.isViewLoaded && ![_lowerRendering objectForKey:revealed] &&
           ![_lowerToRender containsObject:revealed] &&
           ![[self parkedEntries] containsObject:revealed];
}

+ (BOOL)popFollowsSwipe:(nullable ANNStackPopAnimationOptions *)pop {
    return [ANNAnimationRules stackIsOwn:pop] && [pop.interactive withDefault:NO] &&
           [pop.enabled withDefault:YES];
}

- (nullable id<UIViewControllerInteractiveTransitioning>)interactionForAnimator:
    (id<UIViewControllerAnimatedTransitioning>)animator {
    ANNSwipePopInteraction *interaction = _swipeInteraction;
    return interaction && interaction.animator == animator ? interaction : nil;
}

// The animating delegate while a custom animation may be needed: a change with its own plan now, or a top whose
// `animations.pop` is its own; the stack itself otherwise. UIKit reads the delegate's methods when it is set, so it is
// set again at each change, and again when the interaction of the swipe comes or goes with the top's
// `animations.pop.interactive`.
- (void)updateAnimationGateWith:(ANNTransitionPlan *)plan {
    UIViewController *top = [self getCurrentChild];
    ANNStackPopAnimationOptions *pop = top ? [top resolveOptionsWithDefault].animations.pop : nil;
    BOOL needed = plan != nil || [ANNAnimationRules stackIsOwn:pop];
    BOOL interactive = [ANNStackController popFollowsSwipe:pop];
    if (_animatingDelegate.answersInteraction != interactive) {
        _animatingDelegate.answersInteraction = interactive;
        if (self.delegate == _animatingDelegate) {
            self.delegate = self;
        }
    }
    _ownSwipe.enabled = interactive;
    id<UINavigationControllerDelegate> delegate =
        needed ? (id<UINavigationControllerDelegate>)_animatingDelegate : self;
    if (self.delegate != delegate) {
        self.delegate = delegate;
    }
}

// After an operation or a merge: never while a transition of the stack runs (its end does it); a turn later when one
// still runs as the operation ends.
- (void)updateAnimationGateAfterMerge {
    if ([self stackRunningTransition]) {
        return;
    }
    [self updateAnimationGateWith:nil];
}

- (void)updateAnimationGateAtEnd {
    [self updateAnimationGateAtEndTriesLeft:ANNGateRecomputeTries];
}

// While a transition of the stack still runs, the recompute waits for its coordinator's completion and runs a turn
// after it; a coordinator that keeps answering is asked again, a bounded number of turns.
- (void)updateAnimationGateAtEndTriesLeft:(NSUInteger)triesLeft {
    if (self.nodeHelper.destroyed) {
        return;
    }
    id<UIViewControllerTransitionCoordinator> coordinator = [self stackRunningTransition];
    if (!coordinator || triesLeft == 0) {
        [self updateAnimationGateWith:nil];
        return;
    }
    if (_gateUpdateScheduled) {
        return;
    }
    _gateUpdateScheduled = YES;
    __weak ANNStackController *weakSelf = self;
    dispatch_block_t later = ^{
      dispatch_async(dispatch_get_main_queue(), ^{
        ANNStackController *stack = weakSelf;
        if (!stack) {
            return;
        }
        stack->_gateUpdateScheduled = NO;
        [stack updateAnimationGateAtEndTriesLeft:triesLeft - 1];
      });
    };
    void (^completion)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          later();
        };
    if (![coordinator animateAlongsideTransition:nil completion:completion]) {
        later();
    }
}

// The iOS 18 zoom of a push: only for an animated push without an own animation of the stack (own animations win), with
// `zoom.enabled` not false and a non-empty `fromId` whose view is on the screen the push covers and in a window. The
// pushed screen keeps it, so UIKit zooms back at its pop.
- (void)zoomPush:(UIViewController *)pushed covered:(UIViewController *)covered {
    if (@available(iOS 18.0, *)) {
        if (!pushed || !covered || ![self isOwnViewController:covered]) {
            return;
        }
        ANNZoomTransitionOptions *zoom = [pushed resolveOptionsWithDefault].animations.push.zoom;
        NSString *fromId = [zoom.fromId withDefault:nil];
        if (![zoom.enabled withDefault:YES] || ![fromId isKindOfClass:NSString.class] ||
            fromId.length == 0) {
            return;
        }
        __weak UIViewController *weakCovered = covered;
        [ANNZoomAnchor zoom:pushed
                   fromId:fromId
            sourceScreens:^NSArray<UIViewController *> * {
              UIViewController *source = weakCovered;
              return source ? source.shownComponentViewControllers : @[];
            }];
    }
}

#pragma mark - The swipes back

- (BOOL)swipeBackIsUIKits:(UIGestureRecognizer *)recognizer {
    UIViewController *top = self.topViewController;
    if (top != nil && top != [self getCurrentChild]) {
        return YES;
    }
    BOOL own = recognizer == self.interactivePopGestureRecognizer || recognizer == _ownSwipe;
    if (@available(iOS 26.0, *)) {
        own = own || recognizer == self.interactiveContentPopGestureRecognizer;
    }
    return !own && _queue.shown.count < 2;
}

- (BOOL)swipeBackIsStacks:(UIGestureRecognizer *)recognizer {
    return recognizer != nil && recognizer == _ownSwipe;
}

- (BOOL)swipeBackTakes:(UIGestureRecognizer *)recognizer {
    BOOL uikits = recognizer == self.interactivePopGestureRecognizer;
    if (@available(iOS 26.0, *)) {
        uikits = uikits || recognizer == self.interactiveContentPopGestureRecognizer;
    }
    if (recognizer != _ownSwipe && !uikits) {
        return YES;
    }
    NSArray<UIViewController *> *shown = _queue.shown;
    UIViewController *revealed = shown.count >= 2 ? shown[shown.count - 2] : nil;
    // The options as they are now, as `swipeAnimatorFrom:to:` reads them: the gate may lag a change of the defaults,
    // and a swipe of ours without the pop would be a pop the finger can't hold.
    UIViewController *top = self.topViewController;
    BOOL ownPlays = _ownSwipe.enabled && _animatingDelegate.answersInteraction && top &&
                    top == [self getCurrentChild] && [self isOwnViewController:top] &&
                    [ANNStackController
                        popFollowsSwipe:[top resolveOptionsWithDefault].animations.pop] &&
                    [self revealedEntrySettled:revealed];
    if (uikits) {
        return !ownPlays;
    }
    return ownPlays && [self panStartsAtLeadingEdge:_ownSwipe];
}

// A pan that starts near the leading edge of the stack's view and moves more across than along, toward the trailing
// side: where UIKit's edge swipe starts.
- (BOOL)panStartsAtLeadingEdge:(UIPanGestureRecognizer *)pan {
    UIView *view = self.view;
    CGPoint at = [pan locationInView:view];
    CGPoint move = [pan translationInView:view];
    BOOL rightToLeft =
        view.effectiveUserInterfaceLayoutDirection == UIUserInterfaceLayoutDirectionRightToLeft;
    CGFloat start = at.x - move.x;
    CGFloat fromEdge = rightToLeft ? view.bounds.size.width - start : start;
    if (move.x == 0 && move.y == 0) {
        move = [pan velocityInView:view];
    }
    CGFloat along = rightToLeft ? -move.x : move.x;
    return fromEdge <= ANNOwnSwipeEdgeWidth && along > fabs(move.y);
}

// The stack's own swipe began: a pop as UIKit's swipe makes it, which the stack follows as the swipe back
// (`followUIKitTransition`); the transition asks for the top's pop and its interaction (`swipeAnimatorFrom:to:`).
- (void)ownSwipeBack:(UIPanGestureRecognizer *)recognizer {
    if (recognizer.state == UIGestureRecognizerStateBegan) {
        [self popViewControllerAnimated:YES];
    }
}

- (BOOL)hasOneEntry {
    return _queue.entries.count < 2;
}

- (BOOL)swipeBackMayBegin {
    return !self.nodeHelper.destroyed && self.eventEmitter.isCurrent && _queue.entries.count >= 2 &&
           _queue.shown.count >= 2 && _queue.isAtRest;
}

// Both swipes may ask for one touch: the hold's event goes once per attempt.
- (BOOL)holdsSwipeBack {
    UITouch *touch = _swipeTouch;
    if (touch && touch == _heldTouch) {
        UIViewController *top = [self getCurrentChild];
        ANNNavigationOptions *options = [ANNStackOptionsScreen(top) resolveOptionsWithDefault];
        return ![options.topBar.backButton.popStackOnPress withDefault:YES];
    }
    BOOL held = [self sendHoldEvent];
    if (held) {
        _heldTouch = touch;
    }
    return held;
}

- (BOOL)backIsHeld {
    UIViewController *top = [self getCurrentChild];
    ANNNavigationOptions *options = [ANNStackOptionsScreen(top) resolveOptionsWithDefault];
    return top && ![options.topBar.backButton.popStackOnPress withDefault:YES];
}

- (void)swipeBackReceivesTouch:(UITouch *)touch {
    _swipeTouch = touch;
}

- (void)updateSwipeBack {
    UIViewController *top = [self getCurrentChild];
    id value = top ? [[ANNStackOptionsScreen(top) resolveOptionsWithDefault].popGesture get] : nil;
    BOOL isBool = [value isKindOfClass:NSNumber.class];
    // `false` turns both off, `'edge'` keeps only the edge swipe; before iOS 26 there is no content swipe.
    BOOL edge = !(isBool && ![value boolValue]);
    BOOL content = !value || (isBool && [value boolValue]);
    _edgeSwipeDelegate.enabled = edge;
    _ownSwipeDelegate.enabled = edge;
    _moreEdgeSwipeDelegate.enabled = edge;
    _contentSwipeDelegate.enabled = content;
    _moreContentSwipeDelegate.enabled = content;
    [self swipeBackFollowsBar];
}

- (void)swipeBackFollowsBar {
    BOOL hidden = [ANNSystemUI barHiddenOf:self.shownNavigationController];
    _edgeSwipeDelegate.barHidden = hidden;
    _ownSwipeDelegate.barHidden = hidden;
    _contentSwipeDelegate.barHidden = hidden;
    _moreEdgeSwipeDelegate.barHidden = hidden;
    _moreContentSwipeDelegate.barHidden = hidden;
}

#pragma mark - The split view

- (void)setChildren:(NSArray<UIViewController *> *)children
    fromViewController:(UIViewController *)fromViewController
              animated:(BOOL)animated
            completion:(ANNTransitionCompletionBlock)completion
             rejection:(ANNTransitionRejectionBlock)rejection {
    ANNAssertMainQueue();
    NSString *fromId =
        fromViewController.layoutInfo.componentId ?: self.layoutInfo.componentId ?: @"";
    if (self.nodeHelper.destroyed) {
        rejection([ANNErrorTexts screenNotShown:fromId], nil);
        return;
    }
    if (children.count == 0) {
        rejection([ANNErrorTexts noScreensForStack:fromId], nil);
        return;
    }
    NSString *what = [NSString stringWithFormat:@"set the stack of the screen '%@'", fromId];
    [_queue replaceWith:children
               animated:animated
                  waits:NO
                 answer:nil
                   what:what
            componentId:fromId
                 source:nil
             completion:completion
              rejection:rejection];
}

#pragma mark - ANNStackDisplay

- (UIViewController *)stackViewController {
    return self;
}

// In More the More navigation is what shows the screens.
- (BOOL)stackIsOnScreen {
    return self.shownNavigationController.viewIfLoaded.window != nil &&
           ![ANNPendingLayers isWaitingToShow:self];
}

// Only the stack's own transitions (a push, a pop, a replace of its view controllers, the interactive pop): a
// presentation or a dismissal over it or of it never makes it wait (no new wait after a tap). In More, the More
// navigation's transition of the stack's screens (its `CATransaction` never completes there).
- (id<UIViewControllerTransitionCoordinator>)stackRunningTransition {
    UINavigationController *navigation = self.shownNavigationController;
    id<UIViewControllerTransitionCoordinator> coordinator =
        navigation.viewIfLoaded.window ? navigation.transitionCoordinator : nil;
    if (!coordinator) {
        return nil;
    }
    UIViewController *from =
        [coordinator viewControllerForKey:UITransitionContextFromViewControllerKey];
    UIViewController *to =
        [coordinator viewControllerForKey:UITransitionContextToViewControllerKey];
    return [self isOwnViewController:from] || [self isOwnViewController:to] ? coordinator : nil;
}

- (BOOL)isOwnViewController:(UIViewController *)viewController {
    if (!viewController || viewController == self) {
        return NO;
    }
    return viewController.parentViewController == self ||
           [self.viewControllers indexOfObjectIdenticalTo:viewController] != NSNotFound ||
           [_queue.heldNodes indexOfObjectIdenticalTo:viewController] != NSNotFound;
}

- (void)stackChange:(ANNStackChange)change
          toEntries:(NSArray<UIViewController *> *)entries
           animated:(BOOL)animated
               plan:(ANNTransitionPlan *)plan {
    // The bar a push starts from: its bottom, when it shows without a large title.
    CGFloat lowerBarBottom = -1;
    UINavigationController *navigation = [self navigationForChange:change];
    UIViewController *coveredTop = navigation.topViewController;
    // The More navigation is UIKit's, with the tabs' delegate: its transitions stay UIKit's.
    ANNTransitionPlan *ownPlan = animated && navigation == self ? plan : nil;
    // UIKit asks the delegate at the stack's next layout, after this call: the plan and the delegate are in place
    // before it.
    _pendingPlan = ownPlan;
    _pendingChange = change;
    if (_madeUp) {
        [self updateAnimationGateWith:ownPlan];
    }
    // Each push sets the direction of the navigation it runs on before UIKit's change, so that the transition and the
    // swipe back take its edge: React Native's, or the scene's with the app's direction on iOS 17+.
    if (change == ANNStackChangePush) {
        [self forceDirection:ANNAppDirection.pushDirection on:navigation];
    }
    // Own animations win over the zoom, also where UIKit plays them (More).
    if (change == ANNStackChangePush && animated && !plan) {
        [self zoomPush:entries.lastObject covered:coveredTop];
    }
    // UIKit asks for no transition object for the pop of a screen it zoomed: a command's own pop drops the zoom of the
    // screen that leaves right before the call (design 24).
    if (@available(iOS 18.0, *)) {
        if (change == ANNStackChangePop && ownPlan.overridesZoom &&
            coveredTop.preferredTransition && [self isOwnViewController:coveredTop]) {
            coveredTop.preferredTransition = nil;
        }
    }
    if (change == ANNStackChangePush && animated) {
        lowerBarBottom = [self lowerBarBottomOf:navigation leaving:coveredTop];
    }
    // The large title of a screen that comes with its content takes its place for the transition before UIKit's push.
    if (change == ANNStackChangePush && animated) {
        UIViewController *arriving = ANNStackOptionsScreen(entries.lastObject);
        [[ANNTopBarPresenter presenterOfScreen:arriving] arrivesByPushOn:navigation];
    }
    // The revealed screen's large title takes its place for the transition before UIKit lays the screen out for it.
    if (change == ANNStackChangePop && animated) {
        [self popReveals:entries.lastObject leaving:coveredTop on:navigation];
    }
    _coreCalls++;
    @try {
        if (navigation != self) {
            // The tab is open from More: its screens are on the More navigation, over the More list.
            [self moreNavigation:navigation change:change toEntries:entries animated:animated];
        } else {
            switch (change) {
                case ANNStackChangePush:
                    [super pushViewController:entries.lastObject animated:animated];
                    break;
                case ANNStackChangePop:
                    [self popTo:entries.lastObject animated:animated];
                    break;
                case ANNStackChangeReplace:
                    [super setViewControllers:entries animated:animated];
                    break;
            }
        }
    } @finally {
        _coreCalls--;
    }
    // The bar's shared parts first, before UIKit starts the transition: UIKit starts it at the next layout of the
    // stack, and a bar shown or hidden after that start doesn't go along with it (`topBar.visible`).
    [self barTransitionStartedBy:change];
    // UIKit gives the new top its frame and safe area only inside the transition, a few milliseconds later; its content
    // would render and lay out without the bar's room and keep it (the rows under the bar). A layout of the stack right
    // after the call gives the final frame, safe area and bar at once; the screen takes them into its root frame and
    // padding now.
    UINavigationController *shown = self.shownNavigationController;
    if (shown.viewIfLoaded.window) {
        [shown.view layoutIfNeeded];
        UIViewController *top = entries.lastObject;
        if (top) {
            [self updateRootLayoutOf:top];
        }
    }
    if (lowerBarBottom >= 0 && shown.viewIfLoaded.window) {
        UIViewController *topScreen = ANNStackOptionsScreen(entries.lastObject);
        ANNTopBarPresenter *topBar = [ANNTopBarPresenter presenterOfScreen:topScreen];
        [topBar shownFromLowerBarWithBottom:lowerBarBottom];
    }
}

// The bottom of the bar a transition leaves, in the navigation's coordinates, when that bar shows and has no large
// title; -1 otherwise.
- (CGFloat)lowerBarBottomOf:(UINavigationController *)navigation
                    leaving:(nullable UIViewController *)leaving {
    if (!leaving || navigation.navigationBarHidden || ![self isOwnViewController:leaving] ||
        ANNLargeTitleCanShow([ANNStackOptionsScreen(leaving) resolveOptionsWithDefault])) {
        return -1;
    }
    return CGRectGetMaxY(navigation.navigationBar.frame);
}

// A pop or a swipe back starts, before UIKit lays the revealed screen out: the leaving screen first (its title and
// band in its own place), then the revealed screen hears of the bar it starts from, as it is now (its bottom, whether
// its screen has a large title, whether it is clear at its edge).
- (void)popReveals:(UIViewController *)revealed
           leaving:(nullable UIViewController *)leaving
                on:(UINavigationController *)navigation {
    UIViewController *revealedScreen = ANNStackOptionsScreen(revealed);
    ANNTopBarPresenter *topBar = [ANNTopBarPresenter presenterOfScreen:revealedScreen];
    // The revealed title's place first: the leaving screen clears its edge look only when the revealed one draws its
    // band too, or the bar row over the revealed screen would be clear.
    BOOL revealedHosts = [topBar revealedByPop];
    ANNPopBar bar = {.bottom = -1, .largeTitle = NO, .clearAtEdge = NO};
    BOOL ownLeaving = [self isOwnViewController:leaving] ||
                      (leaving && [_queue.shown indexOfObjectIdenticalTo:leaving] != NSNotFound);
    if (leaving && !navigation.navigationBarHidden && ownLeaving) {
        UIViewController *leavingScreen = ANNStackOptionsScreen(leaving);
        ANNTopBarPresenter *leavingBar = [ANNTopBarPresenter presenterOfScreen:leavingScreen];
        [leavingBar leavesByPopClearingEdge:revealedHosts];
        bar.bottom = CGRectGetMaxY(navigation.navigationBar.frame);
        bar.largeTitle = ANNLargeTitleCanShow([leavingScreen resolveOptionsWithDefault]);
        bar.clearAtEdge = [leavingBar clearsBarAtEdge];
    }
    [topBar revealedByPopFrom:bar];
}

// UIKit's swipe back started (on the stack's navigation or on UIKit's More navigation): as a pop, and a swipe given up
// gives the screen that stays what it had.
- (void)swipeBackStarts:(UIViewController *)revealed
                leaving:(UIViewController *)leaving
                     on:(UINavigationController *)navigation {
    [self popReveals:revealed leaving:leaving on:navigation];
    __weak ANNTopBarPresenter *leavingBar =
        [ANNTopBarPresenter presenterOfScreen:ANNStackOptionsScreen(leaving)];
    [self stackWhenTransitionEnds:^(BOOL cancelled) {
      if (cancelled) {
          [leavingBar popGivenUp];
      }
    }];
}

- (void)forceDirection:(UISemanticContentAttribute)direction
                    on:(UINavigationController *)navigation {
    if (navigation == self) {
        _forcedDirection = direction;
    }
    UINavigationBar *bar = navigation.navigationBar;
    if (bar.semanticContentAttribute != direction) {
        bar.semanticContentAttribute = direction;
    }
    UIView *view = navigation.viewIfLoaded;
    if (view && view.semanticContentAttribute != direction) {
        view.semanticContentAttribute = direction;
    }
}

- (void)dropForcedDirection {
    _forcedDirection = UISemanticContentAttributeUnspecified;
    [ANNAppDirection followSceneDirectionIn:self];
}

// One screen back is UIKit's pop of its top, which plays the zoom back of a zoomed screen (also at a
// `pop()`); more screens back, the pop to the target. Inside the core's call: not taken as UIKit's own pop.
- (void)popTo:(UIViewController *)target animated:(BOOL)animated {
    NSArray<UIViewController *> *viewControllers = self.viewControllers;
    NSUInteger count = viewControllers.count;
    if (count >= 2 && viewControllers[count - 2] == target) {
        [super popViewControllerAnimated:animated];
        return;
    }
    [super popToViewController:target animated:animated];
}

// The navigation the change runs on: the More navigation while it shows the stack's screens (the stack's own list is
// empty then), else the stack. A replace's new screens are on no navigation yet: the More navigation that still holds
// the stack's first screen over its list is the one.
- (UINavigationController *)navigationForChange:(ANNStackChange)change {
    UINavigationController *navigation = self.shownNavigationController;
    if (navigation != self) {
        _moreNavigation = navigation;
        return navigation;
    }
    UINavigationController *more = _moreNavigation;
    NSArray<UIViewController *> *moreList = more.viewControllers;
    if (change == ANNStackChangeReplace && more && self.viewControllers.count == 0 &&
        moreList.count >= 2 && moreList[1].layoutParent == self) {
        return more;
    }
    return self;
}

- (void)moreNavigation:(UINavigationController *)more
                change:(ANNStackChange)change
             toEntries:(NSArray<UIViewController *> *)entries
              animated:(BOOL)animated {
    switch (change) {
        case ANNStackChangePush:
            [more pushViewController:entries.lastObject animated:animated];
            break;
        case ANNStackChangePop:
            [more popToViewController:entries.lastObject animated:animated];
            break;
        case ANNStackChangeReplace: {
            UIViewController *list = more.viewControllers.firstObject;
            NSArray<UIViewController *> *shown =
                list ? [@[ list ] arrayByAddingObjectsFromArray:entries] : entries;
            [more setViewControllers:shown animated:animated];
            break;
        }
    }
}

// Right after the UIKit call, while its coordinator exists: the bar's shared parts move with the transition (conflict
// 1), and on iOS 26 the title of a screen a push shows keeps the color of the window's style to its end (never on a pop
// or the swipe).
- (void)barTransitionStartedBy:(ANNStackChange)change {
    if (!self.presenter) {
        return;
    }
    [self applySharedBarIfTopChangedAnimated:YES];
    if (change != ANNStackChangePush) {
        return;
    }
    id<UIViewControllerTransitionCoordinator> coordinator = [self stackRunningTransition];
    // In a tab of More the More navigation shows the screens, not the stack's own view.
    UIWindow *window = self.shownNavigationController.viewIfLoaded.window;
    UIViewController *top = [self barTopEntry];
    if (coordinator && coordinator.isAnimated && !coordinator.isInteractive && window && top) {
        ANNTopBarPresenter *topBar =
            [ANNTopBarPresenter presenterOfScreen:ANNStackOptionsScreen(top)];
        [topBar pinTitleColorAlong:coordinator traits:window.traitCollection];
    }
}

#pragma mark - The bar

// The entry the bar shows: UIKit's top when it is one of the stack's (also the screen a swipe reveals; the More
// navigation's in More), else the stack's current child.
- (nullable UIViewController *)barTopEntry {
    UIViewController *top = self.shownNavigationController.topViewController;
    if (top && [self isOwnViewController:top]) {
        return top;
    }
    return [self getCurrentChild];
}

- (void)applySharedBarIfTopChangedAnimated:(BOOL)animated {
    UIViewController *top = [self barTopEntry];
    UIViewController *screen = top ? ANNStackOptionsScreen(top) : nil;
    BOOL changed = screen != _sharedBarScreen;
    [self applySharedBarAnimated:animated
                      visibility:changed ? ANNSharedBarVisibilityAll : ANNSharedBarVisibilityNone];
}

- (void)applySharedBarAnimated:(BOOL)animated visibility:(ANNSharedBarVisibility)visibility {
    ANNNodePresenter *nodePresenter = self.presenter;
    ANNStackPresenter *presenter = [nodePresenter isKindOfClass:ANNStackPresenter.class]
                                       ? (ANNStackPresenter *)nodePresenter
                                       : nil;
    UIViewController *top = [self barTopEntry];
    if (!presenter || !top || self.nodeHelper.destroyed) {
        return;
    }
    UIViewController *screen = ANNStackOptionsScreen(top);
    if (visibility == ANNSharedBarVisibilityAll) {
        _sharedBarScreen = screen;
    }
    [presenter applySharedBar:[screen resolveOptionsWithDefault]
                     ofScreen:screen
                     animated:animated
                  coordinator:animated ? [self stackRunningTransition] : nil
                   visibility:visibility];
}

- (void)updateRootLayoutOfShownEntries {
    for (UIViewController *entry in _queue.shown) {
        [self updateRootLayoutOf:entry];
    }
}

// The keyboard shows: the bar hides when the screen on top has `topBar.hideOnKeyboard` in its resolved options, the
// stack is in a window with nothing presented over it, and the bar shows (`topBar.hideOnKeyboard`). The value is read
// at each keyboard, so it never outlives the screen that set it. UIKit's `hidesBarsWhenKeyboardAppears` is not used:
// once on, it kept hiding the bar after it was set back to `NO`. The navigation that shows the screens has the bar: the
// stack's own, or UIKit's More navigation in a tab opened from More.
- (void)keyboardWillShow:(NSNotification *)notification {
    UINavigationController *navigation = self.shownNavigationController;
    if (!navigation.viewIfLoaded.window || navigation.presentedViewController ||
        navigation.navigationBarHidden || self.nodeHelper.destroyed) {
        return;
    }
    UIViewController *top = [self barTopEntry];
    ANNTopBarOptions *topBar = [ANNStackOptionsScreen(top) resolveOptionsWithDefault].topBar;
    if (!top || ![topBar.hideOnKeyboard withDefault:NO]) {
        return;
    }
    _keyboardHidBarOf = navigation;
    [navigation setNavigationBarHidden:YES animated:YES];
    // UIKit's keyboard hiding took the toolbar too, and didn't give it back: so does the stack; the toolbar comes back
    // at the next appearance of a screen with one, as before.
    if (!navigation.toolbarHidden) {
        [navigation setToolbarHidden:YES animated:YES];
    }
    // The stack's own navigation marks it in its override; UIKit's More navigation doesn't (`hideWithTopBar`).
    if (navigation != self) {
        [ANNSystemUI setNeedsUpdateOfGroups:ANNSystemUIGroupStatusBar];
        [self swipeBackFollowsBar];
    }
    [self updateRootLayoutOfShownEntries];
}

// The keyboard closed: a bar the stack hid for it comes back when the stack is in a window, the bar is still hidden
// and the screen on top doesn't hide it itself.
- (void)keyboardDidHide:(NSNotification *)notification {
    UINavigationController *navigation = self.shownNavigationController;
    UINavigationController *hidBarOf = _keyboardHidBarOf;
    _keyboardHidBarOf = nil;
    if (!navigation.viewIfLoaded.window || hidBarOf != navigation ||
        !navigation.navigationBarHidden || self.nodeHelper.destroyed) {
        return;
    }
    UIViewController *top = [self barTopEntry];
    ANNTopBarOptions *topBar = [ANNStackOptionsScreen(top) resolveOptionsWithDefault].topBar;
    if (!top || ![topBar.visible withDefault:YES]) {
        return;
    }
    [navigation setNavigationBarHidden:NO animated:YES];
    // The stack's own navigation marks it in its override; UIKit's More navigation doesn't (`hideWithTopBar`).
    if (navigation != self) {
        [ANNSystemUI setNeedsUpdateOfGroups:ANNSystemUIGroupStatusBar];
        [self swipeBackFollowsBar];
    }
    [self updateRootLayoutOfShownEntries];
}

// The end of every transition is its coordinator's completion, never `CATransaction`'s: without a coordinator (no
// animation, no window) at once. In the More tab the screens are in the More navigation, whose coordinator ends them
// (`stackRunningTransition`).
- (void)stackWhenTransitionEnds:(void (^)(BOOL cancelled))ended {
    __block BOOL called = NO;
    void (^once)(BOOL) = ^(BOOL cancelled) {
      if (called) {
          return;
      }
      called = YES;
      ended(cancelled);
    };
    id<UIViewControllerTransitionCoordinator> coordinator = [self stackRunningTransition];
    // At the end of a zoom UIKit runs the completion in a block of its animation queue: the end steps, which stop the
    // content of what left, go to the main queue first.
    void (^completion)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          BOOL cancelled = context.isCancelled;
          if (RCTIsMainQueue()) {
              once(cancelled);
          } else {
              dispatch_async(dispatch_get_main_queue(), ^{
                once(cancelled);
              });
          }
        };
    if (coordinator && [coordinator animateAlongsideTransition:nil completion:completion]) {
        return;
    }
    once(NO);
}

// The screen waits in the window at the stack's size, under UIKit's container view ( `ANNParking`): it isn't a child of
// anything there, gets no touches, is hidden from accessibility, and its appearance is driven, so UIKit's callbacks
// report nothing for it. UIKit gives such a view no safe area: its screens lay out with that of their place on top of
// the stack meanwhile (`ANNScreenPlace`). Each path that ends the wait gives the view back as it was; UIKit's push of
// the entry re-parents it in the same turn, so its departure is not ended here.
- (void)stackPark:(UIViewController *)entry {
    UINavigationController *navigation = self.shownNavigationController;
    // Its screens lay out as on top of the stack, with the bar and the toolbar the entry shows there.
    __weak UIViewController *weakEntry = entry;
    __weak UINavigationController *weakNavigation = navigation;
    ANNScreenPlace * (^place)(void) = ^ANNScreenPlace * {
      UIViewController *parked = weakEntry;
      UINavigationController *stack = weakNavigation;
      if (!parked || !stack) {
          return nil;
      }
      return [ANNScreenPlace placeOfEntry:parked
                                  inStack:stack
                                  options:[ANNStackOptionsScreen(parked) resolveOptionsWithDefault]];
    };
    for (ANNComponentViewController *screen in ANNStackScreensOfEntry(entry)) {
        screen.placeWhileParked = place;
    }
    [_parking park:entry inView:navigation.view];
}

- (void)stackUnpark:(UIViewController *)entry {
    [_parking unpark:entry endingDeparture:NO];
}

- (NSArray<UIViewController *> *)parkedEntries {
    return _parking.parkedNodes;
}

// The back button of every entry but the first: `backAction` (iOS 16+) asks the hold, else goes back. A native screen's
// item is the app's view controller's: the action goes there. The screen's root frame is set for the stack it joins.
- (void)stackEntryJoins:(UIViewController *)entry first:(BOOL)first {
    [self updateBackActionOf:entry first:first];
    [self updateRootLayoutOf:entry];
}

- (void)updateBackActionOf:(UIViewController *)entry {
    NSArray<UIViewController *> *shown = _queue.shown;
    NSUInteger index = [shown indexOfObjectIdenticalTo:entry];
    // An entry not in the list yet joins on top of it.
    [self updateBackActionOf:entry first:index == NSNotFound ? shown.count == 0 : index == 0];
}

- (void)updateBackActionOf:(UIViewController *)entry first:(BOOL)first {
    if (@available(iOS 16.0, *)) {
        UINavigationItem *item = entry.navigationItem;
        BOOL hasLeftButtons = item.leftBarButtonItems.count > 0;
        for (UIBarButtonItemGroup *group in item.leadingItemGroups) {
            hasLeftButtons = hasLeftButtons || group.barButtonItems.count > 0;
        }
        // UIKit makes a back button for a back action where the item has none, so an item that hides its back button
        // (`topBar.backButton.visible: false`) has no action either.
        if (first || hasLeftButtons || item.hidesBackButton) {
            item.backAction = nil;
            return;
        }
        if (!item.backAction) {
            __weak ANNStackController *weakSelf = self;
            item.backAction = [UIAction actionWithHandler:^(UIAction *action) {
              [weakSelf userGoBackTo:nil];
            }];
        }
    }
}

- (void)stackEntriesWillMove {
    [self stopLowerRendering];
}

- (void)stackRenderLowerEntries:(NSArray<UIViewController *> *)entries {
    [_lowerToRender addObjectsFromArray:entries];
    [self renderLowerEntriesNow];
}

- (void)stackOperationEnded {
    [self updateSwipeBack];
    // The plan UIKit never asked for goes (no window, no layout), the zoom anchors of the transition go, and the
    // delegate follows the new top.
    _pendingPlan = nil;
    [ANNZoomAnchor transitionEndedInStack:self];
    [self updateAnimationGateAtEnd];
    // After every operation, also those whose top got no appearance (off screen, a cancelled swipe, UIKit's own pops),
    // the bar's shared parts are the top's again; its visibilities only when the top is another screen than the one
    // they were set for (a cancelled swipe), never over a bar the user hid.
    [self applySharedBarIfTopChangedAnimated:NO];
    UIViewController *top = [self getCurrentChild];
    // Inside a layer that waits to show (a root's `setRoot`), its wait follows the new top; a modal's hold follows the
    // screen it shows now: a screen covered or popped holds no more.
    [ANNShownScreens changedIn:self];
    // A sheet or a modal sized to its content takes the height of the new top.
    if (top != _lastTop) {
        _lastTop = top;
        if (self.viewIfLoaded.window) {
            [ANNSheetPresenter stackDidShowScreenInModal:self];
        }
    }
}

#pragma mark - Lower screens

// Each lower screen makes its content in the window, under the top, and leaves the stack's view when its content is
// ready or after 0.5 s; it waits for nobody, and no `markReady`. A cut or a replace takes them out before it calls
// UIKit.
- (void)renderLowerEntriesNow {
    if (!self.viewIfLoaded.window || [ANNPendingLayers isWaitingToShow:self] ||
        self.nodeHelper.destroyed || _lowerToRender.count == 0) {
        return;
    }
    NSArray<UIViewController *> *entries = [_lowerToRender copy];
    [_lowerToRender removeAllObjects];
    NSArray<UIViewController *> *shown = _queue.shown;
    for (UIViewController *entry in entries) {
        if ([shown indexOfObjectIdenticalTo:entry] == NSNotFound || entry == shown.lastObject ||
            entry.isViewLoaded || [_lowerRendering objectForKey:entry]) {
            continue;
        }
        [self renderLowerEntry:entry];
    }
}

- (void)renderLowerEntry:(UIViewController *)entry {
    NSNumber *number = @(++_lowerNumber);
    [_lowerRendering setObject:number forKey:entry];
    __weak ANNStackController *weakSelf = self;
    __weak UIViewController *weakEntry = entry;
    [entry setReadyHandler:^{
      [weakSelf lowerEntry:weakEntry renderedWithNumber:number];
    }
                    forKey:ANNLowerRenderReadyKey];
    // Parking loads the view, and a native screen is ready inside that load: the readiness test below ends it.
    [_lowerSettingUp addObject:entry];
    @try {
        [self stackPark:entry];
        [entry render];
    } @catch (NSException *exception) {
        RCTLogWarn(@"[ApexNavigation] could not render the screen '%@': %@",
                   entry.layoutInfo.componentId ?: @"", exception.reason ?: exception.name);
        [_lowerSettingUp removeObject:entry];
        [self lowerEntry:entry renderedWithNumber:number];
        return;
    }
    [_lowerSettingUp removeObject:entry];
    [self updateRootLayoutOf:entry];
    if (ANNStackEntryIsReady(entry)) {
        [self lowerEntry:entry renderedWithNumber:number];
        return;
    }
    int64_t delay = (int64_t)(ANNLowerScreenRenderLimit * NSEC_PER_SEC);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
      [weakSelf lowerEntry:weakEntry renderedWithNumber:number];
    });
}

- (void)lowerEntry:(UIViewController *)entry renderedWithNumber:(NSNumber *)number {
    if (!entry || [_lowerSettingUp containsObject:entry] ||
        ![[_lowerRendering objectForKey:entry] isEqualToNumber:number]) {
        return;
    }
    [_lowerRendering removeObjectForKey:entry];
    [entry removeReadyHandlerForKey:ANNLowerRenderReadyKey];
    [self stackUnpark:entry];
}

- (void)stopLowerRendering {
    NSMutableArray<UIViewController *> *entries = [NSMutableArray new];
    for (UIViewController *entry in _lowerRendering.keyEnumerator) {
        [entries addObject:entry];
    }
    for (UIViewController *entry in entries) {
        [self lowerEntry:entry renderedWithNumber:[_lowerRendering objectForKey:entry]];
    }
}

#pragma mark - Helpers

- (void)updateRootLayoutOf:(UIViewController *)entry {
    for (ANNComponentViewController *screen in ANNStackScreensOfEntry(entry)) {
        [screen updateRootLayout];
    }
}

@end

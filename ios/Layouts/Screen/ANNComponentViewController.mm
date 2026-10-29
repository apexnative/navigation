#import "ANNComponentViewController.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNBottomTabsController.h"
#import "ANNComponentPresenter.h"
#import "ANNHinge.h"
#import "ANNKeyCommands.h"
#import "ANNNodeRegistry.h"
#import "ANNParking.h"
#import "ANNPendingLayers.h"
#import "ANNPreviewPush.h"
#import "ANNScreenBars.h"
#import "ANNScreenEnvironment.h"
#import "ANNScreenReadiness.h"
#import "ANNScreenRegions.h"
#import "ANNScreenSafeArea.h"
#import "ANNStackController.h"
#import "ANNSurfaceHostingView.h"
#import "ANNSystemUI.h"
#import "ANNTopBarPresenter.h"
#import "ANNUtils.h"

@implementation ANNComponentViewController

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(NSArray<UIViewController *> *)childViewControllers {
    self = [super initWithNibName:nil bundle:nil];
    if (self) {
        // The screen's view reaches under opaque bars too: the "under bars" rule places the content.
        self.extendedLayoutIncludesOpaqueBars = YES;
        // Each screen has its own looks of the bar: its presenter fills them from the bar's options
        // (`ANNTopBarPresenter`), never replaces them by nil.
        self.navigationItem.standardAppearance = [UINavigationBarAppearance new];
        self.navigationItem.scrollEdgeAppearance = [UINavigationBarAppearance new];
    }
    [self.nodeHelper setUpWithLayoutInfo:layoutInfo
                                 creator:creator
                                 options:options
                          defaultOptions:defaultOptions
                               presenter:presenter
                            eventEmitter:eventEmitter];
    // While the screen lives (removed at its destroy): the end of an edit gives the key commands back, and a change of
    // the hinge's state moves the regions without a change of size or traits.
    NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
    [center addObserver:self
               selector:@selector(textDidEndEditing:)
                   name:UITextFieldTextDidEndEditingNotification
                 object:nil];
    [center addObserver:self
               selector:@selector(textDidEndEditing:)
                   name:UITextViewTextDidEndEditingNotification
                 object:nil];
    [center addObserver:self
               selector:@selector(hingeDidChange:)
                   name:ANNHingeDidChangeNotification
                 object:nil];
    return self;
}

#pragma mark - The view

- (nullable ANNComponentPresenter *)componentPresenter {
    ANNNodePresenter *presenter = self.presenter;
    return [presenter isKindOfClass:ANNComponentPresenter.class] ? (ANNComponentPresenter *)presenter
                                                                  : nil;
}

// The size of the screen of the navigation window's scene, not the deprecated main screen: a surface made before the
// screen is in a window starts with the width it will have.
- (void)loadView {
    UIView *view = [[UIView alloc] initWithFrame:ANNUtils.mainScreen.bounds];
    view.backgroundColor = UIColor.systemBackgroundColor;
    self.view = view;
}

// A screen nobody rendered (a lower screen of a stack, a tab not shown yet) gets its content as UIKit first loads its
// view.
- (void)viewDidLoad {
    [super viewDidLoad];
    [self render];
    [self applyBackground];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [self updateRootLayout];
    [[ANNTopBarPresenter presenterOfScreen:self] screenDidLayOut];
    // A bar shown or hidden lays the screen out again, also on UIKit's More navigation, which the stack doesn't watch:
    // the swipes back follow it.
    UINavigationController *stack = self.stack;
    if ([stack isKindOfClass:ANNStackController.class]) {
        [(ANNStackController *)stack swipeBackFollowsBar];
    }
    // The pair the screen is in may lie otherwise now.
    [self.arrangement screenDidChangeLayout:self];
    [self.componentPresenter layOutStatusBarBlur];
}

// UIKit names the size transition as the moment the status bar's frame changes (a rotation, a new window size): the
// blur follows it along the animation and takes the final frame at its end. The React content takes its new size
// without animation meanwhile, or what it draws itself is stretched along the animation (`ANNSurfaceHostingView`).
- (void)viewWillTransitionToSize:(CGSize)size
       withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator {
    [super viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
    [ANNSurfaceHostingView followSizeTransition:coordinator];
    __weak ANNComponentViewController *weakSelf = self;
    [coordinator
        animateAlongsideTransition:^(id<UIViewControllerTransitionCoordinatorContext> context) {
          [weakSelf.componentPresenter layOutStatusBarBlur];
        }
        completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
          [weakSelf.componentPresenter layOutStatusBarBlur];
        }];
}

- (void)viewSafeAreaInsetsDidChange {
    [super viewSafeAreaInsetsDidChange];
    [self updateRootLayout];
}

// The regions UIKit reserves are still the old ones while it tells of new traits: they are read again on the next turn
// of the main queue. A fold changes the traits, not always the size.
- (void)traitCollectionDidChange:(UITraitCollection *)previousTraitCollection {
    [super traitCollectionDidChange:previousTraitCollection];
    [self updateRootLayout];
    [self.arrangement screenDidChangeLayout:self];
    __weak ANNComponentViewController *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      [weakSelf updateRootLayout];
      [weakSelf.componentPresenter layOutStatusBarBlur];
    });
}

#pragma mark - UIKit's appearance

// UIKit forwards appearance through containment: a stack, tabs, a presentation call these on the screen they show or
// hide. They are the screen's report as a container gives it; the dispatcher keeps it to one per appearance. While a
// command drives the appearance (a window's root replaced), UIKit's calls come at its own moments and would break the
// command's order: the command reports instead.
- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    // A preview screen that UIKit's menu takes leaves its unseen place first, so that the menu reports it.
    [ANNPreviewPush screenWillAppear:self];
    if (![ANNAppearanceDispatcher isAppearanceDriven:self]) {
        [self screenWillAppear];
    }
}

// The system UI follows UIKit's screens, driven or not: the owner asks again at the end of the turn (the orientation
// after the transition, the deferred edges the previous screen may have set, the overlays). So do the pair, the
// hinge (once a screen showed) and the key commands, for native screens too.
- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [ANNSystemUI setNeedsUpdate];
    [self.arrangement screenDidChangeLayout:self];
    [ANNHinge watchForScreens];
    // A parked screen whose place gave it no safe area at all lays out with its view's own from now on.
    if (self.placeWhileParked && ![ANNParking isUnseen:self]) {
        self.placeWhileParked = nil;
        self.placeKeptUntilAppearance = NO;
        [self updateRootLayout];
    }
    // Once JavaScript heard the appearance (a driven one ends later, with its command).
    __weak ANNComponentViewController *weakSelf = self;
    [ANNAppearanceDispatcher afterDidAppearOf:self.appearance
                                          run:^{
                                            [weakSelf takeKeyCommandsFocus];
                                          }];
    if (![ANNAppearanceDispatcher isAppearanceDriven:self]) {
        [self screenDidAppear];
        SEL undriven = @selector(descendantDidAppear:);
        for (UIViewController *parent = self.layoutParent; parent; parent = parent.layoutParent) {
            if ([parent respondsToSelector:undriven]) {
                [(id<ANNLayoutProtocol>)parent descendantDidAppear:self];
            }
        }
        return;
    }
    // A tab switch the tabs drive ends as UIKit shows the new tab's screen. Every parent hears it: with tabs inside
    // tabs the switch may be the outer tabs', and each tabs node ends only its own.
    SEL selector = @selector(drivenDescendantDidAppear:);
    for (UIViewController *parent = self.layoutParent; parent; parent = parent.layoutParent) {
        if ([parent respondsToSelector:selector]) {
            [(id<ANNLayoutProtocol>)parent drivenDescendantDidAppear:self];
        }
    }
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [ANNSystemUI setNeedsUpdate];
    [self.arrangement screenDidChangeLayout:self];
    if (![ANNAppearanceDispatcher isAppearanceDriven:self]) {
        [self screenDidDisappear];
    }
}

#pragma mark - Appearance

- (ANNScreenAppearance *)appearance {
    return self.reactView.appearance;
}

// Every report of an appearance passes here, UIKit's or a command's: the options apply with the parents the screen
// has now, then the content hears it, then the components of its bar.
- (void)screenWillAppear {
    [self.presenter applyOptions:[self resolveOptionsWithDefault]];
    [self.reactView componentWillAppear];
    [[ANNTopBarPresenter presenterOfScreen:self] screenWillAppear];
}

- (void)screenDidAppear {
    [self.reactView componentDidAppear];
    [[ANNTopBarPresenter presenterOfScreen:self] screenDidAppear];
}

- (void)screenDidDisappear {
    [self.reactView componentDidDisappear];
    [[ANNTopBarPresenter presenterOfScreen:self] screenDidDisappear];
}

#pragma mark - The root frame and the safe area

- (void)applyBackground {
    [self.componentPresenter applyBackground:[self resolveOptionsWithDefault]];
}

// The navigation controller that shows the screen's stack: the stack, or UIKit's More navigation while a tab of More
// holds its screens.
- (nullable UINavigationController *)shownNavigation {
    UINavigationController *stack = self.stack;
    if ([stack isKindOfClass:ANNStackController.class]) {
        return [(ANNStackController *)stack shownNavigationController];
    }
    return stack;
}

// The screen's stack shows its top bar: the stack sets `navigationBarHidden` by its top screen's `topBar.visible` and
// then lays its screens out again (`ANNStackPresenter`). In More it is the More navigation's bar that shows.
- (BOOL)hasShownTopBar {
    UINavigationController *navigation = [self shownNavigation];
    return navigation != nil && !navigation.navigationBarHidden;
}

// The nearest tabs answer whether a tab bar shows at the bottom for this screen.
- (BOOL)hasShownTabBar {
    return [[ANNBottomTabsController tabsOf:self] showsTabBarForScreen:self];
}

// UIKit hides the tab bar with the push of a screen that answers YES, and brings it back with the pop; the presenter
// answers by the screen's resolved options, the idiom and the version.
- (BOOL)hidesBottomBarWhenPushed {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super hidesBottomBarWhenPushed];
    }
    return [presenter hidesBottomBarWhenPushedForOptions:[self resolveOptionsWithDefault]];
}

#pragma mark - The system UI

// What a question of `group` is answered with: the screen's options for it, or, for a screen of an overlay that
// doesn't set the group, nil — the main window's deciding controller answers then (`ANNSystemUI`).
- (nullable ANNNavigationOptions *)systemUIOptionsFor:(ANNSystemUIGroup)group {
    return [ANNSystemUI optionsOfScreen:self forGroup:group];
}

// The main window's deciding controller for an overlay screen's question; never this screen (it is in no overlay).
- (nullable UIViewController *)mainDeciderFor:(ANNSystemUIQuestion)question {
    UIViewController *decider = [ANNSystemUI deciderInWindow:ANNUtils.mainWindow question:question];
    return decider != self ? decider : nil;
}

- (UIStatusBarStyle)preferredStatusBarStyle {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super preferredStatusBarStyle];
    }
    ANNNavigationOptions *options = [self systemUIOptionsFor:ANNSystemUIGroupStatusBar];
    if (!options) {
        UIViewController *decider = [self mainDeciderFor:ANNSystemUIQuestionStatusBarStyle];
        return decider ? decider.preferredStatusBarStyle : [super preferredStatusBarStyle];
    }
    return [presenter statusBarStyleForOptions:options];
}

// `hideWithTopBar` reads the bar of the screen's own navigation controller as UIKit asks.
- (BOOL)prefersStatusBarHidden {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super prefersStatusBarHidden];
    }
    ANNNavigationOptions *options = [self systemUIOptionsFor:ANNSystemUIGroupStatusBar];
    if (!options) {
        UIViewController *decider = [self mainDeciderFor:ANNSystemUIQuestionStatusBarHidden];
        return decider ? decider.prefersStatusBarHidden : [super prefersStatusBarHidden];
    }
    return [presenter statusBarHiddenForOptions:options
                            navigationBarHidden:[ANNSystemUI barHiddenOf:[self shownNavigation]]];
}

// The visibility never animates (`statusBar.animate`): the style's own animation is the owner's.
- (UIStatusBarAnimation)preferredStatusBarUpdateAnimation {
    return UIStatusBarAnimationNone;
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return ANNUtils.appOrientationMask;
    }
    ANNNavigationOptions *options = [self systemUIOptionsFor:ANNSystemUIGroupOrientation];
    if (!options) {
        UIViewController *decider = [self mainDeciderFor:ANNSystemUIQuestionOrientations];
        return decider ? decider.supportedInterfaceOrientations : ANNUtils.appOrientationMask;
    }
    return [presenter supportedOrientationsForOptions:options];
}

- (BOOL)prefersHomeIndicatorAutoHidden {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super prefersHomeIndicatorAutoHidden];
    }
    ANNNavigationOptions *options = [self systemUIOptionsFor:ANNSystemUIGroupHomeIndicator];
    if (!options) {
        UIViewController *decider = [self mainDeciderFor:ANNSystemUIQuestionHomeIndicator];
        return decider ? decider.prefersHomeIndicatorAutoHidden
                       : [super prefersHomeIndicatorAutoHidden];
    }
    return [presenter homeIndicatorAutoHiddenForOptions:options];
}

- (UIRectEdge)preferredScreenEdgesDeferringSystemGestures {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super preferredScreenEdgesDeferringSystemGestures];
    }
    ANNNavigationOptions *options = [self systemUIOptionsFor:ANNSystemUIGroupDeferredEdges];
    if (!options) {
        UIViewController *decider = [self mainDeciderFor:ANNSystemUIQuestionDeferredEdges];
        return decider ? decider.preferredScreenEdgesDeferringSystemGestures
                       : [super preferredScreenEdgesDeferringSystemGestures];
    }
    return [presenter deferredEdgesForOptions:options];
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
- (UIVerticalBarBehavior)preferredVerticalBarBehavior API_AVAILABLE(ios(27.1)) {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super preferredVerticalBarBehavior];
    }
    ANNNavigationOptions *options = [self systemUIOptionsFor:ANNSystemUIGroupVerticalBars];
    if (!options) {
        UIViewController *decider = [self mainDeciderFor:ANNSystemUIQuestionVerticalBars];
        return decider ? decider.preferredVerticalBarBehavior
                       : [super preferredVerticalBarBehavior];
    }
    return [presenter verticalBarBehaviorForOptions:options];
}
#endif

// The place the screen waits to show in, while it is parked out of it and until UIKit gives it a safe area of its
// own there: the push lays the screen out once before that, with none, and a list laid out then at the screen's top
// would stay the list the bar follows. A place that shows with no safe area at all hands over as the screen appears.
- (nullable ANNScreenPlace *)parkedPlace {
    ANNScreenPlace * (^place)(void) = self.placeWhileParked;
    if (!place) {
        return nil;
    }
    if (!self.placeKeptUntilAppearance && ![ANNParking isUnseen:self] &&
        self.parentViewController &&
        !UIEdgeInsetsEqualToEdgeInsets(self.view.safeAreaInsets, UIEdgeInsetsZero)) {
        self.placeWhileParked = nil;
        return nil;
    }
    return place();
}

- (CGRect)rootFrameForOptions:(ANNNavigationOptions *)options {
    UIView *view = self.view;
    ANNScreenPlace *place = [self parkedPlace];
    BOOL topBarShown = place ? place.showsTopBar : [self hasShownTopBar];
    BOOL tabBarShown = place ? place.showsTabBar : [self hasShownTabBar];
    BOOL fromTop = ANNScreenIsUnderTopBar(options) || !topBarShown;
    BOOL toBottom = ANNScreenIsUnderTabBar(options) || !tabBarShown;
    UIEdgeInsets safeArea = place ? place.safeArea : view.safeAreaInsets;
    return ANNScreenRootFrame(view.bounds, safeArea, fromTop, toBottom);
}

- (void)updateRootLayout {
    if (!self.isViewLoaded) {
        return;
    }
    ANNNavigationOptions *options = [self resolveOptionsWithDefault];
    UIView<ANNComponentProtocol> *content = _reactView;
    if (content) {
        CGRect frame = [self rootFrameForOptions:options];
        if (!CGRectEqualToRect(content.frame, frame)) {
            content.frame = frame;
        }
        // The surface follows the root frame even when the frame itself didn't change now.
        if ([content isKindOfClass:ANNReactView.class]) {
            [(ANNReactView *)content updateSurfaceSizeIfNeeded];
        }
    }
    [self updateSafeAreaWithOptions:options];
}

// The physical side of the iPhone Duo vertical bar (iOS 27.1): UIKit names it by the leading or trailing edge, which
// is the other physical side from right to left.
- (UIRectEdge)verticalBarSide {
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
    if (@available(iOS 27.1, *)) {
        UIVerticalBarEdge edge = self.traitCollection.verticalBarEdge;
        if (edge == UIVerticalBarEdgeUnspecified) {
            return UIRectEdgeNone;
        }
        BOOL rightToLeft = self.view.effectiveUserInterfaceLayoutDirection ==
                           UIUserInterfaceLayoutDirectionRightToLeft;
        BOOL leading = edge == UIVerticalBarEdgeLeading;
        return leading == rightToLeft ? UIRectEdgeRight : UIRectEdgeLeft;
    }
#endif
    return UIRectEdgeNone;
}

// The insets and the padding of a React screen or a slot screen go to the store, which keeps them, gives JavaScript
// what still covers the content and tells only of changes; the regions in the same pass. Only in a window and with an
// id; never after the destroy, which removed them (a late layout would bring them back), nor for the JavaScript that
// went (an old screen held after a reload: the new JavaScript may give its id to a new screen).
- (void)updateSafeAreaWithOptions:(ANNNavigationOptions *)options {
    UIView<ANNComponentProtocol> *content = _reactView;
    NSString *componentId = self.layoutInfo.componentId;
    ANNEventEmitter *eventEmitter = self.eventEmitter;
    UIView *view = self.view;
    UIWindow *window = view.window;
    if (!content || componentId.length == 0 || !eventEmitter.isCurrent || !window ||
        self.nodeHelper.destroyed) {
        return;
    }
    // A parked screen has no safe area of its own: it takes its place's.
    ANNScreenPlace *place = [self parkedPlace];
    UIEdgeInsets safeArea = place ? place.safeArea : view.safeAreaInsets;
    UIEdgeInsets insets = ANNScreenInsetsOverRoot(content.frame, view.bounds.size, safeArea);
    UINavigationController *stack = [self shownNavigation];
    ANNScreenPaddingContext context = {
        .inStack = stack != nil,
        .verticalBarSide = [self verticalBarSide],
    };
    UIEdgeInsets padding = ANNScreenPadding(insets, options, context);
    [ANNScreenEnvironment setInsets:insets
                            padding:padding
                        forScreenId:componentId
                       eventEmitter:eventEmitter];
    [self updateRegions];
}

// The regions in the same pass, and on the next turn after a change of the hinge's state (`hingeDidChange:`). Nil
// before iOS 27.1: nothing to keep. The store neither keeps nor sends an empty first result.
- (void)updateRegions {
    UIView<ANNComponentProtocol> *content = _reactView;
    NSString *componentId = self.layoutInfo.componentId;
    ANNEventEmitter *eventEmitter = self.eventEmitter;
    UIView *view = self.viewIfLoaded;
    if (!content || componentId.length == 0 || !eventEmitter.isCurrent || !view.window ||
        self.nodeHelper.destroyed) {
        return;
    }
    NSDictionary<NSString *, NSArray *> *regions = [ANNScreenRegions regionsOfView:view
                                                                   inCoordinatesOf:content];
    if (!regions) {
        return;
    }
    [ANNScreenEnvironment setRegions:regions forScreenId:componentId eventEmitter:eventEmitter];
}

- (void)hingeDidChange:(NSNotification *)notification {
    // UIKit's regions are still the old ones while the hinge tells of its new state.
    __weak ANNComponentViewController *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      [weakSelf updateRegions];
    });
}

#pragma mark - Key commands

// The screen's own `keyCommands`, else those of its nearest layout parent that has them: an empty array is a value and
// clears its parents'. The default options don't take part. Read each time UIKit asks; the walk ends at a view
// controller that is no node (UIKit's own).
- (nullable NSArray<NSDictionary *> *)keyCommandOptions {
    for (UIViewController *node = self; node.isLayoutNode; node = node.layoutParent) {
        NSArray<NSDictionary *> *commands = node.options.keyCommands;
        if (commands) {
            return commands;
        }
    }
    return nil;
}

// UIKit's commands of the screen, then its own: they apply to the whole responder chain, so a focused field of the
// screen keeps them.
- (NSArray<UIKeyCommand *> *)keyCommands {
    NSArray<UIKeyCommand *> *commands = [super keyCommands] ?: @[];
    // A preview's screen, also while UIKit's menu shows it, has none: it is no screen of the layout yet.
    BOOL preview = [ANNPreviewPush isPreviewScreen:self];
    NSArray<NSDictionary *> *options = preview ? nil : [self keyCommandOptions];
    if (options.count == 0) {
        return commands;
    }
    SEL action = @selector(keyCommandPressed:);
    return [commands arrayByAddingObjectsFromArray:ANNKeyCommandsFromOptions(options, action)];
}

- (BOOL)canBecomeFirstResponder {
    return [self keyCommandOptions].count > 0;
}

- (void)keyCommandPressed:(UIKeyCommand *)command {
    id keyCommandId = command.propertyList;
    NSString *componentId = self.layoutInfo.componentId;
    if (![keyCommandId isKindOfClass:NSString.class] || !componentId) {
        return;
    }
    [self.eventEmitter sendKeyCommandPressed:componentId keyCommandId:keyCommandId];
}

// Only a screen the user sees: not one that waits unseen in a window (parked, or in a layer that waits to show), and
// not a preview's screen, which is no screen of the layout yet.
- (BOOL)takeKeyCommandsFocus {
    if (!self.viewIfLoaded.window || self.nodeHelper.destroyed ||
        [self keyCommandOptions].count == 0 || [ANNParking isUnseen:self] ||
        [ANNPendingLayers isWaitingToShow:self] || [ANNPreviewPush isPreviewScreen:self] ||
        !ANNNoFirstResponder()) {
        return NO;
    }
    return [self becomeFirstResponder];
}

// During the notification the field is still the first responder: the screen tries on the next turn.
- (void)textDidEndEditing:(NSNotification *)notification {
    __weak ANNComponentViewController *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      [weakSelf takeKeyCommandsFocus];
    });
}

#pragma mark - Leaving

- (void)screenWillLeave {
    if (!self.isViewLoaded) {
        return;
    }
    if ([[self resolveOptionsWithDefault].blurOnUnmount withDefault:NO]) {
        [self.view endEditing:YES];
    }
}

+ (void)nodeWillLeave:(UIViewController *)node {
    if ([node isKindOfClass:ANNComponentViewController.class]) {
        [(ANNComponentViewController *)node screenWillLeave];
    }
    for (UIViewController *child in node.layoutChildren) {
        [self nodeWillLeave:child];
    }
}

#pragma mark - ANNLayoutProtocol

- (UIViewController *)presentedComponentViewController {
    return self;
}

- (NSArray<UIViewController *> *)shownComponentViewControllers {
    return @[ self ];
}

// A screen is a leaf of the layout: a view controller it embeds (a native screen's) is not a node to walk or destroy.
- (NSArray<UIViewController *> *)layoutChildren {
    return @[];
}

// The content is made here, not at init: a screen made for a layout that a later check rejects never starts a React
// surface. Once; the content sits in the root frame, clear and clipped (the core never touches what is inside it), and
// tells the screen when it is ready (`readyForPresentation`, which runs the node's ready handlers).
- (void)render {
    if (_reactView || self.nodeHelper.destroyed) {
        return;
    }
    // The first load of the view renders through `viewDidLoad`.
    UIView *view = self.view;
    if (_reactView || self.nodeHelper.destroyed) {
        return;
    }
    ANNLayoutInfo *layoutInfo = self.layoutInfo;
    __weak ANNComponentViewController *weakSelf = self;
    UIView<ANNComponentProtocol> *content =
        [self.creator createComponentView:layoutInfo.name ?: @""
                              componentId:layoutInfo.componentId
                            componentType:ANNComponentTypeComponent
                      reactViewReadyBlock:^{
                        [weakSelf readyForPresentation];
                      }];
    if (!content) {
        return;
    }
    _reactView = content;
    content.clipsToBounds = YES;
    content.autoresizingMask = UIViewAutoresizingNone;
    content.frame = [self rootFrameForOptions:[self resolveOptionsWithDefault]];
    [view addSubview:content];
    [self.componentPresenter keepStatusBarBlurOnTop];
    [self applyBackground];
    [self updateRootLayout];
}

// A pair's `destroyReactView` reaches only this (nothing calls it but a destroy); the roots of the screen's bar
// components stop at its destroy (step 3).
- (void)destroyReactView {
    [self.reactView stopSurface];
}

- (void)destroy {
    if (![self.nodeHelper markDestroyed]) {
        return;
    }
    [NSNotificationCenter.defaultCenter removeObserver:self];
    // JavaScript hears the screen go before its content stops; a screen hidden already ignores it.
    [ANNAppearanceDispatcher nodeDidDisappear:self];
    [self.nodeHelper unregisterNode];
    // Step 2: the lists of the screen get their system edge effect back and the large title's color leaves them. Step
    // 3: the roots of the screen's bar components (title, buttons, background, search results) stop, on the main queue,
    // once.
    ANNTopBarPresenter *topBar = [ANNTopBarPresenter presenterOfScreen:self];
    [topBar releaseLists];
    [topBar stopComponents];
    [self destroyReactView];
    // The stop holds the hosting view until the surface stopped: the screen lets go of its content view now.
    [_reactView removeFromSuperview];
    // The data is kept by id: a node that holds the id now (a new JavaScript reused it while this screen waited for
    // its destroy, as at a reload) keeps it.
    NSString *componentId = self.layoutInfo.componentId;
    if (componentId && ![ANNNodeRegistry nodeForComponentId:componentId]) {
        [ANNScreenEnvironment removeScreenId:componentId];
        [ANNScreenReadiness removeScreenId:componentId];
    }
}

@end

#import "ANNBottomTabsController.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNBarBackgroundComponent.h"
#import "ANNBottomTabsPresenter.h"
#import "ANNComponentViewController.h"
#import "ANNMoreNavigationDelegate.h"
#import "ANNNavigationState.h"
#import "ANNNodeRegistry.h"
#import "ANNPendingLayers.h"
#import "ANNScreenBars.h"
#import "ANNScrollToTop.h"
#import "ANNShownScreens.h"
#import "ANNStackController.h"
#import "ANNSystemUI.h"
#import "ANNTabLongPress.h"
#import "ANNTabsDesign.h"
#import "ANNTabsPreloadWindow.h"
#import "InteractivePopGestureDelegate.h"
#import <React/RCTLog.h>

// The longest the tabs drive the appearance of a switch whose new screen UIKit never shows (it is not a wait: nothing
// waits for it; the drive only must not stay).
static const NSTimeInterval ANNSwitchDriveLimit = 2;

// How long a step of `afterInitialTab` waits for its tab's root to mount before the next one starts, and how long a
// lent React view stays in the preload window at most without mounting.
static const NSTimeInterval ANNTabMountLimit = 0.5;

// The key of the tabs' ready handlers on the screens of their selected tab (`afterInitialTab`).
static NSString *const ANNTabsReadyKey = @"ApexNavigation.tabs";

// Whether UIKit pops the stack of a tab tapped again itself: the core answers "select" and lets it. NO would make the
// core pop it for every source and answer "don't" (the simulator decides).
static const BOOL ANNUIKitPopsOnRepeatPress = YES;

static ANNTabsAttachMode ANNAttachModeOf(ANNBottomTabsOptions *bottomTabs) {
    NSString *mode = [bottomTabs.tabsAttachMode withDefault:nil];
    if ([mode isEqualToString:@"afterInitialTab"]) {
        return ANNTabsAttachModeAfterInitialTab;
    }
    if ([mode isEqualToString:@"onSwitchToTab"]) {
        return ANNTabsAttachModeOnSwitchToTab;
    }
    return ANNTabsAttachModeTogether;
}

// The index as the options give it, without its fraction; NO when it is out of the tabs (one text on both platforms, a
// negative one too).
static BOOL ANNTabIndexOf(Double *value, NSUInteger count, NSUInteger *index) {
    double given = value.get;
    double whole = trunc(given);
    if (whole < 0 || whole >= (double)count) {
        RCTLogWarn(
            @"[ApexNavigation] bottomTabs.currentTabIndex %@ is out of range: the layout has "
            @"%lu tabs",
            @(given), (unsigned long)count);
        return NO;
    }
    *index = (NSUInteger)whole;
    return YES;
}

// The stacks of a tab: the tab itself, or those inside it (a side menu's center and menus).
static void ANNStacksOfNode(UIViewController *node, NSMutableArray<ANNStackController *> *stacks) {
    if ([node isKindOfClass:ANNStackController.class]) {
        [stacks addObject:(ANNStackController *)node];
        return;
    }
    for (UIViewController *child in node.layoutChildren) {
        ANNStacksOfNode(child, stacks);
    }
}

@interface ANNBottomTabsController ()
@property(nonatomic, readwrite) NSUInteger selectedTabIndex;
@end

@implementation ANNBottomTabsController {
    NSArray<UIViewController *> *_tabs;
    // The `UITab` of each tab, in layout order (iOS 18 and later).
    NSArray *_uiTabs;
    NSHashTable<UIViewController *> *_renderedTabs;
    ANNTabsPreloadWindow *_preloadWindow;
    NSHashTable<ANNComponentViewController *> *_lentScreens;
    // The chain of `afterInitialTab`: started once; a destroy ends it by its number.
    BOOL _chainStarted;
    NSUInteger _chainNumber;
    NSHashTable<ANNComponentViewController *> *_chainWaited;
    // A user's press that UIKit is to finish (`didSelect…`): the tab and the one it leaves.
    NSUInteger _pressedIndex;
    NSUInteger _pressedPrevious;
    // A choice from code runs UIKit's selection: its delegate calls say nothing. The tab chosen stays marked until the
    // next turn, for the More navigation's `willShow`.
    BOOL _selectingFromCode;
    NSUInteger _codeChoiceIndex;
    // `ANN.BottomTabSelected` waiting for the new tab's screen to appear: its tabs, by number.
    BOOL _hasPendingSelected;
    NSUInteger _pendingSelectedIndex;
    NSUInteger _pendingSelectedPrevious;
    NSUInteger _pendingSelectedNumber;
    // The tab long pressed now, whose selection by UIKit at the release is cancelled.
    NSUInteger _longPressedIndex;
    ANNTabLongPress *_longPress;
    ANNMoreNavigationDelegate *_moreDelegate;
    __weak UIViewController *_moreShownTop;
    // The tabs a repaint left for their next selection (they weren't in a window).
    NSHashTable<UIViewController *> *_staleTabs;
    // The switch the tabs drive: its tabs, whether it sends `Selected`, its number.
    BOOL _switchActive;
    NSUInteger _switchFrom;
    NSUInteger _switchTo;
    BOOL _switchSendsSelected;
    NSUInteger _switchNumber;
    // A More pick whose screen shows without a transition: `Selected` is decided as the More navigation showed it.
    dispatch_block_t _decideAtMoreDidShow;
    // UIKit's own look of the More navigation's bar, given back to its list.
    UINavigationBarAppearance *_moreListAppearance;
    __weak ANNStackController *_moreStack;
    // Before iOS 18: whether the explicit visibility was applied once, and the core's hidden state.
    BOOL _visibilityApplied;
    BOOL _hiddenBeforeTabAPI;
    // Whether the bar has laid out in a window once (its titles are measured again then).
    BOOL _barLaidOutInWindow;
}

#pragma mark - Making

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(NSArray<UIViewController *> *)childViewControllers {
    self = [super initWithNibName:nil bundle:nil];
    if (self) {
        _tabs = [childViewControllers copy] ?: @[];
        _renderedTabs = [NSHashTable weakObjectsHashTable];
        _lentScreens = [NSHashTable weakObjectsHashTable];
        _staleTabs = [NSHashTable weakObjectsHashTable];
        _pressedIndex = NSNotFound;
        _codeChoiceIndex = NSNotFound;
        _longPressedIndex = NSNotFound;
        _components = [[ANNReactComponentRegistry alloc] initWithCreator:creator];
        // The tabs are every tab's parent by the layout, also where UIKit gives none (a tab of More with the tab API).
        for (UIViewController *tab in _tabs) {
            [tab setLayoutParentFallback:self];
        }
        // The first tab from the layout's own one-shot options, before the node keeps them without.
        _selectedTabIndex = [self initialTabIndexOf:options.bottomTabs];
        // By assignment: with an overridden getter alone UIKit calls none of the `UITab` methods.
        self.delegate = self;
    }
    [self.nodeHelper setUpWithLayoutInfo:layoutInfo
                                 creator:creator
                                 options:options
                          defaultOptions:defaultOptions
                               presenter:presenter
                            eventEmitter:eventEmitter];
    [self setUpTabs];
    return self;
}

// `currentTabId` when a tab is or holds a layout with that id, else `currentTabIndex` in range, else the first.
- (NSUInteger)initialTabIndexOf:(ANNBottomTabsOptions *)bottomTabs {
    NSString *tabId = [bottomTabs.currentTabId withDefault:nil];
    if (tabId.length > 0) {
        NSUInteger index = [self indexOfTabWithLayoutId:tabId];
        if (index != NSNotFound) {
            return index;
        }
    }
    NSUInteger index = 0;
    if (bottomTabs.currentTabIndex.hasValue &&
        ANNTabIndexOf(bottomTabs.currentTabIndex, _tabs.count, &index)) {
        return index;
    }
    return 0;
}

// The tabs go to UIKit once their items are drawn: the attach mode and the custom row's mode first, then the items,
// then the tabs with the first one selected, then what needs the tabs in the bar.
- (void)setUpTabs {
    ANNNavigationOptions *resolved = [self resolveOptionsWithDefault];
    _attachMode = ANNAttachModeOf(resolved.bottomTabs);
    _usesCustomRow = [self decideCustomRow];
    ANNBottomTabsPresenter *presenter = [self tabsPresenter];
    [presenter applyFirstLook];
    if (@available(iOS 18.0, *)) {
        _usesTabAPI = YES;
        _uiTabs = [self makeUITabs];
    }
    [presenter styleItemsBeforeTabsAreSet];
    [presenter configureSidebarAndCustomizationBeforeTabsAreSet];
    // UIKit's calls while the tabs are set are no user's press.
    _selectingFromCode = YES;
    @try {
        if (@available(iOS 18.0, *)) {
            NSArray<UITab *> *uiTabs = _uiTabs;
            self.tabs = uiTabs;
        } else {
            self.viewControllers = _tabs;
        }
    } @finally {
        _selectingFromCode = NO;
    }
    [self selectInUIKit:_selectedTabIndex];
    // No Edit in More: set again after every list of tabs, which may reset it.
    self.customizableViewControllers = nil;
    [self setUpMoreNavigation];
    [presenter applyAfterTabsAreSet:resolved];
    // The first shown screen's `visible: false` hides the bar before the first frame, without animation.
    [self applyFirstVisibility];
}

// Mixed components fall back to the system's items; with every tab's component, the custom row.
- (BOOL)decideCustomRow {
    NSUInteger withComponent = 0;
    for (UIViewController *tab in _tabs) {
        NSString *name = [[tab resolveOptionsWithDefault].bottomTab.component.name withDefault:nil];
        if (name.length > 0) {
            withComponent++;
        }
    }
    if (withComponent > 0 && withComponent < _tabs.count) {
        RCTLogWarn(
            @"[ApexNavigation] Mixed bottomTab.component usage detected (%lu of %lu tabs). "
            @"All tabs must declare a component or none — falling back to native rendering for "
            @"all tabs.",
            (unsigned long)withComponent, (unsigned long)_tabs.count);
    }
    return _tabs.count > 0 && withComponent == _tabs.count;
}

// One `UITab` per tab, its identifier the tab's id (a new UUID without one); the first `role: 'search'` is the system's
// search tab (`bottomTab.role`), a later one an ordinary tab with the warning. With the custom row every role is an
// ordinary tab.
- (NSArray *)makeUITabs API_AVAILABLE(ios(18.0)) {
    NSMutableArray<UITab *> *uiTabs = [NSMutableArray new];
    BOOL hasSearch = NO;
    for (UIViewController *tab in _tabs) {
        NSString *identifier = tab.layoutInfo.componentId;
        if (identifier.length == 0) {
            identifier = NSUUID.UUID.UUIDString;
        }
        UIViewController * (^provider)(UITab *) = ^UIViewController *(UITab *uiTab) {
          return tab;
        };
        NSString *role =
            _usesCustomRow ? nil : [[tab resolveOptionsWithDefault].bottomTab.role withDefault:nil];
        UITab *uiTab = nil;
        if ([role isEqualToString:@"search"]) {
            if (!hasSearch) {
                hasSearch = YES;
                uiTab = [[UISearchTab alloc] initWithViewControllerProvider:provider];
            } else {
                RCTLogWarn(
                    @"[ApexNavigation] Only one tab per bottomTabs layout may use role:'search'. "
                    @"Subsequent 'search' roles are ignored — creating a normal tab instead.");
            }
        }
        if (!uiTab) {
            uiTab = [[UITab alloc] initWithTitle:@""
                                           image:nil
                                      identifier:identifier
                          viewControllerProvider:provider];
        }
        [uiTabs addObject:uiTab];
    }
    return uiTabs;
}

// UIKit's More navigation shows the tabs past the fifth in compact width: the tabs hear it through its delegate, which
// keeps the one UIKit had.
- (void)setUpMoreNavigation {
    if (_tabs.count <= 5) {
        return;
    }
    UINavigationController *more = self.moreNavigationController;
    if (more.delegate == _moreDelegate && _moreDelegate) {
        return;
    }
    _moreListAppearance = [more.navigationBar.standardAppearance copy];
    id<UINavigationControllerDelegate> previous = more.delegate;
    if ([previous isKindOfClass:ANNMoreNavigationDelegate.class]) {
        previous = ((ANNMoreNavigationDelegate *)previous).previous;
    }
    _moreDelegate = [[ANNMoreNavigationDelegate alloc] initWithTabs:self previous:previous];
    more.delegate = _moreDelegate;
}

- (void)applyFirstVisibility {
    UIViewController *screen = [self screenOfTabAtIndex:_selectedTabIndex];
    ANNNavigationOptions *options =
        screen ? [screen resolveOptionsWithDefault] : [self resolveOptionsWithDefault];
    BOOL hidden = ![options.bottomTabs.visible withDefault:YES];
    if (hidden) {
        [self setBarHidden:YES animated:NO];
    }
    _visibilityApplied = YES;
}

#pragma mark - Places

+ (ANNBottomTabsController *)tabsOf:(UIViewController *)node {
    for (UIViewController *parent = node.layoutParent; parent; parent = parent.layoutParent) {
        if ([parent isKindOfClass:ANNBottomTabsController.class]) {
            return (ANNBottomTabsController *)parent;
        }
    }
    return nil;
}

- (NSArray<UIViewController *> *)layoutTabs {
    return _tabs;
}

- (ANNBottomTabsPresenter *)tabsPresenter {
    ANNNodePresenter *presenter = self.presenter;
    return [presenter isKindOfClass:ANNBottomTabsPresenter.class]
               ? (ANNBottomTabsPresenter *)presenter
               : nil;
}

- (id)uiTabAtIndex:(NSUInteger)index {
    return index < [_uiTabs count] ? _uiTabs[index] : nil;
}

- (NSUInteger)indexOfTab:(UIViewController *)tab {
    return tab ? [_tabs indexOfObjectIdenticalTo:tab] : NSNotFound;
}

- (NSUInteger)indexOfTabHolding:(UIViewController *)node {
    for (UIViewController *child = node; child; child = child.layoutParent) {
        if (child.layoutParent == self) {
            return [self indexOfTab:child];
        }
    }
    return NSNotFound;
}

// A tab with the id, or one that holds a layout with it at any depth. Only a node known by its id counts: the parts of
// a side menu carry the ids of their places, which select nothing.
- (NSUInteger)indexOfTabWithLayoutId:(NSString *)layoutId {
    for (NSUInteger index = 0; index < _tabs.count; index++) {
        if ([self node:_tabs[index] holdsLayoutId:layoutId]) {
            return index;
        }
    }
    return NSNotFound;
}

- (BOOL)node:(UIViewController *)node holdsLayoutId:(NSString *)layoutId {
    if ([node.layoutInfo.componentId isEqualToString:layoutId] &&
        [ANNNodeRegistry nodeForComponentId:layoutId] == node) {
        return YES;
    }
    for (UIViewController *child in node.layoutChildren) {
        if ([self node:child holdsLayoutId:layoutId]) {
            return YES;
        }
    }
    return NO;
}

- (UIViewController *)screenOfTabAtIndex:(NSUInteger)index {
    if (index >= _tabs.count) {
        return nil;
    }
    UIViewController *screen = _tabs[index].presentedComponentViewController;
    return [screen isKindOfClass:ANNComponentViewController.class] ? screen : nil;
}

- (UIViewController *)selectedTabNode {
    return _selectedTabIndex < _tabs.count ? _tabs[_selectedTabIndex] : nil;
}

#pragma mark - ANNLayoutProtocol

- (UIViewController *)getCurrentChild {
    return [self selectedTabNode];
}

- (NSArray<UIViewController *> *)layoutChildren {
    return _tabs ?: @[];
}

- (UIViewController *)systemUIChild {
    return [self selectedTabNode];
}

#pragma mark - The system UI

// The child the status bar and the orientations follow: the selected tab. While UIKit's More navigation is selected,
// the screen on its top when it is one of a tab open from More (in the window's hierarchy, unlike the tab's stack
// node), and nothing while the More list shows: the tabs answer then. UIKit passes the home indicator and the deferred
// edges to the selected view controller itself.
- (nullable UIViewController *)systemUIForwardChild {
    if (_tabs.count > 5 && self.selectedViewController == self.moreNavigationController) {
        UIViewController *top = self.moreNavigationController.topViewController;
        return top.isLayoutNode ? top : nil;
    }
    return [self selectedTabNode];
}

- (UIViewController *)childViewControllerForStatusBarStyle {
    return [self systemUIForwardChild];
}

- (UIViewController *)childViewControllerForStatusBarHidden {
    return [self systemUIForwardChild];
}

// The More list: the tabs' own options over their parents' and the defaults.
- (UIStatusBarStyle)preferredStatusBarStyle {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super preferredStatusBarStyle];
    }
    return [presenter statusBarStyleForOptions:[ANNSystemUI ownOptionsWithDefaultOf:self]];
}

- (BOOL)prefersStatusBarHidden {
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super prefersStatusBarHidden];
    }
    return [presenter statusBarHiddenForOptions:[ANNSystemUI ownOptionsWithDefaultOf:self]
                            navigationBarHidden:NO];
}

// UIKit's tab bar controller asks every tab (F3): the tabs answer what the child they follow answers.
- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    UIViewController *child = [self systemUIForwardChild];
    if (child) {
        return child.supportedInterfaceOrientations;
    }
    ANNNodePresenter *presenter = self.presenter;
    if (!presenter) {
        return [super supportedInterfaceOrientations];
    }
    return [presenter supportedOrientationsForOptions:[ANNSystemUI ownOptionsWithDefaultOf:self]];
}

// The selected tab's waited screens by the command's rule; with `together` the top screen of every other tab too, by
// the first mount of its root (More's tabs too).
- (NSArray<UIViewController *> *)waitedComponentViewControllers {
    NSMutableArray<UIViewController *> *screens =
        [[[self selectedTabNode] waitedComponentViewControllers] mutableCopy]
            ?: [NSMutableArray new];
    if (_attachMode != ANNTabsAttachModeTogether) {
        return screens;
    }
    for (NSUInteger index = 0; index < _tabs.count; index++) {
        UIViewController *screen =
            index == _selectedTabIndex ? nil : [self screenOfTabAtIndex:index];
        if (screen && [screens indexOfObjectIdenticalTo:screen] == NSNotFound) {
            [screens addObject:screen];
        }
    }
    return screens;
}

- (ANNWaitedReadiness)waitedReadinessOfScreen:(UIViewController *)screen {
    NSUInteger index = [self indexOfTabHolding:screen];
    return index != NSNotFound && index != _selectedTabIndex ? ANNWaitedReadinessRootMounted
                                                             : ANNWaitedReadinessContent;
}

// The selected tab renders in place; the others by the attach mode. Nothing is selected or loaded one by one: the
// screens of a tab render without appearing.
- (void)render {
    if (self.nodeHelper.destroyed) {
        return;
    }
    [self renderTabAtIndex:_selectedTabIndex preloading:NO];
    switch (_attachMode) {
        case ANNTabsAttachModeTogether:
            for (NSUInteger index = 0; index < _tabs.count; index++) {
                if (index != _selectedTabIndex) {
                    [self renderTabAtIndex:index preloading:YES];
                }
            }
            break;
        case ANNTabsAttachModeAfterInitialTab:
            [self startChainWhenSelectedTabIsReady];
            break;
        case ANNTabsAttachModeOnSwitchToTab:
            break;
    }
}

// The tabs' own merge: the own options (without the one-shot ones), the choice from code, the bar's part, then the
// parent without `bottomTabs`.
- (void)mergeOptions:(ANNNavigationOptions *)options {
    [self.nodeHelper mergeOwnOptions:options];
    [self chooseTabFromCode:options.bottomTabs];
    [self.presenter mergeOptions:options resolvedOptions:[self resolveOptionsWithDefault]];
    [self.layoutParent mergeChildOptions:[self optionsForParent:options] child:self];
}

// A tab or a screen in it merged: its item, the choice from code, the bar when the tab is the selected one, the
// accessory from any tab at once; then the parent without `bottomTabs`.
- (void)mergeChildOptions:(ANNNavigationOptions *)options child:(UIViewController *)child {
    NSUInteger index = [self indexOfTab:child];
    if (index != NSNotFound && !self.nodeHelper.destroyed) {
        ANNBottomTabsPresenter *presenter = [self tabsPresenter];
        if (options.bottomTab.hasValue) {
            [presenter mergeBottomTab:options.bottomTab ofTabAtIndex:index];
        }
        [self chooseTabFromCode:options.bottomTabs];
        if (options.bottomTabs.hasValue) {
            [presenter mergeChildBar:options.bottomTabs
                     resolvedOptions:[self resolveOptionsWithDefault]
                     fromSelectedTab:index == _selectedTabIndex];
        }
        // The tabs' view takes the selected tab's `layout.backgroundColor` over their own.
        if (options.layout.backgroundColor.hasValue && index == _selectedTabIndex) {
            [presenter applyBackground:[self resolveOptionsWithDefault]];
        }
    }
    [self.layoutParent mergeChildOptions:[self optionsForParent:options] child:self];
}

- (ANNNavigationOptions *)optionsForParent:(ANNNavigationOptions *)options {
    if (!options.bottomTabs.hasValue) {
        return options;
    }
    ANNNavigationOptions *up = [options copy];
    up.bottomTabs = [ANNBottomTabsOptions new];
    return up;
}

// A press or pick that leaves the tab sends `Selected` first; its `popToRootOnBlur` comes right after it.
- (void)childDidDisappear:(UIViewController *)child {
    NSUInteger index = [self indexOfTab:child];
    if (index == NSNotFound) {
        return;
    }
    BOOL selectedPending = (_hasPendingSelected && _pendingSelectedPrevious == index) ||
                           (_pressedIndex != NSNotFound && _pressedPrevious == index) ||
                           (_switchActive && _switchSendsSelected && _switchFrom == index);
    if (!selectedPending) {
        [self popToRootOnBlurOfTabAtIndex:index];
    }
}

// Once: the tabs' screens go, then every root the tabs own stops, on the main queue; what waits for a selection goes
// silently.
- (void)destroy {
    if (![self.nodeHelper markDestroyed]) {
        return;
    }
    _hasPendingSelected = NO;
    _decideAtMoreDidShow = nil;
    _chainNumber++;
    if (_switchActive) {
        _switchActive = NO;
        [ANNAppearanceDispatcher stopDrivingAppearanceOf:@[ _tabs[_switchFrom], _tabs[_switchTo] ]];
    }
    [_longPress detach];
    [self.nodeHelper unregisterNode];
    for (UIViewController *tab in _tabs) {
        @try {
            [tab destroy];
        } @catch (NSException *exception) {
            RCTLogWarn(@"[ApexNavigation] could not destroy the tab '%@': %@",
                       tab.layoutInfo.componentId ?: @"", exception.reason ?: exception.name);
        }
    }
    [self closePreloadWindow];
    [[self tabsPresenter] tabsWillGo];
    [_components stopAll];
}

#pragma mark - The view

- (void)viewDidLoad {
    [super viewDidLoad];
    if (!_longPress) {
        _longPress = [[ANNTabLongPress alloc] initWithTabs:self];
    }
    [_longPress attach];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    if (!_barLaidOutInWindow && self.tabBar.window) {
        _barLaidOutInWindow = YES;
        [[self tabsPresenter] barDidLayOutInWindow];
    }
    [[self tabsPresenter] tabsDidLayOut];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [[self tabsPresenter] tabsDidLayOut];
}

// `afterInitialTab` goes on at the tabs' first appearance at the latest.
- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [self scheduleChain];
}

#pragma mark - Rendering and the preload window

- (void)renderTabAtIndex:(NSUInteger)index preloading:(BOOL)preloading {
    if (index >= _tabs.count) {
        return;
    }
    UIViewController *tab = _tabs[index];
    if ([_renderedTabs containsObject:tab]) {
        return;
    }
    [_renderedTabs addObject:tab];
    [tab render];
    if (preloading) {
        [self lendTopOfTabAtIndex:index];
    }
}

// The React view of the tab's top screen mounts in the preload window and comes back into its screen at its mount or
// when its tab first shows, whichever comes first; a native screen stays where it is.
- (void)lendTopOfTabAtIndex:(NSUInteger)index {
    UIViewController *node = [self screenOfTabAtIndex:index];
    if (![node isKindOfClass:ANNComponentViewController.class]) {
        return;
    }
    ANNComponentViewController *screen = (ANNComponentViewController *)node;
    UIView<ANNComponentProtocol> *content = screen.reactView;
    if (![content respondsToSelector:@selector(whenMounted:)] || content.hasMounted) {
        return;
    }
    if (!_preloadWindow) {
        _preloadWindow = [ANNTabsPreloadWindow preloadWindow];
    }
    if (![screen lendContentTo:_preloadWindow]) {
        return;
    }
    [_lentScreens addObject:screen];
    __weak ANNBottomTabsController *weakSelf = self;
    __weak ANNComponentViewController *weakScreen = screen;
    [content whenMounted:^{
      [weakSelf takeBack:weakScreen];
    }];
    // A root that never mounts goes back anyway: it renders where its tab shows.
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(ANNTabMountLimit * 2 * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     [weakSelf takeBack:weakScreen];
                   });
}

- (void)takeBack:(ANNComponentViewController *)screen {
    if (screen) {
        [screen takeContentBack];
        [_lentScreens removeObject:screen];
    }
    for (ANNComponentViewController *lent in _lentScreens.allObjects) {
        if (lent.contentIsLent) {
            return;
        }
    }
    [self closePreloadWindow];
}

- (void)closePreloadWindow {
    for (ANNComponentViewController *screen in _lentScreens.allObjects) {
        [screen takeContentBack];
    }
    [_lentScreens removeAllObjects];
    [_preloadWindow close];
    _preloadWindow = nil;
}

// `afterInitialTab`: the selected tab's shown screens ready (by their content), then the others one by one.
- (void)startChainWhenSelectedTabIsReady {
    NSHashTable<ANNComponentViewController *> *waited = [NSHashTable weakObjectsHashTable];
    for (UIViewController *node in [self selectedTabNode].shownComponentViewControllers) {
        if ([node isKindOfClass:ANNComponentViewController.class]) {
            ANNComponentViewController *screen = (ANNComponentViewController *)node;
            if (screen.reactView && !screen.reactView.isReady) {
                [waited addObject:screen];
            }
        }
    }
    if (waited.count == 0) {
        [self scheduleChain];
        return;
    }
    __weak ANNBottomTabsController *weakSelf = self;
    __weak NSHashTable<ANNComponentViewController *> *weakWaited = waited;
    for (ANNComponentViewController *screen in waited.allObjects) {
        __weak ANNComponentViewController *weakScreen = screen;
        [screen
            setReadyHandler:^{
              ANNBottomTabsController *tabs = weakSelf;
              for (ANNComponentViewController *other in weakWaited.allObjects) {
                  if (other.reactView && !other.reactView.isReady && other != weakScreen) {
                      return;
                  }
              }
              [tabs scheduleChain];
            }
                     forKey:ANNTabsReadyKey];
    }
    // The table lives as long as the tabs wait: the handlers keep it weakly.
    _chainWaited = waited;
}

- (void)scheduleChain {
    if (_attachMode != ANNTabsAttachModeAfterInitialTab || _chainStarted ||
        self.nodeHelper.destroyed) {
        return;
    }
    _chainStarted = YES;
    NSUInteger number = _chainNumber;
    __weak ANNBottomTabsController *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      [weakSelf chainStepOfNumber:number];
    });
}

// One tab per main-queue step, in layout order; a tab chosen earlier rendered in place already and is skipped; each
// step ends at its root's mount or at the limit, so one tab that never mounts stops nothing.
- (void)chainStepOfNumber:(NSUInteger)number {
    if (number != _chainNumber || self.nodeHelper.destroyed) {
        return;
    }
    NSUInteger next = NSNotFound;
    for (NSUInteger index = 0; index < _tabs.count; index++) {
        if (![_renderedTabs containsObject:_tabs[index]]) {
            next = index;
            break;
        }
    }
    if (next == NSNotFound) {
        return;
    }
    [self renderTabAtIndex:next preloading:YES];
    __block BOOL stepped = NO;
    __weak ANNBottomTabsController *weakSelf = self;
    dispatch_block_t step = ^{
      if (stepped) {
          return;
      }
      stepped = YES;
      dispatch_async(dispatch_get_main_queue(), ^{
        [weakSelf chainStepOfNumber:number];
      });
    };
    UIViewController *screen = [self screenOfTabAtIndex:next];
    UIView<ANNComponentProtocol> *content = [screen isKindOfClass:ANNComponentViewController.class]
                                                ? ((ANNComponentViewController *)screen).reactView
                                                : nil;
    if ([content respondsToSelector:@selector(whenMounted:)] && !content.hasMounted) {
        [content whenMounted:step];
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(ANNTabMountLimit * NSEC_PER_SEC)),
                       dispatch_get_main_queue(), step);
        return;
    }
    step();
}

// A tab about to show: made (`onSwitchToTab`, a tab the chain didn't reach), and repainted when a repaint left it.
- (void)prepareToShowTabAtIndex:(NSUInteger)index {
    if (index >= _tabs.count) {
        return;
    }
    [self renderTabAtIndex:index preloading:NO];
    UIViewController *tab = _tabs[index];
    if ([_staleTabs containsObject:tab]) {
        [_staleTabs removeObject:tab];
        [ANNBottomTabsPresenter repaintNode:tab];
    }
}

#pragma mark - The repaint

/// The repaint passed over the tabs not in a window: they repaint at their next selection.
- (void)markUnshownTabsStale {
    for (NSUInteger index = 0; index < _tabs.count; index++) {
        UIViewController *tab = _tabs[index];
        if (index != _selectedTabIndex && !tab.viewIfLoaded.window) {
            [_staleTabs addObject:tab];
        }
    }
}

#pragma mark - UIKit's selection

- (void)selectInUIKit:(NSUInteger)index {
    if (index >= _tabs.count) {
        return;
    }
    _selectingFromCode = YES;
    @try {
        // With More, UIKit's `selectedTab` goes stale after a pick from the list, and setting the stale one again does
        // nothing; `selectedIndex` stays right.
        if (@available(iOS 18.0, *)) {
            if (_usesTabAPI && _tabs.count <= 5) {
                self.selectedTab = _uiTabs[index];
                return;
            }
        }
        self.selectedIndex = index;
    } @finally {
        _selectingFromCode = NO;
    }
}

#pragma mark - Presses

- (BOOL)pressTabAtIndex:(NSUInteger)index source:(ANNTabPressSource)source {
    ANNAssertMainQueue();
    if (index >= _tabs.count || self.nodeHelper.destroyed) {
        return NO;
    }
    [self finishEarlierSelection];
    _pressedIndex = NSNotFound;
    // The release of a long press: UIKit would select the tab under the finger.
    if (source == ANNTabPressSourceBar && index == _longPressedIndex) {
        return NO;
    }
    BOOL wasSelected = index == _selectedTabIndex;
    ANNBottomTabOptions *bottomTab = [_tabs[index] resolveOptionsWithDefault].bottomTab;
    [self sendPressedOfTabAtIndex:index wasSelected:wasSelected];
    // The button tab: only the event; the row's highlight stays.
    if (![bottomTab.selectTabOnPress withDefault:YES]) {
        return NO;
    }
    if (wasSelected) {
        return [self repeatPressOfTabAtIndex:index source:source options:bottomTab];
    }
    NSUInteger previous = _selectedTabIndex;
    [self prepareToShowTabAtIndex:index];
    self.selectedTabIndex = index;
    [[self tabsPresenter] selectedTabChanged];
    // The switch's appearances go through the dispatcher in the order of a transition; `Selected` and the left tab's
    // `popToRootOnBlur` after them. Where the tabs can't drive it, UIKit's did-select finishes the press.
    if (![self beginSwitchFrom:previous to:index sendsSelected:YES]) {
        _pressedIndex = index;
        _pressedPrevious = previous;
    }
    // A modal's hold follows the screen it shows now.
    [ANNShownScreens changedIn:self];
    if (source == ANNTabPressSourceRow) {
        [self selectInUIKit:index];
        [self finishPress];
        return NO;
    }
    return YES;
}

// The repeat press: a stack deeper than its first screen goes back to it — UIKit pops it for the system's sources (the
// hold of the screen on top applies), the core for a row's cell —, else the screen scrolls to its top.
// `popToRootOnPress: false`: nothing but the event.
- (BOOL)repeatPressOfTabAtIndex:(NSUInteger)index
                         source:(ANNTabPressSource)source
                        options:(ANNBottomTabOptions *)bottomTab {
    if (![bottomTab.popToRootOnPress withDefault:YES]) {
        return NO;
    }
    UIViewController *tab = _tabs[index];
    if ([tab isKindOfClass:ANNStackController.class] &&
        ((ANNStackController *)tab).queue.entries.count >= 2) {
        if (source == ANNTabPressSourceBar && ANNUIKitPopsOnRepeatPress) {
            return YES;
        }
        [(ANNStackController *)tab userPopToRoot];
        return NO;
    }
    UIViewController *screen = [self screenOfTabAtIndex:index];
    UINavigationController *navigation = [tab isKindOfClass:ANNStackController.class]
                                             ? ((ANNStackController *)tab).shownNavigationController
                                             : nil;
    UINavigationBar *bar = navigation.navigationBarHidden ? nil : navigation.navigationBar;
    BOOL largeTitle = screen && ANNLargeTitleCanShow([screen resolveOptionsWithDefault]);
    [ANNScrollToTop scrollToTopIn:screen.viewIfLoaded navigationBar:bar largeTitleShows:largeTitle];
    return NO;
}

// UIKit selected the pressed tab: `ANN.BottomTabSelected` goes after its screen appeared.
- (void)finishPress {
    if (_pressedIndex == NSNotFound) {
        return;
    }
    NSUInteger index = _pressedIndex;
    NSUInteger previous = _pressedPrevious;
    _pressedIndex = NSNotFound;
    [self scheduleSelectedOfTabAtIndex:index previous:previous];
}

- (void)sendPressedOfTabAtIndex:(NSUInteger)index wasSelected:(BOOL)wasSelected {
    NSString *tabsId = self.layoutInfo.componentId ?: @"";
    NSString *screenId = [self screenOfTabAtIndex:index].layoutInfo.componentId;
    [self.eventEmitter sendBottomTabPressed:index
                               bottomTabsId:tabsId
                                   screenId:screenId
                                wasSelected:wasSelected];
}

// `ANN.BottomTabSelected` right after the new tab's screen's `DidAppear`, in the same turn; at the end of the switch
// when no appearance is pending; before the first event of a later press or choice from code; dropped only with the
// tabs.
- (void)scheduleSelectedOfTabAtIndex:(NSUInteger)index previous:(NSUInteger)previous {
    [self scheduleSelectedOfTabAtIndex:index previous:previous moreNavigation:nil];
}

// A More pick: the More navigation's transition decides, or its `didShow` without one.
- (void)scheduleSelectedOfTabAtIndex:(NSUInteger)index
                            previous:(NSUInteger)previous
                      moreNavigation:(UINavigationController *)moreNavigation {
    _hasPendingSelected = YES;
    _pendingSelectedIndex = index;
    _pendingSelectedPrevious = previous;
    NSUInteger number = ++_pendingSelectedNumber;
    __weak ANNBottomTabsController *weakSelf = self;
    dispatch_block_t send = ^{
      [weakSelf sendSelectedOfNumber:number];
    };
    UIViewController *screen = [self screenOfTabAtIndex:index];
    ANNScreenAppearance *appearance = ((ANNComponentViewController *)screen).appearance;
    if (appearance && appearance.phase != ANNAppearancePhaseHidden) {
        [ANNAppearanceDispatcher afterDidAppearOf:appearance run:send];
        return;
    }
    // UIKit may report the new screen's appearance only as its switch goes on (iOS 26): the end of the switch decides,
    // or the next turn without a transition; at once there when nothing is pending.
    __weak UIViewController *weakScreen = screen;
    void (^decide)(void) = ^{
      ANNScreenAppearance *later = ((ANNComponentViewController *)weakScreen).appearance;
      [ANNAppearanceDispatcher afterDidAppearOf:later run:send];
    };
    id<UIViewControllerTransitionCoordinator> coordinator =
        moreNavigation ? moreNavigation.transitionCoordinator : self.transitionCoordinator;
    if (coordinator &&
        [coordinator
            animateAlongsideTransition:nil
                            completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
                              decide();
                            }]) {
        return;
    }
    if (moreNavigation) {
        _decideAtMoreDidShow = decide;
        return;
    }
    dispatch_async(dispatch_get_main_queue(), decide);
}

- (void)sendSelectedOfNumber:(NSUInteger)number {
    if (!_hasPendingSelected || number != _pendingSelectedNumber) {
        return;
    }
    [self sendPendingSelected];
}

// Before anything newer on these tabs (a press, a long press, a More pick, a choice from code): a switch still running
// ends with its appearances and schedules its `Selected`, and a `Selected` still waiting goes, as Android's
// `flushPendingSelected` does.
- (void)finishEarlierSelection {
    [self endSwitch];
    [self sendPendingSelected];
}

- (void)sendPendingSelected {
    if (!_hasPendingSelected) {
        return;
    }
    _hasPendingSelected = NO;
    if (self.nodeHelper.destroyed) {
        return;
    }
    NSUInteger previous = _pendingSelectedPrevious;
    [self.eventEmitter sendBottomTabSelected:_pendingSelectedIndex
                          unselectedTabIndex:previous
                                bottomTabsId:self.layoutInfo.componentId ?: @""];
    // The left tab's `popToRootOnBlur` after `Selected`, then the coalesced state.
    [self popToRootOnBlurOfTabAtIndex:previous];
    if (self.eventEmitter.isCurrent) {
        [ANNNavigationState setNeedsUpdate];
    }
}

- (void)longPressTabAtIndex:(NSUInteger)index marksSelection:(BOOL)marksSelection {
    if (index >= _tabs.count || self.nodeHelper.destroyed) {
        return;
    }
    // A running switch and a pending `Selected` go before the event of a later press.
    [self finishEarlierSelection];
    if (marksSelection) {
        _longPressedIndex = index;
    }
    [self.eventEmitter sendBottomTabLongPressed:index
                                   bottomTabsId:self.layoutInfo.componentId ?: @""];
}

- (void)longPressEnded {
    __weak ANNBottomTabsController *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      ANNBottomTabsController *tabs = weakSelf;
      if (tabs) {
          tabs->_longPressedIndex = NSNotFound;
      }
    });
}

#pragma mark - The choice from code

// `currentTabId` (a tab or any layout in it) over `currentTabIndex`; an unknown id does nothing, an index out of range
// warns; the one-shot fields come from the merge itself, never from resolved options or the defaults.
- (void)chooseTabFromCode:(ANNBottomTabsOptions *)bottomTabs {
    if (!bottomTabs.currentTabId.hasValue && !bottomTabs.currentTabIndex.hasValue) {
        return;
    }
    NSUInteger index = NSNotFound;
    if (bottomTabs.currentTabId.hasValue) {
        NSString *tabId = [bottomTabs.currentTabId withDefault:nil];
        index = tabId.length > 0 ? [self indexOfTabWithLayoutId:tabId] : NSNotFound;
        if (index == NSNotFound) {
            return;
        }
    } else if (!ANNTabIndexOf(bottomTabs.currentTabIndex, _tabs.count, &index)) {
        return;
    }
    [self selectTabFromCode:index];
}

// No press events; the screens appear and disappear as on a press; the left tab's `popToRootOnBlur`; the core's index
// first, then UIKit's, then the state at once.
- (void)selectTabFromCode:(NSUInteger)index {
    ANNAssertMainQueue();
    [self finishEarlierSelection];
    _pressedIndex = NSNotFound;
    if (index == _selectedTabIndex || index >= _tabs.count || self.nodeHelper.destroyed) {
        return;
    }
    NSUInteger previous = _selectedTabIndex;
    [self prepareToShowTabAtIndex:index];
    self.selectedTabIndex = index;
    _codeChoiceIndex = index;
    __weak ANNBottomTabsController *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      ANNBottomTabsController *tabs = weakSelf;
      if (tabs && tabs->_codeChoiceIndex == index) {
          tabs->_codeChoiceIndex = NSNotFound;
      }
    });
    [[self tabsPresenter] selectedTabChanged];
    BOOL driven = [self beginSwitchFrom:previous to:index sendsSelected:NO];
    [self selectInUIKit:index];
    // Not driven (the tabs aren't on screen): the left tab is no longer seen, its `popToRootOnBlur` now.
    if (!driven) {
        [self popToRootOnBlurOfTabAtIndex:previous];
    }
    // A root of tabs that waits to show takes its waited screens again.
    [ANNShownScreens changedIn:self];
    [ANNNavigationState updateNow];
}

#pragma mark - The switch (the same order as a stack's transition)

// The tabs report the switch's appearances themselves: `WillAppear` of the new tab now, then at the end `DidDisappear`
// of the old one and `DidAppear` of the new one, then the switch's own events. UIKit's callbacks of both tabs say
// nothing meanwhile. Only on screen and when no command drives the tabs already (a root that waits). NO: not driven.
- (BOOL)beginSwitchFrom:(NSUInteger)from to:(NSUInteger)to sendsSelected:(BOOL)sendsSelected {
    [self endSwitch];
    BOOL drivenElsewhere = [ANNAppearanceDispatcher isAppearanceDriven:self] ||
                           [ANNPendingLayers isWaitingToShow:self];
    if (from >= _tabs.count || to >= _tabs.count || from == to || !self.viewIfLoaded.window ||
        drivenElsewhere) {
        return NO;
    }
    _switchActive = YES;
    _switchFrom = from;
    _switchTo = to;
    _switchSendsSelected = sendsSelected;
    NSUInteger number = ++_switchNumber;
    UIViewController *newTab = _tabs[to];
    [ANNAppearanceDispatcher driveAppearanceOf:@[ _tabs[from], newTab ]];
    [ANNAppearanceDispatcher beginTransitionShowing:@[ newTab ]];
    __weak ANNBottomTabsController *weakSelf = self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(ANNSwitchDriveLimit * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     ANNBottomTabsController *tabs = weakSelf;
                     if (tabs && tabs->_switchActive && tabs->_switchNumber == number) {
                         [tabs endSwitch];
                     }
                   });
    return YES;
}

// A screen of the tab a driven switch shows appeared in UIKit: the switch ends (its appearances, then its events). A
// screen of another tabs node's switch, or of no switch, ends nothing here.
- (void)drivenDescendantDidAppear:(UIViewController *)screen {
    if (_switchActive && [self indexOfTabHolding:screen] == _switchTo) {
        [self endSwitch];
    }
}

- (void)endSwitch {
    if (!_switchActive) {
        return;
    }
    _switchActive = NO;
    NSUInteger from = _switchFrom;
    NSUInteger to = _switchTo;
    BOOL sendsSelected = _switchSendsSelected;
    UIViewController *oldTab = _tabs[from];
    UIViewController *newTab = _tabs[to];
    [ANNAppearanceDispatcher stopDrivingAppearanceOf:@[ oldTab, newTab ]];
    if (self.nodeHelper.destroyed) {
        return;
    }
    __weak ANNBottomTabsController *weakSelf = self;
    [ANNAppearanceDispatcher endSwitchHiding:@[ oldTab ]
                                     showing:@[ newTab ]
                                      events:^{
                                        ANNBottomTabsController *tabs = weakSelf;
                                        // Every stack of the old tab: what waits for its screen runs now.
                                        NSMutableArray<ANNStackController *> *stacks = [NSMutableArray new];
                                        ANNStacksOfNode(oldTab, stacks);
                                        for (ANNStackController *stack in stacks) {
                                            [stack stackDidLeaveScreen];
                                        }
                                        if (sendsSelected) {
                                            [tabs scheduleSelectedOfTabAtIndex:to previous:from];
                                        } else {
                                            [tabs popToRootOnBlurOfTabAtIndex:from];
                                        }
                                      }];
}

#pragma mark - Leaving a tab

// When the tab is no longer selected and its stack disappeared (or its screens are in the More navigation): its stack
// goes back to its first screen without animation and without appearance events.
- (void)popToRootOnBlurOfTabAtIndex:(NSUInteger)index {
    if (index >= _tabs.count || index == _selectedTabIndex || self.nodeHelper.destroyed) {
        return;
    }
    UIViewController *tab = _tabs[index];
    if (![tab isKindOfClass:ANNStackController.class]) {
        return;
    }
    ANNStackController *stack = (ANNStackController *)tab;
    BOOL inMore = stack.shownNavigationController != stack;
    if (!inMore && stack.viewIfLoaded.window) {
        return;
    }
    if (![[tab resolveOptionsWithDefault].bottomTab.popToRootOnBlur withDefault:NO]) {
        return;
    }
    [stack popToRootSilently];
}

#pragma mark - UITabBarControllerDelegate

// iOS 18 and later: a user's selection (the bar, the iPad's top bar, the sidebar) runs the press path; a choice from
// code passes.
- (BOOL)tabBarController:(UITabBarController *)tabBarController
         shouldSelectTab:(UITab *)tab API_AVAILABLE(ios(18.0)) {
    if (_selectingFromCode) {
        return YES;
    }
    NSUInteger index = [_uiTabs indexOfObjectIdenticalTo:tab];
    if (index == NSNotFound) {
        return YES;
    }
    return [self pressTabAtIndex:index source:ANNTabPressSourceBar];
}

// Only a press recorded in should-select sends `ANN.BottomTabSelected`: the initial selection, a choice from code and a
// repeat press send nothing; the indexes are the core's.
- (void)tabBarController:(UITabBarController *)tabBarController
            didSelectTab:(UITab *)selectedTab
             previousTab:(UITab *)previousTab API_AVAILABLE(ios(18.0)) {
    [self finishPress];
}

// iOS 15–17 (with the tab API UIKit calls these too and they do nothing). More's own container is UIKit's: its tap
// sends nothing.
- (BOOL)tabBarController:(UITabBarController *)tabBarController
    shouldSelectViewController:(UIViewController *)viewController {
    if (_usesTabAPI || _selectingFromCode) {
        return YES;
    }
    if (viewController == self.moreNavigationController) {
        return YES;
    }
    NSUInteger index = [self indexOfTab:viewController];
    if (index == NSNotFound) {
        return YES;
    }
    return [self pressTabAtIndex:index source:ANNTabPressSourceBar];
}

- (void)tabBarController:(UITabBarController *)tabBarController
    didSelectViewController:(UIViewController *)viewController {
    if (_usesTabAPI) {
        return;
    }
    [self finishPress];
}

#pragma mark - More

// The More navigation is about to show a screen: the More list (nothing), a screen of tab T just pushed over the list
// (a pick: `Pressed`, then the selection and `Selected`), the same top shown again by the More tap (a
// re-show: only `Selected`), a deeper screen (the stack's own). A choice from code of T sends nothing.
- (void)moreNavigation:(UINavigationController *)moreNavigation
    willShowViewController:(UIViewController *)viewController {
    if (self.nodeHelper.destroyed) {
        return;
    }
    if (!viewController.isLayoutNode) {
        [self moreNavigationShowsList:moreNavigation];
        return;
    }
    NSUInteger index = [self indexOfTabHolding:viewController];
    if (index == NSNotFound) {
        return;
    }
    UIViewController *tab = _tabs[index];
    if ([tab isKindOfClass:ANNStackController.class]) {
        _moreStack = (ANNStackController *)tab;
        [(ANNStackController *)tab moreNavigationWillShow:moreNavigation];
    }
    if (_codeChoiceIndex == index) {
        return;
    }
    UIViewController *shownTop = _moreShownTop;
    BOOL reshow = viewController == shownTop;
    // UIKit opens a tab from the list with its first screen, or with the screens it kept above it: any screen of T
    // after a top that wasn't T's is the pick.
    BOOL pick = !reshow && (!shownTop || [self indexOfTabHolding:shownTop] != index);
    if (!reshow && !pick) {
        return;
    }
    // A pick in the More list of the tab that is current already (the user went back to the list from it): nothing.
    if (pick && index == _selectedTabIndex) {
        return;
    }
    [self finishEarlierSelection];
    _pressedIndex = NSNotFound;
    if (pick) {
        [self sendPressedOfTabAtIndex:index wasSelected:NO];
    }
    if (index == _selectedTabIndex) {
        return;
    }
    NSUInteger previous = _selectedTabIndex;
    [self prepareToShowTabAtIndex:index];
    self.selectedTabIndex = index;
    [[self tabsPresenter] selectedTabChanged];
    [ANNShownScreens changedIn:self];
    // `Selected` after the picked screen's `DidAppear`; the left tab's `popToRootOnBlur` after it.
    [self scheduleSelectedOfTabAtIndex:index previous:previous moreNavigation:moreNavigation];
}

// The More list shows again: its bar is UIKit's own, not the shared bar of the stack that showed last (the stack sets
// its parts anew when its screens show there again).
- (void)moreNavigationShowsList:(UINavigationController *)moreNavigation {
    [_moreStack forgetSharedBar];
    _moreStack = nil;
    // The More navigation's swipes are UIKit's again: a tab that isn't a stack goes back to the list with them.
    [InteractivePopGestureDelegate
        restoreSystemDelegateOf:moreNavigation.interactivePopGestureRecognizer];
    if (@available(iOS 26.0, *)) {
        [InteractivePopGestureDelegate
            restoreSystemDelegateOf:moreNavigation.interactiveContentPopGestureRecognizer];
    }
    UINavigationBar *bar = moreNavigation.navigationBar;
    if (moreNavigation.navigationBarHidden) {
        [moreNavigation setNavigationBarHidden:NO animated:YES];
    }
    if (!moreNavigation.toolbarHidden) {
        [moreNavigation setToolbarHidden:YES animated:YES];
    }
    moreNavigation.hidesBarsOnSwipe = NO;
    moreNavigation.hidesBarsOnTap = NO;
    moreNavigation.hidesBarsWhenKeyboardAppears = NO;
    bar.barStyle = UIBarStyleDefault;
    bar.clipsToBounds = NO;
    bar.accessibilityIdentifier = nil;
    if (_moreListAppearance && ![bar.standardAppearance isEqual:_moreListAppearance]) {
        bar.standardAppearance = [_moreListAppearance copy];
    }
    [ANNBarBackgroundComponent show:nil inBar:bar coordinator:moreNavigation.transitionCoordinator];
}

- (void)moreNavigation:(UINavigationController *)moreNavigation
    didShowViewController:(UIViewController *)viewController {
    _moreShownTop = viewController;
    dispatch_block_t decide = _decideAtMoreDidShow;
    _decideAtMoreDidShow = nil;
    if (decide) {
        decide();
    }
}

#pragma mark - The bar

- (BOOL)barHidden {
    if (@available(iOS 18.0, *)) {
        return self.isTabBarHidden;
    }
    return _hiddenBeforeTabAPI;
}

- (BOOL)showsTabBarForScreen:(UIViewController *)screen {
    ANNBottomTabsPresenter *presenter = [self tabsPresenter];
    if (_usesCustomRow && presenter.customRowShown) {
        return YES;
    }
    if ([self barIsOnTopOrInSidebar]) {
        return NO;
    }
    return !self.barHidden && !self.tabBar.hidden;
}

// iPad and Mac with iOS 18 and later in a regular width: the bar is on top (or the sidebar shows), the bottom keeps
// nothing.
- (BOOL)barIsOnTopOrInSidebar {
    return [ANNTabsDesign barIsOnTopIn:self.traitCollection];
}

- (void)screenWillAppear:(UIViewController *)screen
                 options:(ANNNavigationOptions *)resolvedOptions {
    if (self.nodeHelper.destroyed || [self indexOfTabHolding:screen] != _selectedTabIndex) {
        return;
    }
    ANNBottomTabsPresenter *presenter = [self tabsPresenter];
    [presenter applyBarLook:resolvedOptions];
    [presenter applyBackground:[self resolveOptionsWithDefault]];
    BOOL hidden = ![resolvedOptions.bottomTabs.visible withDefault:YES];
    BOOL animated = [resolvedOptions.bottomTabs.animate withDefault:YES];
    if (@available(iOS 18.0, *)) {
        [self setBarHidden:hidden animated:animated];
        return;
    }
    // Before iOS 18 UIKit leads during a push or pop; the explicit setting applies when not applied yet or for a
    // screen out of a stack.
    if (!_visibilityApplied || !screen.stack) {
        [self setBarHidden:hidden animated:_visibilityApplied && animated];
        _visibilityApplied = YES;
    }
}

- (void)setBarHidden:(BOOL)hidden animated:(BOOL)animated {
    ANNBottomTabsPresenter *presenter = [self tabsPresenter];
    // With the custom row on a wide iPad the system bar stays hidden and the row keeps the visibility.
    if ([presenter customRowKeepsVisibility]) {
        [presenter setCustomRowHidden:hidden];
        [self updateRootLayoutOfShownScreens];
        return;
    }
    if (hidden == self.barHidden) {
        return;
    }
    BOOL inWindow = self.viewIfLoaded.window != nil;
    if (@available(iOS 18.0, *)) {
        [self setTabBarHidden:hidden animated:animated && inWindow];
    } else {
        [self slideBarHidden:hidden animated:animated && inWindow];
    }
    [presenter barVisibilityChanged];
    [self updateRootLayoutOfShownScreens];
}

// Before iOS 18: the bar slides by its height below the tabs' view and back, 0.15 s ease-out; hidden it is `hidden`.
- (void)slideBarHidden:(BOOL)hidden animated:(BOOL)animated {
    _hiddenBeforeTabAPI = hidden;
    UITabBar *bar = self.tabBar;
    CGAffineTransform below = CGAffineTransformMakeTranslation(0, bar.bounds.size.height + 1);
    if (!animated) {
        bar.transform = CGAffineTransformIdentity;
        bar.hidden = hidden;
        return;
    }
    if (!hidden) {
        bar.hidden = NO;
        bar.transform = below;
    }
    __weak ANNBottomTabsController *weakSelf = self;
    [UIView animateWithDuration:0.15
        delay:0
        options:UIViewAnimationOptionCurveEaseOut | UIViewAnimationOptionBeginFromCurrentState
        animations:^{
          bar.transform = hidden ? below : CGAffineTransformIdentity;
        }
        completion:^(BOOL finished) {
          ANNBottomTabsController *tabs = weakSelf;
          if (tabs && tabs->_hiddenBeforeTabAPI == hidden) {
              bar.hidden = hidden;
              bar.transform = CGAffineTransformIdentity;
          }
        }];
}

// The shown screens of the selected tab apply the "under the tab bar" rule again.
- (void)updateRootLayoutOfShownScreens {
    for (UIViewController *screen in [self selectedTabNode].shownComponentViewControllers) {
        if ([screen isKindOfClass:ANNComponentViewController.class]) {
            [(ANNComponentViewController *)screen updateRootLayout];
        }
    }
}

@end

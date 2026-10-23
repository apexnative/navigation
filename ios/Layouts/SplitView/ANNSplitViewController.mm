#import "ANNSplitViewController.h"
#import "ANNErrorHandler.h"
#import "ANNErrorTexts.h"
#import "ANNShownScreens.h"
#import "UISplitViewController+ANNOptions.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>

// Every column a split view can have, in the order it gives them to UIKit and lists them as its children.
static NSArray<NSString *> *ANNSplitViewColumnNames(void) {
    return @[ @"primary", @"supplementary", @"secondary", @"compact", @"inspector" ];
}

static UISplitViewControllerColumn ANNSplitViewColumn(NSString *name) {
    if ([name isEqualToString:@"supplementary"]) {
        return UISplitViewControllerColumnSupplementary;
    }
    if ([name isEqualToString:@"secondary"]) {
        return UISplitViewControllerColumnSecondary;
    }
    if ([name isEqualToString:@"compact"]) {
        return UISplitViewControllerColumnCompact;
    }
    if ([name isEqualToString:@"inspector"]) {
        if (@available(iOS 26.0, *)) {
            return UISplitViewControllerColumnInspector;
        }
    }
    return UISplitViewControllerColumnPrimary;
}

@implementation ANNSplitViewController {
    NSDictionary<NSString *, UIViewController *> *_columns;
    // The display mode and the inspector's state last sent to JS.
    NSString *_sentDisplayModeState;
    // Whether the columns show as one stack, as last sent to JS; nil before the first value.
    NSNumber *_sentColumnsStacked;
    BOOL _didAppear;
    BOOL _laidOutInWindow;
}

// The initializer of `ANNLayoutProtocol`: the first two children are the `primary` and `secondary` columns.
- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(NSArray<UIViewController *> *)childViewControllers {
    NSMutableDictionary *columns = [NSMutableDictionary new];
    NSArray<NSString *> *names = @[ @"primary", @"secondary" ];
    for (NSUInteger index = 0; index < MIN(childViewControllers.count, names.count); index++) {
        columns[names[index]] = childViewControllers[index];
    }
    return [self initWithLayoutInfo:layoutInfo
                            creator:creator
                            options:options
                     defaultOptions:defaultOptions
                          presenter:presenter
                       eventEmitter:eventEmitter
                            columns:columns];
}

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
                           columns:(NSDictionary<NSString *, UIViewController *> *)columns {
    // The style is given when the split view is made: three columns with a supplementary one.
    self = [super initWithStyle:columns[@"supplementary"] ? UISplitViewControllerStyleTripleColumn
                                                          : UISplitViewControllerStyleDoubleColumn];
    NSMutableDictionary *usedColumns = [columns mutableCopy];
    if (usedColumns[@"inspector"]) {
        if (@available(iOS 26.0, *)) {
        } else {
            RCTLogWarn(@"[ApexNavigation] splitView.inspector needs iPadOS 26; it is left out.");
            [usedColumns removeObjectForKey:@"inspector"];
        }
    }
    _columns = [usedColumns copy];
    self.options = options;
    self.defaultOptions = defaultOptions;
    self.layoutInfo = layoutInfo;
    self.creator = creator;
    self.eventEmitter = eventEmitter;
    self.presenter = presenter;
    for (NSString *name in ANNSplitViewColumnNames()) {
        if (_columns[name]) {
            [self setViewController:_columns[name] forColumn:ANNSplitViewColumn(name)];
        }
    }
    self.delegate = self;
    [presenter bindViewController:self];
    self.extendedLayoutIncludesOpaqueBars = YES;
    [presenter applyOptionsOnInit:self.resolveOptions];
    return self;
}

// UIKit shows an inspector when the split view first appears; `inspector.visible` applies after it.
- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    if (!_didAppear) {
        _didAppear = YES;
        [self ann_setInspectorVisible:self.resolveOptionsWithDefault.splitView.inspector.visible
                             animated:NO];
    }
}

// UIKit collapses and expands the columns as the width changes, and lays them out again then. The first layout in a
// window decides the columns UIKit shows: a layer that waits to show takes its screens again, and the system UI
// follows the current child.
- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [self sendColumnsStackedIfChanged];
    if (!_laidOutInWindow && self.viewIfLoaded.window) {
        _laidOutInWindow = YES;
        [self shownColumnsChanged];
    }
}

// What UIKit shows of the columns changed without an appearance below the split view to tell it.
- (void)shownColumnsChanged {
    [ANNShownScreens changedIn:self];
}

- (UIViewController *)viewControllerOfColumn:(NSString *)column {
    return _columns[column];
}

- (BOOL)isCollapsedIntoCompact {
    return self.isCollapsed && _columns[@"compact"] != nil;
}

// Collapsed without a compact layout, UIKit shows the columns as one stack: a column's navigation controller goes
// over the one before it in the primary's stack.
- (BOOL)columnsStacked {
    return self.isCollapsed && !self.isCollapsedIntoCompact;
}

// UIKit takes a column off the one stack by itself (the back button, the edge swipe), whatever the column holds (a
// stack, or a screen alone in a navigation controller of UIKit's own): the column under it shows, and its screen's
// appearance at the end of UIKit's pop is the one call that reaches the split view. What it shows changed then.
- (void)descendantDidAppear:(UIViewController *)screen {
    if (self.columnsStacked) {
        [ANNShownScreens changedIn:self];
    }
}

// `ANN.SplitViewColumnsStacked`: the first value once the split view is in a window, then each change. JS keeps it by
// the split view's id and takes the first value as a new start (a layout sent again with the same ids). Out of a
// window nothing goes: a split view laid out before it shows, or one that has gone, would tell what nobody sees.
- (void)sendColumnsStackedIfChanged {
    if (!self.viewIfLoaded.window) {
        return;
    }
    BOOL stacked = self.columnsStacked;
    if (_sentColumnsStacked && _sentColumnsStacked.boolValue == stacked) {
        return;
    }
    _sentColumnsStacked = @(stacked);
    [self.eventEmitter sendSplitViewColumnsStacked:self.layoutInfo.componentId stacked:stacked];
}

- (BOOL)goBackFromColumnOf:(UIViewController *)screen completion:(dispatch_block_t)completion {
    UINavigationController *stack = screen.stack;
    // Only for the column on top of the collapsed stack (the columns under it, and those never shown, are out of the
    // window), and only when its stack has nothing to pop: otherwise the pop goes as in any stack, or rejects.
    if (!stack || !self.columnsStacked || !stack.viewIfLoaded.window ||
        stack.viewControllers.count != 1) {
        return NO;
    }
    // Its one entry is the screen, or the arrangement it is a screen of.
    UIViewController *only = stack.viewControllers.firstObject;
    BOOL isOnly = NO;
    for (UIViewController *entry = screen; entry && entry != stack; entry = entry.layoutParent) {
        if (entry == only) {
            isOnly = YES;
            break;
        }
    }
    if (!isOnly) {
        return NO;
    }
    UISplitViewControllerColumn previous;
    if (stack == _columns[@"secondary"]) {
        previous = _columns[@"supplementary"] ? UISplitViewControllerColumnSupplementary
                                              : UISplitViewControllerColumnPrimary;
    } else if (stack == _columns[@"supplementary"]) {
        previous = UISplitViewControllerColumnPrimary;
    } else {
        return NO;
    }
    // A pop of the column's own stack at its root does nothing: showing the column before it takes the column's
    // navigation controller off the primary's stack. The split view shows another column then.
    __weak ANNSplitViewController *weakSelf = self;
    [self performColumnChange:^{
      [self showColumn:previous];
    }
                   completion:^{
                     ANNSplitViewController *splitView = weakSelf;
                     if (splitView) {
                         [ANNShownScreens changedIn:splitView];
                     }
                     completion();
                   }];
    return YES;
}

- (void)showScreen:(UIViewController *)screen
              inColumn:(NSString *)column
    fromViewController:(UIViewController *)fromViewController
            completion:(ANNTransitionCompletionBlock)completion
             rejection:(ANNTransitionRejectionBlock)rejection {
    UIViewController *previous = _columns[column];
    NSString *fromId = fromViewController.layoutInfo.componentId ?: @"";
    if (!previous) {
        rejection([ANNErrorTexts noSuchColumn:fromId column:column], nil);
        return;
    }
    if ([previous isKindOfClass:UINavigationController.class]) {
        // UIKit does not take a navigation controller among the screens of another. The core rejects every layout a
        // stack doesn't take before it asks; this guards the module's own call.
        if ([screen isKindOfClass:UINavigationController.class]) {
            rejection([ANNErrorTexts columnIsStack:fromId column:column], nil);
            return;
        }
        // In place, as a column of UIKit apps changes; UIKit animates a column it brings on top.
        [(UINavigationController *)previous setStackChildren:@[ screen ]
                                                    inColumn:column
                                          fromViewController:fromViewController
                                                    animated:NO
                                                  completion:^{
                                                    [self performColumnChange:^{
                                                      [self showColumn:ANNSplitViewColumn(column)];
                                                    }
                                                                   completion:completion];
                                                  }
                                                   rejection:rejection];
        return;
    }
    // A screen alone: UIKit keeps it in a navigation controller of its own and puts the new screen in its place
    // there, with the screens pushed over it gone. They go once the change is made and they have disappeared.
    NSArray<UIViewController *> *gone = previous.navigationController.viewControllers ?: @[ previous ];
    NSMutableDictionary<NSString *, UIViewController *> *columns = [_columns mutableCopy];
    columns[column] = screen;
    _columns = [columns copy];
    [self
        performColumnChange:^{
          [self setViewController:screen forColumn:ANNSplitViewColumn(column)];
          [self showColumn:ANNSplitViewColumn(column)];
        }
                 completion:^{
                   for (UIViewController *each in gone) {
                       if (each != screen) {
                           [each destroy];
                       }
                   }
                   completion();
                 }];
}

// `done` once the change and the column it brings into view are on screen. A transition UIKit runs for them ends it;
// otherwise the end of the transaction they are made in, which an animation in a column that never ends would hold:
// a second at most then.
- (void)performColumnChange:(dispatch_block_t)change completion:(dispatch_block_t)done {
    __block BOOL finished = NO;
    dispatch_block_t finish = ^{
      if (!finished) {
          finished = YES;
          done();
      }
    };
    [CATransaction begin];
    change();
    id<UIViewControllerTransitionCoordinator> coordinator = self.transitionCoordinator;
    if (coordinator) {
        [coordinator animateAlongsideTransition:nil
                                     completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
                                       finish();
                                     }];
    } else {
        [CATransaction setCompletionBlock:finish];
    }
    [CATransaction commit];
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1 * NSEC_PER_SEC)), dispatch_get_main_queue(), finish);
}

// The compact layout while the split view is collapsed into it; collapsed without one, the column UIKit put on top of
// the one stack it shows (the secondary's navigation controller in the primary's stack, else the supplementary's), else
// the primary; side by side, also before the split view is in a window, the secondary.
- (UIViewController *)getCurrentChild {
    if (self.isCollapsedIntoCompact) {
        return _columns[@"compact"];
    }
    UIViewController *primary = _columns[@"primary"];
    if (!self.isCollapsed) {
        return _columns[@"secondary"] ?: primary;
    }
    UINavigationController *primaryStack = [primary isKindOfClass:UINavigationController.class]
                                               ? (UINavigationController *)primary
                                               : primary.navigationController;
    NSArray<UIViewController *> *stacked = primaryStack.viewControllers ?: @[];
    for (NSString *name in @[ @"secondary", @"supplementary" ]) {
        UIViewController *column = _columns[name];
        if (!column) {
            continue;
        }
        // A column that is a screen alone is in a navigation controller of UIKit's own.
        BOOL isStack = [column isKindOfClass:UINavigationController.class];
        UIViewController *columnStack = isStack ? column : column.navigationController;
        if (columnStack && columnStack != primaryStack &&
            [stacked indexOfObjectIdenticalTo:columnStack] != NSNotFound) {
            return column;
        }
    }
    return primary;
}

// The columns UIKit shows, the secondary first; collapsed, the screens of the current child.
- (NSArray<UIViewController *> *)shownComponentViewControllers {
    if (self.isCollapsed) {
        return self.getCurrentChild.shownComponentViewControllers ?: @[];
    }
    NSMutableArray<UIViewController *> *screens = [NSMutableArray new];
    for (NSString *name in @[ @"secondary", @"supplementary", @"primary", @"inspector" ]) {
        UIViewController *column = _columns[name];
        if (column.viewIfLoaded.window) {
            [screens addObjectsFromArray:column.shownComponentViewControllers];
        }
    }
    return screens.count ? screens : (self.getCurrentChild.shownComponentViewControllers ?: @[]);
}

// Every column of the layout, also one UIKit does not show now.
- (NSArray<UIViewController *> *)layoutChildren {
    NSMutableArray<UIViewController *> *children = [NSMutableArray new];
    for (NSString *name in ANNSplitViewColumnNames()) {
        if (_columns[name]) {
            [children addObject:_columns[name]];
        }
    }
    return children;
}

#pragma mark - UISplitViewControllerDelegate

static NSString *ANNDisplayModeName(UISplitViewControllerDisplayMode displayMode) {
    switch (displayMode) {
    case UISplitViewControllerDisplayModeSecondaryOnly:
        return @"secondaryOnly";
    case UISplitViewControllerDisplayModeOneBesideSecondary:
        return @"oneBesideSecondary";
    case UISplitViewControllerDisplayModeOneOverSecondary:
        return @"oneOverSecondary";
    case UISplitViewControllerDisplayModeTwoBesideSecondary:
        return @"twoBesideSecondary";
    case UISplitViewControllerDisplayModeTwoOverSecondary:
        return @"twoOverSecondary";
    case UISplitViewControllerDisplayModeTwoDisplaceSecondary:
        return @"twoDisplaceSecondary";
    default:
        return @"automatic";
    }
}

// Once UIKit has made the change: the inspector shows or hides with it. The same state is sent
// once.
- (void)sendDisplayMode:(UISplitViewControllerDisplayMode)displayMode {
    __weak ANNSplitViewController *weakSelf = self;
    void (^send)(void) = ^{
      ANNSplitViewController *strongSelf = weakSelf;
      if (!strongSelf) {
          return;
      }
      NSNumber *inspectorVisible = nil;
      if (@available(iOS 26.0, *)) {
          if (strongSelf->_columns[@"inspector"]) {
              inspectorVisible =
                  @([strongSelf isShowingColumn:UISplitViewControllerColumnInspector]);
          }
      }
      NSString *name = ANNDisplayModeName(displayMode);
      NSString *state = [NSString stringWithFormat:@"%@ %@", name, inspectorVisible];
      if ([state isEqualToString:strongSelf->_sentDisplayModeState]) {
          return;
      }
      strongSelf->_sentDisplayModeState = state;
      [strongSelf.eventEmitter sendSplitViewDisplayModeChanged:strongSelf.layoutInfo.componentId
                                                   displayMode:name
                                              inspectorVisible:inspectorVisible];
    };
    id<UIViewControllerTransitionCoordinator> coordinator = self.transitionCoordinator;
    if (coordinator) {
        [coordinator
            animateAlongsideTransition:nil
                            completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
                              send();
                            }];
    } else {
        dispatch_async(dispatch_get_main_queue(), send);
    }
}

// Also when the split view first lays out its columns.
- (void)splitViewController:(UISplitViewController *)svc
    willChangeToDisplayMode:(UISplitViewControllerDisplayMode)displayMode {
    [self sendDisplayMode:displayMode];
    // A column that shows or hides: a layer that waits to show takes its screens again.
    [ANNShownScreens changedIn:self];
}

- (void)splitViewController:(UISplitViewController *)svc
              didShowColumn:(UISplitViewControllerColumn)column API_AVAILABLE(ios(26.0)) {
    if (column == UISplitViewControllerColumnInspector) {
        [self sendDisplayMode:self.displayMode];
    }
}

- (void)splitViewController:(UISplitViewController *)svc
              didHideColumn:(UISplitViewControllerColumn)column API_AVAILABLE(ios(26.0)) {
    if (column == UISplitViewControllerColumnInspector) {
        [self sendDisplayMode:self.displayMode];
    }
}

// `splitView.compactTopColumn`: the column on top when the columns collapse into one stack (without
// a compact layout); UIKit's choice without the option.
- (UISplitViewControllerColumn)splitViewController:(UISplitViewController *)svc
         topColumnForCollapsingToProposedTopColumn:(UISplitViewControllerColumn)proposedTopColumn {
    NSString *top = self.resolveOptionsWithDefault.splitView.compactTopColumn;
    if ([top isEqualToString:@"primary"]) {
        return UISplitViewControllerColumnPrimary;
    }
    if ([top isEqualToString:@"secondary"]) {
        return UISplitViewControllerColumnSecondary;
    }
    return proposedTopColumn;
}

- (void)splitViewControllerDidCollapse:(UISplitViewController *)svc {
    [self sendColumnsStackedIfChanged];
    [self shownColumnsChanged];
}

- (void)splitViewControllerDidExpand:(UISplitViewController *)svc {
    [self sendColumnsStackedIfChanged];
    [self shownColumnsChanged];
}

#pragma mark - UIViewController

- (UIStatusBarStyle)preferredStatusBarStyle {
    return [self.presenter statusBarStyleForOptions:self.resolveOptionsWithDefault];
}

- (BOOL)prefersStatusBarHidden {
    return [self.presenter statusBarHiddenForOptions:self.resolveOptionsWithDefault];
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    return [self.presenter supportedOrientationsForOptions:self.resolveOptionsWithDefault];
}

// The system UI follows the main column, or the compact layout while collapsed into it.
- (UIViewController *)systemUIChild {
    if (self.isCollapsedIntoCompact) {
        return _columns[@"compact"];
    }
    return _columns[@"secondary"];
}

- (UIViewController *)childViewControllerForHomeIndicatorAutoHidden {
    return self.systemUIChild;
}

- (UIViewController *)childViewControllerForScreenEdgesDeferringSystemGestures {
    return self.systemUIChild;
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
// `layout.verticalBars` of the main column's screen, as the home indicator.
- (UIViewController *)childViewControllerForPreferredVerticalBarBehavior API_AVAILABLE(ios(27.1)) {
    return self.systemUIChild;
}
#endif

- (BOOL)hidesBottomBarWhenPushed {
    return [self.presenter hidesBottomBarWhenPushedForOptions:self.resolveOptionsWithDefault];
}

@end

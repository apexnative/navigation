#import "ANNArrangementViewController.h"

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100

#import "ANNComponentViewController.h"
#import "ANNEventContract.h"
#import "ANNExternalViewController.h"
#import "ANNScreenEnvironment.h"
#import "ANNShownScreens.h"
#import "ANNUtils.h"

// `axes`: the axes the pair may use, both without the value.
static UIAxis ANNArrangementAxes(id value) {
    if ([@"horizontal" isEqual:value]) {
        return UIAxisHorizontal;
    }
    if ([@"vertical" isEqual:value]) {
        return UIAxisVertical;
    }
    return UIAxisHorizontal | UIAxisVertical;
}

// `primaryEdge` of an overlay: where the primary goes when the pair goes side by side, trailing without the value.
static NSDirectionalRectEdge ANNPrimaryEdge(id value) {
    if ([@"leading" isEqual:value]) {
        return NSDirectionalRectEdgeLeading;
    }
    if ([@"top" isEqual:value]) {
        return NSDirectionalRectEdgeTop;
    }
    if ([@"bottom" isEqual:value]) {
        return NSDirectionalRectEdgeBottom;
    }
    return NSDirectionalRectEdgeTrailing;
}

// The edge across from `edge`: the secondary's, beside the primary.
static NSDirectionalRectEdge ANNOppositeEdge(NSDirectionalRectEdge edge) {
    switch (edge) {
    case NSDirectionalRectEdgeLeading:
        return NSDirectionalRectEdgeTrailing;
    case NSDirectionalRectEdgeTop:
        return NSDirectionalRectEdgeBottom;
    case NSDirectionalRectEdgeBottom:
        return NSDirectionalRectEdgeTop;
    default:
        return NSDirectionalRectEdgeLeading;
    }
}

// Overlay view properties with the edge a view takes when the pair goes side by side.
static UIOverlayArrangementViewProperties *ANNOverlayProperties(UIOverlayArrangement *overlay,
                                                               NSDirectionalRectEdge edge)
    API_AVAILABLE(ios(27.1)) {
    UIOverlayArrangementViewProperties *properties = [overlay.defaultViewProperties copy];
    properties.edge = edge;
    return properties;
}

// The arrangement of the layout's data: `style`, `axes`, `fraction` and `primaryEdge`, which JS always sends.
static UIArrangement *ANNArrangementOfData(NSDictionary *data) API_AVAILABLE(ios(27.1)) {
    UIAxis axes = ANNArrangementAxes(data[@"axes"]);
    if ([@"overlay" isEqual:data[@"style"]]) {
        UIOverlayArrangement *overlay = [UIOverlayArrangement overlayArrangement];
        overlay.axes = axes;
        // UIKit's own edges (none set) put the primary on the trailing side and the secondary beside it at a fold
        // (measured on iPhone Duo): the default `trailing` keeps them, as a plain UIKit pair has them. Another edge
        // goes to both views, the secondary's across from the primary's, so neither is left without a place.
        NSDirectionalRectEdge edge = ANNPrimaryEdge(data[@"primaryEdge"]);
        if (edge != NSDirectionalRectEdgeTrailing) {
            [overlay setViewProperties:ANNOverlayProperties(overlay, edge)
                          forPlacement:UIArrangementViewControllerViewPlacementPrimary];
            [overlay setViewProperties:ANNOverlayProperties(overlay, ANNOppositeEdge(edge))
                          forPlacement:UIArrangementViewControllerViewPlacementSecondary];
        }
        return overlay;
    }
    UISplitArrangement *split = [UISplitArrangement splitArrangement];
    split.axes = axes;
    // UIKit's own share is the half, and a partly folded device moves it to the fold (measured on iPhone Duo): another
    // share is set only where it differs, along each axis the pair may split on.
    NSNumber *fraction = [data[@"fraction"] isKindOfClass:NSNumber.class] ? data[@"fraction"] : nil;
    CGFloat share = fraction ? MIN(MAX(fraction.doubleValue, 0), 1) : 0.5;
    if (fabs(share - 0.5) > 0.001) {
        UISplitArrangementViewProperties *primary = [split.defaultViewProperties copy];
        UISplitArrangementDimension *dimension =
            [UISplitArrangementDimension fractionalDimension:share];
        if (axes & UIAxisHorizontal) {
            UISplitArrangementDimensionRange *width = [primary.width copy];
            width.preferred = dimension;
            primary.width = width;
        }
        if (axes & UIAxisVertical) {
            UISplitArrangementDimensionRange *height = [primary.height copy];
            height.preferred = dimension;
            primary.height = height;
        }
        [split setViewProperties:primary
                    forPlacement:UIArrangementViewControllerViewPlacementPrimary];
    }
    return split;
}

// The primary alone: a split with no axis leaves the secondary out of the window. An overlay with no axis keeps both
// in it, full size (measured on iPhone Duo).
static UIArrangement *ANNPrimaryAloneArrangement(void) API_AVAILABLE(ios(27.1)) {
    UISplitArrangement *split = [UISplitArrangement splitArrangement];
    split.axes = UIAxisNeither;
    return split;
}

// Room for a pair: both size classes regular (iPad, iPhone Duo unfolded). Nil without a size class yet: the view
// controller is not in a window.
static NSNumber *ANNIsLargeWindow(UITraitCollection *traits) {
    if (traits.horizontalSizeClass == UIUserInterfaceSizeClassUnspecified ||
        traits.verticalSizeClass == UIUserInterfaceSizeClassUnspecified) {
        return nil;
    }
    return @(traits.horizontalSizeClass == UIUserInterfaceSizeClassRegular &&
             traits.verticalSizeClass == UIUserInterfaceSizeClassRegular);
}

// How long a change of the pair takes to settle: UIKit's animation at a fold, with room to spare.
static const NSTimeInterval ANNSettleTime = 0.6;

// A React screen of the pair: a native screen has no `useArrangement`, and keeps no state.
static BOOL ANNKeepsState(UIViewController *screen) {
    return [screen isKindOfClass:ANNComponentViewController.class] &&
           ![screen isKindOfClass:ANNExternalViewController.class];
}

static NSDictionary<NSString *, id> *ANNStateOf(NSString *placement, NSString *layout, BOOL front,
                                                BOOL hidden) {
    const auto &keys = ANNEventContract.ArrangementChanged;
    return @{
        keys.placement : placement,
        keys.layout : layout,
        keys.front : @(front),
        keys.hidden : @(hidden),
    };
}

// The part of a screen's view on screen, in the window: its frame cut by the views around it up to the arrangement's
// that clip their content, as UIKit may place a view by its container.
static CGRect ANNShownFrame(UIView *view, UIView *arrangementView) {
    if (!view) {
        return CGRectNull;
    }
    CGRect frame = [view convertRect:view.bounds toView:nil];
    for (UIView *container = view.superview; container; container = container.superview) {
        if (container.clipsToBounds) {
            frame = CGRectIntersection(frame, [container convertRect:container.bounds toView:nil]);
        }
        if (container == arrangementView) {
            break;
        }
    }
    return frame;
}

// A view of the pair in the window, with room. UIKit takes a view that the axes leave no room for out of the window.
static BOOL ANNIsShown(UIViewController *screen, UIArrangementViewState *state, CGRect shownFrame)
    API_AVAILABLE(ios(27.1)) {
    UIView *view = screen.viewIfLoaded;
    return view.window != nil && !view.isHidden && !state.isHidden && !CGRectIsEmpty(shownFrame);
}

@interface ANNArrangementViewController ()
@property(nonatomic, strong, readwrite) UIViewController *primary;
@property(nonatomic, strong, readwrite) UIViewController *secondary;
@end

@implementation ANNArrangementViewController {
    // The screens that render before the pair is ready for presentation, and whether the secondary is one of them.
    NSUInteger _screensRendering;
    BOOL _secondaryCounted;
    BOOL _stateUpdateScheduled;
    // Counts the changes: the update once they settled runs after the last one.
    NSUInteger _stateChanges;
    // The React views of the screens stopped: the pair keeps no state any more.
    BOOL _screensDestroyed;
    // The arrangement of the layout's data, and whether the primary shows alone outside a large window
    // (`compact: 'primary'`).
    UIArrangement *_arrangement;
    BOOL _primaryAloneWhenSmall;
    // What UIKit has now, once an arrangement went to it.
    BOOL _arranged;
    BOOL _primaryAlone;
    // The scene's size classes the pair takes for a size transition, and which transition set them last: only that
    // one's end takes them away.
    BOOL _sceneSizeClasses;
    NSUInteger _sceneSizeClassesTransition;
}

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
                           primary:(UIViewController *)primary
                         secondary:(UIViewController *)secondary
                              data:(NSDictionary *)data {
    self = [super init];
    [self.nodeHelper setUpWithLayoutInfo:layoutInfo
                                 creator:creator
                                 options:options
                          defaultOptions:defaultOptions
                               presenter:presenter
                            eventEmitter:eventEmitter];
    // The pair stands wherever a screen can and lays out as one: under opaque bars too, as a screen does; its screens
    // apply their own "under bars" rule. Otherwise UIKit takes an opaque tab bar's height off the pair's view, and a
    // tab bar in the iPhone Duo's vertical strip is as tall as the window: the pair would get no height.
    self.extendedLayoutIncludesOpaqueBars = YES;
    _primary = primary;
    _secondary = secondary;
    for (UIViewController *screen in @[ primary, secondary ]) {
        // A screen that UIKit takes out of the window has no parent then: it still belongs to the pair, and through it
        // to the stack.
        [screen setLayoutParentFallback:self];
        if ([screen isKindOfClass:ANNComponentViewController.class]) {
            ((ANNComponentViewController *)screen).arrangement = self;
        }
    }
    [self setViewController:primary
               forPlacement:UIArrangementViewControllerViewPlacementPrimary];
    [self setViewController:secondary
               forPlacement:UIArrangementViewControllerViewPlacementSecondary];
    _arrangement = ANNArrangementOfData(data);
    _primaryAloneWhenSmall = ![@"pair" isEqual:data[@"compact"]];
    // Before it is in a window: the window's size classes, which it will most likely have.
    [self arrangeForTraits:ANNUtils.mainWindow.traitCollection];
    if (!_arranged) {
        [self updateArrangement:_arrangement animated:NO];
    }
    NSArray<UITrait> *sizeClasses =
        @[ UITraitHorizontalSizeClass.class, UITraitVerticalSizeClass.class ];
    [self registerForTraitChanges:sizeClasses withAction:@selector(sizeClassesDidChange)];
    [self noteFirstStateOfData:data];
    return self;
}

// `compact: 'primary'`: the pair shows only in a large window; in any other (an iPhone, iPhone Duo folded) the primary
// shows alone, and the secondary waits out of the window until the window is large (iPhone Duo unfolded). With
// `compact: 'pair'` UIKit lays the pair out everywhere.
- (void)arrangeForTraits:(UITraitCollection *)traits {
    NSNumber *large = ANNIsLargeWindow(traits);
    if (!large) {
        return;
    }
    BOOL alone = _primaryAloneWhenSmall && !large.boolValue;
    if (_arranged && alone == _primaryAlone) {
        return;
    }
    BOOL changed = _arranged;
    _arranged = YES;
    _primaryAlone = alone;
    [self updateArrangement:alone ? ANNPrimaryAloneArrangement() : _arrangement animated:NO];
    // A secondary that leaves the window renders no content: the pair waits for it no more.
    if (alone && _secondaryCounted) {
        _secondaryCounted = NO;
        [_secondary removeReadyHandlerForKey:ANNContainerReadyHandlerKey];
        [self screenDidRender];
    }
    // The screens the pair shows changed: a layer that waits to show takes them again.
    if (changed) {
        [ANNShownScreens changedIn:self];
    }
}

// New size classes come with a new window size, which leads the pair to its new place. UIKit's own animation of the
// arrangement would aim at a layout it gives only in between (on iPhone Duo, one above the other while the window
// still has its old size classes) and play on after the pair took its real one: the screens lagged by half a second.
- (void)sizeClassesDidChange {
    [self arrangeForTraits:self.traitCollection];
}

// On iPhone Duo the scene has its new size classes as the size transition starts, and the pair gets them only after
// UIKit laid it out in the new size: the primary first filled the whole open window, then gave half of it to the
// secondary. For the transition the pair takes the scene's size classes, so it lies as it will from its first layout.
// Only where the pair has the window's size classes: a sheet or a split view's column has its own.
- (void)viewWillTransitionToSize:(CGSize)size
       withTransitionCoordinator:(id<UIViewControllerTransitionCoordinator>)coordinator {
    [super viewWillTransitionToSize:size withTransitionCoordinator:coordinator];
    UITraitCollection *scene = self.viewIfLoaded.window.windowScene.traitCollection;
    NSNumber *large = ANNIsLargeWindow(self.traitCollection);
    NSNumber *largeScene = scene ? ANNIsLargeWindow(scene) : nil;
    if (!large || !largeScene || ![self hasWindowSizeClasses]) {
        return;
    }
    // The size classes the pair has now are the scene's of an earlier transition, still running: this one keeps them
    // until its own end.
    if ([large isEqual:largeScene] && !_sceneSizeClasses) {
        return;
    }
    _sceneSizeClasses = YES;
    NSUInteger transition = ++_sceneSizeClassesTransition;
    self.traitOverrides.horizontalSizeClass = scene.horizontalSizeClass;
    self.traitOverrides.verticalSizeClass = scene.verticalSizeClass;
    [self updateTraitsIfNeeded];
    __weak ANNArrangementViewController *weakSelf = self;
    void (^ended)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          [weakSelf dropSceneSizeClassesOfTransition:transition];
        };
    [coordinator animateAlongsideTransition:nil completion:ended];
}

- (void)dropSceneSizeClassesOfTransition:(NSUInteger)transition {
    if (!_sceneSizeClasses || transition != _sceneSizeClassesTransition) {
        return;
    }
    _sceneSizeClasses = NO;
    [self.traitOverrides removeTrait:UITraitHorizontalSizeClass.class];
    [self.traitOverrides removeTrait:UITraitVerticalSizeClass.class];
}

// The pair's size classes are the window's: it stands in the window's root, not in a presented layer, and no split
// view's column is between them.
- (BOOL)hasWindowSizeClasses {
    UIWindow *window = self.viewIfLoaded.window;
    if (!window) {
        return NO;
    }
    UIViewController *top = self;
    for (UIViewController *parent = self.parentViewController; parent;
         parent = parent.parentViewController) {
        if ([parent isKindOfClass:UISplitViewController.class]) {
            return NO;
        }
        top = parent;
    }
    return top == window.rootViewController;
}

// The first state, before the pair is laid out: as UIKit lays it out in the window's shape (measured on iPhone Duo),
// side by side in a wide window, one above the other in a tall one, the primary alone outside a large window with
// `compact: 'primary'`, so the screens render first with the likely state. The first layout gives the real one.
- (void)noteFirstStateOfData:(NSDictionary *)data {
    CGSize size = ANNUtils.mainWindow.bounds.size;
    BOOL wide = size.width > size.height;
    UIAxis axes = ANNArrangementAxes(data[@"axes"]);
    BOOL paired = !_primaryAlone;
    NSString *layout = @"single";
    if (paired && [@"overlay" isEqual:data[@"style"]]) {
        layout = @"overlay";
    } else if (paired && wide && (axes & UIAxisHorizontal)) {
        layout = @"sideBySide";
    } else if (paired && !wide && (axes & UIAxisVertical)) {
        layout = @"stacked";
    }
    BOOL overlay = [layout isEqualToString:@"overlay"];
    BOOL single = [layout isEqualToString:@"single"];
    [self noteState:ANNStateOf(@"primary", layout, overlay, NO)
           ofScreen:_primary
       eventEmitter:nil];
    [self noteState:ANNStateOf(@"secondary", layout, NO, single)
           ofScreen:_secondary
       eventEmitter:nil];
}

- (void)noteState:(NSDictionary<NSString *, id> *)state
         ofScreen:(UIViewController *)screen
     eventEmitter:(ANNEventEmitter *)eventEmitter {
    if (ANNKeepsState(screen)) {
        [ANNScreenEnvironment setArrangement:state
                                 forScreenId:screen.layoutInfo.componentId
                                eventEmitter:eventEmitter];
    }
}

#pragma mark - Layout

// The primary: the pair's options are its options, and the stack shows its bars.
- (UIViewController *)getCurrentChild {
    return _primary;
}

- (NSArray<UIViewController *> *)layoutChildren {
    return [NSArray arrayWithObjects:_primary, _secondary, nil];
}

// The screens of the pair in the window, the primary first.
- (NSArray<UIViewController *> *)shownComponentViewControllers {
    NSMutableArray<UIViewController *> *shown = [NSMutableArray new];
    for (UIViewController *screen in self.layoutChildren) {
        if (screen.viewIfLoaded.window) {
            [shown addObjectsFromArray:screen.shownComponentViewControllers];
        }
    }
    return shown.count > 0 ? shown : [NSArray arrayWithObjects:_primary, nil];
}

// Both screens render; the pair is ready for presentation once the screens it shows are (both, or the primary alone:
// a secondary out of the window never renders its content), as a screen is once its content is. The screens' ready
// handlers (the container's key, beside the handlers of the waits) keep the pair weakly: what shows the pair holds it
// until then.
- (void)render {
    _screensRendering = 0;
    if (!self.waitForRender) {
        [self readyForPresentation];
    }
    NSArray<UIViewController *> *screens = self.layoutChildren;
    NSArray<UIViewController *> *waited = _primaryAlone ? @[ _primary ] : screens;
    if (_primaryAlone) {
        [_secondary removeReadyHandlerForKey:ANNContainerReadyHandlerKey];
    }
    _screensRendering = waited.count;
    _secondaryCounted = !_primaryAlone;
    __weak ANNArrangementViewController *weakSelf = self;
    for (UIViewController *screen in waited) {
        BOOL secondary = screen == _secondary;
        [screen setReadyHandler:^{
          ANNArrangementViewController *pair = weakSelf;
          if (secondary && pair) {
              pair->_secondaryCounted = NO;
          }
          [pair screenDidRender];
        }
                         forKey:ANNContainerReadyHandlerKey];
    }
    for (UIViewController *screen in screens) {
        [screen render];
    }
}

- (void)screenDidRender {
    if (_screensRendering == 0 || --_screensRendering > 0) {
        return;
    }
    [self readyForPresentation];
}

// A screen tells its parent too when it is ready (UIViewController+LayoutProtocol): the pair waits for both.
- (void)readyForPresentation {
    if (_screensRendering > 0) {
        return;
    }
    [self.nodeHelper readyForPresentation];
}

- (BOOL)waitForRender {
    return self.nodeHelper.waitForRender;
}

- (void)setWaitForRender:(BOOL)waitForRender {
    self.nodeHelper.waitForRender = waitForRender;
    _secondary.waitForRender = waitForRender;
}

// Options set with the pair's id are the primary's, as on Android: the pair has none of its own.
- (void)mergeOptions:(ANNNavigationOptions *)options {
    [_primary mergeOptions:options];
}

// The pair's options are the primary's: its changes reach the stack as the arrangement's, the child the stack shows.
// The secondary's bars don't show, so its changes stop here.
- (void)mergeChildOptions:(ANNNavigationOptions *)options child:(UIViewController *)child {
    if (child != _primary) {
        return;
    }
    [self.presenter mergeOptions:options resolvedOptions:self.resolveOptions];
    [self.layoutParent mergeChildOptions:options child:self];
}

// The stack pops and tears down its child, the arrangement: both screens go.
- (void)screenPopped {
    for (UIViewController *screen in self.layoutChildren) {
        [screen screenPopped];
    }
}

- (void)destroyReactView {
    _screensDestroyed = YES;
    for (UIViewController *screen in self.layoutChildren) {
        [screen destroyReactView];
    }
}

#pragma mark - Capabilities

// The pair's bars, search, status bar and transitions are the primary's.
- (UIViewController *)optionsScreen {
    return _primary;
}

// The stack pops the pair as one entry: both screens leave with it.
- (NSArray<UIViewController *> *)screensLeavingWithNode {
    return self.layoutChildren;
}

// The pair keeps the room of the status bar for its screens.
- (BOOL)keepsStatusBarRoom {
    return YES;
}

#pragma mark - The state of the pair

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [self setNeedsStateUpdate];
}

// In the window now: its own size classes, which the window's stood in for.
- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self arrangeForTraits:self.traitCollection];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [self setNeedsStateUpdate];
}

// Once the layout of this run of the main loop is done: both views are in their places then. And once more when the
// changes have settled: at a fold UIKit moves the views of a pair with an animation that starts with the trait change
// of the screens, and their containers can end in their places without another layout of the screens.
- (void)setNeedsStateUpdate {
    __weak ANNArrangementViewController *weakSelf = self;
    if (!_stateUpdateScheduled) {
        _stateUpdateScheduled = YES;
        dispatch_async(dispatch_get_main_queue(), ^{
          ANNArrangementViewController *arrangement = weakSelf;
          if (arrangement) {
              arrangement->_stateUpdateScheduled = NO;
              [arrangement updateState];
          }
        });
    }
    NSUInteger change = ++_stateChanges;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(ANNSettleTime * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     ANNArrangementViewController *arrangement = weakSelf;
                     if (arrangement && arrangement->_stateChanges == change) {
                         [arrangement updateState];
                     }
                   });
}

// How the pair lies, from where UIKit put the two views. stateForPlacement: does not tell it alone: an overlay that
// goes side by side at a fold still has splitAxis none and both zIndex 0 there (iPhone Duo), only the frames tell. Out
// of the window the pair says nothing: its views are out of it too.
- (void)updateState {
    if (!self.viewIfLoaded.window || _screensDestroyed) {
        return;
    }
    UIArrangementViewState *primaryState =
        [self stateForPlacement:UIArrangementViewControllerViewPlacementPrimary];
    UIArrangementViewState *secondaryState =
        [self stateForPlacement:UIArrangementViewControllerViewPlacementSecondary];
    CGRect primaryFrame = ANNShownFrame(_primary.viewIfLoaded, self.view);
    CGRect secondaryFrame = ANNShownFrame(_secondary.viewIfLoaded, self.view);
    BOOL primaryShown = ANNIsShown(_primary, primaryState, primaryFrame);
    BOOL secondaryShown = ANNIsShown(_secondary, secondaryState, secondaryFrame);
    // Neither in the window: between two layouts.
    if (!primaryShown && !secondaryShown) {
        return;
    }
    NSString *layout = @"single";
    BOOL primaryFront = NO;
    BOOL secondaryFront = NO;
    if (primaryShown && secondaryShown) {
        CGRect overlap = CGRectIntersection(primaryFrame, secondaryFrame);
        if (!CGRectIsNull(overlap) && overlap.size.width > 1 && overlap.size.height > 1) {
            layout = @"overlay";
            // UIKit puts the primary over the secondary (zIndex 1 and 0).
            primaryFront = primaryState.zIndex >= secondaryState.zIndex;
            secondaryFront = !primaryFront;
        } else {
            // Apart: side by side where they are apart across, one above the other where they are apart down.
            CGFloat across = MAX(CGRectGetMinX(secondaryFrame) - CGRectGetMaxX(primaryFrame),
                                 CGRectGetMinX(primaryFrame) - CGRectGetMaxX(secondaryFrame));
            CGFloat down = MAX(CGRectGetMinY(secondaryFrame) - CGRectGetMaxY(primaryFrame),
                               CGRectGetMinY(primaryFrame) - CGRectGetMaxY(secondaryFrame));
            layout = across >= down ? @"sideBySide" : @"stacked";
        }
    }
    [self noteState:ANNStateOf(@"primary", layout, primaryFront, !primaryShown)
           ofScreen:_primary
       eventEmitter:self.eventEmitter];
    [self noteState:ANNStateOf(@"secondary", layout, secondaryFront, !secondaryShown)
           ofScreen:_secondary
       eventEmitter:self.eventEmitter];
}

#pragma mark - ANNArrangementScreenDelegate

- (void)screenDidChangeLayout:(UIViewController *)screen {
    [self setNeedsStateUpdate];
}

- (void)screen:(UIViewController *)screen didSetToolbarItemsAnimated:(BOOL)animated {
    if (screen == _primary) {
        [super setToolbarItems:screen.toolbarItems animated:animated];
    }
}

#pragma mark - UIViewController overrides

// The stack shows the bar of its child: the primary's title, buttons and search.
- (UINavigationItem *)navigationItem {
    return _primary ? _primary.navigationItem : super.navigationItem;
}

- (NSArray<__kindof UIBarButtonItem *> *)toolbarItems {
    return _primary ? _primary.toolbarItems : super.toolbarItems;
}

- (UIViewController *)childViewControllerForStatusBarStyle {
    return _primary;
}

- (UIViewController *)childViewControllerForStatusBarHidden {
    return _primary;
}

- (UIViewController *)childViewControllerForHomeIndicatorAutoHidden {
    return _primary;
}

- (UIViewController *)childViewControllerForScreenEdgesDeferringSystemGestures {
    return _primary;
}

- (UIViewController *)childViewControllerForPreferredVerticalBarBehavior {
    return _primary;
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    return _primary ? _primary.supportedInterfaceOrientations
                    : super.supportedInterfaceOrientations;
}

- (BOOL)hidesBottomBarWhenPushed {
    return _primary.hidesBottomBarWhenPushed;
}

@end

#endif

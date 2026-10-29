#import "ANNSideMenuViewController.h"
#import "InteractivePopGestureDelegate.h"

// The width of a menu without the option, the speed of its animations (points per second), the
// edge a menu opens from with `openGestureMode: 'bezel'`, the pan speed past which a released pan
// keeps its direction, the dimming of the center under a menu over it, and how far a pan stretches.
static const CGFloat ANNSideMenuDefaultWidth = 280;
static const CGFloat ANNSideMenuDefaultVelocity = 840;
static const CGFloat ANNSideMenuEdgeWidth = 20;
static const CGFloat ANNSideMenuFlickVelocity = 200;
static const CGFloat ANNSideMenuDimAlpha = 0.5;
static const CGFloat ANNSideMenuMaxStretch = 1.1;
static const NSTimeInterval ANNSideMenuMinimumDuration = 0.1;

// `sideMenu.menusAttachMode` of the side menu's own options, else of the defaults; any other value, and none, is
// `together`.
static ANNSideMenuAttachMode ANNSideMenuAttachModeOf(ANNNavigationOptions *options,
                                                     ANNNavigationOptions *defaultOptions) {
    Text *value = options.sideMenu.menusAttachMode;
    if (!value.hasValue) {
        value = defaultOptions.sideMenu.menusAttachMode;
    }
    NSString *mode = [value withDefault:nil];
    if ([mode isEqualToString:@"afterCenter"]) {
        return ANNSideMenuAttachModeAfterCenter;
    }
    if ([mode isEqualToString:@"onOpen"]) {
        return ANNSideMenuAttachModeOnOpen;
    }
    return ANNSideMenuAttachModeTogether;
}

static UINavigationController *ANNStackOfResponder(UIResponder *responder) {
    for (; responder; responder = responder.nextResponder) {
        if ([responder isKindOfClass:UINavigationController.class]) {
            return (UINavigationController *)responder;
        }
    }
    return nil;
}

// Less than a point: a scroll view at its edge, also after rounding.
static const CGFloat ANNSideMenuScrollEdgeTolerance = 0.5;

// A scroll view the user can move sideways: its content is wider than it, or it bounces sideways anyway.
static BOOL ANNScrollsHorizontally(UIScrollView *scrollView) {
    if (!scrollView.scrollEnabled) {
        return NO;
    }
    UIEdgeInsets inset = scrollView.adjustedContentInset;
    CGFloat content = scrollView.contentSize.width + inset.left + inset.right;
    return content > CGRectGetWidth(scrollView.bounds) + ANNSideMenuScrollEdgeTolerance ||
           scrollView.alwaysBounceHorizontal;
}

// Whether the content still moves for a finger going toward `dx` (in the scroll view's own coordinates, which React
// Native mirrors in RTL): toward +x the content goes back to its start, toward -x on to its end.
static BOOL ANNScrollViewCanScroll(UIScrollView *scrollView, CGFloat dx) {
    UIEdgeInsets inset = scrollView.adjustedContentInset;
    CGFloat offset = scrollView.contentOffset.x;
    if (dx > 0) {
        return offset > -inset.left + ANNSideMenuScrollEdgeTolerance;
    }
    CGFloat end = scrollView.contentSize.width - CGRectGetWidth(scrollView.bounds) + inset.right;
    return offset < end - ANNSideMenuScrollEdgeTolerance;
}

// Where a menu is in its appearance: the menu's screens get the appearance callbacks of a screen
// that shows and hides, while the side menu itself is on screen.
typedef NS_ENUM(NSInteger, ANNSideMenuAppearance) {
    ANNSideMenuAppearanceHidden,
    ANNSideMenuAppearanceAppearing,
    ANNSideMenuAppearanceShown,
    ANNSideMenuAppearanceDisappearing,
};

@interface ANNSideMenuSideState : NSObject
@property(nonatomic) CGFloat width;
@property(nonatomic) ANNSideMenuOpenMode openMode;
@property(nonatomic) BOOL shouldStretch;
@property(nonatomic) CGFloat velocity;
@property(nonatomic) BOOL enabled;
@property(nonatomic) BOOL glass;
@property(nonatomic) ANNSideMenuAppearance appearance;
@property(nonatomic, copy) NSString *scrimTestID;
// The menu's part is in its container and renders (`menusAttachMode`).
@property(nonatomic) BOOL attached;
@end

@implementation ANNSideMenuSideState

- (instancetype)init {
    self = [super init];
    _width = ANNSideMenuDefaultWidth;
    _openMode = ANNSideMenuOpenModePushContent;
    _shouldStretch = YES;
    _velocity = ANNSideMenuDefaultVelocity;
    _enabled = YES;
    return self;
}

@end

@interface ANNSideMenuViewController () <UIGestureRecognizerDelegate>

@property(nonatomic, strong, readwrite) ANNSideMenuChildViewController *center;
@property(nonatomic, strong, readwrite) ANNSideMenuChildViewController *left;
@property(nonatomic, strong, readwrite) ANNSideMenuChildViewController *right;
@property(nonatomic, readwrite) ANNSideMenuSide openSide;

@end

@implementation ANNSideMenuViewController {
    NSArray<UIViewController *> *_sideMenuChildren;
    ANNSideMenuSideState *_leftState;
    ANNSideMenuSideState *_rightState;
    ANNSideMenuOpenGestureMode _openGestureMode;
    ANNSideMenuAttachMode _attachMode;
    BOOL _firstAppeared;
    NSString *_animationType;
    UIView *_centerContainer;
    UIView *_leftContainer;
    UIView *_rightContainer;
    // Under the screen of a menu with `glass`.
    UIVisualEffectView *_leftGlass;
    UIVisualEffectView *_rightGlass;
    // Over the center while a menu shows: takes the touches that close the menu, dims the center
    // under a menu over it.
    UIView *_shield;
    UIPanGestureRecognizer *_pan;
    UITapGestureRecognizer *_tap;
    // The menu on screen, also while it opens or closes, and how far it is open: 0 closed, 1
    // open, more while a pan stretches it.
    ANNSideMenuSide _side;
    CGFloat _progress;
    CGFloat _panStartProgress;
    CGPoint _touchStart;
    BOOL _animating;
    // Only the last animation finishes: one that a later one replaced does nothing.
    NSUInteger _animationCount;
    BOOL _appeared;
    // The view the pan's touch began on, while the pan may open a menu.
    __weak UIView *_touchView;
}

- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
              childViewControllers:(NSArray *)childViewControllers
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter {
    self = [super init];
    _leftState = [ANNSideMenuSideState new];
    _rightState = [ANNSideMenuSideState new];
    _openGestureMode = ANNSideMenuOpenGestureModeBezel;
    _attachMode = ANNSideMenuAttachModeOf(options, defaultOptions);
    [self setSideMenuChildren:childViewControllers];

    self.presenter = presenter;
    [presenter bindViewController:self];
    self.defaultOptions = defaultOptions;
    self.options = options;
    self.layoutInfo = layoutInfo;
    self.eventEmitter = eventEmitter;
    [presenter applyOptionsOnInit:self.resolveOptions];

    // The center lays out under the bars of the layout around it, as in bottom tabs: otherwise it stops above the tab
    // bar and leaves a gap.
    self.extendedLayoutIncludesOpaqueBars = YES;
    self.edgesForExtendedLayout |= UIRectEdgeBottom;
    return self;
}

// Every child must be a side of the menu; the layout check lets nothing else through, and the text names what came.
// Each side then takes the child that says it is that side.
- (void)setSideMenuChildren:(NSArray *)children {
    NSUInteger stranger = [children indexOfObjectPassingTest:^BOOL(id item, NSUInteger index, BOOL *stop) {
      return ![item isKindOfClass:ANNSideMenuChildViewController.class];
    }];
    if (stranger != NSNotFound) {
        NSString *reason =
            [NSString stringWithFormat:@"Unknown side menu type %@", children[stranger]];
        @throw [NSException exceptionWithName:@"UnknownSideMenuControllerType"
                                       reason:reason
                                     userInfo:nil];
    }
    _sideMenuChildren = children;
    self.center = [self childOfType:ANNSideMenuChildTypeCenter among:children];
    self.left = [self childOfType:ANNSideMenuChildTypeLeft among:children];
    self.right = [self childOfType:ANNSideMenuChildTypeRight among:children];
}

// The last child of that side: a later one would take the place of an earlier one. Nil when the layout has none.
- (nullable ANNSideMenuChildViewController *)childOfType:(ANNSideMenuChildType)type
                                                   among:(NSArray<ANNSideMenuChildViewController *> *)children {
    for (ANNSideMenuChildViewController *child in children.reverseObjectEnumerator) {
        if (child.type == type) {
            return child;
        }
    }
    return nil;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    _leftContainer = [self makeContainer];
    _rightContainer = [self makeContainer];
    _centerContainer = [self makeContainer];
    [self putChild:self.center into:_centerContainer];
    _centerContainer.hidden = NO;
    if (_attachMode == ANNSideMenuAttachModeTogether) {
        [self attachMenu:ANNSideMenuSideLeft];
        [self attachMenu:ANNSideMenuSideRight];
    }
    [self applyGlassOf:ANNSideMenuSideLeft];
    [self applyGlassOf:ANNSideMenuSideRight];

    _shield = [[UIView alloc] initWithFrame:_centerContainer.bounds];
    _shield.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _shield.hidden = YES;
    [_centerContainer addSubview:_shield];

    _tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(handleTap:)];
    [_shield addGestureRecognizer:_tap];
    _pan = [[UIPanGestureRecognizer alloc] initWithTarget:self action:@selector(handlePan:)];
    _pan.delegate = self;
    [self.view addGestureRecognizer:_pan];
}

// The view of a part, which the menu moves; a menu's container is hidden while closed.
- (UIView *)makeContainer {
    UIView *container = [[UIView alloc] initWithFrame:self.view.bounds];
    container.hidden = YES;
    [self.view addSubview:container];
    return container;
}

// A part in its container, over the container's glass. Loading the part's view loads its node's, which renders.
- (void)putChild:(UIViewController *)child into:(UIView *)container {
    if (!child) {
        return;
    }
    [self addChildViewController:child];
    child.view.frame = container.bounds;
    child.view.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [container addSubview:child.view];
    [child didMoveToParentViewController:self];
}

// The menu's part goes into its container and renders, once: with the side menu's view (`together`), after its first
// appearance (`afterCenter`), or as the menu first starts to open (`onOpen`, and any mode for a menu that opens
// earlier). A menu not attached yet keeps its node and options; only its view and content wait.
- (void)attachMenu:(ANNSideMenuSide)side {
    ANNSideMenuChildViewController *menu = [self menuOf:side];
    if (!menu || [self stateOf:side].attached || self.nodeHelper.destroyed) {
        return;
    }
    [self loadViewIfNeeded];
    ANNSideMenuSideState *state = [self stateOf:side];
    if (state.attached) {
        return;
    }
    state.attached = YES;
    [self putChild:menu into:[self containerOf:side]];
    [menu render];
}

// The current part renders, and every other part that is attached or will be with the side menu's view.
- (void)render {
    [self.nodeHelper render];
    UIViewController *currentChild = self.getCurrentChild;
    for (UIViewController *vc in _sideMenuChildren) {
        if (vc != currentChild && [self rendersPart:vc]) {
            [vc render];
        }
    }
}

- (BOOL)rendersPart:(UIViewController *)part {
    if (part == self.left) {
        return _attachMode == ANNSideMenuAttachModeTogether || _leftState.attached;
    }
    if (part == self.right) {
        return _attachMode == ANNSideMenuAttachModeTogether || _rightState.attached;
    }
    return YES;
}

#pragma mark - Options

- (ANNSideMenuSideState *)stateOf:(ANNSideMenuSide)side {
    return side == ANNSideMenuSideRight ? _rightState : _leftState;
}

- (ANNSideMenuChildViewController *)menuOf:(ANNSideMenuSide)side {
    if (side == ANNSideMenuSideLeft) {
        return self.left;
    }
    return side == ANNSideMenuSideRight ? self.right : nil;
}

- (UIView *)containerOf:(ANNSideMenuSide)side {
    return side == ANNSideMenuSideRight ? _rightContainer : _leftContainer;
}

- (void)side:(ANNSideMenuSide)side enabled:(BOOL)enabled {
    [self stateOf:side].enabled = enabled;
}

- (void)side:(ANNSideMenuSide)side width:(double)width {
    [self stateOf:side].width = width;
    [self layoutSides];
}

- (void)side:(ANNSideMenuSide)side openMode:(ANNSideMenuOpenMode)openMode {
    ANNSideMenuSideState *state = [self stateOf:side];
    if (state.openMode == openMode) {
        return;
    }
    state.openMode = openMode;
    if (_side == side) {
        [self orderSide:side];
        [self layoutSides];
    }
}

- (void)side:(ANNSideMenuSide)side shouldStretch:(BOOL)shouldStretch {
    [self stateOf:side].shouldStretch = shouldStretch;
}

- (void)side:(ANNSideMenuSide)side animationVelocity:(double)velocity {
    [self stateOf:side].velocity = velocity > 0 ? velocity : ANNSideMenuDefaultVelocity;
}

- (void)side:(ANNSideMenuSide)side glass:(BOOL)glass {
    [self stateOf:side].glass = glass;
    [self applyGlassOf:side];
}

- (void)applyGlassOf:(ANNSideMenuSide)side {
    if (!self.isViewLoaded) {
        return;
    }
    UIVisualEffectView *glassView = side == ANNSideMenuSideRight ? _rightGlass : _leftGlass;
    if (![self stateOf:side].glass) {
        [glassView removeFromSuperview];
        glassView = nil;
    } else if (!glassView) {
        if (@available(iOS 26.0, *)) {
            UIView *container = [self containerOf:side];
            glassView = [[UIVisualEffectView alloc]
                initWithEffect:[UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular]];
            glassView.frame = container.bounds;
            glassView.autoresizingMask =
                UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
            [container insertSubview:glassView atIndex:0];
        }
    }
    if (side == ANNSideMenuSideRight) {
        _rightGlass = glassView;
    } else {
        _leftGlass = glassView;
    }
}

- (void)setOpenGestureMode:(ANNSideMenuOpenGestureMode)openGestureMode {
    _openGestureMode = openGestureMode;
}

- (void)setAnimationType:(NSString *)animationType {
    _animationType = animationType;
    [self layoutSides];
}

- (void)side:(ANNSideMenuSide)side visible:(BOOL)visible {
    [self side:side visible:visible animated:YES];
}

// A menu on screen out of the window closes at once, as one opens there: no animation runs out of a window.
- (void)side:(ANNSideMenuSide)side visible:(BOOL)visible animated:(BOOL)animated {
    if (visible) {
        [self open:side animated:animated velocity:0];
    } else if (_side == side) {
        if (!animated || !self.viewIfLoaded.window) {
            [self closeImmediately];
        } else {
            [self animateSide:side open:NO velocity:0];
        }
    }
}

- (void)side:(ANNSideMenuSide)side scrimTestID:(NSString *)testID {
    [self stateOf:side].scrimTestID = testID;
    if (_side == side) {
        _shield.accessibilityIdentifier = testID;
    }
}

#pragma mark - Opening and closing

- (void)open:(ANNSideMenuSide)side animated:(BOOL)animated velocity:(CGFloat)velocity {
    if (![self menuOf:side] || (_side == side && _progress >= 1 && !_animating)) {
        return;
    }
    [self loadViewIfNeeded];
    if (_side != ANNSideMenuSideNone && _side != side) {
        [self closeImmediately];
    }
    [self show:side];
    if (!animated || !self.view.window) {
        [self stopAnimation];
        _progress = 1;
        [self layoutSides];
        [self finishSide:side open:YES];
        return;
    }
    [self animateSide:side open:YES velocity:velocity];
}

- (void)closeImmediately {
    [self stopAnimation];
    ANNSideMenuSide side = _side;
    [self side:side willBeVisible:NO animated:NO];
    _progress = 0;
    [self layoutSides];
    [self finishSide:side open:NO];
}

// A menu gets where it goes at once: the animation running now ends here, and its completion does nothing.
- (void)stopAnimation {
    ++_animationCount;
    _animating = NO;
    if (!self.isViewLoaded) {
        return;
    }
    for (UIView *view in @[ _leftContainer, _rightContainer, _centerContainer, _shield ]) {
        [view.layer removeAllAnimations];
    }
}

// The menu on screen in its place under or over the center, its screens about to appear.
- (void)show:(ANNSideMenuSide)side {
    [self attachMenu:side];
    _side = side;
    [self orderSide:side];
    [self containerOf:side].hidden = NO;
    _shield.hidden = NO;
    _shield.accessibilityIdentifier = [self stateOf:side].scrimTestID;
    [self side:side willBeVisible:YES animated:YES];
}

- (void)orderSide:(ANNSideMenuSide)side {
    if (!self.isViewLoaded) {
        return;
    }
    UIView *container = [self containerOf:side];
    if ([self stateOf:side].openMode == ANNSideMenuOpenModeAboveContent) {
        [self.view bringSubviewToFront:container];
    } else {
        [self.view sendSubviewToBack:container];
    }
}

- (void)animateSide:(ANNSideMenuSide)side open:(BOOL)open velocity:(CGFloat)velocity {
    if (side == ANNSideMenuSideNone || !self.isViewLoaded) {
        return;
    }
    // A pan that turned back: the screens of the menu go back to where they were.
    [self side:side willBeVisible:open animated:YES];
    ANNSideMenuSideState *state = [self stateOf:side];
    CGFloat target = open ? 1 : 0;
    CGFloat distance = fabs(target - _progress) * state.width;
    NSTimeInterval duration =
        MAX(distance / MAX(velocity, state.velocity), ANNSideMenuMinimumDuration);
    // A menu at rest starts from its place as laid out now, which the screen may not have drawn yet (a menu just
    // shown): from the drawn state (`BeginFromCurrentState`) starts only a menu already moving.
    UIViewAnimationOptions options = UIViewAnimationOptionCurveEaseOut;
    if (_animating) {
        options |= UIViewAnimationOptionBeginFromCurrentState;
    } else {
        [self layoutSides];
    }
    _animating = YES;
    NSUInteger animation = ++_animationCount;
    [UIView animateWithDuration:duration
        delay:0
        options:options
        animations:^{
          self->_progress = target;
          [self layoutSides];
        }
        completion:^(BOOL finished) {
          if (animation != self->_animationCount) {
              return;
          }
          self->_animating = NO;
          [self finishSide:side open:open];
        }];
}

- (void)finishSide:(ANNSideMenuSide)side open:(BOOL)open {
    if (!open) {
        [self containerOf:side].hidden = YES;
        _shield.hidden = YES;
        _side = ANNSideMenuSideNone;
        [self resetVisualStateOf:side];
    }
    self.openSide = open ? side : ANNSideMenuSideNone;
    [self side:side transitionEnded:open];
    // VoiceOver stays in the open menu.
    UIView *container = [self containerOf:side];
    container.accessibilityViewIsModal = open;
    _centerContainer.accessibilityElementsHidden = open;
    UIAccessibilityPostNotification(UIAccessibilityScreenChangedNotification,
                                    open ? container : nil);
    [self layoutSides];
    [self setNeedsStatusBarAppearanceUpdate];
    [self setNeedsUpdateOfHomeIndicatorAutoHidden];
    [self setNeedsUpdateOfScreenEdgesDeferringSystemGestures];
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
    if (@available(iOS 27.1, *)) {
        [self setNeedsUpdateOfVerticalBarConfiguration];
    }
#endif
}

#pragma mark - Layout

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [self layoutSides];
}

- (void)layoutSides {
    if (!self.isViewLoaded) {
        return;
    }
    CGRect bounds = self.view.bounds;
    CGFloat height = CGRectGetHeight(bounds);
    CGFloat screenWidth = CGRectGetWidth(bounds);
    ANNSideMenuSide side = _side;
    ANNSideMenuSideState *state = [self stateOf:side];
    CGFloat width = MIN(state.width, screenWidth);
    CGFloat shown = MIN(_progress, 1);
    BOOL left = side != ANNSideMenuSideRight;
    BOOL push = state.openMode == ANNSideMenuOpenModePushContent;

    // Pushed aside, the center also follows a pan past the width.
    CGRect center = bounds;
    if (side != ANNSideMenuSideNone && push) {
        center.origin.x = (left ? 1 : -1) * width * MIN(_progress, ANNSideMenuMaxStretch);
    }
    _centerContainer.frame = center;
    _centerContainer.layer.shadowOpacity = side != ANNSideMenuSideNone && push && shown > 0 ? 0.8 : 0;
    _centerContainer.layer.shadowRadius = 10;
    _centerContainer.layer.shadowOffset = CGSizeMake(0, -3);
    _centerContainer.layer.shadowPath = [UIBezierPath bezierPathWithRect:_centerContainer.bounds].CGPath;
    _shield.backgroundColor =
        [UIColor colorWithWhite:0 alpha:push ? 0 : ANNSideMenuDimAlpha * shown];

    for (NSNumber *each in @[ @(ANNSideMenuSideLeft), @(ANNSideMenuSideRight) ]) {
        ANNSideMenuSide menuSide = (ANNSideMenuSide)each.integerValue;
        UIView *container = [self containerOf:menuSide];
        if (menuSide != side) {
            container.hidden = YES;
            continue;
        }
        CGFloat x = left ? 0 : screenWidth - width;
        if (!push) {
            x = left ? width * (shown - 1) : screenWidth - width * shown;
        }
        // Bounds and center hold under any transform, so the transform is set only once: in an animation, a step
        // through the identity would be where a 3D turn (`door`) starts from.
        container.bounds = (CGRect){container.bounds.origin, CGSizeMake(width, height)};
        container.center = CGPointMake(x + width / 2, height / 2);
        container.layer.transform = [self transformOfSide:menuSide width:width push:push];
        container.alpha = push && [_animationType isEqualToString:@"slide-and-scale"] ? shown : 1;
    }
}

// Under the center the menu appears as `animationType` has it; a pan past the width stretches it
// from its outer edge.
- (CATransform3D)transformOfSide:(ANNSideMenuSide)side width:(CGFloat)width push:(BOOL)push {
    BOOL left = side == ANNSideMenuSideLeft;
    CGFloat direction = left ? 1 : -1;
    if (_progress > 1) {
        CGFloat stretch = MIN(_progress, ANNSideMenuMaxStretch);
        return CATransform3DConcat(
            CATransform3DMakeScale(stretch, 1, 1),
            CATransform3DMakeTranslation(direction * width * (stretch - 1) / 2, 0, 0));
    }
    if (!push || !_animationType) {
        return CATransform3DIdentity;
    }
    CGFloat hidden = 1 - _progress;
    if ([_animationType isEqualToString:@"parallax"]) {
        return CATransform3DMakeTranslation(-direction * width / 2 * hidden, 0, 0);
    }
    if ([_animationType isEqualToString:@"slide"]) {
        return CATransform3DMakeTranslation(-direction * width * hidden, 0, 0);
    }
    if ([_animationType isEqualToString:@"slide-and-scale"]) {
        CGFloat scale = 0.9 + 0.1 * _progress;
        return CATransform3DConcat(CATransform3DMakeScale(scale, scale, 1),
                                   CATransform3DMakeTranslation(direction * 50 * hidden, 0, 0));
    }
    if ([_animationType isEqualToString:@"door"]) {
        // The edge next to the center is the hinge, and it moves with the center.
        CATransform3D perspective = CATransform3DIdentity;
        perspective.m34 = -1.0 / 1000;
        CATransform3D toHinge = CATransform3DMakeTranslation(-direction * width / 2, 0, 0);
        CATransform3D fromHinge = CATransform3DMakeTranslation(direction * width / 2, 0, 0);
        CATransform3D rotate = CATransform3DRotate(perspective, direction * -M_PI_2 * hidden, 0, 1, 0);
        CATransform3D follow = CATransform3DMakeTranslation(-direction * width * hidden, 0, 0);
        return CATransform3DConcat(
            CATransform3DConcat(CATransform3DConcat(toHinge, rotate), fromHinge), follow);
    }
    return CATransform3DIdentity;
}

- (void)resetVisualStateOf:(ANNSideMenuSide)side {
    UIView *container = [self containerOf:side];
    container.layer.transform = CATransform3DIdentity;
    container.alpha = 1;
}

#pragma mark - Appearance

// The screens of the side menu appear with it; the screens of a menu also when the menu opens and
// closes.
- (BOOL)shouldAutomaticallyForwardAppearanceMethods {
    return NO;
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    _appeared = YES;
    [self.center beginAppearanceTransition:YES animated:animated];
    if (_side != ANNSideMenuSideNone) {
        [self side:_side willBeVisible:YES animated:animated];
    }
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [self.center endAppearanceTransition];
    if (_side != ANNSideMenuSideNone) {
        [self side:_side transitionEnded:YES];
    }
    if (!_firstAppeared) {
        _firstAppeared = YES;
        if (_attachMode == ANNSideMenuAttachModeAfterCenter) {
            [self attachMenu:ANNSideMenuSideLeft];
            [self attachMenu:ANNSideMenuSideRight];
        }
    }
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [self.center beginAppearanceTransition:NO animated:animated];
    if (_side != ANNSideMenuSideNone) {
        [self side:_side willBeVisible:NO animated:animated];
    }
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [self.center endAppearanceTransition];
    if (_side != ANNSideMenuSideNone) {
        [self side:_side transitionEnded:NO];
    }
    _appeared = NO;
}

- (void)side:(ANNSideMenuSide)side willBeVisible:(BOOL)visible animated:(BOOL)animated {
    ANNSideMenuSideState *state = [self stateOf:side];
    UIViewController *menu = [self menuOf:side];
    if (!menu || !_appeared) {
        return;
    }
    // The state first: the screens apply their options as they appear, which can get here again.
    if (visible && (state.appearance == ANNSideMenuAppearanceHidden ||
                    state.appearance == ANNSideMenuAppearanceDisappearing)) {
        state.appearance = ANNSideMenuAppearanceAppearing;
        [menu beginAppearanceTransition:YES animated:animated];
    } else if (!visible && (state.appearance == ANNSideMenuAppearanceShown ||
                            state.appearance == ANNSideMenuAppearanceAppearing)) {
        state.appearance = ANNSideMenuAppearanceDisappearing;
        [menu beginAppearanceTransition:NO animated:animated];
    }
}

- (void)side:(ANNSideMenuSide)side transitionEnded:(BOOL)visible {
    ANNSideMenuSideState *state = [self stateOf:side];
    UIViewController *menu = [self menuOf:side];
    if (!menu) {
        return;
    }
    if (state.appearance == ANNSideMenuAppearanceAppearing ||
        state.appearance == ANNSideMenuAppearanceDisappearing) {
        state.appearance =
            visible ? ANNSideMenuAppearanceShown : ANNSideMenuAppearanceHidden;
        [menu endAppearanceTransition];
    }
}

#pragma mark - Gestures

- (void)handleTap:(UITapGestureRecognizer *)tap {
    if (!_animating && _side != ANNSideMenuSideNone) {
        [self animateSide:_side open:NO velocity:0];
    }
}

- (void)handlePan:(UIPanGestureRecognizer *)pan {
    switch (pan.state) {
    case UIGestureRecognizerStateBegan:
        if (_side == ANNSideMenuSideNone) {
            CGFloat dx = [pan velocityInView:self.view].x;
            [self show:dx > 0 ? ANNSideMenuSideLeft : ANNSideMenuSideRight];
        }
        _panStartProgress = _progress;
        break;
    case UIGestureRecognizerStateChanged: {
        CGFloat dx = [pan translationInView:self.view].x;
        ANNSideMenuSideState *state = [self stateOf:_side];
        CGFloat progress =
            _panStartProgress + (_side == ANNSideMenuSideLeft ? dx : -dx) / state.width;
        CGFloat maximum = state.shouldStretch ? ANNSideMenuMaxStretch : 1;
        // Past the width the menu follows the finger more slowly.
        if (progress > 1) {
            progress = 1 + (progress - 1) / 4;
        }
        _progress = MAX(0, MIN(progress, maximum));
        [self layoutSides];
        break;
    }
    case UIGestureRecognizerStateEnded:
    case UIGestureRecognizerStateCancelled: {
        CGFloat velocity =
            [pan velocityInView:self.view].x * (_side == ANNSideMenuSideLeft ? 1 : -1);
        BOOL open = fabs(velocity) > ANNSideMenuFlickVelocity ? velocity > 0 : _progress > 0.5;
        if (pan.state == UIGestureRecognizerStateCancelled) {
            open = self.openSide != ANNSideMenuSideNone;
        }
        [self animateSide:_side open:open velocity:fabs(velocity)];
        break;
    }
    default:
        break;
    }
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
       shouldReceiveTouch:(UITouch *)touch {
    if (_animating) {
        return NO;
    }
    if (gestureRecognizer != _pan) {
        return YES;
    }
    _touchView = nil;
    // A menu on screen closes with a pan on it or on the center.
    if (_side != ANNSideMenuSideNone) {
        return YES;
    }
    _touchStart = [touch locationInView:self.view];
    if (![self canOpen:ANNSideMenuSideLeft] && ![self canOpen:ANNSideMenuSideRight]) {
        return NO;
    }
    if ([self isSwipeBackTouch:touch]) {
        return NO;
    }
    _touchView = touch.view;
    return YES;
}

- (BOOL)gestureRecognizerShouldBegin:(UIGestureRecognizer *)gestureRecognizer {
    if (gestureRecognizer != _pan) {
        return YES;
    }
    CGPoint velocity = [_pan velocityInView:self.view];
    // A vertical pan scrolls.
    if (fabs(velocity.x) <= fabs(velocity.y)) {
        return NO;
    }
    if (_side != ANNSideMenuSideNone) {
        return YES;
    }
    if (@available(iOS 26.0, *)) {
        if ([self isContentSwipeBack:_pan]) {
            return NO;
        }
    }
    // A carousel or a slider under the finger moves first while it can; at its edge the menu opens.
    UIScrollView *scrollView = [self horizontalScrollViewUnderTouch];
    if (scrollView && ANNScrollViewCanScroll(scrollView, [_pan velocityInView:scrollView].x)) {
        return NO;
    }
    return [self canOpen:velocity.x > 0 ? ANNSideMenuSideLeft : ANNSideMenuSideRight];
}

// The pan of that scroll view waits for the menu's decision: otherwise, bouncing at its edge, it would begin first and
// the menu would never open there. Only ever asked of the menu's own pan; the scroll view stays as it is.
- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
    shouldBeRequiredToFailByGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer {
    if (gestureRecognizer != _pan) {
        return NO;
    }
    UIScrollView *scrollView = [self horizontalScrollViewUnderTouch];
    return scrollView && otherGestureRecognizer == scrollView.panGestureRecognizer;
}

// With `openGestureMode: 'entireScreen'` and no menu on screen: the nearest scroll view that moves sideways on the way
// from the touch up to the side menu's view. Read only.
- (UIScrollView *)horizontalScrollViewUnderTouch {
    if (_openGestureMode != ANNSideMenuOpenGestureModeEntireScreen || _side != ANNSideMenuSideNone) {
        return nil;
    }
    UIView *root = self.viewIfLoaded;
    for (UIView *view = _touchView; view && view != root; view = view.superview) {
        if ([view isKindOfClass:UIScrollView.class] && ANNScrollsHorizontally((UIScrollView *)view)) {
            return (UIScrollView *)view;
        }
    }
    return nil;
}

// A pan from where it started opens the menu: its edge, or anywhere on the center with
// `openGestureMode: 'entireScreen'`.
- (BOOL)canOpen:(ANNSideMenuSide)side {
    if (![self menuOf:side] || ![self stateOf:side].enabled) {
        return NO;
    }
    if (_openGestureMode == ANNSideMenuOpenGestureModeEntireScreen) {
        return YES;
    }
    CGFloat width = CGRectGetWidth(self.view.bounds);
    return side == ANNSideMenuSideLeft ? _touchStart.x <= ANNSideMenuEdgeWidth
                                       : _touchStart.x >= width - ANNSideMenuEdgeWidth;
}

// A swipe from the leading edge of a stack that can go back is the stack's swipe back, not the menu's: the menu
// opens from that edge only at the root of the stack or with popGesture: false.
- (BOOL)isSwipeBackTouch:(UITouch *)touch {
    UINavigationController *stack = ANNStackOfResponder(touch.view);
    UIGestureRecognizer *swipeBack = stack.interactivePopGestureRecognizer;
    if (!swipeBack.enabled || stack.viewControllers.count < 2) {
        return NO;
    }

    // The menu's own edge zone, on the side the stack goes back from.
    BOOL rightToLeft =
        stack.view.effectiveUserInterfaceLayoutDirection == UIUserInterfaceLayoutDirectionRightToLeft;
    CGFloat x = [touch locationInView:self.view].x;
    CGFloat edge = ANNSideMenuEdgeWidth;
    if (rightToLeft ? x < CGRectGetWidth(self.view.bounds) - edge : x > edge) {
        return NO;
    }

    // Whether the swipe back takes the touch: popGesture, the stack's own checks.
    id<UIGestureRecognizerDelegate> delegate = swipeBack.delegate;
    return ![delegate respondsToSelector:@selector(gestureRecognizer:shouldReceiveTouch:)] ||
           [delegate gestureRecognizer:swipeBack shouldReceiveTouch:touch];
}

// On iOS 26 a stack also goes back with a swipe from anywhere in its content: a pan toward the back direction over a
// stack that can go back is that swipe, not the menu's. The menu still opens that way at the root of the stack or with
// popGesture: false, and toward the other direction.
- (BOOL)isContentSwipeBack:(UIPanGestureRecognizer *)pan API_AVAILABLE(ios(26.0)) {
    UIView *view = [self.view hitTest:[pan locationInView:self.view] withEvent:nil];
    UINavigationController *stack = ANNStackOfResponder(view);
    UIGestureRecognizer *swipeBack = stack.interactiveContentPopGestureRecognizer;
    if (!swipeBack.enabled || stack.viewControllers.count < 2) {
        return NO;
    }
    id<UIGestureRecognizerDelegate> delegate = swipeBack.delegate;
    if ([delegate isKindOfClass:InteractivePopGestureDelegate.class] &&
        !((InteractivePopGestureDelegate *)delegate).enabled) {
        return NO;
    }
    BOOL rightToLeft =
        stack.view.effectiveUserInterfaceLayoutDirection == UIUserInterfaceLayoutDirectionRightToLeft;
    CGFloat dx = [pan velocityInView:self.view].x;
    return rightToLeft ? dx < 0 : dx > 0;
}

#pragma mark - Layout protocol

- (NSArray<UIViewController *> *)layoutChildren {
    return _sideMenuChildren ?: @[];
}

// The center stays on screen under an open menu: both are shown, the center first.
- (NSArray<UIViewController *> *)shownComponentViewControllers {
    NSMutableArray<UIViewController *> *shown =
        [NSMutableArray arrayWithArray:self.center.shownComponentViewControllers ?: @[]];
    UIViewController *current = self.getCurrentChild;
    if (current != self.center) {
        [shown addObjectsFromArray:current.shownComponentViewControllers ?: @[]];
    }
    return shown;
}

- (UIViewController *)getCurrentChild {
    switch (self.openSide) {
    case ANNSideMenuSideLeft:
        return self.left;
    case ANNSideMenuSideRight:
        return self.right;
    default:
        return self.center;
    }
}

- (ANNNavigationOptions *)resolveOptions {
    ANNNavigationOptions *result = [self.nodeHelper resolveOptions];
    if (self.getCurrentChild != self.center) {
        [result.sideMenu mergeOptions:self.center.resolveOptions.sideMenu];
    }
    return result;
}

#pragma mark - UIViewController

- (UIStatusBarStyle)preferredStatusBarStyle {
    return [self.presenter statusBarStyleForOptions:self.resolveOptionsWithDefault];
}

// The open menu or the center.
- (UIViewController *)childViewControllerForHomeIndicatorAutoHidden {
    return self.getCurrentChild;
}

- (UIViewController *)childViewControllerForScreenEdgesDeferringSystemGestures {
    return self.getCurrentChild;
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
// `layout.verticalBars` of the open menu or the center: UIKit's own containers pass the question to their content.
- (UIViewController *)childViewControllerForPreferredVerticalBarBehavior API_AVAILABLE(ios(27.1)) {
    return self.getCurrentChild;
}
#endif

- (BOOL)prefersStatusBarHidden {
    return [self.presenter statusBarHiddenForOptions:self.resolveOptionsWithDefault];
}

- (UIInterfaceOrientationMask)supportedInterfaceOrientations {
    return [self.presenter supportedOrientationsForOptions:self.resolveOptionsWithDefault];
}

- (BOOL)hidesBottomBarWhenPushed {
    return [self.presenter hidesBottomBarWhenPushedForOptions:self.resolveOptionsWithDefault];
}

@end

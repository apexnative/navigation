#import "ANNBottomTabsCustomRow.h"
#import "ANNBottomTabsController.h"
#import "ANNTabsDesign.h"

// The capsule of iOS 26: its height, in a compact height, and its margins.
static const CGFloat ANNCapsuleHeight = 62;
static const CGFloat ANNCapsuleCompactHeight = 44;
static const CGFloat ANNCapsuleMargin = 21;
// The row of a wide iPad or Mac on iOS 26: over the system bar's height, its radius and margins.
static const CGFloat ANNWideRowExtraHeight = 18;
static const CGFloat ANNWideRowRadius = 28;
static const CGFloat ANNWideRowMargin = 16;

@implementation ANNBottomTabsCustomRow {
    __weak ANNBottomTabsController *_tabs;
    ANNBottomTabsCustomRowOptions *_options;
    // The background (an effect, a color or nothing) with the cells in it.
    UIView *_background;
    UIView *_cellHost;
    NSString *_backgroundKind;
    CGFloat _extraSafeArea;
}

- (instancetype)initWithTabs:(ANNBottomTabsController *)tabs {
    if (self = [super initWithFrame:CGRectZero]) {
        _tabs = tabs;
        _options = [ANNBottomTabsCustomRowOptions new];
        self.backgroundColor = UIColor.clearColor;
        NSMutableArray<ANNBottomTabCellView *> *cells = [NSMutableArray new];
        for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
            ANNBottomTabCellView *cell = [[ANNBottomTabCellView alloc] initWithIndex:index];
            UITapGestureRecognizer *tap =
                [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(cellTapped:)];
            [cell addGestureRecognizer:tap];
            UILongPressGestureRecognizer *longPress =
                [[UILongPressGestureRecognizer alloc] initWithTarget:self
                                                              action:@selector(cellLongPressed:)];
            [cell addGestureRecognizer:longPress];
            [tap requireGestureRecognizerToFail:longPress];
            [cells addObject:cell];
        }
        _cells = cells;
        [self makeBackground];
    }
    return self;
}

#pragma mark - Options (`bottomTabs.customRow.*`)

- (void)applyOptions:(ANNBottomTabsCustomRowOptions *)options {
    _options = [options copy] ?: [ANNBottomTabsCustomRowOptions new];
    NSString *testID = [_options.testID withDefault:nil];
    testID = testID.length > 0 ? testID : nil;
    if (![self.accessibilityIdentifier ?: @"" isEqualToString:testID ?: @""]) {
        self.accessibilityIdentifier = testID;
    }
    [self makeBackground];
    [self layOutInTabs];
}

// `backgroundColor` over `backgroundEffect`; `glass` (iOS 26, a blur before), `blur`, `none`; by default the glass on
// iOS 26 and a blur before. A `null` color is no color: the effect decides.
- (void)makeBackground {
    UIColor *color = [_options.backgroundColor withDefault:nil];
    NSString *effect = [_options.backgroundEffect withDefault:nil];
    NSString *kind = color ? @"color" : (effect ?: @"default");
    if ([kind isEqualToString:_backgroundKind]) {
        _background.backgroundColor = color ?: _background.backgroundColor;
        return;
    }
    _backgroundKind = kind;
    UIView *background = nil;
    UIView *host = nil;
    if (color) {
        background = [UIView new];
        background.backgroundColor = color;
        host = background;
    } else if ([effect isEqualToString:@"none"]) {
        background = [UIView new];
        background.backgroundColor = UIColor.clearColor;
        host = background;
    } else {
        UIVisualEffect *visualEffect =
            [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemChromeMaterial];
        if (@available(iOS 26.0, *)) {
            if (![effect isEqualToString:@"blur"]) {
                visualEffect = [UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular];
            }
        }
        UIVisualEffectView *effectView = [[UIVisualEffectView alloc] initWithEffect:visualEffect];
        background = effectView;
        host = effectView.contentView;
    }
    background.clipsToBounds = YES;
    background.autoresizingMask =
        UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    background.frame = self.bounds;
    [_background removeFromSuperview];
    _background = background;
    _cellHost = host;
    [self addSubview:background];
    for (ANNBottomTabCellView *cell in _cells) {
        [host addSubview:cell];
    }
    [self setNeedsLayout];
}

#pragma mark - The place

- (BOOL)isShown {
    return self.superview != nil && !self.hidden;
}

// iOS 18 and later, an iPad or a Mac in a regular width: the system's bar would be on top.
- (BOOL)isWide {
    return [ANNTabsDesign barIsOnTopIn:_tabs.traitCollection];
}

- (void)layOutInTabs {
    ANNBottomTabsController *tabs = _tabs;
    UIView *host = tabs.viewIfLoaded;
    UITabBar *bar = tabs.tabBar;
    if (!tabs || !host || !bar) {
        return;
    }
    if (self.superview != host) {
        [host addSubview:self];
    }
    if (host.subviews.lastObject != self) {
        [host bringSubviewToFront:self];
    }
    // The system bar keeps its place and shows nothing; UIKit makes its subviews anew on a size change. Not the bar's
    // `alpha`: UIKit puts it back to 1 when the bar shows, on a pop with `hidesBottomBarWhenPushed`, and on iOS 27 on
    // a rotation and a theme change, after the tabs' layout (a check here misses it); alpha 0 also drops the system's
    // soft fade at the bottom.
    for (UIView *subview in bar.subviews) {
        if (!subview.hidden) {
            subview.hidden = YES;
        }
    }
    BOOL wide = [self isWide];
    if (wide != _keepsVisibility) {
        // `bottomTabs.visible` while narrow is the system bar's state: the row keeps it as it takes over.
        if (wide) {
            _hiddenByOptions = tabs.barHidden;
        }
        _keepsVisibility = wide;
        if (@available(iOS 18.0, *)) {
            // The floating top bar hides while the row is in use; a narrow window gets the bar back by the options.
            [tabs setTabBarHidden:wide || _hiddenByOptions animated:NO];
        }
    }
    UIWindow *window = host.window;
    CGFloat windowLine = host.bounds.size.height;
    if (window) {
        CGPoint line = CGPointMake(0, window.bounds.size.height - window.safeAreaInsets.bottom);
        windowLine = [window convertPoint:line toView:host].y;
    }
    CGRect barFrame = [bar.superview convertRect:bar.frame toView:host];
    ANNBottomTabsCustomRowOptions *options = _options;
    CGFloat bottomMargin = [options.bottomMargin withDefault:0];
    CGFloat height = 0;
    CGFloat margin = 0;
    CGFloat radius = 0;
    CGFloat bottom = 0;
    CGFloat left = barFrame.origin.x;
    CGFloat width = barFrame.size.width;
    CGFloat extra = 0;
    BOOL glass = ANNTabsDesign.isGlass;
    if (wide) {
        CGFloat barHeight = bar.bounds.size.height - bar.safeAreaInsets.bottom;
        height = options.height.hasValue ? options.height.get
                                         : barHeight + (glass ? ANNWideRowExtraHeight : 0);
        margin = [options.horizontalMargin withDefault:glass ? ANNWideRowMargin : 0];
        radius = [options.cornerRadius withDefault:glass ? ANNWideRowRadius : 0];
        bottom = windowLine - bottomMargin;
        left = 0;
        width = host.bounds.size.width;
        extra = height + bottomMargin;
    } else if (glass) {
        BOOL compact = tabs.traitCollection.verticalSizeClass == UIUserInterfaceSizeClassCompact;
        CGFloat capsule =
            MIN(compact ? ANNCapsuleCompactHeight : ANNCapsuleHeight, barFrame.size.height);
        height = options.height.hasValue ? options.height.get : capsule;
        margin = [options.horizontalMargin withDefault:ANNCapsuleMargin];
        bottom = CGRectGetMinY(barFrame) + capsule - bottomMargin;
        radius = [options.cornerRadius withDefault:height / 2];
    } else {
        CGFloat barHeight = bar.bounds.size.height - bar.safeAreaInsets.bottom;
        height = options.height.hasValue ? options.height.get : barHeight;
        margin = [options.horizontalMargin withDefault:0];
        radius = [options.cornerRadius withDefault:0];
        bottom = windowLine - bottomMargin;
    }
    CGRect frame =
        CGRectMake(left + margin, bottom - height, MAX(0, width - 2 * margin), MAX(0, height));
    if (!wide) {
        extra = MAX(0, CGRectGetMinY(barFrame) - CGRectGetMinY(frame));
    }
    BOOL hidden = wide ? _hiddenByOptions : (tabs.barHidden || bar.hidden);
    // A new size lays the cells out again by itself; the same frame changes nothing.
    if (!CGRectEqualToRect(self.frame, frame)) {
        self.frame = frame;
    }
    if (self.hidden != hidden) {
        self.hidden = hidden;
    }
    if (_background.layer.cornerRadius != radius) {
        _background.layer.cornerRadius = radius;
    }
    [self setExtraSafeArea:hidden ? 0 : extra];
}

- (void)setHiddenByOptions:(BOOL)hiddenByOptions {
    _hiddenByOptions = hiddenByOptions;
    [self layOutInTabs];
}

// The tabs' children keep the room the row covers above the system bar; none without it.
- (void)setExtraSafeArea:(CGFloat)extra {
    if (_extraSafeArea == extra) {
        return;
    }
    _extraSafeArea = extra;
    for (UIViewController *tab in _tabs.layoutTabs) {
        UIEdgeInsets insets = tab.additionalSafeAreaInsets;
        if (insets.bottom != extra) {
            insets.bottom = extra;
            tab.additionalSafeAreaInsets = insets;
        }
    }
}

- (void)layoutSubviews {
    [super layoutSubviews];
    UIView *host = _cellHost;
    NSUInteger count = _cells.count;
    if (!host || count == 0) {
        return;
    }
    CGSize size = host.bounds.size;
    CGFloat cellWidth = size.width / count;
    for (NSUInteger index = 0; index < count; index++) {
        CGRect frame = CGRectMake(cellWidth * index, 0, cellWidth, size.height);
        ANNBottomTabCellView *cell = _cells[index];
        if (!CGRectEqualToRect(cell.frame, frame)) {
            cell.frame = frame;
        }
    }
}

- (void)removeFromTabs {
    [self setExtraSafeArea:0];
    ANNBottomTabsController *tabs = _tabs;
    if (_keepsVisibility) {
        _keepsVisibility = NO;
        if (@available(iOS 18.0, *)) {
            [tabs setTabBarHidden:_hiddenByOptions animated:NO];
        }
    }
    [self removeFromSuperview];
}

#pragma mark - Presses

- (void)cellTapped:(UITapGestureRecognizer *)recognizer {
    UIView *cell = recognizer.view;
    if (recognizer.state != UIGestureRecognizerStateEnded ||
        ![cell isKindOfClass:ANNBottomTabCellView.class]) {
        return;
    }
    [_tabs pressTabAtIndex:((ANNBottomTabCellView *)cell).index source:ANNTabPressSourceRow];
}

- (void)cellLongPressed:(UILongPressGestureRecognizer *)recognizer {
    UIView *cell = recognizer.view;
    if (![cell isKindOfClass:ANNBottomTabCellView.class]) {
        return;
    }
    if (recognizer.state == UIGestureRecognizerStateBegan) {
        [_tabs longPressTabAtIndex:((ANNBottomTabCellView *)cell).index marksSelection:NO];
    }
}

@end

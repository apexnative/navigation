#import "ANNLargeTitlePresenter.h"
#import "ANNAppDirection.h"
#import "ANNBarAppearance.h"
#import "ANNMainList.h"
#import "ANNScreenBars.h"

// How far the color reaches above the list's content: as far as a pull can show.
static const CGFloat ANNListFillHeight = 2000;

// How far the bar's room may be below the room noted for an expanded title and still count as expanded.
static const CGFloat ANNExpandedRoomTolerance = 0.5;

static UINavigationItemLargeTitleDisplayMode ANNItemModeOf(NSString *mode) {
    if ([mode isEqualToString:@"always"]) {
        return UINavigationItemLargeTitleDisplayModeAlways;
    }
    if ([mode isEqualToString:@"automatic"]) {
        return UINavigationItemLargeTitleDisplayModeAutomatic;
    }
    if ([mode isEqualToString:@"inline"]) {
        if (@available(iOS 17.0, *)) {
            return UINavigationItemLargeTitleDisplayModeInline;
        }
        return UINavigationItemLargeTitleDisplayModeAlways;
    }
    return UINavigationItemLargeTitleDisplayModeNever;
}

@implementation ANNLargeTitlePresenter {
    __weak UIViewController *_screen;
    BOOL _appeared;
    UIScrollView *_emptyScrollView;
    // The core told the bar which scroll view to follow at the top edge: only then it gives that choice back.
    BOOL _setsTopEdge;
    UIView *_listFill;
    UIView *_titlePlaceFill;
    // The push from a lower bar whose fill is pending: the old bar's bottom in the screen's coordinates.
    BOOL _titlePlacePending;
    CGFloat _oldBarBottom;
    // The place of the title while a pop reveals the screen, and whether the bar follows it now; for a push, the bar
    // follows it until the screen appeared, also with a main list.
    UIScrollView *_titleHost;
    BOOL _hostsTitle;
    BOOL _hostsPushTitle;
    // The largest room the bar took over the content while the screen showed at rest: that of an expanded title.
    CGFloat _expandedRoom;
}

- (instancetype)initWithScreen:(UIViewController *)screen {
    if (self = [super init]) {
        _screen = screen;
    }
    return self;
}

+ (BOOL)collapsesIntoList:(ANNNavigationOptions *)options {
    if (!ANNLargeTitleCanShow(options) ||
        ![options.topBar.largeTitle.collapseOnScroll withDefault:YES]) {
        return NO;
    }
    if (@available(iOS 26.0, *)) {
        if (ANNAppDirection.reactNativeIsRightToLeft) {
            return NO;
        }
    }
    return YES;
}

- (void)applyMode:(ANNNavigationOptions *)options shownTop:(BOOL)shownTop {
    UIViewController *screen = _screen;
    UINavigationItem *item = screen.navigationItem;
    UINavigationItemLargeTitleDisplayMode mode = ANNItemModeOf(ANNLargeTitleDisplayMode(options));
    if (item.largeTitleDisplayMode == mode) {
        return;
    }
    BOOL turnsOn = item.largeTitleDisplayMode == UINavigationItemLargeTitleDisplayModeNever &&
                   mode != UINavigationItemLargeTitleDisplayModeNever;
    item.largeTitleDisplayMode = mode;
    // UIKit shows a large title turned on for a shown screen only once the bar sized itself.
    if (turnsOn && shownTop) {
        [screen.navigationController.navigationBar sizeToFit];
    }
}

- (void)applyCollapse:(ANNNavigationOptions *)options list:(UIScrollView *)list {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    BOOL canShow = ANNLargeTitleCanShow(options);
    BOOL collapses = [ANNLargeTitlePresenter collapsesIntoList:options];
    UIScrollView *followed = nil;
    if (canShow && !collapses && _appeared) {
        if (!_emptyScrollView) {
            _emptyScrollView = [UIScrollView new];
        }
        followed = _emptyScrollView;
    } else if (collapses) {
        followed = list;
    }
    if (followed != _emptyScrollView) {
        _emptyScrollView = nil;
    }
    // The place of a pop's title holds while the title can show and no main list at the top takes it over; that of a
    // push, while the title can show.
    BOOL dropsHost = NO;
    if (_hostsTitle) {
        if (canShow && (_hostsPushTitle || !collapses || !list)) {
            followed = _titleHost;
        } else {
            _hostsTitle = NO;
            _hostsPushTitle = NO;
            dropsHost = YES;
        }
    }
    if (followed) {
        if ([screen contentScrollViewForEdge:NSDirectionalRectEdgeTop] != followed) {
            [screen setContentScrollView:followed forEdge:NSDirectionalRectEdgeTop];
        }
        _setsTopEdge = YES;
    } else if (_setsTopEdge) {
        // The bar follows the list by itself again.
        [screen setContentScrollView:nil forEdge:NSDirectionalRectEdgeTop];
        _setsTopEdge = NO;
    }
    // Out of the screen only once the bar follows something else.
    if (dropsHost) {
        [_titleHost removeFromSuperview];
    }
}

- (void)screenInPop:(ANNNavigationOptions *)options
               list:(UIScrollView *)list
        inlineInRow:(BOOL)inlineInRow {
    if (@available(iOS 26.0, *)) {
        UIViewController *screen = _screen;
        UIView *screenView = screen.viewIfLoaded;
        UIView *stackView = screen.navigationController.viewIfLoaded;
        // A screen covered while the window turned or resized keeps the safe area of the old size until UIKit lays it
        // out for the transition: its top would put the title at the old height.
        if (stackView && !CGSizeEqualToSize(screenView.bounds.size, stackView.bounds.size)) {
            return;
        }
        BOOL collapses = [ANNLargeTitlePresenter collapsesIntoList:options];
        // The bar would follow the empty scroll view, in no window, or with no main list at the screen's top a list
        // UIKit picks itself, below the bar, which clips the title. A title in the bar's row is in neither.
        BOOL titleLost = (!collapses && _appeared) || !list;
        if (!screenView || inlineInRow || !ANNLargeTitleCanShow(options) || !titleLost) {
            return;
        }
        // UIKit has not laid the screen out for the transition yet: its safe area is still the one it had when it
        // was covered.
        CGFloat top = screenView.safeAreaInsets.top;
        // A title that folds into a list UIKit follows shows collapsed when that list was scrolled: UIKit goes on so.
        // A screen without the bar's room over its content has no place for a title either.
        CGFloat room = top - stackView.safeAreaInsets.top;
        if (room <= 0 || (collapses && ![self roomIsExpanded:room])) {
            return;
        }
        [self placeTitleHostIn:screenView top:top];
        _hostsTitle = YES;
        [self applyCollapse:options list:list];
    }
}

- (void)screenInPush:(ANNNavigationOptions *)options
                list:(UIScrollView *)list
         inlineInRow:(BOOL)inlineInRow
                 top:(CGFloat)top
            stackTop:(CGFloat)stackTop {
    if (@available(iOS 26.0, *)) {
        UIView *screenView = _screen.viewIfLoaded;
        if (!screenView || inlineInRow || !ANNLargeTitleCanShow(options) || top - stackTop <= 0) {
            return;
        }
        [self placeTitleHostIn:screenView top:top];
        _hostsTitle = YES;
        _hostsPushTitle = YES;
        [self applyCollapse:options list:list];
    }
}

// A plain scroll view over the whole screen, at rest at its top under the bar (its content starts at `top`, where the
// content of the screen started as it was covered): UIKit puts the title just above that start, where the bar's title
// is at rest. It takes no touches and draws nothing itself; the core's search of the screen's list skips it.
- (void)placeTitleHostIn:(UIView *)screenView top:(CGFloat)top {
    UIScrollView *host = _titleHost;
    if (!host) {
        host = [UIScrollView new];
        host.userInteractionEnabled = NO;
        host.backgroundColor = UIColor.clearColor;
        host.clipsToBounds = NO;
        host.scrollsToTop = NO;
        host.showsVerticalScrollIndicator = NO;
        host.showsHorizontalScrollIndicator = NO;
        host.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever;
        host.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
        [ANNMainList excludeView:host];
        _titleHost = host;
    }
    CGSize size = screenView.bounds.size;
    host.frame = CGRectMake(0, 0, size.width, size.height);
    host.contentInset = UIEdgeInsetsMake(top, 0, 0, 0);
    host.contentSize = CGSizeMake(size.width, 1);
    host.contentOffset = CGPointMake(0, -top);
    if (host.superview != screenView) {
        [host removeFromSuperview];
        [screenView addSubview:host];
    } else if (screenView.subviews.lastObject != host) {
        [screenView bringSubviewToFront:host];
    }
}

// The bar goes straight from the place of the title to what it follows at rest: a step through no scroll view would
// let UIKit follow a scrolled list for one layout.
- (void)handBackTitle:(ANNNavigationOptions *)options list:(UIScrollView *)list {
    BOOL hosted = _hostsTitle;
    _hostsTitle = NO;
    _hostsPushTitle = NO;
    [self applyCollapse:options list:list];
    if (hosted) {
        [_titleHost removeFromSuperview];
    }
}

- (void)screenDidAppear:(ANNNavigationOptions *)options list:(UIScrollView *)list {
    _appeared = YES;
    [self handBackTitle:options list:list];
    [self noteTopAtRest];
}

- (void)screenDidDisappear:(ANNNavigationOptions *)options list:(UIScrollView *)list {
    [self popEnded:options list:list];
}

- (void)popEnded:(ANNNavigationOptions *)options list:(UIScrollView *)list {
    if (_hostsTitle) {
        [self handBackTitle:options list:list];
    }
}

- (BOOL)hostsTitle {
    return _hostsTitle;
}

// The room noted for an expanded title held a search field that hides on scroll too: that field may have gone with the
// title still expanded.
- (BOOL)roomIsExpanded:(CGFloat)room {
    CGFloat slack = ANNExpandedRoomTolerance;
    UINavigationItem *item = _screen.navigationItem;
    UISearchController *search = item.searchController;
    if (search && item.hidesSearchBarWhenScrolling) {
        BOOL stacked = YES;
        if (@available(iOS 16.0, *)) {
            stacked = item.searchBarPlacement == UINavigationItemSearchBarPlacementStacked;
        }
        if (stacked) {
            slack += search.searchBar.frame.size.height;
        }
    }
    return room >= _expandedRoom - slack;
}

- (void)noteTopAtRest {
    UIViewController *screen = _screen;
    UIView *screenView = screen.viewIfLoaded;
    UIView *stackView = screen.navigationController.viewIfLoaded;
    if (!screenView.window || !stackView) {
        return;
    }
    CGFloat room = screenView.safeAreaInsets.top - stackView.safeAreaInsets.top;
    _expandedRoom = MAX(_expandedRoom, room);
}

- (void)removeTitleHost {
    _hostsTitle = NO;
    _hostsPushTitle = NO;
    [_titleHost removeFromSuperview];
}

- (void)updateListFill:(ANNNavigationOptions *)options
                  list:(UIScrollView *)list
           inlineInRow:(BOOL)inlineInRow
   backgroundComponent:(BOOL)backgroundComponent {
    if (@available(iOS 26.0, *)) {
        UIViewController *screen = _screen;
        UIColor *fill = [ANNBarAppearance solidColorOf:options.topBar.background.color
                                                traits:screen.traitCollection];
        // A React background takes the same place under the rows; the color goes under it in the bar only.
        BOOL wanted = fill && list && !inlineInRow && !backgroundComponent &&
                      [ANNLargeTitlePresenter collapsesIntoList:options] &&
                      ![options.topBar.scrollEdgeAppearance.active withDefault:NO];
        if (!wanted) {
            [self removeListFill];
            return;
        }
        if (!_listFill) {
            _listFill = [UIView new];
            _listFill.userInteractionEnabled = NO;
            _listFill.autoresizingMask = UIViewAutoresizingFlexibleWidth;
        }
        _listFill.backgroundColor = fill;
        _listFill.frame =
            CGRectMake(0, -ANNListFillHeight, list.bounds.size.width, ANNListFillHeight);
        // Under the rows: the first view of the list.
        if (_listFill.superview != list) {
            [_listFill removeFromSuperview];
            [list insertSubview:_listFill atIndex:0];
        } else if (list.subviews.firstObject != _listFill) {
            [list sendSubviewToBack:_listFill];
        }
        return;
    }
}

- (void)removeListFill {
    [_listFill removeFromSuperview];
}

- (void)fillTitlePlaceFrom:(CGFloat)oldBarBottom
                      list:(UIScrollView *)list
                   options:(ANNNavigationOptions *)options {
    if (@available(iOS 26.0, *)) {
        [self removeTitlePlaceFill];
        if (!ANNLargeTitleCanShow(options) || [ANNLargeTitlePresenter collapsesIntoList:options]) {
            return;
        }
        _titlePlacePending = YES;
        _oldBarBottom = oldBarBottom;
        [self updateTitlePlaceFill:options list:list];
    }
}

// The pushed screen's content may render only during the push, so its list mounts later than the push starts: the
// fill is placed again at each mount and layout until the screen appeared.
- (void)updateTitlePlaceFill:(ANNNavigationOptions *)options list:(UIScrollView *)list {
    if (@available(iOS 26.0, *)) {
        UIViewController *screen = _screen;
        UIView *screenView = screen.viewIfLoaded;
        // Under the title wherever UIKit draws it: in the place of a pop's title while there is one.
        UIScrollView *place = _hostsTitle ? _titleHost : list;
        if (!_titlePlacePending || !place || !screenView) {
            return;
        }
        if (!ANNLargeTitleCanShow(options) || [ANNLargeTitlePresenter collapsesIntoList:options]) {
            [_titlePlaceFill removeFromSuperview];
            return;
        }
        // From the old bar's bottom to the first row (the content's origin), in the place's coordinates.
        CGFloat top = [screenView convertPoint:CGPointMake(0, _oldBarBottom) toView:place].y;
        CGFloat height = -top;
        if (height <= 0) {
            [_titlePlaceFill removeFromSuperview];
            return;
        }
        UIView *view = [self titlePlaceFillOf:options];
        view.frame = CGRectMake(0, top, place.bounds.size.width, height);
        if (view.superview != place) {
            [view removeFromSuperview];
            [place insertSubview:view atIndex:0];
        } else if (place.subviews.firstObject != view) {
            [place sendSubviewToBack:view];
        }
    }
}

// The view of the fill: `background.color`, or the bar's own background (the chrome material on a translucent bar,
// the opaque system color otherwise); the one made before while it is of the same kind.
- (UIView *)titlePlaceFillOf:(ANNNavigationOptions *)options API_AVAILABLE(ios(26.0)) {
    ANNTopBarBackgroundOptions *background = options.topBar.background;
    Color *color = background.color;
    UIColor *fill = color.hasValue && !color.isNoColor ? [color withDefault:nil] : nil;
    BOOL material = !fill && [background.translucent withDefault:YES];
    UIView *view = _titlePlaceFill;
    if (!view || [view isKindOfClass:UIVisualEffectView.class] != material) {
        [view removeFromSuperview];
        if (material) {
            UIBlurEffect *effect =
                [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemChromeMaterial];
            view = [[UIVisualEffectView alloc] initWithEffect:effect];
        } else {
            view = [UIView new];
        }
        view.userInteractionEnabled = NO;
        view.autoresizingMask = UIViewAutoresizingFlexibleWidth;
        _titlePlaceFill = view;
    }
    if (!material) {
        view.backgroundColor = fill ?: UIColor.systemBackgroundColor;
    }
    return view;
}

- (void)removeTitlePlaceFill {
    _titlePlacePending = NO;
    [_titlePlaceFill removeFromSuperview];
    _titlePlaceFill = nil;
}

- (void)fillTitlePlaceOfPopFrom:(ANNPopBar)bar
                           list:(UIScrollView *)list
                        options:(ANNNavigationOptions *)options {
    if (bar.bottom < 0) {
        return;
    }
    if (!_hostsTitle) {
        if (!bar.largeTitle) {
            [self fillTitlePlaceFrom:bar.bottom list:list options:options];
        }
        return;
    }
    // The bar's look is clear while the fill lies under the title: the fill is the bar's whole background then, from
    // the screen's top. The bar the pop starts from covers it until that one fades.
    CGFloat titleBottom = _titleHost.contentInset.top;
    BOOL lower = bar.bottom < titleBottom - ANNExpandedRoomTolerance;
    if (lower || (bar.largeTitle && bar.clearAtEdge)) {
        [self fillTitlePlaceFrom:0 list:list options:options];
    }
}

- (BOOL)drawsTitleBand {
    return _hostsTitle && _titleHost && _titlePlaceFill.superview == _titleHost;
}

@end

#import "ANNScreenPlace.h"
#import "ANNAssert.h"
#import "ANNBottomTabsController.h"
#import <objc/runtime.h>

static const void *ANNScreenPlaceKey = &ANNScreenPlaceKey;

// The measuring navigation controller of each stack: its first item stands for the stack's top, the second for the
// entry. Weak keys: it goes with its stack.
static NSMapTable<UINavigationController *, UINavigationController *> *ANNPlaceProbes(void) {
    static NSMapTable<UINavigationController *, UINavigationController *> *probes;
    if (!probes) {
        probes = [NSMapTable weakToStrongObjectsMapTable];
    }
    return probes;
}

static UINavigationController *ANNPlaceProbeOf(UINavigationController *stack) {
    UINavigationController *probe = [ANNPlaceProbes() objectForKey:stack];
    if (!probe) {
        probe = [[UINavigationController alloc] initWithRootViewController:[UIViewController new]];
        [probe pushViewController:[UIViewController new] animated:NO];
        [ANNPlaceProbes() setObject:probe forKey:stack];
    }
    return probe;
}

// What UIKit sizes the bar by, from the entry's item. The search field is a field of its own with the same placement:
// a search controller shows in one item only.
static void ANNCopyBarItem(UINavigationItem *from, UINavigationItem *to) {
    to.title = from.title;
    to.prompt = from.prompt;
    to.largeTitleDisplayMode = from.largeTitleDisplayMode;
    to.standardAppearance = from.standardAppearance;
    to.scrollEdgeAppearance = from.scrollEdgeAppearance;
    to.compactAppearance = from.compactAppearance;
    to.compactScrollEdgeAppearance = from.compactScrollEdgeAppearance;
    to.hidesSearchBarWhenScrolling = from.hidesSearchBarWhenScrolling;
    to.searchController =
        from.searchController ? [[UISearchController alloc] initWithSearchResultsController:nil] : nil;
    if (@available(iOS 16.0, *)) {
        to.style = from.style;
        to.preferredSearchBarPlacement = from.preferredSearchBarPlacement;
    }
    if (@available(iOS 26.0, *)) {
        to.attributedSubtitle = from.attributedSubtitle;
        to.largeAttributedSubtitle = from.largeAttributedSubtitle;
    }
}

// Everything the measure reads: the same answer while none of it changed.
static NSString *ANNPlaceSignature(UINavigationItem *item, UINavigationController *stack,
                                   UIView *host, BOOL showsTopBar, BOOL showsToolbar) {
    NSMutableString *signature = [NSMutableString
        stringWithFormat:@"%@|%@|%ld|%p|%p|%p|%p|%p|%d|%d|%d|%d|%@|%@", item.title, item.prompt,
                         (long)item.largeTitleDisplayMode, item.standardAppearance,
                         item.scrollEdgeAppearance, item.compactAppearance,
                         item.compactScrollEdgeAppearance, item.searchController,
                         item.hidesSearchBarWhenScrolling, stack.navigationBar.prefersLargeTitles,
                         showsTopBar, showsToolbar, NSStringFromCGSize(host.bounds.size),
                         NSStringFromUIEdgeInsets(host.safeAreaInsets)];
    [signature appendFormat:@"|%p|%ld", stack,
                            (long)stack.topViewController.navigationItem.largeTitleDisplayMode];
    if (@available(iOS 16.0, *)) {
        [signature appendFormat:@"|%ld|%ld", (long)item.style, (long)item.preferredSearchBarPlacement];
    }
    if (@available(iOS 26.0, *)) {
        [signature appendFormat:@"|%@|%@", item.attributedSubtitle, item.largeAttributedSubtitle];
    }
    return signature;
}

@implementation ANNScreenPlace {
    NSString *_signature;
}

- (instancetype)initWithSafeArea:(UIEdgeInsets)safeArea
                     showsTopBar:(BOOL)showsTopBar
                     showsTabBar:(BOOL)showsTabBar
                       signature:(NSString *)signature {
    if (self = [super init]) {
        _safeArea = safeArea;
        _showsTopBar = showsTopBar;
        _showsTabBar = showsTabBar;
        _signature = [signature copy];
    }
    return self;
}

+ (ANNScreenPlace *)placeOfEntry:(UIViewController *)entry
                         inStack:(UINavigationController *)stack
                         options:(ANNNavigationOptions *)options {
    UIView *stackView = stack.viewIfLoaded;
    if (!stackView) {
        return nil;
    }
    return [self placeOfEntry:entry inStack:stack measuredIn:stackView options:options];
}

+ (ANNScreenPlace *)placeOfEntry:(UIViewController *)entry
                         inStack:(UINavigationController *)stack
                      measuredIn:(UIView *)host
                         options:(ANNNavigationOptions *)options {
    ANNAssertMainQueue();
    UIWindow *window = host.window;
    if (!window) {
        return nil;
    }
    BOOL showsTopBar = [options.topBar.visible withDefault:YES];
    BOOL showsToolbar = options.toolbar.shouldShow;
    BOOL showsTabBar = !entry.hidesBottomBarWhenPushed &&
                       [[ANNBottomTabsController tabsOf:entry] showsTabBarForScreen:entry];
    NSString *signature =
        ANNPlaceSignature(entry.navigationItem, stack, host, showsTopBar, showsToolbar);
    ANNScreenPlace *kept = objc_getAssociatedObject(entry, ANNScreenPlaceKey);
    if (kept && kept.showsTabBar == showsTabBar && [kept->_signature isEqualToString:signature]) {
        return kept;
    }
    UIEdgeInsets safeArea = [self measureEntry:entry
                                       inStack:stack
                                          host:host
                                   showsTopBar:showsTopBar
                                  showsToolbar:showsToolbar];
    // The measure stands in the host, whose safe area has the tab bar shown now: an entry that hides it gets the
    // window's bottom there.
    if (!showsTabBar) {
        CGFloat tabBar = host.safeAreaInsets.bottom - window.safeAreaInsets.bottom;
        if (tabBar > 0) {
            safeArea.bottom = MAX(safeArea.bottom - tabBar, 0);
        }
    }
    ANNScreenPlace *place = [[ANNScreenPlace alloc] initWithSafeArea:safeArea
                                                          showsTopBar:showsTopBar
                                                          showsTabBar:showsTabBar
                                                            signature:signature];
    objc_setAssociatedObject(entry, ANNScreenPlaceKey, place, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    return place;
}

+ (ANNScreenPlace *)placeOfScreen:(UIViewController *)screen measuredIn:(UIView *)host {
    ANNAssertMainQueue();
    UIWindow *window = host.window;
    if (!window) {
        return nil;
    }
    BOOL showsTabBar = [[ANNBottomTabsController tabsOf:screen] showsTabBarForScreen:screen];
    UIEdgeInsets safeArea = host.safeAreaInsets;
    if (!showsTabBar) {
        CGFloat tabBar = safeArea.bottom - window.safeAreaInsets.bottom;
        if (tabBar > 0) {
            safeArea.bottom = MAX(safeArea.bottom - tabBar, 0);
        }
    }
    return [[ANNScreenPlace alloc] initWithSafeArea:safeArea
                                        showsTopBar:NO
                                        showsTabBar:showsTabBar
                                          signature:@""];
}

// The probe lays out at the host's size in the host, unseen and for this moment only, and leaves no search field
// behind.
+ (UIEdgeInsets)measureEntry:(UIViewController *)entry
                     inStack:(UINavigationController *)stack
                        host:(UIView *)host
                 showsTopBar:(BOOL)showsTopBar
                showsToolbar:(BOOL)showsToolbar {
    UINavigationController *probe = ANNPlaceProbeOf(stack);
    UIViewController *below = probe.viewControllers.firstObject;
    UIViewController *top = probe.topViewController;
    UINavigationBar *bar = probe.navigationBar;
    UINavigationBar *stackBar = stack.navigationBar;
    bar.prefersLargeTitles = stackBar.prefersLargeTitles;
    bar.standardAppearance = stackBar.standardAppearance;
    bar.scrollEdgeAppearance = stackBar.scrollEdgeAppearance;
    bar.compactAppearance = stackBar.compactAppearance;
    below.navigationItem.largeTitleDisplayMode =
        stack.topViewController.navigationItem.largeTitleDisplayMode;
    ANNCopyBarItem(entry.navigationItem, top.navigationItem);
    top.toolbarItems =
        showsToolbar ? @[ [[UIBarButtonItem alloc] initWithTitle:@" "
                                                           style:UIBarButtonItemStylePlain
                                                          target:nil
                                                          action:nil] ]
                     : nil;
    __block UIEdgeInsets safeArea = UIEdgeInsetsZero;
    UIView *probeView = probe.view;
    [UIView performWithoutAnimation:^{
      [probe setNavigationBarHidden:!showsTopBar animated:NO];
      [probe setToolbarHidden:!showsToolbar animated:NO];
      probeView.alpha = 0;
      probeView.userInteractionEnabled = NO;
      probeView.accessibilityElementsHidden = YES;
      probeView.frame = host.bounds;
      [host insertSubview:probeView atIndex:0];
      [probeView layoutIfNeeded];
      [top.view layoutIfNeeded];
      safeArea = top.view.safeAreaInsets;
      [probeView removeFromSuperview];
    }];
    top.navigationItem.searchController = nil;
    top.toolbarItems = nil;
    return safeArea;
}

@end

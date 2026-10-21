#import "ANNMoreNavigationDelegate.h"
#import "ANNBottomTabsController.h"

@implementation ANNMoreNavigationDelegate {
    __weak ANNBottomTabsController *_tabs;
}

- (instancetype)initWithTabs:(ANNBottomTabsController *)tabs
                    previous:(id<UINavigationControllerDelegate>)previous {
    if (self = [super init]) {
        _tabs = tabs;
        _previous = previous;
    }
    return self;
}

#pragma mark - Forwarding

// Whatever the earlier delegate answers, it keeps answering: the animation, the orientations, the rest.
- (BOOL)respondsToSelector:(SEL)selector {
    return [super respondsToSelector:selector] || [_previous respondsToSelector:selector];
}

- (id)forwardingTargetForSelector:(SEL)selector {
    id<UINavigationControllerDelegate> previous = _previous;
    if ([previous respondsToSelector:selector]) {
        return previous;
    }
    return [super forwardingTargetForSelector:selector];
}

#pragma mark - UINavigationControllerDelegate

- (void)navigationController:(UINavigationController *)navigationController
      willShowViewController:(UIViewController *)viewController
                    animated:(BOOL)animated {
    id<UINavigationControllerDelegate> previous = _previous;
    if ([previous respondsToSelector:_cmd]) {
        [previous navigationController:navigationController
                willShowViewController:viewController
                              animated:animated];
    }
    [_tabs moreNavigation:navigationController willShowViewController:viewController];
}

- (void)navigationController:(UINavigationController *)navigationController
       didShowViewController:(UIViewController *)viewController
                    animated:(BOOL)animated {
    id<UINavigationControllerDelegate> previous = _previous;
    if ([previous respondsToSelector:_cmd]) {
        [previous navigationController:navigationController
                 didShowViewController:viewController
                              animated:animated];
    }
    [_tabs moreNavigation:navigationController didShowViewController:viewController];
}

@end

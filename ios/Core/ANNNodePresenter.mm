#import "ANNNodePresenter.h"
#import "ANNSystemUI.h"
#import "ANNSystemUIRules.h"
#import "ANNUtils.h"

@implementation ANNNodePresenter

- (void)bindViewController:(UIViewController *)viewController {
    _boundViewController = viewController;
}

- (void)applyOptionsOnInit:(ANNNavigationOptions *)initialOptions {
}

- (void)applyOptions:(ANNNavigationOptions *)options {
}

- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions resolvedOptions:(ANNNavigationOptions *)resolvedOptions {
}

- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions {
}

- (void)descendantWillAppear:(UIViewController *)screen {
}

- (void)mergeChildOptions:(ANNNavigationOptions *)mergeOptions {
}

- (ANNNavigationOptions *)optionsForParent:(ANNNavigationOptions *)mergeOptions {
    return mergeOptions;
}

#pragma mark - The answers of the node's view controller to UIKit

- (UIStatusBarStyle)statusBarStyleForOptions:(ANNNavigationOptions *)resolvedOptions {
    return ANNStatusBarStyleOf([resolvedOptions.statusBar.style withDefault:nil]);
}

- (BOOL)statusBarHiddenForOptions:(ANNNavigationOptions *)resolvedOptions {
    UIViewController *bound = self.boundViewController;
    UIViewController *screen = bound ? [ANNSystemUI topScreenOf:bound] : nil;
    return [self statusBarHiddenForOptions:resolvedOptions
                       navigationBarHidden:[ANNSystemUI navigationBarHiddenOfScreen:screen]];
}

- (BOOL)statusBarHiddenForOptions:(ANNNavigationOptions *)resolvedOptions
              navigationBarHidden:(BOOL)navigationBarHidden {
    return ANNStatusBarHiddenOf(resolvedOptions.statusBar, navigationBarHidden);
}

- (BOOL)statusBarAnimatesForOptions:(ANNNavigationOptions *)resolvedOptions {
    return [resolvedOptions.statusBar.animate withDefault:YES];
}

- (UIInterfaceOrientationMask)supportedOrientationsForOptions:
    (ANNNavigationOptions *)resolvedOptions {
    return ANNOrientationMaskOf(resolvedOptions.layout.orientation, ANNUtils.appOrientationMask);
}

// On an iPad or a Mac of iOS 18 and later the tabs hide the bar as the screen appears instead. It hides the tab bar
// only, not the toolbar.
- (BOOL)hidesBottomBarWhenPushedForOptions:(ANNNavigationOptions *)resolvedOptions {
    if ([resolvedOptions.bottomTabs.visible withDefault:YES]) {
        return NO;
    }
    UIUserInterfaceIdiom idiom = UIDevice.currentDevice.userInterfaceIdiom;
    if (idiom == UIUserInterfaceIdiomPhone) {
        return YES;
    }
    if (@available(iOS 18.0, *)) {
        return NO;
    }
    return YES;
}

- (BOOL)homeIndicatorAutoHiddenForOptions:(ANNNavigationOptions *)resolvedOptions {
    return [resolvedOptions.layout.autoHideHomeIndicator withDefault:NO];
}

- (UIRectEdge)deferredEdgesForOptions:(ANNNavigationOptions *)resolvedOptions {
    return ANNDeferredEdgesOf(resolvedOptions.layout.deferSystemGestures);
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
- (UIVerticalBarBehavior)verticalBarBehaviorForOptions:(ANNNavigationOptions *)resolvedOptions {
    return [resolvedOptions.layout.verticalBars withDefault:YES] ? UIVerticalBarBehaviorAutomatic
                                                                 : UIVerticalBarBehaviorDisabled;
}
#endif

@end

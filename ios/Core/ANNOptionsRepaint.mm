#import "ANNOptionsRepaint.h"
#import "ANNAssert.h"
#import "ANNModalCoordinator.h"
#import "ANNOverlayCoordinator.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"

@implementation ANNOptionsRepaint

+ (void)applyNewDefaultOptions:(ANNNavigationOptions *)defaults {
    ANNAssertMainQueue();
    UIWindow *mainWindow = ApexNavigation.mainWindow;
    if (!mainWindow) {
        return;
    }
    [CATransaction begin];
    @try {
        [self colorWindow:mainWindow with:defaults.window.backgroundColor];
        // The window's root, the modals (their records) and the overlays' windows (also one still without the scene),
        // bottom to top: the top layer applies last.
        NSMutableArray<UIViewController *> *layers = [NSMutableArray new];
        if (mainWindow.rootViewController) {
            [layers addObject:mainWindow.rootViewController];
        }
        [layers addObjectsFromArray:[ANNModalCoordinator modalRoots]];
        for (UIWindow *window in [ANNOverlayCoordinator overlayWindows]) {
            if (window.rootViewController) {
                [layers addObject:window.rootViewController];
            }
        }
        for (UIViewController *layer in layers) {
            [self repaintNode:layer];
        }
    } @finally {
        [CATransaction commit];
    }
}

// Only defaults that set the color change the window; "no color" is the system background.
+ (void)colorWindow:(UIWindow *)window with:(Color *)color {
    if (!color.hasValue) {
        return;
    }
    window.backgroundColor = color.isNoColor ? UIColor.systemBackgroundColor
                                             : [color withDefault:UIColor.systemBackgroundColor];
}

// Children first, as when a screen appears: a screen applies its options before its containers do.
+ (void)repaintNode:(UIViewController *)node {
    for (UIViewController *child in node.layoutChildren) {
        [self repaintNode:child];
    }
    ANNNodePresenter *presenter = node.presenter;
    if (presenter && node.viewIfLoaded.window) {
        [presenter applyDefaultOptionsChange:[node resolveOptionsWithDefault]];
    }
}

@end

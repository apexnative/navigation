#import "ANNTabsPreloadWindow.h"
#import "ANNAssert.h"
#import "ANNUtils.h"

// Every live preload window, weakly: the reload hides them, the scene's connection moves them. Main queue.
static NSHashTable<ANNTabsPreloadWindow *> *ANNPreloadWindows(void) {
    static NSHashTable<ANNTabsPreloadWindow *> *windows;
    if (!windows) {
        windows = [NSHashTable weakObjectsHashTable];
    }
    return windows;
}

@implementation ANNTabsPreloadWindow

+ (instancetype)preloadWindow {
    ANNAssertMainQueue();
    ANNTabsPreloadWindow *window = [ANNUtils makeWindowOfClass:self];
    window.frame = CGRectZero;
    window.clipsToBounds = YES;
    window.userInteractionEnabled = NO;
    window.accessibilityElementsHidden = YES;
    window.backgroundColor = UIColor.clearColor;
    // Under every window of the app: nothing of it is ever drawn over the navigation.
    window.windowLevel = UIWindowLevelNormal - 1;
    window.hidden = NO;
    [ANNPreloadWindows() addObject:window];
    return window;
}

+ (void)mainWindowSceneDidConnect:(UIWindowScene *)windowScene {
    ANNAssertMainQueue();
    for (ANNTabsPreloadWindow *window in ANNPreloadWindows().allObjects) {
        if (window.windowScene != windowScene) {
            window.windowScene = windowScene;
        }
    }
}

+ (void)hideAllAfterReload {
    ANNAssertMainQueue();
    for (ANNTabsPreloadWindow *window in ANNPreloadWindows().allObjects) {
        window.hidden = YES;
    }
}

- (void)close {
    self.hidden = YES;
    [ANNPreloadWindows() removeObject:self];
    self.windowScene = nil;
}

@end

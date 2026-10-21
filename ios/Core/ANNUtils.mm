#import "ANNUtils.h"
#import "ApexNavigation+Internal.h"

@implementation ANNUtils

+ (UIWindow *)mainWindow {
    return ApexNavigation.mainWindow;
}

+ (UIScreen *)mainScreen {
    // The window knows its screen once it is in a scene; before that the device has one screen to show on.
    return self.mainWindow.windowScene.screen ?: UIScreen.mainScreen;
}

+ (NSArray<UIWindow *> *)windows {
    NSMutableArray<UIWindow *> *windows = [NSMutableArray new];
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if ([scene isKindOfClass:UIWindowScene.class]) {
            [windows addObjectsFromArray:((UIWindowScene *)scene).windows];
        }
    }
    UIWindow *mainWindow = self.mainWindow;
    if (mainWindow && [windows indexOfObjectIdenticalTo:mainWindow] == NSNotFound) {
        [windows addObject:mainWindow];
    }
    return windows;
}

+ (UIWindow *)keyWindowOfScene:(UIWindowScene *)scene {
    for (UIWindow *window in scene.windows) {
        if (window.isKeyWindow) {
            return window;
        }
    }
    return nil;
}

+ (UIWindow *)keyWindow {
    UIWindow *mainWindow = self.mainWindow;
    UIWindowScene *mainScene = mainWindow.windowScene;
    UIWindow *key = [self keyWindowOfScene:mainScene];
    if (key) {
        return key;
    }
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if (scene != mainScene && [scene isKindOfClass:UIWindowScene.class]) {
            key = [self keyWindowOfScene:(UIWindowScene *)scene];
            if (key) {
                return key;
            }
        }
    }
    return mainWindow;
}

+ (CGRect)statusBarFrame {
    UIStatusBarManager *manager = self.mainWindow.windowScene.statusBarManager;
    return manager ? manager.statusBarFrame : CGRectZero;
}

// The core never answers these delegate methods itself; an app's delegate may answer with its root's orientations,
// which ask this again: inside that question the `Info.plist` answers, which asks no controller (F4).
+ (UIInterfaceOrientationMask)appOrientationMask {
    static BOOL asking = NO;
    UIWindow *mainWindow = self.mainWindow;
    UIApplication *application = UIApplication.sharedApplication;
    UIInterfaceOrientationMask mask = 0;
    if (asking) {
        mask = [application supportedInterfaceOrientationsForWindow:mainWindow];
        return mask != 0 ? mask : UIInterfaceOrientationMaskAllButUpsideDown;
    }
    asking = YES;
    @try {
        mask = [self appOrientationMaskOfWindow:mainWindow application:application];
    } @finally {
        asking = NO;
    }
    return mask != 0 ? mask : UIInterfaceOrientationMaskAllButUpsideDown;
}

+ (UIInterfaceOrientationMask)appOrientationMaskOfWindow:(UIWindow *)mainWindow
                                             application:(UIApplication *)application {
    UIInterfaceOrientationMask mask = 0;
    BOOL answered = NO;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270000
    if (@available(iOS 27.0, *)) {
        UIWindowScene *scene = mainWindow.windowScene;
        id<UIWindowSceneDelegate> sceneDelegate = (id<UIWindowSceneDelegate>)scene.delegate;
        SEL selector = @selector(supportedInterfaceOrientationsForWindowScene:);
        if (scene && [sceneDelegate respondsToSelector:selector]) {
            mask = [sceneDelegate supportedInterfaceOrientationsForWindowScene:scene];
            answered = YES;
        }
    }
#endif
    if (!answered) {
        id<UIApplicationDelegate> delegate = application.delegate;
        SEL selector = @selector(application:supportedInterfaceOrientationsForWindow:);
        if ([delegate respondsToSelector:selector]) {
            mask = [delegate application:application
                supportedInterfaceOrientationsForWindow:mainWindow];
            answered = YES;
        }
    }
    if (!answered) {
        mask = [application supportedInterfaceOrientationsForWindow:mainWindow];
    }
    return mask;
}

@end

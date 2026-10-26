#import "ANNSceneDelegate.h"
#import "ANNDeepLinkQueue.h"
#import "ApexNavigation.h"

// On the scene life cycle UIKit stops calling the app delegate's URL, user activity and foreground/background
// methods. The scene forwards them, so AppDelegate overrides (and the deep links of ANNDeepLinkQueue) keep working; the
// links keep their scene, through which React Native 0.88 and newer take them.
@implementation ANNSceneDelegate

- (void)scene:(UIScene *)scene
    willConnectToSession:(UISceneSession *)session
                 options:(UISceneConnectionOptions *)connectionOptions {
    if (![scene isKindOfClass:UIWindowScene.class]) {
        return;
    }
    self.window = [ApexNavigation attachToWindowScene:(UIWindowScene *)scene];

    for (UIOpenURLContext *context in connectionOptions.URLContexts) {
        [self ann_openURLContext:context ofScene:scene];
    }
    for (NSUserActivity *activity in connectionOptions.userActivities) {
        [self ann_continueUserActivity:activity ofScene:scene];
    }
}

- (void)scene:(UIScene *)scene openURLContexts:(NSSet<UIOpenURLContext *> *)URLContexts {
    for (UIOpenURLContext *context in URLContexts) {
        [self ann_openURLContext:context ofScene:scene];
    }
}

- (void)scene:(UIScene *)scene continueUserActivity:(NSUserActivity *)userActivity {
    [self ann_continueUserActivity:userActivity ofScene:scene];
}

- (void)sceneDidBecomeActive:(UIScene *)scene {
    id<UIApplicationDelegate> appDelegate = UIApplication.sharedApplication.delegate;
    if ([appDelegate respondsToSelector:@selector(applicationDidBecomeActive:)]) {
        [appDelegate applicationDidBecomeActive:UIApplication.sharedApplication];
    }
}

- (void)sceneWillResignActive:(UIScene *)scene {
    id<UIApplicationDelegate> appDelegate = UIApplication.sharedApplication.delegate;
    if ([appDelegate respondsToSelector:@selector(applicationWillResignActive:)]) {
        [appDelegate applicationWillResignActive:UIApplication.sharedApplication];
    }
}

- (void)sceneWillEnterForeground:(UIScene *)scene {
    id<UIApplicationDelegate> appDelegate = UIApplication.sharedApplication.delegate;
    if ([appDelegate respondsToSelector:@selector(applicationWillEnterForeground:)]) {
        [appDelegate applicationWillEnterForeground:UIApplication.sharedApplication];
    }
}

- (void)sceneDidEnterBackground:(UIScene *)scene {
    id<UIApplicationDelegate> appDelegate = UIApplication.sharedApplication.delegate;
    if ([appDelegate respondsToSelector:@selector(applicationDidEnterBackground:)]) {
        [appDelegate applicationDidEnterBackground:UIApplication.sharedApplication];
    }
}

#pragma mark - private

- (void)ann_openURLContext:(UIOpenURLContext *)context ofScene:(UIScene *)scene {
    UIApplication *application = UIApplication.sharedApplication;
    id<UIApplicationDelegate> appDelegate = application.delegate;
    if ([appDelegate respondsToSelector:@selector(application:openURL:options:)]) {
        NSMutableDictionary<UIApplicationOpenURLOptionsKey, id> *options = [NSMutableDictionary new];
        options[UIApplicationOpenURLOptionsSourceApplicationKey] = context.options.sourceApplication;
        options[UIApplicationOpenURLOptionsAnnotationKey] = context.options.annotation;
        options[UIApplicationOpenURLOptionsOpenInPlaceKey] = @(context.options.openInPlace);
        [ANNDeepLinkQueue forwardURLContext:context
                                    ofScene:scene
                                    through:^{
                                      [appDelegate application:application openURL:context.URL options:options];
                                    }];
    } else {
        [ANNDeepLinkQueue dispatchURLContext:context ofScene:scene];
    }
}

- (void)ann_continueUserActivity:(NSUserActivity *)activity ofScene:(UIScene *)scene {
    UIApplication *application = UIApplication.sharedApplication;
    id<UIApplicationDelegate> appDelegate = application.delegate;
    if ([appDelegate respondsToSelector:@selector(application:continueUserActivity:restorationHandler:)]) {
        [ANNDeepLinkQueue forwardUserActivity:activity
                                      ofScene:scene
                                      through:^{
                                        [appDelegate application:application
                                             continueUserActivity:activity
                                               restorationHandler:^(
                                                   NSArray<id<UIUserActivityRestoring>> *restorableObjects){
                                               }];
                                      }];
    } else if ([activity.activityType isEqualToString:NSUserActivityTypeBrowsingWeb]) {
        [ANNDeepLinkQueue dispatchUserActivity:activity ofScene:scene];
    }
}

@end

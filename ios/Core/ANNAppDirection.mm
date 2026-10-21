#import "ANNAppDirection.h"
#import "ANNAssert.h"
#import "ANNBottomTabsController.h"
#import "ANNDefaultOptions.h"
#import "ANNStackController.h"
#import "ANNUtils.h"
#import <React/RCTI18nUtil.h>
#import <objc/runtime.h>

// How deep the walk of the shown stacks goes at most.
static const NSUInteger ANNDirectionWalkLimit = 64;

// Main queue: the value applied last, nil before any; and the scene's part of it while the navigation window has no
// scene yet (iOS 17+), apart from the value, so that "the same value again" never drops it.
static NSString *ANNAppliedDirection;
static NSString *ANNPendingSceneDirection;
// How many times the scene's direction changed, and, on each navigation, the change it last followed in a window.
static NSUInteger ANNSceneDirectionChanges;
static const void *ANNFollowedDirectionKey = &ANNFollowedDirectionKey;

static UISemanticContentAttribute ANNAttributeOfDirection(NSString *direction) {
    if ([direction isEqualToString:@"rtl"]) {
        return UISemanticContentAttributeForceRightToLeft;
    }
    if ([direction isEqualToString:@"ltr"]) {
        return UISemanticContentAttributeForceLeftToRight;
    }
    return UISemanticContentAttributeUnspecified;
}

@implementation ANNAppDirection

+ (nullable NSString *)defaultOptionsDirection {
    NSString *direction = [ANNDefaultOptions.current.layout.direction withDefault:nil];
    return direction.length > 0 ? direction : nil;
}

+ (void)applyDefaultOptionsDirection {
    ANNAssertMainQueue();
    NSString *direction = [self defaultOptionsDirection];
    if (!direction) {
        return;
    }
    if (![direction isEqualToString:ANNAppliedDirection]) {
        ANNAppliedDirection = [direction copy];
        [self applyToReactNative:direction];
        if (@available(iOS 17.0, *)) {
            ANNPendingSceneDirection = ANNAppliedDirection;
        } else {
            // Every view and bar made from now on (F5): a stack shown now keeps its direction until
            // its next push, a new root builds in the new one.
            [UIView appearance].semanticContentAttribute = ANNAttributeOfDirection(direction);
        }
    }
    [self applyPendingSceneDirection];
}

+ (void)windowSceneDidConnect {
    ANNAssertMainQueue();
    [self applyPendingSceneDirection];
}

+ (UISemanticContentAttribute)pushDirection {
    if ([self defaultOptionsDirection]) {
        if (@available(iOS 17.0, *)) {
            return UISemanticContentAttributeUnspecified;
        }
    }
    return [self reactNativeIsRightToLeft] ? UISemanticContentAttributeForceRightToLeft
                                           : UISemanticContentAttributeForceLeftToRight;
}

+ (BOOL)reactNativeIsRightToLeft {
    return [RCTI18nUtil sharedInstance].isRTL;
}

// UIKit takes the swipe's edge when a navigation's direction is set and changes: two real sets, the traits' own
// direction and then none, re-read it whatever the navigation had before. Out of a window the traits may still be the
// old ones: the navigation counts as following only from a window.
+ (void)followSceneDirectionIn:(UINavigationController *)navigation {
    if (navigation.viewIfLoaded.window) {
        objc_setAssociatedObject(navigation, ANNFollowedDirectionKey, @(ANNSceneDirectionChanges),
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    BOOL rightToLeft =
        navigation.traitCollection.layoutDirection == UITraitEnvironmentLayoutDirectionRightToLeft;
    UISemanticContentAttribute traits = rightToLeft ? UISemanticContentAttributeForceRightToLeft
                                                    : UISemanticContentAttributeForceLeftToRight;
    NSMutableArray<UIView *> *views = [NSMutableArray arrayWithObject:navigation.navigationBar];
    UIView *view = navigation.viewIfLoaded;
    if (view) {
        [views addObject:view];
    }
    for (UIView *target in views) {
        if (target.semanticContentAttribute != traits) {
            target.semanticContentAttribute = traits;
        }
        target.semanticContentAttribute = UISemanticContentAttributeUnspecified;
    }
}

+ (BOOL)navigationMissedSceneDirection:(UINavigationController *)navigation {
    if (ANNSceneDirectionChanges == 0 || !navigation.viewIfLoaded.window) {
        return NO;
    }
    NSNumber *followed = objc_getAssociatedObject(navigation, ANNFollowedDirectionKey);
    return followed.unsignedIntegerValue != ANNSceneDirectionChanges;
}

#pragma mark - The parts

// React Native lays out in it the surfaces made after it and keeps it across launches.
+ (void)applyToReactNative:(NSString *)direction {
    BOOL rightToLeft = [direction isEqualToString:@"rtl"];
    RCTI18nUtil *i18n = [RCTI18nUtil sharedInstance];
    [i18n allowRTL:rightToLeft || [direction isEqualToString:@"locale"]];
    [i18n forceRTL:rightToLeft];
}

+ (void)applyPendingSceneDirection {
    NSString *direction = ANNPendingSceneDirection;
    UIWindowScene *scene = ANNUtils.mainWindow.windowScene;
    if (!direction || !scene) {
        return;
    }
    ANNPendingSceneDirection = nil;
    if (@available(iOS 17.0, *)) {
        [self applyDirection:direction toScene:scene];
    }
}

// In this order: the scene's trait (`locale` removes the override: the app's language decides), every window of the
// scene with a root takes the new traits now (the keyboard's and an overlay's on its way have none and are skipped),
// then the stacks follow the scene: UIKit takes the swipe's edge as a stack's direction is set, from the direction its
// view has then.
+ (void)applyDirection:(NSString *)direction
               toScene:(UIWindowScene *)scene API_AVAILABLE(ios(17.0)) {
    ANNSceneDirectionChanges++;
    id<UITraitOverrides> overrides = scene.traitOverrides;
    if ([direction isEqualToString:@"rtl"]) {
        overrides.layoutDirection = UITraitEnvironmentLayoutDirectionRightToLeft;
    } else if ([direction isEqualToString:@"ltr"]) {
        overrides.layoutDirection = UITraitEnvironmentLayoutDirectionLeftToRight;
    } else if ([overrides containsTrait:UITraitLayoutDirection.class]) {
        [overrides removeTrait:UITraitLayoutDirection.class];
    }
    NSMutableArray<UIWindow *> *windows = [NSMutableArray new];
    for (UIWindow *window in scene.windows) {
        if (window.rootViewController) {
            [window updateTraitsIfNeeded];
            [windows addObject:window];
        }
    }
    for (UIWindow *window in windows) {
        NSUInteger depth = 0;
        for (UIViewController *controller = window.rootViewController;
             controller && depth < ANNDirectionWalkLimit;
             controller = controller.presentedViewController, depth++) {
            [self dropForcedDirectionIn:controller depth:0];
        }
    }
}

// Every stack, shown or not (an unselected tab, under a full-screen modal, in a closed menu), and UIKit's More
// navigation of tabs with more than five: each takes the scene's new direction now. One out of the window takes its
// traits first, which it would otherwise get only once it is in a window again.
+ (void)dropForcedDirectionIn:(UIViewController *)controller depth:(NSUInteger)depth {
    if (depth >= ANNDirectionWalkLimit) {
        return;
    }
    if ([controller isKindOfClass:ANNStackController.class] && controller.isViewLoaded) {
        if (@available(iOS 17.0, *)) {
            [controller updateTraitsIfNeeded];
        }
        [(ANNStackController *)controller dropForcedDirection];
    }
    ANNBottomTabsController *tabs = [controller isKindOfClass:ANNBottomTabsController.class]
                                        ? (ANNBottomTabsController *)controller
                                        : nil;
    if (tabs.layoutTabs.count > 5) {
        UINavigationController *more = tabs.moreNavigationController;
        if (more.isViewLoaded) {
            if (@available(iOS 17.0, *)) {
                [more updateTraitsIfNeeded];
            }
            [self followSceneDirectionIn:more];
        }
    }
    for (UIViewController *child in controller.childViewControllers) {
        [self dropForcedDirectionIn:child depth:depth + 1];
    }
}

@end

#import "ANNHinge.h"
#import "ANNAssert.h"
#import "ANNUtils.h"

NSNotificationName const ANNHingeDidChangeNotification = @"ANNHingeDidChange";

// Main queue.
static ANNEventEmitter *ANNHingeEventEmitter;
static BOOL ANNHingeUpdatesEnabled;
static BOOL ANNHingeWatchedForScreens;
// Any thread, under @synchronized of the class.
static NSDictionary<NSString *, id> *ANNLastHinge;

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
// Made once, with the first updates; main queue.
static UIHingeInteraction *ANNHingeInteraction API_AVAILABLE(ios(27.1));

static NSString *ANNHingeStateName(UIHingeStatus status) API_AVAILABLE(ios(27.1)) {
    switch (status) {
    case UIHingeStatusClosed:
        return @"closed";
    case UIHingeStatusPartiallyOpen:
        return @"partiallyOpen";
    case UIHingeStatusFullyOpen:
        return @"fullyOpen";
    default:
        return @"unknown";
    }
}

// The angle in degrees to a tenth: UIKit repeats the same angle once the hinge stops, and a tenth is finer than any
// effect needs. While the state is unknown the angle says nothing and is left out.
static NSDictionary<NSString *, id> *ANNHingeValue(UIHinge *hinge) API_AVAILABLE(ios(27.1)) {
    if (!hinge) {
        return @{};
    }
    NSString *state = ANNHingeStateName(hinge.status);
    if (hinge.status == UIHingeStatusUnknown) {
        return @{@"state" : state};
    }
    return @{@"state" : state, @"angle" : @(round(hinge.angle * 1800 / M_PI) / 10)};
}
#endif

@implementation ANNHinge

+ (void)setUpdatesEnabled:(BOOL)enabled eventEmitter:(ANNEventEmitter *)eventEmitter {
    ANNAssertMainQueue();
    ANNHingeEventEmitter = eventEmitter;
    ANNHingeUpdatesEnabled = enabled;
    [self updateInteraction];
}

+ (void)watchForScreens {
    ANNAssertMainQueue();
    ANNHingeWatchedForScreens = YES;
    [self updateInteraction];
}

// The interaction runs while JavaScript asks for updates, and once a screen showed: a partial fold changes the regions
// of a screen (`useReservedRegions`) with no change of its size or traits, and the hinge's state is how the screens
// learn of it.
+ (void)updateInteraction {
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
    if (@available(iOS 27.1, *)) {
        BOOL enabled = ANNHingeUpdatesEnabled || ANNHingeWatchedForScreens;
        if (!ANNHingeInteraction) {
            if (!enabled) {
                return;
            }
            void (^handler)(UIHingeInteraction *, UIHingeInteractionUpdate *) =
                ^(UIHingeInteraction *interaction, UIHingeInteractionUpdate *update) {
                  [ANNHinge hingeDidUpdate:ANNHingeValue(update.hinge)];
                };
            ANNHingeInteraction = [[UIHingeInteraction alloc] initWithUpdateHandler:handler];
        }
        // On the window the navigation shows in. A disabled interaction gets nothing, and the current value once it
        // is enabled again.
        UIWindow *window = ANNUtils.mainWindow;
        if (enabled && window && ANNHingeInteraction.view != window) {
            [ANNHingeInteraction.view removeInteraction:ANNHingeInteraction];
            [window addInteraction:ANNHingeInteraction];
        }
        ANNHingeInteraction.enabled = enabled;
    }
#endif
}

// Each value once: UIKit repeats the last angle while the hinge stands still. `{}` once the interaction leaves the
// window or the device has no hinge.
+ (void)hingeDidUpdate:(NSDictionary<NSString *, id> *)value {
    id previousState = nil;
    @synchronized(ANNHinge.class) {
        if ([ANNLastHinge isEqualToDictionary:value]) {
            return;
        }
        previousState = ANNLastHinge[@"state"];
        ANNLastHinge = value;
    }
    if (ANNHingeUpdatesEnabled) {
        [ANNHingeEventEmitter sendHingeChanged:value];
    }
    id state = value[@"state"];
    if (previousState != state && ![previousState isEqual:state]) {
        [NSNotificationCenter.defaultCenter postNotificationName:ANNHingeDidChangeNotification
                                                          object:nil];
    }
}

+ (NSDictionary<NSString *, id> *)lastValue {
    @synchronized(ANNHinge.class) {
        return ANNLastHinge ?: @{};
    }
}

@end

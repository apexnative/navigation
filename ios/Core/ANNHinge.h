#import "ANNEventEmitter.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Posted on the main queue when the state of the hinge changes (closed, partly open, open), once a screen showed.
extern NSNotificationName const ANNHingeDidChangeNotification;
/// Posted on the main queue for each new value of the hinge (the first one, a move, a new state, the hinge's coming or
/// going), once a screen showed.
extern NSNotificationName const ANNHingeDidMoveNotification;

/// The device's hinge (`useHinge`), iOS 27.1: one UIHingeInteraction on the app's window, which runs once a screen
/// showed (the regions of the screens follow the hinge's state) and while JavaScript asks for updates (it counts its
/// hooks). While JavaScript asks, each new value goes as `ANN.HingeChanged`: `{ state, angle }`, the angle in degrees,
/// or `{}` without a hinge.
@interface ANNHinge : NSObject

/// Starts or stops the updates to JavaScript. Main queue.
+ (void)setUpdatesEnabled:(BOOL)enabled eventEmitter:(nullable ANNEventEmitter *)eventEmitter;

/// Watches the hinge from now on, for the regions of the screens. Main queue.
+ (void)watchForScreens;

/// The last value, `{}` before the first update and without a hinge. Any thread.
+ (NSDictionary<NSString *, id> *)lastValue;

/// Whether the device has a hinge, as the last update tells: NO before the first one. Any thread.
+ (BOOL)isPresent;

@end

NS_ASSUME_NONNULL_END

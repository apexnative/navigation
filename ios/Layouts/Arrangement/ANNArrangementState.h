#import "ANNEventEmitter.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The state of each screen of an arrangement (`useArrangement`): `{ placement, layout, front, hidden }`, which view of
/// the pair it is and how the pair lies. The native side keeps it on the main queue; JavaScript reads it from its own
/// thread for a screen's first render, and gets every change as `ANN.ArrangementChanged`.
@interface ANNArrangementState : NSObject

/// Whether the app shows arrangements (`getStartInfo().arrangements`): built with the iOS 27.1 SDK and running on
/// iOS 27.1 or later. Without them JavaScript sends only the primary, as a screen. Any thread.
+ (BOOL)canShowArrangements;

/// Keeps the screen's state and tells when it changed; nothing goes without an emitter. Main queue.
+ (void)setState:(NSDictionary<NSString *, id> *)state
     forScreenId:(NSString *)screenId
    eventEmitter:(nullable ANNEventEmitter *)eventEmitter;

/// The screen's state, or nil for a screen outside a pair. Any thread.
+ (nullable NSDictionary<NSString *, id> *)stateForScreenId:(NSString *)screenId;

+ (void)removeScreenId:(NSString *)screenId;

/// Forgets every screen: after a reload the new JavaScript counts its ids anew. Main queue.
+ (void)removeAll;

@end

NS_ASSUME_NONNULL_END

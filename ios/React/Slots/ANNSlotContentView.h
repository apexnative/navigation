#import <React/RCTSurfaceView.h>

NS_ASSUME_NONNULL_BEGIN

/// The root of a slot's React children: hidden in the slot, or shown in the screen's container. It is an
/// `RCTSurfaceView` with a touch handler of its own, as the root of a screen's React surface is, so libraries that look
/// for that root above a view find it in one tree too. Gesture Handler adds its root recognizer to it and, as a gesture
/// starts, cancels the JavaScript touches through the touch handler it finds there.
@interface ANNSlotContentView : RCTSurfaceView
@end

NS_ASSUME_NONNULL_END

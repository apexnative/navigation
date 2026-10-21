#import "ANNEventEmitter.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The reserved regions of each screen (`useReservedRegions`), iOS 27.1: `division`, where the screen divides (the fold
/// of a partly folded device), and `occlusion`, where hardware covers it (a camera); each a list of
/// `{ x, y, width, height }`. The native side keeps them on the main queue; JavaScript reads them from its own thread
/// for a screen's first render, and gets every change as `ANN.ScreenRegionsChanged`.
@interface ANNScreenRegions : NSObject

/// The active regions UIKit reserves in `view`, without the margins it adds around them for interactive content, in
/// the points of `coordinateView` and cut to its bounds (a region outside them is left out); nil before iOS 27.1.
/// Main queue.
+ (nullable NSDictionary<NSString *, NSArray *> *)regionsOfView:(UIView *)view
                                                inCoordinatesOf:(UIView *)coordinateView;

/// Keeps the screen's regions and tells when they changed. Main queue.
+ (void)setRegions:(NSDictionary<NSString *, NSArray *> *)regions
       forScreenId:(NSString *)screenId
      eventEmitter:(ANNEventEmitter *)eventEmitter;

/// `{ division, occlusion }`, or nil before the screen was laid out. Any thread.
+ (nullable NSDictionary<NSString *, NSArray *> *)regionsForScreenId:(NSString *)screenId;

+ (void)removeScreenId:(NSString *)screenId;

/// Forgets every screen: after a reload the new JavaScript counts its ids anew. Main queue.
+ (void)removeAll;

@end

NS_ASSUME_NONNULL_END

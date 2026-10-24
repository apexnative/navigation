#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The reserved regions of a screen (`useReservedRegions`), iOS 27.1: `division`, where the screen divides (the fold of
/// a partly folded device), and `occlusion`, where hardware covers it (a camera); each a list of
/// `{ x, y, width, height }`. Measured here; kept for JavaScript by `ANNScreenEnvironment`.
@interface ANNScreenRegions : NSObject

/// The active regions UIKit reserves in `view`, without the margins it adds around them for interactive content, in
/// the points of `coordinateView` and cut to its bounds (a region outside them is left out); nil before iOS 27.1.
/// Main queue.
+ (nullable NSDictionary<NSString *, NSArray *> *)regionsOfView:(UIView *)view
                                                inCoordinatesOf:(UIView *)coordinateView;

@end

NS_ASSUME_NONNULL_END

#import "ANNFrameDriver.h"
#import <UIKit/UIKit.h>

@class ANNElementTransitionOptions;

NS_ASSUME_NONNULL_BEGIN

/// One `elementTransitions[]` item on its React view: the view's own `transform` and `alpha` animate in place, its
/// parents clip it as usual; at the end, at a cancel and at a destroy the values read before come back. Nothing else of
/// the React view changes (no frame, no subview, never `layer.zPosition`).
@interface ANNElementTransition : NSObject <ANNFrameClient>

/// The items found: each `id` first in the leaving screen's React view, then in the arriving one; an item not found or
/// without an animating property is left out.
+ (NSArray<ANNElementTransition *> *)
    transitionsOf:(nullable NSArray<ANNElementTransitionOptions *> *)elements
      leavingRoot:(nullable UIView *)leavingRoot
     arrivingRoot:(nullable UIView *)arrivingRoot;

/// Every item's `id` is found in one of the two screens (the wait's test).
+ (BOOL)allFound:(nullable NSArray<ANNElementTransitionOptions *> *)elements
     leavingRoot:(nullable UIView *)leavingRoot
    arrivingRoot:(nullable UIView *)arrivingRoot;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END

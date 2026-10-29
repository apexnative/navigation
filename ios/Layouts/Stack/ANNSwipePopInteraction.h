#import <UIKit/UIKit.h>

@class ANNTransitionAnimator;

NS_ASSUME_NONNULL_BEGIN

/// The interaction of a swipe back that plays the screen's own pop (`animations.pop.interactive`). It follows the
/// stack's own swipe from the leading edge (UIKit runs its swipe without asking the delegate for a transition), and
/// shows the animator's frames as far as the finger went across `view`, in the direction of the swipe back. Letting go
/// finishes the pop after a flick back or past half way, and puts the screen back otherwise; the rest plays faster
/// after a quick flick, never slower, and UIKit ends its part at the same pace. An interaction UIKit never starts goes
/// with the end of the gesture. Main queue.
@interface ANNSwipePopInteraction : NSObject <UIViewControllerInteractiveTransitioning>

- (instancetype)initWithAnimator:(ANNTransitionAnimator *)animator
                      recognizer:(UIPanGestureRecognizer *)recognizer
                            view:(UIView *)view NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, weak, readonly, nullable) ANNTransitionAnimator *animator;

@end

NS_ASSUME_NONNULL_END

#import <UIKit/UIKit.h>

@class ANNTransitionAnimator;

NS_ASSUME_NONNULL_BEGIN

/// The interaction of a swipe back that plays the screen's own pop (`animations.pop.interactive`). It follows UIKit's
/// recognizer of the swipe, which keeps its delegate and every check of the stack, and shows the animator's frames as
/// far as the finger went across `view`, toward the edge the screens go back to. Letting go finishes the pop after a
/// flick back or past half way, and puts the screen back otherwise; the rest plays faster after a quick flick, never
/// slower, and UIKit ends the bar's change at the same pace. A swipe UIKit never starts as interactive leaves it with
/// the end of the gesture. Main queue.
@interface ANNSwipePopInteraction : NSObject <UIViewControllerInteractiveTransitioning>

- (instancetype)initWithAnimator:(ANNTransitionAnimator *)animator
                      recognizer:(UIPanGestureRecognizer *)recognizer
                            view:(UIView *)view NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, weak, readonly, nullable) ANNTransitionAnimator *animator;

@end

NS_ASSUME_NONNULL_END

#import <UIKit/UIKit.h>

@class ANNTransitionPlan;

NS_ASSUME_NONNULL_BEGIN

/// The one transition object of the core's custom transitions, for the stack (its navigation delegate) and for modals
/// (their transitioning delegate). UIKit's duration is the plan's length, so the bars move along with it.
///
/// The views are the context's (`viewForKey:`), not the controllers': a popover's is its presentation view, and a
/// screen under a modal is in the transition only for a full-screen modal. The arriving view goes into the container
/// above the leaving one (push, `setStackRoot`, show) or below it (pop, dismissal), gets its final frame and lays out
/// (React gets its final size), and hides (alpha 0) until the start; a forward transition starts after its wait
/// (`ANNTransitionWait`), a backward one at once. Then one frame driver moves the sides that play, the elements in
/// place and the shared elements' copies, in Core Animation; a side that doesn't play doesn't move. At the end, before
/// `completeTransition:`, the animations go, the copies go and the React views show again, and both views get back the
/// transform and the alpha they had. Only a pop of the swipe back follows a gesture (`animateFollowingGesture:`) and
/// can end where it began; `completeTransition:` never comes inside `animateTransition:`. Main queue.
@interface ANNTransitionAnimator : NSObject <UIViewControllerAnimatedTransitioning>

- (instancetype)initWithPlan:(ANNTransitionPlan *)plan NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, strong, readonly) ANNTransitionPlan *plan;

/// The start of an interactive transition (a pop of the swipe back, which never waits): as `animateTransition:`, with
/// the frames held at their start until `setProgress:` moves them (`ANNFrameDriver startPausedOn:`); then
/// `finishWithSpeed:` or `rewindWithSpeed:`, after the context's own finish or cancel, plays the end.
- (void)animateFollowingGesture:(id<UIViewControllerContextTransitioning>)transitionContext;

/// The frames show at `progress` (0 to 1) of the length.
- (void)setProgress:(CGFloat)progress;

/// The rest plays (`speed` times as fast, 1 or more), and the transition completes.
- (void)finishWithSpeed:(CGFloat)speed;

/// The frames play back to the start (`speed` times as fast, 1 or more), and the transition completes cancelled.
- (void)rewindWithSpeed:(CGFloat)speed;

/// The seconds the frames take: the plan's length, or the end of a longer element.
@property(nonatomic, readonly) NSTimeInterval length;

/// The transition completed (or was never started).
@property(nonatomic, readonly) BOOL isCompleted;

/// A destroy or a reload during the transition: the wait or the frames stop at the end state, every value written goes
/// back, and the transition completes so that UIKit isn't left half way (an interactive one finishes first). Nothing
/// once it completed.
- (void)cancel;

@end

NS_ASSUME_NONNULL_END

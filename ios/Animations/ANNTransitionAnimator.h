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
/// transform and the alpha they had. Never interactive, never cancelled; `completeTransition:` never
/// comes inside `animateTransition:`. Main queue.
@interface ANNTransitionAnimator : NSObject <UIViewControllerAnimatedTransitioning>

- (instancetype)initWithPlan:(ANNTransitionPlan *)plan NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, strong, readonly) ANNTransitionPlan *plan;

/// A destroy or a reload during the transition: the wait or the frames stop at the end state, every value written goes
/// back, and the transition completes so that UIKit isn't left half way. Nothing once it completed.
- (void)cancel;

@end

NS_ASSUME_NONNULL_END

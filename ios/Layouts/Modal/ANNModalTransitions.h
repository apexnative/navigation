#import <UIKit/UIKit.h>

@class ANNTransitionPlan;

NS_ASSUME_NONNULL_BEGIN

/// The transitioning delegate of one modal with a custom show or dismissal, held by its record (UIKit holds
/// `transitioningDelegate` weakly). It gives UIKit the core's transition object for the show it was made for, and for a
/// dismissal only while the core itself dismisses the modal (`coreDismissal`, set right before the core's
/// `dismissViewControllerAnimated:` and cleared in its completion): the user's swipe or a dismissal of anyone else gets
/// UIKit's own (nil). It never answers a presentation controller, so the style's own one stays (a popover, a full
/// screen). Sheets never get one: they move by UIKit's sheet motion. Main queue.
@interface ANNModalTransitions : NSObject <UIViewControllerTransitioningDelegate>

/// The plan of the show; nil once UIKit took it.
@property(nonatomic, strong, nullable) ANNTransitionPlan *showPlan;

/// The plan of the core's dismissal that runs now; nil when the core dismisses without its own animation.
@property(nonatomic, strong, nullable) ANNTransitionPlan *dismissPlan;

/// The core's own dismissal runs (between its call to UIKit and its completion).
@property(nonatomic) BOOL coreDismissal;

/// A reload or the record's end during a transition: the running custom transition ends at its end state.
- (void)cancel;

@end

NS_ASSUME_NONNULL_END

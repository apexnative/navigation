#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

@class ANNTransitionPlan;

NS_ASSUME_NONNULL_BEGIN

/// What decides the show of a modal (`modalPresentationStyle`, `modalTransitionStyle`), read once, at the present, from
/// the modal root's resolved options (its own, over them its visible screen's, under them the defaults). A
/// `mergeOptions` never changes the style or the transition after the show. Main queue.
@interface ANNModalPresentation : NSObject

/// The decisions of `resolved`. `animated` nil: by `animations.showModal.enter.enabled`; else as given (a lower modal
/// of a `setRoot` layout shows without animation).
- (instancetype)initWithOptions:(ANNNavigationOptions *)resolved
                       animated:(nullable NSNumber *)animated NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// `modalPresentationStyle`: one to one; unset is UIKit's automatic style; an unknown string full screen.
@property(nonatomic, readonly) UIModalPresentationStyle style;
/// `modalTransitionStyle`: `coverVertical` unless one of the others is given.
@property(nonatomic, readonly) UIModalTransitionStyle transitionStyle;
/// The present is animated: `enter.enabled` is not `false`.
@property(nonatomic, readonly) BOOL animated;
/// UIKit shows the modal in its sheet: page sheet, form sheet, the automatic style (`modal.sheet`).
@property(nonatomic, readonly) BOOL isSheet;

/// `animations.showModal` when the show is animated, not a sheet (a sheet moves by UIKit's sheet motion) and has its
/// own animation (`ANNAnimationRules modalIsOwn:`); nil otherwise (UIKit's show, or its zoom).
@property(nonatomic, strong, readonly, nullable) ANNModalAnimationOptions *showAnimation;

/// The plan of the custom show, once made (`showPlanOf:…`); kept for a dismissal without its own animation, which
/// fades a popover over the show's length.
@property(nonatomic, strong, readonly, nullable) ANNTransitionPlan *showPlan;

/// The custom show's plan of `root` over `under` (`showAnimation`), with its wait: at most `waitLimit`; nil without a
/// custom show.
- (nullable ANNTransitionPlan *)showPlanOf:(UIViewController *)root
                                     under:(nullable UIViewController *)under
                                 waitLimit:(NSTimeInterval)waitLimit;

/// The plan of the core's dismissal of `root`: `animation` when it is its own; else, for a popover shown with its own
/// animation, a fade over the show's length; nil for a sheet and for UIKit's own.
- (nullable ANNTransitionPlan *)dismissPlanOf:(UIViewController *)root
                                    animation:(nullable ANNModalAnimationOptions *)animation
                                        under:(nullable UIViewController *)under;

/// Why UIKit can't make this show from `presenting` (`partialCurl` only full screen, to and from), as the reason of the
/// rejection; nil when it can. `windowRoot` is the main window's root.
- (nullable NSString *)problemPresentingFrom:(UIViewController *)presenting
                                  windowRoot:(nullable UIViewController *)windowRoot;

/// Before `presentViewController:`: the style and the transition, the presentation's delegate, the popover kept a
/// popover (`adaptive: false`), the sheet, the size, the popover's source on `presentingLayout`, the hold, the
/// `testID`.
- (void)prepare:(UIViewController *)root
            options:(ANNNavigationOptions *)resolved
           delegate:(id<UIAdaptivePresentationControllerDelegate>)delegate
    presentingLayout:(UIViewController *)presentingLayout;

/// The hold of the modal: `isModalInPresentation` of the presented root is its resolved `modal.swipeToDismiss` turned
/// around (default `true`: no hold), as the screen it shows now has it.
+ (void)applyHoldTo:(UIViewController *)root;

/// The `testID` of the modal's container: the root's view takes `modal.testID`, a sheet `modal.sheet.testID` before it;
/// a view without one loses the old one.
+ (void)applyTestIDTo:(UIViewController *)root sheet:(BOOL)isSheet;

@end

NS_ASSUME_NONNULL_END

#import <UIKit/UIKit.h>

@class ANNElementTransitionOptions;
@class ANNModalAnimationOptions;
@class ANNSharedElementTransitionOptions;
@class ANNStackAnimationOptions;
@class ANNViewAnimationOptions;
@class Bool;

NS_ASSUME_NONNULL_BEGIN

/// What a custom transition does.
typedef NS_ENUM(NSInteger, ANNTransitionKind) {
    ANNTransitionKindPush,
    ANNTransitionKindPop,
    /// `setStackRoot`: forward, like a push.
    ANNTransitionKindReplace,
    ANNTransitionKindPresent,
    ANNTransitionKindDismiss,
};

/// The screen fade of a transition with elements and no screen animation of its own, of a dismissed popover with
/// nothing to play, or of any transition while the system's Reduce Motion is on (then nothing else plays).
typedef NS_ENUM(NSInteger, ANNScreenFade) {
    ANNScreenFadeNone,
    /// The arriving screen from 0 to its alpha.
    ANNScreenFadeArrivingIn,
    /// The leaving screen from its alpha to 0.
    ANNScreenFadeLeavingOut,
};

/// One custom transition of the stack or of a modal, made from the resolved options at the moment the transition is
/// chosen: the sides that play, the elements, the shared pairs, the wait and the length. It holds options and nodes
/// only; the views are the transition context's (`ANNTransitionAnimator`). Main queue.
@interface ANNTransitionPlan : NSObject

/// A push, a pop or a `setStackRoot` of a stack with its own `animation` (`ANNAnimationRules stackIsOwn:`): `enter` on
/// the arriving entry, `exit` on the leaving one. `waitForRender` and `waitLimit` for the forward kinds; nil when the
/// change has no animation of its own.
+ (nullable instancetype)stackPlanOfKind:(ANNTransitionKind)kind
                               animation:(nullable ANNStackAnimationOptions *)animation
                           waitForRender:(nullable Bool *)waitForRender
                               waitLimit:(NSTimeInterval)waitLimit
                                    from:(nullable UIViewController *)from
                                      to:(UIViewController *)to;

/// The show of a modal with its own `animation` (`showModal`): `enter` on the modal, `exit` on the screen under only
/// when `underInTransition` (a full-screen modal); nil when the show has none of its own.
+ (nullable instancetype)presentPlanWithAnimation:(nullable ANNModalAnimationOptions *)animation
                                waitForRender:(nullable Bool *)waitForRender
                                    waitLimit:(NSTimeInterval)waitLimit
                            underInTransition:(BOOL)underInTransition
                                        under:(nullable UIViewController *)under
                                        modal:(UIViewController *)modal;

/// The dismissal of a modal with its own `animation` (`dismissModal`): `exit` on the modal, `enter` on the screen
/// under only when `underInTransition`; nil when the dismissal has none of its own.
+ (nullable instancetype)dismissPlanWithAnimation:(nullable ANNModalAnimationOptions *)animation
                            underInTransition:(BOOL)underInTransition
                                        modal:(UIViewController *)modal
                                        under:(nullable UIViewController *)under;

/// The dismissal of a popover shown with its own animation and closed without one: it fades out over `length`
/// (the show's `exit` never plays on it).
+ (instancetype)fadeOutPlanOfModal:(UIViewController *)modal
                             under:(nullable UIViewController *)under
                            length:(NSTimeInterval)length;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, readonly) ANNTransitionKind kind;

/// A forward kind (push, `setStackRoot`, present): the arriving view goes above, the start waits.
@property(nonatomic, readonly) BOOL isForward;

/// The side of the arriving view and of the leaving one, when it plays (a side that doesn't play doesn't move); a side
/// without a view in this transition is nil.
@property(nonatomic, strong, readonly, nullable) ANNViewAnimationOptions *enter;
@property(nonatomic, strong, readonly, nullable) ANNViewAnimationOptions *exit;

@property(nonatomic, copy, readonly) NSArray<ANNElementTransitionOptions *> *elements;
@property(nonatomic, copy, readonly) NSArray<ANNSharedElementTransitionOptions *> *sharedElements;

@property(nonatomic, readonly) ANNScreenFade screenFade;
@property(nonatomic, readonly) NSTimeInterval screenFadeDuration;

/// The forward start waits for the destination's content and the targets: not with `waitForRender: false`.
@property(nonatomic, readonly) BOOL waits;
/// At most this long (seconds): 0,5 s, or what the command's own wait left of its 0,5 s.
@property(nonatomic, readonly) NSTimeInterval waitLimit;

/// The length: from the configuration when the plan is made; UIKit's duration of the transition (the bars move along
/// with it).
@property(nonatomic, readonly) NSTimeInterval length;

/// The node each side shows: the elements are looked for in their visible screens.
@property(nonatomic, weak, readonly, nullable) UIViewController *fromNode;
/// A dismissal knows the node it reveals only when it starts.
@property(nonatomic, weak, nullable) UIViewController *toNode;

/// The modal's style is full screen: the screen under is in the transition.
@property(nonatomic, readonly) BOOL underInTransition;

/// A dismissal of a popover: with nothing to play it fades out over the length.
@property(nonatomic) BOOL fadesWhenNothingPlays;

/// A pop the core's command asks for (`pop`, `popTo`, `popToRoot`): its own animation plays also on a zoomed screen,
/// whose zoom back UIKit would otherwise play without asking for a transition object (design 24). The back button and
/// the swipes keep UIKit's zoom back.
@property(nonatomic) BOOL overridesZoom;

@end

NS_ASSUME_NONNULL_END

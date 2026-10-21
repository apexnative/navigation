#import <Foundation/Foundation.h>

@class ANNEnterExitScreenAnimationOptions;
@class ANNElementTransitionOptions;
@class ANNModalAnimationOptions;
@class ANNSharedElementTransitionOptions;
@class ANNStackAnimationOptions;
@class ANNViewAnimationOptions;
@class Double;

NS_ASSUME_NONNULL_BEGIN

/// The duration of a property, an element and a shared pair when the options give none, in seconds.
FOUNDATION_EXPORT const NSTimeInterval ANNAnimationDefaultDuration;

/// How long a custom forward transition waits for its content and targets, in seconds: 0,5 s.
FOUNDATION_EXPORT const NSTimeInterval ANNCustomTransitionWaitLimit;

/// The one place of the rules on the animation options: what plays, what makes a transition the core's own, how long it
/// lasts. Pure: options in, answers out.
@interface ANNAnimationRules : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// A property animates: `from` or `to` has a value.
+ (BOOL)propertyPlaysFrom:(nullable Double *)from to:(nullable Double *)to;

/// A side plays: not `enabled: false` (for the sides that have the flag) and at least one property animates.
+ (BOOL)sidePlays:(nullable ANNViewAnimationOptions *)side;

/// The side, when it plays; else nil (a side without its own animation doesn't move).
+ (nullable ANNViewAnimationOptions *)playingSide:(nullable ANNViewAnimationOptions *)side;

/// A stack's change (push, pop, setStackRoot) has its own animation: `content.enter` or `content.exit` plays, or a
/// list of shared elements or elements is not empty. `topBar`, `bottomTabs` and `zoom` never make it own; `[]` is no
/// list.
+ (BOOL)stackIsOwn:(nullable ANNStackAnimationOptions *)animation;

/// A modal's show or dismissal has its own animation: `enter` or `exit` plays, or a list is not empty.
+ (BOOL)modalIsOwn:(nullable ANNModalAnimationOptions *)animation;

/// A `setRoot` has its own animation: a side plays (`'none'` turns both off).
+ (BOOL)rootPlays:(nullable ANNEnterExitScreenAnimationOptions *)animation;

/// `showModal.enter.enabled: false`, `dismissModal.exit.enabled: false`: the whole command goes without an animation;
/// the root's `enter.enabled: false` shows it at once.
+ (BOOL)sideIsDisabled:(nullable ANNViewAnimationOptions *)side;

/// The lists have an item.
+ (BOOL)hasElements:(nullable NSArray<ANNElementTransitionOptions *> *)elements
             shared:(nullable NSArray<ANNSharedElementTransitionOptions *> *)shared;

/// The custom transition's wait after a command's own wait of `commandLimit` that started at `started` (host time; 0:
/// none ran): what is left of the command's limit, at least the 0,1 s test; `ANNCustomTransitionWaitLimit` when no such
/// wait ran.
+ (NSTimeInterval)customWaitLimitAfterWaitStartedAt:(CFTimeInterval)started
                                       commandLimit:(NSTimeInterval)commandLimit;

#pragma mark - Lengths

/// The latest end of a side's playing properties: max(`startDelay` + `duration`), 300 ms where `duration` is unset; 0
/// when nothing plays. Seconds.
+ (NSTimeInterval)lengthOfSide:(nullable ANNViewAnimationOptions *)side;

/// The latest end of the configured elements' animating properties and of the shared pairs (300 ms defaults). Seconds.
+ (NSTimeInterval)lengthOfElements:(nullable NSArray<ANNElementTransitionOptions *> *)elements
                            shared:(nullable NSArray<ANNSharedElementTransitionOptions *> *)shared;

@end

NS_ASSUME_NONNULL_END

#import "ANNAnimationRules.h"
#import "ANNBottomTabsAnimationOptions.h"
#import "ANNModalAnimationOptions.h"
#import "ANNScreenAnimationOptions.h"
#import "ANNStackAnimationOptions.h"
#import "ANNTopBarAnimationOptions.h"
#import "ANNTrack.h"
#import "ANNViewAnimationOptions.h"
#import <QuartzCore/QuartzCore.h>

const NSTimeInterval ANNAnimationDefaultDuration = 0.3;
const NSTimeInterval ANNCustomTransitionWaitLimit = 0.5;

// The custom wait's test of a destination that is ready already: the least it takes.
static const NSTimeInterval ANNCustomTransitionWaitLeast = 0.1;

@implementation ANNAnimationRules

+ (BOOL)propertyPlaysFrom:(Double *)from to:(Double *)to {
    return from.hasValue || to.hasValue;
}

+ (NSTimeInterval)customWaitLimitAfterWaitStartedAt:(CFTimeInterval)started
                                       commandLimit:(NSTimeInterval)commandLimit {
    if (started <= 0) {
        return ANNCustomTransitionWaitLimit;
    }
    NSTimeInterval left = commandLimit - (CACurrentMediaTime() - started);
    return MAX(left, ANNCustomTransitionWaitLeast);
}

+ (BOOL)sideIsDisabled:(ANNViewAnimationOptions *)side {
    Bool *enabled = nil;
    if ([side isKindOfClass:ANNScreenAnimationOptions.class]) {
        enabled = ((ANNScreenAnimationOptions *)side).enabled;
    } else if ([side isKindOfClass:ANNTopBarAnimationOptions.class]) {
        enabled = ((ANNTopBarAnimationOptions *)side).enabled;
    } else if ([side isKindOfClass:ANNBottomTabsAnimationOptions.class]) {
        enabled = ((ANNBottomTabsAnimationOptions *)side).enabled;
    }
    return enabled.hasValue && !enabled.get;
}

+ (BOOL)sidePlays:(ANNViewAnimationOptions *)side {
    if (!side || [self sideIsDisabled:side]) {
        return NO;
    }
    for (ANNAnimationPropertyOptions *property in side.animatedProperties) {
        if ([self propertyPlaysFrom:property.from to:property.to]) {
            return YES;
        }
    }
    return NO;
}

+ (ANNViewAnimationOptions *)playingSide:(ANNViewAnimationOptions *)side {
    return [self sidePlays:side] ? side : nil;
}

+ (BOOL)hasElements:(NSArray<ANNElementTransitionOptions *> *)elements
             shared:(NSArray<ANNSharedElementTransitionOptions *> *)shared {
    return elements.count > 0 || shared.count > 0;
}

+ (BOOL)stackIsOwn:(ANNStackAnimationOptions *)animation {
    if (!animation) {
        return NO;
    }
    return [self sidePlays:animation.content.enter] || [self sidePlays:animation.content.exit] ||
           [self hasElements:animation.elementTransitions
                      shared:animation.sharedElementTransitions];
}

+ (BOOL)modalIsOwn:(ANNModalAnimationOptions *)animation {
    if (!animation) {
        return NO;
    }
    return [self sidePlays:animation.enter] || [self sidePlays:animation.exit] ||
           [self hasElements:animation.elementTransitions
                      shared:animation.sharedElementTransitions];
}

+ (BOOL)rootPlays:(ANNEnterExitScreenAnimationOptions *)animation {
    return [self sidePlays:animation.enter] || [self sidePlays:animation.exit];
}

+ (NSTimeInterval)endOfDuration:(Double *)duration delay:(Double *)delay {
    return ANNSecondsOf(delay, 0) + ANNSecondsOf(duration, ANNAnimationDefaultDuration);
}

+ (NSTimeInterval)lengthOfSide:(ANNViewAnimationOptions *)side {
    if (![self sidePlays:side]) {
        return 0;
    }
    NSTimeInterval length = 0;
    for (ANNAnimationPropertyOptions *property in side.animatedProperties) {
        if ([self propertyPlaysFrom:property.from to:property.to]) {
            length = MAX(length, [self endOfDuration:property.duration delay:property.startDelay]);
        }
    }
    return length;
}

+ (NSTimeInterval)lengthOfElements:(NSArray<ANNElementTransitionOptions *> *)elements
                            shared:(NSArray<ANNSharedElementTransitionOptions *> *)shared {
    NSTimeInterval length = 0;
    for (ANNElementTransitionOptions *element in elements) {
        for (ANNElementAnimationOptions *property in element.animatedProperties) {
            if ([self propertyPlaysFrom:property.from to:property.to]) {
                NSTimeInterval end = [self endOfDuration:property.duration
                                                   delay:property.startDelay];
                length = MAX(length, end);
            }
        }
    }
    for (ANNSharedElementTransitionOptions *pair in shared) {
        length = MAX(length, [self endOfDuration:pair.duration delay:pair.startDelay]);
    }
    return length;
}

@end

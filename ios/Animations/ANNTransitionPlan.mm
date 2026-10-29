#import "ANNTransitionPlan.h"
#import "ANNAnimationRules.h"
#import "ANNModalAnimationOptions.h"
#import "ANNStackAnimationOptions.h"

// The screen fade of a transition with elements and no screen animation of its own.
static const NSTimeInterval ANNElementsScreenFade = 0.3;

@implementation ANNTransitionPlan

- (instancetype)initWithKind:(ANNTransitionKind)kind
                       enter:(ANNViewAnimationOptions *)enter
                        exit:(ANNViewAnimationOptions *)exit
                    elements:(NSArray<ANNElementTransitionOptions *> *)elements
                      shared:(NSArray<ANNSharedElementTransitionOptions *> *)shared
               waitForRender:(Bool *)waitForRender
                   waitLimit:(NSTimeInterval)waitLimit
                        from:(UIViewController *)from
                          to:(UIViewController *)to
           underInTransition:(BOOL)underInTransition {
    if (self = [super init]) {
        _kind = kind;
        _isForward = kind == ANNTransitionKindPush || kind == ANNTransitionKindReplace ||
                     kind == ANNTransitionKindPresent;
        _enter = [ANNAnimationRules playingSide:enter];
        _exit = [ANNAnimationRules playingSide:exit];
        _elements = [elements ?: @[] copy];
        _sharedElements = [shared ?: @[] copy];
        _underInTransition = underInTransition;
        _fromNode = from;
        _toNode = to;
        // With elements and no screen animation of its own, the arriving screen fades in (push, show) or the leaving
        // one out (pop, dismissal); the other doesn't move. Found or not.
        if (!_enter && !_exit && (_elements.count > 0 || _sharedElements.count > 0)) {
            _screenFade = _isForward ? ANNScreenFadeArrivingIn : ANNScreenFadeLeavingOut;
            _screenFadeDuration = ANNElementsScreenFade;
        }
        BOOL noWait = waitForRender.hasValue && !waitForRender.get;
        _waits = _isForward && !noWait;
        _waitLimit = waitLimit > 0 ? waitLimit : 0;
        NSTimeInterval length = MAX([ANNAnimationRules lengthOfSide:_enter],
                                    [ANNAnimationRules lengthOfSide:_exit]);
        length = MAX(length, [ANNAnimationRules lengthOfElements:_elements shared:_sharedElements]);
        length = MAX(length, _screenFadeDuration);
        _length = length;
        // With the system's Reduce Motion on, nothing moves: the transition is a fade of the same length, the arriving
        // screen in going forward, the leaving one out otherwise.
        if (UIAccessibilityIsReduceMotionEnabled() && length > 0) {
            _enter = nil;
            _exit = nil;
            _elements = @[];
            _sharedElements = @[];
            _screenFade = _isForward ? ANNScreenFadeArrivingIn : ANNScreenFadeLeavingOut;
            _screenFadeDuration = length;
        }
    }
    return self;
}

+ (instancetype)stackPlanOfKind:(ANNTransitionKind)kind
                      animation:(ANNStackAnimationOptions *)animation
                  waitForRender:(Bool *)waitForRender
                      waitLimit:(NSTimeInterval)waitLimit
                           from:(UIViewController *)from
                             to:(UIViewController *)to {
    if (![ANNAnimationRules stackIsOwn:animation]) {
        return nil;
    }
    return [[self alloc] initWithKind:kind
                                enter:animation.content.enter
                                 exit:animation.content.exit
                             elements:animation.elementTransitions
                               shared:animation.sharedElementTransitions
                        waitForRender:waitForRender
                            waitLimit:waitLimit
                                 from:from
                                   to:to
                    underInTransition:YES];
}

+ (instancetype)presentPlanWithAnimation:(ANNModalAnimationOptions *)animation
                       waitForRender:(Bool *)waitForRender
                           waitLimit:(NSTimeInterval)waitLimit
                   underInTransition:(BOOL)underInTransition
                               under:(UIViewController *)under
                               modal:(UIViewController *)modal {
    if (![ANNAnimationRules modalIsOwn:animation]) {
        return nil;
    }
    return [[self alloc] initWithKind:ANNTransitionKindPresent
                                enter:animation.enter
                                 exit:underInTransition ? animation.exit : nil
                             elements:animation.elementTransitions
                               shared:animation.sharedElementTransitions
                        waitForRender:waitForRender
                            waitLimit:waitLimit
                                 from:under
                                   to:modal
                    underInTransition:underInTransition];
}

+ (instancetype)dismissPlanWithAnimation:(ANNModalAnimationOptions *)animation
                   underInTransition:(BOOL)underInTransition
                               modal:(UIViewController *)modal
                               under:(UIViewController *)under {
    if (![ANNAnimationRules modalIsOwn:animation]) {
        return nil;
    }
    return [[self alloc] initWithKind:ANNTransitionKindDismiss
                                enter:underInTransition ? animation.enter : nil
                                 exit:animation.exit
                             elements:animation.elementTransitions
                               shared:animation.sharedElementTransitions
                        waitForRender:nil
                            waitLimit:0
                                 from:modal
                                   to:under
                    underInTransition:underInTransition];
}

+ (instancetype)fadeOutPlanOfModal:(UIViewController *)modal
                             under:(UIViewController *)under
                            length:(NSTimeInterval)length {
    ANNTransitionPlan *plan = [[self alloc] initWithKind:ANNTransitionKindDismiss
                                                   enter:nil
                                                    exit:nil
                                                elements:nil
                                                  shared:nil
                                           waitForRender:nil
                                               waitLimit:0
                                                    from:modal
                                                      to:under
                                       underInTransition:NO];
    plan->_screenFade = ANNScreenFadeLeavingOut;
    plan->_screenFadeDuration = length;
    plan->_length = length;
    return plan;
}

@end

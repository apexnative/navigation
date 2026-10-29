#import "ANNStackAnimatingDelegate.h"
#import <objc/runtime.h>

@implementation ANNStackAnimatingDelegate {
    __weak UINavigationController<ANNStackAnimating> *_stack;
}

- (instancetype)initWithStack:(UINavigationController<ANNStackAnimating> *)stack {
    if (self = [super init]) {
        _stack = stack;
    }
    return self;
}

// The choice of the transition object is this delegate's own; the interaction controller too, answered only while
// the top's pop follows the swipe. The other methods of `UINavigationControllerDelegate` are the stack's when the
// stack has them; nothing outside the protocol is forwarded (design 11).
static BOOL ANNIsOwnSelector(SEL selector) {
    SEL animation = @selector(navigationController:
                       animationControllerForOperation:fromViewController:toViewController:);
    return selector == animation;
}

static BOOL ANNIsInteractionSelector(SEL selector) {
    return selector == @selector(navigationController:interactionControllerForAnimationController:);
}

static BOOL ANNIsDelegateSelector(SEL selector) {
    Protocol *protocol = @protocol(UINavigationControllerDelegate);
    return protocol_getMethodDescription(protocol, selector, NO, YES).name != NULL ||
           protocol_getMethodDescription(protocol, selector, YES, YES).name != NULL;
}

// A method of the protocol this delegate passes to the stack.
- (BOOL)forwardsToStack:(SEL)selector {
    return !ANNIsOwnSelector(selector) && !ANNIsInteractionSelector(selector) &&
           ANNIsDelegateSelector(selector) && [_stack respondsToSelector:selector];
}

- (BOOL)respondsToSelector:(SEL)selector {
    if (ANNIsOwnSelector(selector)) {
        return YES;
    }
    if (ANNIsInteractionSelector(selector)) {
        return _answersInteraction;
    }
    if ([super respondsToSelector:selector]) {
        return YES;
    }
    return [self forwardsToStack:selector];
}

- (id)forwardingTargetForSelector:(SEL)selector {
    if ([self forwardsToStack:selector]) {
        return _stack;
    }
    return [super forwardingTargetForSelector:selector];
}

#pragma mark - UINavigationControllerDelegate

- (void)navigationController:(UINavigationController *)navigationController
      willShowViewController:(UIViewController *)viewController
                    animated:(BOOL)animated {
    UINavigationController<ANNStackAnimating> *stack = _stack;
    if ([stack respondsToSelector:_cmd]) {
        [stack navigationController:navigationController
             willShowViewController:viewController
                           animated:animated];
    }
}

- (void)navigationController:(UINavigationController *)navigationController
       didShowViewController:(UIViewController *)viewController
                    animated:(BOOL)animated {
    UINavigationController<ANNStackAnimating> *stack = _stack;
    if ([stack respondsToSelector:_cmd]) {
        [stack navigationController:navigationController
              didShowViewController:viewController
                           animated:animated];
    }
}

- (id<UIViewControllerAnimatedTransitioning>)
               navigationController:(UINavigationController *)navigationController
    animationControllerForOperation:(UINavigationControllerOperation)operation
                 fromViewController:(UIViewController *)fromVC
                   toViewController:(UIViewController *)toVC {
    return [_stack animatorForOperation:operation from:fromVC to:toVC];
}

- (id<UIViewControllerInteractiveTransitioning>)
                   navigationController:(UINavigationController *)navigationController
    interactionControllerForAnimationController:
        (id<UIViewControllerAnimatedTransitioning>)animationController {
    return [_stack interactionForAnimator:animationController];
}

@end

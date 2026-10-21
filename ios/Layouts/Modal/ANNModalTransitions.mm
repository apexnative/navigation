#import "ANNModalTransitions.h"
#import "ANNTransitionAnimator.h"
#import "ANNTransitionPlan.h"

@implementation ANNModalTransitions {
    __weak ANNTransitionAnimator *_running;
}

- (id<UIViewControllerAnimatedTransitioning>)
    animationControllerForPresentedController:(UIViewController *)presented
                         presentingController:(UIViewController *)presenting
                             sourceController:(UIViewController *)source {
    ANNTransitionPlan *plan = _showPlan;
    _showPlan = nil;
    if (!plan) {
        return nil;
    }
    ANNTransitionAnimator *animator = [[ANNTransitionAnimator alloc] initWithPlan:plan];
    _running = animator;
    return animator;
}

- (id<UIViewControllerAnimatedTransitioning>)animationControllerForDismissedController:
    (UIViewController *)dismissed {
    ANNTransitionPlan *plan = _dismissPlan;
    if (!_coreDismissal || !plan) {
        return nil;
    }
    _dismissPlan = nil;
    ANNTransitionAnimator *animator = [[ANNTransitionAnimator alloc] initWithPlan:plan];
    _running = animator;
    return animator;
}

- (void)cancel {
    [_running cancel];
    _running = nil;
}

@end

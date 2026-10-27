#import "ANNSwipePopInteraction.h"
#import "ANNAssert.h"
#import "ANNTransitionAnimator.h"

// A flick faster than this, in points a second, ends the swipe by its direction alone.
static const CGFloat ANNSwipeFlickSpeed = 500;

// The rest of the pop plays at most this many times as fast as its own animation.
static const CGFloat ANNSwipeMostSpeedUp = 3;

@implementation ANNSwipePopInteraction {
    ANNTransitionAnimator *_strongAnimator;
    __weak UIPanGestureRecognizer *_recognizer;
    __weak UIView *_view;
    id<UIViewControllerContextTransitioning> _context;
    // The interaction keeps itself while it follows the gesture: UIKit may let it go before the finger does.
    ANNSwipePopInteraction *_keep;
    // The gesture ended before UIKit started the transition: the end is chosen at the start.
    BOOL _endedEarly;
    BOOL _gaveUpEarly;
    CGFloat _completionSpeed;
}

- (instancetype)initWithAnimator:(ANNTransitionAnimator *)animator
                      recognizer:(UIPanGestureRecognizer *)recognizer
                            view:(UIView *)view {
    if (self = [super init]) {
        _animator = animator;
        _strongAnimator = animator;
        _recognizer = recognizer;
        _view = view;
        _completionSpeed = 1;
        _keep = self;
        [recognizer addTarget:self action:@selector(swipe:)];
    }
    return self;
}

#pragma mark - UIViewControllerInteractiveTransitioning

- (void)startInteractiveTransition:(id<UIViewControllerContextTransitioning>)transitionContext {
    ANNAssertMainQueue();
    ANNTransitionAnimator *animator = _strongAnimator;
    if (!animator || _context) {
        return;
    }
    _context = transitionContext;
    [animator animateFollowingGesture:transitionContext];
    UIGestureRecognizerState state = _recognizer.state;
    BOOL moving = state == UIGestureRecognizerStateBegan || state == UIGestureRecognizerStateChanged;
    if (_gaveUpEarly) {
        [self endFinishing:NO speed:1];
        return;
    }
    if (_endedEarly || !moving) {
        [self letGo];
        return;
    }
    [self follow];
}

- (CGFloat)completionSpeed {
    return _completionSpeed;
}

// The frames play at an even pace to their end, so UIKit's part (the bar) does too.
- (UIViewAnimationCurve)completionCurve {
    return UIViewAnimationCurveLinear;
}

- (BOOL)wantsInteractiveStart {
    return YES;
}

#pragma mark - The gesture

- (void)swipe:(UIPanGestureRecognizer *)recognizer {
    switch (recognizer.state) {
        case UIGestureRecognizerStateBegan:
        case UIGestureRecognizerStateChanged:
            [self follow];
            break;
        case UIGestureRecognizerStateEnded:
            [self letGo];
            break;
        case UIGestureRecognizerStateCancelled:
        case UIGestureRecognizerStateFailed:
            [self giveUp];
            break;
        default:
            break;
    }
}

- (void)follow {
    if (!_context) {
        return;
    }
    if (_strongAnimator.isCompleted) {
        [self stop];
        return;
    }
    CGFloat progress = [self progress];
    [_strongAnimator setProgress:progress];
    [_context updateInteractiveTransition:progress];
}

// The finger left: a flick decides by its direction, a slow release by how far the swipe went.
- (void)letGo {
    if (!_context) {
        [self endedEarlyGivingUp:NO];
        return;
    }
    CGFloat velocity = [self alongBack:[_recognizer velocityInView:_view].x];
    BOOL finish = velocity > ANNSwipeFlickSpeed ||
                  (velocity >= -ANNSwipeFlickSpeed && [self progress] > 0.5);
    // The rest takes the finger's pace when that is quicker than the animation's: the animation would cover the width
    // in its length, the finger in width / velocity.
    CGFloat width = MAX(_view.bounds.size.width, 1);
    BOOL withFinger = finish ? velocity > 0 : velocity < 0;
    CGFloat speed = withFinger ? fabs(velocity) * _strongAnimator.length / width : 1;
    [self endFinishing:finish speed:MIN(MAX(speed, 1.0), ANNSwipeMostSpeedUp)];
}

// The system took the touch (a call, a second gesture): the screen goes back.
- (void)giveUp {
    if (!_context) {
        [self endedEarlyGivingUp:YES];
        return;
    }
    [self endFinishing:NO speed:1];
}

// UIKit holds an interaction it starts; one it never starts goes once the gesture ended.
- (void)endedEarlyGivingUp:(BOOL)gaveUp {
    _endedEarly = YES;
    _gaveUpEarly = gaveUp;
    [_recognizer removeTarget:self action:@selector(swipe:)];
    _keep = nil;
}

- (void)endFinishing:(BOOL)finish speed:(CGFloat)speed {
    id<UIViewControllerContextTransitioning> context = _context;
    ANNTransitionAnimator *animator = _strongAnimator;
    [self stop];
    if (!context || !animator || animator.isCompleted) {
        return;
    }
    // UIKit reads the speed as it ends its own part of the transition.
    _completionSpeed = speed;
    if (finish) {
        [context finishInteractiveTransition];
        [animator finishWithSpeed:speed];
    } else {
        [context cancelInteractiveTransition];
        [animator rewindWithSpeed:speed];
    }
}

// The part of the width the finger went in the direction of the swipe back.
- (CGFloat)progress {
    UIView *view = _view;
    CGFloat width = view.bounds.size.width;
    if (width <= 0) {
        return 0;
    }
    CGFloat along = [self alongBack:[_recognizer translationInView:view].x];
    return MIN(MAX(along / width, 0.0), 1.0);
}

// A move across the view, positive in the direction of the swipe back: rightward, leftward in a right-to-left layout.
- (CGFloat)alongBack:(CGFloat)x {
    BOOL rightToLeft =
        _view.effectiveUserInterfaceLayoutDirection == UIUserInterfaceLayoutDirectionRightToLeft;
    return rightToLeft ? -x : x;
}

- (void)stop {
    [_recognizer removeTarget:self action:@selector(swipe:)];
    _context = nil;
    _strongAnimator = nil;
    _keep = nil;
}

@end

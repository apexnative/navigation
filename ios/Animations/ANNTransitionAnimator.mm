#import "ANNTransitionAnimator.h"
#import "ANNAssert.h"
#import "ANNComponentViewController.h"
#import "ANNElementFinder.h"
#import "ANNElementTransition.h"
#import "ANNFrameDriver.h"
#import "ANNSharedElementFlight.h"
#import "ANNStackQueue.h"
#import "ANNTransitionPlan.h"
#import "ANNTransitionWait.h"
#import "ANNViewAnimationOptions.h"
#import "ANNViewTracks.h"
#import "UIViewController+LayoutProtocol.h"

// The screens of a node that lays out in the transition take their root frame for the final size now: a stack lays out
// its new top only after UIKit's start, which comes inside this transition (finding 9).
static void ANNUpdateRootLayoutOf(UIViewController *node) {
    for (ANNComponentViewController *screen in ANNStackScreensOfEntry(node)) {
        [screen updateRootLayout];
    }
}

@implementation ANNTransitionAnimator {
    id<UIViewControllerContextTransitioning> _context;
    UIView *_arriving;
    UIView *_leaving;
    CGFloat _arrivingAlpha;
    CGFloat _leavingAlpha;
    CATransform3D _arrivingTransform;
    CATransform3D _leavingTransform;
    ANNTransitionWait *_wait;
    ANNFrameDriver *_driver;
    NSArray<id<ANNFrameClient>> *_clients;
    BOOL _began;
    BOOL _completed;
    // The transition follows a gesture (`animateFollowingGesture:`), and its end was chosen.
    BOOL _followsGesture;
    BOOL _endChosen;
    // The container's views of the transition that got a depth for it, and the depth each had.
    NSArray<UIView *> *_depthViews;
    NSArray<NSNumber *> *_depthsBefore;
}

- (instancetype)initWithPlan:(ANNTransitionPlan *)plan {
    if (self = [super init]) {
        _plan = plan;
    }
    return self;
}

#pragma mark - UIViewControllerAnimatedTransitioning

- (NSTimeInterval)transitionDuration:(id<UIViewControllerContextTransitioning>)transitionContext {
    return _plan.length;
}

- (void)animateTransition:(id<UIViewControllerContextTransitioning>)transitionContext {
    ANNAssertMainQueue();
    _context = transitionContext;
    UIView *container = transitionContext.containerView;
    UIViewController *fromController =
        [transitionContext viewControllerForKey:UITransitionContextFromViewControllerKey];
    UIViewController *toController =
        [transitionContext viewControllerForKey:UITransitionContextToViewControllerKey];
    UIView *fromView = [transitionContext viewForKey:UITransitionContextFromViewKey];
    UIView *toView = [transitionContext viewForKey:UITransitionContextToViewKey];
    ANNTransitionKind kind = _plan.kind;
    // The modal itself is always in its transition; the screen under it only where the context gives its view.
    if (!toView && kind == ANNTransitionKindPresent) {
        toView = toController.view;
    }
    if (!fromView && kind == ANNTransitionKindDismiss) {
        fromView = fromController.view;
    }
    _arriving = toView;
    _leaving = fromView;
    // A view the presentation controller placed already (a popover's) stays where it is.
    if (toView && !toView.superview) {
        if (_plan.isForward) {
            if (fromView.superview == container) {
                [container insertSubview:toView aboveSubview:fromView];
            } else {
                [container addSubview:toView];
            }
        } else if (fromView.superview == container) {
            [container insertSubview:toView belowSubview:fromView];
        } else {
            [container insertSubview:toView atIndex:0];
        }
    }
    if (toView) {
        // A presentation view around the controller's (a popover's) is placed by its presentation controller.
        if (toView == toController.view) {
            CGRect frame = [transitionContext finalFrameForViewController:toController];
            if (!CGRectIsEmpty(frame)) {
                toView.frame = frame;
            }
        }
        [toView layoutIfNeeded];
        if (toController) {
            ANNUpdateRootLayoutOf(toController);
        }
        // The alpha the arriving view had, before it hides to the start: the core's waits never change it (a waiting
        // view is parked, not hidden).
        _arrivingAlpha = toView.alpha;
        _arrivingTransform = toView.layer.transform;
        toView.alpha = 0;
    }
    if (fromView) {
        _leavingAlpha = fromView.alpha;
        _leavingTransform = fromView.layer.transform;
    }
    __weak ANNTransitionAnimator *weakSelf = self;
    if (_plan.waits) {
        _wait = [[ANNTransitionWait alloc] initWithPlan:_plan];
        [_wait startThen:^{
          [weakSelf begin];
        }];
        return;
    }
    [self begin];
}

- (void)animateFollowingGesture:(id<UIViewControllerContextTransitioning>)transitionContext {
    _followsGesture = YES;
    [self animateTransition:transitionContext];
}

- (void)setProgress:(CGFloat)progress {
    [_driver setProgress:progress];
}

- (void)finishWithSpeed:(CGFloat)speed {
    _endChosen = YES;
    [_driver finishWithSpeed:speed];
}

- (void)rewindWithSpeed:(CGFloat)speed {
    _endChosen = YES;
    [_driver rewindWithSpeed:speed];
}

- (NSTimeInterval)length {
    return _driver ? _driver.length : _plan.length;
}

- (BOOL)isCompleted {
    return _completed || !_context;
}

- (void)animationEnded:(BOOL)transitionCompleted {
    _wait = nil;
    _driver = nil;
    _clients = nil;
}

#pragma mark - The frames

- (void)begin {
    if (_began || _completed || !_context) {
        return;
    }
    _began = YES;
    _wait = nil;
    UIView *container = _context.containerView;
    ANNTransitionViews *views = [[ANNTransitionViews alloc] initWithPairs:_plan.sharedElements
                                                                 elements:_plan.elements
                                                                 fromNode:_plan.fromNode
                                                                   toNode:_plan.toNode];
    NSMutableArray<id<ANNFrameClient>> *clients = [NSMutableArray new];
    // The shared pairs first: their geometry and pictures are taken while the screens are still at rest.
    NSArray<ANNSharedElementFlight *> *flights =
        [ANNSharedElementFlight flightsOf:_plan.sharedElements views:views container:container];
    NSArray<ANNElementTransition *> *elements =
        [ANNElementTransition transitionsOf:_plan.elements views:views];
    ANNViewTracks *arrivingTracks = nil;
    ANNViewTracks *leavingTracks = nil;
    if (_plan.enter && _arriving) {
        arrivingTracks = [[ANNViewTracks alloc] initWithView:_arriving
                                                        side:_plan.enter
                                                 alphaBefore:_arrivingAlpha];
    }
    if (_plan.exit && _leaving) {
        leavingTracks = [[ANNViewTracks alloc] initWithView:_leaving
                                                       side:_plan.exit
                                                alphaBefore:_leavingAlpha];
    }
    switch (_plan.screenFade) {
        case ANNScreenFadeArrivingIn:
            if (_arriving) {
                arrivingTracks = [[ANNViewTracks alloc] initWithView:_arriving
                                                            fadeFrom:0
                                                                  to:_arrivingAlpha
                                                            duration:_plan.screenFadeDuration
                                                         alphaBefore:_arrivingAlpha];
            }
            break;
        case ANNScreenFadeLeavingOut:
            if (_leaving) {
                leavingTracks = [[ANNViewTracks alloc] initWithView:_leaving
                                                           fadeFrom:_leavingAlpha
                                                                 to:0
                                                           duration:_plan.screenFadeDuration
                                                        alphaBefore:_leavingAlpha];
            }
            break;
        case ANNScreenFadeNone:
            break;
    }
    BOOL nothingPlays = !arrivingTracks.hasTracks && !leavingTracks.hasTracks &&
                        flights.count == 0 && elements.count == 0;
    // A dismissed popover with nothing to play fades out over the length.
    if (nothingPlays && _plan.fadesWhenNothingPlays && _leaving && _plan.length > 0) {
        leavingTracks = [[ANNViewTracks alloc] initWithView:_leaving
                                                   fadeFrom:_leavingAlpha
                                                         to:0
                                                   duration:_plan.length
                                                alphaBefore:_leavingAlpha];
    }
    // The arriving view gets its own alpha back: an `opacity` animation of its tracks shows over it from the start, in
    // the same transaction.
    if (_arriving) {
        _arriving.alpha = _arrivingAlpha;
    }
    if (arrivingTracks.hasTracks) {
        [clients addObject:arrivingTracks];
    }
    if (leavingTracks.hasTracks) {
        [clients addObject:leavingTracks];
    }
    // Each element plays on its own: one whose animations fail goes back and stays, the others and the screens go on.
    for (ANNElementTransition *element in elements) {
        [clients addObject:[[ANNIsolatedElement alloc] initWithClient:element name:element.name]];
    }
    for (ANNSharedElementFlight *flight in flights) {
        [clients addObject:[[ANNIsolatedElement alloc] initWithClient:flight name:flight.name]];
    }
    _clients = clients;
    if ([self tilts]) {
        [self orderInDepthIn:container flights:flights];
    }
    __weak ANNTransitionAnimator *weakSelf = self;
    _driver = [[ANNFrameDriver alloc] initWithClients:clients length:_plan.length];
    if (_followsGesture) {
        [_driver startPausedOn:container.layer
                          done:^(BOOL finished) {
                            [weakSelf complete];
                          }];
        return;
    }
    [_driver startWithDone:^{
      [weakSelf complete];
    }];
}

// A side tilts (`rotationX`, `rotationY`): Core Animation would cut the tilted screen with the other one in 3D, while
// Android draws views in their order.
static BOOL ANNSideTilts(ANNViewAnimationOptions *side) {
    return side.rotationX.from.hasValue || side.rotationX.to.hasValue ||
           side.rotationY.from.hasValue || side.rotationY.to.hasValue;
}

- (BOOL)tilts {
    return ANNSideTilts(_plan.enter) || ANNSideTilts(_plan.exit);
}

// The transition's own views in the container (the screens' or presentation views, the copies; never a React view) get
// depths in their order, apart by more than any tilt reaches, so each one is drawn over the ones before it as on
// Android; `complete` puts the depths back.
- (void)orderInDepthIn:(UIView *)container flights:(NSArray<ANNSharedElementFlight *> *)flights {
    NSHashTable<UIView *> *own = [NSHashTable weakObjectsHashTable];
    if (_arriving) {
        [own addObject:_arriving];
    }
    if (_leaving) {
        [own addObject:_leaving];
    }
    for (ANNSharedElementFlight *flight in flights) {
        [own addObject:flight.copyView];
    }
    CGFloat step = MAX(container.bounds.size.width, container.bounds.size.height);
    NSMutableArray<UIView *> *views = [NSMutableArray new];
    NSMutableArray<NSNumber *> *depths = [NSMutableArray new];
    for (UIView *view in container.subviews) {
        if (![own containsObject:view]) {
            continue;
        }
        [views addObject:view];
        [depths addObject:@(view.layer.zPosition)];
        view.layer.zPosition = step * views.count;
    }
    _depthViews = views;
    _depthsBefore = depths;
}

// Everything the transition wrote goes back before UIKit completes it.
- (void)complete {
    if (_completed || !_context) {
        return;
    }
    _completed = YES;
    for (NSUInteger index = 0; index < _depthViews.count; index++) {
        _depthViews[index].layer.zPosition = (CGFloat)_depthsBefore[index].doubleValue;
    }
    _depthViews = nil;
    for (id<ANNFrameClient> client in _clients) {
        [client restore];
    }
    if (_arriving) {
        _arriving.alpha = _arrivingAlpha;
        _arriving.layer.transform = _arrivingTransform;
    }
    if (_leaving) {
        _leaving.alpha = _leavingAlpha;
        _leaving.layer.transform = _leavingTransform;
    }
    id<UIViewControllerContextTransitioning> context = _context;
    _context = nil;
    [context completeTransition:!context.transitionWasCancelled];
}

- (void)cancel {
    ANNAssertMainQueue();
    if (_completed || !_context) {
        return;
    }
    [_wait cancel];
    _wait = nil;
    // An interactive transition ends at its end state too: UIKit completes only one whose end was chosen.
    if (_followsGesture && !_endChosen) {
        _endChosen = YES;
        [_context finishInteractiveTransition];
    }
    // A stopped clock gets its time back in the commit that removes the animations.
    [CATransaction begin];
    [_driver cancel];
    [self complete];
    [CATransaction commit];
}

@end

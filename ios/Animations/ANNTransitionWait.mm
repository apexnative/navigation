#import "ANNTransitionWait.h"
#import "ANNAssert.h"
#import "ANNElementFinder.h"
#import "ANNElementTransition.h"
#import "ANNSharedElementFlight.h"
#import "ANNTransitionPlan.h"
#import "UIViewController+LayoutProtocol.h"

// The key of the wait's content-mount handler (other waiters of the same content keep theirs).
static NSString *const ANNTransitionTargetsKey = @"ApexNavigation.transitionTargets";

// The test without a mount, for a destination that is ready already.
static const NSTimeInterval ANNReadyCheckDelay = 0.1;

@implementation ANNTransitionWait {
    ANNTransitionPlan *_plan;
    dispatch_block_t _start;
    NSArray<UIView<ANNComponentProtocol> *> *_observed;
    BOOL _finished;
}

- (instancetype)initWithPlan:(ANNTransitionPlan *)plan {
    if (self = [super init]) {
        _plan = plan;
    }
    return self;
}

- (void)dealloc {
    [self removeHandlers];
}

// The screens the transition goes to: what the arriving node shows (a modal's stack its top, tabs the selected one, a
// pair both of its screens).
- (NSArray<UIViewController *> *)destinationScreens {
    UIViewController *to = _plan.toNode;
    return to ? to.shownComponentViewControllers : @[];
}

- (BOOL)contentIsReady {
    for (UIViewController *screen in [self destinationScreens]) {
        UIView<ANNComponentProtocol> *content = screen.reactView;
        if (content && !content.isReady) {
            return NO;
        }
    }
    return YES;
}

- (BOOL)targetsAreFound {
    ANNTransitionViews *views = [[ANNTransitionViews alloc] initWithPairs:_plan.sharedElements
                                                                 elements:_plan.elements
                                                                 fromNode:_plan.fromNode
                                                                   toNode:_plan.toNode];
    return [ANNSharedElementFlight allTargetsFound:_plan.sharedElements views:views] &&
           [ANNElementTransition allFound:_plan.elements views:views];
}

- (BOOL)isReady {
    return [self contentIsReady] && [self targetsAreFound];
}

- (void)startThen:(dispatch_block_t)start {
    ANNAssertMainQueue();
    if (_start || _finished) {
        return;
    }
    _start = [start copy];
    NSMutableArray<UIView<ANNComponentProtocol> *> *observed = [NSMutableArray new];
    for (UIViewController *screen in [self destinationScreens]) {
        if (screen.reactView) {
            [observed addObject:screen.reactView];
        }
    }
    // A destination without React content has nothing to mount: it starts at once, whatever the targets; the pairs and
    // elements not found then are skipped.
    if (observed.count == 0) {
        [self finish];
        return;
    }
    _observed = observed;
    __weak ANNTransitionWait *weakSelf = self;
    for (UIView<ANNComponentProtocol> *content in observed) {
        [content setContentDidMount:^{
          [weakSelf check];
        }
                             forKey:ANNTransitionTargetsKey];
    }
    NSTimeInterval limit = _plan.waitLimit;
    NSTimeInterval readyCheck = MIN(ANNReadyCheckDelay, limit);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(readyCheck * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     [weakSelf check];
                   });
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(limit * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     [weakSelf finish];
                   });
}

- (void)check {
    if (!_finished && _start && [self isReady]) {
        [self finish];
    }
}

- (void)finish {
    if (_finished) {
        return;
    }
    _finished = YES;
    [self removeHandlers];
    dispatch_block_t start = _start;
    _start = nil;
    if (start) {
        start();
    }
}

- (void)cancel {
    _finished = YES;
    _start = nil;
    [self removeHandlers];
}

- (void)removeHandlers {
    NSArray<UIView<ANNComponentProtocol> *> *observed = _observed;
    _observed = nil;
    for (UIView<ANNComponentProtocol> *content in observed) {
        [content setContentDidMount:nil forKey:ANNTransitionTargetsKey];
    }
}

@end

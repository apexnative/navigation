#import "ANNPresentationGate.h"
#import "ANNAssert.h"
#import "ANNRootCoordinator.h"
#import "ApexNavigation+Internal.h"
#import <React/RCTLog.h>

@interface ANNGateStep ()
- (void)runWithDone:(dispatch_block_t)done;
- (void)markDropped;
@end

@implementation ANNGateStep {
    void (^_run)(dispatch_block_t done);
}

- (instancetype)initWithRun:(void (^)(dispatch_block_t done))run {
    if (self = [super init]) {
        _run = [run copy];
    }
    return self;
}

- (void)runWithDone:(dispatch_block_t)done {
    void (^run)(dispatch_block_t) = _run;
    _run = nil;
    if (run) {
        run(done);
    } else {
        done();
    }
}

- (void)markDropped {
    _dropped = YES;
    _run = nil;
}

@end

// Main queue.
static NSMutableArray<ANNGateStep *> *ANNGateQueue;
// A call of a step waits for UIKit's completion.
static BOOL ANNGateBusy;
// The head waits for a running transition's completion, or for the next turn.
static BOOL ANNGateWaiting;
// A step's completion came in this turn: UIKit finishes its own part first, so nothing runs before the next turn,
// also a step asked for inside that completion.
static BOOL ANNGateSettling;
// The number of the running step and of the gate's wait: a late completion of an earlier one does nothing.
static NSUInteger ANNGateStepNumber;
static NSUInteger ANNGateWaitNumber;

@implementation ANNPresentationGate

+ (void)initialize {
    if (self == ANNPresentationGate.class) {
        ANNGateQueue = [NSMutableArray new];
    }
}

+ (void)enqueue:(ANNGateStep *)step {
    ANNAssertMainQueue();
    if (step.dropped) {
        return;
    }
    [ANNGateQueue addObject:step];
    [self pump];
}

+ (void)drop:(ANNGateStep *)step {
    ANNAssertMainQueue();
    [step markDropped];
    [ANNGateQueue removeObjectIdenticalTo:step];
}

+ (void)pump {
    ANNAssertMainQueue();
    if (ANNGateBusy || ANNGateWaiting || ANNGateSettling) {
        return;
    }
    while (ANNGateQueue.count > 0 && ANNGateQueue.firstObject.dropped) {
        [ANNGateQueue removeObjectAtIndex:0];
    }
    ANNGateStep *step = ANNGateQueue.firstObject;
    if (!step) {
        return;
    }
    UIViewController *root =
        step.transitionRoot ? step.transitionRoot() : ApexNavigation.mainWindow.rootViewController;
    id<UIViewControllerTransitionCoordinator> coordinator =
        [ANNRootCoordinator runningPresentationTransitionOver:root];
    if (coordinator) {
        [self waitFor:coordinator];
        return;
    }
    if (step.canRun && !step.canRun()) {
        return;
    }
    [ANNGateQueue removeObjectAtIndex:0];
    ANNGateBusy = YES;
    NSUInteger number = ++ANNGateStepNumber;
    __block BOOL finished = NO;
    dispatch_block_t done = ^{
      if (finished) {
          return;
      }
      finished = YES;
      if (ANNGateStepNumber != number) {
          return;
      }
      ANNGateBusy = NO;
      // UIKit finishes its own part of the transition after the completion: the next step on the next turn.
      ANNGateSettling = YES;
      dispatch_async(dispatch_get_main_queue(), ^{
        ANNGateSettling = NO;
        [ANNPresentationGate pump];
      });
    };
    @try {
        [step runWithDone:done];
    } @catch (NSException *exception) {
        // A step protects its own work; what escapes it still frees the gate.
        RCTLogWarn(@"[ApexNavigation] a presentation step failed: %@",
                   exception.reason ?: exception.name);
        done();
    }
}

// The completion of the running transition: the head is tried on the next turn. When the coordinator promises no
// completion, the next turn tries again.
+ (void)waitFor:(id<UIViewControllerTransitionCoordinator>)coordinator {
    ANNGateWaiting = YES;
    NSUInteger number = ++ANNGateWaitNumber;
    void (^again)(void) = ^{
      dispatch_async(dispatch_get_main_queue(), ^{
        if (ANNGateWaitNumber != number) {
            return;
        }
        ANNGateWaiting = NO;
        [ANNPresentationGate pump];
      });
    };
    BOOL queued = [coordinator
        animateAlongsideTransition:nil
                        completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
                          again();
                        }];
    if (!queued) {
        again();
    }
}

@end

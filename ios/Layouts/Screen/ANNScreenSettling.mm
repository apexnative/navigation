#import "ANNScreenSettling.h"
#import "ANNEventContract.h"
#import "ANNHinge.h"
#import <QuartzCore/QuartzCore.h>

NSNotificationName const ANNScreenSettlingDidEndNotification = @"ANNScreenSettlingDidEnd";

// How long the values of the screens stand still before the settling ends. On iPhone Duo UIKit's last step of a fold
// comes some 30 ms after the size transition's end, and the size transition some 50 ms after the bar's new edge.
static const CFTimeInterval ANNScreenSettlingQuietTime = 0.15;
// The settling holds the insets no longer than this after its last start, whatever keeps changing.
static const CFTimeInterval ANNScreenSettlingLongest = 1.0;
// The degrees the hinge turns before it counts as a move: a device held half open trembles a little.
static const double ANNScreenSettlingHingeStep = 2;

static BOOL ANNSettling;
// The size transitions that began and haven't ended: each screen counts its own call of the same transition.
static NSInteger ANNSettlingTransitions;
static CFTimeInterval ANNSettlingStart;
static CFTimeInterval ANNSettlingLastChange;
// Only the check scheduled last runs.
static NSUInteger ANNSettlingCheckGeneration;
// One more at each end: a transition that began before it no longer counts.
static NSUInteger ANNSettlingEpoch;
// The hinge's state and angle at its first value and at its last move that counted; none without a hinge.
static id ANNSettlingHingeState;
static NSNumber *ANNSettlingHingeAngle;

@implementation ANNScreenSettling

// The screens ask `isSettling` from their first layout on, before any fold.
+ (void)initialize {
    if (self == ANNScreenSettling.class) {
        [NSNotificationCenter.defaultCenter addObserver:self
                                               selector:@selector(hingeDidMove:)
                                                   name:ANNHingeDidMoveNotification
                                                 object:nil];
    }
}

// A fold starts with the hinge, and the display it shows on changes as it moves.
+ (void)hingeDidMove:(NSNotification *)notification {
    NSDictionary<NSString *, id> *value = [ANNHinge lastValue];
    const auto &keys = ANNEventContract.HingeChanged;
    id state = value[keys.state];
    NSNumber *angle = value[keys.angle];
    // The first value, and the hinge's coming or going, are no move.
    if (!state || !ANNSettlingHingeState) {
        ANNSettlingHingeState = state;
        ANNSettlingHingeAngle = angle;
        return;
    }
    double turned = fabs(angle.doubleValue - ANNSettlingHingeAngle.doubleValue);
    BOOL moved = ![state isEqual:ANNSettlingHingeState] || !angle || !ANNSettlingHingeAngle ||
                 turned >= ANNScreenSettlingHingeStep;
    if (!moved) {
        return;
    }
    ANNSettlingHingeState = state;
    ANNSettlingHingeAngle = angle;
    [self beginForTraitChange];
}

+ (void)beginWithTransition:(id<UIViewControllerTransitionCoordinator>)coordinator {
    if (![ANNHinge isPresent]) {
        return;
    }
    [self begin];
    ANNSettlingTransitions += 1;
    NSUInteger epoch = ANNSettlingEpoch;
    void (^ended)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          if (epoch != ANNSettlingEpoch) {
              return;
          }
          ANNSettlingTransitions = MAX(0, ANNSettlingTransitions - 1);
          [ANNScreenSettling valuesDidChange];
          [ANNScreenSettling checkAfter:ANNScreenSettlingQuietTime];
        };
    [coordinator animateAlongsideTransition:nil completion:ended];
    // The longest still holds should the transition's end never come.
    [self checkAfter:ANNScreenSettlingLongest];
}

// Only iPhone Duo has a vertical bar: no need to ask for the hinge.
+ (void)beginForTraitChange {
    [self begin];
    [self checkAfter:ANNScreenSettlingQuietTime];
}

+ (void)begin {
    CFTimeInterval now = CACurrentMediaTime();
    ANNSettling = YES;
    ANNSettlingStart = now;
    ANNSettlingLastChange = now;
}

+ (BOOL)isSettling {
    return ANNSettling;
}

+ (void)valuesDidChange {
    ANNSettlingLastChange = CACurrentMediaTime();
}

+ (void)checkAfter:(CFTimeInterval)delay {
    NSUInteger generation = ++ANNSettlingCheckGeneration;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delay * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     if (generation == ANNSettlingCheckGeneration) {
                         [ANNScreenSettling check];
                     }
                   });
}

// Ends once no transition runs and the values stood still for the quiet time, or at the longest; else looks again
// when that can be the case. A change since the check was scheduled moves the end on.
+ (void)check {
    if (!ANNSettling) {
        return;
    }
    CFTimeInterval now = CACurrentMediaTime();
    CFTimeInterval longest = ANNSettlingStart + ANNScreenSettlingLongest;
    CFTimeInterval quiet = ANNSettlingLastChange + ANNScreenSettlingQuietTime;
    if (now >= longest || (ANNSettlingTransitions == 0 && now >= quiet)) {
        ANNSettling = NO;
        ANNSettlingTransitions = 0;
        ANNSettlingEpoch += 1;
        [NSNotificationCenter.defaultCenter postNotificationName:ANNScreenSettlingDidEndNotification
                                                          object:nil];
        return;
    }
    CFTimeInterval next = ANNSettlingTransitions > 0 ? longest : MIN(quiet, longest);
    [self checkAfter:next - now];
}

@end

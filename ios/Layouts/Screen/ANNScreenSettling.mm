#import "ANNScreenSettling.h"
#import "ANNEventContract.h"
#import "ANNHinge.h"
#import <QuartzCore/QuartzCore.h>

NSNotificationName const ANNScreenSettlingDidEndNotification = @"ANNScreenSettlingDidEnd";

// How long the values of the screens stand still before the settling ends. On iPhone Duo UIKit's last step of a fold
// comes some 30 ms after the size transition's end, and the size transition some 50 ms after the bar's new edge.
static const CFTimeInterval ANNScreenSettlingQuietTime = 0.15;
// Once the size transition ended, UIKit's last steps come within some 30 ms: the screens are on the new display by
// then, and every held moment shows them with the old one's insets.
static const CFTimeInterval ANNScreenSettlingQuietTimeAfterTransition = 0.05;
// The settling holds the insets no longer than this after its last start, whatever keeps changing.
static const CFTimeInterval ANNScreenSettlingLongest = 1.0;
// The degrees the hinge turns before it counts as a move: a device held half open trembles a little.
static const double ANNScreenSettlingHingeStep = 2;

static BOOL ANNSettling;
// The size transitions that began and haven't ended: each screen counts its own call of the same transition.
static NSInteger ANNSettlingTransitions;
// A size transition of this settling ended: the hinge, still turning, no longer holds the insets.
static BOOL ANNSettlingTransitionEnded;
// Until when the hinge's turns after a size transition count for nothing: the hinge goes on turning to its end once
// the display switched, and holds nothing until it rests a while.
static CFTimeInterval ANNSettlingHingeIgnoredUntil;
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
    BOOL newState = ![state isEqual:ANNSettlingHingeState];
    BOOL moved =
        newState || !angle || !ANNSettlingHingeAngle || turned >= ANNScreenSettlingHingeStep;
    if (!moved) {
        return;
    }
    ANNSettlingHingeState = state;
    ANNSettlingHingeAngle = angle;
    // A new state may bring a new layout; a turn after a size transition only goes on to the hinge's end.
    CFTimeInterval now = CACurrentMediaTime();
    BOOL afterTransition = (ANNSettling && ANNSettlingTransitionEnded) ||
                           now < ANNSettlingHingeIgnoredUntil;
    if (!newState && afterTransition) {
        ANNSettlingHingeIgnoredUntil = now + ANNScreenSettlingQuietTime;
        return;
    }
    [self beginForTraitChange];
}

+ (void)beginWithTransition:(id<UIViewControllerTransitionCoordinator>)coordinator {
    if (![ANNHinge isPresent]) {
        return;
    }
    [self begin];
    ANNSettlingTransitionEnded = NO;
    ANNSettlingTransitions += 1;
    NSUInteger epoch = ANNSettlingEpoch;
    void (^ended)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          if (epoch != ANNSettlingEpoch) {
              return;
          }
          ANNSettlingTransitions = MAX(0, ANNSettlingTransitions - 1);
          if (ANNSettlingTransitions == 0) {
              ANNSettlingTransitionEnded = YES;
              ANNSettlingHingeIgnoredUntil = CACurrentMediaTime() + ANNScreenSettlingQuietTime;
          }
          [ANNScreenSettling valuesDidChange];
          [ANNScreenSettling checkAfter:[ANNScreenSettling quietTime]];
        };
    [coordinator animateAlongsideTransition:nil completion:ended];
    // The longest still holds should the transition's end never come.
    [self checkAfter:ANNScreenSettlingLongest];
}

// Only iPhone Duo has a vertical bar: no need to ask for the hinge.
+ (void)beginForTraitChange {
    // After the transition the bar's new edge is one of its last steps: it holds the values a little longer only.
    if (ANNSettling && ANNSettlingTransitionEnded) {
        [self valuesDidChange];
        [self checkAfter:[self quietTime]];
        return;
    }
    [self begin];
    [self checkAfter:ANNScreenSettlingQuietTime];
}

+ (CFTimeInterval)quietTime {
    return ANNSettlingTransitionEnded ? ANNScreenSettlingQuietTimeAfterTransition
                                      : ANNScreenSettlingQuietTime;
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
    CFTimeInterval quiet = ANNSettlingLastChange + [self quietTime];
    if (now >= longest || (ANNSettlingTransitions == 0 && now >= quiet)) {
        ANNSettling = NO;
        ANNSettlingTransitions = 0;
        ANNSettlingTransitionEnded = NO;
        ANNSettlingEpoch += 1;
        [NSNotificationCenter.defaultCenter postNotificationName:ANNScreenSettlingDidEndNotification
                                                          object:nil];
        return;
    }
    CFTimeInterval next = ANNSettlingTransitions > 0 ? longest : MIN(quiet, longest);
    [self checkAfter:next - now];
}

@end

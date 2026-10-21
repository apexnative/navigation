#import "ANNCurve.h"
#import "ANNInterpolationOptions.h"
#import "ANNTrack.h"
#import <React/RCTLog.h>
#import <cmath>

// The spring's defaults, also for a `mass`, `damping` or `stiffness` not above 0.
static const double ANNSpringMass = 3;
static const double ANNSpringDamping = 500;
static const double ANNSpringStiffness = 200;

// The number of steps of the `fastOutSlowIn` table: 201 values.
static const int ANNFastOutSlowInSteps = 200;

// One coordinate of the cubic Bézier from 0 to 1 with the control values `a` and `b`, at its parameter `s`.
static double ANNBezier(double s, double a, double b) {
    double r = 1 - s;
    return 3 * r * r * s * a + 3 * r * s * s * b + s * s * s;
}

// The table, computed once by our own code: the curve's y at x = i/200, the parameter found by bisection, rounded to 4
// decimals.
static const double *ANNFastOutSlowInTable(void) {
    static double table[ANNFastOutSlowInSteps + 1];
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      for (int i = 0; i <= ANNFastOutSlowInSteps; i++) {
          double x = (double)i / ANNFastOutSlowInSteps;
          double low = 0;
          double high = 1;
          for (int step = 0; step < 60; step++) {
              double middle = (low + high) / 2;
              if (ANNBezier(middle, 0.4, 0.2) < x) {
                  low = middle;
              } else {
                  high = middle;
              }
          }
          double y = ANNBezier((low + high) / 2, 0, 1);
          table[i] = std::round(y * 10000) / 10000;
      }
    });
    return table;
}

double ANNFastOutSlowIn(double p) {
    if (p <= 0) {
        return 0;
    }
    if (p >= 1) {
        return 1;
    }
    const double *table = ANNFastOutSlowInTable();
    double position = p * ANNFastOutSlowInSteps;
    int index = MIN((int)position, ANNFastOutSlowInSteps - 1);
    double weight = position - index;
    return table[index] + weight * (table[index + 1] - table[index]);
}

static ANNCurveType ANNCurveTypeNamed(NSString *name) {
    static NSDictionary<NSString *, NSNumber *> *types;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      types = @{
          @"linear" : @(ANNCurveTypeLinear),
          @"accelerate" : @(ANNCurveTypeAccelerate),
          @"decelerate" : @(ANNCurveTypeDecelerate),
          @"accelerateDecelerate" : @(ANNCurveTypeAccelerateDecelerate),
          @"decelerateAccelerate" : @(ANNCurveTypeDecelerateAccelerate),
          @"fastOutSlowIn" : @(ANNCurveTypeFastOutSlowIn),
          @"overshoot" : @(ANNCurveTypeOvershoot),
          @"spring" : @(ANNCurveTypeSpring),
      };
    });
    NSNumber *type = name ? types[name] : nil;
    return type ? (ANNCurveType)type.integerValue : ANNCurveTypeLinear;
}

@implementation ANNCurve {
    double _factor;
    double _tension;
    // The spring: its damping ζ = c / 2m (critical at most without `allowsOverdamping`), its own frequency ω₀ = √(k/m),
    // the start velocity and the property's duration in seconds.
    double _zeta;
    double _omega;
    double _velocity;
    NSTimeInterval _duration;
}

+ (instancetype)curveWithOptions:(ANNInterpolationOptions *)options
                        duration:(NSTimeInterval)duration {
    ANNCurveType type = ANNCurveTypeNamed([options.type withDefault:nil]);
    return [[self alloc] initWithType:type
                               factor:[options.factor withDefault:1]
                              tension:[options.tension withDefault:1]
                                 mass:[options.mass withDefault:ANNSpringMass]
                              damping:[options.damping withDefault:ANNSpringDamping]
                            stiffness:[options.stiffness withDefault:ANNSpringStiffness]
                    allowsOverdamping:[options.allowsOverdamping withDefault:NO]
                      initialVelocity:[options.initialVelocity withDefault:0]
                             duration:duration];
}

+ (instancetype)linear {
    return [[self alloc] initWithType:ANNCurveTypeLinear
                               factor:1
                              tension:1
                                 mass:ANNSpringMass
                              damping:ANNSpringDamping
                            stiffness:ANNSpringStiffness
                    allowsOverdamping:NO
                      initialVelocity:0
                             duration:0];
}

- (instancetype)initWithType:(ANNCurveType)type
                      factor:(double)factor
                     tension:(double)tension
                        mass:(double)mass
                     damping:(double)damping
                   stiffness:(double)stiffness
           allowsOverdamping:(BOOL)allowsOverdamping
             initialVelocity:(double)initialVelocity
                    duration:(NSTimeInterval)duration {
    if (self = [super init]) {
        _type = type;
        _factor = factor;
        _tension = tension;
        double m = mass > 0 ? mass : ANNSpringMass;
        double c = damping > 0 ? damping : ANNSpringDamping;
        double k = stiffness > 0 ? stiffness : ANNSpringStiffness;
        _omega = std::sqrt(k / m);
        _zeta = c / (2 * m);
        // Without `allowsOverdamping` a damping above the critical one counts as critical: the spring comes as fast as
        // it can without swinging, it doesn't crawl.
        if (!allowsOverdamping && _zeta > _omega) {
            _zeta = _omega;
        }
        _velocity = initialVelocity;
        _duration = duration > 0 ? duration : 0;
        if (type == ANNCurveTypeFastOutSlowIn) {
            ANNFastOutSlowInTable();
        }
    }
    return self;
}

- (double)valueAt:(double)p {
    switch (_type) {
        case ANNCurveTypeLinear:
            return p;
        case ANNCurveTypeAccelerate:
            return std::pow(p, 2 * _factor);
        case ANNCurveTypeDecelerate:
            return 1 - std::pow(1 - p, 2 * _factor);
        case ANNCurveTypeAccelerateDecelerate:
            // Slow at both ends, the mirror of `decelerateAccelerate`.
            return p < 0.5 ? 4 * p * p * p : 1 - 4 * (1 - p) * (1 - p) * (1 - p);
        case ANNCurveTypeDecelerateAccelerate: {
            // Fast at both ends, no speed in the middle, f(0) = 0 and f(1) = 1.
            double d = p - 0.5;
            return 0.5 + 4 * d * d * d;
        }
        case ANNCurveTypeFastOutSlowIn:
            return ANNFastOutSlowIn(p);
        case ANNCurveTypeOvershoot: {
            double r = p - 1;
            return 1 + r * r * ((_tension + 1) * r + _tension);
        }
        case ANNCurveTypeSpring:
            return [self springAt:p];
    }
    return p;
}

// The mass on a spring with damping: m·x″ + c·x′ + k·x = 0, x(0) = −1, x′(0) = `initialVelocity`, f = 1 + x(t), with t
// = p × the property's duration in seconds.
- (double)springAt:(double)p {
    double t = p * _duration;
    double zeta = _zeta;
    double omega = _omega;
    double velocity = _velocity;
    double x;
    if (std::fabs(zeta - omega) < 1e-9) {
        x = std::exp(-zeta * t) * (-1 + (velocity - zeta) * t);
    } else if (zeta < omega) {
        double damped = std::sqrt(omega * omega - zeta * zeta);
        x = std::exp(-zeta * t) *
            (-std::cos(damped * t) + (velocity - zeta) / damped * std::sin(damped * t));
    } else {
        double damped = std::sqrt(zeta * zeta - omega * omega);
        x = std::exp(-zeta * t) *
            (-std::cosh(damped * t) + (velocity - zeta) / damped * std::sinh(damped * t));
    }
    return 1 + x;
}

@end

#if DEBUG

// The vectors shared with the Android tests: f at p = 0,25, 0,5, 0,75, computed outside this code from the formulas of
// the curves.
typedef struct {
    const char *name;
    ANNCurveType type;
    double factor, tension, mass, damping, stiffness;
    BOOL overdamping;
    double velocity, duration;
    double expected[3];
} ANNCurveVector;

static const ANNCurveVector ANNCurveVectors[] = {
    {"linear", ANNCurveTypeLinear, 1, 1, 3, 500, 200, NO, 0, 0, {0.25, 0.5, 0.75}},
    {"accelerate", ANNCurveTypeAccelerate, 1, 1, 3, 500, 200, NO, 0, 0, {0.0625, 0.25, 0.5625}},
    {"accelerate 1.5", ANNCurveTypeAccelerate, 1.5, 1, 3, 500, 200, NO, 0, 0,
     {0.015625, 0.125, 0.421875}},
    {"decelerate", ANNCurveTypeDecelerate, 1, 1, 3, 500, 200, NO, 0, 0, {0.4375, 0.75, 0.9375}},
    {"decelerate 2", ANNCurveTypeDecelerate, 2, 1, 3, 500, 200, NO, 0, 0,
     {0.683594, 0.9375, 0.996094}},
    {"accelerateDecelerate", ANNCurveTypeAccelerateDecelerate, 1, 1, 3, 500, 200, NO, 0, 0,
     {0.0625, 0.5, 0.9375}},
    {"decelerateAccelerate", ANNCurveTypeDecelerateAccelerate, 1, 1, 3, 500, 200, NO, 0, 0,
     {0.4375, 0.5, 0.5625}},
    {"fastOutSlowIn", ANNCurveTypeFastOutSlowIn, 1, 1, 3, 500, 200, NO, 0, 0,
     {0.2366, 0.7756, 0.9594}},
    {"overshoot", ANNCurveTypeOvershoot, 1, 1, 3, 500, 200, NO, 0, 0, {0.71875, 1, 1.03125}},
    {"overshoot 2", ANNCurveTypeOvershoot, 1, 2, 3, 500, 200, NO, 0, 0,
     {0.859375, 1.125, 1.078125}},
    {"spring defaults 300 ms", ANNCurveTypeSpring, 1, 1, 3, 500, 200, NO, 0, 0.3,
     {0.125992, 0.346297, 0.548116}},
    {"spring 1/10/200 1 s", ANNCurveTypeSpring, 1, 1, 1, 10, 200, NO, 0, 1,
     {1.300436, 0.912287, 1.024911}},
    {"spring 1/40/100 overdamped 0.5 s", ANNCurveTypeSpring, 1, 1, 1, 40, 100, YES, 0, 0.5,
     {0.230010, 0.448647, 0.605567}},
    {"spring 0/-1/0 300 ms", ANNCurveTypeSpring, 1, 1, 0, -1, 0, NO, 0, 0.3,
     {0.125992, 0.346297, 0.548116}},
    {"spring velocity 5 300 ms", ANNCurveTypeSpring, 1, 1, 3, 500, 200, NO, 5, 0.3,
     {0.329266, 0.566672, 0.727301}},
};

static void ANNExpect(const char *what, double value, double expected, double tolerance) {
    if (!(std::fabs(value - expected) <= tolerance)) {
        RCTLogWarn(@"[ApexNavigation] curve self-check: %s gave %.6f, expected %.6f", what, value,
                   expected);
    }
}

void ANNCurveSelfCheck(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      const double points[] = {0.25, 0.5, 0.75};
      for (const ANNCurveVector &vector : ANNCurveVectors) {
          ANNCurve *curve = [[ANNCurve alloc] initWithType:vector.type
                                                    factor:vector.factor
                                                   tension:vector.tension
                                                      mass:vector.mass
                                                   damping:vector.damping
                                                 stiffness:vector.stiffness
                                         allowsOverdamping:vector.overdamping
                                           initialVelocity:vector.velocity
                                                  duration:vector.duration];
          for (int i = 0; i < 3; i++) {
              ANNExpect(vector.name, [curve valueAt:points[i]], vector.expected[i], 1e-6);
          }
      }
      // The table between its values, and against the Bézier as a sanity check only.
      ANNExpect("fastOutSlowIn at 0.123", ANNFastOutSlowIn(0.123), 0.041440, 1e-6);
      ANNExpect("fastOutSlowIn against the Bezier at 0.123", ANNFastOutSlowIn(0.123), 0.041397,
                1e-4);
      // The timeline: the delay holds `from`, the end is exactly `to`, `duration` 0 is `to` once the delay passed.
      ANNCurve *spring = [[ANNCurve alloc] initWithType:ANNCurveTypeSpring
                                                 factor:1
                                                tension:1
                                                   mass:3
                                                damping:500
                                              stiffness:200
                                      allowsOverdamping:NO
                                        initialVelocity:0
                                               duration:0.3];
      ANNTrack *track = [[ANNTrack alloc] initWithFrom:10
                                                    to:20
                                              duration:0.3
                                                 delay:0.1
                                                 curve:spring];
      ANNExpect("track in its delay", [track valueAt:0.05], 10, 0);
      ANNExpect("track at its start", [track valueAt:0.1], 10, 0);
      ANNExpect("track in its middle", [track valueAt:0.25], 10 + 10 * 0.346297, 1e-5);
      ANNExpect("track at its end", [track valueAt:0.4], 20, 0);
      ANNExpect("track after its end", [track valueAt:5], 20, 0);
      ANNTrack *instant = [[ANNTrack alloc] initWithFrom:1
                                                      to:0
                                                duration:0
                                                   delay:0.2
                                                   curve:[ANNCurve linear]];
      ANNExpect("track of duration 0 in its delay", [instant valueAt:0.1], 1, 0);
      ANNExpect("track of duration 0 after its delay", [instant valueAt:0.2], 0, 0);
    });
}

#endif

#import <Foundation/Foundation.h>

@class ANNInterpolationOptions;

NS_ASSUME_NONNULL_BEGIN

/// The curves of `interpolation`.
typedef NS_ENUM(NSInteger, ANNCurveType) {
    ANNCurveTypeLinear,
    ANNCurveTypeAccelerate,
    ANNCurveTypeDecelerate,
    ANNCurveTypeAccelerateDecelerate,
    ANNCurveTypeDecelerateAccelerate,
    ANNCurveTypeFastOutSlowIn,
    ANNCurveTypeOvershoot,
    ANNCurveTypeSpring,
};

/// One curve f(p): the share of the way at the share of the time p (0…1). Missing, unknown or not an object: `linear`.
/// The spring runs in the real time of its property: it takes the property's duration. Pure, immutable, any queue.
@interface ANNCurve : NSObject

/// The curve of `options` for a property of `duration` seconds (the spring's time is p × `duration`).
+ (instancetype)curveWithOptions:(nullable ANNInterpolationOptions *)options
                        duration:(NSTimeInterval)duration;

/// The linear curve.
+ (instancetype)linear;

- (instancetype)initWithType:(ANNCurveType)type
                      factor:(double)factor
                     tension:(double)tension
                        mass:(double)mass
                     damping:(double)damping
                   stiffness:(double)stiffness
           allowsOverdamping:(BOOL)allowsOverdamping
             initialVelocity:(double)initialVelocity
                    duration:(NSTimeInterval)duration NS_DESIGNATED_INITIALIZER;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, readonly) ANNCurveType type;

/// f(p) as the formula gives it, for p in 0…1 (the spring and `overshoot` may leave 0…1 on the way). The ends of a
/// property are exact elsewhere (`ANNTrack`).
- (double)valueAt:(double)p;

@end

/// The value of `fastOutSlowIn` at p: the 201 values of the Bézier (0,4; 0; 0,2; 1) at x = i/200, rounded to 4
/// decimals, with linear interpolation between them.
FOUNDATION_EXPORT double ANNFastOutSlowIn(double p);

#if DEBUG
/// Development only (there is no iOS test target): checks the curves, the table and the property timeline against the
/// vectors shared with the Android tests, once; a mismatch is logged.
FOUNDATION_EXPORT void ANNCurveSelfCheck(void);
#endif

NS_ASSUME_NONNULL_END

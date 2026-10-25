#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A 4×4 transform split into the parts it is made of (the decomposition of CSS Transforms Level 2): applied to a point
/// in the order scale, skew, rotation, translation, perspective. Two transforms interpolate part by part, the rotation
/// along the shorter arc between the two (a quaternion's slerp), so a rotation never collapses on the way.
typedef struct {
    double translate[3];
    double scale[3];
    /// The shears xy, xz, yz.
    double skew[3];
    double perspective[4];
    /// x, y, z, w.
    double quaternion[4];
} ANNTransformParts;

/// The parts of no transform.
FOUNDATION_EXPORT ANNTransformParts ANNTransformPartsIdentity(void);

/// The parts of `transform` into `parts`; NO when it can't be split (a scale of 0 flattens it).
FOUNDATION_EXPORT BOOL ANNTransformPartsOf(CATransform3D transform, ANNTransformParts *parts);

/// The transform the parts make.
FOUNDATION_EXPORT CATransform3D ANNTransformOfParts(ANNTransformParts parts);

/// The parts at `f` of the way: each number from + f·(to − from), the rotation by slerp along the shorter arc; exactly
/// `from` at f = 0 and `to` at f = 1. Two rotations half a turn apart go either way.
FOUNDATION_EXPORT ANNTransformParts ANNInterpolateTransformParts(ANNTransformParts from,
                                                                 ANNTransformParts to, double f);

/// The rotation of the parts as the turns about x, then y, then z (degrees, each in −180…180), the order a view's
/// tracks compose them in (`ANNViewTracks`).
FOUNDATION_EXPORT void ANNRotationDegreesOf(ANNTransformParts parts, double *x, double *y,
                                           double *z);

/// The shears of the parts as a transform of their own (the scale, rotation and the rest aside).
FOUNDATION_EXPORT CATransform3D ANNSkewTransformOf(ANNTransformParts parts);

#if DEBUG
/// Development only (there is no iOS test target): splits and rebuilds known transforms and checks the rotations a
/// shared element takes (0° → 90°, 0° → 180°, 45° → −45°, 270°, 170° → −170°, a scale, a tilt), once; a mismatch is
/// logged.
FOUNDATION_EXPORT void ANNTransformPartsSelfCheck(void);
#endif

NS_ASSUME_NONNULL_END

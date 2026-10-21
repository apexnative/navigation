#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// The interpolation of values: from + f·(to − from) per component, where f is the share of the way a track gives
// (`ANNTrack fractionAt:`); exactly `from` at f = 0 and exactly `to` at f = 1, whatever the floating point would give.
// iOS never interpolates colors: a shared element flies as pictures.

/// A number.
static inline CGFloat ANNInterpolateNumber(CGFloat from, CGFloat to, double f) {
    if (f == 0) {
        return from;
    }
    if (f == 1) {
        return to;
    }
    return from + (CGFloat)f * (to - from);
}

/// A point: x and y apart.
static inline CGPoint ANNInterpolatePoint(CGPoint from, CGPoint to, double f) {
    return CGPointMake(ANNInterpolateNumber(from.x, to.x, f),
                       ANNInterpolateNumber(from.y, to.y, f));
}

/// A size: width and height apart.
static inline CGSize ANNInterpolateSize(CGSize from, CGSize to, double f) {
    return CGSizeMake(ANNInterpolateNumber(from.width, to.width, f),
                      ANNInterpolateNumber(from.height, to.height, f));
}

/// A rectangle: its origin and its size.
static inline CGRect ANNInterpolateRect(CGRect from, CGRect to, double f) {
    CGPoint origin = ANNInterpolatePoint(from.origin, to.origin, f);
    CGSize size = ANNInterpolateSize(from.size, to.size, f);
    return CGRectMake(origin.x, origin.y, size.width, size.height);
}

/// A 4×4 transform, element by element (a shared element with a `transform` style, as iOS does it now).
static inline CATransform3D ANNInterpolateTransform(CATransform3D from, CATransform3D to,
                                                    double f) {
    if (f == 0) {
        return from;
    }
    if (f == 1) {
        return to;
    }
    CATransform3D t;
    t.m11 = ANNInterpolateNumber(from.m11, to.m11, f);
    t.m12 = ANNInterpolateNumber(from.m12, to.m12, f);
    t.m13 = ANNInterpolateNumber(from.m13, to.m13, f);
    t.m14 = ANNInterpolateNumber(from.m14, to.m14, f);
    t.m21 = ANNInterpolateNumber(from.m21, to.m21, f);
    t.m22 = ANNInterpolateNumber(from.m22, to.m22, f);
    t.m23 = ANNInterpolateNumber(from.m23, to.m23, f);
    t.m24 = ANNInterpolateNumber(from.m24, to.m24, f);
    t.m31 = ANNInterpolateNumber(from.m31, to.m31, f);
    t.m32 = ANNInterpolateNumber(from.m32, to.m32, f);
    t.m33 = ANNInterpolateNumber(from.m33, to.m33, f);
    t.m34 = ANNInterpolateNumber(from.m34, to.m34, f);
    t.m41 = ANNInterpolateNumber(from.m41, to.m41, f);
    t.m42 = ANNInterpolateNumber(from.m42, to.m42, f);
    t.m43 = ANNInterpolateNumber(from.m43, to.m43, f);
    t.m44 = ANNInterpolateNumber(from.m44, to.m44, f);
    return t;
}

NS_ASSUME_NONNULL_END

#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// The interpolation of values: from + f·(to − from) per component, where f is the share of the way a track gives
// (`ANNTrack fractionAt:`); exactly `from` at f = 0 and exactly `to` at f = 1, whatever the floating point would give.
// iOS never interpolates colors: a shared element flies as pictures. A transform goes by its parts
// (`ANNTransformParts`).

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

NS_ASSUME_NONNULL_END

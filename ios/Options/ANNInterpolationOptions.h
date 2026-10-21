#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `Interpolation`: the curve of an animation. A union in TS, one group here: `type` tells which of the other fields
/// count (`factor` for `accelerate` and `decelerate`, `tension` for `overshoot`, the rest for `spring`).
@interface ANNInterpolationOptions : ANNOptions

/// `'linear'`, `'accelerate'`, `'decelerate'`, `'accelerateDecelerate'`, `'decelerateAccelerate'`, `'fastOutSlowIn'`,
/// `'overshoot'` or `'spring'`.
@property(nonatomic, strong) Text *type;
@property(nonatomic, strong) Double *factor;
@property(nonatomic, strong) Double *tension;
@property(nonatomic, strong) Double *mass;
@property(nonatomic, strong) Double *damping;
@property(nonatomic, strong) Double *stiffness;
@property(nonatomic, strong) Bool *allowsOverdamping;
@property(nonatomic, strong) Double *initialVelocity;

@end

NS_ASSUME_NONNULL_END

#import "ANNParam.h"

NS_ASSUME_NONNULL_BEGIN

/// A number with a fraction (a radius, a fraction of the width).
@interface Double : ANNParam

/// The value; 0 without one.
- (double)get;
- (double)withDefault:(double)defaultValue;

@end

NS_ASSUME_NONNULL_END

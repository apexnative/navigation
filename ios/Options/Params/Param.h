#import "ANNParam.h"

NS_ASSUME_NONNULL_BEGIN

/// A value of any JSON type, kept as it came (a detent: a name or a height).
@interface Param : ANNParam

/// The value; nil without one and for `null`.
- (nullable id)get;

/// The value, or `defaultValue` when the options gave none; nil for `null`.
- (nullable id)withDefault:(nullable id)defaultValue;

@end

NS_ASSUME_NONNULL_END

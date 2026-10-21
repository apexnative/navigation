#import "Double.h"

NS_ASSUME_NONNULL_BEGIN

/// Reads `key` of the options' JSON as a number with a fraction.
@interface DoubleParser : NSObject

/// A finite number; a boolean, `NaN` or an infinity is no value.
+ (Double *)parse:(nullable NSDictionary *)json key:(NSString *)key;

/// A duration in milliseconds: a finite number that is not negative.
+ (Double *)parseDuration:(nullable NSDictionary *)json key:(NSString *)key;

@end

NS_ASSUME_NONNULL_END

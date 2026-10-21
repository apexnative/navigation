#import "Bool.h"

NS_ASSUME_NONNULL_BEGIN

/// Reads `key` of the options' JSON: `true` or `false`; anything else, a number included, is no value.
@interface BoolParser : NSObject

+ (Bool *)parse:(nullable NSDictionary *)json key:(NSString *)key;

@end

NS_ASSUME_NONNULL_END

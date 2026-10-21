#import "Number.h"

NS_ASSUME_NONNULL_BEGIN

/// Reads `key` of the options' JSON: a finite number, kept as `NSNumber` (a boolean, `NaN` or an infinity is no
/// value).
@interface NumberParser : NSObject

+ (Number *)parse:(nullable NSDictionary *)json key:(NSString *)key;

@end

NS_ASSUME_NONNULL_END

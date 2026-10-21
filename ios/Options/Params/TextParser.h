#import "Text.h"

NS_ASSUME_NONNULL_BEGIN

/// Reads `key` of the options' JSON as a text.
@interface TextParser : NSObject

/// A string, or `null`: an explicit "no text" that replaces what the layers under it give.
+ (Text *)parse:(nullable NSDictionary *)json key:(NSString *)key;

/// A value of an enumeration: one of `values`; an unknown string, `null` or another type is no value
/// (the option's default applies).
+ (Text *)parse:(nullable NSDictionary *)json key:(NSString *)key values:(NSSet<NSString *> *)values;

@end

NS_ASSUME_NONNULL_END

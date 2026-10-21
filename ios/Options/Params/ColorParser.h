#import "Color.h"

NS_ASSUME_NONNULL_BEGIN

/// Reads `key` of the options' JSON as a color, in the forms JS sends on iOS: an ARGB number, `{ dynamic: { light, dark
/// } }` with a number or a platform color on each side, `{ semantic: [...] }`, and `'NoColor'` (or `null`) for "no
/// color". A value it can't read is no value and a warning in development.
@interface ColorParser : NSObject

+ (Color *)parse:(nullable NSDictionary *)json key:(NSString *)key;

@end

NS_ASSUME_NONNULL_END

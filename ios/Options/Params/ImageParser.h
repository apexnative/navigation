#import "Image.h"

NS_ASSUME_NONNULL_BEGIN

/// Reads `key` of the options' JSON as the source of an image, which loads when an element shows it (`Image`):
/// `{ system, fallback? }` is an SF Symbol, else the fallback source; a source (a string, or an object with `uri`,
/// `bundle`, `scale`, `width`, `height` and Metro's asset flag) is a file, an asset of the app's bundle, a `data:`
/// address, or in development a Metro asset. Remote addresses and arrays of sources are no image, with an error in the
/// React Native log; it never throws.
@interface ImageParser : NSObject

+ (Image *)parse:(nullable NSDictionary *)json key:(NSString *)key;

@end

NS_ASSUME_NONNULL_END

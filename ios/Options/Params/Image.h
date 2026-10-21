#import "Param.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// An image of the options, loaded when the options were read. An image that didn't load is no value: the element shows
/// without it.
@interface Image : Param

- (nullable UIImage *)get;
- (nullable UIImage *)withDefault:(nullable UIImage *)defaultValue;

@end

NS_ASSUME_NONNULL_END

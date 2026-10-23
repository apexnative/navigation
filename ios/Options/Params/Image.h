#import "ANNImageSource.h"
#import "Param.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// An image of the options: its source, read with the options, and the image, loaded when an element asks for it
/// through the shared cache (`ANNOptionImages`). A source that doesn't load is no image: the element shows without it.
@interface Image : Param

/// The image, loaded at once when the cache doesn't have it; nil without a source and when it doesn't load.
- (nullable UIImage *)get;
/// The image, or `defaultValue` without a source and when it doesn't load. `hasValue` says only that the options name
/// an image: a choice between an image and what shows without one reads the image itself.
- (nullable UIImage *)withDefault:(nullable UIImage *)defaultValue;

/// What the options name; nil without a value.
@property(nonatomic, strong, readonly, nullable) ANNImageSource *source;

@end

NS_ASSUME_NONNULL_END

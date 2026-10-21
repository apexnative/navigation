#import "ANNComponentOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `OptionsTopBarButtonComponent`: the React component of a bar button, with the size it asks for.
@interface ANNButtonComponentOptions : ANNComponentOptions

@property(nonatomic, strong) Double *width;
@property(nonatomic, strong) Double *height;

@end

NS_ASSUME_NONNULL_END

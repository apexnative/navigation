#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `window` (`WindowOptions`): the app's window, from the default options only.
@interface ANNWindowOptions : ANNOptions

@property(nonatomic, strong) Color *backgroundColor;

@end

NS_ASSUME_NONNULL_END

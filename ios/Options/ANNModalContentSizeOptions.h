#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `modal.contentSize` (`OptionsModalContentSize`): the size of a form sheet or a popover. `height` is a number or
/// `'content'`.
@interface ANNModalContentSizeOptions : ANNOptions

@property(nonatomic, strong) Double *width;
@property(nonatomic, strong) Param *height;

@end

NS_ASSUME_NONNULL_END

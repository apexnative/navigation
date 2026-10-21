#import "ANNModalContentSizeOptions.h"
#import "ANNOptions.h"
#import "ANNPopoverOptions.h"
#import "ANNSheetOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `modal` (`ModalOptions`): how a modal shows. The sheet, the popover and the size go to their modules.
@interface ANNModalOptions : ANNOptions

@property(nonatomic, strong) ANNSheetOptions *sheet;
@property(nonatomic, strong) ANNModalContentSizeOptions *contentSize;
@property(nonatomic, strong) ANNPopoverOptions *popover;
@property(nonatomic, strong) Text *testID;
@property(nonatomic, strong) Bool *swipeToDismiss;

@end

NS_ASSUME_NONNULL_END

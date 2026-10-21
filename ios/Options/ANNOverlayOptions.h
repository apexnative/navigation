#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `overlay` (`OverlayOptions`): how an overlay takes touches.
@interface ANNOverlayOptions : ANNOptions

/// Touches outside the overlay's views stay with the overlay instead of reaching the screens under it.
@property(nonatomic, strong) Bool *interceptTouchOutside;
@property(nonatomic, strong) Text *testID;

@end

NS_ASSUME_NONNULL_END

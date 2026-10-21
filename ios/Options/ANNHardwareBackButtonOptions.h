#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `hardwareBackButton` (`HardwareBackButtonOptions`): what the system back does.
@interface ANNHardwareBackButtonOptions : ANNOptions

/// `'exit'`, `'first'` or `'previous'`.
@property(nonatomic, strong) Text *bottomTabsOnPress;
@property(nonatomic, strong) Bool *popStackOnPress;
@property(nonatomic, strong) Bool *dismissModalOnPress;

@end

NS_ASSUME_NONNULL_END

#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `bottomTab.dotIndicator` (`DotIndicatorOptions`): the dot on a tab.
@interface ANNBottomTabDotIndicatorOptions : ANNOptions

@property(nonatomic, strong) Color *color;
@property(nonatomic, strong) Double *size;
@property(nonatomic, strong) Bool *visible;

@end

NS_ASSUME_NONNULL_END

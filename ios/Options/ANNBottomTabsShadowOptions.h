#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `bottomTabs.shadow` (`ShadowOptions`): the shadow of the tab bar.
@interface ANNBottomTabsShadowOptions : ANNOptions

@property(nonatomic, strong) Double *opacity;
@property(nonatomic, strong) Color *color;
@property(nonatomic, strong) Double *radius;

@end

NS_ASSUME_NONNULL_END

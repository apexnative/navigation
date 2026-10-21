#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `bottomTabs.customRow` (`BottomTabsCustomRowOptions`): the row of tabs the app draws itself.
@interface ANNBottomTabsCustomRowOptions : ANNOptions

@property(nonatomic, strong) Double *height;
@property(nonatomic, strong) Color *backgroundColor;
/// `'glass'`, `'blur'` or `'none'`.
@property(nonatomic, strong) Text *backgroundEffect;
@property(nonatomic, strong) Double *cornerRadius;
@property(nonatomic, strong) Double *horizontalMargin;
@property(nonatomic, strong) Double *bottomMargin;
@property(nonatomic, strong) Text *testID;

@end

NS_ASSUME_NONNULL_END

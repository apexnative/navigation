#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `fab` (`OptionsFab`): the floating action button and, in `actions`, the buttons of its menu.
@interface ANNFabOptions : ANNOptions

@property(nonatomic, strong) Text *identifier;
@property(nonatomic, strong) Text *testID;
@property(nonatomic, strong) Color *backgroundColor;
@property(nonatomic, strong) Color *clickColor;
@property(nonatomic, strong) Color *rippleColor;
@property(nonatomic, strong) Bool *visible;
@property(nonatomic, strong) Image *icon;
@property(nonatomic, strong) Color *iconColor;
/// `'left'` or `'right'`.
@property(nonatomic, strong) Text *alignHorizontally;
@property(nonatomic, strong) Bool *hideOnScroll;
/// `'mini'` or `'regular'`.
@property(nonatomic, strong) Text *size;
@property(nonatomic, strong, nullable) NSArray<ANNFabOptions *> *actions;
@property(nonatomic, strong) Color *scrimColor;

@end

NS_ASSUME_NONNULL_END

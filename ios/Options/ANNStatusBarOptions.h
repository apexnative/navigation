#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `statusBar` (`OptionsStatusBar`).
@interface ANNStatusBarOptions : ANNOptions

@property(nonatomic, strong) Bool *visible;
/// `'light'` or `'dark'`.
@property(nonatomic, strong) Text *style;
@property(nonatomic, strong) Color *backgroundColor;
@property(nonatomic, strong) Bool *drawBehind;
@property(nonatomic, strong) Bool *translucent;
@property(nonatomic, strong) Bool *animate;
@property(nonatomic, strong) Bool *hideWithTopBar;
@property(nonatomic, strong) Bool *blur;

@end

NS_ASSUME_NONNULL_END

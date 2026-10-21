#import "ANNFontOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.largeTitle` (`OptionsTopBarLargeTitle`).
@interface ANNTopBarLargeTitleOptions : ANNOptions

@property(nonatomic, strong) Bool *visible;
/// `'always'`, `'never'`, `'automatic'` or `'inline'`.
@property(nonatomic, strong) Text *displayMode;
@property(nonatomic, strong) Bool *collapseOnScroll;
@property(nonatomic, strong) Color *color;
/// `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`.
@property(nonatomic, strong) ANNFontOptions *font;

@end

NS_ASSUME_NONNULL_END

#import "ANNFontOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.subtitle` (`OptionsTopBarSubtitle`): the line under the title.
@interface ANNTopBarSubtitleOptions : ANNOptions

@property(nonatomic, strong) Text *text;
/// `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`.
@property(nonatomic, strong) ANNFontOptions *font;
@property(nonatomic, strong) Color *color;
/// `'center'`.
@property(nonatomic, strong) Text *alignment;

@end

NS_ASSUME_NONNULL_END

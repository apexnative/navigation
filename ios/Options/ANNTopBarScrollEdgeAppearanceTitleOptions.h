#import "ANNFontOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `topBar.scrollEdgeAppearance.title` (`OptionsTopBarScrollEdgeAppearanceTitle`): the title when the content is at
/// the edge of the bar.
@interface ANNTopBarScrollEdgeAppearanceTitleOptions : ANNOptions

/// `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`.
@property(nonatomic, strong) ANNFontOptions *font;
@property(nonatomic, strong) Color *color;

@end

NS_ASSUME_NONNULL_END

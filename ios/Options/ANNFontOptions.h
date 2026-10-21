#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// The font of a text of the options: `fontFamily`, `fontSize`, `fontWeight` and `fontStyle`, read from the text's own
/// object (`+[ANNOptionField fontField]`). The color of the text is a separate option.
@interface ANNFontOptions : ANNOptions

@property(nonatomic, strong) Text *fontFamily;
/// In points; no Dynamic Type.
@property(nonatomic, strong) Double *fontSize;
/// `'normal'`, `'bold'`, `'100'`…`'900'` or a weight of UIKit by name; another name draws the normal weight.
@property(nonatomic, strong) Text *fontWeight;
/// `'normal'` or `'italic'`.
@property(nonatomic, strong) Text *fontStyle;

/// The font of the place with these fields, built as React Native builds the font of a text (`RCTFont`): the family
/// by its name (an app's font, else a system one), the size (else the place's, else 17), the weight and the slant.
/// Nil when none of the four is set: the place keeps its system font.
- (nullable UIFont *)fontWithPlaceFont:(nullable UIFont *)placeFont;

@end

NS_ASSUME_NONNULL_END

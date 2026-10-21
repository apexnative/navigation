#import "ANNButtonOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A bar button that shows its icon and its text side by side (`topBar.leftButtons[].showText`): a `UIButton` of the
/// plain configuration (icon padding 6 pt, the symbol in the Body style) that the bar sets on its glass as an icon. Its
/// color doesn't change on a press (the glass shows the press); without a color it has the bar's label color. The icon
/// is tinted as an icon button's: a symbol always, an image only with a color; `disableIconTint` or an image without a
/// color keep their own colors. Disabled: 35 % dimmer, or the explicit disabled color alone.
@interface ANNShowTextButton : UIButton

/// `color` and `disabledColor` are the button's own over its side's; nil: the defaults above.
+ (instancetype)buttonWithOptions:(ANNButtonOptions *)options
                            image:(UIImage *)image
                         isSymbol:(BOOL)isSymbol
                            color:(nullable UIColor *)color
                    disabledColor:(nullable UIColor *)disabledColor;

@end

NS_ASSUME_NONNULL_END

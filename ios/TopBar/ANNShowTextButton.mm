#import "ANNShowTextButton.h"

static const CGFloat ANNShowTextImagePadding = 6;
static const CGFloat ANNShowTextDisabledAlpha = 0.35;

@implementation ANNShowTextButton

+ (instancetype)buttonWithOptions:(ANNButtonOptions *)options
                            image:(UIImage *)image
                         isSymbol:(BOOL)isSymbol
                            color:(UIColor *)color
                    disabledColor:(UIColor *)disabledColor {
    UIButtonConfiguration *configuration = [UIButtonConfiguration plainButtonConfiguration];
    configuration.imagePadding = ANNShowTextImagePadding;
    configuration.preferredSymbolConfigurationForImage =
        [UIImageSymbolConfiguration configurationWithTextStyle:UIFontTextStyleBody];
    BOOL ownColors = [options.disableIconTint withDefault:NO] || (!isSymbol && !color);
    UIFont *font = [options.font
        fontWithPlaceFont:[UIFont preferredFontForTextStyle:UIFontTextStyleBody]]
                       ?: [UIFont preferredFontForTextStyle:UIFontTextStyleBody];
    NSString *text = [options.text withDefault:nil] ?: @"";
    configuration.attributedTitle =
        [[NSAttributedString alloc] initWithString:text attributes:@{NSFontAttributeName : font}];
    UIColor *normal = color ?: UIColor.labelColor;
    BOOL enabled = [options.enabled withDefault:YES];
    UIColor *shown = normal;
    if (!enabled) {
        shown = disabledColor ?: [normal colorWithAlphaComponent:ANNShowTextDisabledAlpha];
    }
    configuration.baseForegroundColor = shown;
    // The icon carries its color itself, as an original image: the tint a bar gives the template image of a custom
    // view (iOS 26 drew it black) doesn't reach it.
    configuration.image =
        ownColors ? [image imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal]
                  : [image imageWithTintColor:shown
                                renderingMode:UIImageRenderingModeAlwaysOriginal];
    // The plain configuration dims and highlights by itself: the colors above are the ones shown in every state.
    configuration.titleTextAttributesTransformer = ^NSDictionary<NSAttributedStringKey, id> *(
        NSDictionary<NSAttributedStringKey, id> *attributes) {
      NSMutableDictionary<NSAttributedStringKey, id> *fixed = [attributes mutableCopy];
      fixed[NSForegroundColorAttributeName] = shown;
      return fixed;
    };
    ANNShowTextButton *button = [self buttonWithConfiguration:configuration primaryAction:nil];
    button.configurationUpdateHandler = ^(UIButton *updated) {
      UIButtonConfiguration *same = updated.configuration;
      same.baseForegroundColor = shown;
      updated.configuration = same;
    };
    button.enabled = enabled;
    NSString *testID = [options.testID withDefault:nil];
    if (testID.length > 0) {
        button.accessibilityIdentifier = testID;
    }
    NSString *label = [options.accessibilityLabel withDefault:nil];
    if (label.length > 0) {
        button.accessibilityLabel = label;
    }
    [button sizeToFit];
    return button;
}

@end

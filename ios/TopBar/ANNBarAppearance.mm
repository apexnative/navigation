#import "ANNBarAppearance.h"
#import "ANNBackButtonPresenter.h"

// UIKit's fonts of the places the options build over: the title of the row, the large title, the text of the back
// button.
static UIFont *ANNTitlePlaceFont(void) {
    return [UIFont systemFontOfSize:17 weight:UIFontWeightSemibold];
}

static UIFont *ANNLargeTitlePlaceFont(void) {
    return [UIFont systemFontOfSize:34 weight:UIFontWeightBold];
}

static UIFont *ANNBackButtonPlaceFont(void) {
    return [UIFont systemFontOfSize:17];
}

// A color the options gave: nil without one and for "no color"; a pair with one side has the bar's own color for the
// other.
static UIColor *ANNColorOf(Color *color, UIColor *placeColor) {
    if (!color.hasValue || color.isNoColor) {
        return nil;
    }
    return [color withDefault:placeColor];
}

static BOOL ANNIsClear(UIColor *color, UITraitCollection *traits) {
    return CGColorGetAlpha([color resolvedColorWithTraitCollection:traits].CGColor) == 0;
}

static NSDictionary<NSAttributedStringKey, id> *ANNTextAttributes(ANNFontOptions *font,
                                                                 Color *color,
                                                                 UIFont *placeFont) {
    NSMutableDictionary<NSAttributedStringKey, id> *attributes = [NSMutableDictionary new];
    UIFont *built = [font fontWithPlaceFont:placeFont];
    if (built) {
        attributes[NSFontAttributeName] = built;
    }
    UIColor *textColor = ANNColorOf(color, UIColor.labelColor);
    if (textColor) {
        attributes[NSForegroundColorAttributeName] = textColor;
    }
    return attributes;
}

@implementation ANNBarAppearance

+ (void)fillStandard:(UINavigationBarAppearance *)standard
          scrollEdge:(UINavigationBarAppearance *)scrollEdge
         withOptions:(ANNNavigationOptions *)options
             context:(ANNBarAppearanceContext)context
              traits:(UITraitCollection *)traits {
    ANNTopBarOptions *topBar = options.topBar;
    ANNTopBarBackgroundOptions *background = topBar.background;
    ANNTopBarScrollEdgeAppearanceOptions *edge = topBar.scrollEdgeAppearance;
    UIColor *color = ANNColorOf(background.color, UIColor.systemBackgroundColor);
    BOOL blur = [background.blur withDefault:NO] || (context.largeTitleNeedsBackground && !color);
    BOOL translucent = [background.translucent withDefault:[self translucentByDefault]];

    // The standard look: the background, then the border (configuring a background resets the shadow).
    [self configure:standard color:color blur:blur translucent:translucent traits:traits];
    [self applyBorderTo:standard noBorder:topBar.noBorder borderColor:topBar.borderColor];

    // The edge look: its own background with `active`; transparent under a large title in the list; else as the
    // standard one. Its border and title act also without `active`, else the standard ones.
    if ([edge.active withDefault:NO]) {
        UIColor *edgeColor = ANNColorOf(edge.background.color, UIColor.systemBackgroundColor);
        if (!edge.background.color.hasValue) {
            edgeColor = color;
        }
        BOOL edgeTranslucent = [edge.background.translucent withDefault:translucent];
        [self configure:scrollEdge
                  color:edgeColor
                   blur:NO
            translucent:edgeTranslucent
                 traits:traits];
    } else if (context.transparentEdge) {
        [scrollEdge configureWithTransparentBackground];
    } else {
        [self configure:scrollEdge color:color blur:blur translucent:translucent traits:traits];
    }
    if (edge.noBorder.hasValue || edge.borderColor.hasValue) {
        [self applyBorderTo:scrollEdge noBorder:edge.noBorder borderColor:edge.borderColor];
    } else if (!context.transparentEdge || [edge.active withDefault:NO]) {
        [self applyBorderTo:scrollEdge noBorder:topBar.noBorder borderColor:topBar.borderColor];
    }

    // The titles.
    standard.titleTextAttributes = [self titleAttributesOf:options];
    scrollEdge.titleTextAttributes = [self scrollEdgeTitleAttributesOf:options];
    ANNTopBarLargeTitleOptions *large = topBar.largeTitle;
    NSDictionary<NSAttributedStringKey, id> *largeTitle =
        ANNTextAttributes(large.font, large.color, ANNLargeTitlePlaceFont());
    standard.largeTitleTextAttributes = largeTitle;
    scrollEdge.largeTitleTextAttributes = largeTitle;

    // The back button's look is the shown screen's: its text and its arrow.
    [self applyBackButton:topBar.backButton to:standard];
    [self applyBackButton:topBar.backButton to:scrollEdge];
}

+ (nullable UIColor *)solidColorOf:(Color *)color traits:(UITraitCollection *)traits {
    UIColor *resolved = ANNColorOf(color, UIColor.systemBackgroundColor);
    return resolved && !ANNIsClear(resolved, traits) ? resolved : nil;
}

+ (BOOL)translucentByDefault {
    if (@available(iOS 26.0, *)) {
        return YES;
    }
    return NO;
}

// `color` beats `blur` and `translucent`; a clear color is a clear bar. On iOS 26 a color is a solid bar: a clear base
// with the color and a 1×1 image of it, without the system effect. `blur` is the system's chrome material on a clear
// base, with no view of its own in the bar.
+ (void)configure:(UINavigationBarAppearance *)appearance
            color:(UIColor *)color
             blur:(BOOL)blur
      translucent:(BOOL)translucent
           traits:(UITraitCollection *)traits {
    if (color) {
        if (ANNIsClear(color, traits)) {
            [appearance configureWithTransparentBackground];
            return;
        }
        if (@available(iOS 26.0, *)) {
            [appearance configureWithTransparentBackground];
            appearance.backgroundEffect = nil;
            appearance.backgroundColor = color;
            appearance.backgroundImage = [self imageOfColor:color traits:traits];
            return;
        }
        [appearance configureWithOpaqueBackground];
        appearance.backgroundColor = color;
        return;
    }
    if (blur) {
        [appearance configureWithTransparentBackground];
        appearance.backgroundEffect =
            [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemChromeMaterial];
        return;
    }
    if (translucent) {
        [appearance configureWithDefaultBackground];
    } else {
        [appearance configureWithOpaqueBackground];
    }
}

+ (void)applyBorderTo:(UINavigationBarAppearance *)appearance
             noBorder:(Bool *)noBorder
          borderColor:(Color *)borderColor {
    if ([noBorder withDefault:NO]) {
        appearance.shadowColor = nil;
        appearance.shadowImage = nil;
        return;
    }
    UIColor *color = ANNColorOf(borderColor, UIColor.separatorColor);
    if (color) {
        appearance.shadowColor = color;
    }
}

+ (NSDictionary<NSAttributedStringKey, id> *)titleAttributesOf:(ANNNavigationOptions *)options {
    ANNTopBarTitleOptions *title = options.topBar.title;
    return ANNTextAttributes(title.font, title.color, ANNTitlePlaceFont());
}

+ (NSDictionary<NSAttributedStringKey, id> *)scrollEdgeTitleAttributesOf:
    (ANNNavigationOptions *)options {
    ANNTopBarScrollEdgeAppearanceTitleOptions *edgeTitle =
        options.topBar.scrollEdgeAppearance.title;
    if (!edgeTitle.hasValue) {
        return [self titleAttributesOf:options];
    }
    return ANNTextAttributes(edgeTitle.font, edgeTitle.color, ANNTitlePlaceFont());
}

// The text of the back button in its normal and highlighted states (a font only with a font option, 17 pt by default),
// and its arrow: the system `chevron.backward` (or the own icon) tinted with the color as the indicator and its
// transition mask (on iOS 26 the bar's tint doesn't color the system arrow).
+ (void)applyBackButton:(ANNTopBarBackButtonOptions *)backButton
                     to:(UINavigationBarAppearance *)appearance {
    UIBarButtonItemAppearance *buttonAppearance =
        [[UIBarButtonItemAppearance alloc] initWithStyle:UIBarButtonItemStylePlain];
    NSMutableDictionary<NSAttributedStringKey, id> *attributes = [NSMutableDictionary new];
    UIFont *font = [backButton.font fontWithPlaceFont:ANNBackButtonPlaceFont()];
    if (font) {
        attributes[NSFontAttributeName] = font;
    }
    UIColor *color = ANNColorOf(backButton.color, UIColor.tintColor);
    if (color) {
        attributes[NSForegroundColorAttributeName] = color;
    }
    buttonAppearance.normal.titleTextAttributes = attributes;
    buttonAppearance.highlighted.titleTextAttributes = attributes;
    appearance.backButtonAppearance = buttonAppearance;

    // Nil gives UIKit's own arrow back: a look filled again never keeps an arrow the options no longer ask for.
    UIImage *indicator = [self backIndicatorOf:backButton color:color];
    [appearance setBackIndicatorImage:indicator transitionMaskImage:indicator];
}

+ (nullable UIImage *)backIndicatorOf:(ANNTopBarBackButtonOptions *)backButton
                                color:(UIColor *)color {
    // With `iconBackground` the own back button draws the icon on its background: UIKit's indicator, which it shows
    // next to any back button, is empty then, so the icon shows once.
    if ([ANNBackButtonPresenter iconBackgroundColorOf:backButton]) {
        return [self emptyIndicator];
    }
    NSString *symbol = [backButton.sfSymbol withDefault:nil];
    UIImage *icon = symbol.length > 0 ? [UIImage systemImageNamed:symbol] : nil;
    BOOL isSymbol = icon != nil;
    if (!icon) {
        icon = [backButton.icon withDefault:nil];
    }
    if (!icon) {
        if (!color) {
            return nil;
        }
        icon = [UIImage systemImageNamed:@"chevron.backward"];
        isSymbol = YES;
    }
    if (color) {
        return [icon imageWithTintColor:color renderingMode:UIImageRenderingModeAlwaysOriginal];
    }
    return isSymbol ? icon : [icon imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
}

+ (UIImage *)emptyIndicator {
    UIGraphicsImageRendererFormat *format = [UIGraphicsImageRendererFormat preferredFormat];
    format.opaque = NO;
    UIGraphicsImageRenderer *renderer =
        [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(1, 1) format:format];
    UIImage *empty = [renderer imageWithActions:^(UIGraphicsImageRendererContext *context){
    }];
    return [empty imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
}

+ (UIColor *)pinnedTitleColor:(Color *)color traits:(UITraitCollection *)traits {
    UIColor *own = ANNColorOf(color, UIColor.labelColor) ?: UIColor.labelColor;
    return [own resolvedColorWithTraitCollection:traits];
}

// The 1×1 picture of the color in both styles: the bar's image view picks the variant of its own style, so a `{ light,
// dark }` color follows a switch of the style without a new appearance.
+ (UIImage *)imageOfColor:(UIColor *)color traits:(UITraitCollection *)traits {
    UIImage * (^draw)(UITraitCollection *) = ^UIImage *(UITraitCollection *variant) {
      UIColor *resolved = [color resolvedColorWithTraitCollection:variant];
      UIGraphicsImageRendererFormat *format = [UIGraphicsImageRendererFormat preferredFormat];
      format.scale = 1;
      UIGraphicsImageRenderer *renderer =
          [[UIGraphicsImageRenderer alloc] initWithSize:CGSizeMake(1, 1) format:format];
      return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
        [resolved setFill];
        [context fillRect:CGRectMake(0, 0, 1, 1)];
      }];
    };
    UITraitCollection *light =
        [UITraitCollection traitCollectionWithUserInterfaceStyle:UIUserInterfaceStyleLight];
    UITraitCollection *dark =
        [UITraitCollection traitCollectionWithUserInterfaceStyle:UIUserInterfaceStyleDark];
    // Each variant is drawn with the window's other traits (contrast, elevation) and registered by its style only.
    NSArray<UITraitCollection *> *base = traits ? @[ traits ] : @[];
    UIImage *lightImage =
        draw([UITraitCollection traitCollectionWithTraitsFromCollections:[base arrayByAddingObject:light]]);
    UIImage *darkImage =
        draw([UITraitCollection traitCollectionWithTraitsFromCollections:[base arrayByAddingObject:dark]]);
    UIImageAsset *asset = [UIImageAsset new];
    [asset registerImage:lightImage withTraitCollection:light];
    [asset registerImage:darkImage withTraitCollection:dark];
    BOOL isDark = traits.userInterfaceStyle == UIUserInterfaceStyleDark;
    UIImage *picked = [asset imageWithTraitCollection:isDark ? dark : light];
    return picked ?: (isDark ? darkImage : lightImage);
}

@end

#import "ANNIconButtonImage.h"
#import "ANNUtils.h"

@implementation ANNIconButtonImage

+ (UIImage *)imageWithIcon:(UIImage *)icon
                      tint:(UIColor *)tint
                background:(UIColor *)background
              cornerRadius:(CGFloat)cornerRadius
                      size:(CGSize)size
                    insets:(UIEdgeInsets)insets {
    // Each variant carries a scale with its style: an asset of style-only traits gives back an image of scale 1, its
    // pixels taken as points (a 32 pt background drawn 96 pt wide). The current scale and the usual ones are drawn,
    // so a screen of another scale still finds its style. Each variant is an original image, so that a variant UIKit
    // picks after a light/dark switch never becomes a template that takes the bar's tint.
    UITraitCollection *current = UITraitCollection.currentTraitCollection;
    CGFloat scale = current.displayScale > 0 ? current.displayScale : ANNUtils.mainScreen.scale;
    NSMutableOrderedSet<NSNumber *> *scales = [NSMutableOrderedSet orderedSetWithObject:@(scale)];
    [scales addObjectsFromArray:@[ @1, @2, @3 ]];
    UIImageAsset *asset = [UIImageAsset new];
    for (NSNumber *variantScale in scales) {
        for (NSNumber *style in @[ @(UIUserInterfaceStyleLight), @(UIUserInterfaceStyleDark) ]) {
            UITraitCollection *traits =
                [self traitsWithStyle:(UIUserInterfaceStyle)style.integerValue
                                scale:variantScale.doubleValue];
            UIImage *drawn = [self drawIcon:icon
                                       tint:tint
                                 background:background
                               cornerRadius:cornerRadius
                                       size:size
                                     insets:insets
                                     traits:traits];
            [asset registerImage:[drawn imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal]
                 withTraitCollection:traits];
        }
    }
    UIUserInterfaceStyle style = current.userInterfaceStyle == UIUserInterfaceStyleDark
                                     ? UIUserInterfaceStyleDark
                                     : UIUserInterfaceStyleLight;
    UIImage *image = [asset imageWithTraitCollection:[self traitsWithStyle:style scale:scale]];
    return [image imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
}

+ (UITraitCollection *)traitsWithStyle:(UIUserInterfaceStyle)style scale:(CGFloat)scale {
    if (@available(iOS 17.0, *)) {
        return [UITraitCollection traitCollectionWithTraits:^(id<UIMutableTraits> traits) {
          traits.userInterfaceStyle = style;
          traits.displayScale = scale;
        }];
    }
    return [UITraitCollection traitCollectionWithTraitsFromCollections:@[
        [UITraitCollection traitCollectionWithUserInterfaceStyle:style],
        [UITraitCollection traitCollectionWithDisplayScale:scale],
    ]];
}

+ (UIImage *)drawIcon:(UIImage *)icon
                 tint:(UIColor *)tint
           background:(UIColor *)background
         cornerRadius:(CGFloat)cornerRadius
                 size:(CGSize)size
               insets:(UIEdgeInsets)insets
               traits:(UITraitCollection *)traits {
    CGSize iconSize = icon.size;
    CGSize padded = CGSizeMake(iconSize.width + insets.left + insets.right,
                               iconSize.height + insets.top + insets.bottom);
    CGSize canvas = CGSizeMake(size.width > 0 ? size.width : padded.width,
                               size.height > 0 ? size.height : padded.height);
    UIImage *drawn = icon;
    if (tint) {
        drawn = [icon imageWithTintColor:[tint resolvedColorWithTraitCollection:traits]
                           renderingMode:UIImageRenderingModeAlwaysOriginal];
    } else if (icon.isSymbolImage) {
        UIColor *label = [UIColor.labelColor resolvedColorWithTraitCollection:traits];
        drawn = [icon imageWithTintColor:label renderingMode:UIImageRenderingModeAlwaysOriginal];
    }
    UIGraphicsImageRendererFormat *format =
        [UIGraphicsImageRendererFormat formatForTraitCollection:traits];
    format.scale = traits.displayScale;
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:canvas
                                                                               format:format];
    return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
      if (background) {
          [[background resolvedColorWithTraitCollection:traits] setFill];
          [[UIBezierPath bezierPathWithRoundedRect:CGRectMake(0, 0, canvas.width, canvas.height)
                                      cornerRadius:cornerRadius] fill];
      }
      CGFloat x = insets.left + (canvas.width - padded.width) / 2;
      CGFloat y = insets.top + (canvas.height - padded.height) / 2;
      [drawn drawInRect:CGRectMake(x, y, iconSize.width, iconSize.height)];
    }];
}

@end

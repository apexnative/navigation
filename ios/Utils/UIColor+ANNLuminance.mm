#import "UIColor+ANNLuminance.h"

@implementation UIColor (ANNLuminance)

- (BOOL)ann_isLightForTraitCollection:(UITraitCollection *)traitCollection {
    UITraitCollection *traits = traitCollection ?: UITraitCollection.currentTraitCollection;
    UIColor *color = [self resolvedColorWithTraitCollection:traits];
    CGFloat red = 0;
    CGFloat green = 0;
    CGFloat blue = 0;
    CGFloat alpha = 0;
    if (![color getRed:&red green:&green blue:&blue alpha:&alpha]) {
        // A color of another space (a pattern, a catalog color): its components in sRGB.
        CGColorSpaceRef sRGB = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
        CGColorRef converted =
            CGColorCreateCopyByMatchingToColorSpace(sRGB, kCGRenderingIntentDefault, color.CGColor, NULL);
        CGColorSpaceRelease(sRGB);
        if (!converted) {
            return NO;
        }
        const CGFloat *components = CGColorGetComponents(converted);
        if (CGColorGetNumberOfComponents(converted) >= 3) {
            red = components[0];
            green = components[1];
            blue = components[2];
        }
        CGColorRelease(converted);
    }
    return 0.299 * red + 0.587 * green + 0.114 * blue > 0.5;
}

@end

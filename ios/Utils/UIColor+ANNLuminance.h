#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface UIColor (ANNLuminance)

/// Whether the color is light: its luminance `0.299 R + 0.587 G + 0.114 B` (components 0…1, in sRGB) is greater than
/// 0.5. A dynamic color is taken in the given traits, or the current ones.
- (BOOL)ann_isLightForTraitCollection:(nullable UITraitCollection *)traitCollection;

@end

NS_ASSUME_NONNULL_END

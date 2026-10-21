#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The image of an image button (`iconBackground` with a color, or `iconInsets`;
/// `topBar.leftButtons[].iconBackground`, `topBar.backButton.iconBackground`): the icon drawn on a rounded rectangle of
/// the background color, or with its insets, into one image with a light and a dark variant, which UIKit picks by the
/// style shown.
@interface ANNIconButtonImage : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// `tint` colors the icon (nil: its own colors); `background` fills the rectangle of `size` (zero: the icon's size)
/// rounded by `cornerRadius`; `insets` pad the icon. Colors may be dynamic: each variant resolves them.
+ (UIImage *)imageWithIcon:(UIImage *)icon
                      tint:(nullable UIColor *)tint
                background:(nullable UIColor *)background
              cornerRadius:(CGFloat)cornerRadius
                      size:(CGSize)size
                    insets:(UIEdgeInsets)insets;

@end

NS_ASSUME_NONNULL_END

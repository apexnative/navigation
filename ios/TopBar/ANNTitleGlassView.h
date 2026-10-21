#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The title on a Liquid Glass capsule (`topBar.title.glass`, iOS 26+): a `UIGlassEffect` in a visual effect view with
/// capsule corners. A text title shows the title and the subtitle in two lines on it (17 pt semibold, 15 pt with a
/// subtitle, the label color; the subtitle 12 pt in the secondary label color; the options' fonts and colors over
/// them), padded 7/16 pt for one line and 4/16 pt for two. A React title sits on it as it is, without padding, the
/// capsule its size. Id `<topBar.testID>.title` on the capsule.
API_AVAILABLE(ios(26.0))
@interface ANNTitleGlassView : UIView

/// Whether `glass` asks for a capsule: `regular` or `clear`.
+ (BOOL)isGlass:(nullable NSString *)glass;

/// A capsule with the text title of `options`.
- (instancetype)initWithGlass:(NSString *)glass options:(ANNNavigationOptions *)options;

/// A capsule with a React title's container (its own size follows the container's).
- (instancetype)initWithGlass:(NSString *)glass
                    component:(UIView *)component
                      options:(ANNNavigationOptions *)options;

- (instancetype)initWithFrame:(CGRect)frame NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

/// The component's size changed: the capsule takes it.
- (void)componentSizeDidChange;

@end

NS_ASSUME_NONNULL_END

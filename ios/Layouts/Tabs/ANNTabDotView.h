#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The dot of a tab before iOS 26 and in the compatibility mode: a round view of its own over the tab bar, its center
/// on the top right corner of the tab's icon. Placed by frame, never by constraints (a tab bar under its controller
/// throws on a change of constraints), and placed again at every layout of the tabs. Takes no touches.
@interface ANNTabDotView : UIView

/// The dot's diameter (`dotIndicator.size`, 6 by default) and color (red by default).
- (void)setDiameter:(CGFloat)diameter color:(UIColor *)color;

/// The dot over `button` (the tab's button in `bar`): at the top right corner of its icon, or hidden when the button or
/// its icon isn't there.
- (void)placeOverButton:(nullable UIView *)button inBar:(UITabBar *)bar;

@end

NS_ASSUME_NONNULL_END

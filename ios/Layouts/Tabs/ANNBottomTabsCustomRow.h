#import "ANNBottomTabCellView.h"
#import "ANNBottomTabsCustomRowOptions.h"
#import <UIKit/UIKit.h>

@class ANNBottomTabsController;

NS_ASSUME_NONNULL_BEGIN

/// The core's custom row of tabs on iOS: a container over the tabs' view, above all of its children, holding one cell
/// per tab in equal widths and layout order. The system bar stays under it (it keeps the bottom safe area and gives the
/// frame) and shows nothing. The row takes the cells' presses (tap, long press) to the tabs' one press path, shows the
/// tab the core selected, hides with the bar, and gives the tabs the extra bottom safe area it covers above the system
/// bar. Its place per design: the capsule of iOS 26 at the bottom, the full width at the bottom of a wide iPad or Mac
/// (the system's top bar hidden), the bar's place before iOS 26. Main queue.
@interface ANNBottomTabsCustomRow : UIView

- (instancetype)initWithTabs:(ANNBottomTabsController *)tabs;

- (instancetype)initWithFrame:(CGRect)frame NS_UNAVAILABLE;
- (nullable instancetype)initWithCoder:(NSCoder *)coder NS_UNAVAILABLE;

/// One cell per tab, in layout order.
@property(nonatomic, copy, readonly) NSArray<ANNBottomTabCellView *> *cells;

/// The tabs' resolved `customRow`: its background, shape, margins and height, at once.
- (void)applyOptions:(ANNBottomTabsCustomRowOptions *)options;

/// The row in its place again: the bar's frame now, the design, the width. The system bar's subviews are hidden again
/// (UIKit makes them anew on a size change).
- (void)layOutInTabs;

/// The row shows: it is in place and neither the bar nor its own visibility hides it.
@property(nonatomic, readonly) BOOL isShown;

/// On a wide iPad or Mac (iOS 18 and later) the system's top bar is hidden and the row keeps `bottomTabs.visible`
/// itself.
@property(nonatomic, readonly) BOOL keepsVisibility;

/// `bottomTabs.visible` of the row while it keeps the visibility.
@property(nonatomic) BOOL hiddenByOptions;

/// The row leaves the tabs: the extra safe area goes, the system bar comes back by the options.
- (void)removeFromTabs;

@end

NS_ASSUME_NONNULL_END

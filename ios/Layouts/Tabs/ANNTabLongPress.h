#import <UIKit/UIKit.h>

@class ANNBottomTabsController;

NS_ASSUME_NONNULL_BEGIN

/// The long press on the tab bar's buttons. On iOS 26 the system selects tabs with its own gesture: the recognizer
/// works together with it, and the tabs cancel UIKit's selection of the long-pressed tab at the release. Before iOS 26
/// the long press cancels the touch of the button by itself. The tab is found by the frame of each tab's own button
/// (the module's `viewOfItem:` with the tab's item), converted into the bar's coordinates, among the tabs of the
/// layout: More and the room between the buttons send nothing. The iPad's top bar is another view of the system: no
/// long press there.
@interface ANNTabLongPress : NSObject <UIGestureRecognizerDelegate>

- (instancetype)initWithTabs:(ANNBottomTabsController *)tabs;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The recognizer goes onto the tabs' bar (once).
- (void)attach;

/// The recognizer leaves the bar.
- (void)detach;

@end

NS_ASSUME_NONNULL_END

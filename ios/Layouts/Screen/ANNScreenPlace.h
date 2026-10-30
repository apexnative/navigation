#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Where an entry will show on top of a stack, as UIKit lays it out there: the safe area its screens get, and whether
/// the stack's bar and the tab bar show for it. A screen that waits for its content out of its stack (parked,
/// `ANNParking`) has no parent view controller, and UIKit gives its view no safe area at all: it lays out with its
/// place's instead (`placeWhileParked` of `ANNComponentViewController`), so that its root frame, its padding, the
/// insets JavaScript reads in its first render and its main list are those it has after the push. Nothing of it moves
/// as the push starts: a list that moved then would lose the large title, which UIKit draws in the list the bar
/// follows.
///
/// The bar's height is UIKit's own: a navigation controller of the core's, unseen in the stack's view for the moment of
/// the measure, shows an item made like the entry's (title, display mode of the large title, looks, subtitles, prompt,
/// style, search field) over one like the stack's top, with the stack's bar settings and the entry's toolbar. Main
/// queue.
@interface ANNScreenPlace : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The place of `entry` (a screen, or a pair whose screens show under its bar) on top of `stack`, with `options` the
/// resolved options the stack shows its bar and toolbar by; nil when the stack isn't in a window. Measured again only
/// when something the measure reads changed since the last one for `entry`.
+ (nullable ANNScreenPlace *)placeOfEntry:(UIViewController *)entry
                                  inStack:(UINavigationController *)stack
                                  options:(ANNNavigationOptions *)options;

/// The same, measured in `host` instead of the stack's view: for a stack UIKit gives no safe area yet (a tab not shown,
/// parked in the tabs' view), `host` is a view with the safe area the stack gets as it shows (the shown tab's). Nil
/// when `host` isn't in a window.
+ (nullable ANNScreenPlace *)placeOfEntry:(UIViewController *)entry
                                  inStack:(UINavigationController *)stack
                               measuredIn:(UIView *)host
                                  options:(ANNNavigationOptions *)options;

/// The place of a screen that is a tab of its own, with no stack: the safe area of `host` (the shown tab's view), no
/// top bar. Nil when `host` isn't in a window.
+ (nullable ANNScreenPlace *)placeOfScreen:(UIViewController *)screen measuredIn:(UIView *)host;

/// The safe area of the entry's view in the place.
@property(nonatomic, readonly) UIEdgeInsets safeArea;

/// The stack's bar shows for the entry (`topBar.visible`).
@property(nonatomic, readonly) BOOL showsTopBar;

/// A tab bar shows at the bottom for the entry: one shows now and the entry doesn't hide it.
@property(nonatomic, readonly) BOOL showsTabBar;

@end

NS_ASSUME_NONNULL_END

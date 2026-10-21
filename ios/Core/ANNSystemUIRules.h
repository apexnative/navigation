#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The values of the system UI a screen answers UIKit with, from its options alone: no view, no window.
/// `ANNNodePresenter` answers through these rules, so the screens, the side menu and the split view answer the same.

/// The sides of the values of `layout.orientation`: `all` and `sensor` all four, `landscape` and `sensorLandscape` both
/// landscapes, `portrait` the portrait, `sensorPortrait` both portraits, `upsideDown` the portrait upside down,
/// `default` the app's; unknown values count for nothing. The union meets the app's mask; no values, no option, or
/// nothing in common with the app give the app's mask (an empty mask would crash UIKit).
FOUNDATION_EXPORT UIInterfaceOrientationMask
ANNOrientationMaskOf(NSArray<NSString *> *_Nullable values, UIInterfaceOrientationMask appMask);

/// `statusBar.style`: `'light'` the light content, `'dark'` the dark content, none or another value UIKit's default,
/// which follows the interface style.
FOUNDATION_EXPORT UIStatusBarStyle ANNStatusBarStyleOf(NSString *_Nullable style);

/// `statusBar.visible` decides when set; without it `statusBar.hideWithTopBar` hides the status bar while the bar of
/// the deciding screen's navigation controller is hidden (`navigationBarHidden`; a screen without one passes NO).
FOUNDATION_EXPORT BOOL ANNStatusBarHiddenOf(ANNStatusBarOptions *statusBar,
                                           BOOL navigationBarHidden);

/// `layout.deferSystemGestures`: the union of the physical edges named (right to left doesn't swap them); none, or only
/// unknown values, defer nothing.
FOUNDATION_EXPORT UIRectEdge ANNDeferredEdgesOf(NSArray<NSString *> *_Nullable values);

#if DEBUG
/// Development only (there is no iOS test target): checks the rules against the vectors the Android tests share for the
/// sides of the orientation values, once; a mismatch is logged.
FOUNDATION_EXPORT void ANNSystemUIRulesSelfCheck(void);
#endif

NS_ASSUME_NONNULL_END

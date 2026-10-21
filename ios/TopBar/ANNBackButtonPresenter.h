#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The back button of a screen in a stack (`topBar.backButton.*`): UIKit's system button. Its title and mode go to the
/// screen below (`backButtonTitle`, `backButtonDisplayMode`), which the core marks as configured; when the screen on
/// top has no back options, what the core set before goes, and a back button a native screen set on its own item is
/// never touched. An own `backBarButtonItem` only with `enableMenu: false` or an `iconBackground` with a color. Its
/// color, font and arrow are the shown screen's look (`ANNBarAppearance`). No `testID`: UIKit names it `BackButton`.
@interface ANNBackButtonPresenter : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The back button of `screen` from its resolved `options`; nothing without a screen below.
+ (void)applyOptions:(ANNNavigationOptions *)options ofScreen:(UIViewController *)screen;

/// `backButton.visible` into the screen's own item (`hidesBackButton`).
+ (void)applyVisibility:(ANNNavigationOptions *)options ofScreen:(UIViewController *)screen;

/// A new look of a shown back button (font, color, icon, SF Symbol, icon background): UIKit applies it only when it
/// builds the button again, so its mode is switched there and back on the screen below, never during a transition.
+ (void)rebuildBackButtonOfScreen:(UIViewController *)screen;

/// Whether `screen` has a back button: an entry above the first of its stack, `backButton.visible` not `false` (by the
/// options). `iconBackground.color` of the back button: with it the back button is the core's own, its icon drawn on
/// that background (`topBar.backButton.iconBackground`); nil without one.
+ (nullable UIColor *)iconBackgroundColorOf:(ANNTopBarBackButtonOptions *)backButton;

+ (BOOL)screenHasBackButton:(UIViewController *)screen options:(ANNNavigationOptions *)options;

@end

NS_ASSUME_NONNULL_END

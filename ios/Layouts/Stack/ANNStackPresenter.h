#import "ANNNodePresenter.h"

NS_ASSUME_NONNULL_BEGIN

/// Which visibilities of the stack's bars an application of the shared part sets. A bar the user hid (`hideOnScroll`
/// before iOS 27, `hideOnTap`, `hideOnKeyboard`) comes back only when the top changes, at the top's appearance, or by a
/// merge that names its visibility.
typedef NS_OPTIONS(NSUInteger, ANNSharedBarVisibility) {
    ANNSharedBarVisibilityNone = 0,
    /// The bar's (`topBar.visible`).
    ANNSharedBarVisibilityBar = 1 << 0,
    /// The toolbar's (`toolbar.visible`, `toolbar.buttons`).
    ANNSharedBarVisibilityToolbar = 1 << 1,
    ANNSharedBarVisibilityAll = ANNSharedBarVisibilityBar | ANNSharedBarVisibilityToolbar,
};

/// The visibilities a merge names.
FOUNDATION_EXPORT ANNSharedBarVisibility ANNSharedBarVisibilityOfMerge(ANNNavigationOptions *mergeOptions);

/// The presenter of a stack: large titles always allowed in its bar (whether a screen shows one is its own options'),
/// `popGesture` of the top into the swipes back (`popGesture`), and the parts of the bar the stack shares, from the
/// options of the screen UIKit shows on top only: the bar's visibility, `hidesBarsOnSwipe` (before iOS 27),
/// `hidesBarsOnTap` (`hideOnKeyboard` is the stack's, at each keyboard), `barStyle`, `clipsToBounds`, the bar's
/// `testID`, the toolbar's visibility and `testID`, on iOS 26 a copy of the screen's look on the bar, and the screen's
/// React background in the bar. The screens' items keep the rest (`ANNTopBarPresenter`). The stack's view takes the
/// resolved background.
@interface ANNStackPresenter : ANNNodePresenter

/// The stack's view gets `layout.backgroundColor` of `resolvedOptions` (the shown child's over the stack's over the
/// defaults): seen through clear bars and around the screens during transitions; none or "no color" is clear.
- (void)applyBackground:(ANNNavigationOptions *)resolvedOptions;

/// The shared parts from `options`, the resolved options of `screen`, which UIKit shows on top. `animated`: the
/// visibility of the bar animates by `topBar.animate` (and the toolbar's), along `coordinator` when one runs, with
/// which the background crossfades too. `visibility`: which of the bars' visibilities are set.
- (void)applySharedBar:(ANNNavigationOptions *)options
              ofScreen:(UIViewController *)screen
              animated:(BOOL)animated
           coordinator:(nullable id<UIViewControllerTransitionCoordinator>)coordinator
            visibility:(ANNSharedBarVisibility)visibility;

@end

NS_ASSUME_NONNULL_END

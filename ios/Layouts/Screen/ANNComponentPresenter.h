#import "ANNNodePresenter.h"

@class ANNTopBarPresenter;

NS_ASSUME_NONNULL_BEGIN

/// The presenter of a screen (`Component`, `ExternalComponent`): the background, the status bar's blur
/// (`statusBar.blur`), the "under bars" rule with the root frame and the safe area again on `mergeOptions`
/// (`mergeOptions` step 4), and the screen's part of the top bar (`topBar`). It applies at the screen's creation, at
/// each appearance (the screen's `screenWillAppear`, the path of every report), at a `mergeOptions` of the screen, and
/// on new default options.
@interface ANNComponentPresenter : ANNNodePresenter

/// The screen's part of the bar: its item, its bar components, its lists (made as the presenter binds its screen).
@property(nonatomic, strong, readonly, nullable) ANNTopBarPresenter *topBar;

/// The screen's view gets `layout.backgroundColor` (the system background without it, clear for "no color"); the
/// content (the React root; a slot screen's container) gets `layout.componentBackgroundColor` over it, clear without
/// it. Nothing before the screen's view is loaded.
- (void)applyBackground:(ANNNavigationOptions *)resolvedOptions;

/// `statusBar.blur` (iOS): UIKit's regular blur over the screen's own view in the frame of the scene's status bar at
/// this moment (x 0, the view's width; it doesn't follow a rotation); `false` or none removes it. At each appearance
/// and at a merge that names it; nothing before the screen's view is loaded.
- (void)applyStatusBarBlur:(ANNNavigationOptions *)resolvedOptions;

/// The blur stays the last subview of the screen's view: the content added after it goes under it.
- (void)keepStatusBarBlurOnTop;

@end

NS_ASSUME_NONNULL_END

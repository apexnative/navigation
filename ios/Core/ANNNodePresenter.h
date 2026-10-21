#import "ANNNavigationOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What applies the resolved options of a layout node to its view controller and answers UIKit's questions with them:
/// the status bar, the orientations, the home indicator, the deferred edges, the vertical bars,
/// `hidesBottomBarWhenPushed`, the background, and the node's own options. The core makes one for each node and
/// gives it to the node at creation; a container keeps it (`UIViewController+LayoutProtocol`) and asks it for the
/// answers UIKit wants from a view controller.
@interface ANNNodePresenter : NSObject

/// The view controller the presenter applies the options to.
@property(nonatomic, weak, readonly, nullable) UIViewController *boundViewController;

- (void)bindViewController:(UIViewController *)viewController;

/// The node was made: its first options, before it shows.
- (void)applyOptionsOnInit:(ANNNavigationOptions *)initialOptions;

/// The node shows (again): all of its resolved options.
- (void)applyOptions:(ANNNavigationOptions *)options;

/// `mergeOptions`: what the merge gives, with the resolved options after it.
- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions resolvedOptions:(ANNNavigationOptions *)resolvedOptions;

/// New default options while the node shows: apply again what depends on color and look (backgrounds, titles, buttons,
/// bars, tabs) from the node's new resolved options. Called by the repaint pass (`ANNOptionsRepaint`), children before
/// their parents, for nodes whose view is in a window; the rest apply the new options when they appear. Each area's
/// presenter fills its part.
- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions;

/// A screen inside the node starts to appear (`ANNComponentPresenter applyOptions:`, after the screen's own options):
/// a container that applies options at every appearance of a screen inside it (the side menu) does it here. Each layout
/// parent of the screen hears it, the nearest first. Default: nothing.
- (void)descendantWillAppear:(UIViewController *)screen;

/// A node inside the node merged `mergeOptions` and the merge came up to it (a node without an answer of its own to
/// `mergeChildOptions:child:`, the split view): the presenter takes its part. Default: nothing.
- (void)mergeChildOptions:(ANNNavigationOptions *)mergeOptions;

/// A merge into the node, or one that came up from a child, goes on to the node's parent as this answers: the merge
/// without the groups the node keeps for itself (the side menu: `sideMenu`, so that only the nearest side menu acts).
/// Default: the merge as it is.
- (ANNNavigationOptions *)optionsForParent:(ANNNavigationOptions *)mergeOptions;

#pragma mark - The answers of the node's view controller to UIKit

/// `statusBar.style` (none is UIKit's default).
- (UIStatusBarStyle)statusBarStyleForOptions:(ANNNavigationOptions *)resolvedOptions;

/// `statusBar.visible`, else `statusBar.hideWithTopBar` with the bar of the navigation controller of the screen the
/// node's system UI follows (`+[ANNSystemUI topScreenOf:]`): the side menu's and the split view's answer.
- (BOOL)statusBarHiddenForOptions:(ANNNavigationOptions *)resolvedOptions;

/// `statusBar.visible`, else `statusBar.hideWithTopBar` with `navigationBarHidden`, the bar of the deciding screen's
/// navigation controller as the caller knows it (NO without one).
- (BOOL)statusBarHiddenForOptions:(ANNNavigationOptions *)resolvedOptions
              navigationBarHidden:(BOOL)navigationBarHidden;

/// `statusBar.animate`: a change of the style animates unless it is `false`.
- (BOOL)statusBarAnimatesForOptions:(ANNNavigationOptions *)resolvedOptions;

/// `layout.orientation` against the app's orientations (`ANNUtils.appOrientationMask`); never empty.
- (UIInterfaceOrientationMask)supportedOrientationsForOptions:
    (ANNNavigationOptions *)resolvedOptions;

/// UIKit hides the tab bar with the push of a node that answers YES: its resolved `bottomTabs.visible` is `false`, on
/// iPhone, and on iPad before iOS 18.
- (BOOL)hidesBottomBarWhenPushedForOptions:(ANNNavigationOptions *)resolvedOptions;

/// `layout.autoHideHomeIndicator`.
- (BOOL)homeIndicatorAutoHiddenForOptions:(ANNNavigationOptions *)resolvedOptions;

/// `layout.deferSystemGestures`: the physical edges.
- (UIRectEdge)deferredEdgesForOptions:(ANNNavigationOptions *)resolvedOptions;

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
/// `layout.verticalBars`: `false` disables the vertical bar, else UIKit decides.
- (UIVerticalBarBehavior)verticalBarBehaviorForOptions:(ANNNavigationOptions *)resolvedOptions
    API_AVAILABLE(ios(27.1));
#endif

@end

NS_ASSUME_NONNULL_END

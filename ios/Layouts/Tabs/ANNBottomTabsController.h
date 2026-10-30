#import "ANNLayoutProtocol.h"
#import "ANNReactComponentRegistry.h"
#import "UIViewController+LayoutProtocol.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// When the screens of the tabs are made (`bottomTabs.tabsAttachMode`), read once at creation.
typedef NS_ENUM(NSInteger, ANNTabsAttachMode) {
    /// Every tab renders with the tabs; the tabs are ready when every tab is.
    ANNTabsAttachModeTogether,
    /// The selected tab first; the others one by one once it is ready.
    ANNTabsAttachModeAfterInitialTab,
    /// Only the selected tab; another one at its first selection.
    ANNTabsAttachModeOnSwitchToTab,
};

/// Where a press of a tab comes from: the system's bar (and the iPad's top bar, the sidebar, the More list), or a cell
/// of the core's custom row.
typedef NS_ENUM(NSInteger, ANNTabPressSource) {
    ANNTabPressSourceBar,
    ANNTabPressSourceRow,
};

/// The tabs of the layout (`BottomTabs`): UIKit's tab bar controller with the tabs in layout order — on iOS 18 and
/// later as `UITab`s, before as its view controllers. The core keeps its own list of the tabs and the selected index in
/// it: every index it sends or reads is in layout order, never UIKit's, More's or the user's order. Its presenter
/// (`ANNBottomTabsPresenter`) draws the items and the bar; the node owns the selection and its events, the waits of its
/// screens, the roots of its cells and its accessory (`components`), and the tabs' parts of More, the sidebar and the
/// long press. Main queue.
@interface ANNBottomTabsController
    : UITabBarController <ANNLayoutProtocol, UITabBarControllerDelegate>

/// The tabs with their children in layout order. `options` are the layout's own options of the node, still with the
/// one-shot `currentTabId` / `currentTabIndex`, which choose the first tab before the node keeps its options without
/// them.
- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(nullable NSArray<UIViewController *> *)childViewControllers;

/// The nearest tabs among the layout parents of `node` (not `node` itself); nil out of tabs.
+ (nullable ANNBottomTabsController *)tabsOf:(UIViewController *)node;

/// `node` is in a tab rendered before it shows, which renders its content in its tabs' view: in a window, not shown.
+ (BOOL)isInUnseenTab:(UIViewController *)node;

/// The tabs in layout order.
@property(nonatomic, copy, readonly) NSArray<UIViewController *> *layoutTabs;

/// The index of the selected tab in layout order; never More's or UIKit's.
@property(nonatomic, readonly) NSUInteger selectedTabIndex;

/// The attach mode read at creation.
@property(nonatomic, readonly) ANNTabsAttachMode attachMode;

/// The system tab API (`UITab`, iOS 18 and later) holds the tabs.
@property(nonatomic, readonly) BOOL usesTabAPI;

/// Every tab draws its item with a React component (`bottomTab.component`): the core's custom row stands in for the
/// bar's items. Decided at creation.
@property(nonatomic, readonly) BOOL usesCustomRow;

/// The React roots the tabs own, by `componentId`: the cells of the custom row, the accessories.
@property(nonatomic, strong, readonly) ANNReactComponentRegistry *components;

/// The `UITab` of the tab at `index` (iOS 18 and later with the tab API); nil otherwise.
- (nullable id)uiTabAtIndex:(NSUInteger)index;

/// The index of the tab that is `node` or holds it; `NSNotFound` when none does.
- (NSUInteger)indexOfTabHolding:(UIViewController *)node;

/// The screen of the tab at `index`: the top of its stack, down the layouts; nil when none.
- (nullable UIViewController *)screenOfTabAtIndex:(NSUInteger)index;

#pragma mark - The screens and the bar

/// A tab bar shows at the bottom for `screen` (`hasShownTabBar`): the bar (or the custom row) is shown, and on an iPad
/// of iOS 18 and later the bar isn't on top or in the sidebar.
- (BOOL)showsTabBarForScreen:(UIViewController *)screen;

/// A screen of the tabs starts to appear with its resolved options: for a screen the selected tab shows, the bar
/// follows them — its look, and on iOS 18 its visibility when it differs.
- (void)screenWillAppear:(UIViewController *)screen options:(ANNNavigationOptions *)resolvedOptions;

/// The bar shows or hides (the core's visibility); the screens lay out again.
- (void)setBarHidden:(BOOL)hidden animated:(BOOL)animated;

/// The bar is hidden by the core's visibility (or the custom row's own state on a wide iPad).
@property(nonatomic, readonly) BOOL barHidden;

/// The shown screens of the selected tab apply the "under the tab bar" rule again.
- (void)updateRootLayoutOfShownScreens;

/// The repaint passed over the tabs not in a window: each repaints at its next selection.
- (void)markUnshownTabsStale;

#pragma mark - Presses

/// A press of the tab at `index` from `source`, the one path of every source: `ANN.BottomTabPressed`, then the
/// selection by `selectTabOnPress`, the repeat press, and `ANN.BottomTabSelected` after the new tab's screen appeared.
/// YES when UIKit is to go on with its own selection (the system bar's sources).
- (BOOL)pressTabAtIndex:(NSUInteger)index source:(ANNTabPressSource)source;

/// A long press of the tab at `index`: `ANN.BottomTabLongPressed`; with `marksSelection` UIKit's selection of that tab
/// at the release is cancelled (iOS 26) until `longPressEnded`.
- (void)longPressTabAtIndex:(NSUInteger)index marksSelection:(BOOL)marksSelection;

/// The long press ended: its mark goes on the next turn of the main queue.
- (void)longPressEnded;

#pragma mark - More (the More navigation's delegate forwards here)

- (void)moreNavigation:(UINavigationController *)moreNavigation
    willShowViewController:(UIViewController *)viewController;
- (void)moreNavigation:(UINavigationController *)moreNavigation
    didShowViewController:(UIViewController *)viewController;

@end

NS_ASSUME_NONNULL_END

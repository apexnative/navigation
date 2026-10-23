#import "ANNNodePresenter.h"

NS_ASSUME_NONNULL_BEGIN

/// The presenter of the tabs: the item of every tab (whole, from its resolved `bottomTab`, `ANNBottomTabItemBuilder`),
/// the badges and dots it holds, the bar's look per design (before iOS 26 and the compatibility mode, the glass of iOS
/// 26, the iPad's sidebar and customization), the `testID`s of the bar, its buttons and More, the custom row
/// (`ANNBottomTabsCustomRow`) and the bottom accessory (iOS 26). It applies at creation, at each appearance of a screen
/// of the selected tab, at `mergeOptions` and at the repaint; only what changed is set. Its node is an
/// `ANNBottomTabsController`.
@interface ANNBottomTabsPresenter : ANNNodePresenter

/// The repaint of a node the repaint pass left (a tab not in a window), as it is about to show: the nodes of its
/// subtree whose views are in its view (what shows with it), children first, with their resolved options.
+ (void)repaintNode:(UIViewController *)node;

#pragma mark - Creation, in this order

/// The bar before the options: opaque, the system background, no effect, not translucent.
- (void)applyFirstLook;

/// Every tab's item from its resolved options, before UIKit gets the tabs.
- (void)styleItemsBeforeTabsAreSet;

/// iOS 18 and later: the mode, the sidebar's layout, the customization and every tab's placement, read once before the
/// tabs are set.
- (void)configureSidebarAndCustomizationBeforeTabsAreSet;

/// The tabs are in the bar: the badge colors of iOS 26, the dots, the titles' mode, the `testID`s, the bar's look, the
/// custom row, the accessory.
- (void)applyAfterTabsAreSet:(ANNNavigationOptions *)resolvedOptions;

#pragma mark - Showing and changing

/// A screen of the selected tab appears: the bar's look follows its resolved options.
- (void)applyBarLook:(ANNNavigationOptions *)resolvedOptions;

/// The tabs' view gets `layout.backgroundColor` of `resolvedOptions`, the tabs' resolved options (the selected tab's
/// over the tabs' over the defaults); none or "no color" is clear (`layout.backgroundColor`).
- (void)applyBackground:(ANNNavigationOptions *)resolvedOptions;

/// A tab, or a screen in it, merged `bottomTab`: the item of that tab shows its resolved `bottomTab` with the merge
/// over it, whole; its badge and dot change only when the merge carries them.
- (void)mergeBottomTab:(ANNBottomTabOptions *)mergeOptions ofTabAtIndex:(NSUInteger)index;

/// A tab, or a screen in it, merged `bottomTabs`: the bar's part when it is the selected tab, by the shown screen's
/// resolved options; the accessory from any tab at once.
- (void)mergeChildBar:(ANNBottomTabsOptions *)mergeOptions
      resolvedOptions:(ANNNavigationOptions *)resolvedOptions
      fromSelectedTab:(BOOL)fromSelectedTab;

/// The core selected another tab: the custom row's highlight and cells.
- (void)selectedTabChanged;

/// The tabs laid out (and before they appear): the dots, the buttons' `testID`s, the custom row.
- (void)tabsDidLayOut;

/// The bar laid out in a window for the first time: the titles are set again, so that the bar measures them at their
/// real place.
- (void)barDidLayOutInWindow;

/// The bar showed or hid: the custom row follows it.
- (void)barVisibilityChanged;

/// The tabs go: the row and the dots leave the bar; the roots stop with the tabs' registry after this.
- (void)tabsWillGo;

#pragma mark - The custom row

/// The custom row shows (it is the bar the screens keep room for).
@property(nonatomic, readonly) BOOL customRowShown;

/// The row keeps the visibility itself: on a wide iPad or Mac the system bar stays hidden under it.
- (BOOL)customRowKeepsVisibility;

/// `bottomTabs.visible` of the row in that case.
- (void)setCustomRowHidden:(BOOL)hidden;

@end

NS_ASSUME_NONNULL_END

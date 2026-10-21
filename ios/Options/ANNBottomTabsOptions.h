#import "ANNBottomTabsAccessoryOptions.h"
#import "ANNBottomTabsCustomRowOptions.h"
#import "ANNBottomTabsShadowOptions.h"
#import "ANNOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// `bottomTabs` (`OptionsBottomTabs`). `currentTabIndex` and `currentTabId` are one-shot: a node's own options never
/// keep them.
@interface ANNBottomTabsOptions : ANNOptions

@property(nonatomic, strong) Bool *visible;
@property(nonatomic, strong) Bool *animate;
/// The index of the tab to select; a whole number (the fraction is dropped).
@property(nonatomic, strong) Double *currentTabIndex;
@property(nonatomic, strong) Text *currentTabId;
@property(nonatomic, strong) Text *testID;
@property(nonatomic, strong) Text *accessibilityLabel;
@property(nonatomic, strong) Text *moreTestID;
@property(nonatomic, strong) Bool *drawBehind;
@property(nonatomic, strong) Color *backgroundColor;
/// `'together'`, `'afterInitialTab'` or `'onSwitchToTab'`.
@property(nonatomic, strong) Text *tabsAttachMode;
/// `'default'` or `'black'`.
@property(nonatomic, strong) Text *barStyle;
/// `'stretch'` or `'compact'`.
@property(nonatomic, strong) Text *layoutStyle;
@property(nonatomic, strong) Double *cornerRadius;
@property(nonatomic, strong) Double *bottomMargin;
@property(nonatomic, strong) Bool *translucent;
@property(nonatomic, strong) Bool *hideShadow;
/// `'alwaysShow'`, `'showWhenActive'`, `'alwaysHide'` or `'showWhenActiveForce'`.
@property(nonatomic, strong) Text *titleDisplayMode;
@property(nonatomic, strong) Double *elevation;
@property(nonatomic, strong) Bool *hideOnScroll;
@property(nonatomic, strong) Color *borderColor;
@property(nonatomic, strong) Double *borderWidth;
@property(nonatomic, strong) ANNBottomTabsShadowOptions *shadow;
@property(nonatomic, strong) ANNBottomTabsCustomRowOptions *customRow;
/// `'automatic'`, `'tabBar'` or `'sidebar'`.
@property(nonatomic, strong) Text *sidebar;
/// `'tile'` or `'overlap'`.
@property(nonatomic, strong) Text *sidebarLayout;
@property(nonatomic, strong) Text *customizationId;
/// `'automatic'`, `'never'`, `'onScrollDown'` or `'onScrollUp'`.
@property(nonatomic, strong) Text *minimizeBehavior;
@property(nonatomic, strong) ANNBottomTabsAccessoryOptions *bottomAccessory;

@end

NS_ASSUME_NONNULL_END

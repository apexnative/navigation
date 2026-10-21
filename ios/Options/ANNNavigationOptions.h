#import "ANNAnimationOptions.h"
#import "ANNBottomTabOptions.h"
#import "ANNBottomTabsOptions.h"
#import "ANNFabOptions.h"
#import "ANNHardwareBackButtonOptions.h"
#import "ANNLayoutOptions.h"
#import "ANNModalOptions.h"
#import "ANNNavigationBarOptions.h"
#import "ANNOptions.h"
#import "ANNOverlayOptions.h"
#import "ANNPreviewOptions.h"
#import "ANNScrollEdgeEffectOptions.h"
#import "ANNSideMenuOptions.h"
#import "ANNSplitViewOptions.h"
#import "ANNStatusBarOptions.h"
#import "ANNToolbarOptions.h"
#import "ANNTopBarOptions.h"
#import "ANNWindowOptions.h"

NS_ASSUME_NONNULL_BEGIN

/// The options of a layout node, the whole `Options` tree, and `window`, which JS puts into the default options only.
/// Read once (`ANNOptionsReader`), merged by one rule, resolved through each node's cache.
@interface ANNNavigationOptions : ANNOptions

@property(nonatomic, strong) ANNTopBarOptions *topBar;
@property(nonatomic, strong) ANNToolbarOptions *toolbar;
@property(nonatomic, strong) ANNScrollEdgeEffectOptions *scrollEdgeEffect;
@property(nonatomic, strong) ANNBottomTabsOptions *bottomTabs;
@property(nonatomic, strong) ANNBottomTabOptions *bottomTab;
@property(nonatomic, strong) ANNModalOptions *modal;
/// `'pageSheet'`, `'formSheet'`, `'fullScreen'`, `'overFullScreen'`, `'overCurrentContext'`, `'currentContext'` or
/// `'popover'`.
@property(nonatomic, strong) Text *modalPresentationStyle;
/// `'coverVertical'`, `'crossDissolve'`, `'flipHorizontal'` or `'partialCurl'`.
@property(nonatomic, strong) Text *modalTransitionStyle;
@property(nonatomic, strong) ANNOverlayOptions *overlay;
@property(nonatomic, strong) ANNSideMenuOptions *sideMenu;
@property(nonatomic, strong) ANNSplitViewOptions *splitView;
@property(nonatomic, strong) ANNLayoutOptions *layout;
@property(nonatomic, strong) ANNStatusBarOptions *statusBar;
@property(nonatomic, strong) ANNNavigationBarOptions *navigationBar;
@property(nonatomic, strong) ANNAnimationOptions *animations;
/// `true`, `false` or `'edge'`.
@property(nonatomic, strong) Param *popGesture;
@property(nonatomic, strong) ANNHardwareBackButtonOptions *hardwareBackButton;
@property(nonatomic, strong) ANNFabOptions *fab;
/// The shortcuts as JS gives them, `{ id, input, modifiers?, title? }` (`ANNKeyCommands`); nil when unset.
@property(nonatomic, strong, nullable) NSArray<NSDictionary *> *keyCommands;
@property(nonatomic, strong) Bool *blurOnUnmount;
@property(nonatomic, strong) ANNPreviewOptions *preview;
@property(nonatomic, strong) ANNWindowOptions *window;

/// These options without the one-shot ones (`bottomTabs.currentTabIndex`, `bottomTabs.currentTabId`,
/// `sideMenu.left.visible`, `sideMenu.right.visible`): a copy when they have any, else these options themselves.
- (ANNNavigationOptions *)withoutOneShotOptions;

@end

NS_ASSUME_NONNULL_END

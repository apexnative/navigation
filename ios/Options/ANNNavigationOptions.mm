#import "ANNNavigationOptions.h"

@implementation ANNNavigationOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"topBar" class:ANNTopBarOptions.class],
        [ANNOptionField groupField:@"toolbar" class:ANNToolbarOptions.class],
        [ANNOptionField groupField:@"scrollEdgeEffect" class:ANNScrollEdgeEffectOptions.class],
        [ANNOptionField groupField:@"bottomTabs" class:ANNBottomTabsOptions.class],
        [ANNOptionField groupField:@"bottomTab" class:ANNBottomTabOptions.class],
        [ANNOptionField groupField:@"modal" class:ANNModalOptions.class],
        [ANNOptionField enumField:@"modalPresentationStyle"
                           values:@[
                               @"pageSheet", @"formSheet", @"fullScreen", @"overFullScreen", @"overCurrentContext",
                               @"currentContext", @"popover"
                           ]],
        [ANNOptionField enumField:@"modalTransitionStyle"
                           values:@[ @"coverVertical", @"crossDissolve", @"flipHorizontal", @"partialCurl" ]],
        [ANNOptionField groupField:@"overlay" class:ANNOverlayOptions.class],
        [ANNOptionField groupField:@"sideMenu" class:ANNSideMenuOptions.class],
        [ANNOptionField groupField:@"splitView" class:ANNSplitViewOptions.class],
        [ANNOptionField groupField:@"layout" class:ANNLayoutOptions.class],
        [ANNOptionField groupField:@"statusBar" class:ANNStatusBarOptions.class],
        [ANNOptionField groupField:@"navigationBar" class:ANNNavigationBarOptions.class],
        [ANNOptionField groupField:@"animations" class:ANNAnimationOptions.class],
        [ANNOptionField paramField:@"popGesture"
                             check:^BOOL(id value) {
                               return ANNJSONIsBoolean(value) || [value isEqual:@"edge"];
                             }],
        [ANNOptionField groupField:@"hardwareBackButton" class:ANNHardwareBackButtonOptions.class],
        [ANNOptionField groupField:@"fab" class:ANNFabOptions.class],
        [ANNOptionField objectArrayField:@"keyCommands"],
        [ANNOptionField boolField:@"blurOnUnmount"],
        [ANNOptionField groupField:@"preview" class:ANNPreviewOptions.class],
        [ANNOptionField groupField:@"window" class:ANNWindowOptions.class],
    ];
}

- (ANNNavigationOptions *)withoutOneShotOptions {
    BOOL hasOneShot = self.bottomTabs.currentTabIndex.hasValue || self.bottomTabs.currentTabId.hasValue ||
                      self.sideMenu.left.visible.hasValue || self.sideMenu.right.visible.hasValue;
    if (!hasOneShot) {
        return self;
    }
    ANNNavigationOptions *options = [self copy];
    options.bottomTabs.currentTabIndex = [[Double alloc] initWithValue:nil];
    options.bottomTabs.currentTabId = [[Text alloc] initWithValue:nil];
    options.sideMenu.left.visible = [[Bool alloc] initWithValue:nil];
    options.sideMenu.right.visible = [[Bool alloc] initWithValue:nil];
    return options;
}

@end

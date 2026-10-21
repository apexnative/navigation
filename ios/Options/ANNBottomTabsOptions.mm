#import "ANNBottomTabsOptions.h"

@implementation ANNBottomTabsOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField boolField:@"animate"],
        [ANNOptionField doubleField:@"currentTabIndex"],
        [ANNOptionField textField:@"currentTabId"],
        [ANNOptionField textField:@"testID"],
        [ANNOptionField textField:@"accessibilityLabel"],
        [ANNOptionField textField:@"moreTestID"],
        [ANNOptionField boolField:@"drawBehind"],
        [ANNOptionField colorField:@"backgroundColor"],
        [ANNOptionField enumField:@"tabsAttachMode"
                           values:@[
                               @"together", @"afterInitialTab", @"onSwitchToTab"
                           ]],
        [ANNOptionField enumField:@"barStyle" values:@[ @"default", @"black" ]],
        [ANNOptionField enumField:@"layoutStyle" values:@[ @"stretch", @"compact" ]],
        [ANNOptionField doubleField:@"cornerRadius"],
        [ANNOptionField doubleField:@"bottomMargin"],
        [ANNOptionField boolField:@"translucent"],
        [ANNOptionField boolField:@"hideShadow"],
        [ANNOptionField enumField:@"titleDisplayMode"
                           values:@[
                               @"alwaysShow", @"showWhenActive", @"alwaysHide",
                               @"showWhenActiveForce"
                           ]],
        [ANNOptionField doubleField:@"elevation"],
        [ANNOptionField boolField:@"hideOnScroll"],
        [ANNOptionField colorField:@"borderColor"],
        [ANNOptionField doubleField:@"borderWidth"],
        [ANNOptionField groupField:@"shadow" class:ANNBottomTabsShadowOptions.class],
        [ANNOptionField groupField:@"customRow" class:ANNBottomTabsCustomRowOptions.class],
        [ANNOptionField enumField:@"sidebar" values:@[ @"automatic", @"tabBar", @"sidebar" ]],
        [ANNOptionField enumField:@"sidebarLayout" values:@[ @"tile", @"overlap" ]],
        [ANNOptionField textField:@"customizationId"],
        [ANNOptionField enumField:@"minimizeBehavior"
                           values:@[
                               @"automatic", @"never", @"onScrollDown", @"onScrollUp"
                           ]],
        [ANNOptionField groupField:@"bottomAccessory" class:ANNBottomTabsAccessoryOptions.class],
    ];
}

@end

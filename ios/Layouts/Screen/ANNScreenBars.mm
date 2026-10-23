#import "ANNScreenBars.h"
#import "ANNTabsDesign.h"
#import <UIKit/UIKit.h>

NSString *ANNLargeTitleDisplayMode(ANNNavigationOptions *options) {
    ANNTopBarLargeTitleOptions *largeTitle = options.topBar.largeTitle;
    NSString *mode = largeTitle.displayMode.hasValue ? [largeTitle.displayMode get] : nil;
    if (mode.length == 0) {
        return [largeTitle.visible withDefault:NO] ? @"always" : @"never";
    }
    if ([mode isEqualToString:@"inline"]) {
        if (@available(iOS 17.0, *)) {
            return mode;
        }
        return @"always";
    }
    return mode;
}

BOOL ANNLargeTitleCanShow(ANNNavigationOptions *options) {
    return ![ANNLargeTitleDisplayMode(options) isEqualToString:@"never"];
}

BOOL ANNLargeTitleShowsLarge(ANNNavigationOptions *options) {
    NSString *mode = ANNLargeTitleDisplayMode(options);
    return [mode isEqualToString:@"always"] || [mode isEqualToString:@"automatic"];
}

BOOL ANNScreenIsUnderTopBar(ANNNavigationOptions *options) {
    ANNTopBarOptions *topBar = options.topBar;
    return [topBar.drawBehind withDefault:NO] || [topBar.background.translucent withDefault:NO] ||
           ![topBar.visible withDefault:YES] || ANNLargeTitleCanShow(options) ||
           [topBar.hideOnScroll withDefault:NO] || [topBar.searchBar.hideOnScroll withDefault:NO];
}

BOOL ANNScreenIsUnderTabBar(ANNNavigationOptions *options) {
    ANNBottomTabsOptions *bottomTabs = options.bottomTabs;
    // Under the glass bar by default; the compatibility mode keeps the opaque bar of before iOS 26.
    BOOL drawsBehindByDefault = ANNTabsDesign.isGlass;
    return [bottomTabs.drawBehind withDefault:drawsBehindByDefault] ||
           [bottomTabs.translucent withDefault:NO] || ![bottomTabs.visible withDefault:YES];
}

BOOL ANNOptionsChangeScreenBars(ANNNavigationOptions *options) {
    ANNTopBarOptions *topBar = options.topBar;
    ANNBottomTabsOptions *bottomTabs = options.bottomTabs;
    return topBar.drawBehind.hasValue || topBar.background.translucent.hasValue ||
           topBar.visible.hasValue || topBar.largeTitle.displayMode.hasValue ||
           topBar.largeTitle.visible.hasValue || topBar.hideOnScroll.hasValue ||
           topBar.searchBar.hideOnScroll.hasValue || bottomTabs.drawBehind.hasValue ||
           bottomTabs.translucent.hasValue || bottomTabs.visible.hasValue;
}

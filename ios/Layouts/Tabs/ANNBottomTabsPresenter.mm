#import "ANNBottomTabsPresenter.h"
#import "ANNBottomTabItemBuilder.h"
#import "ANNBottomTabsAccessoryView.h"
#import "ANNBottomTabsController.h"
#import "ANNBottomTabsCustomRow.h"
#import "ANNOptionImages.h"
#import "ANNTabDotView.h"
#import "ANNTabsDesign.h"
#import "UITabBar+utils.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>

// The dot of a tab before iOS 26: its default size and color.
static const CGFloat ANNDotDefaultSize = 6;
// A button not there yet gets its `testID` again this often, at most this many times (`bottomTab.testID`).
static const NSTimeInterval ANNTestIDRetryInterval = 0.15;
static const NSUInteger ANNTestIDRetries = 5;

// A picture of `color` (both of its styles when it is dynamic): the bar's 1×1 background and the border's line.
static UIImage *ANNImageOfColor(UIColor *color, CGSize size) {
    UIImage * (^draw)(UITraitCollection *) = ^UIImage *(UITraitCollection *traits) {
      UIGraphicsImageRendererFormat *format = [UIGraphicsImageRendererFormat preferredFormat];
      format.opaque = NO;
      UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:size
                                                                                 format:format];
      UIColor *resolved = [color resolvedColorWithTraitCollection:traits];
      return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
        [resolved setFill];
        [context fillRect:CGRectMake(0, 0, size.width, size.height)];
      }];
    };
    UITraitCollection *light =
        [UITraitCollection traitCollectionWithUserInterfaceStyle:UIUserInterfaceStyleLight];
    UITraitCollection *dark =
        [UITraitCollection traitCollectionWithUserInterfaceStyle:UIUserInterfaceStyleDark];
    UIImage *lightImage = draw(light);
    UIImageAsset *asset = [UIImageAsset new];
    [asset registerImage:lightImage withTraitCollection:light];
    [asset registerImage:draw(dark) withTraitCollection:dark];
    return [asset imageWithTraitCollection:UITraitCollection.currentTraitCollection] ?: lightImage;
}

static BOOL ANNSameText(NSString *a, NSString *b) {
    return a == b || [a isEqualToString:b];
}

static id ANNOrNull(id value) {
    return value ?: NSNull.null;
}

static id ANNValue(NSArray *list, NSUInteger index) {
    id value = index < list.count ? list[index] : nil;
    return value == NSNull.null ? nil : value;
}

@implementation ANNBottomTabsPresenter {
    // Per tab, by layout index: the badge text the item shows and the dot it shows (held across rebuilds), the dot's
    // view before iOS 26, whether the item is a system item of a role (before iOS 18), the `testID` given to its
    // button.
    NSMutableArray *_badges;
    NSMutableArray<ANNBottomTabDotIndicatorOptions *> *_dots;
    NSMutableArray *_dotViews;
    NSMutableArray<NSNumber *> *_roleItems;
    // The identifiers the buttons had before the core gave them a `testID`, to give back.
    NSMapTable<UIView *, NSString *> *_buttonIds;
    BOOL _tabsAreSet;
    BOOL _titlesHidden;
    // The bar's look: its options, and the background and border every item's look starts from.
    ANNBottomTabsOptions *_barOptions;
    UITabBarAppearance *_base;
    ANNBottomTabsCustomRow *_row;
    // The accessory shown (its id), and the content view of each accessory made, kept until the tabs go.
    NSString *_accessoryId;
    NSMutableDictionary<NSString *, ANNBottomTabsAccessoryView *> *_accessoryViews;
    // The pictures of the bar's colors by color and size.
    NSMutableDictionary<NSArray *, UIImage *> *_colorImages;
    // The icons an item waits for, by layout index: a token per wait, which a later build of the item replaces.
    NSMutableDictionary<NSNumber *, NSObject *> *_iconLoads;
}

- (nullable ANNBottomTabsController *)tabs {
    UIViewController *bound = self.boundViewController;
    return [bound isKindOfClass:ANNBottomTabsController.class] ? (ANNBottomTabsController *)bound
                                                               : nil;
}

+ (void)repaintNode:(UIViewController *)node {
    [self repaintNode:node showingIn:node.viewIfLoaded];
}

// Children first. Only what shows with the tab repaints: a node whose view is out of the tab's views (a stack's lower
// screens, a hidden tab of tabs inside) takes the new options at its own appearance, as the repaint of what shows
// leaves the nodes out of a window (`ANNOptionsRepaint`).
+ (void)repaintNode:(UIViewController *)node showingIn:(nullable UIView *)tabView {
    for (UIViewController *child in node.layoutChildren) {
        [self repaintNode:child showingIn:tabView];
    }
    UIView *view = node.viewIfLoaded;
    if (tabView && view && [view isDescendantOfView:tabView]) {
        [node.presenter applyDefaultOptionsChange:[node resolveOptionsWithDefault]];
    }
}

#pragma mark - Creation

- (void)applyFirstLook {
    UITabBar *bar = self.tabs.tabBar;
    UITabBarAppearance *appearance = [UITabBarAppearance new];
    [appearance configureWithOpaqueBackground];
    appearance.backgroundColor = UIColor.systemBackgroundColor;
    appearance.backgroundEffect = nil;
    bar.standardAppearance = appearance;
    bar.scrollEdgeAppearance = appearance;
    bar.translucent = NO;
    _base = appearance;
}

- (void)styleItemsBeforeTabsAreSet {
    ANNBottomTabsController *tabs = self.tabs;
    NSUInteger count = tabs.layoutTabs.count;
    _badges = [NSMutableArray new];
    _dots = [NSMutableArray new];
    _dotViews = [NSMutableArray new];
    _roleItems = [NSMutableArray new];
    _buttonIds = [NSMapTable weakToStrongObjectsMapTable];
    _accessoryViews = [NSMutableDictionary new];
    _colorImages = [NSMutableDictionary new];
    _iconLoads = [NSMutableDictionary new];
    for (NSUInteger index = 0; index < count; index++) {
        [_badges addObject:NSNull.null];
        [_dots addObject:[ANNBottomTabDotIndicatorOptions new]];
        [_dotViews addObject:NSNull.null];
        [_roleItems addObject:@NO];
    }
    ANNNavigationOptions *resolved = [tabs resolveOptionsWithDefault];
    _barOptions = [resolved.bottomTabs copy];
    _titlesHidden = [self titlesHiddenBy:_barOptions];
    _base = [self baseAppearanceOf:_barOptions titleFont:[self titleFontOf:resolved]];
    // The bar lays its buttons out with its own look: with the titles' font in it before the tabs are set, a title in
    // the app's font gets its width (not the system font's) and every title sits at one height (fix round 1).
    UITabBar *bar = tabs.tabBar;
    bar.standardAppearance = _base;
    bar.scrollEdgeAppearance = _base;
    [self makeRoleItemsIfNeeded];
    for (NSUInteger index = 0; index < count; index++) {
        ANNBottomTabOptions *bottomTab =
            [tabs.layoutTabs[index] resolveOptionsWithDefault].bottomTab;
        [self buildItemAtIndex:index bottomTab:bottomTab updatesBadgeAndDot:YES];
    }
}

// Before iOS 18 a role is the system item of the role (`bottomTab.role`): one search, known roles; others ordinary.
- (void)makeRoleItemsIfNeeded {
    ANNBottomTabsController *tabs = self.tabs;
    if (tabs.usesTabAPI || tabs.usesCustomRow) {
        return;
    }
    BOOL hasSearch = NO;
    for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
        UIViewController *tab = tabs.layoutTabs[index];
        NSString *role = [[tab resolveOptionsWithDefault].bottomTab.role withDefault:nil];
        if (role.length == 0) {
            continue;
        }
        UITabBarSystemItem systemItem = UITabBarSystemItemMore;
        if (![ANNBottomTabItemBuilder systemItemOfRole:role item:&systemItem]) {
            RCTLogWarn(
                @"[ApexNavigation] Unknown bottomTab role '%@' — falling back to normal tab.",
                role);
            continue;
        }
        if (systemItem == UITabBarSystemItemSearch) {
            if (hasSearch) {
                RCTLogWarn(
                    @"[ApexNavigation] Only one tab per bottomTabs layout may use role:'search'. "
                    @"Subsequent 'search' roles are ignored — creating a normal tab instead.");
                continue;
            }
            hasSearch = YES;
        }
        tab.tabBarItem = [[UITabBarItem alloc] initWithTabBarSystemItem:systemItem
                                                                    tag:(NSInteger)index];
        _roleItems[index] = @YES;
    }
}

- (void)configureSidebarAndCustomizationBeforeTabsAreSet {
    if (@available(iOS 18.0, *)) {
        ANNBottomTabsController *tabs = self.tabs;
        ANNBottomTabsOptions *bottomTabs = _barOptions;
        NSString *customizationId = [bottomTabs.customizationId withDefault:nil];
        BOOL customizes = customizationId.length > 0;
        tabs.customizationIdentifier = customizes ? customizationId : nil;
        for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
            UITab *uiTab = [tabs uiTabAtIndex:index];
            if (!uiTab) {
                continue;
            }
            ANNBottomTabOptions *bottomTab =
                [tabs.layoutTabs[index] resolveOptionsWithDefault].bottomTab;
            BOOL search = [uiTab isKindOfClass:UISearchTab.class];
            // Without customization every tab is fixed (the search tab pinned, as UIKit does): no Edit, the layout's
            // order, nothing UIKit saved before.
            UITabPlacement placement = search ? UITabPlacementPinned : UITabPlacementFixed;
            if (customizes) {
                NSString *customization = [bottomTab.customization withDefault:nil];
                placement = search && customization.length == 0 ? UITabPlacementPinned
                                                                : [self placementOf:customization];
            }
            uiTab.preferredPlacement = placement;
            uiTab.hiddenByDefault = ![bottomTab.shownByDefault withDefault:YES];
            uiTab.allowsHiding = [bottomTab.allowsHiding withDefault:NO];
        }
        [self applySidebar:bottomTabs named:NO];
    }
}

- (UITabPlacement)placementOf:(NSString *)customization API_AVAILABLE(ios(18.0)) {
    if ([customization isEqualToString:@"fixed"]) {
        return UITabPlacementFixed;
    }
    if ([customization isEqualToString:@"optional"]) {
        return UITabPlacementOptional;
    }
    if ([customization isEqualToString:@"pinned"]) {
        return UITabPlacementPinned;
    }
    if ([customization isEqualToString:@"sidebarOnly"]) {
        return UITabPlacementSidebarOnly;
    }
    return UITabPlacementMovable;
}

- (void)applyAfterTabsAreSet:(ANNNavigationOptions *)resolvedOptions {
    _tabsAreSet = YES;
    ANNBottomTabsController *tabs = self.tabs;
    // The glass's badge colors go in once the tabs are in the bar, and every item again with them.
    for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
        ANNBottomTabOptions *bottomTab =
            [tabs.layoutTabs[index] resolveOptionsWithDefault].bottomTab;
        [self buildItemAtIndex:index bottomTab:bottomTab updatesBadgeAndDot:NO];
    }
    if (_titlesHidden && !tabs.usesTabAPI) {
        [tabs.tabBar removeTabBarItemTitles];
    }
    [self applyBarLook:resolvedOptions];
    [self applyBackground:resolvedOptions];
    if (tabs.usesCustomRow) {
        _row = [[ANNBottomTabsCustomRow alloc] initWithTabs:tabs];
        [self updateCells];
        [_row applyOptions:resolvedOptions.bottomTabs.customRow];
    }
    [self applyAccessoryOf:resolvedOptions animated:NO];
    [self applyButtonTestIDsWithRetries:ANNTestIDRetries];
}

#pragma mark - ANNNodePresenter

// `mergeOptions` with the tabs' id: every item takes a `bottomTab` merge, the bar its part.
- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions
     resolvedOptions:(ANNNavigationOptions *)resolvedOptions {
    [super mergeOptions:mergeOptions resolvedOptions:resolvedOptions];
    ANNBottomTabsController *tabs = self.tabs;
    if (mergeOptions.bottomTab.hasValue) {
        for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
            [self mergeBottomTab:mergeOptions.bottomTab ofTabAtIndex:index];
        }
    }
    if (mergeOptions.bottomTabs.hasValue) {
        [self mergeChildBar:mergeOptions.bottomTabs
            resolvedOptions:resolvedOptions
            fromSelectedTab:YES];
    }
    if (mergeOptions.layout.backgroundColor.hasValue) {
        [self applyBackground:resolvedOptions];
    }
}

// New default options: the items of every tab, shown or not (badge and dot as they are, the badge color follows), and
// the shown bar's look; its visibility stays. The tabs not in a window repaint at their next selection.
- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions {
    [super applyDefaultOptionsChange:resolvedOptions];
    ANNBottomTabsController *tabs = self.tabs;
    [self applyBarLook:resolvedOptions];
    [self applyBackground:resolvedOptions];
    for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
        ANNBottomTabOptions *bottomTab =
            [tabs.layoutTabs[index] resolveOptionsWithDefault].bottomTab;
        [self buildItemAtIndex:index bottomTab:bottomTab updatesBadgeAndDot:NO];
    }
    [_row applyOptions:resolvedOptions.bottomTabs.customRow];
    [self updateCells];
    [tabs markUnshownTabsStale];
}

#pragma mark - Items (`bottomTab.*`)

- (void)mergeBottomTab:(ANNBottomTabOptions *)mergeOptions ofTabAtIndex:(NSUInteger)index {
    ANNBottomTabsController *tabs = self.tabs;
    if (index >= tabs.layoutTabs.count) {
        return;
    }
    ANNBottomTabOptions *bottomTab =
        [[tabs.layoutTabs[index] resolveOptionsWithDefault].bottomTab copy];
    [bottomTab mergeOptions:mergeOptions];
    BOOL badgeAndDot = mergeOptions.badge.hasValue || mergeOptions.dotIndicator.hasValue;
    if (mergeOptions.dotIndicator.hasValue && !mergeOptions.badge.hasValue) {
        // The dot merges by field over the one the item shows; the badge it shows stays.
        ANNBottomTabDotIndicatorOptions *dot = [_dots[index] copy];
        [dot mergeOptions:mergeOptions.dotIndicator];
        bottomTab.dotIndicator = dot;
        bottomTab.badge = [Text withValue:ANNValue(_badges, index)];
    } else if (mergeOptions.badge.hasValue && !mergeOptions.dotIndicator.hasValue) {
        bottomTab.dotIndicator = [_dots[index] copy];
    }
    [self buildItemAtIndex:index bottomTab:bottomTab updatesBadgeAndDot:badgeAndDot];
    [self updateCells];
    [self applyButtonTestIDsWithRetries:mergeOptions.testID.hasValue ? ANNTestIDRetries : 0];
}

// The whole item from `bottomTab`; the badge and the dot from it only with `updatesBadgeAndDot`.
- (void)buildItemAtIndex:(NSUInteger)index
               bottomTab:(ANNBottomTabOptions *)bottomTab
      updatesBadgeAndDot:(BOOL)updatesBadgeAndDot {
    ANNBottomTabsController *tabs = self.tabs;
    UIViewController *tab = tabs.layoutTabs[index];
    UITabBarItem *item = tab.tabBarItem;
    id uiTab = [tabs uiTabAtIndex:index];
    BOOL roleItem = [_roleItems[index] boolValue];
    BOOL row = tabs.usesCustomRow;
    if (updatesBadgeAndDot) {
        NSString *badge = [bottomTab.badge withDefault:nil];
        _badges[index] = ANNOrNull(badge.length > 0 ? badge : nil);
        _dots[index] = [bottomTab.dotIndicator copy] ?: [ANNBottomTabDotIndicatorOptions new];
    }
    NSString *badge = ANNValue(_badges, index);
    ANNBottomTabDotIndicatorOptions *dot = _dots[index];
    BOOL dotShown = !row && !badge && [dot.visible withDefault:NO];
    BOOL glass = ANNTabsDesign.isGlass;
    // The row's items show nothing: no image, no title, only the accessibility.
    NSString *text = [ANNBottomTabItemBuilder titleOf:bottomTab];
    NSString *title = row || _titlesHidden ? nil : text;
    // While new icons load off the main queue the item keeps the ones it shows; a wait of an earlier build is over.
    [_iconLoads removeObjectForKey:@(index)];
    BOOL keepsIcons = !row && [self loadsIconsLaterOf:bottomTab atIndex:index];
    UIImage *icon = row || keepsIcons ? nil : [ANNBottomTabItemBuilder iconOf:bottomTab];
    UIImage *selectedIcon =
        row || keepsIcons ? nil : [ANNBottomTabItemBuilder selectedIconOf:bottomTab];
    if (@available(iOS 18.0, *)) {
        if (uiTab) {
            UITab *systemTab = uiTab;
            BOOL search = [systemTab isKindOfClass:UISearchTab.class];
            // The search tab keeps the system's title and image unless the options give them.
            if (!search || text || row || _titlesHidden) {
                NSString *tabTitle = title ?: @"";
                if (!ANNSameText(systemTab.title, tabTitle)) {
                    systemTab.title = tabTitle;
                }
            }
            if (!keepsIcons && (!search || icon || row) && systemTab.image != icon) {
                systemTab.image = icon;
            }
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270000
            if (@available(iOS 26.1, *)) {
                if (!keepsIcons && (!search || selectedIcon || row) &&
                    systemTab.selectedImage != selectedIcon) {
                    systemTab.selectedImage = selectedIcon;
                }
            }
#endif
        }
    }
    if (!roleItem) {
        // A rebuilt item keeps no hidden title: the core clears it itself. Before the tab API the More list
        // shows the items of the tabs in it with their titles: a hidden title is cleared only in the bar.
        NSString *itemTitle = title;
        if (!row && _titlesHidden && !tabs.usesTabAPI && ![self itemIsInBar:item]) {
            itemTitle = text;
        }
        if (!ANNSameText(item.title, itemTitle)) {
            item.title = itemTitle;
        }
        if (row) {
            item.accessibilityLabel = text;
        }
    }
    if (!keepsIcons && (!roleItem || icon)) {
        if (item.image != icon) {
            item.image = icon;
        }
        if (item.selectedImage != selectedIcon) {
            item.selectedImage = selectedIcon;
        }
    }
    // The icon's insets act only before iOS 26, not on a role's item.
    if (@available(iOS 26.0, *)) {
    } else if (!roleItem) {
        UIEdgeInsets insets = [ANNBottomTabItemBuilder iconInsetsOf:bottomTab];
        if (!UIEdgeInsetsEqualToEdgeInsets(item.imageInsets, insets)) {
            item.imageInsets = insets;
        }
    }
    // The badge's color: the badge's own, or on the glass the dot's (an empty badge); on the glass only once the tab is
    // in the bar.
    UIColor *badgeColor = [bottomTab.badgeColor withDefault:nil];
    if (glass && !badge && dotShown) {
        badgeColor = [dot.color withDefault:UIColor.systemRedColor] ?: UIColor.systemRedColor;
    }
    if (glass && !_tabsAreSet) {
        badgeColor = nil;
    }
    UITabBarAppearance *appearance = [ANNBottomTabItemBuilder appearanceOf:bottomTab
                                                                      base:_base
                                                                badgeColor:badgeColor];
    if (![item.standardAppearance isEqual:appearance]) {
        item.standardAppearance = appearance;
    }
    if (![item.scrollEdgeAppearance isEqual:appearance]) {
        item.scrollEdgeAppearance = appearance;
    }
    // The badge; on the glass the dot is an empty badge; with the row neither shows on the bar.
    NSString *badgeValue = row ? nil : (badge ?: (glass && dotShown ? @"" : nil));
    if (!ANNSameText(item.badgeValue, badgeValue)) {
        item.badgeValue = badgeValue;
    }
    if (@available(iOS 18.0, *)) {
        UITab *systemTab = uiTab;
        if (systemTab && !ANNSameText(systemTab.badgeValue, badgeValue)) {
            systemTab.badgeValue = badgeValue;
        }
    }
    if (!glass) {
        [self updateDotAtIndex:index shown:dotShown options:dot];
    }
    // The `testID` of the item and of its `UITab`; the button's at the layout. With the row the cell carries it.
    NSString *testID = row ? nil : [bottomTab.testID withDefault:nil];
    testID = testID.length > 0 ? testID : nil;
    if (!ANNSameText(item.accessibilityIdentifier, testID)) {
        item.accessibilityIdentifier = testID;
    }
    if (@available(iOS 18.0, *)) {
        UITab *systemTab = uiTab;
        if (systemTab && !ANNSameText(systemTab.accessibilityIdentifier, testID)) {
            systemTab.accessibilityIdentifier = testID;
        }
    }
}

// New raster icons of an item on the bar that shows load off the main queue when the cache doesn't have them: the item
// takes them when they came, built again from the same options, unless it was built again first (the last build wins).
// The first icons, and every icon while the bar is out of a window, load at once: the tab's first frame shows them.
- (BOOL)loadsIconsLaterOf:(ANNBottomTabOptions *)bottomTab atIndex:(NSUInteger)index {
    if (!_tabsAreSet || !self.tabs.viewIfLoaded.window) {
        return NO;
    }
    NSMutableArray<ANNImageSource *> *later = [NSMutableArray new];
    for (Image *raster in [ANNBottomTabItemBuilder rastersOf:bottomTab]) {
        if ([ANNOptionImages loadsLater:raster.source]) {
            [later addObject:raster.source];
        }
    }
    if (!later.count) {
        return NO;
    }
    NSObject *load = [NSObject new];
    _iconLoads[@(index)] = load;
    __block NSUInteger pending = later.count;
    __block BOOL asking = YES;
    __weak ANNBottomTabsPresenter *weakSelf = self;
    for (ANNImageSource *source in later) {
        [ANNOptionImages loadLater:source
                              done:^(UIImage *image) {
                                pending--;
                                ANNBottomTabsPresenter *presenter = weakSelf;
                                if (pending == 0 && !asking) {
                                    [presenter iconsCame:load atIndex:index bottomTab:bottomTab];
                                }
                              }];
    }
    asking = NO;
    if (pending == 0) {
        // A load ended while the others were asked for: the cache has them all now.
        [_iconLoads removeObjectForKey:@(index)];
        return NO;
    }
    return YES;
}

- (void)iconsCame:(NSObject *)load
          atIndex:(NSUInteger)index
        bottomTab:(ANNBottomTabOptions *)bottomTab {
    if (_iconLoads[@(index)] != load || index >= self.tabs.layoutTabs.count) {
        return;
    }
    [_iconLoads removeObjectForKey:@(index)];
    [self buildItemAtIndex:index bottomTab:bottomTab updatesBadgeAndDot:NO];
}

- (void)updateDotAtIndex:(NSUInteger)index
                   shown:(BOOL)shown
                 options:(ANNBottomTabDotIndicatorOptions *)dot {
    ANNTabDotView *view = ANNValue(_dotViews, index);
    if (!shown) {
        view.hidden = YES;
        return;
    }
    if (!view) {
        view = [[ANNTabDotView alloc] initWithFrame:CGRectZero];
        _dotViews[index] = view;
    }
    CGFloat size = [dot.size withDefault:ANNDotDefaultSize];
    UIColor *color = [dot.color withDefault:UIColor.systemRedColor] ?: UIColor.systemRedColor;
    [view setDiameter:size color:color];
    [self placeDots];
}

// Before the tab API, hidden titles: UIKit moves items between the bar and the More list as the width changes (an iPad
// window); an item in the bar shows no title, one in the More list its title.
- (void)updateHiddenTitles {
    ANNBottomTabsController *tabs = self.tabs;
    if (!_titlesHidden || tabs.usesTabAPI || tabs.usesCustomRow) {
        return;
    }
    for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
        UIViewController *tab = tabs.layoutTabs[index];
        UITabBarItem *item = tab.tabBarItem;
        if ([_roleItems[index] boolValue]) {
            continue;
        }
        NSString *title = [self itemIsInBar:item]
                              ? nil
                              : [ANNBottomTabItemBuilder
                                    titleOf:[tab resolveOptionsWithDefault].bottomTab];
        if (!ANNSameText(item.title, title)) {
            item.title = title;
        }
    }
}

// Whether UIKit shows the item in the bar, not in the More list.
- (BOOL)itemIsInBar:(UITabBarItem *)item {
    return _tabsAreSet && [self.tabs.tabBar.items indexOfObjectIdenticalTo:item] != NSNotFound;
}

// Each dot at its tab's button; a tab without a button (in More) has none. The bar lays itself out first: in
// the tabs' layout and right after an item's rebuild its buttons (and an icon centered by the item layout, a new
// icon's size) still have their previous frames.
- (void)placeDots {
    ANNBottomTabsController *tabs = self.tabs;
    UITabBar *bar = tabs.tabBar;
    BOOL anyDot = NO;
    for (NSUInteger index = 0; index < tabs.layoutTabs.count && !anyDot; index++) {
        anyDot = ANNValue(_dotViews, index) != nil && !tabs.usesCustomRow &&
                 !ANNValue(_badges, index) && [_dots[index].visible withDefault:NO];
    }
    if (anyDot) {
        [bar layoutIfNeeded];
    }
    for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
        ANNTabDotView *view = ANNValue(_dotViews, index);
        if (!view) {
            continue;
        }
        BOOL shown = !tabs.usesCustomRow && !ANNValue(_badges, index) &&
                     [_dots[index].visible withDefault:NO];
        if (!shown) {
            view.hidden = YES;
            continue;
        }
        UITabBarItem *item = tabs.layoutTabs[index].tabBarItem;
        BOOL inBar = [bar.items indexOfObjectIdenticalTo:item] != NSNotFound;
        [view placeOverButton:inBar ? [bar viewOfItem:item] : nil inBar:bar];
    }
}

#pragma mark - testID (`bottomTab.testID`, `bottomTabs.moreTestID`)

// Each tab's button carries its `testID`; a button not there yet gets it again after 0.15 s, at most 5 times; a
// removed `testID` gives the button its identifier back.
- (void)applyButtonTestIDsWithRetries:(NSUInteger)retries {
    ANNBottomTabsController *tabs = self.tabs;
    UITabBar *bar = tabs.tabBar;
    if (!tabs || !bar) {
        return;
    }
    BOOL missing = NO;
    for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
        UITabBarItem *item = tabs.layoutTabs[index].tabBarItem;
        NSString *testID = item.accessibilityIdentifier;
        UIView *button = [bar viewOfItem:item];
        if (!button) {
            missing = missing || testID.length > 0;
            continue;
        }
        [self setTestID:testID ofButton:button];
    }
    if (tabs.layoutTabs.count > 5) {
        NSString *moreTestID = [_barOptions.moreTestID withDefault:nil];
        UIView *button = [bar viewOfItem:tabs.moreNavigationController.tabBarItem];
        if (button) {
            [self setTestID:moreTestID ofButton:button];
        } else {
            missing = missing || moreTestID.length > 0;
        }
    }
    if (!missing || retries == 0) {
        return;
    }
    __weak ANNBottomTabsPresenter *weakSelf = self;
    dispatch_after(
        dispatch_time(DISPATCH_TIME_NOW, (int64_t)(ANNTestIDRetryInterval * NSEC_PER_SEC)),
        dispatch_get_main_queue(), ^{
          [weakSelf applyButtonTestIDsWithRetries:retries - 1];
        });
}

- (void)setTestID:(NSString *)testID ofButton:(UIView *)button {
    if (testID.length > 0) {
        if (![_buttonIds objectForKey:button]) {
            [_buttonIds setObject:button.accessibilityIdentifier ?: @"" forKey:button];
        }
        if (!ANNSameText(button.accessibilityIdentifier, testID)) {
            button.accessibilityIdentifier = testID;
        }
        return;
    }
    NSString *previous = [_buttonIds objectForKey:button];
    if (previous) {
        button.accessibilityIdentifier = previous.length > 0 ? previous : nil;
        [_buttonIds removeObjectForKey:button];
    }
}

#pragma mark - The bar's look

- (BOOL)titlesHiddenBy:(ANNBottomTabsOptions *)bottomTabs {
    return [[bottomTabs.titleDisplayMode withDefault:nil] isEqualToString:@"alwaysHide"] &&
           !self.tabs.usesCustomRow;
}

// The font of the titles of the bar: the shown tab's (`bottomTab.font` with its stack's, the tabs' and the defaults').
- (UIFont *)titleFontOf:(ANNNavigationOptions *)resolvedOptions {
    return [ANNBottomTabItemBuilder titleFontOf:resolvedOptions.bottomTab];
}

// The picture of a color, made once while the color stays (an equal look sets nothing again).
- (UIImage *)imageOfColor:(UIColor *)color size:(CGSize)size {
    NSArray *key = @[ color, [NSValue valueWithCGSize:size] ];
    UIImage *image = _colorImages[key];
    if (!image) {
        image = ANNImageOfColor(color, size);
        _colorImages[key] = image;
    }
    return image;
}

// The background and the border every item's look starts from, with the titles' font in every layout and state.
- (UITabBarAppearance *)baseAppearanceOf:(ANNBottomTabsOptions *)bottomTabs
                                titleFont:(UIFont *)titleFont {
    UITabBarAppearance *appearance = [self backgroundAppearanceOf:bottomTabs];
    if (titleFont) {
        NSArray<UITabBarItemAppearance *> *layouts = @[
            appearance.stackedLayoutAppearance, appearance.inlineLayoutAppearance,
            appearance.compactInlineLayoutAppearance
        ];
        for (UITabBarItemAppearance *layout in layouts) {
            for (UITabBarItemStateAppearance *state in @[ layout.normal, layout.selected ]) {
                NSMutableDictionary<NSAttributedStringKey, id> *attributes =
                    [state.titleTextAttributes mutableCopy] ?: [NSMutableDictionary new];
                attributes[NSFontAttributeName] = titleFont;
                state.titleTextAttributes = attributes;
            }
        }
    }
    return appearance;
}

- (UITabBarAppearance *)backgroundAppearanceOf:(ANNBottomTabsOptions *)bottomTabs {
    UITabBarAppearance *appearance = [UITabBarAppearance new];
    UIColor *color = [bottomTabs.backgroundColor withDefault:nil];
    BOOL transparent =
        color &&
        CGColorGetAlpha(
            [color resolvedColorWithTraitCollection:UITraitCollection.currentTraitCollection]
                .CGColor) == 0;
    if (ANNTabsDesign.isGlass) {
        // The glass takes no color; a transparent one gives a transparent bar.
        if (transparent) {
            [appearance configureWithTransparentBackground];
        } else {
            [appearance configureWithDefaultBackground];
            if (color) {
                static BOOL warned;
                if (!warned) {
                    warned = YES;
                    RCTLogWarn(
                        @"[ApexNavigation] bottomTabs.backgroundColor is ignored with Liquid "
                        @"Glass (iOS 26). It applies with UIDesignRequiresCompatibility and on "
                        @"earlier iOS versions.");
                }
            }
        }
        return appearance;
    }
    if (transparent) {
        [appearance configureWithTransparentBackground];
    } else if (color) {
        [appearance configureWithOpaqueBackground];
        appearance.backgroundColor = color;
        appearance.backgroundImage = [self imageOfColor:color size:CGSizeMake(1, 1)];
    } else if ([bottomTabs.translucent withDefault:NO]) {
        [appearance configureWithDefaultBackground];
    } else {
        [appearance configureWithOpaqueBackground];
        appearance.backgroundColor = UIColor.systemBackgroundColor;
        appearance.backgroundEffect = nil;
    }
    // The top border: a line of `borderWidth` (0.1 with a color alone) in `borderColor` (black).
    if (!transparent && (bottomTabs.borderColor.hasValue || bottomTabs.borderWidth.hasValue)) {
        UIColor *borderColor =
            [bottomTabs.borderColor withDefault:UIColor.blackColor] ?: UIColor.blackColor;
        CGFloat width = bottomTabs.borderWidth.hasValue ? bottomTabs.borderWidth.get : 0.1;
        appearance.shadowColor = nil;
        CGSize lineSize = CGSizeMake(1, MAX(width, 0.1));
        appearance.shadowImage = [self imageOfColor:borderColor size:lineSize];
    }
    return appearance;
}

- (void)applyBarLook:(ANNNavigationOptions *)resolvedOptions {
    ANNBottomTabsController *tabs = self.tabs;
    UITabBar *bar = tabs.tabBar;
    if (!tabs || !bar) {
        return;
    }
    ANNBottomTabsOptions *bottomTabs = resolvedOptions.bottomTabs;
    _barOptions = [bottomTabs copy];
    BOOL glass = ANNTabsDesign.isGlass;
    UITabBarAppearance *base = [self baseAppearanceOf:bottomTabs
                                            titleFont:[self titleFontOf:resolvedOptions]];
    BOOL titlesHidden = [self titlesHiddenBy:bottomTabs];
    BOOL itemsChange = ![base isEqual:_base] || titlesHidden != _titlesHidden;
    _base = base;
    _titlesHidden = titlesHidden;
    if (![bar.standardAppearance isEqual:base]) {
        bar.standardAppearance = base;
    }
    if (![bar.scrollEdgeAppearance isEqual:base]) {
        bar.scrollEdgeAppearance = base;
    }
    if (!glass) {
        UIColor *color = [bottomTabs.backgroundColor withDefault:nil];
        BOOL translucent = !color && [bottomTabs.translucent withDefault:NO];
        if (bar.translucent != translucent) {
            bar.translucent = translucent;
        }
        if (!(bar.barTintColor == color || [bar.barTintColor isEqual:color])) {
            bar.barTintColor = color;
        }
        if (bottomTabs.barStyle.hasValue) {
            BOOL black = [[bottomTabs.barStyle withDefault:nil] isEqualToString:@"black"];
            UIBarStyle style = black ? UIBarStyleBlack : UIBarStyleDefault;
            if (bar.barStyle != style) {
                bar.barStyle = style;
            }
        }
        // The compatibility mode colors the bar's own view too.
        if (ANNTabsDesign.isCompatibilityMode &&
            !(bar.backgroundColor == color || [bar.backgroundColor isEqual:color])) {
            bar.backgroundColor = color;
        }
    }
    // `hideShadow` clips the bar's view on every version (`bottomTabs.hideShadow`).
    BOOL clips = [bottomTabs.hideShadow withDefault:NO];
    if (bar.clipsToBounds != clips) {
        bar.clipsToBounds = clips;
    }
    // The shadow of the bar's layer: an unset field keeps the current one (`bottomTabs.shadow`).
    ANNBottomTabsShadowOptions *shadow = bottomTabs.shadow;
    if (shadow.opacity.hasValue) {
        bar.layer.shadowOpacity = (float)shadow.opacity.get;
    }
    if (shadow.color.hasValue) {
        bar.layer.shadowColor = [shadow.color withDefault:nil].CGColor;
    }
    if (shadow.radius.hasValue) {
        bar.layer.shadowRadius = shadow.radius.get;
    }
    NSString *testID = [bottomTabs.testID withDefault:nil];
    testID = testID.length > 0 ? testID : nil;
    if (!ANNSameText(bar.accessibilityIdentifier, testID)) {
        bar.accessibilityIdentifier = testID;
    }
    NSString *label = [bottomTabs.accessibilityLabel withDefault:nil];
    label = label.length > 0 ? label : nil;
    if (!ANNSameText(bar.accessibilityLabel, label)) {
        bar.accessibilityLabel = label;
    }
    // Icons in the middle without titles: only the old design needs it, the module knows.
    [bar setItemImagesCentered:titlesHidden];
    [self applyMinimizeBehavior:bottomTabs named:NO];
    if (itemsChange && _tabsAreSet) {
        for (NSUInteger index = 0; index < tabs.layoutTabs.count; index++) {
            ANNBottomTabOptions *bottomTab =
                [tabs.layoutTabs[index] resolveOptionsWithDefault].bottomTab;
            [self buildItemAtIndex:index bottomTab:bottomTab updatesBadgeAndDot:NO];
        }
        if (titlesHidden && !tabs.usesTabAPI) {
            [bar removeTabBarItemTitles];
        }
    }
}

// iOS 26: the system's minimizing; with the row always `never`, with the warning when the option is set.
- (void)applyMinimizeBehavior:(ANNBottomTabsOptions *)bottomTabs named:(BOOL)named {
    if (@available(iOS 26.0, *)) {
        ANNBottomTabsController *tabs = self.tabs;
        UITabBarMinimizeBehavior behavior = UITabBarMinimizeBehaviorAutomatic;
        NSString *value = [bottomTabs.minimizeBehavior withDefault:nil];
        if (tabs.usesCustomRow) {
            if (named && value) {
                RCTLogWarn(
                    @"[ApexNavigation] bottomTabs.minimizeBehavior is ignored with tab items drawn "
                    @"by components (bottomTab.component).");
            }
            behavior = UITabBarMinimizeBehaviorNever;
        } else if ([value isEqualToString:@"never"]) {
            behavior = UITabBarMinimizeBehaviorNever;
        } else if ([value isEqualToString:@"onScrollDown"]) {
            behavior = UITabBarMinimizeBehaviorOnScrollDown;
        } else if ([value isEqualToString:@"onScrollUp"]) {
            behavior = UITabBarMinimizeBehaviorOnScrollUp;
        }
        if (tabs.tabBarMinimizeBehavior != behavior) {
            tabs.tabBarMinimizeBehavior = behavior;
        }
    }
}

// The iPad's mode and sidebar layout (`tile` by default, «2а»), at creation and merge; the custom row keeps the bar
// alone; customization keeps the mode; the iPhone Duo's sidebar.
- (void)applySidebar:(ANNBottomTabsOptions *)bottomTabs named:(BOOL)named API_AVAILABLE(ios(18.0)) {
    ANNBottomTabsController *tabs = self.tabs;
    NSString *sidebar = [bottomTabs.sidebar withDefault:@"automatic"] ?: @"automatic";
    if (tabs.usesCustomRow && [sidebar isEqualToString:@"sidebar"]) {
        RCTLogWarn(
            @"[ApexNavigation] bottomTabs.sidebar: 'sidebar' is ignored with tab items drawn by "
            @"components (bottomTab.component): the sidebar lists the system tabs.");
        sidebar = @"tabBar";
    }
    UITabBarControllerMode mode = UITabBarControllerModeAutomatic;
    if ([sidebar isEqualToString:@"tabBar"]) {
        mode = UITabBarControllerModeTabBar;
    } else if ([sidebar isEqualToString:@"sidebar"]) {
        mode = UITabBarControllerModeTabSidebar;
    }
    if (tabs.mode != mode) {
        if (named && tabs.customizationIdentifier.length > 0) {
            RCTLogWarn(
                @"[ApexNavigation] bottomTabs.sidebar does not change while the user customizes "
                @"the tabs (bottomTabs.customizationId).");
        } else {
            tabs.mode = mode;
        }
    }
    BOOL overlap = [[bottomTabs.sidebarLayout withDefault:nil] isEqualToString:@"overlap"];
    UITabBarControllerSidebarLayout layout =
        overlap ? UITabBarControllerSidebarLayoutOverlap : UITabBarControllerSidebarLayoutTile;
    if (tabs.sidebar.preferredLayout != layout) {
        tabs.sidebar.preferredLayout = layout;
    }
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270000
    if (@available(iOS 27.0, *)) {
        if (tabs.traitCollection.userInterfaceIdiom == UIUserInterfaceIdiomPhone) {
            // The placement of the mode in effect: the user's customization may have kept the mode.
            UITabBarControllerSidebarPlacement placement =
                tabs.mode == UITabBarControllerModeTabSidebar
                    ? UITabBarControllerSidebarPlacementSidebar
                    : UITabBarControllerSidebarPlacementAutomatic;
            if (tabs.sidebar.preferredPlacement != placement) {
                tabs.sidebar.preferredPlacement = placement;
            }
        }
    }
#endif
}

#pragma mark - The background (`layout.backgroundColor`)

- (void)applyBackground:(ANNNavigationOptions *)resolvedOptions {
    UIView *view = self.tabs.viewIfLoaded;
    if (!view) {
        return;
    }
    UIColor *color = [resolvedOptions.layout.backgroundColor withDefault:nil];
    if (!(view.backgroundColor == color || [view.backgroundColor isEqual:color])) {
        view.backgroundColor = color;
    }
}

#pragma mark - Merges of the bar

- (void)mergeChildBar:(ANNBottomTabsOptions *)mergeOptions
      resolvedOptions:(ANNNavigationOptions *)resolvedOptions
      fromSelectedTab:(BOOL)fromSelectedTab {
    ANNBottomTabsController *tabs = self.tabs;
    // The accessory from any tab, at once, with the tabs' options over the merge kept.
    if (mergeOptions.bottomAccessory.hasValue) {
        ANNNavigationOptions *accessoryOptions = [resolvedOptions copy];
        [accessoryOptions.bottomTabs.bottomAccessory mergeOptions:mergeOptions.bottomAccessory];
        [self applyAccessoryOf:accessoryOptions animated:YES];
    }
    if (!fromSelectedTab) {
        return;
    }
    ANNBottomTabsOptions *bottomTabs = resolvedOptions.bottomTabs;
    [self applyBarLook:resolvedOptions];
    if (mergeOptions.minimizeBehavior.hasValue) {
        [self applyMinimizeBehavior:bottomTabs named:YES];
    }
    if (@available(iOS 18.0, *)) {
        if (mergeOptions.sidebar.hasValue || mergeOptions.sidebarLayout.hasValue) {
            [self applySidebar:bottomTabs named:mergeOptions.sidebar.hasValue];
        }
    }
    if (mergeOptions.customRow.hasValue) {
        [_row applyOptions:bottomTabs.customRow];
    }
    // The visibility at once, animated by `animate`.
    if (mergeOptions.visible.hasValue) {
        [tabs setBarHidden:![bottomTabs.visible withDefault:YES]
                  animated:[bottomTabs.animate withDefault:YES]];
    }
    if (mergeOptions.drawBehind.hasValue || mergeOptions.translucent.hasValue ||
        mergeOptions.visible.hasValue) {
        [tabs updateRootLayoutOfShownScreens];
    }
    if (mergeOptions.testID.hasValue || mergeOptions.moreTestID.hasValue) {
        [self applyButtonTestIDsWithRetries:ANNTestIDRetries];
    }
}

#pragma mark - The accessory (iOS 26)

// No name or `visible: false`: no accessory. The same id shown: nothing; another: a new one. A root of an id stays
// with the tabs once made, so one hidden and shown again keeps its state.
- (void)applyAccessoryOf:(ANNNavigationOptions *)options animated:(BOOL)animated {
    if (@available(iOS 26.0, *)) {
        ANNBottomTabsController *tabs = self.tabs;
        ANNBottomTabsAccessoryOptions *accessory = options.bottomTabs.bottomAccessory;
        NSString *name = [accessory.component.name withDefault:nil];
        NSString *componentId = [accessory.component.componentId withDefault:nil];
        BOOL visible = [accessory.visible withDefault:YES];
        BOOL inWindow = tabs.viewIfLoaded.window != nil;
        if (name.length == 0 || componentId.length == 0 || !visible) {
            if (tabs.bottomAccessory) {
                [tabs setBottomAccessory:nil animated:animated && inWindow];
            }
            _accessoryId = nil;
            return;
        }
        ANNBottomTabsAccessoryView *view = _accessoryViews[componentId];
        UIView<ANNComponentProtocol> *root =
            [tabs.components componentView:accessory.component
                                      type:ANNComponentTypeBottomTabsAccessory];
        // The module takes the core's React view only (a component of the options never has the slot prefix).
        if (![root isKindOfClass:ANNReactView.class]) {
            return;
        }
        if (view.reactView != root) {
            view = [[ANNBottomTabsAccessoryView alloc] initWithReactView:(ANNReactView *)root
                                                            eventEmitter:tabs.eventEmitter];
            _accessoryViews[componentId] = view;
        }
        NSString *testID = [accessory.testID withDefault:nil];
        view.accessibilityIdentifier = testID.length > 0 ? testID : nil;
        if ([_accessoryId isEqualToString:componentId] &&
            tabs.bottomAccessory.contentView == view) {
            return;
        }
        _accessoryId = componentId;
        UITabAccessory *tabAccessory = [[UITabAccessory alloc] initWithContentView:view];
        [tabs setBottomAccessory:tabAccessory animated:animated && inWindow];
    }
}

#pragma mark - The custom row and its cells

- (BOOL)customRowShown {
    return _row.isShown;
}

- (BOOL)customRowKeepsVisibility {
    return _row.keepsVisibility;
}

- (void)setCustomRowHidden:(BOOL)hidden {
    _row.hiddenByOptions = hidden;
}

// Each cell's root and its state: the tab's component (made once by its id, kept by the tabs), `selected` from the
// core's selection, the badge and the dot the tab holds.
- (void)updateCells {
    ANNBottomTabsController *tabs = self.tabs;
    if (!_row || !tabs) {
        return;
    }
    NSArray<ANNBottomTabCellView *> *cells = _row.cells;
    for (NSUInteger index = 0; index < cells.count && index < tabs.layoutTabs.count; index++) {
        ANNBottomTabCellView *cell = cells[index];
        ANNBottomTabOptions *bottomTab =
            [tabs.layoutTabs[index] resolveOptionsWithDefault].bottomTab;
        if (!cell.content) {
            cell.content = [tabs.components componentView:bottomTab.component
                                                     type:ANNComponentTypeBottomTabItem];
        }
        NSString *badge = ANNValue(_badges, index);
        ANNBottomTabDotIndicatorOptions *dot = _dots[index];
        BOOL dotShown = !badge && [dot.visible withDefault:NO];
        [cell setSelected:index == tabs.selectedTabIndex
                    badge:badge
                      dot:dotShown
                 dotColor:dot.color.hasValue ? [dot.color withDefault:nil] : nil];
        [cell setTestID:[bottomTab.testID withDefault:nil]];
    }
}

- (void)selectedTabChanged {
    [self updateCells];
}

- (void)tabsDidLayOut {
    if (!_tabsAreSet) {
        return;
    }
    [self updateHiddenTitles];
    if (!ANNTabsDesign.isGlass) {
        [self placeDots];
    }
    [self applyButtonTestIDsWithRetries:0];
    [_row layOutInTabs];
}

// The glass bar measures a title when it is set and keeps that size: a title set before the bar was ever in a window
// was measured in a button of its first layout there, when the bar's floating platter had no size yet, and stays cut
// short ("Sec…") and lower than a title measured later. Neither a layout of the bar nor of its buttons measures it
// again; setting the title does.
- (void)barDidLayOutInWindow {
    if (!_tabsAreSet || !ANNTabsDesign.isGlass) {
        return;
    }
    ANNBottomTabsController *tabs = self.tabs;
    BOOL tabAPI = NO;
    if (@available(iOS 18.0, *)) {
        tabAPI = tabs.usesTabAPI;
        for (NSUInteger index = 0; tabAPI && index < tabs.layoutTabs.count; index++) {
            UITab *tab = [tabs uiTabAtIndex:index];
            NSString *title = tab.title;
            if (title.length > 0) {
                tab.title = @"";
                tab.title = title;
            }
        }
    }
    // UIKit's own More item is in the bar too.
    NSMutableArray<UITabBarItem *> *items = [NSMutableArray new];
    if (!tabAPI) {
        [items addObjectsFromArray:tabs.tabBar.items];
    } else if (tabs.layoutTabs.count > 5) {
        [items addObject:tabs.moreNavigationController.tabBarItem];
    }
    for (UITabBarItem *item in items) {
        NSString *title = item.title;
        if (title.length > 0) {
            item.title = @"";
            item.title = title;
        }
    }
}

- (void)barVisibilityChanged {
    [_row layOutInTabs];
}

- (void)tabsWillGo {
    for (id view in _dotViews) {
        if (view != NSNull.null) {
            [(UIView *)view removeFromSuperview];
        }
    }
    [_row removeFromTabs];
    _row = nil;
}

@end

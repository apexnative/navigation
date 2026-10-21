#import "ANNBottomTabItemBuilder.h"

// The size of a tab's title without `fontSize` (`bottomTab.fontSize`).
static const CGFloat ANNTabTitleFontSize = 10;

// A raster in its own colors, or painted in `color` (the color goes into the raster: the glass of iOS 26 ignores the
// color of an unselected icon otherwise).
static UIImage *ANNRaster(UIImage *image, UIColor *_Nullable color) {
    if (color) {
        return [image imageWithTintColor:color renderingMode:UIImageRenderingModeAlwaysOriginal];
    }
    return [image imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
}

static UIImage *_Nullable ANNSymbol(Text *name) {
    NSString *symbol = [name withDefault:nil];
    return symbol.length > 0 ? [UIImage systemImageNamed:symbol] : nil;
}

static void ANNSetTitleAttributes(UITabBarItemStateAppearance *state, UIColor *color,
                                  UIFont *_Nullable font) {
    NSMutableDictionary<NSAttributedStringKey, id> *attributes =
        [state.titleTextAttributes mutableCopy] ?: [NSMutableDictionary new];
    attributes[NSForegroundColorAttributeName] = color;
    if (font) {
        attributes[NSFontAttributeName] = font;
    }
    state.titleTextAttributes = attributes;
}

@implementation ANNBottomTabItemBuilder

+ (NSString *)titleOf:(ANNBottomTabOptions *)tab {
    NSString *text = [tab.text withDefault:nil];
    return text.length > 0 ? text : nil;
}

+ (UIImage *)iconOf:(ANNBottomTabOptions *)tab {
    UIImage *symbol = ANNSymbol(tab.sfSymbol);
    if (symbol) {
        return symbol;
    }
    UIImage *icon = [tab.icon withDefault:nil];
    if (!icon) {
        return nil;
    }
    BOOL tints = ![tab.disableIconTint withDefault:NO];
    return ANNRaster(icon, tints ? [tab.iconColor withDefault:nil] : nil);
}

+ (UIImage *)selectedIconOf:(ANNBottomTabOptions *)tab {
    UIImage *symbol = ANNSymbol(tab.sfSelectedSymbol);
    if (symbol) {
        return symbol;
    }
    BOOL tints = ![tab.disableSelectedIconTint withDefault:NO];
    UIColor *color = tints ? [self selectedIconColorOf:tab] : nil;
    UIImage *selected = [tab.selectedIcon withDefault:nil];
    if (selected) {
        return ANNRaster(selected, color);
    }
    UIImage *iconSymbol = ANNSymbol(tab.sfSymbol);
    if (iconSymbol) {
        return iconSymbol;
    }
    UIImage *icon = [tab.icon withDefault:nil];
    return icon ? ANNRaster(icon, color) : nil;
}

+ (UIColor *)selectedIconColorOf:(ANNBottomTabOptions *)tab {
    return [tab.selectedIconColor withDefault:nil] ?: [tab.iconColor withDefault:nil];
}

+ (UITabBarAppearance *)appearanceOf:(ANNBottomTabOptions *)tab
                                base:(UITabBarAppearance *)base
                          badgeColor:(UIColor *)badgeColor {
    UITabBarAppearance *appearance = [base copy];
    UIColor *iconColor =
        [tab.disableIconTint withDefault:NO] ? nil : [tab.iconColor withDefault:nil];
    UIColor *selectedIconColor =
        [tab.disableSelectedIconTint withDefault:NO] ? nil : [self selectedIconColorOf:tab];
    UIColor *textColor = [tab.textColor withDefault:UIColor.labelColor] ?: UIColor.labelColor;
    UIColor *selectedTextColor =
        [tab.selectedTextColor withDefault:UIColor.labelColor] ?: UIColor.labelColor;
    UIFont *font = [self titleFontOf:tab];
    BOOL everyStateBadge = NO;
    if (@available(iOS 26.0, *)) {
        everyStateBadge = YES;
    }
    NSArray<UITabBarItemAppearance *> *layouts = @[
        appearance.stackedLayoutAppearance, appearance.inlineLayoutAppearance,
        appearance.compactInlineLayoutAppearance
    ];
    for (UITabBarItemAppearance *layout in layouts) {
        if (iconColor) {
            layout.normal.iconColor = iconColor;
        }
        if (selectedIconColor) {
            layout.selected.iconColor = selectedIconColor;
        }
        ANNSetTitleAttributes(layout.normal, textColor, font);
        ANNSetTitleAttributes(layout.selected, selectedTextColor, font);
        if (badgeColor && everyStateBadge) {
            layout.normal.badgeBackgroundColor = badgeColor;
            layout.selected.badgeBackgroundColor = badgeColor;
            layout.focused.badgeBackgroundColor = badgeColor;
            layout.disabled.badgeBackgroundColor = badgeColor;
        }
    }
    if (badgeColor && !everyStateBadge) {
        appearance.stackedLayoutAppearance.normal.badgeBackgroundColor = badgeColor;
    }
    return appearance;
}

+ (UIFont *)titleFontOf:(ANNBottomTabOptions *)tab {
    return [tab.font fontWithPlaceFont:[UIFont systemFontOfSize:ANNTabTitleFontSize]];
}

+ (UIEdgeInsets)iconInsetsOf:(ANNBottomTabOptions *)tab {
    ANNIconInsetsOptions *insets = tab.iconInsets;
    return UIEdgeInsetsMake([insets.top withDefault:0], [insets.left withDefault:0],
                            [insets.bottom withDefault:0], [insets.right withDefault:0]);
}

+ (BOOL)systemItemOfRole:(NSString *)role item:(UITabBarSystemItem *)systemItem {
    static NSDictionary<NSString *, NSNumber *> *items;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      items = @{
          @"more" : @(UITabBarSystemItemMore),
          @"favorites" : @(UITabBarSystemItemFavorites),
          @"featured" : @(UITabBarSystemItemFeatured),
          @"topRated" : @(UITabBarSystemItemTopRated),
          @"recents" : @(UITabBarSystemItemRecents),
          @"contacts" : @(UITabBarSystemItemContacts),
          @"history" : @(UITabBarSystemItemHistory),
          @"bookmarks" : @(UITabBarSystemItemBookmarks),
          @"search" : @(UITabBarSystemItemSearch),
          @"downloads" : @(UITabBarSystemItemDownloads),
          @"mostRecent" : @(UITabBarSystemItemMostRecent),
          @"mostViewed" : @(UITabBarSystemItemMostViewed),
      };
    });
    NSNumber *item = role ? items[role] : nil;
    if (!item) {
        return NO;
    }
    *systemItem = (UITabBarSystemItem)item.integerValue;
    return YES;
}

@end

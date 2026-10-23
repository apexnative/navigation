#import "ANNBottomTabOptions.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What a tab's item shows, from the tab's resolved `bottomTab` (`bottomTab.*`): pure functions the tabs' presenter
/// builds each item with, whole, at creation, at a `bottomTab` merge and at the repaint. The badge and the dot are the
/// presenter's (they hold across rebuilds).
@interface ANNBottomTabItemBuilder : NSObject

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// The text of the item (`bottomTab.text`); nil without one.
+ (nullable NSString *)titleOf:(ANNBottomTabOptions *)tab;

/// The icon (`bottomTab.icon`): the SF Symbol of `sfSymbol` over the raster, which shows in its own colors or painted
/// in `iconColor` (the color goes into the raster) unless `disableIconTint`; nil without one.
+ (nullable UIImage *)iconOf:(ANNBottomTabOptions *)tab;

/// The selected icon (`bottomTab.selectedIcon`): `sfSelectedSymbol` or `selectedIcon`, else the SF Symbol of `icon`
/// when there is one (not the raster under it), else the raster `icon`; a raster painted in `selectedIconColor`
/// (default `iconColor`) unless `disableSelectedIconTint`. Nil without any.
+ (nullable UIImage *)selectedIconOf:(ANNBottomTabOptions *)tab;

/// The images of the options `iconOf:` and `selectedIconOf:` draw from (`bottomTab.icon`, `bottomTab.selectedIcon`):
/// each one an SF Symbol doesn't cover. Their sources, not their images: nothing loads.
+ (NSArray<Image *> *)rastersOf:(ANNBottomTabOptions *)tab;

/// `selectedIconColor`, else `iconColor` (`bottomTab.selectedIconColor`); nil without both.
+ (nullable UIColor *)selectedIconColorOf:(ANNBottomTabOptions *)tab;

/// The look of the item: `base` (the bar's background and border) with the item's icon colors (so the system paints SF
/// Symbols in them), its title colors (`labelColor` without one) and font in the normal and selected states of every
/// layout, and `badgeColor` (before iOS 26: the stacked layout's normal state; on the glass of iOS 26: every layout and
/// state) when given.
+ (UITabBarAppearance *)appearanceOf:(ANNBottomTabOptions *)tab
                                base:(UITabBarAppearance *)base
                          badgeColor:(nullable UIColor *)badgeColor;

/// The title's font: `fontFamily`, `fontStyle`, `fontWeight`, `fontSize` (10 by default) through React Native's font
/// builder; nil when the tab sets none (the system's font).
+ (nullable UIFont *)titleFontOf:(ANNBottomTabOptions *)tab;

/// `iconInsets`, each side alone, a missing side 0.
+ (UIEdgeInsets)iconInsetsOf:(ANNBottomTabOptions *)tab;

/// Before iOS 18: the system item of `role` (`search`, `bookmarks`, … `more`); NO for an unknown one.
+ (BOOL)systemItemOfRole:(NSString *)role item:(UITabBarSystemItem *)systemItem;

@end

NS_ASSUME_NONNULL_END

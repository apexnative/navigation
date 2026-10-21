#import <UIKit/UIKit.h>

@interface UITabBar (utils)

/**
 * The button of an item in the tab bar. UIKit has no public API for it: this reads the private
 * `view` of the item, and is nil when UIKit has no such view (or the item none).
 */
- (UIView *)viewOfItem:(UITabBarItem *)item;

- (UIView *)tabBarItemViewAtIndex:(NSUInteger)index;

- (void)removeTabBarItemTitles;

/**
 * Icons in the middle of the buttons of this tab bar, for items without titles: before iOS 26 (and
 * in its compatibility mode) a button keeps the room of its title; the iOS 26 design centers the
 * icons itself, and this does nothing there. UIKit has no public API for it: the layout of the
 * private button class changes once for the app, and only tab bars with centered images move their
 * icons (and badges). `NO` gives the buttons the layout of UIKit back.
 */
- (void)setItemImagesCentered:(BOOL)centered;

@end

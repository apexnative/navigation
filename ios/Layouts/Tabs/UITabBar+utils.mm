#import "UITabBar+utils.h"
#import "UIView+Utils.h"
#import <objc/runtime.h>

static const void *ANNItemImagesCenteredKey = &ANNItemImagesCenteredKey;

typedef void (*ANNLayoutSubviewsIMP)(id, SEL);
static ANNLayoutSubviewsIMP ANNTabBarButtonLayoutSubviewsOriginal;

static UITabBar *ANNTabBarOfButton(UIView *button) {
    UIView *view = button.superview;
    while (view && ![view isKindOfClass:UITabBar.class]) {
        view = view.superview;
    }
    return (UITabBar *)view;
}

// -layoutSubviews of UITabBarButton. The button keeps the room of a title its item does not have,
// so in a tab bar with centered images the icon moves to the middle of the button after UIKit lays
// it out, and the badge moves with it.
static void ANNTabBarButtonLayoutSubviews(UIView *button, SEL _cmd) {
    ANNTabBarButtonLayoutSubviewsOriginal(button, _cmd);
    if (![objc_getAssociatedObject(ANNTabBarOfButton(button), ANNItemImagesCenteredKey)
            boolValue]) {
        return;
    }
    // With a translucent tab bar the icons of the tabs that are not selected are inside a vibrancy
    // view of the button.
    UIView *icon = [button findDescendantByClass:NSClassFromString(@"UITabBarSwappableImageView")];
    if (!icon) {
        return;
    }
    CGRect iconFrame = [icon.superview convertRect:icon.frame toView:button];
    CGFloat offset = CGRectGetMidY(button.bounds) - CGRectGetMidY(iconFrame);
    icon.center = CGPointMake(icon.center.x, icon.center.y + offset);
    UIView *badge = [button findDescendantByClass:NSClassFromString(@"_UIBadgeView")];
    badge.center = CGPointMake(badge.center.x, badge.center.y + offset);
}

// The design of iOS 26 centers the icon of an item without a title itself; in its compatibility
// mode (UIDesignRequiresCompatibility) the tab bar is drawn as before.
static BOOL ANNTabBarButtonsKeepTitleRoom(void) {
    if (@available(iOS 26.0, *)) {
        return [[NSBundle.mainBundle objectForInfoDictionaryKey:@"UIDesignRequiresCompatibility"]
            boolValue];
    }
    return YES;
}

// Once for the app; the buttons of other tab bars keep the layout of UIKit.
static void ANNInstallTabBarButtonLayout(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      Class buttonClass = NSClassFromString(@"UITabBarButton");
      Method method =
          buttonClass ? class_getInstanceMethod(buttonClass, @selector(layoutSubviews)) : NULL;
      if (!method) {
          return;
      }
      ANNTabBarButtonLayoutSubviewsOriginal =
          (ANNLayoutSubviewsIMP)method_getImplementation(method);
      // Added to the button class when it only inherits the method: other views keep theirs.
      class_replaceMethod(buttonClass, @selector(layoutSubviews),
                          (IMP)ANNTabBarButtonLayoutSubviews, method_getTypeEncoding(method));
    });
}

@implementation UITabBar (utils)

- (UIView *)viewOfItem:(UITabBarItem *)item {
    if (![item respondsToSelector:NSSelectorFromString(@"view")]) {
        return nil;
    }
    id view = [item valueForKey:@"view"];
    return [view isKindOfClass:UIView.class] ? view : nil;
}

- (UIView *)tabBarItemViewAtIndex:(NSUInteger)index {
    return index < self.items.count ? [self viewOfItem:self.items[index]] : nil;
}

- (void)removeTabBarItemTitles {
    for (UITabBarItem *item in self.items) {
        item.title = nil;
    }
}

- (void)setItemImagesCentered:(BOOL)centered {
    centered = centered && ANNTabBarButtonsKeepTitleRoom();
    if ([objc_getAssociatedObject(self, ANNItemImagesCenteredKey) boolValue] == centered) {
        return;
    }
    if (centered) {
        ANNInstallTabBarButtonLayout();
    }
    objc_setAssociatedObject(self, ANNItemImagesCenteredKey, @(centered),
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    for (UITabBarItem *item in self.items) {
        [[self viewOfItem:item] setNeedsLayout];
    }
}

@end

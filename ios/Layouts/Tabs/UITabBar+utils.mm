#import "UITabBar+utils.h"
#import "UIView+Utils.h"
#import <React/RCTLog.h>
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

// Once for the app; the buttons of other tab bars keep the layout of UIKit. No public API does this: in the
// compatibility mode of iOS 26 UIKit ignores `imageInsets` and a symbol's baseline offset, and a symbol drawn into a
// taller bitmap is scaled down and stops being a symbol. `badgePositionAdjustment` would move the badge alone, so the
// button's layout moves both.
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

// Once per app run, in DEBUG: the tab items' buttons can't be found any more.
static void ANNWarnNoButtonOfItem(void) {
#if DEBUG
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      RCTLogWarn(@"[ApexNavigation] This iOS version gives no button for a tab item: "
                 @"bottomTab.testID on the tab buttons, the dot before iOS 26 and the long "
                 @"press on a tab don't apply.");
    });
#endif
}

@implementation UITabBar (utils)

- (UIView *)viewOfItem:(UITabBarItem *)item {
    if (!item) {
        return nil;
    }
    // An item out of the bar (in More, or before the bar's first layout) has no button: that is no failure. Only an
    // item without the `view` key, or with something else under it, says that UIKit changed.
    if (![item respondsToSelector:NSSelectorFromString(@"view")]) {
        ANNWarnNoButtonOfItem();
        return nil;
    }
    id view = [item valueForKey:@"view"];
    if (view && ![view isKindOfClass:UIView.class]) {
        ANNWarnNoButtonOfItem();
        return nil;
    }
    return view;
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

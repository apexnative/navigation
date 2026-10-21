#import "ANNBackButtonPresenter.h"
#import "ANNExternalViewController.h"
#import "ANNIconButtonImage.h"
#import "ANNStackController.h"
#import <objc/runtime.h>

// The item below that the core configured: what it set may go again.
static const void *ANNBackButtonConfiguredKey = &ANNBackButtonConfiguredKey;

// The own back button: with `enableMenu: false` it takes no history menu (UIKit gives the menu to the back item).
@interface ANNBackBarButtonItem : UIBarButtonItem
@property(nonatomic) BOOL allowsMenu;
@end

@implementation ANNBackBarButtonItem

- (void)setMenu:(UIMenu *)menu {
    [super setMenu:self.allowsMenu ? menu : nil];
}

@end

@implementation ANNBackButtonPresenter

// The entries of the stack the screen is in, as they show (a pushed screen is there before UIKit's list has it).
+ (nullable NSArray<UIViewController *> *)entriesOf:(UIViewController *)screen
                                             entry:(UIViewController *_Nullable *_Nonnull)entry {
    UINavigationController *stack = screen.stack;
    if (![stack isKindOfClass:ANNStackController.class]) {
        *entry = nil;
        return nil;
    }
    ANNStackController *stackController = (ANNStackController *)stack;
    *entry = [stackController entryOf:screen];
    return stackController.queue.shown;
}

+ (nullable UIViewController *)entryBelow:(UIViewController *)screen {
    UIViewController *entry = nil;
    NSArray<UIViewController *> *entries = [self entriesOf:screen entry:&entry];
    NSUInteger index = entry ? [entries indexOfObjectIdenticalTo:entry] : NSNotFound;
    return index != NSNotFound && index > 0 ? entries[index - 1] : nil;
}

+ (BOOL)screenHasBackButton:(UIViewController *)screen options:(ANNNavigationOptions *)options {
    if (![options.topBar.backButton.visible withDefault:YES]) {
        return NO;
    }
    UIViewController *entry = nil;
    NSArray<UIViewController *> *entries = [self entriesOf:screen entry:&entry];
    NSUInteger index = entry ? [entries indexOfObjectIdenticalTo:entry] : NSNotFound;
    if (index == NSNotFound) {
        // A screen not in a list yet (its push waits): it shows above what the stack has.
        return entries.count > 0 && entry != nil;
    }
    return index > 0;
}

+ (void)applyVisibility:(ANNNavigationOptions *)options ofScreen:(UIViewController *)screen {
    Bool *visible = options.topBar.backButton.visible;
    // A native screen's item is the app's: its own `hidesBackButton` stays unless the options say.
    if (!visible.hasValue && [screen isKindOfClass:ANNExternalViewController.class]) {
        return;
    }
    BOOL hidden = ![visible withDefault:YES];
    if (screen.navigationItem.hidesBackButton != hidden) {
        screen.navigationItem.hidesBackButton = hidden;
    }
}

+ (void)applyOptions:(ANNNavigationOptions *)options ofScreen:(UIViewController *)screen {
    [self applyVisibility:options ofScreen:screen];
    UIViewController *below = [self entryBelow:screen];
    if (!below) {
        return;
    }
    UINavigationItem *item = below.navigationItem;
    BOOL configured = [objc_getAssociatedObject(item, ANNBackButtonConfiguredKey) boolValue];
    // A back button the screen below set itself (a native screen's own item) stays as it is.
    BOOL ownedByScreen =
        item.backButtonTitle != nil || item.backBarButtonItem != nil ||
        item.backButtonDisplayMode != UINavigationItemBackButtonDisplayModeDefault;
    if (!configured && ownedByScreen) {
        return;
    }
    ANNTopBarBackButtonOptions *backButton = options.topBar.backButton;
    NSString *title = [backButton.title withDefault:nil];
    NSString *belowTitle = below.navigationItem.title;
    UINavigationItemBackButtonDisplayMode mode = [self displayModeOf:backButton];
    if (backButton.showTitle.hasValue) {
        if (backButton.showTitle.get) {
            title = title ?: belowTitle;
        } else {
            mode = UINavigationItemBackButtonDisplayModeMinimal;
        }
    }
    BOOL ownButton = (backButton.enableMenu.hasValue && !backButton.enableMenu.get) ||
                     [self iconBackgroundColorOf:backButton] != nil;
    if (!title && mode == UINavigationItemBackButtonDisplayModeDefault && !ownButton) {
        if (configured) {
            item.backButtonTitle = nil;
            item.backBarButtonItem = nil;
            item.backButtonDisplayMode = UINavigationItemBackButtonDisplayModeDefault;
            objc_setAssociatedObject(item, ANNBackButtonConfiguredKey, nil,
                                     OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        }
        return;
    }
    objc_setAssociatedObject(item, ANNBackButtonConfiguredKey, @YES,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    item.backButtonTitle = title;
    item.backButtonDisplayMode = mode;
    item.backBarButtonItem =
        ownButton ? [self ownButtonOf:backButton title:title belowTitle:belowTitle] : nil;
}

+ (UINavigationItemBackButtonDisplayMode)displayModeOf:(ANNTopBarBackButtonOptions *)backButton {
    NSString *mode = [backButton.displayMode withDefault:nil];
    if ([mode isEqualToString:@"generic"]) {
        return UINavigationItemBackButtonDisplayModeGeneric;
    }
    if ([mode isEqualToString:@"minimal"]) {
        return UINavigationItemBackButtonDisplayModeMinimal;
    }
    return UINavigationItemBackButtonDisplayModeDefault;
}

+ (nullable UIColor *)iconBackgroundColorOf:(ANNTopBarBackButtonOptions *)backButton {
    Color *color = backButton.iconBackground.color;
    if (!color.hasValue || color.isNoColor) {
        return nil;
    }
    return [color withDefault:UIColor.clearColor];
}

// The icon (own, or `chevron.backward`) drawn on its background into an image; the history menu stays unless
// `enableMenu: false`; before iOS 26 the title of the screen below shows, as on the system button.
+ (UIBarButtonItem *)ownButtonOf:(ANNTopBarBackButtonOptions *)backButton
                           title:(nullable NSString *)title
                      belowTitle:(nullable NSString *)belowTitle {
    ANNBackBarButtonItem *item = [ANNBackBarButtonItem new];
    item.allowsMenu = [backButton.enableMenu withDefault:YES];
    item.style = UIBarButtonItemStylePlain;
    BOOL before26 = YES;
    if (@available(iOS 26.0, *)) {
        before26 = NO;
    }
    item.title = title ?: (before26 ? belowTitle : nil);
    UIColor *background = [self iconBackgroundColorOf:backButton];
    if (background) {
        NSString *symbol = [backButton.sfSymbol withDefault:nil];
        UIImage *icon = symbol.length > 0 ? [UIImage systemImageNamed:symbol] : nil;
        icon = icon ?: [backButton.icon withDefault:nil];
        icon = icon ?: [UIImage systemImageNamed:@"chevron.backward"];
        Color *color = backButton.color;
        UIColor *tint =
            color.hasValue && !color.isNoColor ? [color withDefault:UIColor.tintColor] : nil;
        ANNIconBackgroundOptions *iconBackground = backButton.iconBackground;
        item.image = [ANNIconButtonImage
            imageWithIcon:icon
                     tint:tint
               background:background
             cornerRadius:[iconBackground.cornerRadius withDefault:0]
                     size:CGSizeMake([iconBackground.width withDefault:0],
                                     [iconBackground.height withDefault:0])
                   insets:UIEdgeInsetsZero];
        if (@available(iOS 26.0, *)) {
            item.hidesSharedBackground = [backButton.hideSharedBackground withDefault:YES];
        }
    }
    return item;
}

+ (void)rebuildBackButtonOfScreen:(UIViewController *)screen {
    UIViewController *below = [self entryBelow:screen];
    if (!below || screen.transitionCoordinator) {
        return;
    }
    UINavigationItem *item = below.navigationItem;
    UINavigationItemBackButtonDisplayMode mode = item.backButtonDisplayMode;
    item.backButtonDisplayMode = mode == UINavigationItemBackButtonDisplayModeMinimal
                                     ? UINavigationItemBackButtonDisplayModeDefault
                                     : UINavigationItemBackButtonDisplayModeMinimal;
    item.backButtonDisplayMode = mode;
}

@end

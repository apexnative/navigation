#import "ANNPopoverPresenter.h"
#import "ANNUIBarButtonItem.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>
#import <React/RCTViewComponentView.h>
#import <objc/runtime.h>

// The delegate of a popover that stays a popover: UIKit asks the delegate for the adaptive style of
// every presentation (asking the presentation controller for it asks the delegate again), so only
// these popovers get it.
@interface ANNNonAdaptivePopoverDelegate : NSObject <UIPopoverPresentationControllerDelegate>
@property(nonatomic, weak) id<UIAdaptivePresentationControllerDelegate> delegate;
@end

@implementation ANNNonAdaptivePopoverDelegate

- (UIModalPresentationStyle)
    adaptivePresentationStyleForPresentationController:(UIPresentationController *)controller
                                       traitCollection:(UITraitCollection *)traitCollection {
    return UIModalPresentationNone;
}

- (BOOL)respondsToSelector:(SEL)selector {
    return [super respondsToSelector:selector] || [self.delegate respondsToSelector:selector];
}

- (id)forwardingTargetForSelector:(SEL)selector {
    return [self.delegate respondsToSelector:selector] ? self.delegate : nil;
}

@end

static const void *ANNNonAdaptivePopoverDelegateKey = &ANNNonAdaptivePopoverDelegateKey;

@implementation ANNPopoverPresenter

+ (void)keepPopoverOf:(UIViewController *)modal
             delegate:(id<UIAdaptivePresentationControllerDelegate>)delegate {
    ANNNonAdaptivePopoverDelegate *popoverDelegate = [ANNNonAdaptivePopoverDelegate new];
    popoverDelegate.delegate = delegate;
    // The presentation controller holds its delegate weakly.
    objc_setAssociatedObject(modal, ANNNonAdaptivePopoverDelegateKey, popoverDelegate,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    modal.popoverPresentationController.delegate = popoverDelegate;
}

+ (void)anchorPopover:(UIPopoverPresentationController *)popover
              options:(ANNPopoverOptions *)options
           presenting:(UIViewController *)presenting {
    if (!popover) {
        return;
    }
    NSString *buttonId = [options.sourceButtonId withDefault:nil];
    NSString *nativeID = [options.sourceNativeID withDefault:nil];
    UIBarButtonItem *button = buttonId ? [self barButtonWithId:buttonId in:presenting] : nil;
    UIView *view = !button && nativeID ? [self viewWithNativeID:nativeID in:presenting.view] : nil;
    if (button) {
        if (@available(iOS 16.0, *)) {
            popover.sourceItem = button;
        } else {
            popover.barButtonItem = button;
        }
    } else if (view) {
        popover.sourceView = view;
        popover.sourceRect = view.bounds;
    } else if (![self hasAnchor:popover]) {
        if (buttonId || nativeID) {
            RCTLogWarn(@"[ApexNavigation] modal.popover: no source %@ on the presenting screen; the popover "
                       @"stands in the middle.",
                       buttonId ?: nativeID);
        }
        UIView *screen = presenting.view;
        popover.sourceView = screen;
        popover.sourceRect =
            CGRectMake(CGRectGetMidX(screen.bounds), CGRectGetMidY(screen.bounds), 0, 0);
        popover.permittedArrowDirections = 0;
        return;
    }
    popover.permittedArrowDirections = [self arrowDirections:options.arrowDirections];
}

+ (BOOL)hasAnchor:(UIPopoverPresentationController *)popover {
    if (@available(iOS 16.0, *)) {
        if (popover.sourceItem) {
            return YES;
        }
    }
    return popover.sourceView != nil || popover.barButtonItem != nil;
}

+ (UIPopoverArrowDirection)arrowDirections:(NSArray *)directions {
    if (directions.count == 0) {
        return UIPopoverArrowDirectionAny;
    }
    NSDictionary<NSString *, NSNumber *> *map = @{
        @"up" : @(UIPopoverArrowDirectionUp),
        @"down" : @(UIPopoverArrowDirectionDown),
        @"left" : @(UIPopoverArrowDirectionLeft),
        @"right" : @(UIPopoverArrowDirectionRight),
    };
    UIPopoverArrowDirection mask = 0;
    for (id direction in directions) {
        mask |= [map[direction] unsignedIntegerValue];
    }
    return mask ?: UIPopoverArrowDirectionAny;
}

// In the screens on screen: in a split view also the secondary column, not the primary one alone.
+ (UIBarButtonItem *)barButtonWithId:(NSString *)buttonId in:(UIViewController *)layout {
    for (UIViewController *screen in layout.shownComponentViewControllers) {
        UIBarButtonItem *button = [self barButtonWithId:buttonId inScreen:screen];
        if (button) {
            return button;
        }
    }
    return nil;
}

+ (UIBarButtonItem *)barButtonWithId:(NSString *)buttonId inScreen:(UIViewController *)screen {
    UINavigationItem *navigationItem = screen.navigationItem;
    NSMutableArray<UIBarButtonItem *> *items = [NSMutableArray new];
    [items addObjectsFromArray:navigationItem.leftBarButtonItems ?: @[]];
    [items addObjectsFromArray:navigationItem.rightBarButtonItems ?: @[]];
    [items addObjectsFromArray:screen.toolbarItems ?: @[]];
    if (@available(iOS 16.0, *)) {
        for (NSArray<UIBarButtonItemGroup *> *groups in @[
                 navigationItem.leadingItemGroups, navigationItem.trailingItemGroups,
                 navigationItem.centerItemGroups
             ]) {
            for (UIBarButtonItemGroup *group in groups) {
                [items addObjectsFromArray:group.barButtonItems];
            }
        }
    }
    for (UIBarButtonItem *item in items) {
        if ([item isKindOfClass:ANNUIBarButtonItem.class] &&
            [((ANNUIBarButtonItem *)item).buttonId isEqualToString:buttonId]) {
            return item;
        }
    }
    return nil;
}

+ (UIView *)viewWithNativeID:(NSString *)nativeID in:(UIView *)root {
    if ([root isKindOfClass:RCTViewComponentView.class] &&
        [((RCTViewComponentView *)root).nativeId isEqualToString:nativeID]) {
        return root;
    }
    for (UIView *subview in root.subviews) {
        UIView *found = [self viewWithNativeID:nativeID in:subview];
        if (found) {
            return found;
        }
    }
    return nil;
}

@end

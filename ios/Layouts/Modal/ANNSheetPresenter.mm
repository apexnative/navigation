#import "ANNSheetPresenter.h"
#import "ANNModalSizePresenter.h"
#import "ANNReactView.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>
#import <objc/runtime.h>

// Set on a sheet that follows the height of its content.
static const void *ANNSheetFollowsContentKey = &ANNSheetFollowsContentKey;

// Identifiers of the custom detents: the prefix and the height in points, or `content`.
static NSString *const ANNSheetDetentPrefix = @"rnn.sheet.";
static NSString *const ANNContentDetent = @"content";

@implementation ANNSheetPresenter

+ (void)applyOptions:(ANNSheetOptions *)options toModal:(UIViewController *)modal {
    UISheetPresentationController *sheet = [self sheetOfModal:modal];
    if (sheet && options.hasValue) {
        [self applyOptions:options toSheet:sheet];
    }
}

+ (void)mergeOptions:(ANNSheetOptions *)options
    ofViewController:(UIViewController *)viewController {
    if (!options.hasValue) {
        return;
    }
    UIViewController *modal = viewController;
    while (modal.parentViewController) {
        modal = modal.parentViewController;
    }
    if (!modal.presentingViewController) {
        return;
    }
    UISheetPresentationController *sheet = [self sheetOfModal:modal];
    [sheet animateChanges:^{
      [self applyOptions:options toSheet:sheet];
    }];
}

// Only page and form sheets have one (and the automatic style, which is a page sheet); asking for
// it makes a presentation controller for the style the view controller has.
+ (UISheetPresentationController *)sheetOfModal:(UIViewController *)modal {
    UIModalPresentationStyle style = modal.modalPresentationStyle;
    if (style != UIModalPresentationPageSheet && style != UIModalPresentationFormSheet &&
        style != UIModalPresentationAutomatic) {
        return nil;
    }
    return modal.sheetPresentationController;
}

+ (void)applyOptions:(ANNSheetOptions *)options toSheet:(UISheetPresentationController *)sheet {
    if (options.detents) {
        NSMutableArray<UISheetPresentationControllerDetent *> *detents = [NSMutableArray new];
        for (id value in options.detents) {
            UISheetPresentationControllerDetent *detent = [self detentFor:value ofSheet:sheet];
            if (detent) {
                [detents addObject:detent];
            }
        }
        sheet.detents =
            detents.count ? detents : @[ UISheetPresentationControllerDetent.largeDetent ];
        [self followContentOfSheet:sheet when:[options.detents containsObject:ANNContentDetent]];
    }
    if (options.selectedDetent.hasValue) {
        sheet.selectedDetentIdentifier = [self identifierFor:options.selectedDetent.get];
    }
    if (options.largestUndimmedDetent.hasValue) {
        sheet.largestUndimmedDetentIdentifier =
            [self identifierFor:options.largestUndimmedDetent.get];
    }
    if (options.grabber.hasValue) {
        sheet.prefersGrabberVisible = options.grabber.get;
    }
    if (options.expandsOnScroll.hasValue) {
        sheet.prefersScrollingExpandsWhenScrolledToEdge = options.expandsOnScroll.get;
    }
    if (options.cornerRadius.hasValue) {
        sheet.preferredCornerRadius = options.cornerRadius.get;
    }
}

+ (UISheetPresentationControllerDetent *)detentFor:(id)value
                                           ofSheet:(UISheetPresentationController *)sheet {
    if ([value isEqual:@"medium"]) {
        return UISheetPresentationControllerDetent.mediumDetent;
    }
    if ([value isEqual:@"large"]) {
        return UISheetPresentationControllerDetent.largeDetent;
    }
    if ([value isKindOfClass:NSNumber.class]) {
        if (@available(iOS 16.0, *)) {
            CGFloat height = [value doubleValue];
            return [UISheetPresentationControllerDetent
                customDetentWithIdentifier:[self identifierFor:value]
                                  resolver:^CGFloat(
                                      id<UISheetPresentationControllerDetentResolutionContext>
                                          context) {
                                    return MIN(height, context.maximumDetentValue);
                                  }];
        }
        RCTLogWarn(@"[ApexNavigation] modal.sheet: a detent height needs iOS 16; %@ is left out.", value);
        return nil;
    }
    if ([value isEqual:ANNContentDetent]) {
        if (@available(iOS 16.0, *)) {
            __weak UISheetPresentationController *weakSheet = sheet;
            return [UISheetPresentationControllerDetent
                customDetentWithIdentifier:[self identifierFor:value]
                                  resolver:^CGFloat(
                                      id<UISheetPresentationControllerDetentResolutionContext>
                                          context) {
                                    CGFloat height =
                                        [self contentHeightOf:weakSheet.presentedViewController];
                                    return height > 0 ? MIN(height, context.maximumDetentValue)
                                                      : context.maximumDetentValue;
                                  }];
        }
        RCTLogWarn(@"[ApexNavigation] modal.sheet: the 'content' detent needs iOS 16; it is left out.");
        return nil;
    }
    RCTLogWarn(@"[ApexNavigation] modal.sheet: unknown detent %@.", value);
    return nil;
}

// The height of the screen the modal shows: the space above its React content (a top bar) and the
// content laid out without a height. UIKit adds the bottom safe area to a detent.
+ (CGFloat)contentHeightOf:(UIViewController *)modal {
    return [self contentHeightOf:modal width:modal.view.bounds.size.width];
}

+ (CGFloat)contentHeightOf:(UIViewController *)modal width:(CGFloat)width {
    UIViewController *screen = modal.presentedComponentViewController;
    UIView<ANNComponentProtocol> *reactView = screen.reactView;
    if (!reactView || width <= 0) {
        return 0;
    }
    CGSize content = CGSizeZero;
    if (reactView.surface) {
        content = [reactView.surface sizeThatFitsMinimumSize:CGSizeMake(width, 0)
                                                 maximumSize:CGSizeMake(width, CGFLOAT_MAX)];
    } else if ([reactView respondsToSelector:@selector(contentHeightAtWidth:)]) {
        // One tree: the slot measures its content itself; nothing measured yet is no height, and the detent stands at
        // its maximum.
        content.height = [reactView contentHeightAtWidth:width];
        if (content.height <= 0) {
            return 0;
        }
    }
    CGFloat top = 0;
    if (reactView.window) {
        top = MAX(0, [reactView convertPoint:CGPointZero toView:modal.view].y);
    } else if (screen.navigationController && !screen.navigationController.navigationBarHidden) {
        top = CGRectGetMaxY(screen.navigationController.navigationBar.frame);
    }
    return top + content.height;
}

// The sheet follows the content: Fabric reports no size changes, so the content is measured after
// it mounts and the detents are resolved again when its height changes.
+ (void)followContentOfSheet:(UISheetPresentationController *)sheet when:(BOOL)follow {
    objc_setAssociatedObject(sheet, ANNSheetFollowsContentKey, follow ? @YES : nil,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    UIView<ANNComponentProtocol> *reactView =
        sheet.presentedViewController.presentedComponentViewController.reactView;
    if (!follow) {
        [reactView setContentDidMount:nil forKey:ANNContentDetent];
        return;
    }
    if (@available(iOS 16.0, *)) {
        __weak UISheetPresentationController *weakSheet = sheet;
        __block CGFloat height = [self contentHeightOf:sheet.presentedViewController];
        [reactView
            setContentDidMount:^{
              UISheetPresentationController *strongSheet = weakSheet;
              CGFloat newHeight = [self contentHeightOf:strongSheet.presentedViewController];
              if (!strongSheet || fabs(newHeight - height) < 0.5) {
                  return;
              }
              height = newHeight;
              [strongSheet animateChanges:^{
                [strongSheet invalidateDetents];
              }];
            }
                        forKey:ANNContentDetent];
    }
}

+ (void)stackDidShowScreenInModal:(UINavigationController *)stack {
    UIViewController *modal = stack;
    while (modal.parentViewController) {
        modal = modal.parentViewController;
    }
    if (!modal.presentingViewController) {
        return;
    }
    if (@available(iOS 16.0, *)) {
        UISheetPresentationController *sheet = [self sheetOfModal:modal];
        if (sheet && objc_getAssociatedObject(sheet, ANNSheetFollowsContentKey)) {
            // The new screen's content mounts may come later: its mounts resolve the detents again too.
            [self followContentOfSheet:sheet when:YES];
            [sheet animateChanges:^{
              [sheet invalidateDetents];
            }];
        }
    }
    [ANNModalSizePresenter applyOptions:modal.resolveOptionsWithDefault.modal toModal:modal];
}

// nil (no detent) for null, as when `largestUndimmedDetent: null` dims the screen below again.
+ (UISheetPresentationControllerDetentIdentifier)identifierFor:(id)value {
    if ([value isEqual:@"medium"]) {
        return UISheetPresentationControllerDetentIdentifierMedium;
    }
    if ([value isEqual:@"large"]) {
        return UISheetPresentationControllerDetentIdentifierLarge;
    }
    if ([value isKindOfClass:NSNumber.class]) {
        return [ANNSheetDetentPrefix stringByAppendingString:[value stringValue]];
    }
    if ([value isKindOfClass:NSString.class]) {
        return [ANNSheetDetentPrefix stringByAppendingString:value];
    }
    return nil;
}

+ (id)detentForIdentifier:(UISheetPresentationControllerDetentIdentifier)identifier {
    if ([identifier isEqualToString:UISheetPresentationControllerDetentIdentifierMedium]) {
        return @"medium";
    }
    if ([identifier isEqualToString:UISheetPresentationControllerDetentIdentifierLarge]) {
        return @"large";
    }
    if (![identifier hasPrefix:ANNSheetDetentPrefix]) {
        return identifier;
    }
    NSString *value = [identifier substringFromIndex:ANNSheetDetentPrefix.length];
    NSScanner *scanner = [NSScanner scannerWithString:value];
    double height;
    if ([scanner scanDouble:&height] && scanner.isAtEnd) {
        return @(height);
    }
    return value;
}

@end

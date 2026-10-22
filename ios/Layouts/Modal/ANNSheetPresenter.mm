#import "ANNSheetPresenter.h"
#import "ANNModalSizePresenter.h"
#import "ANNReactView.h"
#import "ANNUtils.h"
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
        for (id value in [self sortedDetents:options.detents ofSheet:sheet]) {
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

// UIKit wants the detents once each, from the lowest to the highest: it takes the last for the largest and closes the
// sheet on a drag down from the first. The options may give them in any order and more than once; the first of a
// repeat stays, and detents of the same height keep their order.
+ (NSArray *)sortedDetents:(NSArray *)values ofSheet:(UISheetPresentationController *)sheet {
    NSMutableArray *detents = [NSMutableArray new];
    NSMutableSet<NSString *> *identifiers = [NSMutableSet new];
    for (id value in values) {
        NSString *identifier = [self identifierFor:value];
        if (identifier && ![identifiers containsObject:identifier]) {
            [identifiers addObject:identifier];
            [detents addObject:value];
        }
    }
    CGFloat maximum = [self maximumDetentHeightOf:sheet];
    return [detents sortedArrayWithOptions:NSSortStable
                           usingComparator:^NSComparisonResult(id a, id b) {
                             return [self compareHeightOf:a
                                                     with:b
                                                  maximum:maximum
                                                  ofSheet:sheet];
                           }];
}

// The content of a sheet sized to it changed its height, and with it maybe its place among the other detents.
+ (void)sortDetentsOfSheet:(UISheetPresentationController *)sheet API_AVAILABLE(ios(16.0)) {
    CGFloat maximum = [self maximumDetentHeightOf:sheet];
    NSArray<UISheetPresentationControllerDetent *> *detents = sheet.detents;
    NSArray<UISheetPresentationControllerDetent *> *sorted = [detents
        sortedArrayWithOptions:NSSortStable
               usingComparator:^NSComparisonResult(UISheetPresentationControllerDetent *a,
                                                   UISheetPresentationControllerDetent *b) {
                 return [self compareHeightOf:[self detentForIdentifier:a.identifier]
                                         with:[self detentForIdentifier:b.identifier]
                                      maximum:maximum
                                      ofSheet:sheet];
               }];
    if (![sorted isEqualToArray:detents]) {
        sheet.detents = sorted;
    }
}

+ (NSComparisonResult)compareHeightOf:(id)a
                                 with:(id)b
                              maximum:(CGFloat)maximum
                              ofSheet:(UISheetPresentationController *)sheet {
    CGFloat heightA = [self heightOf:a maximum:maximum ofSheet:sheet];
    CGFloat heightB = [self heightOf:b maximum:maximum ofSheet:sheet];
    return heightA < heightB ? NSOrderedAscending
                             : heightA > heightB ? NSOrderedDescending : NSOrderedSame;
}

// The height a detent of the options stands at, to sort them, as its resolver gives it: 'medium' half of the largest
// height (UIKit's is about half), 'large' all of it, a height capped at it, 'content' the content's height now, all of
// it while the content isn't measured.
+ (CGFloat)heightOf:(id)value
            maximum:(CGFloat)maximum
            ofSheet:(UISheetPresentationController *)sheet {
    if ([value isEqual:@"medium"]) {
        return maximum / 2;
    }
    if ([value isKindOfClass:NSNumber.class]) {
        return MIN([value doubleValue], maximum);
    }
    if ([value isEqual:ANNContentDetent]) {
        CGFloat height = [self contentHeightOf:sheet.presentedViewController];
        return height > 0 ? MIN(height, maximum) : maximum;
    }
    return maximum;
}

// About the largest height of a sheet: its container, or the window before it is shown, below the top safe area.
+ (CGFloat)maximumDetentHeightOf:(UISheetPresentationController *)sheet {
    UIView *view = sheet.containerView ?: ANNUtils.mainWindow;
    if (!view) {
        return ANNUtils.mainScreen.bounds.size.height;
    }
    return view.bounds.size.height - view.safeAreaInsets.top;
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
                [self sortDetentsOfSheet:strongSheet];
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
              [self sortDetentsOfSheet:sheet];
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

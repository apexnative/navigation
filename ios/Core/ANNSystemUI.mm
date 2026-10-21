#import "ANNSystemUI.h"
#import "ANNAssert.h"
#import "ANNComponentViewController.h"
#import "ANNDefaultOptions.h"
#import "ANNOverlayCoordinator.h"
#import "ANNRootCoordinator.h"
#import "ANNSystemUIRules.h"
#import "ANNUtils.h"
#import "UIViewController+LayoutProtocol.h"

// `statusBar.animate`: a change of the style fades over this.
static const NSTimeInterval ANNStatusBarStyleDuration = 0.35;

// How deep a walk of controllers goes at most: a chain that loops ends there.
static const NSUInteger ANNWalkLimit = 64;

// The status bar of a window after the owner's last ask: only to choose whether a change of the style animates.
@interface ANNSystemUIStatusBarRecord : NSObject
@property(nonatomic) UIStatusBarStyle style;
@property(nonatomic) BOOL hidden;
@end

@implementation ANNSystemUIStatusBarRecord
@end

// Main queue.
static ANNSystemUIGroup ANNMarkedGroups;
static BOOL ANNUpdateScheduled;
static NSMapTable<UIWindow *, ANNSystemUIStatusBarRecord *> *ANNStatusBarRecords;

// The presentation style UIKit uses for `presented` now (F12): its presentation controller's (an automatic one is a
// sheet there), and for a popover in a compact width the style UIKit adapts it to — full screen, unless its delegate
// keeps it a popover (a kept popover answers none).
static UIModalPresentationStyle ANNUsedPresentationStyle(UIViewController *presented) {
    UIPresentationController *presentation = presented.presentationController;
    if (!presentation || presentation.presentedViewController != presented) {
        return presented.modalPresentationStyle;
    }
    UIModalPresentationStyle style = presentation.presentationStyle;
    UITraitCollection *traits = presentation.traitCollection;
    if (style == UIModalPresentationPopover &&
        traits.horizontalSizeClass == UIUserInterfaceSizeClassCompact) {
        UIModalPresentationStyle adapted =
            [presentation adaptivePresentationStyleForTraitCollection:traits];
        if (adapted != UIModalPresentationNone) {
            style = adapted;
        }
    }
    return style;
}

// UIKit hands the status bar, the home indicator, the deferred edges and the vertical bars to a presented controller
// shown full screen, and the status bar also to one that captures it; the orientations to one that covers the screen
// (F4).
static BOOL ANNPresentedTakes(UIViewController *presented, ANNSystemUIQuestion question) {
    UIModalPresentationStyle style = ANNUsedPresentationStyle(presented);
    if (question == ANNSystemUIQuestionOrientations) {
        return style == UIModalPresentationFullScreen ||
               style == UIModalPresentationOverFullScreen;
    }
    BOOL statusBar = question == ANNSystemUIQuestionStatusBarStyle ||
                     question == ANNSystemUIQuestionStatusBarHidden;
    if (statusBar && presented.modalPresentationCapturesStatusBarAppearance) {
        return YES;
    }
    return style == UIModalPresentationFullScreen;
}

// The child `controller` hands `question` to, as UIKit asks it.
static UIViewController *ANNChildFor(UIViewController *controller, ANNSystemUIQuestion question) {
    switch (question) {
        case ANNSystemUIQuestionStatusBarStyle:
            return controller.childViewControllerForStatusBarStyle;
        case ANNSystemUIQuestionStatusBarHidden:
            return controller.childViewControllerForStatusBarHidden;
        case ANNSystemUIQuestionHomeIndicator:
            return controller.childViewControllerForHomeIndicatorAutoHidden;
        case ANNSystemUIQuestionDeferredEdges:
            return controller.childViewControllerForScreenEdgesDeferringSystemGestures;
        case ANNSystemUIQuestionVerticalBars:
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
            if (@available(iOS 27.1, *)) {
                return controller.childViewControllerForPreferredVerticalBarBehavior;
            }
#endif
            return nil;
        case ANNSystemUIQuestionOrientations:
            return nil;
    }
    return nil;
}

// The window's root and its presented chain, to the first nil.
static NSArray<UIViewController *> *ANNChainOf(UIWindow *window) {
    UIViewController *root = window.rootViewController;
    if (!root) {
        return @[];
    }
    NSMutableArray<UIViewController *> *chain = [NSMutableArray arrayWithObject:root];
    for (UIViewController *presented = root.presentedViewController;
         presented && chain.count < ANNWalkLimit; presented = presented.presentedViewController) {
        [chain addObject:presented];
    }
    return chain;
}

@implementation ANNSystemUI

#pragma mark - Triggers

+ (void)setNeedsUpdate {
    [self setNeedsUpdateOfGroups:ANNSystemUIGroupAll];
}

+ (void)setNeedsUpdateOfGroups:(ANNSystemUIGroup)groups {
    ANNAssertMainQueue();
    if (groups == ANNSystemUIGroupNone) {
        return;
    }
#if DEBUG
    ANNSystemUIRulesSelfCheck();
#endif
    ANNMarkedGroups |= groups;
    if (ANNUpdateScheduled) {
        return;
    }
    // The end of this turn of the main queue: every trigger of the turn is in, nothing waits for it.
    ANNUpdateScheduled = YES;
    dispatch_async(dispatch_get_main_queue(), ^{
      [ANNSystemUI update];
    });
}

+ (void)optionsMerged:(ANNNavigationOptions *)options {
    [self setNeedsUpdateOfGroups:[self groupsNamedBy:options onlyRead:NO]];
}

+ (ANNSystemUIGroup)groupsNamedBy:(ANNNavigationOptions *)options onlyRead:(BOOL)onlyRead {
    ANNSystemUIGroup groups = ANNSystemUIGroupNone;
    ANNStatusBarOptions *statusBar = options.statusBar;
    BOOL namesStatusBar = onlyRead ? statusBar.visible.hasValue || statusBar.style.hasValue ||
                                         statusBar.hideWithTopBar.hasValue
                                   : statusBar.hasValue;
    if (namesStatusBar) {
        groups |= ANNSystemUIGroupStatusBar;
    }
    ANNLayoutOptions *layout = options.layout;
    if (layout.orientation) {
        groups |= ANNSystemUIGroupOrientation;
    }
    if (layout.autoHideHomeIndicator.hasValue) {
        groups |= ANNSystemUIGroupHomeIndicator;
    }
    if (layout.deferSystemGestures) {
        groups |= ANNSystemUIGroupDeferredEdges;
    }
    if (layout.verticalBars.hasValue) {
        groups |= ANNSystemUIGroupVerticalBars;
    }
    return groups;
}

#pragma mark - The update

// The marked groups of the turn, asked in the main window and in every overlay window: an overlay's screens may
// answer what the main window's deciding controller answers, and UIKit may ask either window (F1). While the main
// window's root plays its own animation, its transform is on the root's view: the orientation waits for its end, which
// marks it again (the rest is asked now).
+ (void)update {
    ANNUpdateScheduled = NO;
    ANNSystemUIGroup groups = ANNMarkedGroups;
    ANNMarkedGroups = ANNSystemUIGroupNone;
    if ((groups & ANNSystemUIGroupOrientation) && [ANNRootCoordinator rootAnimationRuns]) {
        groups &= ~ANNSystemUIGroupOrientation;
        ANNMarkedGroups |= ANNSystemUIGroupOrientation;
    }
    if (groups == ANNSystemUIGroupNone) {
        return;
    }
    NSMutableArray<UIWindow *> *windows = [NSMutableArray new];
    UIWindow *mainWindow = ANNUtils.mainWindow;
    if (mainWindow) {
        [windows addObject:mainWindow];
    }
    [windows addObjectsFromArray:[ANNOverlayCoordinator overlayWindows]];
    if (groups & ANNSystemUIGroupStatusBar) {
        [self askStatusBarInWindows:windows];
    }
    for (UIWindow *window in windows) {
        [self askGroups:groups inWindow:window];
    }
    // iOS 15 turns toward the device only when asked to, if the device's orientation is allowed now.
    if (groups & ANNSystemUIGroupOrientation) {
        if (@available(iOS 16.0, *)) {
        } else {
            [UIViewController attemptRotationToDeviceOrientation];
        }
    }
}

// UIKit compares the values itself: every controller of the window's chain is asked, the root first.
+ (void)askGroups:(ANNSystemUIGroup)groups inWindow:(UIWindow *)window {
    NSArray<UIViewController *> *chain = ANNChainOf(window);
    if (chain.count == 0) {
        return;
    }
    if (groups & ANNSystemUIGroupOrientation) {
        // UIKit turns the interface itself when its orientation is no longer allowed; animated.
        if (@available(iOS 16.0, *)) {
            [chain.firstObject setNeedsUpdateOfSupportedInterfaceOrientations];
            if (chain.count > 1) {
                [chain.lastObject setNeedsUpdateOfSupportedInterfaceOrientations];
            }
        }
    }
    for (UIViewController *controller in chain) {
        if (groups & ANNSystemUIGroupHomeIndicator) {
            [controller setNeedsUpdateOfHomeIndicatorAutoHidden];
        }
        if (groups & ANNSystemUIGroupDeferredEdges) {
            [controller setNeedsUpdateOfScreenEdgesDeferringSystemGestures];
        }
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
        if (groups & ANNSystemUIGroupVerticalBars) {
            if (@available(iOS 27.1, *)) {
                [controller setNeedsUpdateOfVerticalBarConfiguration];
            }
        }
#endif
    }
}

// A change of the style alone fades over 0.35 s unless the deciding screen's `statusBar.animate` is `false`; a change
// of the visibility never animates, with or without the style: the safe area changes with it, and the screens'
// content would move along (`statusBar.animate`). UIKit shows one status bar for the scene, from whichever window it
// takes (F1): when the visibility changed in any window, no window is asked inside an animation.
+ (void)askStatusBarInWindows:(NSArray<UIWindow *> *)windows {
    if (!ANNStatusBarRecords) {
        ANNStatusBarRecords = [NSMapTable weakToStrongObjectsMapTable];
    }
    NSMutableArray<NSArray<UIViewController *> *> *chains = [NSMutableArray new];
    NSMutableArray<NSNumber *> *styleFades = [NSMutableArray new];
    BOOL hiddenChanged = NO;
    for (UIWindow *window in windows) {
        NSArray<UIViewController *> *chain = ANNChainOf(window);
        if (chain.count == 0) {
            continue;
        }
        UIViewController *styleDecider =
            [self deciderInWindow:window question:ANNSystemUIQuestionStatusBarStyle];
        UIViewController *hiddenDecider =
            [self deciderInWindow:window question:ANNSystemUIQuestionStatusBarHidden];
        UIStatusBarStyle style =
            styleDecider ? styleDecider.preferredStatusBarStyle : UIStatusBarStyleDefault;
        BOOL hidden = hiddenDecider.prefersStatusBarHidden;
        ANNSystemUIStatusBarRecord *record = [ANNStatusBarRecords objectForKey:window];
        BOOL fades = record && record.style != style && [self statusBarAnimatesFor:styleDecider];
        if (record && record.hidden != hidden) {
            hiddenChanged = YES;
        }
        if (!record) {
            record = [ANNSystemUIStatusBarRecord new];
            [ANNStatusBarRecords setObject:record forKey:window];
        }
        record.style = style;
        record.hidden = hidden;
        [chains addObject:chain];
        [styleFades addObject:@(fades)];
    }
    for (NSUInteger index = 0; index < chains.count; index++) {
        NSArray<UIViewController *> *chain = chains[index];
        void (^ask)(void) = ^{
          for (UIViewController *controller in chain) {
              [controller setNeedsStatusBarAppearanceUpdate];
          }
        };
        if (!hiddenChanged && styleFades[index].boolValue) {
            [UIView animateWithDuration:ANNStatusBarStyleDuration animations:ask];
        } else {
            ask();
        }
    }
}

// A controller that is no node of the layout (React Native's `<Modal>`, an alert, the splash) animates as UIKit's
// default; a node by its options, as it answers.
+ (BOOL)statusBarAnimatesFor:(UIViewController *)decider {
    if (!decider.isLayoutNode) {
        return YES;
    }
    UIViewController *answering = decider;
    ANNNavigationOptions *options = [self optionsOfScreen:decider
                                                 forGroup:ANNSystemUIGroupStatusBar];
    if (!options) {
        UIViewController *main = [self deciderInWindow:ANNUtils.mainWindow
                                              question:ANNSystemUIQuestionStatusBarStyle];
        if (!main || main == decider || !main.isLayoutNode) {
            return YES;
        }
        answering = main;
        options = [self optionsOfScreen:main forGroup:ANNSystemUIGroupStatusBar];
    }
    ANNNodePresenter *presenter = answering.presenter;
    return options && presenter ? [presenter statusBarAnimatesForOptions:options] : YES;
}

#pragma mark - Who decides

+ (UIViewController *)topScreenOf:(UIViewController *)node {
    UIViewController *screen = node;
    for (NSUInteger depth = 0; screen && depth < ANNWalkLimit; depth++) {
        if (!screen.isLayoutNode) {
            return nil;
        }
        UIViewController *child = [screen systemUIChild] ?: [screen getCurrentChild];
        if (!child || child == screen) {
            return screen;
        }
        screen = child;
    }
    return screen;
}

+ (UIViewController *)deciderInWindow:(UIWindow *)window question:(ANNSystemUIQuestion)question {
    UIViewController *root = window.rootViewController;
    if (!root) {
        return nil;
    }
    UIViewController *decider = root;
    NSUInteger depth = 0;
    UIViewController *presented = root.presentedViewController;
    while (presented && depth < ANNWalkLimit) {
        if (ANNPresentedTakes(presented, question)) {
            decider = presented;
        }
        presented = presented.presentedViewController;
        depth++;
    }
    for (depth = 0; depth < ANNWalkLimit; depth++) {
        UIViewController *child = ANNChildFor(decider, question);
        if (!child || child == decider) {
            break;
        }
        decider = child;
    }
    return decider;
}

+ (ANNNavigationOptions *)optionsOfScreen:(UIViewController *)screen
                                 forGroup:(ANNSystemUIGroup)group {
    if (![ANNOverlayCoordinator overlayRootIdOf:screen]) {
        return [screen resolveOptionsWithDefault];
    }
    // The overlay's own options from its root down to the screen, merged on a copy: never the defaults.
    NSMutableArray<UIViewController *> *nodes = [NSMutableArray new];
    for (UIViewController *node = screen; node && nodes.count < ANNWalkLimit;
         node = node.layoutParent) {
        [nodes addObject:node];
    }
    ANNNavigationOptions *own = [ANNNavigationOptions new];
    for (UIViewController *node in nodes.reverseObjectEnumerator) {
        ANNNavigationOptions *options = node.options;
        if (options) {
            [own mergeOptions:options];
        }
    }
    return ([self groupsNamedBy:own onlyRead:YES] & group) ? own : nil;
}

+ (BOOL)navigationBarHiddenOfScreen:(UIViewController *)screen {
    if (![screen isKindOfClass:ANNComponentViewController.class]) {
        return NO;
    }
    return [self barHiddenOf:[(ANNComponentViewController *)screen shownNavigation]];
}

+ (BOOL)barHiddenOf:(UINavigationController *)navigation {
    return navigation != nil && (navigation.navigationBarHidden || navigation.navigationBar.hidden);
}

+ (ANNNavigationOptions *)ownOptionsWithDefaultOf:(UIViewController *)node {
    ANNNavigationOptions *result = [ANNDefaultOptions.current copy];
    NSMutableArray<UIViewController *> *parents = [NSMutableArray new];
    for (UIViewController *parent = node.layoutParent; parent && parents.count < ANNWalkLimit;
         parent = parent.layoutParent) {
        [parents addObject:parent];
    }
    for (UIViewController *parent in parents.reverseObjectEnumerator) {
        ANNNavigationOptions *own = parent.options;
        if (own) {
            [result mergeOptions:own];
        }
    }
    ANNNavigationOptions *own = node.options;
    if (own) {
        [result mergeOptions:own];
    }
    return result;
}

@end

#import "ANNStackPresenter.h"
#import "ANNBarBackgroundComponent.h"
#import "ANNStackController.h"
#import "ANNSystemUI.h"
#import "ANNTopBarPresenter.h"

ANNSharedBarVisibility ANNSharedBarVisibilityOfMerge(ANNNavigationOptions *mergeOptions) {
    ANNSharedBarVisibility visibility = ANNSharedBarVisibilityNone;
    if (mergeOptions.topBar.visible.hasValue) {
        visibility |= ANNSharedBarVisibilityBar;
    }
    ANNToolbarOptions *toolbar = mergeOptions.toolbar;
    if (toolbar.visible.hasValue || toolbar.buttons != nil) {
        visibility |= ANNSharedBarVisibilityToolbar;
    }
    return visibility;
}

@implementation ANNStackPresenter

- (nullable ANNStackController *)stack {
    UIViewController *bound = self.boundViewController;
    return [bound isKindOfClass:ANNStackController.class] ? (ANNStackController *)bound : nil;
}

#pragma mark - ANNNodePresenter

// The bar exists before the view: it allows large titles from the start.
- (void)applyOptionsOnInit:(ANNNavigationOptions *)initialOptions {
    [super applyOptionsOnInit:initialOptions];
    self.stack.navigationBar.prefersLargeTitles = YES;
}

// A top screen appeared (UIKit's `didShowViewController:`): what the stack shows follows its options.
- (void)applyOptions:(ANNNavigationOptions *)options {
    [super applyOptions:options];
    ANNStackController *stack = self.stack;
    [stack updateSwipeBack];
    [stack applySharedBarIfTopChangedAnimated:NO];
    [self applyBackground:options];
}

// A merge with the stack's id: the top screen's bar takes the merge's bar part with its own resolved options (its item
// holds title, buttons and back button), then the stack its shared part (correction 7).
- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions
     resolvedOptions:(ANNNavigationOptions *)resolvedOptions {
    [super mergeOptions:mergeOptions resolvedOptions:resolvedOptions];
    ANNStackController *stack = self.stack;
    if (mergeOptions.popGesture.hasValue) {
        [stack updateSwipeBack];
    }
    UIViewController *top = [stack getCurrentChild];
    UIViewController *screen = top ? ANNStackOptionsScreen(top) : nil;
    ANNTopBarPresenter *topBar = screen ? [ANNTopBarPresenter presenterOfScreen:screen] : nil;
    if (topBar) {
        [topBar mergeParentOptions:mergeOptions resolvedOptions:[screen resolveOptionsWithDefault]];
    }
    [stack applySharedBarAnimated:YES visibility:ANNSharedBarVisibilityOfMerge(mergeOptions)];
    if (mergeOptions.layout.backgroundColor.hasValue) {
        [self applyBackground:resolvedOptions];
    }
}

// New default options repaint the shown stack's bar at once, without its visibility.
- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions {
    [super applyDefaultOptionsChange:resolvedOptions];
    [self.stack applySharedBarAnimated:NO visibility:ANNSharedBarVisibilityNone];
    [self applyBackground:resolvedOptions];
}

#pragma mark - The background (`layout.backgroundColor`)

- (void)applyBackground:(ANNNavigationOptions *)resolvedOptions {
    UIView *view = self.stack.viewIfLoaded;
    if (!view) {
        return;
    }
    UIColor *color = [resolvedOptions.layout.backgroundColor withDefault:nil];
    if (!(view.backgroundColor == color || [view.backgroundColor isEqual:color])) {
        view.backgroundColor = color;
    }
}

#pragma mark - The shared parts of the bar

- (void)applySharedBar:(ANNNavigationOptions *)options
              ofScreen:(UIViewController *)screen
              animated:(BOOL)animated
           coordinator:(id<UIViewControllerTransitionCoordinator>)coordinator
            visibility:(ANNSharedBarVisibility)visibility {
    ANNStackController *stack = self.stack;
    if (!stack) {
        return;
    }
    // In More the screens show in UIKit's More navigation: the shared parts go onto it.
    UINavigationController *navigation = stack.shownNavigationController;
    UINavigationBar *bar = navigation.navigationBar;
    BOOL inWindow = navigation.viewIfLoaded.window != nil;
    ANNTopBarOptions *topBar = options.topBar;
    BOOL barsChanged = NO;
    // `visible` (`topBar.visible`), animated by the resolved `animate`; UIKit animates it with the transition that
    // runs.
    BOOL hidden = ![topBar.visible withDefault:YES];
    if ((visibility & ANNSharedBarVisibilityBar) && navigation.navigationBarHidden != hidden) {
        BOOL animates = animated && inWindow && [topBar.animate withDefault:YES];
        [navigation setNavigationBarHidden:hidden animated:animates];
        barsChanged = YES;
        // `hideWithTopBar` follows the bar: UIKit's More navigation's too.
        [ANNSystemUI setNeedsUpdateOfGroups:ANNSystemUIGroupStatusBar];
    }
    // The toolbar shows while the screen has buttons and `toolbar.visible` isn't `false`.
    BOOL toolbarHidden = !options.toolbar.shouldShow;
    if ((visibility & ANNSharedBarVisibilityToolbar) && navigation.toolbarHidden != toolbarHidden) {
        [navigation setToolbarHidden:toolbarHidden animated:animated && inWindow];
        barsChanged = YES;
    }
    // The hiding modes: before iOS 27 `hideOnScroll` is the stack's swipe; from 27 the screen's item minimizes the bar.
    BOOL hidesOnSwipe = [topBar.hideOnScroll withDefault:NO];
    if (@available(iOS 27.0, *)) {
        hidesOnSwipe = NO;
    }
    if (navigation.hidesBarsOnSwipe != hidesOnSwipe) {
        navigation.hidesBarsOnSwipe = hidesOnSwipe;
    }
    BOOL hidesOnTap = [topBar.hideOnTap withDefault:NO];
    if (navigation.hidesBarsOnTap != hidesOnTap) {
        navigation.hidesBarsOnTap = hidesOnTap;
    }
    // `hideOnKeyboard` is the stack's own (`keyboardWillShow:` of `ANNStackController`), read from the top at each
    // keyboard: UIKit's `hidesBarsWhenKeyboardAppears`, once on, kept hiding the bar for the screens shown after the
    // one that set it, also after it was set back to `NO`. UIKit's flag stays off.
    if (navigation.hidesBarsWhenKeyboardAppears) {
        navigation.hidesBarsWhenKeyboardAppears = NO;
    }
    // Passed through.
    BOOL black = [[topBar.barStyle withDefault:nil] isEqualToString:@"black"];
    UIBarStyle barStyle = black ? UIBarStyleBlack : UIBarStyleDefault;
    if (bar.barStyle != barStyle) {
        bar.barStyle = barStyle;
    }
    BOOL clips = [topBar.background.clipToBounds withDefault:NO];
    if (bar.clipsToBounds != clips) {
        bar.clipsToBounds = clips;
    }
    // Without a `testID` UIKit names the bar by its title. Each value is set only when it changes: nothing of the bar
    // moves in the middle of a transition for nothing.
    NSString *testID = [topBar.testID withDefault:nil];
    testID = testID.length > 0 ? testID : nil;
    if (![bar.accessibilityIdentifier ?: @"" isEqualToString:testID ?: @""]) {
        bar.accessibilityIdentifier = testID;
    }
    // The toolbar exists once it shows or has an id to carry: asking for it makes it.
    NSString *toolbarTestID = [options.toolbar.testID withDefault:nil];
    toolbarTestID = toolbarTestID.length > 0 ? toolbarTestID : nil;
    if (toolbarTestID || !navigation.toolbarHidden) {
        UIToolbar *toolbar = navigation.toolbar;
        if (![toolbar.accessibilityIdentifier ?: @"" isEqualToString:toolbarTestID ?: @""]) {
            toolbar.accessibilityIdentifier = toolbarTestID;
        }
    }
    // iOS 26: the bar keeps a copy of the shown screen's look too.
    if (@available(iOS 26.0, *)) {
        UINavigationBarAppearance *standard = screen.navigationItem.standardAppearance;
        if (standard && ![bar.standardAppearance isEqual:standard]) {
            bar.standardAppearance = [standard copy];
        }
    }
    ANNTopBarPresenter *screenBar = [ANNTopBarPresenter presenterOfScreen:screen];
    [ANNBarBackgroundComponent show:screenBar.background inBar:bar coordinator:coordinator];
    // Outside a transition the screen places it at once (in the list under an expanded large title); after a
    // transition its appearance does.
    if (!coordinator) {
        [screenBar updateBackgroundPlacement];
    }
    // The screens apply their "under bars" rule again (`hasShownTopBar`, the toolbar's padding).
    if (barsChanged) {
        [stack updateRootLayoutOfShownEntries];
    }
}

@end

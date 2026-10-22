#import "ANNComponentPresenter.h"
#import "ANNBottomTabsController.h"
#import "ANNComponentViewController.h"
#import "ANNDefaultOptions.h"
#import "ANNTopBarPresenter.h"
#import "ANNUtils.h"

// The presenters whose screen shows `statusBar.blur`: the status bar hidden or shown lays out no view, so they hear of
// it from the system UI (`statusBarDidChange`).
static NSHashTable<ANNComponentPresenter *> *ANNBlurringPresenters;

@implementation ANNComponentPresenter {
    // `statusBar.blur` while it is on.
    UIVisualEffectView *_statusBarBlur;
}

- (nullable ANNComponentViewController *)screen {
    UIViewController *bound = self.boundViewController;
    return [bound isKindOfClass:ANNComponentViewController.class] ? (ANNComponentViewController *)bound
                                                                    : nil;
}

#pragma mark - ANNNodePresenter

// The bar's part comes with the screen: its registry takes the screen's creator, set before the presenter binds.
- (void)bindViewController:(UIViewController *)viewController {
    [super bindViewController:viewController];
    if (!_topBar && [viewController isKindOfClass:ANNComponentViewController.class]) {
        _topBar = [[ANNTopBarPresenter alloc] initWithScreen:viewController];
    }
}

// The screen has no view yet at its creation (a screen not shown at once makes its content when UIKit first loads its
// view): its background comes when the view loads. Its bar takes the screen's own options over the defaults now (the
// stack's aren't known yet).
- (void)applyOptionsOnInit:(ANNNavigationOptions *)initialOptions {
    [super applyOptionsOnInit:initialOptions];
    [_topBar applyOptionsOnInit:[initialOptions withDefault:ANNDefaultOptions.current]];
}

// Each appearance, with the options of the stack and the tabs the screen is in now: the bar first (it may show or hide
// the stack's bar), then the containers around the screen, then the rule of the root frame.
- (void)applyOptions:(ANNNavigationOptions *)options {
    [super applyOptions:options];
    [self applyBackground:options];
    [self applyStatusBarBlur:options];
    [_topBar applyOptions:options];
    // The tabs follow the screen their selected tab shows: their bar's look and visibility, before the screen reads
    // whether a tab bar shows (`hasShownTabBar`).
    UIViewController *bound = self.boundViewController;
    if (bound) {
        [[ANNBottomTabsController tabsOf:bound] screenWillAppear:bound options:options];
        // A container that applies its options at every appearance of a screen inside it (a side menu) does it now, the
        // nearest first.
        for (UIViewController *parent = bound.layoutParent; parent.isLayoutNode;
             parent = parent.layoutParent) {
            [parent.presenter descendantWillAppear:bound];
        }
    }
    [self.screen updateRootLayout];
}

// The rule and the safe area are read again on every merge: the edges, the large title or the bars may have changed
// (`mergeOptions` step 4).
- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions
     resolvedOptions:(ANNNavigationOptions *)resolvedOptions {
    [super mergeOptions:mergeOptions resolvedOptions:resolvedOptions];
    ANNLayoutOptions *layout = mergeOptions.layout;
    if (layout.backgroundColor.hasValue || layout.componentBackgroundColor.hasValue) {
        [self applyBackground:resolvedOptions];
    }
    if (mergeOptions.statusBar.blur.hasValue) {
        [self applyStatusBarBlur:resolvedOptions];
    }
    [_topBar mergeOptions:mergeOptions resolvedOptions:resolvedOptions];
    [self.screen updateRootLayout];
}

// New default options repaint the background and the bar; the rule and the safe area stay.
- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions {
    [super applyDefaultOptionsChange:resolvedOptions];
    [self applyBackground:resolvedOptions];
    [_topBar applyDefaultOptionsChange:resolvedOptions];
}

#pragma mark - Background

- (void)applyBackground:(ANNNavigationOptions *)resolvedOptions {
    ANNComponentViewController *screen = self.screen;
    if (!screen.isViewLoaded) {
        return;
    }
    ANNLayoutOptions *layout = resolvedOptions.layout;
    screen.view.backgroundColor =
        [layout.backgroundColor withDefault:UIColor.systemBackgroundColor] ?: UIColor.clearColor;
    screen.reactView.backgroundColor =
        [layout.componentBackgroundColor withDefault:UIColor.clearColor] ?: UIColor.clearColor;
}

#pragma mark - The status bar's blur (`statusBar.blur`)

// Over the screen's own view, never over a React view; no touches, nothing for VoiceOver.
- (void)applyStatusBarBlur:(ANNNavigationOptions *)resolvedOptions {
    ANNComponentViewController *screen = self.screen;
    if (!screen.isViewLoaded) {
        return;
    }
    if (![resolvedOptions.statusBar.blur withDefault:NO]) {
        [_statusBarBlur removeFromSuperview];
        _statusBarBlur = nil;
        [ANNBlurringPresenters removeObject:self];
        return;
    }
    UIView *view = screen.view;
    if (!_statusBarBlur) {
        UIBlurEffect *effect = [UIBlurEffect effectWithStyle:UIBlurEffectStyleRegular];
        _statusBarBlur = [[UIVisualEffectView alloc] initWithEffect:effect];
        _statusBarBlur.userInteractionEnabled = NO;
        _statusBarBlur.isAccessibilityElement = NO;
        _statusBarBlur.accessibilityElementsHidden = YES;
        if (!ANNBlurringPresenters) {
            ANNBlurringPresenters = [NSHashTable weakObjectsHashTable];
        }
        [ANNBlurringPresenters addObject:self];
    }
    if (_statusBarBlur.superview != view) {
        [view addSubview:_statusBarBlur];
    } else {
        [view bringSubviewToFront:_statusBarBlur];
    }
    [self layOutStatusBarBlur];
}

// The status bar's frame in the scene of the screen's window (the main window's before the screen is in one), in the
// view's own coordinates without a conversion: a pushed screen's view may still be out of place while it appears. A
// hidden status bar has no frame, and the blur no height.
- (void)layOutStatusBarBlur {
    UIView *view = _statusBarBlur.superview;
    if (!view) {
        return;
    }
    UIWindowScene *scene = view.window.windowScene;
    CGRect statusBar = scene ? scene.statusBarManager.statusBarFrame : ANNUtils.statusBarFrame;
    CGRect frame = CGRectMake(0, CGRectGetMinY(statusBar), CGRectGetWidth(view.bounds),
                              CGRectGetHeight(statusBar));
    if (!CGRectEqualToRect(_statusBarBlur.frame, frame)) {
        _statusBarBlur.frame = frame;
    }
}

+ (void)statusBarDidChange {
    for (ANNComponentPresenter *presenter in ANNBlurringPresenters.allObjects) {
        [presenter layOutStatusBarBlur];
    }
}

- (void)keepStatusBarBlurOnTop {
    UIView *superview = _statusBarBlur.superview;
    if (superview && superview.subviews.lastObject != _statusBarBlur) {
        [superview bringSubviewToFront:_statusBarBlur];
    }
}

@end

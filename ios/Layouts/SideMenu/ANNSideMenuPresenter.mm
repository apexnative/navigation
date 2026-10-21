#import "ANNSideMenuPresenter.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNDefaultOptions.h"
#import "ANNShownScreens.h"
#import "ANNSideMenuViewController.h"

// The defaults of `sideMenu.*`: what a menu shows with when no layer gives a value.
static const double ANNSideMenuPresenterDefaultWidth = 280;
static const double ANNSideMenuPresenterDefaultVelocity = 840;

// `openMode`: only `pushContent` moves the center; any other value, and none, is `aboveContent`.
static ANNSideMenuOpenMode ANNSideMenuOpenModeOf(Text *openMode) {
    return [[openMode get] isEqualToString:@"pushContent"] ? ANNSideMenuOpenModePushContent
                                                           : ANNSideMenuOpenModeAboveContent;
}

// `openGestureMode`: only `entireScreen` opens from anywhere; any other value, and none, is `bezel`.
static ANNSideMenuOpenGestureMode ANNSideMenuOpenGestureModeOf(Text *openGestureMode) {
    return [[openGestureMode get] isEqualToString:@"entireScreen"] ? ANNSideMenuOpenGestureModeEntireScreen
                                                                   : ANNSideMenuOpenGestureModeBezel;
}

static NSString *ANNSideMenuDerivedID(NSString *testID, NSString *suffix) {
    return testID ? [testID stringByAppendingString:suffix] : nil;
}

@implementation ANNSideMenuPresenter {
    // The layout's `visible: true` of each side, until the first appearance of a screen inside.
    BOOL _openLeftAtFirstShow;
    BOOL _openRightAtFirstShow;
    BOOL _shown;
}

- (ANNSideMenuViewController *)sideMenu {
    UIViewController *bound = self.boundViewController;
    if (![bound isKindOfClass:ANNSideMenuViewController.class] || bound.nodeHelper.destroyed) {
        return nil;
    }
    return (ANNSideMenuViewController *)bound;
}

- (void)setVisibilityAtFirstShow:(ANNSideMenuOptions *)sideMenu {
    _openLeftAtFirstShow = [sideMenu.left.visible withDefault:NO];
    _openRightAtFirstShow = [sideMenu.right.visible withDefault:NO];
}

#pragma mark - ANNNodePresenter

// The module passes its own resolution, without the defaults (no `visible` at creation).
- (void)applyOptionsOnInit:(ANNNavigationOptions *)initialOptions {
    [super applyOptionsOnInit:initialOptions];
    [self apply:[initialOptions withDefault:ANNDefaultOptions.current] namedBy:nil];
}

- (void)applyOptions:(ANNNavigationOptions *)options {
    [super applyOptions:options];
    [self apply:options namedBy:nil];
}

// A screen inside appears: every field again from the side menu's resolution now (the current part is still the
// center while a menu opens). The first time, the layout's `visible` too, taken before the module is called, so that
// the screens it shows, applying their options, find nothing left to open.
- (void)descendantWillAppear:(UIViewController *)screen {
    [super descendantWillAppear:screen];
    ANNSideMenuViewController *sideMenu = self.sideMenu;
    if (!sideMenu) {
        return;
    }
    [self apply:[sideMenu.nodeHelper resolveOptionsWithDefaultUncached] namedBy:nil];
    if (_shown) {
        return;
    }
    _shown = YES;
    BOOL left = _openLeftAtFirstShow;
    BOOL right = _openRightAtFirstShow;
    _openLeftAtFirstShow = NO;
    _openRightAtFirstShow = NO;
    // Without animation: the side menu itself is only starting to appear; both sides, left first.
    if (left) {
        [sideMenu side:ANNSideMenuSideLeft visible:YES animated:NO];
    }
    if (right) {
        [sideMenu side:ANNSideMenuSideRight visible:YES animated:NO];
    }
    if (!left && !right) {
        return;
    }
    // A show the command drives (`setRoot`, a modal, a tab switch) listed only the center's screens, and UIKit's
    // calls of the menu's screens are silent then: the open menu starts to appear with the side menu now, with its
    // options; its `DidAppear` comes with the show's end.
    UIViewController *menu = [sideMenu getCurrentChild];
    if (menu != sideMenu.center && [ANNAppearanceDispatcher isAppearanceDriven:sideMenu]) {
        [ANNAppearanceDispatcher nodeWillAppear:menu];
    }
    [ANNShownScreens changedIn:sideMenu];
}

// `mergeOptions` with the side menu's id.
- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions
     resolvedOptions:(ANNNavigationOptions *)resolvedOptions {
    [super mergeOptions:mergeOptions resolvedOptions:resolvedOptions];
    [self applyMerge:mergeOptions];
}

- (void)mergeChildOptions:(ANNNavigationOptions *)mergeOptions {
    [self applyMerge:mergeOptions];
}

// New defaults repaint the look: the background and the `testID`s; the rest at the next appearance, never `visible`.
- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions {
    [super applyDefaultOptionsChange:resolvedOptions];
    ANNSideMenuViewController *sideMenu = self.sideMenu;
    if (!sideMenu) {
        return;
    }
    ANNNavigationOptions *resolved = [sideMenu.nodeHelper resolveOptionsWithDefaultUncached];
    [self applyBackground:resolved];
    [self applyTestIDs:resolved.sideMenu];
}

// Only the nearest side menu acts on `sideMenu`: its parents get the merge without it.
- (ANNNavigationOptions *)optionsForParent:(ANNNavigationOptions *)mergeOptions {
    if (!mergeOptions.sideMenu.hasValue) {
        return mergeOptions;
    }
    ANNNavigationOptions *up = [mergeOptions copy];
    up.sideMenu = [ANNSideMenuOptions new];
    return up;
}

#pragma mark - Applying

// The fields the merge names, with the values of the side menu's resolution after it, then the merge's `visible` with
// its `animate`, last.
- (void)applyMerge:(ANNNavigationOptions *)mergeOptions {
    ANNSideMenuViewController *sideMenu = self.sideMenu;
    if (!sideMenu) {
        return;
    }
    [self apply:[sideMenu.nodeHelper resolveOptionsWithDefaultUncached] namedBy:mergeOptions];
    [self applyVisibility:mergeOptions.sideMenu];
}

// Everything of `options` (`named` nil), or only what `named` gives a value to; `visible` never.
- (void)apply:(ANNNavigationOptions *)options namedBy:(ANNNavigationOptions *)named {
    ANNSideMenuViewController *sideMenu = self.sideMenu;
    if (!sideMenu) {
        return;
    }
    ANNSideMenuOptions *resolved = options.sideMenu;
    ANNSideMenuOptions *merge = named.sideMenu;
    [self applySide:ANNSideMenuSideLeft
            options:resolved.left
              named:named ? merge.left : nil
                 to:sideMenu];
    [self applySide:ANNSideMenuSideRight
            options:resolved.right
              named:named ? merge.right : nil
                 to:sideMenu];
    if (!named || merge.openGestureMode.hasValue) {
        [sideMenu setOpenGestureMode:ANNSideMenuOpenGestureModeOf(resolved.openGestureMode)];
    }
    if (!named || merge.animationType.hasValue) {
        [sideMenu setAnimationType:[resolved.animationType get]];
    }
    if (!named || merge.testID.hasValue || merge.left.testID.hasValue || merge.right.testID.hasValue) {
        [self applyTestIDs:resolved];
    }
    if (!named || named.layout.backgroundColor.hasValue) {
        [self applyBackground:options];
    }
}

- (void)applySide:(ANNSideMenuSide)side
          options:(ANNSideMenuSideOptions *)options
            named:(ANNSideMenuSideOptions *)named
               to:(ANNSideMenuViewController *)sideMenu {
    BOOL all = named == nil;
    if (all || named.enabled.hasValue) {
        [sideMenu side:side enabled:[options.enabled withDefault:YES]];
    }
    // The width given by the side menu or the screen on top, else the default.
    if (all || named.width.hasValue) {
        [sideMenu side:side width:[options.width withDefault:ANNSideMenuPresenterDefaultWidth]];
    }
    if (all || named.openMode.hasValue) {
        [sideMenu side:side openMode:ANNSideMenuOpenModeOf(options.openMode)];
    }
    if (all || named.shouldStretchDrawer.hasValue) {
        [sideMenu side:side shouldStretch:[options.shouldStretchDrawer withDefault:YES]];
    }
    if (all || named.animationVelocity.hasValue) {
        [sideMenu side:side
            animationVelocity:[options.animationVelocity
                                  withDefault:ANNSideMenuPresenterDefaultVelocity]];
    }
    if (all || named.glass.hasValue) {
        [sideMenu side:side glass:[options.glass withDefault:NO]];
    }
}

// `visible: true` opens (the other menu closes at once), `false` closes; animated unless `animate: false`. Left first,
// then right: a later `true` wins.
- (void)applyVisibility:(ANNSideMenuOptions *)sideMenuOptions {
    ANNSideMenuViewController *sideMenu = self.sideMenu;
    if (!sideMenu) {
        return;
    }
    BOOL acted = NO;
    for (NSNumber *each in @[ @(ANNSideMenuSideLeft), @(ANNSideMenuSideRight) ]) {
        ANNSideMenuSide side = (ANNSideMenuSide)each.integerValue;
        ANNSideMenuSideOptions *options =
            side == ANNSideMenuSideLeft ? sideMenuOptions.left : sideMenuOptions.right;
        if (!options.visible.hasValue) {
            continue;
        }
        acted = YES;
        // A command after the layout wins over the layout's own `visible`: an open over both sides (one menu at a
        // time), a close over its own side only.
        if ([options.visible get] || side == ANNSideMenuSideLeft) {
            _openLeftAtFirstShow = NO;
        }
        if ([options.visible get] || side == ANNSideMenuSideRight) {
            _openRightAtFirstShow = NO;
        }
        [sideMenu side:side visible:[options.visible get] animated:[options.animate withDefault:YES]];
    }
    if (acted) {
        [ANNShownScreens changedIn:sideMenu];
    }
}

#pragma mark - Look

// The side menu's view (`layout.backgroundColor`, 18): a color, or nothing for none and for "no color".
- (void)applyBackground:(ANNNavigationOptions *)options {
    UIView *view = self.sideMenu.viewIfLoaded;
    if (view) {
        view.backgroundColor = [options.layout.backgroundColor withDefault:nil];
    }
}

// A side's own `testID` wins for its container and its scrim.
- (void)applyTestIDs:(ANNSideMenuOptions *)options {
    ANNSideMenuViewController *sideMenu = self.sideMenu;
    if (!sideMenu) {
        return;
    }
    NSString *testID = [options.testID get];
    sideMenu.viewIfLoaded.accessibilityIdentifier = testID;
    sideMenu.center.testID = ANNSideMenuDerivedID(testID, @".center");
    NSString *left = [options.left.testID get];
    NSString *right = [options.right.testID get];
    sideMenu.left.testID = left ?: ANNSideMenuDerivedID(testID, @".left");
    sideMenu.right.testID = right ?: ANNSideMenuDerivedID(testID, @".right");
    [sideMenu side:ANNSideMenuSideLeft scrimTestID:ANNSideMenuDerivedID(left ?: testID, @".scrim")];
    [sideMenu side:ANNSideMenuSideRight scrimTestID:ANNSideMenuDerivedID(right ?: testID, @".scrim")];
}

@end

#import "ANNNavigationState.h"
#import "ANNAssert.h"
#import "ANNBottomTabsController.h"
#import "ANNComponentViewController.h"
#import "ANNErrorHandler.h"
#import "ANNErrorTexts.h"
#import "ANNModalCoordinator.h"
#import "ANNSlotContainers.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"

// Main queue.
static BOOL ANNUpdateScheduled;
static BOOL ANNHasLastState;
static NSString *ANNLastScreenId;
static NSString *ANNLastScreenName;
static NSNumber *ANNLastTab;
static NSUInteger ANNUpdateHolds;
static BOOL ANNUpdateWaitsForRelease;

static BOOL ANNSameValue(id a, id b) {
    return a == b || [a isEqual:b];
}

@implementation ANNNavigationState

+ (void)setNeedsUpdate {
    ANNAssertMainQueue();
    if (ANNUpdateScheduled) {
        return;
    }
    ANNUpdateScheduled = YES;
    dispatch_async(dispatch_get_main_queue(), ^{
      ANNUpdateScheduled = NO;
      if (ANNUpdateHolds > 0) {
          ANNUpdateWaitsForRelease = YES;
          return;
      }
      [ANNNavigationState updateNow];
    });
}

+ (void)holdUpdates {
    ANNAssertMainQueue();
    ANNUpdateHolds++;
}

+ (void)releaseUpdates {
    ANNAssertMainQueue();
    if (ANNUpdateHolds == 0) {
        return;
    }
    ANNUpdateHolds--;
    if (ANNUpdateHolds == 0 && ANNUpdateWaitsForRelease) {
        ANNUpdateWaitsForRelease = NO;
        [self setNeedsUpdate];
    }
}

+ (void)updateNow {
    [self updateNowFor:nil];
}

+ (void)updateNowFor:(NSString *)commandName {
    ANNAssertMainQueue();
    NSString *screenId = nil;
    NSString *name = nil;
    NSNumber *tab = nil;
    // The one guard of the state step: a walk that fails costs only this state, never the events and the answer of
    // the transition that asked for it.
    @try {
        ANNBottomTabsController *tabs = nil;
        UIViewController *screen = [self visibleScreenWithTabs:&tabs];
        screenId = screen.layoutInfo.componentId;
        if (screenId) {
            name = ANNComponentNameWithoutSlotPrefix(screen.layoutInfo.name ?: @"");
            tab = tabs ? @(tabs.selectedTabIndex) : nil;
        }
    } @catch (NSException *exception) {
        NSString *reason = exception.reason.length > 0 ? exception.reason : exception.name;
        [ANNErrorHandler logFailureOf:commandName ?: @"transition"
                                 text:[ANNErrorTexts couldNot:@"report the navigation state"
                                                        reason:reason]];
        return;
    }
    if (ANNHasLastState && ANNSameValue(screenId, ANNLastScreenId) &&
        ANNSameValue(name, ANNLastScreenName) && ANNSameValue(tab, ANNLastTab)) {
        return;
    }
    ANNHasLastState = YES;
    ANNLastScreenId = screenId;
    ANNLastScreenName = name;
    ANNLastTab = tab;
    [ApexNavigation.eventEmitter sendNavigationStateChanged:screenId name:name tab:tab];
}

+ (void)reset {
    ANNAssertMainQueue();
    ANNHasLastState = NO;
    // A new bundle: nothing of the old JavaScript holds the state any more.
    ANNUpdateHolds = 0;
    ANNUpdateWaitsForRelease = NO;
    ANNLastScreenId = nil;
    ANNLastScreenName = nil;
    ANNLastTab = nil;
}

#pragma mark - What shows

// The layout the user sees: the top modal on screen, from the modals' own records (a modal being dismissed counts until
// its end, one closed below doesn't, foreign controllers and overlays never), else the window's root; then down the
// current children. `tabs` gets the nearest tabs the visible screen is in, the last met on the way down: in a modal
// those of the modal, never those under it; with tabs inside tabs the inner ones. Their own selected index is in layout
// order, More's tabs included, never UIKit's.
+ (UIViewController *)visibleScreenWithTabs:(ANNBottomTabsController **)tabs {
    UIViewController *visible = [ANNModalCoordinator visibleLayout];
    ANNBottomTabsController *nearestTabs = nil;
    for (UIViewController *node = visible; node; node = [node getCurrentChild]) {
        visible = node;
        if ([node isKindOfClass:ANNBottomTabsController.class]) {
            nearestTabs = (ANNBottomTabsController *)node;
        }
    }
    *tabs = nearestTabs;
    return [visible isKindOfClass:ANNComponentViewController.class] ? visible : nil;
}

@end

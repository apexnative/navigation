#import "ANNShownScreens.h"
#import "ANNAssert.h"
#import "ANNModalCoordinator.h"
#import "ANNPendingLayers.h"
#import "ANNSystemUI.h"

@implementation ANNShownScreens

+ (void)changedIn:(UIViewController *)node {
    ANNAssertMainQueue();
    [ANNPendingLayers shownScreensChangedIn:node];
    [ANNModalCoordinator visibleScreenChangedIn:node];
    [ANNSystemUI setNeedsUpdate];
}

@end

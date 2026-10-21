#import "ANNSlotHost.h"
#import "ANNAssert.h"
#import "ANNUtils.h"
#import <React/RCTFabricSurface.h>
#import <React/RCTSurfaceHostingView.h>
#import <ReactCommon/RCTHost.h>

static UIWindow *ANNSlotHostWindow;
static RCTFabricSurface *ANNSlotHostSurface;

@implementation ANNSlotHost

+ (NSString *)moduleName {
    return @"apexnative.slots";
}

+ (NSInteger)rootTag {
    return ANNSlotHostSurface.rootTag;
}

+ (RCTFabricSurface *)surface {
    return ANNSlotHostSurface;
}

+ (void)startWithHost:(RCTHost *)host {
    if (ANNSlotHostSurface != nil || host == nil) {
        return;
    }
    RCTFabricSurface *surface = [host createSurfaceWithModuleName:self.moduleName initialProperties:@{}];
    RCTSurfaceHostingView *surfaceView = [[RCTSurfaceHostingView alloc]
        initWithSurface:surface
        sizeMeasureMode:RCTSurfaceSizeMeasureModeWidthExact | RCTSurfaceSizeMeasureModeHeightExact];
    surfaceView.backgroundColor = UIColor.clearColor;
    surfaceView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;

    // Hidden and without a root view controller: it takes no touches and has no say in the orientation or the status
    // bar (a visible window above the app does). The tree still mounts; the slots move their content into screens.
    UIWindowScene *scene = ANNUtils.mainWindow.windowScene;
    UIWindow *window = scene ? [[UIWindow alloc] initWithWindowScene:scene]
                             : [[UIWindow alloc] initWithFrame:ANNUtils.mainScreen.bounds];
    surfaceView.frame = window.bounds;
    [window addSubview:surfaceView];
    window.hidden = YES;

    ANNSlotHostWindow = window;
    ANNSlotHostSurface = surface;
    // The host starts the surface itself once the bundle has run. A start of its own raced it: both found the surface
    // not started yet and attached it to its view twice, which React Native asserts against.
}

+ (void)mainWindowSceneDidConnect:(UIWindowScene *)windowScene {
    ANNAssertMainQueue();
    UIWindow *window = ANNSlotHostWindow;
    if (window && window.windowScene != windowScene) {
        window.windowScene = windowScene;
    }
}

@end

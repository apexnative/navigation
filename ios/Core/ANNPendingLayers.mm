#import "ANNPendingLayers.h"
#import "ANNAssert.h"
#import "UIViewController+LayoutProtocol.h"

// The layers that wait, kept weakly: a layer that goes without being removed waits no more. Main queue.
static NSMapTable<UIViewController *, dispatch_block_t> *ANNLayers(void) {
    static NSMapTable<UIViewController *, dispatch_block_t> *layers;
    if (!layers) {
        layers = [NSMapTable weakToStrongObjectsMapTable];
    }
    return layers;
}

@implementation ANNPendingLayers

+ (void)add:(UIViewController *)layer onShownScreensChanged:(dispatch_block_t)onShownScreensChanged {
    ANNAssertMainQueue();
    [ANNLayers() setObject:[onShownScreensChanged copy] forKey:layer];
}

+ (void)remove:(UIViewController *)layer {
    ANNAssertMainQueue();
    [ANNLayers() removeObjectForKey:layer];
}

// The layer that waits to show and holds `node`, the nearest first.
+ (nullable UIViewController *)layerOf:(UIViewController *)node {
    NSMapTable<UIViewController *, dispatch_block_t> *layers = ANNLayers();
    if (layers.count == 0) {
        return nil;
    }
    for (UIViewController *parent = node; parent; parent = parent.layoutParent) {
        if ([layers objectForKey:parent]) {
            return parent;
        }
    }
    return nil;
}

+ (BOOL)isWaitingToShow:(UIViewController *)node {
    ANNAssertMainQueue();
    return [self layerOf:node] != nil;
}

+ (void)shownScreensChangedIn:(UIViewController *)node {
    ANNAssertMainQueue();
    UIViewController *layer = [self layerOf:node];
    if (!layer) {
        return;
    }
    dispatch_block_t handler = [ANNLayers() objectForKey:layer];
    if (handler) {
        handler();
    }
}

@end

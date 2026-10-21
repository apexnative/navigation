#import "ANNSlotContentView.h"
#import <React/RCTSurfaceTouchHandler.h>

@implementation ANNSlotContentView {
    RCTSurfaceTouchHandler *_touchHandler;
}

- (instancetype)initWithSurface:(id<RCTSurfaceProtocol>)surface {
    if (self = [super initWithSurface:surface]) {
        // The children are outside the views of the host's surface, so they need a touch handler of their own. It
        // sits on this view, where React Native's surface view has its own, for Gesture Handler to find.
        _touchHandler = [RCTSurfaceTouchHandler new];
        [_touchHandler attachToView:self];
    }
    return self;
}

@end

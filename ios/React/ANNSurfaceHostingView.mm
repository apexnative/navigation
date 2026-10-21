#import "ANNSurfaceHostingView.h"
#import <React/RCTFabricSurface.h>

@implementation ANNSurfaceHostingView {
    // The surface was given a new size since its last mount.
    BOOL _surfaceSizeChanged;
}

// React Native 0.87 posts the hosting view's traits as the app's scheme from here (`RCTSurfaceHostingView.mm`). On
// iOS 26 the glass of a bar switches the style of the views under it, and without this filter the app's whole theme
// switched while a list scrolled under the bar. A change the window shares too passes: the window's style changed.
- (void)traitCollectionDidChange:(UITraitCollection *)previousTraitCollection {
    UIWindow *window = self.window;
    if (window &&
        self.traitCollection.userInterfaceStyle != window.traitCollection.userInterfaceStyle) {
        return;
    }
    [super traitCollectionDidChange:previousTraitCollection];
}

#pragma mark - The surface's size

// React Native gives the surface its size here. A size change is noted for the next mount (see below). The note is
// put aside during the call: the change's own commit mounts inside it and must not take an earlier note, or a size
// that changes every frame (a sheet dragged between detents, a window resized) would lay out three times a frame. A
// layout that changes nothing keeps the earlier note for the render still on its way.
- (void)layoutSubviews {
    RCTFabricSurface *surface = [self fabricSurface];
    CGSize minimumSize = surface.minimumSize;
    CGSize maximumSize = surface.maximumSize;
    BOOL sizeChangedBefore = _surfaceSizeChanged;
    _surfaceSizeChanged = NO;
    [super layoutSubviews];
    BOOL sizeChanged = surface && (!CGSizeEqualToSize(minimumSize, surface.minimumSize) ||
                                   !CGSizeEqualToSize(maximumSize, surface.maximumSize));
    _surfaceSizeChanged = sizeChanged || sizeChangedBefore;
}

// A React commit that JavaScript started before the size change lands after it with children laid out for the old
// size, and React Native keeps their layout: the root has the new size, but the children are clean (`YogaLayoutable
// ShadowNode::updateYogaChildren` checks their style only), so nothing lays them out again until the next change of
// the size (a rotation with a render on its way — a pop from a landscape screen, a JavaScript reload in landscape —
// left a tab's root laid out for landscape in portrait). At the first mount after a size change the surface lays out
// once more: a maximum one point taller and back. Both commits lay out the whole tree (a change of the constraints
// dirties the root) and give the same frames as the exact size: the root's minimum holds it at the size. They mount on
// the main queue inside these calls, so nothing in between reaches the screen.
- (void)surfaceDidMount {
    if (!_surfaceSizeChanged) {
        return;
    }
    _surfaceSizeChanged = NO;
    RCTFabricSurface *surface = [self fabricSurface];
    if (!surface) {
        return;
    }
    CGSize minimumSize = surface.minimumSize;
    CGSize maximumSize = surface.maximumSize;
    [surface setMinimumSize:minimumSize
                maximumSize:CGSizeMake(maximumSize.width, maximumSize.height + 1)];
    [surface setMinimumSize:minimumSize maximumSize:maximumSize];
}

- (nullable RCTFabricSurface *)fabricSurface {
    id surface = self.surface;
    return [surface isKindOfClass:RCTFabricSurface.class] ? surface : nil;
}

@end

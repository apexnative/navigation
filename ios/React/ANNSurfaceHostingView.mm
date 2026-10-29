#import "ANNSurfaceHostingView.h"
#import "ANNAssert.h"
#import <React/RCTFabricSurface.h>
#import <objc/runtime.h>

// How long a gone screen's surface that doesn't run yet is kept for its stop. The host starts a surface through a weak
// reference once its JavaScript ran, so a surface let go before that, which nothing else holds, goes away unstarted.
static const NSTimeInterval ANNSurfaceStartWaitLimit = 5.0;
static const void *ANNSurfaceStopperKey = &ANNSurfaceStopperKey;

// The hosting views whose surface stops at its start; each keeps its surface. Main queue.
static NSMutableSet<ANNSurfaceHostingView *> *ANNHostingViewsStoppingAtStart;

// The size transitions of the window that run now. Main queue.
static NSInteger ANNSizeTransitionCount;

// The delegate of a surface its hosting view let go at the limit: the surface keeps it, and it keeps nothing, so
// neither holds the other alive. A surface that something else still starts (a start already on its way holds its
// surface; React Native restarts every surface it knows after a reload, holding it) stops at once, instead of running
// with no screen and going away running, which leaves its React tree registered.
@interface ANNSurfaceStopper : NSObject <RCTSurfaceDelegate>
@end

@implementation ANNSurfaceStopper

// React Native's delegate method names the deprecated `RCTSurface`; the surface is an `RCTFabricSurface`.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated"
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
- (void)surface:(RCTSurface *)surface didChangeStage:(RCTSurfaceStage)stage {
    if (!RCTSurfaceStageIsRunning(stage)) {
        return;
    }
    // The start tells this off the main queue, before it ends: the stop goes on the main queue, the block keeping the
    // surface until then.
    id<RCTSurfaceProtocol> startedSurface = (id<RCTSurfaceProtocol>)surface;
    dispatch_async(dispatch_get_main_queue(), ^{
      [startedSurface stop];
    });
}
#pragma clang diagnostic pop

@end

@implementation ANNSurfaceHostingView {
    // The surface was given a new size since its last mount.
    BOOL _surfaceSizeChanged;
    // The screen went: the surface stops each time it runs. Main queue.
    BOOL _stopsWhenRunning;
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

// UIKit lays the screens out inside the animation of a rotation or a new window size, and React Native commits and
// mounts the new layout synchronously inside the layout that gives it the size: every frame of its views animated
// from the old size to the new one, and what a view draws itself (an SVG, a glass view, text) was drawn once at the new
// size and stretched along that animation. During such a transition the content takes its new layout at once; the
// hosting view itself still follows the animation. Other animated size changes (a sheet's detent, a split view's
// column) keep the content's frames animated, so content pinned to the moving edge rides along with it.
+ (void)followSizeTransition:(id<UIViewControllerTransitionCoordinator>)coordinator {
    ANNSizeTransitionCount++;
    [coordinator animateAlongsideTransition:nil
                                 completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
                                   ANNSizeTransitionCount--;
                                 }];
}

+ (void)layOutContent:(void (^)(void))layout {
    if (ANNSizeTransitionCount > 0) {
        [UIView performWithoutAnimation:layout];
    } else {
        layout();
    }
}

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
    [ANNSurfaceHostingView layOutContent:^{
      [super layoutSubviews];
    }];
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

#pragma mark - The stop

// `RCTFabricSurface stop` does nothing while the surface doesn't run, and the host starts a surface later, off the main
// queue: a screen can go before that. Its surface then stops at its change to running. This view is kept until then,
// with its surface: released earlier, it would stop the surface in its `dealloc`, maybe off the main queue.
- (void)stopSurfaceWhenRunning {
    ANNAssertMainQueue();
    if (_stopsWhenRunning) {
        return;
    }
    _stopsWhenRunning = YES;
    RCTFabricSurface *surface = [self fabricSurface];
    if (!surface) {
        return;
    }
    if (RCTSurfaceStageIsRunning(surface.stage)) {
        [surface stop];
        return;
    }
    if (!ANNHostingViewsStoppingAtStart) {
        ANNHostingViewsStoppingAtStart = [NSMutableSet new];
    }
    [ANNHostingViewsStoppingAtStart addObject:self];
    __weak ANNSurfaceHostingView *weakSelf = self;
    int64_t delay = (int64_t)(ANNSurfaceStartWaitLimit * NSEC_PER_SEC);
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
      [weakSelf letSurfaceGo];
    });
}

// The surface tells its stage off the main queue, from its start and its stop. React Native's hosting view follows it
// as before; a gone screen's surface that starts stops on the next turn of the main queue, once its start ended. The
// block keeps this view, and with it the surface, until then, so their last release is on the main queue.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated"
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
- (void)surface:(RCTSurface *)surface didChangeStage:(RCTSurfaceStage)stage {
    [super surface:surface didChangeStage:stage];
    if (!RCTSurfaceStageIsRunning(stage)) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
      [self stopSurfaceIfAsked];
    });
}
#pragma clang diagnostic pop

- (void)stopSurfaceIfAsked {
    if (!_stopsWhenRunning) {
        return;
    }
    RCTFabricSurface *surface = [self fabricSurface];
    if (surface && RCTSurfaceStageIsRunning(surface.stage)) {
        [surface stop];
    }
    [ANNHostingViewsStoppingAtStart removeObject:self];
}

// The limit: the surface hasn't started. It gets a delegate of its own that stops it at any later start, and this
// view lets it go. The delegate changes before the check: a start told before the change reaches this view, which
// stops the surface (its block keeps the view); one told after it reaches the new delegate.
- (void)letSurfaceGo {
    if (![ANNHostingViewsStoppingAtStart containsObject:self]) {
        return;
    }
    RCTFabricSurface *surface = [self fabricSurface];
    ANNSurfaceStopper *stopper = [ANNSurfaceStopper new];
    objc_setAssociatedObject(surface, ANNSurfaceStopperKey, stopper,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    surface.delegate = stopper;
    if (RCTSurfaceStageIsRunning(surface.stage)) {
        [surface stop];
    }
    [ANNHostingViewsStoppingAtStart removeObject:self];
}

- (nullable RCTFabricSurface *)fabricSurface {
    id surface = self.surface;
    return [surface isKindOfClass:RCTFabricSurface.class] ? surface : nil;
}

@end

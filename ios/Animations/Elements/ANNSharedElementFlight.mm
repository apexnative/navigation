#import "ANNSharedElementFlight.h"
#import "ANNElementFinder.h"
#import "ANNInterpolate.h"
#import "ANNSharedElementTransitionOptions.h"
#import "ANNTrack.h"
#import "ANNTransformParts.h"
#import "ANNUtils.h"
#import <React/RCTViewComponentView.h>
#import <react/renderer/components/view/ViewProps.h>
#include <cmath>

// How close two rectangles count as the same (points): a visible part that is the whole element needs no mask.
static const CGFloat ANNSameRectTolerance = 0.5;

// One end of a flight: the place, the size, the transform, the corners and the visible part of a React view, in the
// container's coordinates. The transform is the whole one the view is drawn with there (its own and its parents'),
// about its center; once split, its scale goes into the size, the corners and the visible part, so the size the copy
// shows changes evenly on the way.
typedef struct {
    CGPoint center;
    CGSize size;
    CATransform3D transform;
    // The transform's parts, which the flight interpolates; their scale is only its sign once the scale is in the size.
    ANNTransformParts parts;
    CGFloat cornerRadius;
    // The part the clipping parents leave visible, in the copy's coordinates, and its corners.
    CGRect visible;
    CGFloat visibleRadius;
    BOOL clipped;
} ANNFlightEnd;

// What the copy shows at one time: its size, center, transform and corners, its two pictures' frames and alphas, and
// its visible part (in the copy's coordinates) with its corners.
typedef struct {
    CGSize size;
    CGPoint center;
    CATransform3D transform;
    CGFloat cornerRadius;
    CGRect fromFrame;
    CGRect toFrame;
    CGFloat fromAlpha;
    CGFloat toAlpha;
    CGRect mask;
    CGFloat maskRadius;
} ANNFlightState;

static BOOL ANNIsText(UIView *view) {
    static Class paragraph;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      paragraph = NSClassFromString(@"RCTParagraphComponentView");
    });
    return paragraph && [view isKindOfClass:paragraph];
}

// The `zIndex` of the view's props, read only; 0 without one.
static NSInteger ANNZIndexOf(UIView *view) {
    if (![view isKindOfClass:RCTViewComponentView.class]) {
        return 0;
    }
    facebook::react::Props::Shared props = ((RCTViewComponentView *)view).props;
    if (!props) {
        return 0;
    }
    auto viewProps = std::static_pointer_cast<const facebook::react::ViewProps>(props);
    return viewProps->zIndex.value_or(0);
}

static BOOL ANNSameRect(CGRect a, CGRect b) {
    return fabs(a.origin.x - b.origin.x) <= ANNSameRectTolerance &&
           fabs(a.origin.y - b.origin.y) <= ANNSameRectTolerance &&
           fabs(a.size.width - b.size.width) <= ANNSameRectTolerance &&
           fabs(a.size.height - b.size.height) <= ANNSameRectTolerance;
}

// The corner radius of the view: its layer's, else that of a clipping parent the view fills.
static CGFloat ANNCornerRadiusOf(UIView *view) {
    if (view.layer.cornerRadius > 0) {
        return view.layer.cornerRadius;
    }
    UIView *child = view;
    for (UIView *parent = view.superview; parent; parent = parent.superview) {
        CGRect place = [child convertRect:child.bounds toView:parent];
        if (!ANNSameRect(place, parent.bounds)) {
            return 0;
        }
        if ((parent.clipsToBounds || parent.layer.masksToBounds) && parent.layer.cornerRadius > 0) {
            return parent.layer.cornerRadius;
        }
        child = parent;
    }
    return 0;
}

// The anchor of a layer in the coordinates of its bounds.
static CGPoint ANNAnchorInBounds(CALayer *layer) {
    CGRect bounds = layer.bounds;
    return CGPointMake(bounds.origin.x + layer.anchorPoint.x * bounds.size.width,
                       bounds.origin.y + layer.anchorPoint.y * bounds.size.height);
}

// What takes a point of the layer, counted from its anchor, to the top of its tree: its transform and place, then each
// superlayer's `sublayerTransform`, transform and place the same way (a layer's transforms turn about its anchor).
static CATransform3D ANNTransformToTop(CALayer *layer) {
    CATransform3D transform = CATransform3DConcat(
        layer.transform, CATransform3DMakeTranslation(layer.position.x, layer.position.y, 0));
    for (CALayer *parent = layer.superlayer; parent; parent = parent.superlayer) {
        CGPoint anchor = ANNAnchorInBounds(parent);
        transform = CATransform3DConcat(transform,
                                        CATransform3DMakeTranslation(-anchor.x, -anchor.y, 0));
        transform = CATransform3DConcat(transform, parent.sublayerTransform);
        transform = CATransform3DConcat(transform, parent.transform);
        transform = CATransform3DConcat(
            transform, CATransform3DMakeTranslation(parent.position.x, parent.position.y, 0));
    }
    return transform;
}

static ANNFlightEnd ANNFlightEndOf(UIView *view, UIView *screenRoot, UIView *container) {
    ANNFlightEnd end;
    CALayer *layer = view.layer;
    end.size = layer.bounds.size;
    // From the view's center to the container's bounds: up to the top, then down into the container.
    CGPoint containerAnchor = ANNAnchorInBounds(container.layer);
    CATransform3D toContainer =
        CATransform3DConcat(CATransform3DInvert(ANNTransformToTop(container.layer)),
                            CATransform3DMakeTranslation(containerAnchor.x, containerAnchor.y, 0));
    CATransform3D whole = CATransform3DConcat(ANNTransformToTop(layer), toContainer);
    // The center is where the view's center lands; the rest of the transform turns about it.
    end.center = CGPointMake(whole.m41 / whole.m44, whole.m42 / whole.m44);
    end.transform = CATransform3DConcat(
        whole, CATransform3DMakeTranslation(-end.center.x, -end.center.y, 0));
    end.cornerRadius = ANNCornerRadiusOf(view);
    // The visible part: the element's bounds cut by every parent that clips (`overflow: hidden`, a scroll view) up to
    // the screen's root, in the element's own coordinates, moved to the copy's (its bounds start at 0, 0).
    CGRect own = view.bounds;
    CGRect visible = own;
    CGFloat radius = 0;
    for (UIView *parent = view.superview; parent; parent = parent.superview) {
        if (parent.clipsToBounds || parent.layer.masksToBounds ||
            [parent isKindOfClass:UIScrollView.class]) {
            CGRect parentBounds = [parent convertRect:parent.bounds toView:view];
            CGRect cut = CGRectIntersection(visible, parentBounds);
            if (!CGRectEqualToRect(cut, visible)) {
                radius = parent.layer.cornerRadius;
            }
            visible = CGRectIsNull(cut) ? CGRectZero : cut;
        }
        if (parent == screenRoot) {
            break;
        }
    }
    end.clipped = !ANNSameRect(visible, own);
    end.visible = CGRectOffset(visible, -own.origin.x, -own.origin.y);
    end.visibleRadius = end.clipped ? radius : end.cornerRadius;
    return end;
}

static BOOL ANNIsFiniteRect(CGRect rect) {
    return isfinite(rect.origin.x) && isfinite(rect.origin.y) && isfinite(rect.size.width) &&
           isfinite(rect.size.height);
}

static BOOL ANNFlightEndIsFinite(ANNFlightEnd end) {
    const CATransform3D t = end.transform;
    const CGFloat values[] = {t.m11, t.m12, t.m13, t.m14, t.m21, t.m22, t.m23, t.m24,
                              t.m31, t.m32, t.m33, t.m34, t.m41, t.m42, t.m43, t.m44};
    for (CGFloat value : values) {
        if (!isfinite(value)) {
            return NO;
        }
    }
    return isfinite(end.center.x) && isfinite(end.center.y) && isfinite(end.size.width) &&
           isfinite(end.size.height) && isfinite(end.cornerRadius) && isfinite(end.visibleRadius) &&
           ANNIsFiniteRect(end.visible);
}

// The parts of the end's transform, its scale moved into the size, the corners and the visible part (a mirror keeps
// its sign); NO when the transform flattens the view.
static BOOL ANNSplitFlightEnd(ANNFlightEnd *end) {
    ANNTransformParts parts;
    if (!ANNTransformPartsOf(end->transform, &parts)) {
        return NO;
    }
    CGFloat scaleX = (CGFloat)std::fabs(parts.scale[0]);
    CGFloat scaleY = (CGFloat)std::fabs(parts.scale[1]);
    CGFloat scale = MIN(scaleX, scaleY);
    parts.scale[0] = std::copysign(1.0, parts.scale[0]);
    parts.scale[1] = std::copysign(1.0, parts.scale[1]);
    end->parts = parts;
    end->size = CGSizeMake(end->size.width * scaleX, end->size.height * scaleY);
    end->cornerRadius *= scale;
    end->visibleRadius *= scale;
    end->visible = CGRectMake(end->visible.origin.x * scaleX, end->visible.origin.y * scaleY,
                              end->visible.size.width * scaleX,
                              end->visible.size.height * scaleY);
    return YES;
}

// The picture of the view's layer and its sublayers at the screen's scale, in the layer's own coordinates.
static UIImage *ANNPictureOf(UIView *view) {
    CGSize size = view.bounds.size;
    if (size.width <= 0 || size.height <= 0) {
        return nil;
    }
    UIGraphicsImageRendererFormat *format = [UIGraphicsImageRendererFormat preferredFormat];
    format.scale = ANNUtils.mainScreen.scale;
    format.opaque = NO;
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:size
                                                                               format:format];
    CGPoint origin = view.bounds.origin;
    return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
      CGContextTranslateCTM(context.CGContext, -origin.x, -origin.y);
      [view.layer renderInContext:context.CGContext];
    }];
}

// A layer at `frame` in its superlayer: its bounds and its position (its anchor is its center).
static void ANNPlaceLayer(CALayer *layer, CGRect frame) {
    layer.bounds = CGRectMake(0, 0, frame.size.width, frame.size.height);
    layer.position = CGPointMake(CGRectGetMidX(frame), CGRectGetMidY(frame));
}

// The core's animation of `keyPath` on `layer` (`ANNKeyframes`), under its key.
static void ANNAddKeyframes(CALayer *layer, NSString *keyPath, NSArray *values,
                            NSArray<NSNumber *> *times, NSTimeInterval length) {
    CAKeyframeAnimation *animation = ANNKeyframes(keyPath, values, times, length);
    if (animation) {
        [layer addAnimation:animation forKey:ANNAnimationKey(keyPath)];
    }
}

// The bounds and the position of a layer at `frame`, as values of its animations.
static NSValue *ANNBoundsValue(CGRect frame) {
    return [NSValue valueWithCGRect:CGRectMake(0, 0, frame.size.width, frame.size.height)];
}

static NSValue *ANNPositionValue(CGRect frame) {
    return [NSValue valueWithCGPoint:CGPointMake(CGRectGetMidX(frame), CGRectGetMidY(frame))];
}

@implementation ANNSharedElementFlight {
    __weak UIView *_fromView;
    __weak UIView *_toView;
    BOOL _fromHidden;
    BOOL _toHidden;
    UIView *_copy;
    UIImageView *_fromPicture;
    UIImageView *_toPicture;
    // The visible part of the copy: a plain layer with corners, as Core Animation can't animate a path whose corners
    // change.
    CALayer *_mask;
    ANNFlightEnd _from;
    ANNFlightEnd _to;
    ANNTrack *_track;
    BOOL _text;
    BOOL _grows;
    BOOL _rightToLeft;
    NSInteger _zIndex;
    BOOL _restored;
}

+ (BOOL)allTargetsFound:(NSArray<ANNSharedElementTransitionOptions *> *)pairs
                  views:(ANNTransitionViews *)views {
    for (ANNSharedElementTransitionOptions *pair in pairs) {
        if (![views arrivingViewWithNativeID:ANNNativeIDOf(pair.toId)]) {
            return NO;
        }
    }
    return YES;
}

+ (NSArray<ANNSharedElementFlight *> *)
    flightsOf:(NSArray<ANNSharedElementTransitionOptions *> *)pairs
        views:(ANNTransitionViews *)views
    container:(UIView *)container {
    NSMutableArray<ANNSharedElementFlight *> *flights = [NSMutableArray new];
    NSHashTable<UIView *> *taken = [NSHashTable weakObjectsHashTable];
    for (ANNSharedElementTransitionOptions *pair in pairs) {
        NSString *fromId = ANNNativeIDOf(pair.fromId);
        NSString *toId = ANNNativeIDOf(pair.toId);
        UIView *from = [views leavingViewWithNativeID:fromId];
        UIView *to = [views arrivingViewWithNativeID:toId];
        if (!from || !to || from == to || [taken containsObject:from] ||
            [taken containsObject:to]) {
            continue;
        }
        NSString *name = [NSString stringWithFormat:@"'%@' -> '%@'", fromId, toId];
        __block ANNSharedElementFlight *flight = nil;
        ANNElementStep(name, ^{
          flight = [[self alloc] initWithPair:pair
                                         from:from
                                           to:to
                                  leavingRoot:views.leavingRoot
                                 arrivingRoot:views.arrivingRoot
                                    container:container
                                         name:name];
        });
        if (flight) {
            [taken addObject:from];
            [taken addObject:to];
            [flights addObject:flight];
        }
    }
    // The copies by the `zIndex` of their `to` views, ascending; equal ones in the order of the list.
    NSArray<ANNSharedElementFlight *> *ordered =
        [flights sortedArrayWithOptions:NSSortStable
                        usingComparator:^NSComparisonResult(ANNSharedElementFlight *a,
                                                            ANNSharedElementFlight *b) {
                          if (a->_zIndex == b->_zIndex) {
                              return NSOrderedSame;
                          }
                          return a->_zIndex < b->_zIndex ? NSOrderedAscending : NSOrderedDescending;
                        }];
    // A copy that can't take off goes away, and its pair's views show as they were.
    NSMutableArray<ANNSharedElementFlight *> *flying = [NSMutableArray new];
    for (ANNSharedElementFlight *flight in ordered) {
        if (ANNElementStep(flight.name, ^{
              [flight takeOffIn:container];
            })) {
            [flying addObject:flight];
        } else {
            [flight restore];
        }
    }
    return flying;
}

- (instancetype)initWithPair:(ANNSharedElementTransitionOptions *)pair
                        from:(UIView *)from
                          to:(UIView *)to
                 leavingRoot:(UIView *)leavingRoot
                arrivingRoot:(UIView *)arrivingRoot
                   container:(UIView *)container
                        name:(NSString *)name {
    // A view of no size gives no picture: the pair is skipped and both views stay as they are.
    if (from.bounds.size.width <= 0 || from.bounds.size.height <= 0 || to.bounds.size.width <= 0 ||
        to.bounds.size.height <= 0) {
        ANNElementSkipped(name, @"a view of the pair has no size, it isn't laid out");
        return nil;
    }
    if (!(self = [super init])) {
        return nil;
    }
    _name = [name copy];
    _fromView = from;
    _toView = to;
    _from = ANNFlightEndOf(from, leavingRoot, container);
    _to = ANNFlightEndOf(to, arrivingRoot, container);
    // A place that isn't a number would stop the copy's layer with an exception at its first frame.
    if (!ANNFlightEndIsFinite(_from) || !ANNFlightEndIsFinite(_to)) {
        ANNElementSkipped(name, @"the place of a view of the pair can't be worked out");
        return nil;
    }
    // A transform that flattens the view (a scale of 0) has no parts to fly between.
    if (!ANNSplitFlightEnd(&_from) || !ANNSplitFlightEnd(&_to)) {
        ANNElementSkipped(name, @"the transform of a view of the pair flattens it");
        return nil;
    }
    _text = ANNIsText(from) || ANNIsText(to);
    _grows = _to.size.height >= _from.size.height;
    _rightToLeft =
        to.effectiveUserInterfaceLayoutDirection == UIUserInterfaceLayoutDirectionRightToLeft;
    _zIndex = ANNZIndexOf(to);
    _track = [ANNTrack trackFrom:0
                              to:1
                  durationMillis:pair.duration
                     delayMillis:pair.startDelay
                   interpolation:pair.interpolation];
    // Both pictures before either view hides (design 34).
    UIImage *fromImage = ANNPictureOf(from);
    UIImage *toImage = ANNPictureOf(to);
    if (!fromImage || !toImage) {
        ANNElementSkipped(name, @"no picture of a view of the pair can be taken");
        return nil;
    }
    _copy = [UIView new];
    _copy.userInteractionEnabled = NO;
    // Each picture fills the frame it gets: the whole copy for a view, its own aspect for a text.
    _fromPicture = [[UIImageView alloc] initWithImage:fromImage];
    _toPicture = [[UIImageView alloc] initWithImage:toImage];
    _fromPicture.contentMode = UIViewContentModeScaleToFill;
    _toPicture.contentMode = UIViewContentModeScaleToFill;
    // A text's pictures are neither clipped nor masked.
    _copy.clipsToBounds = !_text;
    if (!_text && (_from.clipped || _to.clipped)) {
        _mask = [CALayer layer];
        _mask.backgroundColor = UIColor.blackColor.CGColor;
        _copy.layer.mask = _mask;
    }
    [_copy addSubview:_fromPicture];
    [_copy addSubview:_toPicture];
    return self;
}

// The copy goes into the container and the React views hide while it flies.
- (void)takeOffIn:(UIView *)container {
    UIView *from = _fromView;
    UIView *to = _toView;
    _fromHidden = from.hidden;
    _toHidden = to.hidden;
    [container addSubview:_copy];
    from.hidden = YES;
    to.hidden = YES;
    [self placeAtTime:0];
}

- (UIView *)copyView {
    return _copy;
}

// The copy's whole state at `time`: its own geometry, its pictures' and its visible part's.
- (ANNFlightState)stateAtTime:(NSTimeInterval)time {
    ANNFlightState state;
    double f = [_track fractionAt:time];
    state.size = ANNInterpolateSize(_from.size, _to.size, f);
    state.center = ANNInterpolatePoint(_from.center, _to.center, f);
    state.transform = ANNTransformOfParts(ANNInterpolateTransformParts(_from.parts, _to.parts, f));
    state.cornerRadius = 0;
    state.mask = CGRectZero;
    state.maskRadius = 0;
    if (_text) {
        [self placeTextPicturesOf:&state atTime:time];
        return state;
    }
    state.cornerRadius = ANNInterpolateNumber(_from.cornerRadius, _to.cornerRadius, f);
    CGRect whole = CGRectMake(0, 0, state.size.width, state.size.height);
    state.fromFrame = whole;
    state.toFrame = whole;
    state.fromAlpha = 1;
    state.toAlpha = (CGFloat)f;
    if (_mask) {
        CGRect fromWhole = CGRectMake(0, 0, _from.size.width, _from.size.height);
        CGRect toWhole = CGRectMake(0, 0, _to.size.width, _to.size.height);
        CGRect fromVisible = _from.clipped ? _from.visible : fromWhole;
        CGRect toVisible = _to.clipped ? _to.visible : toWhole;
        state.mask = ANNInterpolateRect(fromVisible, toVisible, f);
        // At most half the shorter side, as a rounded path has it.
        CGFloat radius = ANNInterpolateNumber(_from.visibleRadius, _to.visibleRadius, f);
        CGFloat half = MIN(state.mask.size.width, state.mask.size.height) / 2;
        state.maskRadius = MAX(MIN(radius, half), 0);
    }
    return state;
}

// The text's pictures keep their aspect at the height of the copy, at the line's start; they swap by the clock,
// linearly, in the first quarter of the flight when the text grows, in the last when it shrinks.
- (void)placeTextPicturesOf:(ANNFlightState *)state atTime:(NSTimeInterval)time {
    CGRect frames[2];
    UIImageView *pictures[2] = {_fromPicture, _toPicture};
    for (int index = 0; index < 2; index++) {
        CGSize imageSize = pictures[index].image.size;
        CGFloat height = state->size.height;
        CGFloat width = imageSize.height > 0 ? imageSize.width * height / imageSize.height : 0;
        CGFloat x = _rightToLeft ? state->size.width - width : 0;
        frames[index] = CGRectMake(x, 0, width, height);
    }
    state->fromFrame = frames[0];
    state->toFrame = frames[1];
    double swap = [self swapAtTime:time];
    state->fromAlpha = (CGFloat)(1 - swap);
    state->toAlpha = (CGFloat)swap;
}

// The quarter of the flight the text's pictures swap in.
- (NSTimeInterval)swapStart {
    NSTimeInterval duration = _track.duration;
    return _track.delay + (_grows ? 0 : duration - duration / 4);
}

- (double)swapAtTime:(NSTimeInterval)time {
    NSTimeInterval quarter = _track.duration / 4;
    if (quarter <= 0) {
        return time >= _track.delay ? 1 : 0;
    }
    return MIN(MAX((time - [self swapStart]) / quarter, 0.0), 1.0);
}

// The copy shows its state at `time` (its own values, with no animation).
- (void)placeAtTime:(NSTimeInterval)time {
    ANNFlightState state = [self stateAtTime:time];
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    _copy.layer.transform = CATransform3DIdentity;
    _copy.bounds = CGRectMake(0, 0, state.size.width, state.size.height);
    _copy.center = state.center;
    _copy.layer.transform = state.transform;
    if (!_text) {
        _copy.layer.cornerRadius = state.cornerRadius;
    }
    ANNPlaceLayer(_fromPicture.layer, state.fromFrame);
    ANNPlaceLayer(_toPicture.layer, state.toFrame);
    _fromPicture.alpha = state.fromAlpha;
    _toPicture.alpha = state.toAlpha;
    if (_mask) {
        ANNPlaceLayer(_mask, state.mask);
        _mask.cornerRadius = state.maskRadius;
    }
    [CATransaction commit];
}

#pragma mark - ANNFrameClient

- (NSTimeInterval)endTime {
    return _track.endTime;
}

- (void)addAnimationsWithLength:(NSTimeInterval)length {
    if (_restored) {
        return;
    }
    NSMutableArray<NSNumber *> *marks =
        [NSMutableArray arrayWithObjects:@(_track.delay), @(_track.endTime), nil];
    if (_text) {
        [marks addObject:@([self swapStart])];
        [marks addObject:@([self swapStart] + _track.duration / 4)];
    }
    NSArray<NSNumber *> *times = ANNSampleTimes(_track.endTime, length, marks);
    NSMutableArray *bounds = [NSMutableArray new];
    NSMutableArray *positions = [NSMutableArray new];
    NSMutableArray *transforms = [NSMutableArray new];
    NSMutableArray *radii = [NSMutableArray new];
    NSMutableArray *fromBounds = [NSMutableArray new];
    NSMutableArray *fromPositions = [NSMutableArray new];
    NSMutableArray *fromAlphas = [NSMutableArray new];
    NSMutableArray *toBounds = [NSMutableArray new];
    NSMutableArray *toPositions = [NSMutableArray new];
    NSMutableArray *toAlphas = [NSMutableArray new];
    NSMutableArray *maskBounds = [NSMutableArray new];
    NSMutableArray *maskPositions = [NSMutableArray new];
    NSMutableArray *maskRadii = [NSMutableArray new];
    for (NSNumber *time in times) {
        ANNFlightState state = [self stateAtTime:time.doubleValue];
        CGRect whole = CGRectMake(0, 0, state.size.width, state.size.height);
        [bounds addObject:ANNBoundsValue(whole)];
        [positions addObject:[NSValue valueWithCGPoint:state.center]];
        [transforms addObject:[NSValue valueWithCATransform3D:state.transform]];
        [radii addObject:@(state.cornerRadius)];
        [fromBounds addObject:ANNBoundsValue(state.fromFrame)];
        [fromPositions addObject:ANNPositionValue(state.fromFrame)];
        [fromAlphas addObject:@(state.fromAlpha)];
        [toBounds addObject:ANNBoundsValue(state.toFrame)];
        [toPositions addObject:ANNPositionValue(state.toFrame)];
        [toAlphas addObject:@(state.toAlpha)];
        [maskBounds addObject:ANNBoundsValue(state.mask)];
        [maskPositions addObject:ANNPositionValue(state.mask)];
        [maskRadii addObject:@(state.maskRadius)];
    }
    CALayer *copy = _copy.layer;
    ANNAddKeyframes(copy, @"bounds", bounds, times, length);
    ANNAddKeyframes(copy, @"position", positions, times, length);
    ANNAddKeyframes(copy, @"transform", transforms, times, length);
    if (!_text) {
        ANNAddKeyframes(copy, @"cornerRadius", radii, times, length);
    }
    ANNAddKeyframes(_fromPicture.layer, @"bounds", fromBounds, times, length);
    ANNAddKeyframes(_fromPicture.layer, @"position", fromPositions, times, length);
    ANNAddKeyframes(_fromPicture.layer, @"opacity", fromAlphas, times, length);
    ANNAddKeyframes(_toPicture.layer, @"bounds", toBounds, times, length);
    ANNAddKeyframes(_toPicture.layer, @"position", toPositions, times, length);
    ANNAddKeyframes(_toPicture.layer, @"opacity", toAlphas, times, length);
    if (_mask) {
        ANNAddKeyframes(_mask, @"bounds", maskBounds, times, length);
        ANNAddKeyframes(_mask, @"position", maskPositions, times, length);
        ANNAddKeyframes(_mask, @"cornerRadius", maskRadii, times, length);
    }
}

- (void)restore {
    if (_restored) {
        return;
    }
    _restored = YES;
    [_copy removeFromSuperview];
    _fromView.hidden = _fromHidden;
    _toView.hidden = _toHidden;
}

@end

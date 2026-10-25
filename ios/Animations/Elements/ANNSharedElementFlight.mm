#import "ANNSharedElementFlight.h"
#import "ANNElementFinder.h"
#import "ANNInterpolate.h"
#import "ANNSharedElementTransitionOptions.h"
#import "ANNTrack.h"
#import "ANNTransformParts.h"
#import "ANNUtils.h"
#import <React/RCTViewComponentView.h>
#import <react/renderer/components/view/ViewProps.h>

// How close two rectangles count as the same (points): a visible part that is the whole element needs no mask.
static const CGFloat ANNSameRectTolerance = 0.5;

// One end of a flight: the place, the size, the transform, the corners and the visible part of a React view, in the
// container's coordinates.
typedef struct {
    CGPoint center;
    CGSize size;
    CATransform3D transform;
    // The transform's parts, which the flight interpolates.
    ANNTransformParts parts;
    CGFloat cornerRadius;
    // The part the clipping parents leave visible, in the copy's coordinates, and its corners.
    CGRect visible;
    CGFloat visibleRadius;
    BOOL clipped;
} ANNFlightEnd;

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

static ANNFlightEnd ANNFlightEndOf(UIView *view, UIView *screenRoot, UIView *container) {
    ANNFlightEnd end;
    CALayer *layer = view.layer;
    end.size = layer.bounds.size;
    end.center = [view.superview convertPoint:layer.position toView:container];
    end.transform = layer.transform;
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

@implementation ANNSharedElementFlight {
    __weak UIView *_fromView;
    __weak UIView *_toView;
    BOOL _fromHidden;
    BOOL _toHidden;
    UIView *_copy;
    UIImageView *_fromPicture;
    UIImageView *_toPicture;
    CAShapeLayer *_mask;
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
    if (!ANNTransformPartsOf(_from.transform, &_from.parts) ||
        !ANNTransformPartsOf(_to.transform, &_to.parts)) {
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
        _mask = [CAShapeLayer layer];
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
    [self applyAtTime:0];
}

- (UIView *)copyView {
    return _copy;
}

#pragma mark - ANNFrameClient

- (NSTimeInterval)endTime {
    return _track.endTime;
}

- (void)applyAtTime:(NSTimeInterval)time {
    if (_restored) {
        return;
    }
    double f = [_track fractionAt:time];
    CGSize size = ANNInterpolateSize(_from.size, _to.size, f);
    _copy.layer.transform = CATransform3DIdentity;
    _copy.bounds = CGRectMake(0, 0, size.width, size.height);
    _copy.center = ANNInterpolatePoint(_from.center, _to.center, f);
    _copy.layer.transform =
        ANNTransformOfParts(ANNInterpolateTransformParts(_from.parts, _to.parts, f));
    if (_text) {
        [self placeTextPicturesAtTime:time size:size];
        return;
    }
    _copy.layer.cornerRadius = ANNInterpolateNumber(_from.cornerRadius, _to.cornerRadius, f);
    _fromPicture.frame = _copy.bounds;
    _toPicture.frame = _copy.bounds;
    _fromPicture.alpha = 1;
    _toPicture.alpha = (CGFloat)f;
    if (_mask) {
        CGRect fromWhole = CGRectMake(0, 0, _from.size.width, _from.size.height);
        CGRect toWhole = CGRectMake(0, 0, _to.size.width, _to.size.height);
        CGRect fromVisible = _from.clipped ? _from.visible : fromWhole;
        CGRect toVisible = _to.clipped ? _to.visible : toWhole;
        CGRect visible = ANNInterpolateRect(fromVisible, toVisible, f);
        CGFloat radius = ANNInterpolateNumber(_from.visibleRadius, _to.visibleRadius, f);
        _mask.frame = _copy.bounds;
        _mask.path = [UIBezierPath bezierPathWithRoundedRect:visible cornerRadius:radius].CGPath;
    }
}

// The text's pictures keep their aspect at the height of the copy, at the line's start; they swap by the clock,
// linearly, in the first quarter of the flight when the text grows, in the last when it shrinks.
- (void)placeTextPicturesAtTime:(NSTimeInterval)time size:(CGSize)size {
    for (UIImageView *picture in @[ _fromPicture, _toPicture ]) {
        CGSize imageSize = picture.image.size;
        CGFloat height = size.height;
        CGFloat width = imageSize.height > 0 ? imageSize.width * height / imageSize.height : 0;
        CGFloat x = _rightToLeft ? size.width - width : 0;
        picture.frame = CGRectMake(x, 0, width, height);
    }
    NSTimeInterval duration = _track.duration;
    NSTimeInterval quarter = duration / 4;
    NSTimeInterval start = _track.delay + (_grows ? 0 : duration - quarter);
    double swap;
    if (quarter <= 0) {
        swap = time >= _track.delay ? 1 : 0;
    } else {
        swap = MIN(MAX((time - start) / quarter, 0.0), 1.0);
    }
    _fromPicture.alpha = (CGFloat)(1 - swap);
    _toPicture.alpha = (CGFloat)swap;
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

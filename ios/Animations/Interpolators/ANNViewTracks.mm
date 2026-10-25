#import "ANNViewTracks.h"
#import "ANNAnimationPropertyOptions.h"
#import "ANNElementTransitionOptions.h"
#import "ANNTrack.h"
#import "ANNTransformParts.h"
#import "ANNUtils.h"
#import "ANNViewAnimationOptions.h"
#import <QuartzCore/QuartzCore.h>

CGFloat ANNCameraDistance(void) {
    CGFloat scale = ANNUtils.mainScreen.scale;
    if (scale <= 0) {
        scale = 1;
    }
    return 1280.0 / (scale * sqrt(5.0));
}

// The properties of a side, in the order of `animatedProperties`.
typedef NS_ENUM(NSInteger, ANNViewProperty) {
    ANNViewPropertyX,
    ANNViewPropertyY,
    ANNViewPropertyTranslationX,
    ANNViewPropertyTranslationY,
    ANNViewPropertyAlpha,
    ANNViewPropertyScaleX,
    ANNViewPropertyScaleY,
    ANNViewPropertyRotationX,
    ANNViewPropertyRotationY,
    ANNViewPropertyRotation,
    ANNViewPropertyCount,
};

static CGFloat ANNRadians(double degrees) {
    return (CGFloat)(degrees * M_PI / 180.0);
}

// The rotations as React Native's transforms make them (its matrices, row by row as `CATransform3D`): the same picture
// as Android's `rotationX`, `rotationY`, `rotation` with the same degrees.
static CATransform3D ANNRotationX(CGFloat radians) {
    CATransform3D t = CATransform3DIdentity;
    t.m22 = cos(radians);
    t.m23 = sin(radians);
    t.m32 = -sin(radians);
    t.m33 = cos(radians);
    return t;
}

static CATransform3D ANNRotationY(CGFloat radians) {
    CATransform3D t = CATransform3DIdentity;
    t.m11 = cos(radians);
    t.m13 = -sin(radians);
    t.m31 = sin(radians);
    t.m33 = cos(radians);
    return t;
}

static CATransform3D ANNRotationZ(CGFloat radians) {
    CATransform3D t = CATransform3DIdentity;
    t.m11 = cos(radians);
    t.m12 = sin(radians);
    t.m21 = -sin(radians);
    t.m22 = cos(radians);
    return t;
}

// A track of one property, or nil when it doesn't play (neither `from` nor `to`): the missing end is the view's value.
static ANNTrack *ANNTrackOf(Double *from, Double *to, Double *duration, Double *delay,
                            ANNInterpolationOptions *interpolation, double viewValue) {
    if (!from.hasValue && !to.hasValue) {
        return nil;
    }
    return [ANNTrack trackFrom:from.hasValue ? [from get] : viewValue
                            to:to.hasValue ? [to get] : viewValue
                durationMillis:duration
                   delayMillis:delay
                 interpolation:interpolation];
}

@implementation ANNViewTracks {
    ANNTrack *_tracks[ANNViewPropertyCount];
    // The view's place in its parent when the tracks were made (`x₀`, `y₀`).
    CGPoint _origin;
    CGFloat _alphaBefore;
    CATransform3D _transformBefore;
    // The parts of the view's own transform (its React Native `transform`): what a property that doesn't play keeps,
    // and the missing end of one that does, as Android keeps a view's own properties.
    CGPoint _ownTranslation;
    CGPoint _ownScale;
    double _ownRotationX;
    double _ownRotationY;
    double _ownRotation;
    CATransform3D _ownSkew;
    CGFloat _ownPerspective;
    BOOL _animatesTransform;
    BOOL _tilts;
    BOOL _wroteAlpha;
    BOOL _wroteTransform;
    BOOL _restored;
    NSTimeInterval _endTime;
}

- (instancetype)initWithView:(UIView *)view
                        side:(ANNViewAnimationOptions *)side
                 alphaBefore:(CGFloat)alphaBefore {
    if (self = [self initWithView:view alphaBefore:alphaBefore]) {
        NSArray<ANNAnimationPropertyOptions *> *properties = side.animatedProperties;
        NSInteger count = MIN((NSInteger)ANNViewPropertyCount, (NSInteger)properties.count);
        for (NSInteger index = 0; index < count; index++) {
            ANNAnimationPropertyOptions *property = properties[index];
            _tracks[index] = ANNTrackOf(property.from, property.to, property.duration,
                                        property.startDelay, property.interpolation,
                                        [self viewValueOf:(ANNViewProperty)index]);
        }
        [self tracksMade];
    }
    return self;
}

- (instancetype)initWithView:(UIView *)view element:(ANNElementTransitionOptions *)element {
    if (self = [self initWithView:view alphaBefore:view.alpha]) {
        NSArray<ANNElementAnimationOptions *> *properties = element.animatedProperties;
        NSInteger count = MIN((NSInteger)ANNViewPropertyCount, (NSInteger)properties.count);
        for (NSInteger index = 0; index < count; index++) {
            ANNElementAnimationOptions *property = properties[index];
            _tracks[index] = ANNTrackOf(property.from, property.to, property.duration,
                                        property.startDelay, property.interpolation,
                                        [self viewValueOf:(ANNViewProperty)index]);
        }
        [self tracksMade];
    }
    return self;
}

- (instancetype)initWithView:(UIView *)view
                    fadeFrom:(CGFloat)from
                          to:(CGFloat)to
                    duration:(NSTimeInterval)duration
                 alphaBefore:(CGFloat)alphaBefore {
    if (self = [self initWithView:view alphaBefore:alphaBefore]) {
        _tracks[ANNViewPropertyAlpha] = [[ANNTrack alloc] initWithFrom:from
                                                                    to:to
                                                              duration:duration
                                                                 delay:0
                                                                 curve:[ANNCurve linear]];
        [self tracksMade];
    }
    return self;
}

- (instancetype)initWithView:(UIView *)view alphaBefore:(CGFloat)alphaBefore {
    if (self = [super init]) {
        _view = view;
        CALayer *layer = view.layer;
        CGRect bounds = layer.bounds;
        _origin = CGPointMake(layer.position.x - bounds.size.width * layer.anchorPoint.x,
                              layer.position.y - bounds.size.height * layer.anchorPoint.y);
        _alphaBefore = alphaBefore;
        _transformBefore = layer.transform;
        [self readOwnTransform];
    }
    return self;
}

// A transform that can't be split (a scale of 0) counts as none, as before the view's own transform was kept.
- (void)readOwnTransform {
    _ownScale = CGPointMake(1, 1);
    _ownSkew = CATransform3DIdentity;
    ANNTransformParts parts;
    if (!ANNTransformPartsOf(_transformBefore, &parts)) {
        return;
    }
    _ownTranslation = CGPointMake((CGFloat)parts.translate[0], (CGFloat)parts.translate[1]);
    _ownScale = CGPointMake((CGFloat)parts.scale[0], (CGFloat)parts.scale[1]);
    ANNRotationDegreesOf(parts, &_ownRotationX, &_ownRotationY, &_ownRotation);
    _ownSkew = ANNSkewTransformOf(parts);
    _ownPerspective = (CGFloat)parts.perspective[2];
}

- (double)viewValueOf:(ANNViewProperty)property {
    switch (property) {
        case ANNViewPropertyX:
            return _origin.x + _ownTranslation.x;
        case ANNViewPropertyY:
            return _origin.y + _ownTranslation.y;
        case ANNViewPropertyTranslationX:
            return _ownTranslation.x;
        case ANNViewPropertyTranslationY:
            return _ownTranslation.y;
        case ANNViewPropertyAlpha:
            return _alphaBefore;
        case ANNViewPropertyScaleX:
            return _ownScale.x;
        case ANNViewPropertyScaleY:
            return _ownScale.y;
        case ANNViewPropertyRotationX:
            return _ownRotationX;
        case ANNViewPropertyRotationY:
            return _ownRotationY;
        case ANNViewPropertyRotation:
            return _ownRotation;
        default:
            return 0;
    }
}

- (void)tracksMade {
    _endTime = 0;
    for (NSInteger index = 0; index < ANNViewPropertyCount; index++) {
        ANNTrack *track = _tracks[index];
        if (!track) {
            continue;
        }
        _endTime = MAX(_endTime, track.endTime);
        if (index != ANNViewPropertyAlpha) {
            _animatesTransform = YES;
        }
    }
    _tilts = _tracks[ANNViewPropertyRotationX] || _tracks[ANNViewPropertyRotationY];
}

- (BOOL)hasTracks {
    return _animatesTransform || self.animatesAlpha;
}

- (BOOL)animatesAlpha {
    return _tracks[ANNViewPropertyAlpha] != nil;
}

- (NSTimeInterval)endTime {
    return _endTime;
}

- (double)valueOf:(ANNViewProperty)property at:(NSTimeInterval)time {
    ANNTrack *track = _tracks[property];
    return track ? [track valueAt:time] : [self viewValueOf:property];
}

- (void)applyAtTime:(NSTimeInterval)time {
    if (_restored) {
        return;
    }
    if (self.animatesAlpha) {
        _view.alpha = (CGFloat)[_tracks[ANNViewPropertyAlpha] valueAt:time];
        _wroteAlpha = YES;
    }
    if (!_animatesTransform) {
        return;
    }
    CGFloat scaleX = (CGFloat)[self valueOf:ANNViewPropertyScaleX at:time];
    CGFloat scaleY = (CGFloat)[self valueOf:ANNViewPropertyScaleY at:time];
    // The translation wins over the place when a side has both.
    CGFloat translationX =
        _tracks[ANNViewPropertyTranslationX] || !_tracks[ANNViewPropertyX]
            ? (CGFloat)[self valueOf:ANNViewPropertyTranslationX at:time]
            : (CGFloat)([self valueOf:ANNViewPropertyX at:time] - _origin.x);
    CGFloat translationY =
        _tracks[ANNViewPropertyTranslationY] || !_tracks[ANNViewPropertyY]
            ? (CGFloat)[self valueOf:ANNViewPropertyTranslationY at:time]
            : (CGFloat)([self valueOf:ANNViewPropertyY at:time] - _origin.y);
    // `CATransform3D` multiplies row vectors: each step below comes after the ones before it.
    CATransform3D transform =
        CATransform3DConcat(CATransform3DMakeScale(scaleX, scaleY, 1), _ownSkew);
    if (_tracks[ANNViewPropertyRotationX] || _ownRotationX != 0) {
        CGFloat radians = ANNRadians([self valueOf:ANNViewPropertyRotationX at:time]);
        transform = CATransform3DConcat(transform, ANNRotationX(radians));
    }
    if (_tracks[ANNViewPropertyRotationY] || _ownRotationY != 0) {
        CGFloat radians = ANNRadians([self valueOf:ANNViewPropertyRotationY at:time]);
        transform = CATransform3DConcat(transform, ANNRotationY(radians));
    }
    if (_tracks[ANNViewPropertyRotation] || _ownRotation != 0) {
        CGFloat radians = ANNRadians([self valueOf:ANNViewPropertyRotation at:time]);
        transform = CATransform3DConcat(transform, ANNRotationZ(radians));
    }
    // A tilt that plays gets the camera's perspective unless the view has its own; a view's own tilt keeps the
    // perspective it had, none without one, as React Native draws it.
    if (_tilts || _ownPerspective != 0) {
        CATransform3D perspective = CATransform3DIdentity;
        perspective.m34 = _ownPerspective != 0 ? _ownPerspective : -1.0 / ANNCameraDistance();
        transform = CATransform3DConcat(transform, perspective);
    }
    transform = CATransform3DConcat(transform,
                                    CATransform3DMakeTranslation(translationX, translationY, 0));
    _view.layer.transform = transform;
    _wroteTransform = YES;
}

- (void)restore {
    if (_restored) {
        return;
    }
    _restored = YES;
    if (_wroteAlpha) {
        _view.alpha = _alphaBefore;
    }
    if (_wroteTransform) {
        _view.layer.transform = _transformBefore;
    }
}

@end

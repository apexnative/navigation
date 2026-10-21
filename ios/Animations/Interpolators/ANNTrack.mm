#import "ANNTrack.h"
#import "ANNAnimationRules.h"
#import "ANNInterpolationOptions.h"

NSTimeInterval ANNSecondsOf(Double *millis, NSTimeInterval fallback) {
    if (!millis.hasValue) {
        return fallback;
    }
    double value = [millis get];
    return value >= 0 && isfinite(value) ? value / 1000.0 : fallback;
}

@implementation ANNTrack {
    ANNCurve *_curve;
}

- (instancetype)initWithFrom:(double)from
                          to:(double)to
                    duration:(NSTimeInterval)duration
                       delay:(NSTimeInterval)delay
                       curve:(ANNCurve *)curve {
    if (self = [super init]) {
        _from = from;
        _to = to;
        _duration = duration > 0 ? duration : 0;
        _delay = delay > 0 ? delay : 0;
        _curve = curve;
    }
    return self;
}

+ (instancetype)trackFrom:(double)from
                       to:(double)to
           durationMillis:(Double *)duration
              delayMillis:(Double *)delay
            interpolation:(ANNInterpolationOptions *)interpolation {
    NSTimeInterval seconds = ANNSecondsOf(duration, ANNAnimationDefaultDuration);
    return [[self alloc] initWithFrom:from
                                   to:to
                             duration:seconds
                                delay:ANNSecondsOf(delay, 0)
                                curve:[ANNCurve curveWithOptions:interpolation duration:seconds]];
}

- (NSTimeInterval)endTime {
    return _delay + _duration;
}

- (double)fractionAt:(NSTimeInterval)time {
    if (time < _delay) {
        return 0;
    }
    if (_duration <= 0 || time >= _delay + _duration) {
        return 1;
    }
    double p = (time - _delay) / _duration;
    if (p <= 0) {
        return 0;
    }
    return [_curve valueAt:p];
}

- (double)valueAt:(NSTimeInterval)time {
    if (time < _delay) {
        return _from;
    }
    if (_duration <= 0 || time >= _delay + _duration) {
        return _to;
    }
    double p = (time - _delay) / _duration;
    if (p <= 0) {
        return _from;
    }
    return _from + [_curve valueAt:p] * (_to - _from);
}

@end

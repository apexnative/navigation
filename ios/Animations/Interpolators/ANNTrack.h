#import "ANNCurve.h"
#import "Double.h"
#import <Foundation/Foundation.h>

@class ANNInterpolationOptions;

NS_ASSUME_NONNULL_BEGIN

/// The timeline of one animated value: its own duration, delay and curve. The progress is p = (t − delay) / duration;
/// during the delay the value holds `from`, from the end on it holds exactly `to`, and a duration of 0 is `to` once the
/// delay passed (design 19). Times in seconds from the start of the transition. Pure, immutable.
@interface ANNTrack : NSObject

- (instancetype)initWithFrom:(double)from
                          to:(double)to
                    duration:(NSTimeInterval)duration
                       delay:(NSTimeInterval)delay
                       curve:(ANNCurve *)curve NS_DESIGNATED_INITIALIZER;

/// A track of the timing options of the core (`duration`, `startDelay` in milliseconds; 300 and 0 when unset) between
/// `from` and `to`.
+ (instancetype)trackFrom:(double)from
                       to:(double)to
           durationMillis:(nullable Double *)duration
              delayMillis:(nullable Double *)delay
            interpolation:(nullable ANNInterpolationOptions *)interpolation;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@property(nonatomic, readonly) double from;
@property(nonatomic, readonly) double to;
@property(nonatomic, readonly) NSTimeInterval duration;
@property(nonatomic, readonly) NSTimeInterval delay;

/// The time the track holds its end from: delay + duration.
@property(nonatomic, readonly) NSTimeInterval endTime;

/// The share of the way at `time`: exactly 0 before and at the start, exactly 1 from the end on, the curve between.
- (double)fractionAt:(NSTimeInterval)time;

/// The value at `time`: `from` + f(p)·(`to` − `from`), exactly `from` before the start and exactly `to` from the end
/// on.
- (double)valueAt:(NSTimeInterval)time;

@end

/// A duration or a delay of the options in seconds: milliseconds, `fallback` when unset (the reader keeps only finite
/// values not below 0).
FOUNDATION_EXPORT NSTimeInterval ANNSecondsOf(Double *_Nullable millis, NSTimeInterval fallback);

NS_ASSUME_NONNULL_END

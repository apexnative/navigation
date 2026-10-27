#import "ANNFrameDriver.h"
#import "ANNAssert.h"
#import "ANNCurve.h"
#import "ANNTransformParts.h"
#include <cmath>

// The samples per second of a client's values: a frame of a 120 Hz display each.
static const double ANNSamplesPerSecond = 120;

// At most this many samples per client: a longer transition samples less often.
static const NSUInteger ANNMaxSamples = 2400;

NSArray<NSNumber *> *ANNSampleTimes(NSTimeInterval end, NSTimeInterval length,
                                    NSArray<NSNumber *> *marks) {
    end = MIN(MAX(end, 0), length);
    NSMutableArray<NSNumber *> *candidates = [NSMutableArray arrayWithObject:@0];
    double step = MAX(1 / ANNSamplesPerSecond, end / ANNMaxSamples);
    NSUInteger count = (NSUInteger)std::floor(end / step);
    for (NSUInteger index = 1; index <= count; index++) {
        [candidates addObject:@(index * step)];
    }
    for (NSNumber *mark in marks) {
        double time = mark.doubleValue;
        if (std::isfinite(time) && time > 0 && time < end) {
            [candidates addObject:mark];
        }
    }
    [candidates addObject:@(end)];
    [candidates addObject:@(length)];
    [candidates sortUsingSelector:@selector(compare:)];
    NSMutableArray<NSNumber *> *times = [NSMutableArray arrayWithCapacity:candidates.count];
    for (NSNumber *time in candidates) {
        // Samples closer than a microsecond are one.
        if (times.count == 0 || time.doubleValue - times.lastObject.doubleValue > 1e-6) {
            [times addObject:time];
        }
    }
    return times;
}

CAKeyframeAnimation *ANNKeyframes(NSString *keyPath, NSArray *values, NSArray<NSNumber *> *times,
                                  NSTimeInterval length) {
    if (length <= 0 || values.count == 0 || values.count != times.count) {
        return nil;
    }
    NSMutableArray<NSNumber *> *keyTimes = [NSMutableArray arrayWithCapacity:times.count];
    for (NSNumber *time in times) {
        [keyTimes addObject:@(MIN(MAX(time.doubleValue / length, 0.0), 1.0))];
    }
    CAKeyframeAnimation *animation = [CAKeyframeAnimation animationWithKeyPath:keyPath];
    animation.values = values;
    animation.keyTimes = keyTimes;
    animation.calculationMode = kCAAnimationLinear;
    animation.duration = length;
    animation.fillMode = kCAFillModeBoth;
    animation.removedOnCompletion = NO;
    return animation;
}

NSString *ANNAnimationKey(NSString *keyPath) {
    return [@"ApexNavigation." stringByAppendingString:keyPath];
}

@implementation ANNFrameDriver {
    NSArray<id<ANNFrameClient>> *_clients;
    NSTimeInterval _length;
    dispatch_block_t _done;
    BOOL _running;
    BOOL _starting;
    // When the animations were added; they start at the commit of the current transaction, a moment later.
    CFTimeInterval _start;
    // The driver keeps itself while its animations run: the transition that started it needn't.
    ANNFrameDriver *_keep;
    // A transition that follows a gesture: the layer whose time stops, its timing before, the clock's time the
    // animations started at, the `done` that tells how it ended, and whether its end plays.
    CALayer *_clock;
    float _clockSpeed;
    CFTimeInterval _clockBegin;
    CFTimeInterval _clockOffset;
    CFTimeInterval _base;
    void (^_pausedDone)(BOOL finished);
    BOOL _ending;
}

- (instancetype)initWithClients:(NSArray<id<ANNFrameClient>> *)clients
                         length:(NSTimeInterval)length {
    if (self = [super init]) {
        _clients = [clients copy];
        NSTimeInterval end = length > 0 ? length : 0;
        for (id<ANNFrameClient> client in _clients) {
            end = MAX(end, client.endTime);
        }
        _length = end;
    }
    return self;
}

- (BOOL)isRunning {
    return _running;
}

- (NSTimeInterval)length {
    return _length;
}

- (void)startWithDone:(dispatch_block_t)done {
    ANNAssertMainQueue();
#if DEBUG
    ANNCurveSelfCheck();
    ANNTransformPartsSelfCheck();
#endif
    if (_running || _done || _pausedDone) {
        return;
    }
    _done = [done copy];
    _running = YES;
    _keep = self;
    _starting = YES;
    _start = CACurrentMediaTime();
    __weak ANNFrameDriver *weakSelf = self;
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    // Called once every animation added in this transaction ended or was removed (with its layer too), and at once
    // without any.
    [CATransaction setCompletionBlock:^{
      [weakSelf animationsEnded];
    }];
    @try {
        for (id<ANNFrameClient> client in _clients) {
            [client addAnimationsWithLength:_length];
        }
    } @finally {
        [CATransaction commit];
        _starting = NO;
    }
}

- (void)animationsEnded {
    if (!_running) {
        return;
    }
    // `done` never comes inside `startWithDone:`, nor before the length passed: a transition with nothing to play (no
    // client, every element skipped) or whose animations went early still takes its time, as on Android.
    __weak ANNFrameDriver *weakSelf = self;
    NSTimeInterval left = _length - (CACurrentMediaTime() - _start);
    if (_starting || left > 0) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(MAX(left, 0) * NSEC_PER_SEC)),
                       dispatch_get_main_queue(), ^{
                         [weakSelf animationsEnded];
                       });
        return;
    }
    _running = NO;
    dispatch_block_t done = _done;
    _done = nil;
    _keep = nil;
    if (done) {
        done();
    }
}

- (void)cancel {
    ANNAssertMainQueue();
    if (!_running) {
        return;
    }
    _running = NO;
    _done = nil;
    _pausedDone = nil;
    _keep = nil;
    [self releaseClock];
}

#pragma mark - Following a gesture

- (void)startPausedOn:(CALayer *)clock done:(void (^)(BOOL finished))done {
    ANNAssertMainQueue();
    if (_running || _done || _pausedDone) {
        return;
    }
    _pausedDone = [done copy];
    _running = YES;
    _keep = self;
    _clock = clock;
    _clockSpeed = clock.speed;
    _clockBegin = clock.beginTime;
    _clockOffset = clock.timeOffset;
    // The clock stops at its time now: what else animates under it holds still, and the animations added now start
    // there (Core Animation gives an animation without a begin time the layer's time at the commit).
    _base = [clock convertTime:CACurrentMediaTime() fromLayer:nil];
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    clock.speed = 0;
    clock.beginTime = 0;
    clock.timeOffset = _base;
    @try {
        for (id<ANNFrameClient> client in _clients) {
            [client addAnimationsWithLength:_length];
        }
    } @finally {
        [CATransaction commit];
    }
}

- (void)setProgress:(CGFloat)progress {
    ANNAssertMainQueue();
    if (!_running || !_pausedDone || _ending) {
        return;
    }
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    _clock.timeOffset = _base + MIN(MAX(progress, 0.0), 1.0) * _length;
    [CATransaction commit];
}

- (void)finishWithSpeed:(CGFloat)speed {
    [self playOnForward:YES speed:speed];
}

- (void)rewindWithSpeed:(CGFloat)speed {
    [self playOnForward:NO speed:speed];
}

// The clock runs again from where it shows, forward or back, and `done` comes once it reached the end or the start.
- (void)playOnForward:(BOOL)forward speed:(CGFloat)speed {
    ANNAssertMainQueue();
    if (!_running || !_pausedDone || _ending) {
        return;
    }
    _ending = YES;
    speed = MAX(speed, 1.0);
    CALayer *clock = _clock;
    CFTimeInterval at = clock.timeOffset - _base;
    CFTimeInterval left = forward ? _length - at : at;
    CALayer *parent = clock.superlayer;
    CFTimeInterval now = parent ? [parent convertTime:CACurrentMediaTime() fromLayer:nil]
                                : CACurrentMediaTime();
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    // The clock's time is (parent's time - beginTime) * speed + timeOffset: it goes on from where it shows.
    clock.beginTime = now;
    clock.speed = (float)(forward ? speed : -speed);
    [CATransaction commit];
    __weak ANNFrameDriver *weakSelf = self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(MAX(left / speed, 0) * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     [weakSelf pausedEndedForward:forward];
                   });
}

- (void)pausedEndedForward:(BOOL)forward {
    if (!_running || !_pausedDone) {
        return;
    }
    _running = NO;
    void (^done)(BOOL) = _pausedDone;
    _pausedDone = nil;
    _keep = nil;
    // The clock's time back and the caller's restore in one commit: between two, the animations would show at the
    // clock's own time for a frame (past their end, or part way).
    [CATransaction begin];
    [self releaseClock];
    done(forward);
    [CATransaction commit];
}

// The clock gets back the timing it had before the gesture.
- (void)releaseClock {
    CALayer *clock = _clock;
    _clock = nil;
    if (!clock) {
        return;
    }
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    clock.speed = _clockSpeed;
    clock.beginTime = _clockBegin;
    clock.timeOffset = _clockOffset;
    [CATransaction commit];
}

@end

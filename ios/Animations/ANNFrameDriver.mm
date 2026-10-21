#import "ANNFrameDriver.h"
#import "ANNAssert.h"
#import "ANNCurve.h"
#import <QuartzCore/QuartzCore.h>

// The display link holds its target strongly and the run loop holds the link: the driver is reached weakly, so a driver
// nobody keeps any more stops at its next frame.
@interface ANNFrameTarget : NSObject
@property(nonatomic, weak) id owner;
@end

@interface ANNFrameDriver ()
- (void)frame:(CADisplayLink *)link;
@end

@implementation ANNFrameTarget

- (void)frame:(CADisplayLink *)link {
    ANNFrameDriver *driver = _owner;
    if (!driver) {
        [link invalidate];
        return;
    }
    [driver frame:link];
}

@end

@implementation ANNFrameDriver {
    NSArray<id<ANNFrameClient>> *_clients;
    NSTimeInterval _length;
    CADisplayLink *_link;
    CFTimeInterval _start;
    dispatch_block_t _done;
    // The driver keeps itself while its frames run: the transition that started it needn't.
    ANNFrameDriver *_running;
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
    return _link != nil;
}

- (void)startWithDone:(dispatch_block_t)done {
    ANNAssertMainQueue();
#if DEBUG
    ANNCurveSelfCheck();
#endif
    if (_link) {
        return;
    }
    _done = [done copy];
    _running = self;
    [self applyAtTime:0];
    ANNFrameTarget *target = [ANNFrameTarget new];
    target.owner = self;
    _link = [CADisplayLink displayLinkWithTarget:target selector:@selector(frame:)];
    [_link addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
    _start = CACurrentMediaTime();
}

- (void)frame:(CADisplayLink *)link {
    if (link != _link) {
        [link invalidate];
        return;
    }
    NSTimeInterval time = link.targetTimestamp - _start;
    if (time < 0) {
        time = 0;
    }
    if (time >= _length) {
        [self applyAtTime:_length];
        [self stop];
        dispatch_block_t done = _done;
        _done = nil;
        _running = nil;
        if (done) {
            done();
        }
        return;
    }
    [self applyAtTime:time];
}

- (void)applyAtTime:(NSTimeInterval)time {
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    for (id<ANNFrameClient> client in _clients) {
        [client applyAtTime:time];
    }
    [CATransaction commit];
}

- (void)stop {
    [_link invalidate];
    _link = nil;
}

- (void)cancel {
    ANNAssertMainQueue();
    if (!_link) {
        return;
    }
    [self applyAtTime:_length];
    [self stop];
    _done = nil;
    _running = nil;
}

- (void)dealloc {
    [_link invalidate];
}

@end

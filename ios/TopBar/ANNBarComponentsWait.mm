#import "ANNBarComponentsWait.h"
#import "ANNAssert.h"
#import "ANNTopBarPresenter.h"

@implementation ANNBarComponentsWait {
    dispatch_block_t _done;
    // What each kind still waits for, and whether its limit passed.
    NSUInteger _titleAndBackgroundLeft;
    NSUInteger _buttonsLeft;
    BOOL _titleAndBackgroundEnded;
    BOOL _buttonsEnded;
}

+ (instancetype)waitForScreens:(NSArray<UIViewController *> *)screens
                        limits:(ANNBarWaitLimits)limits
                          done:(dispatch_block_t)done {
    ANNAssertMainQueue();
    ANNBarComponentsWait *wait = [[self alloc] initWithDone:done];
    [wait startWithScreens:screens limits:limits];
    return wait.finished ? nil : wait;
}

- (instancetype)initWithDone:(dispatch_block_t)done {
    if (self = [super init]) {
        _done = [done copy];
    }
    return self;
}

- (void)startWithScreens:(NSArray<UIViewController *> *)screens limits:(ANNBarWaitLimits)limits {
    NSMutableArray<dispatch_block_t> *subscriptions = [NSMutableArray new];
    __weak ANNBarComponentsWait *weakSelf = self;
    for (UIViewController *screen in screens) {
        ANNTopBarPresenter *presenter = [ANNTopBarPresenter presenterOfScreen:screen];
        if (!presenter) {
            continue;
        }
        ANNReactComponentRegistry *registry = presenter.registry;
        [presenter collectWaitedComponents:^(NSString *componentId, BOOL isButton) {
          if (isButton) {
              self->_buttonsLeft++;
          } else {
              self->_titleAndBackgroundLeft++;
          }
          [subscriptions addObject:^{
            [registry whenComponentReady:componentId
                                      do:^{
                                        [weakSelf componentReady:isButton];
                                      }];
          }];
        }];
    }
    _titleAndBackgroundEnded = _titleAndBackgroundLeft == 0;
    _buttonsEnded = _buttonsLeft == 0;
    if (_titleAndBackgroundEnded && _buttonsEnded) {
        _finished = YES;
        return;
    }
    // Each kind to its own limit, all from now.
    [self limit:limits.titleAndBackground buttons:NO];
    [self limit:limits.buttons buttons:YES];
    for (dispatch_block_t subscribe in subscriptions) {
        subscribe();
    }
}

- (void)limit:(NSTimeInterval)limit buttons:(BOOL)buttons {
    if (limit <= 0 || (buttons ? _buttonsEnded : _titleAndBackgroundEnded)) {
        return;
    }
    __weak ANNBarComponentsWait *weakSelf = self;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(limit * NSEC_PER_SEC)),
                   dispatch_get_main_queue(), ^{
                     [weakSelf kindEnded:buttons];
                   });
}

- (void)componentReady:(BOOL)isButton {
    if (_finished) {
        return;
    }
    if (isButton) {
        if (_buttonsLeft > 0 && --_buttonsLeft == 0) {
            [self kindEnded:YES];
        }
    } else if (_titleAndBackgroundLeft > 0 && --_titleAndBackgroundLeft == 0) {
        [self kindEnded:NO];
    }
}

- (void)kindEnded:(BOOL)buttons {
    if (_finished) {
        return;
    }
    if (buttons) {
        _buttonsEnded = YES;
    } else {
        _titleAndBackgroundEnded = YES;
    }
    if (!_buttonsEnded || !_titleAndBackgroundEnded) {
        return;
    }
    _finished = YES;
    dispatch_block_t done = _done;
    _done = nil;
    if (done) {
        done();
    }
}

- (void)cancel {
    _finished = YES;
    _done = nil;
}

@end

#import "ANNRenderWait.h"
#import "ANNAssert.h"
#import "ANNComponentViewController.h"
#import "ANNJavaScriptCalls.h"
#import "ANNScreenReadiness.h"
#import "UIViewController+LayoutProtocol.h"
#import <QuartzCore/QuartzCore.h>

// A screen whose rendered state stays unknown counts as ready after this, with `unknownContentRule`.
static const NSTimeInterval ANNUnknownContentLimit = 1;

// What a kind of the bar whose limit passed in the content phase still gets, with `barLimitsFromStart`: a limit of 0
// would mean none of its own.
static const NSTimeInterval ANNPassedBarLimit = 0.001;

// What is left of a bar kind's `limit` after `spent`; no limit of its own stays none.
static NSTimeInterval ANNBarLimitLeft(NSTimeInterval limit, NSTimeInterval spent) {
    if (limit <= 0) {
        return limit;
    }
    return MAX(limit - spent, ANNPassedBarLimit);
}

// The number of the last wait made: each wait listens under a key of its own.
static NSUInteger ANNRenderWaitCount;

@implementation ANNRenderWait {
    ANNRenderWaitContent _content;
    UIViewController *_node;
    NSArray<UIViewController *> * (^_screens)(void);
    NSTimeInterval _limit;
    ANNRenderWaitBar _bar;
    ANNBarWaitLimits _barLimits;
    NSString *_key;
    // When the running wait started (`barLimitsFromStart`).
    CFTimeInterval _startTime;
    // The token: each start, phase and end takes the next one, so that what an earlier one set up does nothing.
    NSUInteger _number;
    // Inside `start`: a step runs as it is, under the caller's protection.
    BOOL _starting;
    // Inside `setUp`: a ready handler leaves the node to the readiness test after it.
    BOOL _settingUp;
    // The content is there and the phase waits for the calls JavaScript made by then (`settlesThroughJavaScript`); the
    // number of that settle, so that one of an earlier take does nothing.
    BOOL _settling;
    NSUInteger _settleNumber;
    // The content nodes still waited, and every node the wait set its ready handler on.
    NSHashTable<UIViewController *> *_waited;
    NSHashTable<UIViewController *> *_listened;
    // The waited screens of the "unseen" kind (a subset of `_waited`), and whether their limit passed in this start (a
    // retake waits for them no more then).
    NSHashTable<UIViewController *> *_unseenWaited;
    BOOL _unseenLimitPassed;
    ANNBarComponentsWait *_barWait;
    // What the last take of the running phase took: its screens (weakly) and, in the content phase, what the take read
    // of each to decide what it waits for (`takeStateOfScreens:`). A retake that would take the same does nothing.
    NSPointerArray *_takenScreens;
    NSArray<NSNumber *> *_takenStates;
}

- (instancetype)initWithContent:(ANNRenderWaitContent)content
                           node:(UIViewController *)node
                        screens:(NSArray<UIViewController *> * (^)(void))screens
                          limit:(NSTimeInterval)limit
                            bar:(ANNRenderWaitBar)bar
                      barLimits:(ANNBarWaitLimits)barLimits {
    if (self = [super init]) {
        _content = content;
        _node = node;
        _screens = [screens copy];
        _limit = limit;
        _bar = bar;
        _barLimits = barLimits;
        _key = [NSString
            stringWithFormat:@"ApexNavigation.renderWait.%lu", (unsigned long)++ANNRenderWaitCount];
        _waited = [NSHashTable weakObjectsHashTable];
        _listened = [NSHashTable weakObjectsHashTable];
        _unseenWaited = [NSHashTable weakObjectsHashTable];
    }
    return self;
}

#pragma mark - The start

- (void)start {
    ANNAssertMainQueue();
    if (_isWaiting) {
        return;
    }
    _isWaiting = YES;
    _isInBarPhase = NO;
    _unseenLimitPassed = NO;
    _startTime = CACurrentMediaTime();
    NSUInteger number = ++_number;
    _starting = YES;
    @try {
        [self startContentOfNumber:number];
    } @finally {
        _starting = NO;
    }
    if (![self runs:number]) {
        return;
    }
    __weak ANNRenderWait *weakSelf = self;
    if (_limit > 0) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(_limit * NSEC_PER_SEC)),
                       dispatch_get_main_queue(), ^{
                         [weakSelf limitOfNumber:number];
                       });
    }
    if (_unseenLimit > 0) {
        int64_t delay = (int64_t)(_unseenLimit * NSEC_PER_SEC);
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
          [weakSelf unseenLimitOfNumber:number];
        });
    }
    if (_unknownContentRule) {
        int64_t delay = (int64_t)(ANNUnknownContentLimit * NSEC_PER_SEC);
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue(), ^{
          [weakSelf unknownLimitOfNumber:number];
        });
    }
}

// The handlers first, then the setup (which may make the content ready inside it), then the test: a node ready by
// then counts at once. Nothing left to wait for ends the content phase here.
- (void)startContentOfNumber:(NSUInteger)number {
    [self takeScreensOfNumber:number];
    dispatch_block_t setUp = _setUp;
    if (setUp) {
        _settingUp = YES;
        @try {
            setUp();
        } @finally {
            _settingUp = NO;
        }
    }
    if (![self runs:number]) {
        return;
    }
    [self testNode];
    if (_waited.count == 0 && !_barWait) {
        [self contentOfNumber:number];
    }
}

// The content phase takes what it waits for: the bar components that go with the content, then a ready handler on
// each content node.
- (void)takeScreensOfNumber:(NSUInteger)number {
    NSArray<UIViewController *> *screens = [self screens];
    [self rememberTakenScreens:screens states:[self takeStateOfScreens:screens]];
    if (_bar == ANNRenderWaitBarWithContent) {
        __weak ANNRenderWait *weakSelf = self;
        [_barWait cancel];
        _barWait = [ANNBarComponentsWait waitForScreens:[self barScreens]
                                                 limits:_barLimits
                                                   done:^{
                                                     [weakSelf barOfNumber:number];
                                                   }];
    }
    switch (_content) {
        case ANNRenderWaitContentNone:
            break;
        case ANNRenderWaitContentEachScreen:
            // Each screen by its content; an unselected tab's to the unseen limit. A screen with no content to wait
            // for (a native screen) is ready.
            for (UIViewController *node in screens) {
                if (![node isKindOfClass:ANNComponentViewController.class]) {
                    continue;
                }
                ANNComponentViewController *screen = (ANNComponentViewController *)node;
                UIView<ANNComponentProtocol> *content = screen.reactView;
                if (!content || content.isReady) {
                    continue;
                }
                if (ANNWaitedReadinessOf(screen) == ANNWaitedReadinessContent) {
                    [self listenTo:screen number:number];
                    continue;
                }
                if (_unseenLimitPassed) {
                    continue;
                }
                [_unseenWaited addObject:screen];
                [self listenTo:screen number:number];
            }
            break;
        case ANNRenderWaitContentNode:
            if (_node) {
                [self listenTo:_node number:number];
            }
            break;
    }
}

- (void)listenTo:(UIViewController *)node number:(NSUInteger)number {
    [_waited addObject:node];
    [_listened addObject:node];
    __weak ANNRenderWait *weakSelf = self;
    __weak UIViewController *weakNode = node;
    [node setReadyHandler:^{
      [weakSelf node:weakNode readyOfNumber:number];
    }
                   forKey:_key];
}

#pragma mark - What ends a phase

- (void)node:(UIViewController *)node readyOfNumber:(NSUInteger)number {
    if (_settingUp || !node || ![self runs:number] || ![_waited containsObject:node]) {
        return;
    }
    [_waited removeObject:node];
    if (_waited.count == 0 && !_barWait) {
        [self contentOfNumber:number];
    }
}

// The bar components are ready, or past their own limits: the phase ends unless content is still waited.
- (void)barOfNumber:(NSUInteger)number {
    if (![self runs:number]) {
        return;
    }
    _barWait = nil;
    if (_waited.count > 0) {
        return;
    }
    [self contentOfNumber:number];
}

// What the running phase waited for came by its signals, or was there already. The content phase with
// `settlesThroughJavaScript` ends once the calls JavaScript made by now are applied: the commit that mounted the content
// ran its layout effects (`useScreenOptions`) in the same task of the JavaScript thread, so their `mergeOptions` are on
// their way to the main queue ahead of the settle. Content that was there already (as the wait starts or takes its
// screens again) may have mounted in a commit that ran just now: it settles too. The bar phase, and screens without
// React content (a native screen), end at once.
- (void)contentOfNumber:(NSUInteger)number {
    if (!_settlesThroughJavaScript || _isInBarPhase || ![self hasReactScreen]) {
        [self phaseOfNumber:number endedMarkingReady:NO];
        return;
    }
    if (_settling) {
        return;
    }
    _settling = YES;
    NSUInteger settle = ++_settleNumber;
    __weak ANNRenderWait *weakSelf = self;
    [ANNJavaScriptCalls afterCallsMadeSoFar:^{
      [weakSelf settleOfNumber:number settle:settle];
    }];
}

- (void)settleOfNumber:(NSUInteger)number settle:(NSUInteger)settle {
    if (![self runs:number] || !_settling || settle != _settleNumber) {
        return;
    }
    _settling = NO;
    [self phaseOfNumber:number endedMarkingReady:NO];
}

// The screens of the "unseen" kind are waited no longer: nobody sees them, nothing of theirs is held.
- (void)unseenLimitOfNumber:(NSUInteger)number {
    if (![self runs:number]) {
        return;
    }
    _unseenLimitPassed = YES;
    if (_unseenWaited.count == 0) {
        return;
    }
    for (UIViewController *node in _unseenWaited.allObjects) {
        [_waited removeObject:node];
    }
    [_unseenWaited removeAllObjects];
    if (_waited.count == 0 && !_barWait) {
        [self phaseOfNumber:number endedMarkingReady:NO];
    }
}

// The limit: what is still waited counts as rendered from now on, its held events go at once. A phase that only waits
// for the calls of JavaScript (its content is there) ends without marking anything.
- (void)limitOfNumber:(NSUInteger)number {
    if (![self runs:number]) {
        return;
    }
    if (_settling) {
        [self stopSettling];
        [self phaseOfNumber:number endedMarkingReady:NO];
        return;
    }
    if (_waited.count == 0 && !_barWait) {
        return;
    }
    [self phaseOfNumber:number endedMarkingReady:YES];
}

// The unknown-content rule: a screen whose safe area view spoke keeps the wait for its signal; otherwise the content
// counts as ready.
- (void)unknownLimitOfNumber:(NSUInteger)number {
    if (![self runs:number]) {
        return;
    }
    for (UIViewController *node in [self screens]) {
        if (![node isKindOfClass:ANNComponentViewController.class]) {
            continue;
        }
        ANNComponentViewController *screen = (ANNComponentViewController *)node;
        NSString *screenId = screen.layoutInfo.componentId ?: @"";
        if (screen.reactView && !screen.reactView.isReady &&
            [ANNScreenReadiness contentRenderedOfScreen:screenId] != ANNContentRenderedUnknown) {
            return;
        }
    }
    [self phaseOfNumber:number endedMarkingReady:YES];
}

- (void)endPhase {
    ANNAssertMainQueue();
    [self phaseOfNumber:_number endedMarkingReady:NO];
}

// The running phase ends: the content phase goes on to the bar phase when the bar components come after it,
// otherwise the wait ends.
- (void)phaseOfNumber:(NSUInteger)number endedMarkingReady:(BOOL)markReady {
    if (![self runs:number]) {
        return;
    }
    BOOL (^canEnd)(void) = _canEnd;
    if (canEnd && !canEnd()) {
        return;
    }
    [self runStep:^{
      if (markReady) {
          [self markContentReady];
      }
      if (self->_bar == ANNRenderWaitBarAfterContent && !self->_isInBarPhase) {
          [self startBarPhaseWithLimits:[self barLimitsAfterContent]];
          return;
      }
      [self finish];
    }];
}

// With `ANNRenderWaitContentNode` the node's readiness is the node's own: every screen of it is marked. Otherwise the
// screens still waited are.
- (void)markContentReady {
    NSArray<UIViewController *> *marked =
        _content == ANNRenderWaitContentNode ? [self screens] : _waited.allObjects;
    for (UIViewController *node in marked) {
        if ([node isKindOfClass:ANNComponentViewController.class]) {
            [((ANNComponentViewController *)node).appearance markReady];
        }
    }
}

#pragma mark - The bar phase

// The content is there: the bar components of the screens now, each kind to its limit in `limits` from now. Nothing to
// wait for ends the wait at once.
- (void)startBarPhaseWithLimits:(ANNBarWaitLimits)limits {
    [self stopSettling];
    [_barWait cancel];
    _barWait = nil;
    [self releaseContent];
    _isInBarPhase = YES;
    NSUInteger number = ++_number;
    __weak ANNRenderWait *weakSelf = self;
    NSArray<UIViewController *> *barScreens = [self barScreens];
    [self rememberTakenScreens:barScreens states:nil];
    ANNBarComponentsWait *barWait = [ANNBarComponentsWait waitForScreens:barScreens
                                                                  limits:limits
                                                                    done:^{
                                                                      [weakSelf barOfNumber:number];
                                                                    }];
    _barWait = barWait;
    if (!barWait) {
        [self finish];
    }
}

#pragma mark - Taking again, the end

- (void)retakeScreens:(BOOL)take {
    ANNAssertMainQueue();
    if (!_isWaiting) {
        return;
    }
    // Nothing changed for the wait: taking the same screens again would only start its bar components' limits anew.
    if (take && [self takesSameAsLastTake]) {
        return;
    }
    if (_isInBarPhase) {
        [self runStep:^{
          [self startBarPhaseWithLimits:self->_barLimits];
        }];
        return;
    }
    // The same token: the limit set at the start stays.
    NSUInteger number = _number;
    [self stopSettling];
    [self releaseContent];
    [_barWait cancel];
    _barWait = nil;
    if (!take) {
        _takenScreens = nil;
        _takenStates = nil;
        [self phaseOfNumber:number endedMarkingReady:NO];
        return;
    }
    // As at the start: a node ready already counts at once (its ready handler waits for the next time), and content
    // that is there settles again, the settle of the last take having stopped.
    [self takeScreensOfNumber:number];
    [self testNode];
    if (_waited.count == 0 && !_barWait) {
        [self contentOfNumber:number];
    }
}

- (void)cancel {
    ANNAssertMainQueue();
    if (!_isWaiting) {
        return;
    }
    [self stop];
}

- (void)finish {
    dispatch_block_t ended = _ended;
    [self stop];
    if (ended) {
        ended();
    }
}

// The wait lets go of everything: its handlers, its bar wait; its limits find another token.
- (void)stop {
    [self stopSettling];
    _isWaiting = NO;
    _isInBarPhase = NO;
    _takenScreens = nil;
    _takenStates = nil;
    _number++;
    [_barWait cancel];
    _barWait = nil;
    [self releaseContent];
    _ended = nil;
}

- (void)releaseContent {
    for (UIViewController *node in _listened.allObjects) {
        [node removeReadyHandlerForKey:_key];
    }
    [_listened removeAllObjects];
    [_waited removeAllObjects];
    [_unseenWaited removeAllObjects];
}

#pragma mark - Helpers

// A settle on its way does nothing from now on.
- (void)stopSettling {
    _settling = NO;
    _settleNumber++;
}

// What the content phase's take reads of each screen to decide what it waits for: the readiness it needs, whether it
// has its content yet, and whether that content is ready.
- (NSArray<NSNumber *> *)takeStateOfScreens:(NSArray<UIViewController *> *)screens {
    NSMutableArray<NSNumber *> *states = [NSMutableArray arrayWithCapacity:screens.count];
    for (UIViewController *node in screens) {
        NSUInteger state = (NSUInteger)ANNWaitedReadinessOf(node) << 2;
        if ([node isKindOfClass:ANNComponentViewController.class]) {
            UIView<ANNComponentProtocol> *content = ((ANNComponentViewController *)node).reactView;
            state |= (content ? 1 : 0) | (content.isReady ? 2 : 0);
        }
        [states addObject:@(state)];
    }
    return states;
}

- (void)rememberTakenScreens:(NSArray<UIViewController *> *)screens
                      states:(nullable NSArray<NSNumber *> *)states {
    _takenScreens = [NSPointerArray weakObjectsPointerArray];
    for (UIViewController *node in screens) {
        [_takenScreens addPointer:(__bridge void *)node];
    }
    _takenStates = states;
}

// The phase would take the same screens as its last take, in the content phase each in the same state. A screen gone
// since then reads nil and differs.
- (BOOL)takesSameAsLastTake {
    if (!_takenScreens) {
        return NO;
    }
    NSArray<UIViewController *> *screens = _isInBarPhase ? [self barScreens] : [self screens];
    if (screens.count != _takenScreens.count) {
        return NO;
    }
    for (NSUInteger index = 0; index < screens.count; index++) {
        if ((__bridge void *)screens[index] != [_takenScreens pointerAtIndex:index]) {
            return NO;
        }
    }
    if (_isInBarPhase) {
        return YES;
    }
    return [[self takeStateOfScreens:screens] isEqualToArray:_takenStates ?: @[]];
}

- (BOOL)runs:(NSUInteger)number {
    return _isWaiting && _number == number;
}

- (NSArray<UIViewController *> *)screens {
    NSArray<UIViewController *> * (^screens)(void) = _screens;
    return (screens ? screens() : nil) ?: @[];
}

// The screens whose bar components are waited for: those waited by their content (never a screen the user doesn't see).
- (NSArray<UIViewController *> *)barScreens {
    NSMutableArray<UIViewController *> *screens = [NSMutableArray new];
    for (UIViewController *node in [self screens]) {
        if (ANNWaitedReadinessOf(node) == ANNWaitedReadinessContent) {
            [screens addObject:node];
        }
    }
    return screens;
}

// With `ANNRenderWaitContentNode`, the node counts as ready when every screen of it is.
- (void)testNode {
    if (_content == ANNRenderWaitContentNode && _node && [self screensAreReady]) {
        [_waited removeObject:_node];
    }
}

// The limits of the bar phase that follows the content phase: with `barLimitsFromStart`, what is left of each since the
// wait started.
- (ANNBarWaitLimits)barLimitsAfterContent {
    ANNBarWaitLimits limits = _barLimits;
    if (!_barLimitsFromStart) {
        return limits;
    }
    NSTimeInterval spent = CACurrentMediaTime() - _startTime;
    limits.titleAndBackground = ANNBarLimitLeft(limits.titleAndBackground, spent);
    limits.buttons = ANNBarLimitLeft(limits.buttons, spent);
    return limits;
}

// A screen with React content is among the screens: a commit of React mounted it.
- (BOOL)hasReactScreen {
    for (UIViewController *node in [self screens]) {
        if ([node isKindOfClass:ANNComponentViewController.class] &&
            ((ANNComponentViewController *)node).reactView) {
            return YES;
        }
    }
    return NO;
}

// Every screen has no content to wait for (a native screen) or its content is ready.
- (BOOL)screensAreReady {
    for (UIViewController *node in [self screens]) {
        if (![node isKindOfClass:ANNComponentViewController.class]) {
            continue;
        }
        ANNComponentViewController *screen = (ANNComponentViewController *)node;
        if (screen.reactView && !screen.reactView.isReady) {
            return NO;
        }
    }
    return YES;
}

- (void)runStep:(dispatch_block_t)step {
    void (^protect)(dispatch_block_t) = _protect;
    if (_starting || !protect) {
        step();
        return;
    }
    protect(step);
}

@end

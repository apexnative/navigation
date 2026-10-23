#import "ANNStackQueue.h"
#import "ANNAnimationRules.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNBarComponentsWait.h"
#import "ANNCommandRunner.h"
#import "ANNComponentViewController.h"
#import "ANNErrorTexts.h"
#import "ANNReloadCoordinator.h"
#import "ANNRenderWait.h"
#import "ANNTransitionPlan.h"
#import "UIViewController+LayoutProtocol.h"
#import <QuartzCore/QuartzCore.h>
#import <React/RCTLog.h>

// How long the new top of a `setStackRoot` waits for its first content when `waitForRender` is not set.
static const NSTimeInterval ANNStackRootRenderLimit = 0.5;

// The limits of the bar components of a push: title and background 1 s, buttons 0.5 s, together from the wait's start.
static const ANNBarWaitLimits ANNPushBarLimits = {1, 0.5};

// The bar components of a `setStackRoot` within its 0.5 s.
static const ANNBarWaitLimits ANNStackRootBarLimits = {0.5, 0.5};

typedef NS_ENUM(NSInteger, ANNStackOperationKind) {
    ANNStackOperationPush,
    ANNStackOperationCut,
    ANNStackOperationReplace,
    ANNStackOperationNone,
    ANNStackOperationSwipe,
};

// One command or action of the user in the queue: what it moves, its answer, and how far it got.
@interface ANNStackOperation : NSObject
@property(nonatomic) ANNStackOperationKind kind;
// The JavaScript it belongs to: a step of another one does nothing (reload).
@property(nonatomic) NSUInteger generation;
@property(nonatomic, strong, nullable) ANNCommandAnswer *answer;
@property(nonatomic, strong, nullable) id value;
@property(nonatomic, copy) NSString *what;
@property(nonatomic, copy, nullable) NSString *componentId;
// The node the command came from; a completed swipe that took it (or the target) away marks `sourceGone`.
@property(nonatomic, weak, nullable) UIViewController *source;
@property(nonatomic) BOOL sourceGone;
// push
@property(nonatomic, strong, nullable) UIViewController *node;
// cut, swipe: the entry that stays on top
@property(nonatomic, strong, nullable) UIViewController *target;
@property(nonatomic) BOOL silent;
// replace
@property(nonatomic, copy, nullable) NSArray<UIViewController *> *nodes;
// replace, push: a push with `animated` NO has no animation, with `waits` NO no wait before its transition.
@property(nonatomic) BOOL animated;
@property(nonatomic) BOOL waits;
@property(nonatomic) BOOL atOnce;
// When the replace's own wait for its new top started (`waitForRender` unset): the custom transition's wait takes what
// is left of the 0.5 s. 0 when it didn't wait so.
@property(nonatomic) CFTimeInterval waitStarted;
@property(nonatomic, copy, nullable) dispatch_block_t completion;
@property(nonatomic, copy, nullable) ANNTransitionRejectionBlock rejection;
// The run.
@property(nonatomic) BOOL done;
// The operation's render wait: its content, then or together with its bar components.
@property(nonatomic, strong, nullable) ANNRenderWait *wait;
@property(nonatomic, readonly) BOOL waiting;
// The entry that waits in the stack's view while the wait runs.
@property(nonatomic, strong, nullable) UIViewController *waited;
@property(nonatomic) BOOL parked;
@property(nonatomic, copy, nullable) NSArray<UIViewController *> *driven;
// The entries the transition takes away, top first; the entry a push covers.
@property(nonatomic, copy, nullable) NSArray<UIViewController *> *leaving;
@property(nonatomic, strong, nullable) UIViewController *covered;
@end

@implementation ANNStackOperation

- (BOOL)isCurrent {
    return _generation == ANNReloadCoordinator.generation;
}

// The operation waits for a screen to render (either phase of its wait).
- (BOOL)waiting {
    return _wait.isWaiting;
}

@end

static NSString *ANNIdOf(UIViewController *node) {
    return node.layoutInfo.componentId ?: @"";
}

UIViewController *ANNStackOptionsScreen(UIViewController *entry) {
    if ([entry respondsToSelector:@selector(optionsScreen)]) {
        UIViewController *screen = [(id<ANNLayoutProtocol>)entry optionsScreen];
        if (screen) {
            return screen;
        }
    }
    return entry;
}

NSString *ANNStackAnswerIdOf(UIViewController *entry, ANNStackAnswerCommand command) {
    switch (command) {
        case ANNStackAnswerCommandPush:
            return ANNIdOf(entry);
        case ANNStackAnswerCommandReplace:
        case ANNStackAnswerCommandPop:
        case ANNStackAnswerCommandPopToRoot:
        case ANNStackAnswerCommandKey:
        case ANNStackAnswerCommandShowInColumn:
            return ANNIdOf(ANNStackOptionsScreen(entry));
    }
    return ANNIdOf(ANNStackOptionsScreen(entry));
}

NSArray<ANNComponentViewController *> *ANNStackScreensOfEntry(UIViewController *entry) {
    if ([entry isKindOfClass:ANNComponentViewController.class]) {
        return @[ (ANNComponentViewController *)entry ];
    }
    NSMutableArray<ANNComponentViewController *> *screens = [NSMutableArray new];
    for (UIViewController *child in entry.layoutChildren) {
        if ([child isKindOfClass:ANNComponentViewController.class]) {
            [screens addObject:(ANNComponentViewController *)child];
        }
    }
    return screens;
}

BOOL ANNStackEntryIsReady(UIViewController *entry) {
    for (ANNComponentViewController *screen in ANNStackScreensOfEntry(entry)) {
        if (screen.reactView && !screen.reactView.isReady) {
            return NO;
        }
    }
    return YES;
}

// A screen renders once by itself; another entry (a pair) renders only while its view never loaded.
static void ANNRenderIfNeeded(UIViewController *entry) {
    if ([entry isKindOfClass:ANNComponentViewController.class] || !entry.isViewLoaded) {
        [entry render];
    }
}

// The node an operation shows renders right before its transition. A caller that waited itself (`waits` NO: a
// preview, `showInColumn`, the split view's replace) rendered it already: only a screen, which renders once, is asked
// again; a pair's `render` would start its count again.
static void ANNRenderBeforeTransition(UIViewController *node, BOOL waits) {
    if (waits || [node isKindOfClass:ANNComponentViewController.class]) {
        [node render];
    }
}

static NSArray<UIViewController *> *ANNTopFirst(NSArray<UIViewController *> *entries) {
    return entries.reverseObjectEnumerator.allObjects;
}

@implementation ANNStackQueue {
    __weak id<ANNStackDisplay> _display;
    NSMutableArray<UIViewController *> *_entries;
    NSMutableArray<UIViewController *> *_shown;
    NSMutableArray<ANNStackOperation *> *_pending;
    // The operation that waits or runs its transition (iOS runs one at a time).
    ANNStackOperation *_current;
    BOOL _running;
    BOOL _runScheduled;
    // UIKit runs a transition the queue didn't start: the next operation waits for its end.
    BOOL _waitingForUIKit;
    BOOL _destroyed;
}

- (instancetype)initWithDisplay:(id<ANNStackDisplay>)display {
    if (self = [super init]) {
        _display = display;
        _entries = [NSMutableArray new];
        _shown = [NSMutableArray new];
        _pending = [NSMutableArray new];
    }
    return self;
}

#pragma mark - The lists

- (NSArray<UIViewController *> *)entries {
    return [_entries copy];
}

- (NSArray<UIViewController *> *)shown {
    return [_shown copy];
}

- (BOOL)isAtRest {
    return !_current && _pending.count == 0 && !_waitingForUIKit && !_destroyed &&
           ![_display stackRunningTransition];
}

- (NSArray<UIViewController *> *)heldNodes {
    NSMutableOrderedSet<UIViewController *> *nodes =
        [NSMutableOrderedSet orderedSetWithArray:_shown];
    [nodes addObjectsFromArray:_entries];
    NSMutableArray<ANNStackOperation *> *operations = [_pending mutableCopy];
    if (_current) {
        [operations insertObject:_current atIndex:0];
    }
    for (ANNStackOperation *operation in operations) {
        if (operation.node) {
            [nodes addObject:operation.node];
        }
        if (operation.nodes) {
            [nodes addObjectsFromArray:operation.nodes];
        }
        if (operation.leaving) {
            [nodes addObjectsFromArray:operation.leaving];
        }
    }
    return nodes.array;
}

- (void)setInitialEntries:(NSArray<UIViewController *> *)entries {
    UIViewController *stack = [_display stackViewController];
    for (UIViewController *entry in entries) {
        [entry setLayoutParentFallback:stack];
    }
    [_entries setArray:entries];
    [_shown setArray:entries];
}

#pragma mark - Accepting an operation (`entries` changes now)

- (ANNStackOperation *)operationOfKind:(ANNStackOperationKind)kind
                                answer:(ANNCommandAnswer *)answer
                                  what:(NSString *)what
                           componentId:(NSString *)componentId
                                source:(UIViewController *)source {
    ANNStackOperation *operation = [ANNStackOperation new];
    operation.kind = kind;
    operation.answer = answer;
    operation.generation = answer ? answer.generation : ANNReloadCoordinator.generation;
    operation.what = what ?: @"go back";
    operation.componentId = componentId;
    operation.source = source;
    return operation;
}

- (void)push:(UIViewController *)node
         answer:(ANNCommandAnswer *)answer
           what:(NSString *)what
    componentId:(NSString *)componentId
         source:(UIViewController *)source {
    [self push:node
             answer:answer
               what:what
        componentId:componentId
             source:source
           animated:YES
              waits:YES];
}

- (void)push:(UIViewController *)node
         answer:(ANNCommandAnswer *)answer
           what:(NSString *)what
    componentId:(NSString *)componentId
         source:(UIViewController *)source
       animated:(BOOL)animated
          waits:(BOOL)waits {
    ANNAssertMainQueue();
    ANNStackOperation *operation = [self operationOfKind:ANNStackOperationPush
                                                  answer:answer
                                                    what:what
                                             componentId:componentId
                                                  source:source];
    operation.node = node;
    operation.value = ANNStackAnswerIdOf(node, ANNStackAnswerCommandPush);
    operation.animated = animated;
    operation.waits = waits;
    if (_destroyed) {
        [self reject:operation text:[ANNErrorTexts screenNotShown:componentId]];
        [node destroy];
        return;
    }
    // A push over a waiting `setStackRoot` completes it now: its screens lie under the push.
    [self completeWaitingReplace];
    [node setLayoutParentFallback:[_display stackViewController]];
    [_entries addObject:node];
    [_pending addObject:operation];
    [self runNext];
}

- (void)cutTo:(UIViewController *)target
         answer:(ANNCommandAnswer *)answer
          value:(id)value
           what:(NSString *)what
    componentId:(NSString *)componentId
         source:(UIViewController *)source
         silent:(BOOL)silent {
    ANNAssertMainQueue();
    ANNStackOperation *operation = [self operationOfKind:ANNStackOperationCut
                                                  answer:answer
                                                    what:what
                                             componentId:componentId
                                                  source:source];
    operation.target = target;
    operation.value = value;
    operation.silent = silent;
    if (_destroyed) {
        [self reject:operation text:[ANNErrorTexts screenNotShown:componentId ?: ANNIdOf(target)]];
        return;
    }
    // A cut while a `setStackRoot` waits completes it first and runs on its list: a back never waits for a render that
    // may take long.
    [self completeWaitingReplace];
    NSUInteger index = [_entries indexOfObjectIdenticalTo:target];
    if (index == NSNotFound) {
        [self reject:operation text:[ANNErrorTexts screenNotShown:componentId ?: ANNIdOf(target)]];
        return;
    }
    NSRange above = NSMakeRange(index + 1, _entries.count - index - 1);
    NSArray<UIViewController *> *removed = [_entries subarrayWithRange:above];
    [_entries removeObjectsInRange:above];
    // The pushes it takes away that haven't shown are interrupted now (C1): a back never waits behind a screen the user
    // hasn't seen. When nothing else goes, it moves nothing: a `none` in its turn.
    if (![self interruptPushesOf:removed]) {
        if (!answer) {
            return;
        }
        operation.kind = ANNStackOperationNone;
    }
    [_pending addObject:operation];
    [self runNext];
}

- (void)replaceWith:(NSArray<UIViewController *> *)nodes
           animated:(BOOL)animated
              waits:(BOOL)waits
             answer:(ANNCommandAnswer *)answer
               what:(NSString *)what
        componentId:(NSString *)componentId
             source:(UIViewController *)source
         completion:(dispatch_block_t)completion
          rejection:(ANNTransitionRejectionBlock)rejection {
    ANNAssertMainQueue();
    ANNStackOperation *operation = [self operationOfKind:ANNStackOperationReplace
                                                  answer:answer
                                                    what:what
                                             componentId:componentId
                                                  source:source];
    operation.nodes = nodes;
    operation.animated = animated;
    operation.waits = waits;
    operation.completion = completion;
    operation.rejection = rejection;
    operation.value = ANNStackAnswerIdOf(nodes.lastObject, ANNStackAnswerCommandReplace);
    if (_destroyed) {
        [self reject:operation text:[ANNErrorTexts screenNotShown:componentId]];
        for (UIViewController *node in nodes) {
            [node destroy];
        }
        return;
    }
    // A second `setStackRoot` completes a waiting one and runs from its state; the pushes not shown yet go.
    [self completeWaitingReplace];
    [self interruptPushesOf:[_entries copy]];
    UIViewController *stack = [_display stackViewController];
    for (UIViewController *node in nodes) {
        [node setLayoutParentFallback:stack];
    }
    [_entries setArray:nodes];
    [_pending addObject:operation];
    [self runNext];
}

- (void)noneWithValue:(id)value
               answer:(ANNCommandAnswer *)answer
                 what:(NSString *)what
          componentId:(NSString *)componentId
               source:(UIViewController *)source {
    ANNAssertMainQueue();
    ANNStackOperation *operation = [self operationOfKind:ANNStackOperationNone
                                                  answer:answer
                                                    what:what
                                             componentId:componentId
                                                  source:source];
    operation.value = value;
    if (_destroyed) {
        [self reject:operation text:[ANNErrorTexts screenNotShown:componentId]];
        return;
    }
    [_pending addObject:operation];
    [self runNext];
}

// Every push whose node `removed` holds and that hasn't started its transition (waiting for its render, or queued)
// is interrupted, oldest first. Whether anything else of `removed` is left to move.
- (BOOL)interruptPushesOf:(NSArray<UIViewController *> *)removed {
    NSMutableArray<UIViewController *> *left = [removed mutableCopy];
    ANNStackOperation *current = _current;
    if (current.kind == ANNStackOperationPush && current.waiting &&
        [removed indexOfObjectIdenticalTo:current.node] != NSNotFound) {
        [left removeObjectIdenticalTo:current.node];
        [self interrupt:current];
    }
    for (ANNStackOperation *operation in [_pending copy]) {
        if (operation.kind == ANNStackOperationPush && operation.node &&
            [removed indexOfObjectIdenticalTo:operation.node] != NSNotFound) {
            [_pending removeObjectIdenticalTo:operation];
            [left removeObjectIdenticalTo:operation.node];
            [self interrupt:operation];
        }
    }
    return left.count > 0;
}

// An interrupted push: it resolves with its node's id, which never appeared, and the node is destroyed without
// appearance events and without `ScreenPopped` (its destroy's own `SlotDestroyed` or `NativeScreenReleased` go). The
// screen under it is not touched.
- (void)interrupt:(ANNStackOperation *)operation {
    [self endWaitOf:operation];
    [self unparkOf:operation];
    [self stopDrivingOf:operation];
    operation.done = YES;
    operation.wait.ended = nil;
    if (_current == operation) {
        _current = nil;
    }
    if (!operation.isCurrent) {
        return;
    }
    [operation.answer resolve:operation.value];
    [self destroyNodes:@[ operation.node ]];
}

// A waiting `setStackRoot` completed at once: its end steps now, without an animation and without an appearance of its
// new top, which lies under what comes next. YES when it completed.
- (BOOL)completeWaitingReplace {
    ANNStackOperation *operation = _current;
    if (operation.kind != ANNStackOperationReplace || !operation.waiting || !operation.isCurrent) {
        return NO;
    }
    [self endWaitOf:operation];
    operation.atOnce = YES;
    [self protect:operation
             step:^{
               [self startReplaceTransition:operation];
             }];
    return _current != operation;
}

#pragma mark - Running

- (void)runNext {
    if (_running || _destroyed || _waitingForUIKit) {
        return;
    }
    _running = YES;
    while (!_destroyed && !_waitingForUIKit) {
        ANNStackOperation *head = _pending.firstObject;
        if (!head) {
            break;
        }
        if (_current) {
            // A waiting `setStackRoot` is completed at once by what changes the list after it; a `none` waits until
            // it starts its transition (C6).
            if (_current.kind == ANNStackOperationReplace && head.kind != ANNStackOperationNone &&
                [self completeWaitingReplace]) {
                continue;
            }
            break;
        }
        [_pending removeObjectAtIndex:0];
        if (!head.isCurrent) {
            continue;
        }
        id<UIViewControllerTransitionCoordinator> coordinator = [_display stackRunningTransition];
        if (coordinator) {
            [_pending insertObject:head atIndex:0];
            [self waitForUIKit:coordinator];
            break;
        }
        _current = head;
        [self protect:head
                 step:^{
                   [self start:head];
                 }];
    }
    _running = NO;
}

// The operation that ended asked for the next one: the loop takes it when it runs, else on the next turn of the main
// queue, once UIKit finished its own part of the transition that ended.
- (void)scheduleRunNext {
    if (_running || _runScheduled || _destroyed) {
        return;
    }
    _runScheduled = YES;
    __weak ANNStackQueue *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      ANNStackQueue *queue = weakSelf;
      if (!queue) {
          return;
      }
      queue->_runScheduled = NO;
      [queue runNext];
    });
}

// UIKit drops a push or a pop asked for during a transition of its own: the queue goes on once it ended.
- (void)waitForUIKit:(id<UIViewControllerTransitionCoordinator>)coordinator {
    _waitingForUIKit = YES;
    __weak ANNStackQueue *weakSelf = self;
    dispatch_block_t resume = ^{
      dispatch_async(dispatch_get_main_queue(), ^{
        ANNStackQueue *queue = weakSelf;
        if (!queue || !queue->_waitingForUIKit) {
            return;
        }
        queue->_waitingForUIKit = NO;
        [queue runNext];
      });
    };
    void (^completion)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          resume();
        };
    if (![coordinator animateAlongsideTransition:nil completion:completion]) {
        resume();
    }
}

- (void)start:(ANNStackOperation *)operation {
    switch (operation.kind) {
        case ANNStackOperationPush:
            [self startPush:operation];
            break;
        case ANNStackOperationCut:
            [self startCut:operation];
            break;
        case ANNStackOperationReplace:
            [self startReplace:operation];
            break;
        case ANNStackOperationNone:
            if (operation.sourceGone) {
                [self rejectInTurn:operation];
                return;
            }
            [operation.answer resolve:operation.value];
            [self finish:operation];
            break;
        case ANNStackOperationSwipe:
            break;
    }
}

#pragma mark - push

- (void)startPush:(ANNStackOperation *)operation {
    UIViewController *node = operation.node;
    // Its node went before its turn (a swipe that completed took the screen it came from, C8): interrupted.
    if ([_entries indexOfObjectIdenticalTo:node] == NSNotFound) {
        [self interrupt:operation];
        [self scheduleRunNext];
        return;
    }
    // `waitForRender: true` waits for the content as long as it takes, then for the bar components (their limits from
    // the content's end); unset (also with `enabled: false`), only for the bar components, each kind to its own limit,
    // without parking (bar roots render out of a window); `false`, for nothing. The content comes during the
    // transition. A stack that isn't seen never waits.
    Bool *waitForRender = [node resolveOptionsWithDefault].animations.push.waitForRender;
    // A push whose caller waited already (a preview, `showInColumn`) waits for nothing here.
    BOOL onScreen = operation.waits && [_display stackIsOnScreen];
    __weak ANNStackQueue *weakSelf = self;
    __weak ANNStackOperation *weakOperation = operation;
    if (onScreen && waitForRender.hasValue && waitForRender.get) {
        [self waitFor:operation
                    entry:node
                  content:ANNRenderWaitContentNode
                    limit:0
             unknownLimit:YES
                      bar:ANNRenderWaitBarAfterContent
                barLimits:ANNPushBarLimits
                     then:^{
                       [weakSelf startPushTransition:weakOperation];
                     }];
        return;
    }
    if (onScreen && !waitForRender.hasValue) {
        [self waitFor:operation
                    entry:node
                  content:ANNRenderWaitContentNone
                    limit:0
             unknownLimit:NO
                      bar:ANNRenderWaitBarAfterContent
                barLimits:ANNPushBarLimits
                     then:^{
                       // UIKit sizes the screen inside the push: it makes its content right before.
                       [weakOperation.node render];
                       [weakSelf startPushTransition:weakOperation];
                     }];
        return;
    }
    // UIKit sizes the screen inside the push: it makes its content right before.
    ANNRenderBeforeTransition(node, operation.waits);
    [self startPushTransition:operation];
}

- (void)startPushTransition:(ANNStackOperation *)operation {
    if (!operation) {
        return;
    }
    UIViewController *node = operation.node;
    [self unparkOf:operation];
    BOOL onScreen = [_display stackIsOnScreen];
    // The arriving screen's `animations.push`: its own transition when it has one, else UIKit's push (or the zoom);
    // none with `enabled: false`.
    ANNStackPushAnimationOptions *push = [node resolveOptionsWithDefault].animations.push;
    BOOL animated = onScreen && operation.animated && [push.enabled withDefault:YES];
    UIViewController *covered = _shown.lastObject;
    operation.covered = covered;
    ANNTransitionPlan *plan = nil;
    if (animated) {
        plan = [ANNTransitionPlan stackPlanOfKind:ANNTransitionKindPush
                                        animation:push
                                    waitForRender:push.waitForRender
                                        waitLimit:ANNCustomTransitionWaitLimit
                                             from:covered
                                               to:node];
    }
    [self drive:operation nodes:covered ? @[ covered, node ] : @[ node ]];
    [_display stackEntryJoins:node first:_shown.count == 0];
    [_shown addObject:node];
    if (onScreen) {
        [ANNAppearanceDispatcher beginTransitionShowing:@[ node ]];
    }
    [_display stackChange:ANNStackChangePush toEntries:[_shown copy] animated:animated plan:plan];
    [self whenTransitionOf:operation
                      ends:^(BOOL cancelled) {
                        [self endPush:operation];
                      }];
}

- (void)endPush:(ANNStackOperation *)operation {
    UIViewController *node = operation.node;
    UIViewController *covered = operation.covered;
    BOOL showing = [_display stackIsOnScreen] && _shown.lastObject == node;
    [ANNAppearanceDispatcher endTransitionHiding:covered ? @[ covered ] : @[]
                                         showing:showing ? @[ node ] : @[]
                                          events:nil
                                          answer:operation.answer
                                           value:operation.value];
    [self finish:operation];
}

#pragma mark - cut (`pop`, `popTo`, `popToRoot`, the user's back)

- (void)startCut:(ANNStackOperation *)operation {
    UIViewController *target = operation.target;
    NSUInteger index = [_shown indexOfObjectIdenticalTo:target];
    if (operation.sourceGone || index == NSNotFound) {
        [self rejectInTurn:operation];
        return;
    }
    NSArray<UIViewController *> *above =
        [_shown subarrayWithRange:NSMakeRange(index + 1, _shown.count - index - 1)];
    // What it would remove went already (a swipe that completed): nothing moves, and no second `ScreenPopped`.
    if (above.count == 0) {
        [operation.answer resolve:operation.value];
        [self finish:operation];
        return;
    }
    NSArray<UIViewController *> *leaving = ANNTopFirst(above);
    operation.leaving = leaving;
    for (UIViewController *entry in leaving) {
        [ANNComponentViewController nodeWillLeave:entry];
    }
    [_display stackEntriesWillMove];
    BOOL onScreen = !operation.silent && [_display stackIsOnScreen];
    // The pop transition is the leaving top's `animations.pop` (`pop` step 3); the screens between it and the target go
    // without one. Whether it is animated is read from the screen the command came from (`popTo`: its target), and from
    // the top for the user's back and a command by the stack's id.
    UIViewController *top = leaving.firstObject;
    ANNStackAnimationOptions *pop = [top resolveOptionsWithDefault].animations.pop;
    UIViewController *enabledFrom = operation.source ?: top;
    Bool *enabled = enabledFrom == top
                        ? pop.enabled
                        : [enabledFrom resolveOptionsWithDefault].animations.pop.enabled;
    BOOL animated = onScreen && [enabled withDefault:YES];
    ANNTransitionPlan *plan = animated ? [ANNTransitionPlan stackPlanOfKind:ANNTransitionKindPop
                                                                  animation:pop
                                                              waitForRender:nil
                                                                  waitLimit:0
                                                                       from:top
                                                                         to:target]
                                       : nil;
    plan.overridesZoom = operation.answer != nil;
    [self drive:operation nodes:[leaving arrayByAddingObject:target]];
    [_shown removeObjectsInRange:NSMakeRange(index + 1, _shown.count - index - 1)];
    // A lower screen that never rendered makes its content now, so that its `WillAppear` has content to report.
    ANNRenderIfNeeded(target);
    if (onScreen) {
        [ANNAppearanceDispatcher beginTransitionShowing:@[ target ]];
    }
    [_display stackChange:ANNStackChangePop toEntries:[_shown copy] animated:animated plan:plan];
    [self whenTransitionOf:operation
                      ends:^(BOOL cancelled) {
                        [self endCut:operation];
                      }];
}

// The order: `DidDisappear` of the visible top (the others never showed), `DidAppear` of the target, the state,
// `ScreenPopped` of each entry that left, top to bottom, then their destroys, then the answer.
- (void)endCut:(ANNStackOperation *)operation {
    UIViewController *target = operation.target;
    NSArray<UIViewController *> *leaving = operation.leaving;
    BOOL showing =
        !operation.silent && [_display stackIsOnScreen] && _shown.lastObject == target;
    __weak ANNStackQueue *weakSelf = self;
    [ANNAppearanceDispatcher endTransitionHiding:operation.silent ? @[] : leaving
                                         showing:showing ? @[ target ] : @[]
                                          events:^{
                                            [weakSelf poppedEntries:leaving reused:@[]];
                                          }
                                          answer:operation.answer
                                           value:operation.value];
    [self finish:operation];
}

#pragma mark - replace (`setStackRoot`, the split view's call)

- (void)startReplace:(ANNStackOperation *)operation {
    if (operation.sourceGone) {
        [self rejectInTurn:operation];
        return;
    }
    UIViewController *top = operation.nodes.lastObject;
    // Like a root: the new top waits unseen for its first content and its bar components together at most 0.5 s; with
    // `waitForRender: true` the content as long as it takes, then the bar components at most 0.5 s; with `false` not at
    // all. The split view's call never waits.
    if (operation.waits && [_display stackIsOnScreen]) {
        Bool *waitForRender = [top resolveOptionsWithDefault].animations.setStackRoot.waitForRender;
        __weak ANNStackQueue *weakSelf = self;
        __weak ANNStackOperation *weakOperation = operation;
        if (!waitForRender.hasValue) {
            operation.waitStarted = CACurrentMediaTime();
            [self waitFor:operation
                        entry:top
                      content:ANNRenderWaitContentNode
                        limit:ANNStackRootRenderLimit
                 unknownLimit:NO
                          bar:ANNRenderWaitBarWithContent
                    barLimits:ANNStackRootBarLimits
                         then:^{
                           [weakSelf startReplaceTransition:weakOperation];
                         }];
            return;
        }
        if (waitForRender.get) {
            [self waitFor:operation
                        entry:top
                      content:ANNRenderWaitContentNode
                        limit:0
                 unknownLimit:YES
                          bar:ANNRenderWaitBarAfterContent
                    barLimits:ANNStackRootBarLimits
                         then:^{
                           [weakSelf startReplaceTransition:weakOperation];
                         }];
            return;
        }
    }
    ANNRenderBeforeTransition(top, operation.waits);
    [self startReplaceTransition:operation];
}

- (void)startReplaceTransition:(ANNStackOperation *)operation {
    if (!operation) {
        return;
    }
    [self unparkOf:operation];
    NSArray<UIViewController *> *nodes = operation.nodes;
    UIViewController *top = nodes.lastObject;
    NSMutableArray<UIViewController *> *leaving = [NSMutableArray new];
    for (UIViewController *entry in _shown.reverseObjectEnumerator) {
        if ([nodes indexOfObjectIdenticalTo:entry] == NSNotFound) {
            [leaving addObject:entry];
        }
    }
    operation.leaving = leaving;
    for (UIViewController *entry in leaving) {
        [ANNComponentViewController nodeWillLeave:entry];
    }
    [_display stackEntriesWillMove];
    BOOL onScreen = !operation.atOnce && [_display stackIsOnScreen];
    BOOL animated = onScreen && operation.animated;
    ANNTransitionPlan *plan = animated && operation.waits
                                  ? [self replacePlanOf:operation from:_shown.lastObject]
                                  : nil;
    [self drive:operation nodes:[leaving arrayByAddingObject:top]];
    for (NSUInteger index = 0; index < nodes.count; index++) {
        [_display stackEntryJoins:nodes[index] first:index == 0];
    }
    [_shown setArray:nodes];
    if (onScreen) {
        [ANNAppearanceDispatcher beginTransitionShowing:@[ top ]];
    }
    [_display stackChange:ANNStackChangeReplace toEntries:nodes animated:animated plan:plan];
    if (operation.atOnce) {
        [self endReplace:operation];
        return;
    }
    [self whenTransitionOf:operation
                      ends:^(BOOL cancelled) {
                        [self endReplace:operation];
                      }];
}

// The new top's own `animations.setStackRoot`, else its own `animations.push`, else none (UIKit's
// `setViewControllers:animated:`); iOS doesn't read `asPush`. Only a `setStackRoot` command: the split view's call
// keeps UIKit's. The wait is the `setStackRoot`'s `waitForRender`.
- (ANNTransitionPlan *)replacePlanOf:(ANNStackOperation *)operation
                                from:(UIViewController *)oldTop {
    UIViewController *top = operation.nodes.lastObject;
    if (!top) {
        return nil;
    }
    ANNAnimationOptions *animations = [top resolveOptionsWithDefault].animations;
    Bool *waitForRender = animations.setStackRoot.waitForRender;
    // With `waitForRender: true` the targets 0.5 s; unset, what the `setStackRoot`'s own wait left.
    NSTimeInterval limit =
        waitForRender.hasValue
            ? ANNCustomTransitionWaitLimit
            : [ANNAnimationRules customWaitLimitAfterWaitStartedAt:operation.waitStarted
                                                      commandLimit:ANNStackRootRenderLimit];
    ANNStackAnimationOptions *animation = animations.setStackRoot;
    if (![ANNAnimationRules stackIsOwn:animation]) {
        animation = animations.push;
    }
    return [ANNTransitionPlan stackPlanOfKind:ANNTransitionKindReplace
                                    animation:animation
                                waitForRender:waitForRender
                                    waitLimit:limit
                                         from:oldTop
                                           to:top];
}

// `ScreenPopped` of each old entry whose id no new node takes, top to bottom, then their destroys; an old entry whose
// id a new node took gets no `ScreenPopped` and is destroyed after the others.
- (void)endReplace:(ANNStackOperation *)operation {
    NSArray<UIViewController *> *nodes = operation.nodes;
    UIViewController *top = nodes.lastObject;
    NSMutableSet<NSString *> *newIds = [NSMutableSet new];
    for (UIViewController *node in nodes) {
        [newIds addObject:ANNIdOf(node)];
        for (ANNComponentViewController *screen in ANNStackScreensOfEntry(node)) {
            [newIds addObject:ANNIdOf(screen)];
        }
    }
    NSMutableArray<UIViewController *> *popped = [NSMutableArray new];
    NSMutableArray<UIViewController *> *reused = [NSMutableArray new];
    for (UIViewController *entry in operation.leaving) {
        [([newIds containsObject:ANNIdOf(entry)] ? reused : popped) addObject:entry];
    }
    BOOL showing = !operation.atOnce && [_display stackIsOnScreen] && _shown.lastObject == top;
    __weak ANNStackQueue *weakSelf = self;
    [ANNAppearanceDispatcher endTransitionHiding:operation.leaving
                                         showing:showing ? @[ top ] : @[]
                                          events:^{
                                            [weakSelf poppedEntries:popped reused:reused];
                                          }
                                          answer:operation.answer
                                           value:operation.value];
    // One answer: a failure after this rejects nothing.
    dispatch_block_t completion = operation.completion;
    operation.completion = nil;
    operation.rejection = nil;
    if (completion && operation.isCurrent) {
        completion();
    }
    // The lower new screens make their content now, under the top.
    if (nodes.count > 1) {
        NSRange lower = NSMakeRange(0, nodes.count - 1);
        [_display stackRenderLowerEntries:[nodes subarrayWithRange:lower]];
    }
    [self finish:operation];
}

#pragma mark - The swipe back

- (BOOL)swipeBeganFrom:(UIViewController *)top revealing:(UIViewController *)revealed {
    ANNAssertMainQueue();
    if (_current || _destroyed || _shown.lastObject != top ||
        [_shown indexOfObjectIdenticalTo:revealed] == NSNotFound) {
        return NO;
    }
    ANNStackOperation *operation = [self operationOfKind:ANNStackOperationSwipe
                                                  answer:nil
                                                    what:@"go back"
                                             componentId:nil
                                                  source:nil];
    operation.target = revealed;
    operation.leaving = @[ top ];
    _current = operation;
    // The stack reports the swipe itself, as a command: UIKit's appearance callbacks of the two screens send nothing
    // meanwhile.
    // The lower screens still rendering in the stack's view leave it before UIKit moves the revealed one.
    [_display stackEntriesWillMove];
    [self drive:operation nodes:@[ top, revealed ]];
    ANNRenderIfNeeded(revealed);
    [ANNAppearanceDispatcher beginTransitionShowing:@[ revealed ]];
    [self whenTransitionOf:operation
                      ends:^(BOOL cancelled) {
                        [self endSwipe:operation cancelled:cancelled];
                      }];
    return YES;
}

- (void)endSwipe:(ANNStackOperation *)operation cancelled:(BOOL)cancelled {
    UIViewController *top = operation.leaving.firstObject;
    UIViewController *revealed = operation.target;
    // A cancelled swipe: the screen under goes again; the top never left.
    if (cancelled) {
        [ANNAppearanceDispatcher endTransitionHiding:@[ revealed ]
                                             showing:@[]
                                              events:nil
                                              answer:nil
                                               value:nil];
        [self finish:operation];
        return;
    }
    [_shown removeObjectIdenticalTo:top];
    // The top and what was accepted over it leave `entries` now: a push from it is interrupted, a command from it or to
    // it rejects in its turn.
    [self removeEntriesFrom:top];
    [ANNComponentViewController nodeWillLeave:top];
    BOOL showing = [_display stackIsOnScreen] && _shown.lastObject == revealed;
    __weak ANNStackQueue *weakSelf = self;
    // No `NavigationButtonPressed` on a finished swipe.
    [ANNAppearanceDispatcher endTransitionHiding:@[ top ]
                                         showing:showing ? @[ revealed ] : @[]
                                          events:^{
                                            [weakSelf poppedEntries:@[ top ] reused:@[]];
                                          }
                                          answer:nil
                                           value:nil];
    [self finish:operation];
}

#pragma mark - UIKit's own changes

- (void)uikitShows:(NSArray<UIViewController *> *)viewControllers {
    ANNAssertMainQueue();
    // The queue's own transition reports itself.
    if (_destroyed || (_current && !_current.waiting)) {
        return;
    }
    NSUInteger kept = 0;
    while (kept < _shown.count &&
           [viewControllers indexOfObjectIdenticalTo:_shown[kept]] != NSNotFound) {
        kept++;
    }
    if (kept == _shown.count) {
        return;
    }
    NSRange range = NSMakeRange(kept, _shown.count - kept);
    NSArray<UIViewController *> *gone = ANNTopFirst([_shown subarrayWithRange:range]);
    [_shown removeObjectsInRange:range];
    [self removeEntriesFrom:gone.lastObject];
    for (UIViewController *entry in gone) {
        [ANNComponentViewController nodeWillLeave:entry];
    }
    // UIKit reported the appearance of its own transition; what left gets its `ScreenPopped` and goes.
    __weak ANNStackQueue *weakSelf = self;
    [ANNAppearanceDispatcher endTransitionHiding:gone
                                         showing:@[]
                                          events:^{
                                            [weakSelf poppedEntries:gone reused:@[]];
                                          }
                                          answer:nil
                                           value:nil];
    [_display stackOperationEnded];
    [self scheduleRunNext];
}

// `entry` and everything over it leave `entries`: a push of them is interrupted, a queued command from them or to them
// rejects in its turn.
- (void)removeEntriesFrom:(UIViewController *)entry {
    NSUInteger index = entry ? [_entries indexOfObjectIdenticalTo:entry] : NSNotFound;
    if (index == NSNotFound) {
        return;
    }
    NSRange range = NSMakeRange(index, _entries.count - index);
    NSArray<UIViewController *> *removed = [_entries subarrayWithRange:range];
    [_entries removeObjectsInRange:range];
    [self interruptPushesOf:removed];
    for (ANNStackOperation *pending in _pending) {
        UIViewController *source = pending.source;
        UIViewController *target = pending.target;
        if ((source && [removed indexOfObjectIdenticalTo:source] != NSNotFound) ||
            (target && [removed indexOfObjectIdenticalTo:target] != NSNotFound)) {
            pending.sourceGone = YES;
        }
    }
}

#pragma mark - The stack

- (void)stackWentOffScreen {
    ANNStackOperation *operation = _current;
    if (operation.waiting) {
        // The wait's phase ends without `markReady`: the operation runs as in a stack off screen, and its top appears
        // when the stack shows.
        [operation.wait endPhase];
    }
}

- (NSArray<UIViewController *> *)destroyQueue {
    ANNAssertMainQueue();
    NSArray<UIViewController *> *nodes = self.heldNodes;
    _destroyed = YES;
    _waitingForUIKit = NO;
    NSMutableArray<ANNStackOperation *> *operations = [_pending mutableCopy];
    if (_current) {
        [operations insertObject:_current atIndex:0];
    }
    _current = nil;
    [_pending removeAllObjects];
    for (ANNStackOperation *operation in operations) {
        [self endWaitOf:operation];
        [self unparkOf:operation];
        [self stopDrivingOf:operation];
        if (operation.done) {
            continue;
        }
        operation.done = YES;
        operation.wait.ended = nil;
        if (operation.isCurrent) {
            [self answerRejection:operation
                             text:[ANNErrorTexts screenNotShown:operation.componentId ?: @""]];
        }
    }
    [_entries removeAllObjects];
    [_shown removeAllObjects];
    return nodes;
}

#pragma mark - Waits

// The operation's render wait (`ANNRenderWait`), its token, limits and bar components. With content, the entry waits
// for it in the stack's view, its appearance driven: the wait ends at the entry's ready handler (set first, then the
// park and `render`, then the test of 16.1), at `limit` with `markReady`, or with `unknownLimit` for a screen whose
// rendered state stays unknown after 1 s. Without content, only the bar components, without parking (bar roots render
// out of a window). A wait that would end does nothing once its operation ended or belongs to an old JavaScript; its
// end runs protected (the end steps of the operation, `then`).
- (void)waitFor:(ANNStackOperation *)operation
           entry:(UIViewController *)entry
         content:(ANNRenderWaitContent)content
           limit:(NSTimeInterval)limit
    unknownLimit:(BOOL)unknownLimit
             bar:(ANNRenderWaitBar)bar
       barLimits:(ANNBarWaitLimits)barLimits
            then:(dispatch_block_t)then {
    __weak ANNStackQueue *weakSelf = self;
    __weak ANNStackOperation *weakOperation = operation;
    // The screens the wait is about are those the entry waits for (a pair: the ones it puts in the window).
    NSArray<UIViewController *> * (^screens)(void) = ^NSArray<UIViewController *> * {
      return entry.waitedComponentViewControllers;
    };
    ANNRenderWait *wait = [[ANNRenderWait alloc] initWithContent:content
                                                            node:entry
                                                         screens:screens
                                                           limit:limit
                                                             bar:bar
                                                       barLimits:barLimits];
    wait.unknownContentRule = unknownLimit;
    wait.canEnd = ^BOOL {
      ANNStackQueue *queue = weakSelf;
      ANNStackOperation *waiting = weakOperation;
      return queue && waiting && !waiting.done && waiting.isCurrent && !queue->_destroyed;
    };
    wait.protect = ^(dispatch_block_t step) {
      [weakSelf protect:weakOperation step:step];
    };
    wait.ended = then;
    if (content != ANNRenderWaitContentNone) {
        wait.setUp = ^{
          ANNStackQueue *queue = weakSelf;
          ANNStackOperation *waiting = weakOperation;
          if (!queue || !waiting) {
              return;
          }
          // A pair is ready once the screens it shows are only when it waits for them.
          if (![entry isKindOfClass:ANNComponentViewController.class]) {
              [entry setWaitForRender:YES];
          }
          // The place in the window loads the view, which renders a screen nobody rendered. A native screen is ready
          // inside the load that parking makes: the wait's test after the setup ends its wait.
          waiting.waited = entry;
          waiting.parked = YES;
          [queue->_display stackPark:entry];
          [entry render];
          for (ANNComponentViewController *screen in ANNStackScreensOfEntry(entry)) {
              [screen updateRootLayout];
          }
        };
    }
    operation.wait = wait;
    [wait start];
}

- (void)endWaitOf:(ANNStackOperation *)operation {
    [operation.wait cancel];
}

- (void)unparkOf:(ANNStackOperation *)operation {
    if (!operation.parked) {
        return;
    }
    operation.parked = NO;
    [_display stackUnpark:operation.waited];
}

#pragma mark - The end steps

// From its start to its end a command drives the appearance of the screens it moves: UIKit's callbacks of those screens
// send nothing, whenever UIKit calls them against the coordinator's completion.
- (void)drive:(ANNStackOperation *)operation nodes:(NSArray<UIViewController *> *)nodes {
    [self stopDrivingOf:operation];
    operation.driven = nodes;
    [ANNAppearanceDispatcher driveAppearanceOf:nodes];
}

- (void)stopDrivingOf:(ANNStackOperation *)operation {
    NSArray<UIViewController *> *driven = operation.driven;
    if (driven) {
        operation.driven = nil;
        [ANNAppearanceDispatcher stopDrivingAppearanceOf:driven];
    }
}

// The end of the transition, guarded: a completion of an operation that ended already, or of a JavaScript that went,
// does nothing (a reload's old stack stays as it is until its destroy).
- (void)whenTransitionOf:(ANNStackOperation *)operation ends:(void (^)(BOOL cancelled))ended {
    __weak ANNStackQueue *weakSelf = self;
    [_display stackWhenTransitionEnds:^(BOOL cancelled) {
      ANNStackQueue *queue = weakSelf;
      if (!queue || operation.done || !operation.isCurrent || queue->_destroyed) {
          return;
      }
      [queue protect:operation
                step:^{
                  ended(cancelled);
                }];
    }];
}

// Every `ScreenPopped` first, then the destroys in the same order: the content of each stops only after all the events.
- (void)poppedEntries:(NSArray<UIViewController *> *)popped
               reused:(NSArray<UIViewController *> *)reused {
    for (UIViewController *entry in popped) {
        [ANNAppearanceDispatcher screenPopped:entry];
    }
    [self destroyNodes:popped];
    [self destroyNodes:reused];
}

- (void)destroyNodes:(NSArray<UIViewController *> *)nodes {
    for (UIViewController *node in nodes) {
        @try {
            [node destroy];
        } @catch (NSException *exception) {
            RCTLogWarn(@"[ApexNavigation] could not destroy the screen '%@': %@", ANNIdOf(node),
                       exception.reason ?: exception.name);
        }
    }
}

- (void)finish:(ANNStackOperation *)operation {
    operation.done = YES;
    operation.wait.ended = nil;
    [self stopDrivingOf:operation];
    if (_current == operation) {
        _current = nil;
    }
    @try {
        [_display stackOperationEnded];
    } @catch (NSException *exception) {
        RCTLogWarn(@"[ApexNavigation] %@",
                   [ANNCommandRunner rejectionOf:exception what:@"show the top of the stack"]);
    }
    [self scheduleRunNext];
}

#pragma mark - Failures

// The steps after a command's body run outside the runner's protection: a failure rejects that operation with its error
// text, ends what it holds, destroys what it made and never showed, and the queue goes on.
- (void)protect:(ANNStackOperation *)operation step:(dispatch_block_t)step {
    @try {
        step();
    } @catch (NSException *exception) {
        [self operation:operation failed:exception];
    }
}

- (void)operation:(ANNStackOperation *)operation failed:(NSException *)exception {
    NSString *text = [ANNCommandRunner rejectionOf:exception what:operation.what];
    if (operation.done) {
        RCTLogWarn(@"[ApexNavigation] %@", text);
        return;
    }
    [self endWaitOf:operation];
    [self unparkOf:operation];
    [self stopDrivingOf:operation];
    if (operation.answer || operation.rejection) {
        [self answerRejection:operation text:text];
    } else {
        RCTLogWarn(@"[ApexNavigation] %@", text);
    }
    [self destroyUnshownNodesOf:operation];
    [self rebuildEntries];
    [self finish:operation];
}

// An operation whose screen went before its turn: it rejects with its command's id and destroys silently what it made.
- (void)rejectInTurn:(ANNStackOperation *)operation {
    [self answerRejection:operation
                     text:[ANNErrorTexts screenNotShown:operation.componentId ?: @""]];
    [self destroyUnshownNodesOf:operation];
    [self rebuildEntries];
    [self finish:operation];
}

- (void)reject:(ANNStackOperation *)operation text:(NSString *)text {
    operation.done = YES;
    [self answerRejection:operation text:text];
}

- (void)answerRejection:(ANNStackOperation *)operation text:(NSString *)text {
    if (operation.answer) {
        [operation.answer reject:text];
        return;
    }
    ANNTransitionRejectionBlock rejection = operation.rejection;
    operation.rejection = nil;
    operation.completion = nil;
    if (rejection && operation.isCurrent) {
        rejection(text, nil);
    }
}

- (void)destroyUnshownNodesOf:(ANNStackOperation *)operation {
    NSMutableArray<UIViewController *> *made = [NSMutableArray new];
    if (operation.kind == ANNStackOperationPush && operation.node) {
        [made addObject:operation.node];
    } else if (operation.kind == ANNStackOperationReplace && operation.nodes) {
        [made addObjectsFromArray:operation.nodes];
    }
    // A cut or a replace that failed after it took entries off what shows leaves them in no list: they go too.
    for (UIViewController *entry in operation.leaving ?: @[]) {
        if ([_entries indexOfObjectIdenticalTo:entry] == NSNotFound) {
            [made addObject:entry];
        }
    }
    NSMutableArray<UIViewController *> *unshown = [NSMutableArray new];
    for (UIViewController *node in made) {
        if ([_shown indexOfObjectIdenticalTo:node] == NSNotFound &&
            [unshown indexOfObjectIdenticalTo:node] == NSNotFound) {
            [unshown addObject:node];
        }
    }
    operation.leaving = nil;
    [self destroyNodes:unshown];
}

// `entries` after an operation that failed: what shows, with what the queued operations accepted over it.
- (void)rebuildEntries {
    NSMutableArray<UIViewController *> *rebuilt = [_shown mutableCopy];
    for (ANNStackOperation *operation in _pending) {
        switch (operation.kind) {
            case ANNStackOperationPush:
                if (operation.node) {
                    [rebuilt addObject:operation.node];
                }
                break;
            case ANNStackOperationReplace:
                [rebuilt setArray:operation.nodes ?: @[]];
                break;
            case ANNStackOperationCut: {
                NSUInteger index = [rebuilt indexOfObjectIdenticalTo:operation.target];
                if (index != NSNotFound) {
                    NSRange above = NSMakeRange(index + 1, rebuilt.count - index - 1);
                    [rebuilt removeObjectsInRange:above];
                }
                break;
            }
            default:
                break;
        }
    }
    [_entries setArray:rebuilt];
}

@end

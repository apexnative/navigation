#import "ANNNodeHelper.h"
#import "ANNAssert.h"
#import "ANNDefaultOptions.h"
#import "ANNNodeRegistry.h"
#import "ANNOptionsCache.h"
#import "ApexNavigation+Internal.h"
#import "UIViewController+LayoutProtocol.h"

// Whether `nodes` are the nodes a weak list kept, in order: a node that went away is a difference.
static BOOL ANNSameNodes(NSPointerArray *kept, NSArray<UIViewController *> *nodes) {
    if (kept.count != nodes.count) {
        return NO;
    }
    for (NSUInteger i = 0; i < nodes.count; i++) {
        if ([kept pointerAtIndex:i] != (__bridge void *)nodes[i]) {
            return NO;
        }
    }
    return YES;
}

static NSPointerArray *ANNWeakNodes(NSArray<UIViewController *> *nodes) {
    NSPointerArray *list = [NSPointerArray weakObjectsPointerArray];
    for (UIViewController *node in nodes) {
        [list addPointer:(__bridge void *)node];
    }
    return list;
}

NSString *const ANNContainerReadyHandlerKey = @"ApexNavigation.container";

// Set while a node works out the app's resolution (`appResolution`): no user layer is written. Main queue.
static BOOL ANNUserLayersLeftOut = NO;

@implementation ANNNodeHelper {
    ANNNavigationOptions *_options;
    // The user layer: the choices by key, and their keys in the order they were set, the order they are written in.
    NSMutableDictionary<NSString *, ANNUserChoice *> *_userChoices;
    NSMutableArray<NSString *> *_userChoiceKeys;
    // The ready handlers by key, and their keys in the order they were first set.
    NSMutableDictionary<NSString *, dispatch_block_t> *_readyHandlers;
    NSMutableArray<NSString *> *_readyKeys;
    // The cache of `resolveOptionsWithDefault`: the result, the generation of the options it was built at, and the
    // nodes it was built along (the parents, and the visible chain of current children), kept weakly.
    ANNNavigationOptions *_resolved;
    NSUInteger _resolvedGeneration;
    NSPointerArray *_resolvedParents;
    NSPointerArray *_resolvedChain;
}

- (instancetype)initWithNode:(UIViewController *)node {
    if (self = [super init]) {
        _node = node;
    }
    return self;
}

- (void)setUpWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                    creator:(id<ANNComponentViewCreator>)creator
                    options:(ANNNavigationOptions *)options
             defaultOptions:(ANNNavigationOptions *)defaultOptions
                  presenter:(ANNNodePresenter *)presenter
               eventEmitter:(ANNEventEmitter *)eventEmitter {
    _layoutInfo = layoutInfo;
    _creator = creator;
    self.options = options;
    _presenter = presenter;
    // One emitter per node: without one given, the node keeps the emitter of the JavaScript that runs now, the same
    // one its screen's record takes, so that every event of the node goes to one JavaScript.
    _eventEmitter = eventEmitter ?: ApexNavigation.eventEmitter;
    UIViewController *node = _node;
    if (node) {
        [presenter bindViewController:node];
    }
    // The node's own answer: a container may resolve its options otherwise.
    [presenter applyOptionsOnInit:node ? [node resolveOptions] : [self resolveOptions]];
}

// A UIKit parent that is no node of the layout (UIKit's More navigation holding a tab's screens) gives way to the
// fallback the container set, so that merges and resolution go through the layout. An explicit parent (a split view's
// column) wins over both.
- (UIViewController *)layoutParent {
    UIViewController *explicitParent = _explicitLayoutParent;
    if (explicitParent) {
        return explicitParent;
    }
    UIViewController *parent = _node.parentViewController;
    if (!parent) {
        return _layoutParentFallback;
    }
    if (parent.isLayoutNode) {
        return parent;
    }
    return _layoutParentFallback ?: parent;
}

#pragma mark - Own options

- (ANNNavigationOptions *)options {
    return _options;
}

// Every write of own options passes here: the one-shot options never stay, and every cache misses.
- (void)setOptions:(ANNNavigationOptions *)options {
    ANNAssertMainQueue();
    _options = [options withoutOneShotOptions];
    [ANNOptionsCache optionsChanged];
}

- (ANNNavigationOptions *)defaultOptions {
    return ANNDefaultOptions.current;
}

- (void)setDefaultOptions:(ANNNavigationOptions *)defaultOptions {
}

// With a user layer, the choices meet the merge as on Android: they see the app's resolution before it and after it
// (the merge in place for that, uncached), and those the merge changes leave; the setter then moves the cache once for
// both.
- (void)mergeOwnOptions:(ANNNavigationOptions *)options {
    ANNAssertMainQueue();
    ANNNavigationOptions *own = _options ?: [ANNNavigationOptions new];
    ANNNavigationOptions *merged = [own copy];
    [merged mergeOptions:options];
    if (_userChoiceKeys.count == 0) {
        self.options = merged;
        return;
    }
    ANNNavigationOptions *before = [self appResolution];
    ANNNavigationOptions *stored = [merged withoutOneShotOptions];
    _options = stored;
    ANNNavigationOptions *after = [self appResolution];
    [self userChoicesMeetMerge:options own:own before:before after:after];
    self.options = stored;
}

- (void)changeOwnOptions:(void (^)(ANNNavigationOptions *options))change {
    ANNNavigationOptions *changed = _options ? [_options copy] : [ANNNavigationOptions new];
    change(changed);
    self.options = changed;
}

#pragma mark - The user layer

- (void)setUserChoice:(ANNUserChoice *)choice forKey:(NSString *)key {
    ANNAssertMainQueue();
    if (!choice && !_userChoices[key]) {
        return;
    }
    if (!_userChoices) {
        _userChoices = [NSMutableDictionary new];
        _userChoiceKeys = [NSMutableArray new];
    }
    // A choice set again goes last: it is the newest.
    [_userChoiceKeys removeObject:key];
    if (choice) {
        _userChoices[key] = choice;
        [_userChoiceKeys addObject:key];
    } else {
        [_userChoices removeObjectForKey:key];
    }
    [ANNOptionsCache optionsChanged];
}

- (ANNUserChoice *)userChoiceForKey:(NSString *)key {
    return _userChoices[key];
}

// Each choice answers the merge itself: it stays, keeps a part, or goes. The order of the rest stays.
- (void)userChoicesMeetMerge:(ANNNavigationOptions *)merge
                         own:(ANNNavigationOptions *)own
                      before:(ANNNavigationOptions *)before
                       after:(ANNNavigationOptions *)after {
    for (NSString *key in [_userChoiceKeys copy]) {
        ANNUserChoice *choice = _userChoices[key];
        ANNUserChoice *kept = [choice choiceAfterMerge:merge own:own before:before after:after];
        if (kept == choice) {
            continue;
        }
        if (kept) {
            _userChoices[key] = kept;
        } else {
            [_userChoices removeObjectForKey:key];
            [_userChoiceKeys removeObject:key];
        }
    }
}

// The node's resolution with the defaults and without any user layer: what the app's options alone give (Android's
// `resolveCurrentOptions().withDefaultOptions`). Built now, never cached: the resolution reads no cache on the way.
- (ANNNavigationOptions *)appResolution {
    BOOL leftOut = ANNUserLayersLeftOut;
    ANNUserLayersLeftOut = YES;
    @try {
        return [self resolveOptionsWithDefaultUncached];
    } @finally {
        ANNUserLayersLeftOut = leftOut;
    }
}

- (void)applyUserLayerTo:(ANNNavigationOptions *)options {
    if (ANNUserLayersLeftOut) {
        return;
    }
    for (NSString *key in _userChoiceKeys) {
        [_userChoices[key] applyTo:options];
    }
}

#pragma mark - Resolving

- (ANNNavigationOptions *)resolveOptions {
    ANNNavigationOptions *result = _options ? [_options copy] : [ANNNavigationOptions new];
    [self applyUserLayerTo:result];
    UIViewController *child = [_node getCurrentChild];
    if (child) {
        [result mergeOptions:[child resolveOptions]];
    }
    return result;
}

- (ANNNavigationOptions *)resolveOptionsWithDefault {
    UIViewController *node = _node;
    if (!node) {
        return [self resolveOptions];
    }
    NSMutableArray<UIViewController *> *parents = [NSMutableArray new];
    for (UIViewController *parent = node.layoutParent; parent; parent = parent.layoutParent) {
        [parents addObject:parent];
    }
    NSMutableArray<UIViewController *> *chain = [NSMutableArray new];
    for (UIViewController *child = [node getCurrentChild]; child; child = [child getCurrentChild]) {
        [chain addObject:child];
    }
    if (_resolved && _resolvedGeneration == ANNOptionsCache.generation && ANNSameNodes(_resolvedParents, parents) &&
        ANNSameNodes(_resolvedChain, chain)) {
        return _resolved;
    }
    ANNNavigationOptions *result = [self resolveOfNode:node parents:parents];
    _resolved = result;
    _resolvedGeneration = ANNOptionsCache.generation;
    _resolvedParents = ANNWeakNodes(parents);
    _resolvedChain = ANNWeakNodes(chain);
    return result;
}

- (ANNNavigationOptions *)resolveOptionsWithDefaultUncached {
    UIViewController *node = _node;
    if (!node) {
        return [self resolveOptions];
    }
    NSMutableArray<UIViewController *> *parents = [NSMutableArray new];
    for (UIViewController *parent = node.layoutParent; parent; parent = parent.layoutParent) {
        [parents addObject:parent];
    }
    return [self resolveOfNode:node parents:parents];
}

// Built on copies: the defaults never go into a node's own options. Each parent's user layer goes over its own
// options; a node without a current child writes its own layer once more at the end, for the lists it takes from its
// parents or the defaults (a toggle of a button of the stack's). Writing a choice twice gives the same result.
- (ANNNavigationOptions *)resolveOfNode:(UIViewController *)node
                                parents:(NSArray<UIViewController *> *)parents {
    ANNNavigationOptions *result = [ANNDefaultOptions.current copy];
    for (UIViewController *parent in parents.reverseObjectEnumerator) {
        ANNNavigationOptions *own = parent.options;
        if (own) {
            [result mergeOptions:own];
        }
        [parent.nodeHelper applyUserLayerTo:result];
    }
    [result mergeOptions:[node resolveOptions]];
    if (![node getCurrentChild]) {
        [self applyUserLayerTo:result];
    }
    return result;
}

#pragma mark - Rendering

// What the node shows renders through its current child (a side menu's center); a container with more to render (the
// tabs, a side menu's menus) renders the rest itself.
- (void)render {
    [[_node getCurrentChild] render];
}

// Every handler of this moment runs once: a handler may set or remove handlers, which wait for the next moment.
- (void)readyForPresentation {
    if (_readyKeys.count == 0) {
        return;
    }
    NSArray<NSString *> *keys = _readyKeys;
    NSDictionary<NSString *, dispatch_block_t> *handlers = _readyHandlers;
    _readyKeys = nil;
    _readyHandlers = nil;
    for (NSString *key in keys) {
        dispatch_block_t handler = handlers[key];
        if (handler) {
            handler();
        }
    }
}

- (void)setReadyHandler:(dispatch_block_t)handler forKey:(NSString *)key {
    if (!_readyHandlers) {
        _readyHandlers = [NSMutableDictionary new];
        _readyKeys = [NSMutableArray new];
    }
    if (!_readyHandlers[key]) {
        [_readyKeys addObject:key];
    }
    _readyHandlers[key] = [handler copy];
}

- (void)removeReadyHandlerForKey:(NSString *)key {
    if (!_readyHandlers[key]) {
        return;
    }
    [_readyHandlers removeObjectForKey:key];
    [_readyKeys removeObject:key];
}

#pragma mark - Destroy

- (BOOL)markDestroyed {
    if (_destroyed) {
        return NO;
    }
    _destroyed = YES;
    // Nothing waits for a node that went: its waiters end by their own paths (a limit, a cancel).
    _readyHandlers = nil;
    _readyKeys = nil;
    return YES;
}

#pragma mark - The registry

- (void)registerNode {
    UIViewController *node = _node;
    NSString *componentId = _layoutInfo.componentId;
    if (node && componentId) {
        [ANNNodeRegistry registerNode:node componentId:componentId];
    }
}

- (void)unregisterNode {
    UIViewController *node = _node;
    NSString *componentId = _layoutInfo.componentId;
    if (node && componentId) {
        [ANNNodeRegistry unregisterNode:node componentId:componentId];
    }
}

@end

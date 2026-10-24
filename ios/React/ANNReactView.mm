#import "ANNReactView.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "ANNEventEmitter.h"
#import "ANNScreenAppearance.h"
#import "ANNScreenReadiness.h"
#import "ANNSlotContainers.h"
#import "ANNSurfaceHostingView.h"
#import <React/RCTFabricSurface.h>
#import <React/RCTSurfacePresenter.h>
#import <React/RCTSurfaceView.h>
#import <ReactCommon/RCTHost.h>

@interface ANNReactView () <RCTSurfacePresenterObserver, ANNScreenReadinessListener>
@end

@implementation ANNReactView {
    NSString *_componentId;
    ANNComponentType _componentType;
    ANNEventEmitter *_eventEmitter;
    ANNReactViewReadyCompletionBlock _reactViewReadyBlock;
    NSMutableDictionary<NSString *, void (^)(void)> *_contentDidMountHandlers;
    RCTFabricSurface *_surface;
    ANNSurfaceHostingView *_hostingView;
    // The presenter the observer was added to; the same one removes it at the stop.
    __weak RCTSurfacePresenter *_surfacePresenter;
    BOOL _rendersAtOnce;
    // React Native mounts our surface now: a signal that comes inside the mount makes the view ready at its end, once
    // the whole transaction is on screen and React Native is out of its mounting.
    BOOL _mounting;
    BOOL _renderedDuringMount;
    BOOL _ready;
    BOOL _stopped;
    // The root mounted once, and who waits for that (`whenMounted:`).
    BOOL _mounted;
    NSMutableArray<dispatch_block_t> *_mountHandlers;
    NSDictionary<NSString *, id> *_nativeProps;
}

@synthesize passThroughTouches = _passThroughTouches;
@synthesize appearance = _appearance;

- (instancetype)initWithHost:(RCTHost *)host
                 componentId:(NSString *)componentId
                        name:(NSString *)name
               componentType:(ANNComponentType)componentType
                eventEmitter:(ANNEventEmitter *)eventEmitter
         reactViewReadyBlock:(ANNReactViewReadyCompletionBlock)reactViewReadyBlock {
    // A root without an id is never made: the command that asked for it rejects.
    if (componentId.length == 0) {
        @throw [NSException exceptionWithName:NSInvalidArgumentException
                                       reason:@"a React view needs the id of its screen"
                                     userInfo:nil];
    }
    if (self = [super initWithFrame:CGRectZero]) {
        _componentId = [componentId copy];
        _moduleName = [name copy];
        _componentType = componentType;
        _eventEmitter = eventEmitter;
        _reactViewReadyBlock = [reactViewReadyBlock copy];
        self.backgroundColor = UIColor.clearColor;
        NSString *componentName = ANNComponentNameWithoutSlotPrefix(_moduleName ?: @"");
        _appearance = [[ANNScreenAppearance alloc] initWithComponentId:_componentId
                                                         componentName:componentName
                                                         componentType:componentType
                                                               content:self
                                                          eventEmitter:eventEmitter];
        // The safe area view of a component of a bar or of a tab cell renders at once: the core answers it by the id. A
        // screen and the tabs' accessory wait for their layout.
        _rendersAtOnce = componentType != ANNComponentTypeComponent &&
                         componentType != ANNComponentTypeBottomTabsAccessory;
        if (_rendersAtOnce) {
            [ANNScreenReadiness setRendersAtOnce:YES forScreenId:_componentId];
        }
        // A new root starts with nothing said for its id: late signals of an old surface with the same id (restarted
        // by React Native after a reload) or of a root that went don't make it ready or take its children fallback
        // away. A screen's root that still holds the id is going anyway; a bar component's id may be shown by a live
        // root of another screen too, whose signals stay (each root answers by its own render).
        if (!_rendersAtOnce || [ANNScreenReadiness listenerCountForScreenId:_componentId] == 0) {
            [ANNScreenReadiness removeScreenId:_componentId];
        }
        // Both before the surface exists, so its first mount and its first signal are never missed.
        _surfacePresenter = host.surfacePresenter;
        [_surfacePresenter addObserver:self];
        [self makeSurfaceWithHost:host];
        [ANNScreenReadiness setListener:self forScreenId:_componentId];
    }
    return self;
}

// The surface's props are only the id: the props of the screen stay in JavaScript. The host starts the surface itself
// once the bundle ran (`createSurfaceWithModuleName:`); a second start of ours could attach it to its view twice, which
// React Native asserts against. The hosting view sizes the surface to its own size exactly, in its `layoutSubviews`.
- (void)makeSurfaceWithHost:(RCTHost *)host {
    if (!host) {
        return;
    }
    _surface = [host createSurfaceWithModuleName:_moduleName
                               initialProperties:@{@"componentId" : _componentId}];
    _hostingView = [[ANNSurfaceHostingView alloc]
        initWithSurface:_surface
        sizeMeasureMode:RCTSurfaceSizeMeasureModeWidthExact | RCTSurfaceSizeMeasureModeHeightExact];
    _hostingView.backgroundColor = UIColor.clearColor;
    _hostingView.frame = self.bounds;
    _hostingView.autoresizingMask =
        UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self addSubview:_hostingView];
}

- (NSString *)componentId {
    return _componentId;
}

#pragma mark - The surface's size

// The hosting view, the only place that sizes the surface, follows this view at every layout of it and of the screen,
// so the surface's size never waits for a later layout pass. A render on its way during a size change is laid out
// again by the hosting view at the next mount (`-[ANNSurfaceHostingView surfaceDidMount]`). The hosting view is the
// core's; React Native's views inside it are never touched.
- (void)layoutSubviews {
    [super layoutSubviews];
    [self updateSurfaceSizeIfNeeded];
}

- (void)updateSurfaceSizeIfNeeded {
    ANNSurfaceHostingView *hostingView = _hostingView;
    if (!hostingView || !_surface || _stopped) {
        return;
    }
    if (!CGRectEqualToRect(hostingView.frame, self.bounds)) {
        hostingView.frame = self.bounds;
    }
    CGSize size = hostingView.bounds.size;
    if (CGSizeEqualToSize(_surface.minimumSize, size) &&
        CGSizeEqualToSize(_surface.maximumSize, size)) {
        return;
    }
    [hostingView setNeedsLayout];
    [hostingView layoutIfNeeded];
}

- (ANNReactComponentType)componentType {
    return _componentType;
}

- (RCTFabricSurface *)surface {
    return _surface;
}

- (BOOL)isReady {
    return _ready;
}

- (BOOL)hasMounted {
    return _mounted;
}

- (void)whenMounted:(dispatch_block_t)handler {
    if (_stopped) {
        return;
    }
    if (_mounted) {
        handler();
        return;
    }
    if (!_mountHandlers) {
        _mountHandlers = [NSMutableArray new];
    }
    [_mountHandlers addObject:[handler copy]];
}

// The first mount of the root (or its readiness, which comes with a mount): each waiter hears it once.
- (void)markMounted {
    if (_mounted || _stopped) {
        return;
    }
    _mounted = YES;
    NSArray<dispatch_block_t> *handlers = _mountHandlers;
    _mountHandlers = nil;
    for (dispatch_block_t handler in handlers) {
        handler();
    }
}

- (void)setNativeProps:(NSDictionary<NSString *, id> *)nativeProps {
    ANNAssertMainQueue();
    if (_stopped || !_surface || [_nativeProps isEqualToDictionary:nativeProps]) {
        return;
    }
    _nativeProps = [nativeProps copy];
    NSMutableDictionary<NSString *, id> *properties = [nativeProps mutableCopy];
    properties[@"componentId"] = _componentId;
    _surface.properties = properties;
}

#pragma mark - Readiness

// Once: the content-mount handlers of this moment run first (the dispatcher's `ANNAppearance` key sends the events it
// held), then the ready block (the node's `readyForPresentation`, which ends a command's wait). Nothing waits here on
// its own: the waiting command has its limit.
- (void)becomeReady {
    if (_ready || _stopped) {
        return;
    }
    _ready = YES;
    [self markMounted];
    [self runContentDidMountHandlers];
    ANNReactViewReadyCompletionBlock readyBlock = _reactViewReadyBlock;
    _reactViewReadyBlock = nil;
    if (readyBlock) {
        readyBlock();
    }
}

- (void)runContentDidMountHandlers {
    // A handler may set or remove handlers: each one of this moment runs once.
    for (void (^handler)(void) in _contentDidMountHandlers.allValues) {
        handler();
    }
}

- (void)setContentDidMount:(void (^)(void))handler forKey:(NSString *)key {
    if (!_contentDidMountHandlers) {
        _contentDidMountHandlers = [NSMutableDictionary new];
    }
    _contentDidMountHandlers[key] = [handler copy];
}

// Read only: whether the root view of the surface has children, for a root without a safe area view. Nothing in React
// Native's views changes.
- (BOOL)rootViewHasChildren {
    UIView *surfaceView = _surface.view;
    for (UIView *rootView in surfaceView.subviews) {
        if (rootView.tag == _surface.rootTag) {
            return rootView.subviews.count > 0;
        }
    }
    return NO;
}

#pragma mark - ANNScreenReadinessListener

// The safe area view of the id says the content rendered. It says so while React Native mounts the surface (its props
// or its first children): then the view gets ready as that mount ends.
- (void)screenReadinessContentDidRender:(NSString *)screenId {
    if (_mounting) {
        _renderedDuringMount = YES;
        return;
    }
    // Another live root of the same bar component's id said it, in its own mount: not this root's render.
    if (_rendersAtOnce && [ANNScreenReadiness listenerCountForScreenId:_componentId] > 1) {
        return;
    }
    [self becomeReady];
}

#pragma mark - RCTSurfacePresenterObserver

- (void)willMountComponentsWithRootTag:(NSInteger)rootTag {
    if (_surface && rootTag == _surface.rootTag) {
        _mounting = YES;
    }
}

// Every mount of our own surface: while ready, the content-mount handlers run (a sheet's and a modal's size, the large
// title); before, the content gets ready if its safe area view said so during this mount, or, for a root no safe area
// view ever spoke for, once the root view has children.
- (void)didMountComponentsWithRootTag:(NSInteger)rootTag {
    if (_stopped || !_surface || rootTag != _surface.rootTag) {
        return;
    }
    _mounting = NO;
    [_hostingView surfaceDidMount];
    if (_stopped) {
        return;
    }
    if (_ready) {
        [self runContentDidMountHandlers];
        return;
    }
    // The first mount, before any readiness test: a tab not shown counts as rendered by it.
    [self markMounted];
    if (_stopped) {
        return;
    }
    if (_renderedDuringMount) {
        _renderedDuringMount = NO;
        [self becomeReady];
        return;
    }
    if ([ANNScreenReadiness contentRenderedOfScreen:_componentId] == ANNContentRenderedUnknown &&
        [self rootViewHasChildren]) {
        [self becomeReady];
    }
}

#pragma mark - Appearance

- (void)componentWillAppear {
    [ANNAppearanceDispatcher willAppear:_appearance];
}

- (void)componentDidAppear {
    [ANNAppearanceDispatcher didAppear:_appearance];
}

- (void)componentDidDisappear {
    [ANNAppearanceDispatcher didDisappear:_appearance];
}

#pragma mark - Stop

// Once, on the main queue, never in `dealloc`: the appearance ends first, so JavaScript hears the end of an appearance
// it heard start and what is still held is dropped with its limit; then the observer leaves the presenter it was added
// to (in `dealloc` the presenter's weak entry would be empty already), the readiness listener and the "renders at once"
// mark go, and the surface stops a turn later, as soon as it runs.
- (void)stopSurface {
    ANNAssertMainQueue();
    if (_stopped) {
        return;
    }
    _stopped = YES;
    [ANNAppearanceDispatcher didDisappear:_appearance];
    [_surfacePresenter removeObserver:self];
    // A root that renders at once (a bar's or a tab's component) has no screen to clear its id at a destroy: its stop
    // does, while the id is still its own and no other live root shows it, so a later root with the same id isn't
    // "rendered" before it renders. The "renders at once" mark goes the same way: a root that stops after a newer one
    // took its id (a reload) leaves the newer one's mark. Read once, before the listener goes.
    BOOL heldId = [ANNScreenReadiness isListener:self forScreenId:_componentId];
    [ANNScreenReadiness removeListener:self forScreenId:_componentId];
    // The tabs' accessory has no screen to clear its id either.
    BOOL clearsOwnId = _rendersAtOnce || _componentType == ANNComponentTypeBottomTabsAccessory;
    if (clearsOwnId && heldId && [ANNScreenReadiness listenerCountForScreenId:_componentId] == 0) {
        [ANNScreenReadiness removeScreenId:_componentId];
        if (_rendersAtOnce) {
            [ANNScreenReadiness setRendersAtOnce:NO forScreenId:_componentId];
        }
    }
    _reactViewReadyBlock = nil;
    _mountHandlers = nil;
    [_contentDidMountHandlers removeAllObjects];
    ANNSurfaceHostingView *hostingView = _hostingView;
    if (_surface && hostingView) {
        // On the next turn of the main queue: a destroy that runs inside React Native's mounting (a wait that ends in
        // a mount, a root that replaces at once) would stop the surface there, React Native would drop its last
        // transaction and recycle its root view with the old content still in it, to show in a later screen. The
        // hosting view stops the surface then, or at its start when it doesn't run yet; the block keeps the hosting
        // view, and with it the surface, until it asks.
        dispatch_async(dispatch_get_main_queue(), ^{
          [hostingView stopSurfaceWhenRunning];
        });
    }
}

@end

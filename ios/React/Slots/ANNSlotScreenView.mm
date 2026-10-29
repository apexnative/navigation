#import "ANNSlotScreenView.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNScreenAppearance.h"
#import "ANNScreenReadiness.h"
#import "ANNSlotHost.h"
#import "ANNSurfaceHostingView.h"
#import "../../cpp/react/renderer/components/ApexNavigationSpec/ANNSlotState.h"
#import <React/RCTConstants.h>
#import <React/RCTSurfacePresenter.h>
#import <React/RCTUtils.h>
#import <ReactCommon/RCTHost.h>

@interface ANNSlotScreenView () <RCTSurfacePresenterObserver, ANNScreenReadinessListener>
@end

@implementation ANNSlotScreenView {
    ANNScreenAppearance *_appearance;
    __weak RCTSurfacePresenter *_surfacePresenter;
    NSString *_componentId;
    NSString *_moduleName;
    ANNReactViewReadyCompletionBlock _reactViewReadyBlock;
    NSMutableDictionary<NSString *, void (^)(void)> *_contentDidMountHandlers;
    BOOL _isMounted;
    BOOL _stopped;
    // The ready signal went: the content mounted and rendered.
    BOOL _ready;
    // The host's surface mounts now: readiness is decided as that mount ends.
    BOOL _hostMounting;
    CGSize _lastSize;
}

- (instancetype)initWithComponentId:(NSString *)componentId
                               name:(NSString *)name
                               host:(RCTHost *)host
                       eventEmitter:(ANNEventEmitter *)eventEmitter
                reactViewReadyBlock:(ANNReactViewReadyCompletionBlock)reactViewReadyBlock {
    self = [super initWithFrame:CGRectZero];
    if (self) {
        _componentId = [componentId copy];
        _moduleName = ANNComponentNameWithoutSlotPrefix(name);
        _eventEmitter = eventEmitter;
        _reactViewReadyBlock = [reactViewReadyBlock copy];
        // The core's dispatcher holds the appearance until the slot's content mounted here (`isReady` and the mount
        // handlers), at most 0.5 s, and keeps it balanced.
        _appearance = [[ANNScreenAppearance alloc] initWithComponentId:_componentId
                                                         componentName:_moduleName
                                                         componentType:ANNReactComponentTypeScreen
                                                               content:self
                                                          eventEmitter:eventEmitter];
        [ANNSlotHost startWithHost:host];
        [ANNSlotContainers setContainer:self forSlotId:_componentId];
        _surfacePresenter = host.surfacePresenter;
        [_surfacePresenter addObserver:self];
        // Ready once the slot's content mounted here and the screen rendered into its safe area view: the empty safe
        // area view mounts a commit before the screen renders. A slot whose screen renders nothing holds a waiting
        // command no longer than the command's own limit.
        [ANNScreenReadiness setListener:self forScreenId:_componentId];
    }
    return self;
}

- (RCTFabricSurface *)surface {
    return nil;
}

// The slot lays its content out at this size: every change goes to it (rotation, a sheet detent, the bars). Its state
// update mounts synchronously, here: during a rotation the content takes its new layout at once, as a screen's surface
// does (`+[ANNSurfaceHostingView layOutContent:]`).
- (void)layoutSubviews {
    [super layoutSubviews];
    if (!CGSizeEqualToSize(self.bounds.size, _lastSize)) {
        _lastSize = self.bounds.size;
        NSString *componentId = _componentId;
        [ANNSurfaceHostingView layOutContent:^{
          [ANNSlotContainers containerDidChange:componentId];
        }];
    }
}

// React Native hears of the app's light or dark style only from its hosting views. In one tree the screens have none in
// the window: the host's is in a hidden window, which UIKit gives no trait changes, so `useColorScheme` kept the old
// theme. The screen's view tells React Native as the hosting view of a screen would, with its filter
// (`ANNSurfaceHostingView`): only a style its window shares, so the theme follows the window alone. React Native skips
// the traits UIKit takes for the snapshots in the background; so does this.
- (void)traitCollectionDidChange:(UITraitCollection *)previousTraitCollection {
    [super traitCollectionDidChange:previousTraitCollection];
    UIWindow *window = self.window;
    UIUserInterfaceStyle style = self.traitCollection.userInterfaceStyle;
    if (!window || style == previousTraitCollection.userInterfaceStyle ||
        style != window.traitCollection.userInterfaceStyle ||
        RCTSharedApplication().applicationState == UIApplicationStateBackground) {
        return;
    }
    [[NSNotificationCenter defaultCenter]
        postNotificationName:RCTUserInterfaceStyleDidChangeNotification
                      object:self
                    userInfo:@{
                        RCTUserInterfaceStyleDidChangeNotificationTraitCollectionKey : self.traitCollection
                    }];
}

- (void)stopSurface {
    if (!RCTIsMainQueue()) {
        dispatch_async(dispatch_get_main_queue(), ^{
          [self stopSurface];
        });
        return;
    }
    if (_stopped) {
        return;
    }
    _stopped = YES;
    [_surfacePresenter removeObserver:self];
    [ANNScreenReadiness removeListener:self forScreenId:_componentId];
    // A later screen shown under the same id keeps the slot.
    if ([ANNSlotContainers containerForSlotId:_componentId] != self) {
        return;
    }
    facebook::react::ANNSlotContentHeights::forget(_componentId.UTF8String ?: "");
    [ANNSlotContainers removeContainerForSlotId:_componentId];
    [_eventEmitter sendSlotDestroyed:_componentId];
}

// The slot measures its content in its own layout: the first question asks for it and lays the slot out again; the
// height comes with that layout, before the mount the content's mount handlers hear. The width is the container's, at
// which the slot lays its content out.
- (CGFloat)contentHeightAtWidth:(CGFloat)width {
    std::string slotId = _componentId.UTF8String ?: "";
    if (_stopped || slotId.empty()) {
        return 0;
    }
    if (!facebook::react::ANNSlotContentHeights::isRequested(slotId)) {
        facebook::react::ANNSlotContentHeights::request(slotId);
        [ANNSlotContainers containerDidChange:_componentId];
        return 0;
    }
    facebook::react::Float height = facebook::react::ANNSlotContentHeights::height(slotId);
    return height > 0 ? (CGFloat)height : 0;
}

- (void)setContentDidMount:(void (^)(void))handler forKey:(NSString *)key {
    if (!_contentDidMountHandlers) {
        _contentDidMountHandlers = [NSMutableDictionary new];
    }
    _contentDidMountHandlers[key] = [handler copy];
}

#pragma mark - ANNSlotContainer

- (void)slotContentDidMount {
    _isMounted = YES;
    if (!_ready) {
        if (!_hostMounting) {
            [self becomeReadyIfRendered];
        }
        return;
    }
    [self runContentDidMountHandlers];
}

// The content is in the slot, and its safe area view said the screen rendered into it, or no safe area view ever
// spoke for the id (the mount counts then, as for a React root without one).
- (void)becomeReadyIfRendered {
    if (_ready || _stopped || !_isMounted ||
        [ANNScreenReadiness contentRenderedOfScreen:_componentId] == ANNContentRenderedNo) {
        return;
    }
    // The handlers of this mount first (the held appearance, the bar, the sizes), then the ready block that lets a
    // waiting command go on, as for a React root.
    _ready = YES;
    [self runContentDidMountHandlers];
    [self reactViewReady];
}

- (void)runContentDidMountHandlers {
    for (void (^handler)(void) in _contentDidMountHandlers.allValues) {
        handler();
    }
}

#pragma mark - ANNScreenReadinessListener

// The safe area view says so while the host mounts: the screen gets ready as that mount ends.
- (void)screenReadinessContentDidRender:(NSString *)screenId {
    if (!_hostMounting) {
        [self becomeReadyIfRendered];
    }
}

#pragma mark - RCTSurfacePresenterObserver

// React mounts the views deeper in the screen's content (a list, rendered once the screen's safe area view is laid
// out) in the host's surface, and the slot hears only of its own children: the screen hears of every mount of the
// host, as the React view of a screen hears of every mount of its surface (a large title's color goes into the list
// as it mounts, during the push). Only while it is in the window: a screen gets the rest as it appears.
- (void)willMountComponentsWithRootTag:(NSInteger)rootTag {
    if (rootTag == ANNSlotHost.rootTag) {
        _hostMounting = YES;
    }
}

- (void)didMountComponentsWithRootTag:(NSInteger)rootTag {
    if (rootTag != ANNSlotHost.rootTag) {
        return;
    }
    _hostMounting = NO;
    if (!_ready) {
        [self becomeReadyIfRendered];
        return;
    }
    if (!_isMounted || _stopped || !self.window) {
        return;
    }
    [self runContentDidMountHandlers];
}

- (void)dealloc {
    [_surfacePresenter removeObserver:self];
}

- (void)reactViewReady {
    _ready = YES;
    if (_reactViewReadyBlock) {
        ANNReactViewReadyCompletionBlock block = _reactViewReadyBlock;
        _reactViewReadyBlock = nil;
        block();
    }
}

#pragma mark - ANNComponentProtocol

- (ANNScreenAppearance *)appearance {
    return _appearance;
}

- (BOOL)isReady {
    return _ready;
}

- (NSString *)componentId {
    return _componentId;
}

- (ANNReactComponentType)componentType {
    return ANNReactComponentTypeScreen;
}

- (void)componentWillAppear {
    [ANNAppearanceDispatcher willAppear:_appearance];
}

- (void)componentDidAppear {
    [ANNAppearanceDispatcher didAppear:_appearance];
}

- (void)componentDidDisappear {
    [ANNAppearanceDispatcher didDisappear:_appearance];
}

@end

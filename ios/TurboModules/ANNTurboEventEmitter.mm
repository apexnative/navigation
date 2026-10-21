#import "ANNTurboEventEmitter.h"
#import "ANNAssert.h"
#import "ANNEventEmitter.h"
#import "ANNReloadCoordinator.h"
#import <React/RCTUtils.h>

// The module React Native made last: a reload makes a new one for the new JavaScript.
static __weak ANNTurboEventEmitter *ANNCurrentTurboEventEmitter;

@implementation ANNTurboEventEmitter {
    // Main queue.
    BOOL _hasListeners;
    BOOL _listensToAppLaunched;
}

RCT_EXPORT_MODULE(ANNTurboEventEmitter)

+ (BOOL)requiresMainQueueSetup {
    return NO;
}

// `addListener` and `removeListeners` come here, on the queue the events go from.
- (dispatch_queue_t)methodQueue {
    return dispatch_get_main_queue();
}

+ (ANNTurboEventEmitter *)current {
    @synchronized(ANNTurboEventEmitter.class) {
        return ANNCurrentTurboEventEmitter;
    }
}

- (instancetype)init {
    if (self = [super init]) {
        _bundlesBefore = ANNReloadCoordinator.bundleCount;
        @synchronized(ANNTurboEventEmitter.class) {
            ANNCurrentTurboEventEmitter = self;
        }
    }
    return self;
}

// Every event of the navigation and of the modules.
- (NSArray<NSString *> *)supportedEvents {
    return @[
        @"ANN.AppLaunched",
        @"ANN.ArrangementChanged",
        @"ANN.BottomAccessoryPlacementChanged",
        @"ANN.BottomTabLongPressed",
        @"ANN.BottomTabPressed",
        @"ANN.BottomTabSelected",
        @"ANN.CommandCompleted",
        @"ANN.ComponentDidAppear",
        @"ANN.ComponentDidDisappear",
        @"ANN.ComponentWillAppear",
        @"ANN.HingeChanged",
        @"ANN.KeyCommandPressed",
        @"ANN.ModalAttemptedToDismiss",
        @"ANN.ModalDismissed",
        @"ANN.NativeScreenCall",
        @"ANN.NativeScreenReleased",
        @"ANN.NavigationButtonPressed",
        @"ANN.NavigationStateChanged",
        @"ANN.PreviewCompleted",
        @"ANN.ScreenInsetsChanged",
        @"ANN.ScreenPopped",
        @"ANN.ScreenRegionsChanged",
        @"ANN.SearchBarCancelPressed",
        @"ANN.SearchBarSubmitted",
        @"ANN.SearchBarUpdated",
        @"ANN.SearchSuggestionPicked",
        @"ANN.SheetDetentChanged",
        @"ANN.SlotDestroyed",
        @"ANN.SplitViewColumnsStacked",
        @"ANN.SplitViewDisplayModeChanged",
        @"ANN.TitleRenamed",
    ];
}

#pragma mark - Listeners

- (BOOL)hasListeners {
    return _hasListeners;
}

- (BOOL)listensToAppLaunched {
    return _listensToAppLaunched;
}

- (void)startObserving {
    _hasListeners = YES;
}

// JavaScript removes listeners by count, not by name: with none left, it listens to `AppLaunched` no more either.
- (void)stopObserving {
    _hasListeners = NO;
    _listensToAppLaunched = NO;
}

- (void)addListener:(NSString *)eventName {
    [super addListener:eventName];
    if ([eventName isEqualToString:@"ANN.AppLaunched"] && !_listensToAppLaunched) {
        _listensToAppLaunched = YES;
        [ANNEventEmitter eventEmitterListensToAppLaunched:self];
    }
}

- (void)invalidate {
    [super invalidate];
    _hasListeners = NO;
    _listensToAppLaunched = NO;
    RCTExecuteOnMainQueue(^{
      [ANNEventEmitter eventEmitterWentAway:self];
    });
}

- (void)sendIfListened:(NSString *)eventName body:(id)body {
    ANNAssertMainQueue();
    if (_hasListeners) {
        [self sendEventWithName:eventName body:body];
    }
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeANNTurboEventEmitterSpecJSI>(params);
}

@end

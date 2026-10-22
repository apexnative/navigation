#import "ANNEventEmitter.h"
#import "ANNAssert.h"
#import "ANNEventContract.h"
#import "ANNReloadCoordinator.h"
#import "ANNTurboEventEmitter.h"
#import <React/RCTUtils.h>

static NSString *ANNComponentTypeName(ANNReactComponentType componentType) {
    switch (componentType) {
    case ANNComponentTypeTopBarTitle:
        return @"TopBarTitle";
    case ANNComponentTypeTopBarButton:
        return @"TopBarButton";
    case ANNComponentTypeTopBarBackground:
        return @"TopBarBackground";
    case ANNComponentTypeBottomTabItem:
        return @"BottomTabItem";
    default:
        return @"Component";
    }
}

// Whether an `AppLaunched` waits for JavaScript to listen to it, and the bundle it is for (the count of bundles when it
// was kept). Main queue.
static BOOL ANNAppLaunchedPending;
static NSUInteger ANNAppLaunchedBundle;

@implementation ANNEventEmitter

- (instancetype)init {
    if (self = [super init]) {
        _generation = ANNReloadCoordinator.generation;
    }
    return self;
}

- (BOOL)isCurrent {
    return _generation == ANNReloadCoordinator.generation;
}

// The generation is checked where the event goes out, on the main queue: an event sent from another queue just
// before a reload is dropped too.
- (void)sendEvent:(NSString *)name body:(NSDictionary<NSString *, id> *)body {
    RCTExecuteOnMainQueue(^{
      if (!self.isCurrent) {
          return;
      }
      [ANNTurboEventEmitter.current sendIfListened:name body:body];
    });
}

#pragma mark - The contract

- (void)sendAppLaunched {
    ANNAssertMainQueue();
    ANNTurboEventEmitter *eventEmitter = ANNTurboEventEmitter.current;
    if (!eventEmitter.listensToAppLaunched) {
        ANNAppLaunchedPending = YES;
        ANNAppLaunchedBundle = ANNReloadCoordinator.bundleCount;
        return;
    }
    ANNAppLaunchedPending = NO;
    [eventEmitter sendIfListened:ANNEventContract.AppLaunched.event body:nil];
}

+ (void)eventEmitterListensToAppLaunched:(ANNTurboEventEmitter *)eventEmitter {
    ANNAssertMainQueue();
    if (!ANNAppLaunchedPending) {
        return;
    }
    ANNAppLaunchedPending = NO;
    [eventEmitter sendIfListened:ANNEventContract.AppLaunched.event body:nil];
}

// Only an `AppLaunched` of the module's own JavaScript or an older one: the module is made while its bundle runs,
// before that bundle counts, so its JavaScript's is the next bundle at most. One kept for a newer bundle stays, also
// when the old module goes late.
+ (void)eventEmitterWentAway:(ANNTurboEventEmitter *)eventEmitter {
    ANNAssertMainQueue();
    if (ANNAppLaunchedPending && ANNAppLaunchedBundle <= eventEmitter.bundlesBefore + 1) {
        ANNAppLaunchedPending = NO;
    }
}

- (void)sendCommandCompleted:(NSString *)commandName
                   commandId:(NSString *)commandId
              completionTime:(double)completionTime {
    const auto &keys = ANNEventContract.CommandCompleted;
    [self sendEvent:keys.event
               body:@{
                   keys.commandName : commandName,
                   keys.commandId : commandId,
                   keys.completionTime : @(completionTime),
               }];
}

#pragma mark - Screens

- (void)sendComponentWillAppear:(NSString *)componentId
                  componentName:(NSString *)componentName
                  componentType:(ANNReactComponentType)componentType {
    [self sendComponentEvent:ANNEventContract.ComponentWillAppear.event
                 componentId:componentId
               componentName:componentName
               componentType:componentType];
}

- (void)sendComponentDidAppear:(NSString *)componentId
                 componentName:(NSString *)componentName
                 componentType:(ANNReactComponentType)componentType {
    [self sendComponentEvent:ANNEventContract.ComponentDidAppear.event
                 componentId:componentId
               componentName:componentName
               componentType:componentType];
}

- (void)sendComponentDidDisappear:(NSString *)componentId
                    componentName:(NSString *)componentName
                    componentType:(ANNReactComponentType)componentType {
    [self sendComponentEvent:ANNEventContract.ComponentDidDisappear.event
                 componentId:componentId
               componentName:componentName
               componentType:componentType];
}

// The three appearance events have the same keys.
- (void)sendComponentEvent:(NSString *)name
               componentId:(NSString *)componentId
             componentName:(NSString *)componentName
             componentType:(ANNReactComponentType)componentType {
    const auto &keys = ANNEventContract.ComponentDidAppear;
    [self sendEvent:name
               body:@{
                   keys.componentId : componentId ?: @"",
                   keys.componentName : componentName ?: @"",
                   keys.componentType : ANNComponentTypeName(componentType),
               }];
}

- (void)sendOnNavigationButtonPressed:(NSString *)componentId buttonId:(NSString *)buttonId {
    [self sendOnNavigationButtonPressed:componentId buttonId:buttonId selected:nil];
}

- (void)sendOnNavigationButtonPressed:(NSString *)componentId
                             buttonId:(NSString *)buttonId
                             selected:(NSNumber *)selected {
    const auto &keys = ANNEventContract.NavigationButtonPressed;
    NSMutableDictionary *body =
        [@{keys.componentId : componentId ?: @"", keys.buttonId : buttonId ?: @""} mutableCopy];
    if (selected) {
        body[keys.selected] = @(selected.boolValue);
    }
    [self sendEvent:keys.event body:body];
}

- (void)sendScreenPopped:(NSString *)componentId {
    const auto &keys = ANNEventContract.ScreenPopped;
    [self sendEvent:keys.event body:@{keys.componentId : componentId ?: @""}];
}

- (void)sendNavigationStateChanged:(NSString *)screenId name:(NSString *)name tab:(NSNumber *)tab {
    const auto &keys = ANNEventContract.NavigationStateChanged;
    const auto &screenKeys = ANNEventContract.NavigationStateChangedScreen;
    id screen = screenId ? @{screenKeys.id : screenId, screenKeys.name : name ?: @""}
                         : (id)NSNull.null;
    [self sendEvent:keys.event body:@{keys.screen : screen, keys.tab : tab ?: (id)NSNull.null}];
}

- (void)sendScreenInsetsChanged:(NSString *)componentId
                         insets:(NSDictionary<NSString *, NSNumber *> *)insets {
    const auto &keys = ANNEventContract.ScreenInsetsChanged;
    NSMutableDictionary *body = [insets mutableCopy] ?: [NSMutableDictionary new];
    body[keys.componentId] = componentId;
    [self sendEvent:keys.event body:body];
}

- (void)sendScreenRegionsChanged:(NSString *)componentId
                         regions:(NSDictionary<NSString *, NSArray *> *)regions {
    const auto &keys = ANNEventContract.ScreenRegionsChanged;
    NSMutableDictionary *body = [regions mutableCopy] ?: [NSMutableDictionary new];
    body[keys.componentId] = componentId;
    [self sendEvent:keys.event body:body];
}

- (void)sendSlotDestroyed:(NSString *)componentId {
    const auto &keys = ANNEventContract.SlotDestroyed;
    [self sendEvent:keys.event body:@{keys.componentId : componentId ?: @""}];
}

- (void)sendTitleRenamed:(NSString *)componentId text:(NSString *)text {
    const auto &keys = ANNEventContract.TitleRenamed;
    [self sendEvent:keys.event
               body:@{keys.componentId : componentId ?: @"", keys.text : text ?: @""}];
}

- (void)sendKeyCommandPressed:(NSString *)componentId keyCommandId:(NSString *)keyCommandId {
    const auto &keys = ANNEventContract.KeyCommandPressed;
    [self sendEvent:keys.event
               body:@{
                   keys.componentId : componentId ?: @"",
                   keys.keyCommandId : keyCommandId ?: @"",
               }];
}

- (void)sendPreviewCompleted:(NSString *)componentId
          previewComponentId:(NSString *)previewComponentId {
    const auto &keys = ANNEventContract.PreviewCompleted;
    [self sendEvent:keys.event
               body:@{
                   keys.componentId : componentId ?: @"",
                   keys.previewComponentId : previewComponentId ?: @"",
               }];
}

#pragma mark - Modals

- (void)sendModalDismissed:(NSString *)componentId
             componentName:(NSString *)componentName
           modalsDismissed:(NSUInteger)modalsDismissed {
    const auto &keys = ANNEventContract.ModalDismissed;
    [self sendEvent:keys.event
               body:@{
                   keys.componentId : componentId ?: @"",
                   keys.componentName : componentName ?: @"",
                   keys.modalsDismissed : @(modalsDismissed),
               }];
}

- (void)sendModalAttemptedToDismiss:(NSString *)componentId {
    const auto &keys = ANNEventContract.ModalAttemptedToDismiss;
    [self sendEvent:keys.event body:@{keys.componentId : componentId ?: @""}];
}

- (void)sendSheetDetentChanged:(NSString *)componentId detent:(id)detent {
    const auto &keys = ANNEventContract.SheetDetentChanged;
    [self sendEvent:keys.event
               body:@{keys.componentId : componentId ?: @"", keys.detent : detent ?: @""}];
}

#pragma mark - Search

- (void)sendOnSearchBarUpdated:(NSString *)componentId
                          text:(NSString *)text
                     isFocused:(BOOL)isFocused
                         scope:(NSString *)scope
                        tokens:(NSArray<NSString *> *)tokens {
    const auto &keys = ANNEventContract.SearchBarUpdated;
    NSMutableDictionary *body = [@{
        keys.componentId : componentId ?: @"",
        keys.text : text ?: @"",
        keys.isFocused : @(isFocused),
    } mutableCopy];
    body[keys.scope] = scope;
    body[keys.tokens] = tokens;
    [self sendEvent:keys.event body:body];
}

- (void)sendOnSearchBarSubmitted:(NSString *)componentId
                            text:(NSString *)text
                           scope:(NSString *)scope
                          tokens:(NSArray<NSString *> *)tokens {
    const auto &keys = ANNEventContract.SearchBarSubmitted;
    NSMutableDictionary *body =
        [@{keys.componentId : componentId ?: @"", keys.text : text ?: @""} mutableCopy];
    body[keys.scope] = scope;
    body[keys.tokens] = tokens;
    [self sendEvent:keys.event body:body];
}

- (void)sendOnSearchBarCancelPressed:(NSString *)componentId {
    const auto &keys = ANNEventContract.SearchBarCancelPressed;
    [self sendEvent:keys.event body:@{keys.componentId : componentId ?: @""}];
}

- (void)sendOnSearchSuggestionPicked:(NSString *)componentId suggestionId:(NSString *)suggestionId {
    const auto &keys = ANNEventContract.SearchSuggestionPicked;
    [self sendEvent:keys.event
               body:@{
                   keys.componentId : componentId ?: @"",
                   keys.suggestionId : suggestionId ?: @"",
               }];
}

#pragma mark - Split view, pair, hinge

- (void)sendSplitViewColumnsStacked:(NSString *)componentId stacked:(BOOL)stacked {
    const auto &keys = ANNEventContract.SplitViewColumnsStacked;
    [self sendEvent:keys.event
               body:@{keys.componentId : componentId ?: @"", keys.stacked : @(stacked)}];
}

- (void)sendSplitViewDisplayModeChanged:(NSString *)componentId
                            displayMode:(NSString *)displayMode
                       inspectorVisible:(NSNumber *)inspectorVisible {
    const auto &keys = ANNEventContract.SplitViewDisplayModeChanged;
    NSMutableDictionary *body = [@{
        keys.componentId : componentId ?: @"",
        keys.displayMode : displayMode ?: @"automatic",
    } mutableCopy];
    body[keys.inspectorVisible] = inspectorVisible;
    [self sendEvent:keys.event body:body];
}

- (void)sendArrangementChanged:(NSString *)componentId state:(NSDictionary<NSString *, id> *)state {
    const auto &keys = ANNEventContract.ArrangementChanged;
    NSMutableDictionary *body = [state mutableCopy] ?: [NSMutableDictionary new];
    body[keys.componentId] = componentId;
    [self sendEvent:keys.event body:body];
}

- (void)sendHingeChanged:(NSDictionary<NSString *, id> *)value {
    [self sendEvent:ANNEventContract.HingeChanged.event body:value ?: @{}];
}

#pragma mark - Native screens, tabs

- (void)sendNativeScreenCall:(NSDictionary<NSString *, id> *)call {
    [self sendEvent:ANNEventContract.NativeScreenCall.event body:call];
}

- (void)sendNativeScreenReleased:(NSString *)componentId {
    const auto &keys = ANNEventContract.NativeScreenReleased;
    [self sendEvent:keys.event body:@{keys.componentId : componentId ?: @""}];
}

- (void)sendBottomTabSelected:(NSUInteger)selectedTabIndex
           unselectedTabIndex:(NSUInteger)unselectedTabIndex
                 bottomTabsId:(NSString *)bottomTabsId {
    const auto &keys = ANNEventContract.BottomTabSelected;
    [self sendEvent:keys.event
               body:@{
                   keys.selectedTabIndex : @(selectedTabIndex),
                   keys.unselectedTabIndex : @(unselectedTabIndex),
                   keys.bottomTabsId : bottomTabsId ?: @"",
               }];
}

- (void)sendBottomTabPressed:(NSUInteger)tabIndex
                bottomTabsId:(NSString *)bottomTabsId
                    screenId:(NSString *)screenId
                 wasSelected:(BOOL)wasSelected {
    const auto &keys = ANNEventContract.BottomTabPressed;
    NSMutableDictionary *body = [@{
        keys.tabIndex : @(tabIndex),
        keys.bottomTabsId : bottomTabsId ?: @"",
        keys.wasSelected : @(wasSelected),
    } mutableCopy];
    body[keys.screenId] = screenId;
    [self sendEvent:keys.event body:body];
}

- (void)sendBottomTabLongPressed:(NSUInteger)selectedTabIndex
                    bottomTabsId:(NSString *)bottomTabsId {
    const auto &keys = ANNEventContract.BottomTabLongPressed;
    [self sendEvent:keys.event
               body:@{
                   keys.selectedTabIndex : @(selectedTabIndex),
                   keys.bottomTabsId : bottomTabsId ?: @"",
               }];
}

- (void)sendBottomAccessoryPlacementChanged:(NSString *)componentId
                                  placement:(NSString *)placement {
    const auto &keys = ANNEventContract.BottomAccessoryPlacementChanged;
    [self sendEvent:keys.event
               body:@{keys.componentId : componentId ?: @"", keys.placement : placement ?: @""}];
}

@end

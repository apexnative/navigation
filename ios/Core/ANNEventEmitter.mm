#import "ANNEventEmitter.h"
#import "ANNAssert.h"
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

static NSString *const ANNAppLaunchedEvent = @"ANN.AppLaunched";

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
    NSString *eventName = [@"ANN." stringByAppendingString:name];
    RCTExecuteOnMainQueue(^{
      if (!self.isCurrent) {
          return;
      }
      [ANNTurboEventEmitter.current sendIfListened:eventName body:body];
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
    [eventEmitter sendIfListened:ANNAppLaunchedEvent body:nil];
}

+ (void)eventEmitterListensToAppLaunched:(ANNTurboEventEmitter *)eventEmitter {
    ANNAssertMainQueue();
    if (!ANNAppLaunchedPending) {
        return;
    }
    ANNAppLaunchedPending = NO;
    [eventEmitter sendIfListened:ANNAppLaunchedEvent body:nil];
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
    [self sendEvent:@"CommandCompleted"
               body:@{
                   @"commandName" : commandName,
                   @"commandId" : commandId,
                   @"completionTime" : @(completionTime),
               }];
}

#pragma mark - Screens

- (void)sendComponentWillAppear:(NSString *)componentId
                  componentName:(NSString *)componentName
                  componentType:(ANNReactComponentType)componentType {
    [self sendComponentEvent:@"ComponentWillAppear"
                 componentId:componentId
               componentName:componentName
               componentType:componentType];
}

- (void)sendComponentDidAppear:(NSString *)componentId
                 componentName:(NSString *)componentName
                 componentType:(ANNReactComponentType)componentType {
    [self sendComponentEvent:@"ComponentDidAppear"
                 componentId:componentId
               componentName:componentName
               componentType:componentType];
}

- (void)sendComponentDidDisappear:(NSString *)componentId
                    componentName:(NSString *)componentName
                    componentType:(ANNReactComponentType)componentType {
    [self sendComponentEvent:@"ComponentDidDisappear"
                 componentId:componentId
               componentName:componentName
               componentType:componentType];
}

- (void)sendComponentEvent:(NSString *)name
               componentId:(NSString *)componentId
             componentName:(NSString *)componentName
             componentType:(ANNReactComponentType)componentType {
    [self sendEvent:name
               body:@{
                   @"componentId" : componentId ?: @"",
                   @"componentName" : componentName ?: @"",
                   @"componentType" : ANNComponentTypeName(componentType),
               }];
}

- (void)sendOnNavigationButtonPressed:(NSString *)componentId buttonId:(NSString *)buttonId {
    [self sendOnNavigationButtonPressed:componentId buttonId:buttonId selected:nil];
}

- (void)sendOnNavigationButtonPressed:(NSString *)componentId
                             buttonId:(NSString *)buttonId
                             selected:(NSNumber *)selected {
    NSMutableDictionary *body =
        [@{@"componentId" : componentId ?: @"", @"buttonId" : buttonId ?: @""} mutableCopy];
    if (selected) {
        body[@"selected"] = @(selected.boolValue);
    }
    [self sendEvent:@"NavigationButtonPressed" body:body];
}

- (void)sendScreenPopped:(NSString *)componentId {
    [self sendEvent:@"ScreenPopped" body:@{@"componentId" : componentId ?: @""}];
}

- (void)sendNavigationStateChanged:(NSString *)screenId name:(NSString *)name tab:(NSNumber *)tab {
    id screen = screenId ? @{@"id" : screenId, @"name" : name ?: @""} : (id)NSNull.null;
    [self sendEvent:@"NavigationStateChanged"
               body:@{@"screen" : screen, @"tab" : tab ?: (id)NSNull.null}];
}

- (void)sendScreenInsetsChanged:(NSString *)componentId
                         insets:(NSDictionary<NSString *, NSNumber *> *)insets {
    NSMutableDictionary *body = [insets mutableCopy] ?: [NSMutableDictionary new];
    body[@"componentId"] = componentId;
    [self sendEvent:@"ScreenInsetsChanged" body:body];
}

- (void)sendScreenRegionsChanged:(NSString *)componentId
                         regions:(NSDictionary<NSString *, NSArray *> *)regions {
    NSMutableDictionary *body = [regions mutableCopy] ?: [NSMutableDictionary new];
    body[@"componentId"] = componentId;
    [self sendEvent:@"ScreenRegionsChanged" body:body];
}

- (void)sendSlotDestroyed:(NSString *)componentId {
    [self sendEvent:@"SlotDestroyed" body:@{@"componentId" : componentId ?: @""}];
}

- (void)sendTitleRenamed:(NSString *)componentId text:(NSString *)text {
    [self sendEvent:@"TitleRenamed"
               body:@{@"componentId" : componentId ?: @"", @"text" : text ?: @""}];
}

- (void)sendKeyCommandPressed:(NSString *)componentId keyCommandId:(NSString *)keyCommandId {
    [self sendEvent:@"KeyCommandPressed"
               body:@{@"componentId" : componentId ?: @"", @"keyCommandId" : keyCommandId ?: @""}];
}

- (void)sendPreviewCompleted:(NSString *)componentId
          previewComponentId:(NSString *)previewComponentId {
    [self sendEvent:@"PreviewCompleted"
               body:@{
                   @"componentId" : componentId ?: @"",
                   @"previewComponentId" : previewComponentId ?: @"",
               }];
}

#pragma mark - Modals

- (void)sendModalDismissed:(NSString *)componentId
             componentName:(NSString *)componentName
           modalsDismissed:(NSUInteger)modalsDismissed {
    [self sendEvent:@"ModalDismissed"
               body:@{
                   @"componentId" : componentId ?: @"",
                   @"componentName" : componentName ?: @"",
                   @"modalsDismissed" : @(modalsDismissed),
               }];
}

- (void)sendModalAttemptedToDismiss:(NSString *)componentId {
    [self sendEvent:@"ModalAttemptedToDismiss" body:@{@"componentId" : componentId ?: @""}];
}

- (void)sendSheetDetentChanged:(NSString *)componentId detent:(id)detent {
    [self sendEvent:@"SheetDetentChanged"
               body:@{@"componentId" : componentId ?: @"", @"detent" : detent ?: @""}];
}

#pragma mark - Search

- (void)sendOnSearchBarUpdated:(NSString *)componentId
                          text:(NSString *)text
                     isFocused:(BOOL)isFocused
                         scope:(NSString *)scope
                        tokens:(NSArray<NSString *> *)tokens {
    NSMutableDictionary *body = [@{
        @"componentId" : componentId ?: @"",
        @"text" : text ?: @"",
        @"isFocused" : @(isFocused),
    } mutableCopy];
    body[@"scope"] = scope;
    body[@"tokens"] = tokens;
    [self sendEvent:@"SearchBarUpdated" body:body];
}

- (void)sendOnSearchBarSubmitted:(NSString *)componentId
                            text:(NSString *)text
                           scope:(NSString *)scope
                          tokens:(NSArray<NSString *> *)tokens {
    NSMutableDictionary *body =
        [@{@"componentId" : componentId ?: @"", @"text" : text ?: @""} mutableCopy];
    body[@"scope"] = scope;
    body[@"tokens"] = tokens;
    [self sendEvent:@"SearchBarSubmitted" body:body];
}

- (void)sendOnSearchBarCancelPressed:(NSString *)componentId {
    [self sendEvent:@"SearchBarCancelPressed" body:@{@"componentId" : componentId ?: @""}];
}

- (void)sendOnSearchSuggestionPicked:(NSString *)componentId suggestionId:(NSString *)suggestionId {
    [self sendEvent:@"SearchSuggestionPicked"
               body:@{@"componentId" : componentId ?: @"", @"suggestionId" : suggestionId ?: @""}];
}

#pragma mark - Split view, pair, hinge

- (void)sendSplitViewColumnsStacked:(NSString *)componentId stacked:(BOOL)stacked {
    [self sendEvent:@"SplitViewColumnsStacked"
               body:@{@"componentId" : componentId ?: @"", @"stacked" : @(stacked)}];
}

- (void)sendSplitViewDisplayModeChanged:(NSString *)componentId
                            displayMode:(NSString *)displayMode
                       inspectorVisible:(NSNumber *)inspectorVisible {
    NSMutableDictionary *body = [@{
        @"componentId" : componentId ?: @"",
        @"displayMode" : displayMode ?: @"automatic",
    } mutableCopy];
    body[@"inspectorVisible"] = inspectorVisible;
    [self sendEvent:@"SplitViewDisplayModeChanged" body:body];
}

- (void)sendArrangementChanged:(NSString *)componentId state:(NSDictionary<NSString *, id> *)state {
    NSMutableDictionary *body = [state mutableCopy] ?: [NSMutableDictionary new];
    body[@"componentId"] = componentId;
    [self sendEvent:@"ArrangementChanged" body:body];
}

- (void)sendHingeChanged:(NSDictionary<NSString *, id> *)value {
    [self sendEvent:@"HingeChanged" body:value ?: @{}];
}

#pragma mark - Native screens, tabs

- (void)sendNativeScreenCall:(NSDictionary<NSString *, id> *)call {
    [self sendEvent:@"NativeScreenCall" body:call];
}

- (void)sendNativeScreenReleased:(NSString *)componentId {
    [self sendEvent:@"NativeScreenReleased" body:@{@"componentId" : componentId ?: @""}];
}

- (void)sendBottomTabSelected:(NSUInteger)selectedTabIndex
           unselectedTabIndex:(NSUInteger)unselectedTabIndex
                 bottomTabsId:(NSString *)bottomTabsId {
    [self sendEvent:@"BottomTabSelected"
               body:@{
                   @"selectedTabIndex" : @(selectedTabIndex),
                   @"unselectedTabIndex" : @(unselectedTabIndex),
                   @"bottomTabsId" : bottomTabsId ?: @"",
               }];
}

- (void)sendBottomTabPressed:(NSUInteger)tabIndex
                bottomTabsId:(NSString *)bottomTabsId
                    screenId:(NSString *)screenId
                 wasSelected:(BOOL)wasSelected {
    NSMutableDictionary *body = [@{
        @"tabIndex" : @(tabIndex),
        @"bottomTabsId" : bottomTabsId ?: @"",
        @"wasSelected" : @(wasSelected),
    } mutableCopy];
    body[@"screenId"] = screenId;
    [self sendEvent:@"BottomTabPressed" body:body];
}

- (void)sendBottomTabLongPressed:(NSUInteger)selectedTabIndex
                    bottomTabsId:(NSString *)bottomTabsId {
    [self sendEvent:@"BottomTabLongPressed"
               body:@{
                   @"selectedTabIndex" : @(selectedTabIndex),
                   @"bottomTabsId" : bottomTabsId ?: @"",
               }];
}

- (void)sendBottomAccessoryPlacementChanged:(NSString *)componentId
                                  placement:(NSString *)placement {
    [self sendEvent:@"BottomAccessoryPlacementChanged"
               body:@{@"componentId" : componentId ?: @"", @"placement" : placement ?: @""}];
}

@end

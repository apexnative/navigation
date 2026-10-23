#import "ANNComponentType.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@class ANNTurboEventEmitter;

/// The core's sender of the events `ANN.<Name>` to JavaScript. It goes through the emitter module of the current
/// JavaScript (`ANNTurboEventEmitter`) on the main queue, so events and answers reach JavaScript in the order the
/// native side sent them. While JavaScript listens to no event (none runs yet, or a reload is under way) an event is
/// dropped silently.
///
/// An emitter belongs to one JavaScript: it takes the generation of `ANNReloadCoordinator` when it is made and drops
/// every event once that generation moved. `ApexNavigation.eventEmitter` gives the emitter of the JavaScript that runs
/// now; every node and content keeps the one it was made with, so the screens of a JavaScript that went tell the new
/// one nothing.
@interface ANNEventEmitter : NSObject

/// The generation of the JavaScript this emitter sends to.
@property(nonatomic, readonly) NSUInteger generation;

/// Whether the JavaScript of this emitter still runs: its events go only then. Any thread.
@property(nonatomic, readonly) BOOL isCurrent;

/// Sends the event `name` (`ANNEventContract.<Event>.event`, `ANN.<Event>`) with the payload as it is, its keys from
/// `ANNEventContract` too. From another queue it moves to the main queue first.
- (void)sendEvent:(NSString *)name body:(nullable NSDictionary<NSString *, id> *)body;

#pragma mark - The contract

/// `ANN.AppLaunched`, with an empty payload. While JavaScript has no listener of it, it is kept and goes once JavaScript
/// adds the first one. Main queue.
- (void)sendAppLaunched;

/// `ANN.CommandCompleted { commandName, commandId, completionTime }`, the time in milliseconds since 1970
/// (EVT-CommandCompleted).
- (void)sendCommandCompleted:(NSString *)commandName
                   commandId:(NSString *)commandId
              completionTime:(double)completionTime;

/// The emitter module tells that JavaScript added its first listener of `ANN.AppLaunched`: a kept `AppLaunched` goes
/// now. Main queue.
+ (void)eventEmitterListensToAppLaunched:(ANNTurboEventEmitter *)eventEmitter;

/// The emitter module of a JavaScript that went is invalidated: an `AppLaunched` kept for that JavaScript, which never
/// listened to it, is dropped, so that the next JavaScript gets only its own. Main queue.
+ (void)eventEmitterWentAway:(ANNTurboEventEmitter *)eventEmitter;

#pragma mark - Screens

- (void)sendComponentWillAppear:(NSString *)componentId
                  componentName:(NSString *)componentName
                  componentType:(ANNReactComponentType)componentType;

- (void)sendComponentDidAppear:(NSString *)componentId
                 componentName:(NSString *)componentName
                 componentType:(ANNReactComponentType)componentType;

- (void)sendComponentDidDisappear:(NSString *)componentId
                    componentName:(NSString *)componentName
                    componentType:(ANNReactComponentType)componentType;

/// `ANN.NavigationButtonPressed { componentId, buttonId }`.
- (void)sendOnNavigationButtonPressed:(NSString *)componentId buttonId:(NSString *)buttonId;

/// `ANN.NavigationButtonPressed { componentId, buttonId, selected? }`: `selected` only for a button with `toggle`, its
/// new state, left out when nil.
- (void)sendOnNavigationButtonPressed:(NSString *)componentId
                             buttonId:(NSString *)buttonId
                             selected:(nullable NSNumber *)selected;

/// `ANN.ScreenPopped { componentId }`: the screen left its stack (EVT-ScreenPopped).
- (void)sendScreenPopped:(NSString *)componentId;

/// `ANN.NavigationStateChanged { screen: { id, name } | null, tab: number | null }`: a nil screen id sends `null` for
/// the screen, a nil tab `null` for the tab (EVT-NavigationStateChanged).
- (void)sendNavigationStateChanged:(nullable NSString *)screenId
                              name:(nullable NSString *)name
                               tab:(nullable NSNumber *)tab;

- (void)sendScreenInsetsChanged:(NSString *)componentId
                         insets:(NSDictionary<NSString *, NSNumber *> *)insets;

- (void)sendScreenRegionsChanged:(NSString *)componentId
                         regions:(NSDictionary<NSString *, NSArray *> *)regions;

- (void)sendSlotDestroyed:(NSString *)componentId;

/// `ANN.DeepLinkDispatched { url }` (iOS only): a link React Native's `Linking` didn't take, for JavaScript to hand it.
- (void)sendDeepLinkDispatched:(NSURL *)url;

/// `ANN.TitleRenamed { componentId, text }` (EVT-TitleRenamed).
- (void)sendTitleRenamed:(NSString *)componentId text:(NSString *)text;

/// `ANN.KeyCommandPressed { componentId, keyCommandId }` (EVT-KeyCommandPressed).
- (void)sendKeyCommandPressed:(NSString *)componentId keyCommandId:(NSString *)keyCommandId;

/// `ANN.PreviewCompleted { componentId, previewComponentId }` (EVT-PreviewCompleted).
- (void)sendPreviewCompleted:(NSString *)componentId
          previewComponentId:(NSString *)previewComponentId;

#pragma mark - Modals

/// `ANN.ModalDismissed { componentId, componentName, modalsDismissed }`: the id and the name of the modal's visible
/// screen (EVT-ModalDismissed).
- (void)sendModalDismissed:(NSString *)componentId
             componentName:(NSString *)componentName
           modalsDismissed:(NSUInteger)modalsDismissed;

/// `ANN.ModalAttemptedToDismiss { componentId }` (EVT-ModalAttemptedToDismiss).
- (void)sendModalAttemptedToDismiss:(NSString *)componentId;

/// `ANN.SheetDetentChanged { componentId, detent }`: `detent` is `'medium'`, `'large'`, `'content'` or a number
/// (EVT-SheetDetentChanged).
- (void)sendSheetDetentChanged:(NSString *)componentId detent:(id)detent;

#pragma mark - Search

- (void)sendOnSearchBarUpdated:(NSString *)componentId
                          text:(NSString *)text
                     isFocused:(BOOL)isFocused
                         scope:(nullable NSString *)scope
                        tokens:(nullable NSArray<NSString *> *)tokens;

- (void)sendOnSearchBarSubmitted:(NSString *)componentId
                            text:(NSString *)text
                           scope:(nullable NSString *)scope
                          tokens:(nullable NSArray<NSString *> *)tokens;

- (void)sendOnSearchBarCancelPressed:(NSString *)componentId;

- (void)sendOnSearchSuggestionPicked:(NSString *)componentId suggestionId:(NSString *)suggestionId;

#pragma mark - Split view, pair, hinge

- (void)sendSplitViewColumnsStacked:(NSString *)componentId stacked:(BOOL)stacked;

/// `inspectorVisible` only for a split view with an inspector column.
- (void)sendSplitViewDisplayModeChanged:(NSString *)componentId
                            displayMode:(NSString *)displayMode
                       inspectorVisible:(nullable NSNumber *)inspectorVisible;

/// `state` is `{ placement, layout, front, hidden }`.
- (void)sendArrangementChanged:(NSString *)componentId state:(NSDictionary<NSString *, id> *)state;

/// `value` is `{ state, angle }`, or `{}` without a hinge.
- (void)sendHingeChanged:(NSDictionary<NSString *, id> *)value;

#pragma mark - Native screens, tabs

/// `call` is `{ componentId, callId, kind, name, args }`.
- (void)sendNativeScreenCall:(NSDictionary<NSString *, id> *)call;

/// `ANN.NativeScreenReleased { componentId }` (EVT-NativeScreenReleased).
- (void)sendNativeScreenReleased:(NSString *)componentId;

/// `ANN.BottomTabSelected { selectedTabIndex, unselectedTabIndex, bottomTabsId }` (EVT-BottomTabSelected).
- (void)sendBottomTabSelected:(NSUInteger)selectedTabIndex
           unselectedTabIndex:(NSUInteger)unselectedTabIndex
                 bottomTabsId:(NSString *)bottomTabsId;

/// `ANN.BottomTabPressed { tabIndex, bottomTabsId, screenId?, wasSelected }`: `screenId` left out when nil
/// (EVT-BottomTabPressed).
- (void)sendBottomTabPressed:(NSUInteger)tabIndex
                bottomTabsId:(NSString *)bottomTabsId
                    screenId:(nullable NSString *)screenId
                 wasSelected:(BOOL)wasSelected;

/// `ANN.BottomTabLongPressed { selectedTabIndex, bottomTabsId }`: the index of the tab pressed
/// (EVT-BottomTabLongPressed).
- (void)sendBottomTabLongPressed:(NSUInteger)selectedTabIndex
                    bottomTabsId:(NSString *)bottomTabsId;

- (void)sendBottomAccessoryPlacementChanged:(NSString *)componentId placement:(NSString *)placement;

@end

NS_ASSUME_NONNULL_END

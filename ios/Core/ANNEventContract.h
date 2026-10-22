// Made by scripts/contract.mjs from the event types of src/interfaces/: change them and run the script again.

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The names of the events `ANN.<Name>` (`event`) and the keys of their payloads, as the types of `src/interfaces/`
/// give them; what each field means is its JSDoc there. The object types a field holds have keys of their own. The
/// senders take every name and key from here: `ANNEventContract.CommandCompleted.commandId`.
FOUNDATION_EXPORT const struct ANNEventContractTable {
    /// `ANN.AppLaunched`.
    struct {
        __unsafe_unretained NSString *event;
    } AppLaunched;
    /// `ANN.ArrangementChanged`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *placement;
        __unsafe_unretained NSString *layout;
        __unsafe_unretained NSString *front;
        __unsafe_unretained NSString *hidden;
    } ArrangementChanged;
    /// `ANN.BottomAccessoryPlacementChanged` (iOS).
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *placement;
    } BottomAccessoryPlacementChanged;
    /// `ANN.BottomTabLongPressed`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *selectedTabIndex;
        __unsafe_unretained NSString *bottomTabsId;
    } BottomTabLongPressed;
    /// `ANN.BottomTabPressed`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *tabIndex;
        __unsafe_unretained NSString *bottomTabsId;
        /// Optional.
        __unsafe_unretained NSString *screenId;
        __unsafe_unretained NSString *wasSelected;
    } BottomTabPressed;
    /// `ANN.BottomTabSelected`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *selectedTabIndex;
        __unsafe_unretained NSString *unselectedTabIndex;
        __unsafe_unretained NSString *bottomTabsId;
    } BottomTabSelected;
    /// `ANN.CommandCompleted`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *commandName;
        __unsafe_unretained NSString *commandId;
        __unsafe_unretained NSString *completionTime;
    } CommandCompleted;
    /// `ANN.ComponentDidAppear`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *componentName;
        __unsafe_unretained NSString *componentType;
    } ComponentDidAppear;
    /// `ANN.ComponentDidDisappear`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *componentName;
        __unsafe_unretained NSString *componentType;
    } ComponentDidDisappear;
    /// `ANN.ComponentWillAppear`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *componentName;
        __unsafe_unretained NSString *componentType;
    } ComponentWillAppear;
    /// `ANN.DeepLinkDispatched` (iOS).
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *url;
    } DeepLinkDispatched;
    /// `ANN.HingeChanged`.
    struct {
        __unsafe_unretained NSString *event;
        /// Optional.
        __unsafe_unretained NSString *state;
        /// Optional.
        __unsafe_unretained NSString *angle;
    } HingeChanged;
    /// `ANN.KeyCommandPressed` (iOS).
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *keyCommandId;
    } KeyCommandPressed;
    /// `ANN.ModalAttemptedToDismiss`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
    } ModalAttemptedToDismiss;
    /// `ANN.ModalDismissed`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *componentName;
        __unsafe_unretained NSString *modalsDismissed;
    } ModalDismissed;
    /// `ANN.NativeScreenCall`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *callId;
        __unsafe_unretained NSString *kind;
        __unsafe_unretained NSString *name;
        __unsafe_unretained NSString *args;
    } NativeScreenCall;
    /// `ANN.NativeScreenReleased`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
    } NativeScreenReleased;
    /// `ANN.NavigationButtonPressed`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *buttonId;
        /// Optional, iOS only.
        __unsafe_unretained NSString *selected;
    } NavigationButtonPressed;
    /// `ANN.NavigationStateChanged`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *screen;
        __unsafe_unretained NSString *tab;
    } NavigationStateChanged;
    /// The object of `ANN.NavigationStateChanged.screen`.
    struct {
        __unsafe_unretained NSString *id;
        __unsafe_unretained NSString *name;
    } NavigationStateChangedScreen;
    /// `ANN.PreviewCompleted` (iOS).
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *previewComponentId;
    } PreviewCompleted;
    /// `ANN.ScreenInsetsChanged`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *top;
        __unsafe_unretained NSString *right;
        __unsafe_unretained NSString *bottom;
        __unsafe_unretained NSString *left;
    } ScreenInsetsChanged;
    /// `ANN.ScreenPopped`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
    } ScreenPopped;
    /// The object of `ANN.ScreenRegionsChanged.division`.
    struct {
        __unsafe_unretained NSString *x;
        __unsafe_unretained NSString *y;
        __unsafe_unretained NSString *width;
        __unsafe_unretained NSString *height;
    } ScreenRegionRect;
    /// `ANN.ScreenRegionsChanged`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *division;
        __unsafe_unretained NSString *occlusion;
    } ScreenRegionsChanged;
    /// `ANN.SearchBarCancelPressed`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
    } SearchBarCancelPressed;
    /// `ANN.SearchBarSubmitted`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *text;
        /// Optional.
        __unsafe_unretained NSString *scope;
        /// Optional, iOS only.
        __unsafe_unretained NSString *tokens;
    } SearchBarSubmitted;
    /// `ANN.SearchBarUpdated`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *text;
        __unsafe_unretained NSString *isFocused;
        /// Optional.
        __unsafe_unretained NSString *scope;
        /// Optional, iOS only.
        __unsafe_unretained NSString *tokens;
    } SearchBarUpdated;
    /// `ANN.SearchSuggestionPicked`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *suggestionId;
    } SearchSuggestionPicked;
    /// `ANN.SheetDetentChanged`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *detent;
    } SheetDetentChanged;
    /// `ANN.SlotDestroyed`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
    } SlotDestroyed;
    /// `ANN.SplitViewColumnsStacked`.
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *stacked;
    } SplitViewColumnsStacked;
    /// `ANN.SplitViewDisplayModeChanged` (iOS).
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *displayMode;
        /// Optional.
        __unsafe_unretained NSString *inspectorVisible;
    } SplitViewDisplayModeChanged;
    /// `ANN.TitleRenamed` (iOS).
    struct {
        __unsafe_unretained NSString *event;
        __unsafe_unretained NSString *componentId;
        __unsafe_unretained NSString *text;
    } TitleRenamed;
} ANNEventContract;

/// The name of every event, `ANN.<Name>`.
FOUNDATION_EXPORT NSArray<NSString *> *ANNEventContractNames(void);

NS_ASSUME_NONNULL_END

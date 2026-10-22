// Made by scripts/contract.mjs from the event types of src/interfaces/: change them and run the script again.

#import "ANNEventContract.h"

const struct ANNEventContractTable ANNEventContract = {
    .AppLaunched = {.event = @"ANN.AppLaunched"},
    .ArrangementChanged =
        {
            .event = @"ANN.ArrangementChanged",
            .componentId = @"componentId",
            .placement = @"placement",
            .layout = @"layout",
            .front = @"front",
            .hidden = @"hidden",
        },
    .BottomAccessoryPlacementChanged =
        {
            .event = @"ANN.BottomAccessoryPlacementChanged",
            .componentId = @"componentId",
            .placement = @"placement",
        },
    .BottomTabLongPressed =
        {
            .event = @"ANN.BottomTabLongPressed",
            .selectedTabIndex = @"selectedTabIndex",
            .bottomTabsId = @"bottomTabsId",
        },
    .BottomTabPressed =
        {
            .event = @"ANN.BottomTabPressed",
            .tabIndex = @"tabIndex",
            .bottomTabsId = @"bottomTabsId",
            .screenId = @"screenId",
            .wasSelected = @"wasSelected",
        },
    .BottomTabSelected =
        {
            .event = @"ANN.BottomTabSelected",
            .selectedTabIndex = @"selectedTabIndex",
            .unselectedTabIndex = @"unselectedTabIndex",
            .bottomTabsId = @"bottomTabsId",
        },
    .CommandCompleted =
        {
            .event = @"ANN.CommandCompleted",
            .commandName = @"commandName",
            .commandId = @"commandId",
            .completionTime = @"completionTime",
        },
    .ComponentDidAppear =
        {
            .event = @"ANN.ComponentDidAppear",
            .componentId = @"componentId",
            .componentName = @"componentName",
            .componentType = @"componentType",
        },
    .ComponentDidDisappear =
        {
            .event = @"ANN.ComponentDidDisappear",
            .componentId = @"componentId",
            .componentName = @"componentName",
            .componentType = @"componentType",
        },
    .ComponentWillAppear =
        {
            .event = @"ANN.ComponentWillAppear",
            .componentId = @"componentId",
            .componentName = @"componentName",
            .componentType = @"componentType",
        },
    .DeepLinkDispatched = {.event = @"ANN.DeepLinkDispatched", .url = @"url"},
    .HingeChanged = {.event = @"ANN.HingeChanged", .state = @"state", .angle = @"angle"},
    .KeyCommandPressed =
        {
            .event = @"ANN.KeyCommandPressed",
            .componentId = @"componentId",
            .keyCommandId = @"keyCommandId",
        },
    .ModalAttemptedToDismiss =
        {
            .event = @"ANN.ModalAttemptedToDismiss",
            .componentId = @"componentId",
        },
    .ModalDismissed =
        {
            .event = @"ANN.ModalDismissed",
            .componentId = @"componentId",
            .componentName = @"componentName",
            .modalsDismissed = @"modalsDismissed",
        },
    .NativeScreenCall =
        {
            .event = @"ANN.NativeScreenCall",
            .componentId = @"componentId",
            .callId = @"callId",
            .kind = @"kind",
            .name = @"name",
            .args = @"args",
        },
    .NativeScreenReleased = {.event = @"ANN.NativeScreenReleased", .componentId = @"componentId"},
    .NavigationButtonPressed =
        {
            .event = @"ANN.NavigationButtonPressed",
            .componentId = @"componentId",
            .buttonId = @"buttonId",
            .selected = @"selected",
        },
    .NavigationStateChanged =
        {
            .event = @"ANN.NavigationStateChanged",
            .screen = @"screen",
            .tab = @"tab",
        },
    .NavigationStateChangedScreen = {.id = @"id", .name = @"name"},
    .PreviewCompleted =
        {
            .event = @"ANN.PreviewCompleted",
            .componentId = @"componentId",
            .previewComponentId = @"previewComponentId",
        },
    .ScreenInsetsChanged =
        {
            .event = @"ANN.ScreenInsetsChanged",
            .componentId = @"componentId",
            .top = @"top",
            .right = @"right",
            .bottom = @"bottom",
            .left = @"left",
        },
    .ScreenPopped = {.event = @"ANN.ScreenPopped", .componentId = @"componentId"},
    .ScreenRegionRect = {.x = @"x", .y = @"y", .width = @"width", .height = @"height"},
    .ScreenRegionsChanged =
        {
            .event = @"ANN.ScreenRegionsChanged",
            .componentId = @"componentId",
            .division = @"division",
            .occlusion = @"occlusion",
        },
    .SearchBarCancelPressed =
        {
            .event = @"ANN.SearchBarCancelPressed",
            .componentId = @"componentId",
        },
    .SearchBarSubmitted =
        {
            .event = @"ANN.SearchBarSubmitted",
            .componentId = @"componentId",
            .text = @"text",
            .scope = @"scope",
            .tokens = @"tokens",
        },
    .SearchBarUpdated =
        {
            .event = @"ANN.SearchBarUpdated",
            .componentId = @"componentId",
            .text = @"text",
            .isFocused = @"isFocused",
            .scope = @"scope",
            .tokens = @"tokens",
        },
    .SearchSuggestionPicked =
        {
            .event = @"ANN.SearchSuggestionPicked",
            .componentId = @"componentId",
            .suggestionId = @"suggestionId",
        },
    .SheetDetentChanged =
        {
            .event = @"ANN.SheetDetentChanged",
            .componentId = @"componentId",
            .detent = @"detent",
        },
    .SlotDestroyed = {.event = @"ANN.SlotDestroyed", .componentId = @"componentId"},
    .SplitViewColumnsStacked =
        {
            .event = @"ANN.SplitViewColumnsStacked",
            .componentId = @"componentId",
            .stacked = @"stacked",
        },
    .SplitViewDisplayModeChanged =
        {
            .event = @"ANN.SplitViewDisplayModeChanged",
            .componentId = @"componentId",
            .displayMode = @"displayMode",
            .inspectorVisible = @"inspectorVisible",
        },
    .TitleRenamed = {.event = @"ANN.TitleRenamed", .componentId = @"componentId", .text = @"text"},
};

NSArray<NSString *> *ANNEventContractNames(void) {
    return @[
        ANNEventContract.AppLaunched.event,
        ANNEventContract.ArrangementChanged.event,
        ANNEventContract.BottomAccessoryPlacementChanged.event,
        ANNEventContract.BottomTabLongPressed.event,
        ANNEventContract.BottomTabPressed.event,
        ANNEventContract.BottomTabSelected.event,
        ANNEventContract.CommandCompleted.event,
        ANNEventContract.ComponentDidAppear.event,
        ANNEventContract.ComponentDidDisappear.event,
        ANNEventContract.ComponentWillAppear.event,
        ANNEventContract.DeepLinkDispatched.event,
        ANNEventContract.HingeChanged.event,
        ANNEventContract.KeyCommandPressed.event,
        ANNEventContract.ModalAttemptedToDismiss.event,
        ANNEventContract.ModalDismissed.event,
        ANNEventContract.NativeScreenCall.event,
        ANNEventContract.NativeScreenReleased.event,
        ANNEventContract.NavigationButtonPressed.event,
        ANNEventContract.NavigationStateChanged.event,
        ANNEventContract.PreviewCompleted.event,
        ANNEventContract.ScreenInsetsChanged.event,
        ANNEventContract.ScreenPopped.event,
        ANNEventContract.ScreenRegionsChanged.event,
        ANNEventContract.SearchBarCancelPressed.event,
        ANNEventContract.SearchBarSubmitted.event,
        ANNEventContract.SearchBarUpdated.event,
        ANNEventContract.SearchSuggestionPicked.event,
        ANNEventContract.SheetDetentChanged.event,
        ANNEventContract.SlotDestroyed.event,
        ANNEventContract.SplitViewColumnsStacked.event,
        ANNEventContract.SplitViewDisplayModeChanged.event,
        ANNEventContract.TitleRenamed.event,
    ];
}

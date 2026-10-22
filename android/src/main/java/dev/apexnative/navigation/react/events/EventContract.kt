// Made by scripts/contract.mjs from the event types of src/interfaces/: change them and run the script again.

package dev.apexnative.navigation.react.events

/**
 * The names of the events `ANN.<Name>` ([EVENT]) and the keys of their payloads, as the types of `src/interfaces/` give
 * them; what each field means is its JSDoc there. The object types a field holds have keys of their own. The sender
 * and the builders of payloads take every name and key from here: `EventContract.CommandCompleted.COMMAND_ID`. The
 * events and fields only iOS sends are not here.
 */
internal object EventContract {
    /** `ANN.AppLaunched`. */
    object AppLaunched {
        const val EVENT = "ANN.AppLaunched"
    }

    /** `ANN.ArrangementChanged`. */
    object ArrangementChanged {
        const val EVENT = "ANN.ArrangementChanged"
        const val COMPONENT_ID = "componentId"
        const val PLACEMENT = "placement"
        const val LAYOUT = "layout"
        const val FRONT = "front"
        const val HIDDEN = "hidden"
    }

    /** `ANN.BottomTabLongPressed`. */
    object BottomTabLongPressed {
        const val EVENT = "ANN.BottomTabLongPressed"
        const val SELECTED_TAB_INDEX = "selectedTabIndex"
        const val BOTTOM_TABS_ID = "bottomTabsId"
    }

    /** `ANN.BottomTabPressed`. */
    object BottomTabPressed {
        const val EVENT = "ANN.BottomTabPressed"
        const val TAB_INDEX = "tabIndex"
        const val BOTTOM_TABS_ID = "bottomTabsId"
        /** Optional. */
        const val SCREEN_ID = "screenId"
        const val WAS_SELECTED = "wasSelected"
    }

    /** `ANN.BottomTabSelected`. */
    object BottomTabSelected {
        const val EVENT = "ANN.BottomTabSelected"
        const val SELECTED_TAB_INDEX = "selectedTabIndex"
        const val UNSELECTED_TAB_INDEX = "unselectedTabIndex"
        const val BOTTOM_TABS_ID = "bottomTabsId"
    }

    /** `ANN.CommandCompleted`. */
    object CommandCompleted {
        const val EVENT = "ANN.CommandCompleted"
        const val COMMAND_NAME = "commandName"
        const val COMMAND_ID = "commandId"
        const val COMPLETION_TIME = "completionTime"
    }

    /** `ANN.ComponentDidAppear`. */
    object ComponentDidAppear {
        const val EVENT = "ANN.ComponentDidAppear"
        const val COMPONENT_ID = "componentId"
        const val COMPONENT_NAME = "componentName"
        const val COMPONENT_TYPE = "componentType"
    }

    /** `ANN.ComponentDidDisappear`. */
    object ComponentDidDisappear {
        const val EVENT = "ANN.ComponentDidDisappear"
        const val COMPONENT_ID = "componentId"
        const val COMPONENT_NAME = "componentName"
        const val COMPONENT_TYPE = "componentType"
    }

    /** `ANN.ComponentWillAppear`. */
    object ComponentWillAppear {
        const val EVENT = "ANN.ComponentWillAppear"
        const val COMPONENT_ID = "componentId"
        const val COMPONENT_NAME = "componentName"
        const val COMPONENT_TYPE = "componentType"
    }

    /** `ANN.HingeChanged`. */
    object HingeChanged {
        const val EVENT = "ANN.HingeChanged"
        /** Optional. */
        const val STATE = "state"
        /** Optional. */
        const val ANGLE = "angle"
    }

    /** `ANN.ModalAttemptedToDismiss`. */
    object ModalAttemptedToDismiss {
        const val EVENT = "ANN.ModalAttemptedToDismiss"
        const val COMPONENT_ID = "componentId"
    }

    /** `ANN.ModalDismissed`. */
    object ModalDismissed {
        const val EVENT = "ANN.ModalDismissed"
        const val COMPONENT_ID = "componentId"
        const val COMPONENT_NAME = "componentName"
        const val MODALS_DISMISSED = "modalsDismissed"
    }

    /** `ANN.NativeScreenCall`. */
    object NativeScreenCall {
        const val EVENT = "ANN.NativeScreenCall"
        const val COMPONENT_ID = "componentId"
        const val CALL_ID = "callId"
        const val KIND = "kind"
        const val NAME = "name"
        const val ARGS = "args"
    }

    /** `ANN.NativeScreenReleased`. */
    object NativeScreenReleased {
        const val EVENT = "ANN.NativeScreenReleased"
        const val COMPONENT_ID = "componentId"
    }

    /** `ANN.NavigationButtonPressed`. */
    object NavigationButtonPressed {
        const val EVENT = "ANN.NavigationButtonPressed"
        const val COMPONENT_ID = "componentId"
        const val BUTTON_ID = "buttonId"
    }

    /** `ANN.NavigationStateChanged`. */
    object NavigationStateChanged {
        const val EVENT = "ANN.NavigationStateChanged"
        const val SCREEN = "screen"
        const val TAB = "tab"
    }

    /** The object of `ANN.NavigationStateChanged.screen`. */
    object NavigationStateChangedScreen {
        const val ID = "id"
        const val NAME = "name"
    }

    /** `ANN.ScreenInsetsChanged`. */
    object ScreenInsetsChanged {
        const val EVENT = "ANN.ScreenInsetsChanged"
        const val COMPONENT_ID = "componentId"
        const val TOP = "top"
        const val RIGHT = "right"
        const val BOTTOM = "bottom"
        const val LEFT = "left"
    }

    /** `ANN.ScreenPopped`. */
    object ScreenPopped {
        const val EVENT = "ANN.ScreenPopped"
        const val COMPONENT_ID = "componentId"
    }

    /** The object of `ANN.ScreenRegionsChanged.division`. */
    object ScreenRegionRect {
        const val X = "x"
        const val Y = "y"
        const val WIDTH = "width"
        const val HEIGHT = "height"
    }

    /** `ANN.ScreenRegionsChanged`. */
    object ScreenRegionsChanged {
        const val EVENT = "ANN.ScreenRegionsChanged"
        const val COMPONENT_ID = "componentId"
        const val DIVISION = "division"
        const val OCCLUSION = "occlusion"
    }

    /** `ANN.SearchBarCancelPressed`. */
    object SearchBarCancelPressed {
        const val EVENT = "ANN.SearchBarCancelPressed"
        const val COMPONENT_ID = "componentId"
    }

    /** `ANN.SearchBarSubmitted`. */
    object SearchBarSubmitted {
        const val EVENT = "ANN.SearchBarSubmitted"
        const val COMPONENT_ID = "componentId"
        const val TEXT = "text"
        /** Optional. */
        const val SCOPE = "scope"
    }

    /** `ANN.SearchBarUpdated`. */
    object SearchBarUpdated {
        const val EVENT = "ANN.SearchBarUpdated"
        const val COMPONENT_ID = "componentId"
        const val TEXT = "text"
        const val IS_FOCUSED = "isFocused"
        /** Optional. */
        const val SCOPE = "scope"
    }

    /** `ANN.SearchSuggestionPicked`. */
    object SearchSuggestionPicked {
        const val EVENT = "ANN.SearchSuggestionPicked"
        const val COMPONENT_ID = "componentId"
        const val SUGGESTION_ID = "suggestionId"
    }

    /** `ANN.SheetDetentChanged`. */
    object SheetDetentChanged {
        const val EVENT = "ANN.SheetDetentChanged"
        const val COMPONENT_ID = "componentId"
        const val DETENT = "detent"
    }

    /** `ANN.SlotDestroyed`. */
    object SlotDestroyed {
        const val EVENT = "ANN.SlotDestroyed"
        const val COMPONENT_ID = "componentId"
    }

    /** `ANN.SplitViewColumnsStacked`. */
    object SplitViewColumnsStacked {
        const val EVENT = "ANN.SplitViewColumnsStacked"
        const val COMPONENT_ID = "componentId"
        const val STACKED = "stacked"
    }
}

package dev.apexnative.navigation.react.events

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.NavigationApplication
import dev.apexnative.navigation.nativescreen.NativeScreenCalls
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.utils.NavigationLog
import org.json.JSONArray

/**
 * Sends the events `ANN.<Name>` to the JavaScript of [reactContext] through React Native's device event emitter, in
 * the order they are sent: sent from the main thread only. Without a context, or when its React instance is gone, an
 * event is dropped with one line in the log. The names and the keys come from [EventContract], made from the types of
 * `src/interfaces/`.
 *
 * An emitter belongs to one JavaScript, as on iOS (`ANNEventEmitter`): it takes the number of the JavaScript
 * ([generation], `ReloadCoordinator`) when it is made and drops every event silently once that number moved. React
 * Native keeps one context across a reload (bridgeless), so without it an event of a screen of the JavaScript that
 * went would reach the new one, which may have a screen with the same id. The core and its modules send through
 * [current], or [of] with the number of the JavaScript a node was made for.
 */
class EventEmitter @JvmOverloads constructor(
    private val reactContext: ReactContext?,
    /** The number of the JavaScript this emitter sends to: by default the one that runs as it is made. */
    val generation: Int = ReloadCoordinator.generation,
) {
    /** Whether the JavaScript of this emitter still runs: its events go only then. Any thread. */
    val isCurrent: Boolean
        get() = ReloadCoordinator.isCurrent(generation)

    /** Whether an event sent now reaches JavaScript: its JavaScript still runs, in a live React instance. */
    val canSend: Boolean
        get() = isCurrent && reactContext?.hasActiveReactInstance() == true

    fun emitAppLaunched() = emit(EventContract.AppLaunched.EVENT, Arguments.createMap())

    fun emitCommandCompleted(commandName: String, commandId: String, completionTime: Long) =
        emit(EventContract.CommandCompleted.EVENT, Arguments.createMap().apply {
            putString(EventContract.CommandCompleted.COMMAND_NAME, commandName)
            putString(EventContract.CommandCompleted.COMMAND_ID, commandId)
            putDouble(EventContract.CommandCompleted.COMPLETION_TIME, completionTime.toDouble())
        })

    fun emitComponentWillAppear(componentId: String, componentName: String, type: ComponentType) =
        emit(EventContract.ComponentWillAppear.EVENT, component(componentId, componentName, type))

    fun emitComponentDidAppear(componentId: String, componentName: String, type: ComponentType) =
        emit(EventContract.ComponentDidAppear.EVENT, component(componentId, componentName, type))

    fun emitComponentDidDisappear(componentId: String, componentName: String, type: ComponentType) =
        emit(EventContract.ComponentDidDisappear.EVENT, component(componentId, componentName, type))

    /** The screen the user sees (`null` for none) and the selected tab of the tabs it is in (`null` out of tabs). */
    fun emitNavigationStateChanged(screenId: String?, screenName: String?, tab: Int?) =
        emit(EventContract.NavigationStateChanged.EVENT, Arguments.createMap().apply {
            val keys = EventContract.NavigationStateChanged
            if (screenId == null) {
                putNull(keys.SCREEN)
            } else {
                putMap(keys.SCREEN, Arguments.createMap().apply {
                    putString(EventContract.NavigationStateChangedScreen.ID, screenId)
                    putString(EventContract.NavigationStateChangedScreen.NAME, screenName)
                })
            }
            if (tab == null) putNull(keys.TAB) else putInt(keys.TAB, tab)
        })

    fun emitScreenPopped(componentId: String) = emit(EventContract.ScreenPopped.EVENT, withId(componentId))

    fun emitNavigationButtonPressed(componentId: String, buttonId: String) =
        emit(EventContract.NavigationButtonPressed.EVENT, withId(componentId).apply {
            putString(EventContract.NavigationButtonPressed.BUTTON_ID, buttonId)
        })

    fun emitBottomTabSelected(selectedTabIndex: Int, unselectedTabIndex: Int, bottomTabsId: String) =
        emit(EventContract.BottomTabSelected.EVENT, Arguments.createMap().apply {
            putInt(EventContract.BottomTabSelected.SELECTED_TAB_INDEX, selectedTabIndex)
            putInt(EventContract.BottomTabSelected.UNSELECTED_TAB_INDEX, unselectedTabIndex)
            putString(EventContract.BottomTabSelected.BOTTOM_TABS_ID, bottomTabsId)
        })

    fun emitBottomTabPressed(tabIndex: Int, bottomTabsId: String, screenId: String?, wasSelected: Boolean) =
        emit(EventContract.BottomTabPressed.EVENT, Arguments.createMap().apply {
            putInt(EventContract.BottomTabPressed.TAB_INDEX, tabIndex)
            putString(EventContract.BottomTabPressed.BOTTOM_TABS_ID, bottomTabsId)
            screenId?.let { putString(EventContract.BottomTabPressed.SCREEN_ID, it) }
            putBoolean(EventContract.BottomTabPressed.WAS_SELECTED, wasSelected)
        })

    fun emitBottomTabLongPressed(selectedTabIndex: Int, bottomTabsId: String) =
        emit(EventContract.BottomTabLongPressed.EVENT, Arguments.createMap().apply {
            putInt(EventContract.BottomTabLongPressed.SELECTED_TAB_INDEX, selectedTabIndex)
            putString(EventContract.BottomTabLongPressed.BOTTOM_TABS_ID, bottomTabsId)
        })

    /** The id and the name (without `slot:`) of the screen the modal showed, and how many modals went. */
    fun emitModalDismissed(componentId: String, componentName: String, modalsDismissed: Int) =
        emit(EventContract.ModalDismissed.EVENT, withId(componentId).apply {
            putString(EventContract.ModalDismissed.COMPONENT_NAME, componentName)
            putInt(EventContract.ModalDismissed.MODALS_DISMISSED, modalsDismissed)
        })

    fun emitModalAttemptedToDismiss(componentId: String) =
        emit(EventContract.ModalAttemptedToDismiss.EVENT, withId(componentId))

    /** [detent]: `medium`, `large`, or a height in dp. */
    fun emitSheetDetentChanged(componentId: String, detent: Any) =
        emit(EventContract.SheetDetentChanged.EVENT, withId(componentId).apply {
            when (detent) {
                is Number -> putDouble(EventContract.SheetDetentChanged.DETENT, detent.toDouble())
                else -> putString(EventContract.SheetDetentChanged.DETENT, detent.toString())
            }
        })

    fun emitSearchBarUpdated(componentId: String, text: String, isFocused: Boolean, scope: String?) =
        emit(EventContract.SearchBarUpdated.EVENT, withId(componentId).apply {
            putString(EventContract.SearchBarUpdated.TEXT, text)
            putBoolean(EventContract.SearchBarUpdated.IS_FOCUSED, isFocused)
            scope?.let { putString(EventContract.SearchBarUpdated.SCOPE, it) }
        })

    fun emitSearchBarSubmitted(componentId: String, text: String, scope: String?) =
        emit(EventContract.SearchBarSubmitted.EVENT, withId(componentId).apply {
            putString(EventContract.SearchBarSubmitted.TEXT, text)
            scope?.let { putString(EventContract.SearchBarSubmitted.SCOPE, it) }
        })

    fun emitSearchBarCancelPressed(componentId: String) =
        emit(EventContract.SearchBarCancelPressed.EVENT, withId(componentId))

    fun emitSearchSuggestionPicked(componentId: String, suggestionId: String) =
        emit(EventContract.SearchSuggestionPicked.EVENT, withId(componentId).apply {
            putString(EventContract.SearchSuggestionPicked.SUGGESTION_ID, suggestionId)
        })

    fun emitSplitViewColumnsStacked(componentId: String, stacked: Boolean) =
        emit(EventContract.SplitViewColumnsStacked.EVENT, withId(componentId).apply {
            putBoolean(EventContract.SplitViewColumnsStacked.STACKED, stacked)
        })

    fun emitSlotDestroyed(componentId: String) = emit(EventContract.SlotDestroyed.EVENT, withId(componentId))

    /** [insets]: `{ top, right, bottom, left }`. */
    fun emitScreenInsetsChanged(componentId: String, insets: WritableMap) =
        emit(EventContract.ScreenInsetsChanged.EVENT, withId(componentId).apply { merge(insets) })

    /** [regions]: `{ division, occlusion }`. */
    fun emitScreenRegionsChanged(componentId: String, regions: WritableMap) =
        emit(EventContract.ScreenRegionsChanged.EVENT, withId(componentId).apply { merge(regions) })

    /** [state]: `{ placement, layout, front, hidden }`. */
    fun emitArrangementChanged(componentId: String, state: WritableMap) =
        emit(EventContract.ArrangementChanged.EVENT, withId(componentId).apply { merge(state) })

    /** [value]: `{ state, angle? }`, or `{}` without a hinge. */
    fun emitHingeChanged(value: WritableMap) = emit(EventContract.HingeChanged.EVENT, value)

    /** [kind]: `function` or `command`; [arguments] as JSON, which JavaScript gets as `args`. */
    fun emitNativeScreenCall(componentId: String, callId: String, kind: String, name: String, arguments: JSONArray) =
        emit(EventContract.NativeScreenCall.EVENT, withId(componentId).apply {
            putString(EventContract.NativeScreenCall.CALL_ID, callId)
            putString(EventContract.NativeScreenCall.KIND, kind)
            putString(EventContract.NativeScreenCall.NAME, name)
            putArray(EventContract.NativeScreenCall.ARGS, NativeScreenCalls.toArguments(arguments))
        })

    fun emitNativeScreenReleased(componentId: String) =
        emit(EventContract.NativeScreenReleased.EVENT, withId(componentId))

    // The three appearance events have the same keys.
    private fun component(componentId: String, componentName: String, type: ComponentType): WritableMap =
        withId(componentId).apply {
            putString(EventContract.ComponentDidAppear.COMPONENT_NAME, componentName)
            putString(EventContract.ComponentDidAppear.COMPONENT_TYPE, type.jsName)
        }

    // The events of a screen carry its id under the same key.
    private fun withId(componentId: String): WritableMap =
        Arguments.createMap().apply { putString(EventContract.ScreenPopped.COMPONENT_ID, componentId) }

    private fun emit(name: String, data: WritableMap) {
        // The JavaScript of this emitter went: the new one never had its screens.
        if (!isCurrent) return
        val context = reactContext
        if (context == null || !context.hasActiveReactInstance()) {
            Log.w(NavigationLog.TAG, "Could not send event $name. React context is null!")
            return
        }
        context.emitDeviceEvent(name, data)
    }

    companion object {
        /**
         * The sender to the JavaScript that runs now: the React context of the app's `ReactGateway`. It goes silent
         * once that JavaScript goes (a reload): one kept for later sends nothing to the next.
         */
        @JvmStatic
        fun current(): EventEmitter = of(ReloadCoordinator.generation)

        /**
         * The sender to the JavaScript [generation] (a node's `jsGeneration`): the React context of the app's
         * `ReactGateway` while that JavaScript runs, silent once it went, so an event of a screen made for the
         * JavaScript that went never reaches the new one.
         */
        internal fun of(generation: Int): EventEmitter =
            EventEmitter(NavigationApplication.instanceOrNull?.reactGatewayOrNull?.currentReactContext, generation)

        // The names of the events, for modules outside the library; inside it, [EventContract] has them with their
        // keys.
        const val APP_LAUNCHED = EventContract.AppLaunched.EVENT
        const val COMMAND_COMPLETED = EventContract.CommandCompleted.EVENT
        const val COMPONENT_WILL_APPEAR = EventContract.ComponentWillAppear.EVENT
        const val COMPONENT_DID_APPEAR = EventContract.ComponentDidAppear.EVENT
        const val COMPONENT_DID_DISAPPEAR = EventContract.ComponentDidDisappear.EVENT
        const val NAVIGATION_STATE_CHANGED = EventContract.NavigationStateChanged.EVENT
        const val SCREEN_POPPED = EventContract.ScreenPopped.EVENT
        const val NAVIGATION_BUTTON_PRESSED = EventContract.NavigationButtonPressed.EVENT
        const val BOTTOM_TAB_SELECTED = EventContract.BottomTabSelected.EVENT
        const val BOTTOM_TAB_PRESSED = EventContract.BottomTabPressed.EVENT
        const val BOTTOM_TAB_LONG_PRESSED = EventContract.BottomTabLongPressed.EVENT
        const val MODAL_DISMISSED = EventContract.ModalDismissed.EVENT
        const val MODAL_ATTEMPTED_TO_DISMISS = EventContract.ModalAttemptedToDismiss.EVENT
        const val SHEET_DETENT_CHANGED = EventContract.SheetDetentChanged.EVENT
        const val SEARCH_BAR_UPDATED = EventContract.SearchBarUpdated.EVENT
        const val SEARCH_BAR_SUBMITTED = EventContract.SearchBarSubmitted.EVENT
        const val SEARCH_BAR_CANCEL_PRESSED = EventContract.SearchBarCancelPressed.EVENT
        const val SEARCH_SUGGESTION_PICKED = EventContract.SearchSuggestionPicked.EVENT
        const val SPLIT_VIEW_COLUMNS_STACKED = EventContract.SplitViewColumnsStacked.EVENT
        const val SLOT_DESTROYED = EventContract.SlotDestroyed.EVENT
        const val SCREEN_INSETS_CHANGED = EventContract.ScreenInsetsChanged.EVENT
        const val SCREEN_REGIONS_CHANGED = EventContract.ScreenRegionsChanged.EVENT
        const val ARRANGEMENT_CHANGED = EventContract.ArrangementChanged.EVENT
        const val HINGE_CHANGED = EventContract.HingeChanged.EVENT
        const val NATIVE_SCREEN_CALL = EventContract.NativeScreenCall.EVENT
        const val NATIVE_SCREEN_RELEASED = EventContract.NativeScreenReleased.EVENT
    }
}

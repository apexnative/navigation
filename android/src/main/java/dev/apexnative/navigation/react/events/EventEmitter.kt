package dev.apexnative.navigation.react.events

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.NavigationApplication
import dev.apexnative.navigation.nativescreen.NativeScreenCalls
import dev.apexnative.navigation.utils.NavigationLog
import org.json.JSONArray

/**
 * Sends the events `ANN.<Name>` to the JavaScript of [reactContext] through React Native's device event emitter, in
 * the order they are sent: sent from the main thread only. Without a context, or when its React instance is gone, an
 * event is dropped with one line in the log. The names and the fields are the contract. The core sends through
 * [current]; modules keep making their own.
 */
class EventEmitter(private val reactContext: ReactContext?) {
    /** Whether an event sent now reaches JavaScript. */
    val canSend: Boolean
        get() = reactContext?.hasActiveReactInstance() == true

    fun emitAppLaunched() = emit(APP_LAUNCHED, Arguments.createMap())

    fun emitCommandCompleted(commandName: String, commandId: String, completionTime: Long) =
        emit(COMMAND_COMPLETED, Arguments.createMap().apply {
            putString("commandName", commandName)
            putString("commandId", commandId)
            putDouble("completionTime", completionTime.toDouble())
        })

    fun emitComponentWillAppear(componentId: String, componentName: String, type: ComponentType) =
        emit(COMPONENT_WILL_APPEAR, component(componentId, componentName, type))

    fun emitComponentDidAppear(componentId: String, componentName: String, type: ComponentType) =
        emit(COMPONENT_DID_APPEAR, component(componentId, componentName, type))

    fun emitComponentDidDisappear(componentId: String, componentName: String, type: ComponentType) =
        emit(COMPONENT_DID_DISAPPEAR, component(componentId, componentName, type))

    /** The screen the user sees (`null` for none) and the selected tab of the tabs it is in (`null` out of tabs). */
    fun emitNavigationStateChanged(screenId: String?, screenName: String?, tab: Int?) =
        emit(NAVIGATION_STATE_CHANGED, Arguments.createMap().apply {
            if (screenId == null) {
                putNull("screen")
            } else {
                putMap("screen", Arguments.createMap().apply {
                    putString("id", screenId)
                    putString("name", screenName)
                })
            }
            if (tab == null) putNull("tab") else putInt("tab", tab)
        })

    fun emitScreenPopped(componentId: String) = emit(SCREEN_POPPED, withId(componentId))

    fun emitNavigationButtonPressed(componentId: String, buttonId: String) =
        emit(NAVIGATION_BUTTON_PRESSED, withId(componentId).apply { putString("buttonId", buttonId) })

    fun emitBottomTabSelected(selectedTabIndex: Int, unselectedTabIndex: Int, bottomTabsId: String) =
        emit(BOTTOM_TAB_SELECTED, Arguments.createMap().apply {
            putInt("selectedTabIndex", selectedTabIndex)
            putInt("unselectedTabIndex", unselectedTabIndex)
            putString("bottomTabsId", bottomTabsId)
        })

    fun emitBottomTabPressed(tabIndex: Int, bottomTabsId: String, screenId: String?, wasSelected: Boolean) =
        emit(BOTTOM_TAB_PRESSED, Arguments.createMap().apply {
            putInt("tabIndex", tabIndex)
            putString("bottomTabsId", bottomTabsId)
            screenId?.let { putString("screenId", it) }
            putBoolean("wasSelected", wasSelected)
        })

    fun emitBottomTabLongPressed(selectedTabIndex: Int, bottomTabsId: String) =
        emit(BOTTOM_TAB_LONG_PRESSED, Arguments.createMap().apply {
            putInt("selectedTabIndex", selectedTabIndex)
            putString("bottomTabsId", bottomTabsId)
        })

    /** The id and the name (without `slot:`) of the screen the modal showed, and how many modals went. */
    fun emitModalDismissed(componentId: String, componentName: String, modalsDismissed: Int) =
        emit(MODAL_DISMISSED, withId(componentId).apply {
            putString("componentName", componentName)
            putInt("modalsDismissed", modalsDismissed)
        })

    fun emitModalAttemptedToDismiss(componentId: String) = emit(MODAL_ATTEMPTED_TO_DISMISS, withId(componentId))

    /** [detent]: `medium`, `large`, or a height in dp. */
    fun emitSheetDetentChanged(componentId: String, detent: Any) =
        emit(SHEET_DETENT_CHANGED, withId(componentId).apply {
            when (detent) {
                is Number -> putDouble("detent", detent.toDouble())
                else -> putString("detent", detent.toString())
            }
        })

    fun emitSearchBarUpdated(componentId: String, text: String, isFocused: Boolean, scope: String?) =
        emit(SEARCH_BAR_UPDATED, withId(componentId).apply {
            putString("text", text)
            putBoolean("isFocused", isFocused)
            scope?.let { putString("scope", it) }
        })

    fun emitSearchBarSubmitted(componentId: String, text: String, scope: String?) =
        emit(SEARCH_BAR_SUBMITTED, withId(componentId).apply {
            putString("text", text)
            scope?.let { putString("scope", it) }
        })

    fun emitSearchBarCancelPressed(componentId: String) = emit(SEARCH_BAR_CANCEL_PRESSED, withId(componentId))

    fun emitSearchSuggestionPicked(componentId: String, suggestionId: String) =
        emit(SEARCH_SUGGESTION_PICKED, withId(componentId).apply { putString("suggestionId", suggestionId) })

    fun emitSplitViewColumnsStacked(componentId: String, stacked: Boolean) =
        emit(SPLIT_VIEW_COLUMNS_STACKED, withId(componentId).apply { putBoolean("stacked", stacked) })

    fun emitSlotDestroyed(componentId: String) = emit(SLOT_DESTROYED, withId(componentId))

    /** [insets]: `{ top, right, bottom, left }`. */
    fun emitScreenInsetsChanged(componentId: String, insets: WritableMap) =
        emit(SCREEN_INSETS_CHANGED, withId(componentId).apply { merge(insets) })

    /** [regions]: `{ division, occlusion }`. */
    fun emitScreenRegionsChanged(componentId: String, regions: WritableMap) =
        emit(SCREEN_REGIONS_CHANGED, withId(componentId).apply { merge(regions) })

    /** [state]: `{ placement, layout, front, hidden }`. */
    fun emitArrangementChanged(componentId: String, state: WritableMap) =
        emit(ARRANGEMENT_CHANGED, withId(componentId).apply { merge(state) })

    /** [value]: `{ state, angle? }`, or `{}` without a hinge. */
    fun emitHingeChanged(value: WritableMap) = emit(HINGE_CHANGED, value)

    /** [kind]: `function` or `command`; [arguments] as JSON, which JavaScript gets as `args`. */
    fun emitNativeScreenCall(componentId: String, callId: String, kind: String, name: String, arguments: JSONArray) =
        emit(NATIVE_SCREEN_CALL, withId(componentId).apply {
            putString("callId", callId)
            putString("kind", kind)
            putString("name", name)
            putArray("args", NativeScreenCalls.toArguments(arguments))
        })

    fun emitNativeScreenReleased(componentId: String) = emit(NATIVE_SCREEN_RELEASED, withId(componentId))

    private fun component(componentId: String, componentName: String, type: ComponentType): WritableMap =
        withId(componentId).apply {
            putString("componentName", componentName)
            putString("componentType", type.jsName)
        }

    private fun withId(componentId: String): WritableMap =
        Arguments.createMap().apply { putString("componentId", componentId) }

    private fun emit(name: String, data: WritableMap) {
        val context = reactContext
        if (context == null || !context.hasActiveReactInstance()) {
            Log.w(NavigationLog.TAG, "Could not send event $name. React context is null!")
            return
        }
        context.emitDeviceEvent(name, data)
    }

    companion object {
        /** The sender to the JavaScript that runs now: the React context of the app's `ReactGateway`. */
        @JvmStatic
        fun current(): EventEmitter =
            EventEmitter(NavigationApplication.instanceOrNull?.reactGatewayOrNull?.currentReactContext)

        const val APP_LAUNCHED = "ANN.AppLaunched"
        const val COMMAND_COMPLETED = "ANN.CommandCompleted"
        const val COMPONENT_WILL_APPEAR = "ANN.ComponentWillAppear"
        const val COMPONENT_DID_APPEAR = "ANN.ComponentDidAppear"
        const val COMPONENT_DID_DISAPPEAR = "ANN.ComponentDidDisappear"
        const val NAVIGATION_STATE_CHANGED = "ANN.NavigationStateChanged"
        const val SCREEN_POPPED = "ANN.ScreenPopped"
        const val NAVIGATION_BUTTON_PRESSED = "ANN.NavigationButtonPressed"
        const val BOTTOM_TAB_SELECTED = "ANN.BottomTabSelected"
        const val BOTTOM_TAB_PRESSED = "ANN.BottomTabPressed"
        const val BOTTOM_TAB_LONG_PRESSED = "ANN.BottomTabLongPressed"
        const val MODAL_DISMISSED = "ANN.ModalDismissed"
        const val MODAL_ATTEMPTED_TO_DISMISS = "ANN.ModalAttemptedToDismiss"
        const val SHEET_DETENT_CHANGED = "ANN.SheetDetentChanged"
        const val SEARCH_BAR_UPDATED = "ANN.SearchBarUpdated"
        const val SEARCH_BAR_SUBMITTED = "ANN.SearchBarSubmitted"
        const val SEARCH_BAR_CANCEL_PRESSED = "ANN.SearchBarCancelPressed"
        const val SEARCH_SUGGESTION_PICKED = "ANN.SearchSuggestionPicked"
        const val SPLIT_VIEW_COLUMNS_STACKED = "ANN.SplitViewColumnsStacked"
        const val SLOT_DESTROYED = "ANN.SlotDestroyed"
        const val SCREEN_INSETS_CHANGED = "ANN.ScreenInsetsChanged"
        const val SCREEN_REGIONS_CHANGED = "ANN.ScreenRegionsChanged"
        const val ARRANGEMENT_CHANGED = "ANN.ArrangementChanged"
        const val HINGE_CHANGED = "ANN.HingeChanged"
        const val NATIVE_SCREEN_CALL = "ANN.NativeScreenCall"
        const val NATIVE_SCREEN_RELEASED = "ANN.NativeScreenReleased"
    }
}

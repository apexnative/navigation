package dev.apexnative.navigation.react

import android.content.Context
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.react.events.EventEmitter
import java.util.concurrent.ConcurrentHashMap

/**
 * The state of each screen of a pair (`useArrangement`): which screen of the pair it is, how the pair lies, whether it
 * lies over the other one and whether it is out of the window. Set on the main thread as the pair lays out; JavaScript
 * reads it from its own thread for a screen's first render, and gets every change as `ANN.ArrangementChanged`. A
 * screen outside a pair has none.
 */
internal object ArrangementStates {
    data class State(val placement: String, val layout: String, val front: Boolean, val hidden: Boolean)

    private val states = ConcurrentHashMap<String, State>()

    /** Keeps the screen's state, and tells JavaScript when it changed and [notify] holds. Main thread only. */
    fun set(context: Context, screenId: String, state: State, notify: Boolean) {
        if (states.put(screenId, state) == state || !notify) return
        val reactContext = (context.applicationContext as? ReactApplication)?.reactHost?.currentReactContext ?: return
        EventEmitter(reactContext).emitArrangementChanged(screenId, toMap(state))
    }

    /** `{ placement, layout, front, hidden }`, empty for a screen outside a pair. Any thread. */
    fun get(screenId: String): WritableMap = states[screenId]?.let(::toMap) ?: Arguments.createMap()

    fun remove(screenId: String) {
        states.remove(screenId)
    }

    private fun toMap(state: State): WritableMap = Arguments.createMap().apply {
        putString("placement", state.placement)
        putString("layout", state.layout)
        putBoolean("front", state.front)
        putBoolean("hidden", state.hidden)
    }
}

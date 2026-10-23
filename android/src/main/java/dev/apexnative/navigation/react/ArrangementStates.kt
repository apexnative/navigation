package dev.apexnative.navigation.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.react.events.EventContract
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

    /**
     * Keeps the screen's state, and tells JavaScript when it changed and [notify] holds, through [emitter], the one of
     * the JavaScript the screen was made for: silent once it went. Main thread only.
     */
    fun set(screenId: String, state: State, notify: Boolean, emitter: EventEmitter) {
        if (states.put(screenId, state) == state || !notify) return
        emitter.emitArrangementChanged(screenId, toMap(state))
    }

    /** `{ placement, layout, front, hidden }`, empty for a screen outside a pair. Any thread. */
    fun get(screenId: String): WritableMap = states[screenId]?.let(::toMap) ?: Arguments.createMap()

    fun remove(screenId: String) {
        states.remove(screenId)
    }

    private fun toMap(state: State): WritableMap = Arguments.createMap().apply {
        putString(EventContract.ArrangementChanged.PLACEMENT, state.placement)
        putString(EventContract.ArrangementChanged.LAYOUT, state.layout)
        putBoolean(EventContract.ArrangementChanged.FRONT, state.front)
        putBoolean(EventContract.ArrangementChanged.HIDDEN, state.hidden)
    }
}

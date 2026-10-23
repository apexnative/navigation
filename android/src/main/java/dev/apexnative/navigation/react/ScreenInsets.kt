package dev.apexnative.navigation.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.react.events.EventContract
import dev.apexnative.navigation.react.events.EventEmitter
import java.util.concurrent.ConcurrentHashMap

/**
 * The safe area of each screen, in dp: how much of its content the bars and the system cover on each side. The padding
 * is the part of it the screen's safe area view keeps its content out of: the insets on the edges the screen's options
 * give. JavaScript gets the rest, what still covers the content (0 on a padded edge): it reads it from its own thread
 * for a screen's first render, and gets every change as `ANN.ScreenInsetsChanged`.
 */
object ScreenInsets {
    data class Insets(val top: Double, val right: Double, val bottom: Double, val left: Double)

    private val insets = ConcurrentHashMap<String, Insets>()
    // Main thread only.
    private val padding = HashMap<String, Insets>()
    private val paddingListeners = HashMap<String, MutableSet<() -> Unit>>()

    /** The padding of the screen's safe area view; none before the screen was laid out. */
    fun padding(screenId: String): Insets? = padding[screenId]

    /** Calls `listener` when the padding of the screen changes; returns the function that stops it. */
    fun listenToPadding(screenId: String, listener: () -> Unit): () -> Unit {
        paddingListeners.getOrPut(screenId) { LinkedHashSet() }.add(listener)
        return {
            paddingListeners[screenId]?.let {
                it.remove(listener)
                if (it.isEmpty()) paddingListeners.remove(screenId)
            }
        }
    }

    /**
     * Keeps the screen's insets and padding, and tells when they changed through [emitter], the one of the JavaScript
     * the screen was made for: silent once it went.
     */
    fun update(screenId: String, value: Insets, paddingValue: Insets, emitter: EventEmitter) {
        if (padding.put(screenId, paddingValue) != paddingValue) {
            paddingListeners[screenId]?.toList()?.forEach { it() }
        }
        // JavaScript gets what still covers the content: the edges the safe area view keeps it off are 0.
        val uncovered = Insets(
            top = (value.top - paddingValue.top).coerceAtLeast(0.0),
            right = (value.right - paddingValue.right).coerceAtLeast(0.0),
            bottom = (value.bottom - paddingValue.bottom).coerceAtLeast(0.0),
            left = (value.left - paddingValue.left).coerceAtLeast(0.0),
        )
        if (insets.put(screenId, uncovered) == uncovered) return
        emitter.emitScreenInsetsChanged(screenId, toMap(uncovered))
    }

    /** `{ top, right, bottom, left }`, empty before the screen was laid out. Any thread. */
    fun get(screenId: String): WritableMap = insets[screenId]?.let(::toMap) ?: Arguments.createMap()

    fun remove(screenId: String) {
        insets.remove(screenId)
        padding.remove(screenId)
    }

    private fun toMap(value: Insets): WritableMap = Arguments.createMap().apply {
        putDouble(EventContract.ScreenInsetsChanged.TOP, value.top)
        putDouble(EventContract.ScreenInsetsChanged.RIGHT, value.right)
        putDouble(EventContract.ScreenInsetsChanged.BOTTOM, value.bottom)
        putDouble(EventContract.ScreenInsetsChanged.LEFT, value.left)
    }
}

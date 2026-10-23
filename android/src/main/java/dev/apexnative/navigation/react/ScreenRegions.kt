package dev.apexnative.navigation.react

import android.app.Activity
import android.graphics.Rect
import androidx.core.view.WindowInsetsCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import dev.apexnative.navigation.react.events.EventContract
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.utils.WindowFolds
import java.util.concurrent.ConcurrentHashMap

/**
 * The reserved regions of each screen (`useReservedRegions`), in dp from the top left of its content: where it divides
 * (a fold the window is divided at, `division`) and where hardware covers it (a fold that hides what is under it, the
 * camera cutout: `occlusion`). Only those on the screen, cut to it: a fold at the edge of a screen of a pair is not on
 * it. Measured on the main thread as each screen lays out, and again after a change of the window's folds (which lays
 * the window out); JavaScript reads them from its own thread for a screen's first render, and gets every change as
 * `ANN.ScreenRegionsChanged`.
 */
internal object ScreenRegions {
    data class Region(val x: Double, val y: Double, val width: Double, val height: Double)

    data class Regions(val division: List<Region>, val occlusion: List<Region>) {
        val isEmpty: Boolean get() = division.isEmpty() && occlusion.isEmpty()
    }

    private val NONE = Regions(emptyList(), emptyList())
    private val regions = ConcurrentHashMap<String, Regions>()

    /**
     * Measures the regions of the screen whose content the layout puts at [content] in the window (pixels), with the
     * window's [insets] (the camera cutout), and tells JavaScript when they changed, through [emitter], the one of the
     * JavaScript the screen was made for: silent once it went. Main thread only.
     */
    fun update(
        activity: Activity,
        screenId: String,
        content: Rect,
        insets: WindowInsetsCompat?,
        emitter: EventEmitter,
    ) {
        val folds = WindowFolds.of(activity)?.folds.orEmpty()
        val cutouts = insets?.displayCutout?.boundingRects.orEmpty()
        val density = activity.resources.displayMetrics.density
        val value = Regions(
            division = folds.filter { it.separating }.mapNotNull { onScreen(it.bounds, content, density) },
            occlusion = (folds.filter { it.occludes }.map { it.bounds } + cutouts)
                .mapNotNull { onScreen(it, content, density) },
        )
        val previous = regions.put(screenId, value)
        // None at first is what JavaScript reads before the first layout.
        if (previous == value || (previous == null && value.isEmpty)) return
        emitter.emitScreenRegionsChanged(screenId, toMap(value))
    }

    /** `{ division, occlusion }`, empty lists for an unknown screen and before its first layout. Any thread. */
    fun get(screenId: String): WritableMap = toMap(regions[screenId] ?: NONE)

    fun remove(screenId: String) {
        regions.remove(screenId)
    }

    // The part of [bounds] (window pixels) on the screen, in dp from its top left; null off it. A fold without width
    // counts where it crosses the screen.
    private fun onScreen(bounds: Rect, content: Rect, density: Float): Region? {
        val part = Rect(bounds)
        if (!part.intersect(content)) return null
        return Region(
            x = (part.left - content.left) / density.toDouble(),
            y = (part.top - content.top) / density.toDouble(),
            width = part.width() / density.toDouble(),
            height = part.height() / density.toDouble(),
        )
    }

    private fun toMap(value: Regions): WritableMap = Arguments.createMap().apply {
        putArray(EventContract.ScreenRegionsChanged.DIVISION, toArray(value.division))
        putArray(EventContract.ScreenRegionsChanged.OCCLUSION, toArray(value.occlusion))
    }

    private fun toArray(regions: List<Region>): WritableArray = Arguments.createArray().apply {
        for (region in regions) {
            pushMap(Arguments.createMap().apply {
                putDouble(EventContract.ScreenRegionRect.X, region.x)
                putDouble(EventContract.ScreenRegionRect.Y, region.y)
                putDouble(EventContract.ScreenRegionRect.WIDTH, region.width)
                putDouble(EventContract.ScreenRegionRect.HEIGHT, region.height)
            })
        }
    }
}

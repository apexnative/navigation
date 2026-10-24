package dev.apexnative.navigation.react

import android.app.Activity
import android.graphics.Rect
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.react.ScreenEnvironment.Region
import dev.apexnative.navigation.react.ScreenEnvironment.Regions
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.utils.WindowFolds

/**
 * Measures the reserved regions of each screen (`useReservedRegions`), in dp from the top left of its content: where
 * it divides (a fold the window is divided at, `division`) and where hardware covers it (a fold that hides what is
 * under it, the camera cutout: `occlusion`). Only those on the screen, cut to it: a fold at the edge of a screen of a
 * pair is not on it. Measured on the main thread as each screen lays out, and again after a change of the window's
 * folds (which lays the window out); [ScreenEnvironment] keeps them, answers JavaScript's reads and tells it of every
 * change as `ANN.ScreenRegionsChanged`.
 */
internal object ScreenRegions {
    /**
     * Measures the regions of the screen whose content the layout puts at [content] in the window (pixels), with the
     * window's [insets] (the camera cutout), and keeps them in [ScreenEnvironment], which tells JavaScript when they
     * changed through [emitter], the one of the JavaScript the screen was made for: silent once it went. Main thread
     * only.
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
        ScreenEnvironment.setRegions(screenId, value, ScreenEnvironment.changesOf(emitter))
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
}

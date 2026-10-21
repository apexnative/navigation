package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.options.LargeTitleOptions

/**
 * The large title of the bar: when it shows, and how it collapses as the list of the screen scrolls. Pure functions;
 * the presenter applies them to the views.
 */
internal object LargeTitleCollapse {
    /**
     * Whether the large title shows over the entry at [index] of [modes], the modes of the stack's entries bottom to
     * top (`LargeTitleOptions.mode`): `always` yes, `never` (and `inline`) no, `automatic` as the entry below, and
     * the first entry shows it. Never on a hidden bar ([barVisible] false).
     */
    fun isShown(modes: List<String>, index: Int, barVisible: Boolean): Boolean {
        if (!barVisible || index !in modes.indices) return false
        var current = index
        while (current >= 0) {
            when (modes[current]) {
                LargeTitleOptions.ALWAYS -> return true
                LargeTitleOptions.AUTOMATIC -> if (current == 0) return true else current--
                else -> return false
            }
        }
        return false
    }

    /** How far the large title has collapsed for the list's offset [offset], over [range] (88 dp): 0 … 1. */
    fun fraction(offset: Float, range: Float): Float = if (range <= 0f) 0f else (offset / range).coerceIn(0f, 1f)

    /** How far the bar moves up, in pixels: the list's offset, at most [range]. */
    fun shift(offset: Float, range: Float): Float = offset.coerceIn(0f, range.coerceAtLeast(0f))

    /** The large title's opacity: it fades as it collapses. */
    fun largeTitleAlpha(fraction: Float): Float = 1f - fraction

    /** The opacity of the row's title: it comes in on the curve (0.8, 0, 0.8, 0.15). */
    fun rowTitleAlpha(fraction: Float): Float = ROW_TITLE_CURVE.at(fraction)

    /** Accessibility goes to the large title while more than half of it shows. */
    fun largeTitleHasAccessibility(fraction: Float): Boolean = fraction < 0.5f

    private val ROW_TITLE_CURVE = CubicBezier(0.8f, 0f, 0.8f, 0.15f)
}

/** A CSS-style cubic Bézier easing from (0, 0) to (1, 1), solved for x: the same in the unit tests and on a device. */
internal class CubicBezier(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) {
    /** The curve's y where its x is [x], 0 … 1. */
    fun at(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var low = 0f
        var high = 1f
        var t = x
        repeat(SOLVE_STEPS) {
            val current = coordinate(t, x1, x2)
            if (current < x) low = t else high = t
            t = (low + high) / 2f
        }
        return coordinate(t, y1, y2)
    }

    private fun coordinate(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
    }

    private companion object {
        const val SOLVE_STEPS = 30
    }
}

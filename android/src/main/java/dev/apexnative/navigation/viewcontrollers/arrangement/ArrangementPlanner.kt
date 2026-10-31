package dev.apexnative.navigation.viewcontrollers.arrangement

import android.graphics.Rect
import dev.apexnative.navigation.utils.WindowFolds
import dev.apexnative.navigation.viewcontrollers.arrangement.ArrangementDefinition.Compact
import dev.apexnative.navigation.viewcontrollers.arrangement.ArrangementDefinition.Edge
import dev.apexnative.navigation.viewcontrollers.arrangement.ArrangementDefinition.Style
import dev.apexnative.navigation.viewcontrollers.arrangement.ArrangementPlan.Layout
import kotlin.math.roundToInt

/**
 * How a pair lies now: the frames of its screens in the pair's own coordinates; no [secondary] while only the primary
 * shows.
 */
internal data class ArrangementPlan(val layout: Layout, val primary: Rect, val secondary: Rect?) {
    /** The names `ArrangementState.layout` has in JavaScript. */
    enum class Layout(val jsName: String) {
        SIDE_BY_SIDE("sideBySide"),
        STACKED("stacked"),
        OVERLAY("overlay"),
        SINGLE("single"),
    }
}

/** A separating fold that crosses the pair from one edge to the other, in the pair's coordinates. */
internal data class PairFold(val bounds: Rect, val vertical: Boolean, val halfOpened: Boolean, val occludes: Boolean)

/**
 * Where the fold crosses [area] (window pixels) from one edge to the other, in [area]'s coordinates; null for a fold
 * that doesn't divide the window, or that misses or only touches the area.
 */
internal fun WindowFolds.Fold.across(area: Rect): PairFold? {
    if (!separating) return null
    val local = Rect(bounds).apply { offset(-area.left, -area.top) }
    val width = area.width()
    val height = area.height()
    val crosses = if (vertical) {
        local.left > 0 && local.right < width && local.top <= 0 && local.bottom >= height
    } else {
        local.top > 0 && local.bottom < height && local.left <= 0 && local.right >= width
    }
    return if (crosses) PairFold(local, vertical, halfOpened, occludes) else null
}

/**
 * Decides the frames of a pair. A window too small for a pair (`largeWindow` false: Material's compact width or
 * height) shows the primary alone with `compact: 'primary'`. Otherwise, and with `compact: 'pair'`: `split` lies side
 * by side when wider than tall, else one above the other, the primary first (at the leading side or on top) or, with
 * `primaryEdge` trailing or bottom, second, with its `fraction`; a fold across the pair puts the boundary on it, and
 * the screens on its two sides, off its bounds.
 * `overlay`: both the whole size, the primary over the secondary; side by side at a fold that the device is partly
 * folded at, or that hides what is under it, the primary on `primaryEdge`. Where the axes don't allow the way the pair
 * would lie, only the primary shows (an overlay stays one over the other).
 */
internal object ArrangementPlanner {
    fun plan(
        definition: ArrangementDefinition,
        width: Int,
        height: Int,
        rtl: Boolean,
        fold: PairFold?,
        largeWindow: Boolean,
    ): ArrangementPlan {
        val whole = Rect(0, 0, width, height)
        if (definition.compact == Compact.PRIMARY && !largeWindow) return ArrangementPlan(Layout.SINGLE, whole, null)
        return when (definition.style) {
            Style.SPLIT -> {
                val sideBySide = fold?.vertical ?: (width > height)
                when {
                    !definition.allows(sideBySide) -> ArrangementPlan(Layout.SINGLE, whole, null)
                    fold != null ->
                        atFold(fold, width, height, primaryFirst(definition.primaryEdge, fold.vertical, rtl))
                    else -> byFraction(definition, width, height, sideBySide, rtl)
                }
            }
            Style.OVERLAY ->
                if (fold != null && (fold.halfOpened || fold.occludes) && definition.allows(fold.vertical)) {
                    atFold(fold, width, height, primaryFirst(definition.primaryEdge, fold.vertical, rtl))
                } else {
                    ArrangementPlan(Layout.OVERLAY, whole, Rect(whole))
                }
        }
    }

    // The two parts on the sides of the fold, the left or the top one first.
    private fun atFold(fold: PairFold, width: Int, height: Int, primaryFirst: Boolean): ArrangementPlan {
        val (layout, first, second) = if (fold.vertical) {
            Triple(Layout.SIDE_BY_SIDE, Rect(0, 0, fold.bounds.left, height), Rect(fold.bounds.right, 0, width, height))
        } else {
            Triple(Layout.STACKED, Rect(0, 0, width, fold.bounds.top), Rect(0, fold.bounds.bottom, width, height))
        }
        return if (primaryFirst) ArrangementPlan(layout, first, second) else ArrangementPlan(layout, second, first)
    }

    private fun byFraction(
        definition: ArrangementDefinition,
        width: Int,
        height: Int,
        sideBySide: Boolean,
        rtl: Boolean,
    ): ArrangementPlan {
        val first = primaryFirst(definition.primaryEdge, sideBySide, rtl)
        if (!sideBySide) {
            val share = (height * definition.fraction).roundToInt()
            val boundary = if (first) share else height - share
            val top = Rect(0, 0, width, boundary)
            val bottom = Rect(0, boundary, width, height)
            return ArrangementPlan(Layout.STACKED, if (first) top else bottom, if (first) bottom else top)
        }
        val share = (width * definition.fraction).roundToInt()
        // The primary's part on the left or on the right: the leading side is the right one from right to left.
        val boundary = if (first) share else width - share
        val left = Rect(0, 0, boundary, height)
        val right = Rect(boundary, 0, width, height)
        return ArrangementPlan(Layout.SIDE_BY_SIDE, if (first) left else right, if (first) right else left)
    }

    // Whether the primary takes the left part of a vertical fold (or of a pair side by side), or the top part of a
    // horizontal one (or of a pair one above the other). An edge of the other axis counts as the nearest one: leading
    // as top and top as leading, trailing as bottom and bottom as trailing.
    private fun primaryFirst(edge: Edge, vertical: Boolean, rtl: Boolean): Boolean {
        val first = edge == Edge.LEADING || edge == Edge.TOP
        return if (vertical) first != rtl else first
    }
}

package dev.apexnative.navigation.viewcontrollers.stack.fab

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where the FAB of a stack sits: at the bottom, 16 dp from the edges, on the side of `fab.alignHorizontally` (`right`,
 * the default, is the end of the line, `left` its start; mirrored right to left). From the bottom it clears what is
 * higher: the tab bar at rest (its height with its bottom padding; a compact one also the navigation bar) or the
 * navigation bar (the screen draws under it edge to edge, and an opaque one counts too) — plus 16 dp. At the sides it
 * clears the side insets of the navigation bar (three buttons in landscape) and of the cutout. A menu keeps 16 dp for
 * its shadows inside, so its margins are that much smaller and its main button sits where a FAB would. Pure arithmetic
 * in pixels.
 */
internal object FabPlacement {
    /** Insets at the four edges, in pixels. */
    data class Edges(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

    /**
     * The tab bar under the stack, at rest (a tab bar hidden by scroll or drawn behind still counts where it rests);
     * none without one, or with `bottomTabs.visible: false`.
     */
    data class Tabs(val restingHeight: Int, val compact: Boolean)

    /** The place: on the left edge or the right one, the margin from that edge and from the bottom, in pixels. */
    data class Place(val onLeft: Boolean, val sideMargin: Int, val bottomMargin: Int)

    /** The margin of the FAB from the edges and from what is under it, in dp. */
    const val MARGIN_DP = 16f

    fun place(
        navigationBar: Edges,
        cutout: Edges,
        tabs: Tabs?,
        density: Float,
        rtl: Boolean,
        alignStart: Boolean,
        shadowInset: Int = 0,
    ): Place {
        val margin = (MARGIN_DP * density).roundToInt()
        val onLeft = alignStart != rtl
        val side = if (onLeft) max(navigationBar.left, cutout.left) else max(navigationBar.right, cutout.right)
        val tabsHeight = tabs?.let { it.restingHeight + if (it.compact) navigationBar.bottom else 0 } ?: 0
        val bottom = max(tabsHeight, navigationBar.bottom)
        return Place(onLeft, margin + side - shadowInset, margin + bottom - shadowInset)
    }
}

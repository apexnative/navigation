package dev.apexnative.navigation.viewcontrollers.component

import dev.apexnative.navigation.react.ScreenEnvironment.Insets

/**
 * The arithmetic of a screen's room, safe area and background on Android, without views: the screen nodes read the
 * views and the options, and give the numbers here. Sizes are pixels unless a function says dp.
 */
internal object ScreenSafeArea {
    /** Four sides: a rectangle (its edges), or how much covers each edge of something. */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** One view on the way from the React root up to the window: its place in its parent, and its parent's scroll. */
    data class Step(val left: Int, val top: Int, val parentScrollX: Int, val parentScrollY: Int)

    const val TOP = "top"
    const val RIGHT = "right"
    const val BOTTOM = "bottom"
    const val LEFT = "left"

    private val ALL_EDGES = setOf(TOP, RIGHT, BOTTOM, LEFT)
    private val DEFAULT_EDGES = setOf(TOP, RIGHT, LEFT)
    private const val OPAQUE = 0xFF

    /**
     * Whether a screen measures its safe area now: its view shows, no layout is on its way to it or to a view around it
     * (it isn't in its place yet), and it is the top of its stack or has no safe area yet (two pushes in one frame).
     */
    fun measuresNow(shown: Boolean, layoutPending: Boolean, topOfStack: Boolean, hasSafeArea: Boolean): Boolean =
        shown && !layoutPending && (topOfStack || !hasSafeArea)

    /**
     * Where a view of [width] × [height] is in the window: the sum of the places up its [steps], each less the scroll
     * of its parent, without the translations of animations, so a screen coming in counts where it will rest.
     */
    fun placeInWindow(steps: List<Step>, width: Int, height: Int): Box {
        var x = 0
        var y = 0
        for (step in steps) {
            x += step.left - step.parentScrollX
            y += step.top - step.parentScrollY
        }
        return Box(x, y, x + width, y + height)
    }

    /**
     * How much of the React root at [root] (in the window) the bars and the system cover, in dp: at the top the larger
     * of the system's top ([system], status bar and cutout) and the bottom of the header ([headerBottom]); at the
     * bottom the smaller of the system's bottom and the top of the tabs ([tabsTop]); at the sides the system bars and
     * the cutout. The root is counted inside the screen view's own padding: what that padding keeps off (an opaque
     * navigation bar, the keyboard, side bars) isn't counted twice.
     */
    fun insets(
        windowWidth: Int,
        windowHeight: Int,
        system: Box,
        root: Box,
        headerBottom: Int?,
        tabsTop: Int?,
        density: Float,
    ): Insets {
        val coveredAbove = maxOf(system.top, headerBottom ?: 0)
        val coveredBelow = minOf(windowHeight - system.bottom, tabsTop ?: windowHeight)
        return Insets(
            top = dp((coveredAbove - root.top).coerceAtLeast(0), density),
            right = dp((root.right - (windowWidth - system.right)).coerceAtLeast(0), density),
            bottom = dp((root.bottom - coveredBelow).coerceAtLeast(0), density),
            left = dp((system.left - root.left).coerceAtLeast(0), density),
        )
    }

    /**
     * The edges the screen's safe area view keeps its content off: exactly [safeAreaEdges] when the options give them,
     * but the top under a large title ([largeTitle]); else the top and the sides, but the top when the stack's header
     * hides on scroll or a large title shows ([topFree]). The bottom is free by default: the content goes down to the
     * bottom, and a list keeps its last rows clear of the navigation bar with `useSafeAreaInsets()`.
     */
    fun paddedEdges(safeAreaEdges: List<String>?, topFree: Boolean, largeTitle: Boolean): Set<String> {
        if (safeAreaEdges != null) {
            val edges = safeAreaEdges.filterTo(LinkedHashSet()) { it in ALL_EDGES }
            // Under a large title the list keeps its first rows clear of the bar itself; a padded top would follow the
            // header as it collapses and move the list with it.
            if (largeTitle) edges -= TOP
            return edges
        }
        val edges = LinkedHashSet(DEFAULT_EDGES)
        if (topFree) edges -= TOP
        return edges
    }

    /** The padding of the safe area view: the [insets] on the [edges] it keeps, 0 on the others. */
    fun padding(insets: Insets, edges: Set<String>): Insets = Insets(
        top = if (TOP in edges) insets.top else 0.0,
        right = if (RIGHT in edges) insets.right else 0.0,
        bottom = if (BOTTOM in edges) insets.bottom else 0.0,
        left = if (LEFT in edges) insets.left else 0.0,
    )

    /**
     * The insets JavaScript gets (`useSafeAreaInsets`): what of the [insets] still covers the content once the safe
     * area view keeps it off its [padding], 0 on a padded edge.
     */
    fun uncovered(insets: Insets, padding: Insets) = Insets(
        top = (insets.top - padding.top).coerceAtLeast(0.0),
        right = (insets.right - padding.right).coerceAtLeast(0.0),
        bottom = (insets.bottom - padding.bottom).coerceAtLeast(0.0),
        left = (insets.left - padding.left).coerceAtLeast(0.0),
    )

    /**
     * The inner padding of a screen's view: the side system bars ([systemBars]) at the sides; at the bottom the larger
     * of the navigation bar (none when the screen draws under it) and the keyboard ([imeBottom]; none with
     * `layout.adjustResize: false`), less the room the screen keeps below it for the tabs ([bottomRoom]), never below
     * 0; nothing at the top, which the screen's room gives.
     */
    fun roomPadding(
        systemBars: Box,
        imeBottom: Int,
        drawsUnderNavigationBar: Boolean,
        adjustsForKeyboard: Boolean,
        bottomRoom: Int,
    ): Box {
        val navigationBar = if (drawsUnderNavigationBar) 0 else systemBars.bottom
        val keyboard = if (adjustsForKeyboard) imeBottom else 0
        val bottom = (maxOf(navigationBar, keyboard) - bottomRoom).coerceAtLeast(0)
        return Box(systemBars.left, 0, systemBars.right, bottom)
    }

    /**
     * Whether a screen draws under the navigation bar (`navigationBar.drawBehind`): as `navigationBar.drawBehind` says
     * ([drawBehind]); without it, unless the bar has a color of its own that is opaque ([barColor], ARGB). Without edge
     * to edge the window ends above the bar, and the bar's insets are 0 anyway.
     */
    fun drawsUnderNavigationBar(drawBehind: Boolean?, barColor: Int?): Boolean =
        drawBehind ?: (barColor == null || (barColor ushr 24) != OPAQUE)

    /**
     * The room for the status bar a screen keeps at its top: its height, but none when the status bar is hidden or the
     * screen is drawn under it ([hiddenOrDrawBehind]), in a sheet, in a side menu, or when a layout around keeps it
     * ([keptAround]).
     */
    fun statusBarRoom(
        statusBarHeight: Int,
        hiddenOrDrawBehind: Boolean,
        inSheet: Boolean,
        inSideMenu: Boolean,
        keptAround: Boolean,
    ): Int = if (hiddenOrDrawBehind || inSheet || inSideMenu || keptAround) 0 else statusBarHeight

    /**
     * How far from the top of the screen's view `layout.backgroundColor` starts: the color stays out of the status
     * bar's height, but goes up to the top when the screen is drawn under the status bar or it is hidden
     * ([hiddenOrDrawBehind]), for a screen of a side menu, in a sheet, of a pair (the pair keeps the room), and for a
     * screen with a room at its top already ([topRoom]: it is below the status bar anyway).
     */
    fun backgroundBand(
        statusBarHeight: Int,
        hiddenOrDrawBehind: Boolean,
        inSideMenu: Boolean,
        inSheet: Boolean,
        inPair: Boolean,
        topRoom: Int,
    ): Int = if (hiddenOrDrawBehind || inSideMenu || inSheet || inPair || topRoom > 0) 0 else statusBarHeight

    private fun dp(px: Int, density: Float): Double = if (density > 0) px / density.toDouble() else px.toDouble()
}

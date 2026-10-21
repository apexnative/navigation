package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.options.Options

/**
 * The room the bar takes, counted from a screen's own resolved options, never from where the bar is now (a large title
 * collapsed, a search open, the bar moved by a scroll): a screen not on top keeps its room through a push and a pop.
 * Pixels. A pure function.
 */
internal object TopBarRoom {
    /** What the window gives the bar: the status bar's height (shown or not) and the cutout's at the top. */
    class Insets(val statusBar: Int, val cutout: Int)

    /** The parts of the bar as [Options] set them, in pixels. */
    class Bar(
        /** The strip under the status bar, at the top of the bar ([strip]). */
        val strip: Int,
        /** The room over the bar ([margin]). */
        val margin: Int,
        /** `topBar.topMargin` alone, in pixels. */
        val topMargin: Int,
        /** `topBar.height`: the bar without the strip, the large title and the search. */
        val height: Int,
    )

    /**
     * A screen with [options] lies under the bar, the bar keeping no room over it: `topBar.drawBehind`,
     * `topBar.visible: false`, `topBar.hideOnScroll` or `topBar.searchBar.hideOnScroll`, or a large title shown
     * ([largeTitleShown]).
     */
    fun isUnderBar(options: Options, largeTitleShown: Boolean): Boolean {
        val topBar = options.topBar
        return topBar.drawBehind.isTrue || topBar.visible.isFalse || topBar.hideOnScroll.isTrue ||
            topBar.searchBar.hideOnScroll.isTrue || largeTitleShown
    }

    /**
     * The safe area of a screen with [options] leaves its top edge free: the bar or the search hides on scroll, or a
     * large title shows; the list pads itself then.
     */
    fun leavesTopEdgeFree(options: Options, largeTitleShown: Boolean): Boolean {
        val topBar = options.topBar
        if (topBar.visible.isFalse) return false
        return topBar.hideOnScroll.isTrue || topBar.searchBar.hideOnScroll.isTrue || largeTitleShown
    }

    /**
     * Whether a merge of [options] changes the room of the screen on top: the bar's visibility, `drawBehind`, the
     * hide on scroll of the bar or the search, the large title, the search, `topBar.height` or `topBar.topMargin`
     * (counted again at those merges).
     */
    fun isTouchedBy(options: Options): Boolean {
        val topBar = options.topBar
        return topBar.visible.hasValue() || topBar.drawBehind.hasValue() || topBar.hideOnScroll.hasValue() ||
            topBar.searchBar.hideOnScroll.hasValue() || topBar.largeTitle.hasValue() || topBar.height.hasValue() ||
            topBar.topMargin.hasValue() || topBar.searchBar.visible.hasValue() || topBar.searchBar.placement.hasValue()
    }

    /**
     * The strip of the bar under the status bar: the status bar's height, at least the cutout's, while it shows and
     * `statusBar.drawBehind` isn't `false`; with the status bar hidden only the cutout's (a merge of `drawBehind` adds
     * nothing then); none in a sheet, and none with `drawBehind: false`, the bar below the status bar.
     */
    fun strip(options: Options, insets: Insets, inSheet: Boolean): Int {
        if (inSheet) return 0
        val statusBar = options.statusBar
        if (statusBar.visible.isFalse) return insets.cutout
        if (statusBar.drawBehind.isFalse) return 0
        return maxOf(insets.statusBar, insets.cutout)
    }

    /**
     * The room over the bar: `topBar.topMargin` ([topMargin] pixels), and the status bar's height when the bar isn't
     * under it and it shows.
     */
    fun margin(options: Options, insets: Insets, inSheet: Boolean, topMargin: Int): Int {
        val belowStatusBar = !inSheet && !options.statusBar.visible.isFalse && options.statusBar.drawBehind.isFalse
        return topMargin + if (belowStatusBar) insets.statusBar else 0
    }

    /**
     * Whether the bar keeps the room of the status bar for a screen: the bar shows and is drawn under the status bar,
     * so the screen keeps none of its own (`ParentController.keepsStatusBarRoomOf`).
     */
    fun keepsStatusBarRoom(options: Options, bar: Bar): Boolean = !options.topBar.visible.isFalse && bar.strip > 0

    /**
     * The room the bar takes at the top of a screen, besides the layouts' around: none under the bar; else the strip,
     * `topBar.topMargin` and the height as the options set them (without the large title), and [searchHeight] for a
     * search under the row. The status bar's height over a bar below it is the screen's own room (it counts it itself
     * then).
     */
    fun topInset(underBar: Boolean, bar: Bar, searchHeight: Int): Int =
        if (underBar) 0 else bar.strip + bar.topMargin + bar.height + searchHeight
}

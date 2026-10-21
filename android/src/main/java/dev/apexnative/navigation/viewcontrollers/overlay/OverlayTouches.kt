package dev.apexnative.navigation.viewcontrollers.overlay

/**
 * Whether an overlay takes a touch, decided at its down, for the whole gesture, before React Native's root sees it.
 * With `overlay.interceptTouchOutside: true` the overlay takes every touch. Otherwise a touch on a screen's React
 * content goes to the overlay only when React Native's own touch target search (honoring `pointerEvents`: `box-none`
 * takes nothing) finds a view other than the content's root; a touch on the overlay's own native views (a bar) is the
 * overlay's; every other touch goes to the layers under it. Pure.
 */
internal object OverlayTouches {
    /** Where a touch landed in an overlay. */
    enum class Place {
        /** On a screen's React content: the target search decides. */
        Content,

        /** On a screen's view but outside its React content (what the system covers, kept as padding). */
        Screen,

        /** On the overlay's own native views: a bar, a native screen. */
        OwnView,

        /** Outside the overlay's views. */
        Outside,
    }

    /**
     * Whether the overlay takes the touch at [place]: [intercepts] (`interceptTouchOutside`); on the content, when the
     * target search found [target] other than [contentRoot] (the tag of the content's root view, which the search
     * answers with for the root alone and for nothing).
     */
    fun takes(intercepts: Boolean, place: Place, target: Int, contentRoot: Int): Boolean = when {
        intercepts -> true
        place == Place.Content -> target != contentRoot
        place == Place.OwnView -> true
        else -> false
    }

    /**
     * Whether a touch at [y] (in the window) on a screen lands on the bars of its layouts over its content: above the
     * bottom of its stack's header ([headerBottom]) or below the top of its tabs ([tabsTop]); none of them: no.
     */
    fun isOnBars(y: Float, headerBottom: Int?, tabsTop: Int?): Boolean =
        (headerBottom != null && y < headerBottom) || (tabsTop != null && y >= tabsTop)
}

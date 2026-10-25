package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPlacement

/**
 * The room the tab bar takes from the screens of its tabs: from the bar as its options set it, not as it lays out now,
 * so a screen gets its room in the same layout pass. Pure arithmetic in pixels.
 */
internal object TabsRoom {
    /**
     * The bar as its options set it: its own [height] (80 dp), the navigation bar under it ([navigationBar], the
     * window's bottom inset), `layoutStyle: 'compact'` ([compact]), `bottomMargin` ([bottomMargin]) and what the
     * custom row covers above it ([rowOvershoot]).
     */
    data class Bar(
        val height: Int,
        val navigationBar: Int,
        val compact: Boolean,
        val bottomMargin: Int,
        val rowOvershoot: Int = 0,
    ) {
        /**
         * The height of the bar's container: a stretched one goes under the navigation bar, its items above it; a
         * compact one floats above it.
         */
        val containerHeight: Int
            get() = height + if (compact) 0 else navigationBar

        /**
         * How far the container's bottom is from the bottom of the tabs: the margin, and the navigation bar of a
         * compact one.
         */
        val offset: Int
            get() = bottomMargin + if (compact) navigationBar else 0

        /** From the bottom of the tabs to the top of the bar at rest. */
        val reach: Int
            get() = containerHeight + offset
    }

    /** What the options of the screen asking say of the bar: `bottomTabs.visible`, `drawBehind`, `hideOnScroll`. */
    data class Screen(val hidden: Boolean, val drawBehind: Boolean, val hideOnScroll: Boolean)

    /**
     * The room a screen keeps at its bottom: the bar's reach and what the custom row covers above it, unless the screen
     * hides the bar, draws behind it, or the bar hides on scroll (the screen goes under it then); with the room of the
     * layouts around the tabs ([around]).
     */
    fun bottomInset(bar: Bar, screen: Screen, around: Int): Int {
        if (screen.hidden || screen.drawBehind || screen.hideOnScroll) return around
        return around + bar.reach + bar.rowOvershoot
    }

    /**
     * The top of the bar in the window where it rests, from the bottom of the tabs in the window ([tabsBottom]); none
     * when the screen's options hide the bar.
     */
    fun tabsTop(bar: Bar, screen: Screen, tabsBottom: Int): Int? =
        if (screen.hidden) null else tabsBottom - bar.reach - bar.rowOvershoot

    /**
     * The bar at rest under a stack's FAB: also when the screen is under it (`drawBehind`, `hideOnScroll`); none when
     * the screen hides it.
     */
    fun fabTabs(bar: Bar, screen: Screen): FabPlacement.Tabs? =
        if (screen.hidden) null else FabPlacement.Tabs(bar.containerHeight + bar.bottomMargin, bar.compact)
}

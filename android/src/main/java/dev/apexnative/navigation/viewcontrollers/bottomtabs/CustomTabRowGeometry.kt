package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.customrow.BottomTabsCustomRowOptions

/**
 * Where the custom row of the tabs (`bottomTabs.customRow`) lies over the bar on Android: its left and right edges are
 * the bar's less `horizontalMargin`, its bottom the bar's bottom (above the navigation bar, never the window's), its
 * height the content and `bottomMargin` below it; the content is the bar's height and 18 dp, or exactly
 * `customRow.height`, higher than the bar too: it grows up from the bar's bottom, and the screens get what it covers
 * above the bar as room. Pure arithmetic in pixels.
 */
internal object CustomTabRowGeometry {
    /** The row's frame in its parent's coordinates, and the height of its content. */
    data class Place(val left: Int, val top: Int, val right: Int, val bottom: Int, val content: Int)

    /** The extra height of the row's content over the bar's by default, dp. */
    const val EXTRA_HEIGHT_DP = 18f

    /** `customRow.horizontalMargin` by default, dp. */
    const val HORIZONTAL_MARGIN_DP = 16f

    /** `customRow.cornerRadius` by default, dp. */
    const val CORNER_RADIUS_DP = 28f

    /** The row's light shadow: its elevation, dp. */
    const val ELEVATION_DP = 3f

    // The theme's background under the row without a color: nearly opaque (no blur of what is under).
    private const val THEME_ALPHA = 0xF0
    private const val RGB = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24

    /**
     * The content's height: [height] (`customRow.height`) when set, else the bar's height ([barHeight]) and
     * [extra] (18 dp).
     */
    fun content(barHeight: Int, height: Int?, extra: Int): Int = height ?: (barHeight + extra)

    /**
     * The row over the bar whose edges are [barLeft], [barRight] and [barBottom], with [margin]
     * (`horizontalMargin`), the content's height [content] and [bottomMargin], which lifts it once.
     */
    fun place(barLeft: Int, barRight: Int, barBottom: Int, margin: Int, content: Int, bottomMargin: Int): Place =
        Place(
            left = barLeft + margin,
            top = barBottom - content - bottomMargin,
            right = barRight - margin,
            bottom = barBottom,
            content = content,
        )

    /**
     * The row over the bar as the tabs' view lays it out: the bar's [barLeft], [barRight] and [barBottom] in that
     * view, whose height is [tabsHeight] and whose bottom [navigationBar] pixels lie under the navigation bar — the
     * row's bottom never goes over them —, with [margin], [content] and [bottomMargin] as [place] takes them.
     */
    fun inTabs(
        barLeft: Int,
        barRight: Int,
        barBottom: Int,
        tabsHeight: Int,
        navigationBar: Int,
        margin: Int,
        content: Int,
        bottomMargin: Int,
    ): Place = place(barLeft, barRight, minOf(barBottom, tabsHeight - navigationBar), margin, content, bottomMargin)

    /**
     * The part of the row's frame at [place] below its visible part, in pixels: `bottomMargin`. The row draws and takes
     * touches only above it.
     */
    fun gap(place: Place): Int = (place.bottom - place.top - place.content).coerceAtLeast(0)

    /**
     * Whether the row is put at [next] again: only when it changed from where it was put last ([last]), so a layout
     * pass that changed nothing changes nothing and UI tests reach idle.
     */
    fun needsPlacing(last: Place?, next: Place): Boolean = last != next

    /**
     * The row's own lift over its [elevation] while the bar's container stands at [barZ]: as high as the container, so
     * the row, after it in the tabs' view, draws over it and takes the touches first even under a raised bar.
     */
    fun lift(barZ: Float, elevation: Float): Float = (barZ - elevation).coerceAtLeast(0f)

    /**
     * The row's background: `customRow.backgroundColor` ([color]; `null` and `'NoColor'` are none); without it
     * `backgroundEffect: 'none'` ([effect]) is clear, any other effect or none the theme's `android:colorBackground`
     * ([themeBackground]) with alpha 0xF0.
     */
    fun background(color: Int?, effect: BottomTabsCustomRowOptions.BackgroundEffect?, themeBackground: Int): Int {
        if (color != null) return color
        if (effect == BottomTabsCustomRowOptions.BackgroundEffect.NONE) return 0
        return (themeBackground and RGB) or (THEME_ALPHA shl ALPHA_SHIFT)
    }

    /**
     * What the row covers above the bar ([barHeight]), which the screens keep free at their bottom: from the options,
     * not from where the row lays out, so the screens get it in the same pass.
     */
    fun overshoot(barHeight: Int, content: Int, bottomMargin: Int): Int =
        (content + bottomMargin - barHeight).coerceAtLeast(0)
}

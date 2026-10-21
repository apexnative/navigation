package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.options.BottomTabsOptions
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.views.bottomtabs.BottomTabsContainer

/**
 * What the container of the tab bar draws from `bottomTabs`: the background (`null` and `'NoColor'` the theme's), a top
 * line shown only when `borderColor` or `borderWidth` is set (dark gray, the thin line, the width in dp), the shadow
 * only with `shadow` (black by default, its alpha the opacity), the elevation and the corners in dp. The values alone,
 * without views.
 */
internal object TabBarLook {
    /** The line without `borderWidth`: one pixel. */
    const val THIN_LINE_PX = 1f

    private const val DARK_GRAY = 0xFF444444.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val OPAQUE = 255
    private const val RGB = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24

    /**
     * The look of [options], their colors read by [color] (none for unset and `'NoColor'`), the theme's background
     * [defaultBackground], sizes at [densityDpi].
     */
    fun of(
        options: BottomTabsOptions,
        color: (ThemeColour) -> Int?,
        defaultBackground: Int,
        densityDpi: Int,
    ): BottomTabsContainer.Look {
        fun px(dp: Double): Float = UiUtils.dpToPixels(dp, densityDpi).toFloat()
        val border = options.borderColor.hasValue() || options.borderWidth.hasValue()
        val shadow = options.shadow
        return BottomTabsContainer.Look(
            background = color(options.backgroundColor) ?: defaultBackground,
            borderColor = if (border) color(options.borderColor) ?: DARK_GRAY else null,
            borderWidth = options.borderWidth.get(null)?.let(::px) ?: THIN_LINE_PX,
            shadow = if (shadow.hasValue()) {
                BottomTabsContainer.Shadow(
                    color = withOpacity(color(shadow.color) ?: BLACK, shadow.opacity.get(null)),
                    radius = shadow.radius.get(0.0).toFloat(),
                )
            } else {
                null
            },
            elevation = px(options.elevation.get(0.0)),
            cornerRadius = px(options.cornerRadius.get(0.0)),
        )
    }

    /** [color] with the alpha of [opacity] (0 … 1); as it is without one. */
    fun withOpacity(color: Int, opacity: Double?): Int {
        opacity ?: return color
        val alpha = (opacity.coerceIn(0.0, 1.0) * OPAQUE).toInt()
        return (color and RGB) or (alpha shl ALPHA_SHIFT)
    }

    /**
     * Whether every tab draws its cell (`bottomTab.component` of [components] of [count] tabs); with some but not all,
     * [mixedWarning] says so and every tab is drawn natively.
     */
    fun usesRow(components: Int, count: Int): Boolean = count > 0 && components == count

    /** The warning when only some tabs have a component; none otherwise. */
    fun mixedWarning(components: Int, count: Int): String? = if (components in 1 until count) {
        "Mixed bottomTab.component usage detected ($components of $count tabs). " +
            "All tabs must declare a component or none — falling back to native rendering for all tabs."
    } else {
        null
    }
}

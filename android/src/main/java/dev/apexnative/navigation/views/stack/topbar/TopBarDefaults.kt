package dev.apexnative.navigation.views.stack.topbar

import android.content.Context
import android.graphics.Color
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import dev.apexnative.navigation.utils.MaterialTheme

/**
 * The top bar's colors when the options don't set them: Material 3's surface, onSurface and onSurfaceVariant of the
 * app's theme, or of the navigation's neutral one when the app's isn't Material 3 ([MaterialTheme]), so they follow
 * light and dark mode.
 */
object TopBarDefaults {
    @JvmStatic
    fun backgroundColor(context: Context): Int =
        MaterialColors.getColor(MaterialTheme.context(context), MaterialR.attr.colorSurface, Color.WHITE)

    @JvmStatic
    fun titleColor(context: Context): Int =
        MaterialColors.getColor(MaterialTheme.context(context), MaterialR.attr.colorOnSurface, Color.BLACK)

    @JvmStatic
    fun subtitleColor(context: Context): Int =
        MaterialColors.getColor(MaterialTheme.context(context), MaterialR.attr.colorOnSurfaceVariant, Color.GRAY)

    /** The back arrow's. */
    @JvmStatic
    fun navigationIconColor(context: Context): Int =
        MaterialColors.getColor(MaterialTheme.context(context), MaterialR.attr.colorOnSurface, Color.BLACK)

    @JvmStatic
    fun overflowIconColor(context: Context): Int =
        MaterialColors.getColor(MaterialTheme.context(context), MaterialR.attr.colorOnSurfaceVariant, Color.BLACK)
}

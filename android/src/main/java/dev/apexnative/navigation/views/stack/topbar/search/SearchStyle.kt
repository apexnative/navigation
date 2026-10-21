package dev.apexnative.navigation.views.stack.topbar.search

import android.content.Context
import android.graphics.Color
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import dev.apexnative.navigation.utils.MaterialTheme

/** The look of the top bar's search: Material 3 colors from the app's theme, light or dark with it. */
object SearchStyle {
    /** The height of the row of the search in the bar, and of the pill in it. */
    const val ROW_HEIGHT_DP = 72
    const val PILL_HEIGHT_DP = 56

    /** Material 3 components need a Material 3 theme: the app's, or the navigation's neutral one when it has another. */
    @JvmStatic
    fun materialContext(context: Context): Context = MaterialTheme.context(context)

    fun pillColor(context: Context): Int =
        MaterialColors.getColor(context, MaterialR.attr.colorSurfaceContainerHigh, Color.rgb(0xEC, 0xE6, 0xF0))

    fun textColor(context: Context): Int = MaterialColors.getColor(context, MaterialR.attr.colorOnSurface, Color.BLACK)

    fun hintColor(context: Context): Int =
        MaterialColors.getColor(context, MaterialR.attr.colorOnSurfaceVariant, Color.GRAY)

    /** Over the screen while the search is open and empty (obscuresBackgroundDuringPresentation). */
    fun scrimColor(): Int = Color.argb(0x52, 0, 0, 0)
}

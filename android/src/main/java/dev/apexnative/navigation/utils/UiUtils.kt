package dev.apexnative.navigation.utils

import android.content.Context
import android.util.TypedValue
import kotlin.math.roundToInt

/** Sizes on the screen: dp and sp of the options into pixels of the views. */
object UiUtils {
    private const val BASE_DENSITY_DPI = 160

    /** [dp] in pixels on the screen of [context]. */
    @JvmStatic
    fun dpToPx(context: Context, dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)

    /** [dp] in whole pixels on the screen of [context]. */
    @JvmStatic
    fun dpToPx(context: Context, dp: Int): Int = dpToPx(context, dp.toFloat()).roundToInt()

    /** [px] pixels in dp on the screen of [context]. */
    @JvmStatic
    fun pxToDp(context: Context, px: Float): Float = px / context.resources.displayMetrics.density

    /**
     * A size of the options in dp, in whole pixels of the screen of [context]: times `densityDpi / 160`, the fraction
     * of a pixel dropped; a size of 0 or less is not converted.
     */
    @JvmStatic
    fun dpToPixels(context: Context, dp: Double): Int = dpToPixels(dp, context.resources.displayMetrics.densityDpi)

    /** [dp] in whole pixels at [densityDpi], as [dpToPixels] converts it. */
    @JvmStatic
    fun dpToPixels(dp: Double, densityDpi: Int): Int =
        if (dp <= 0) dp.toInt() else (dp * densityDpi / BASE_DENSITY_DPI).toInt()

    /**
     * A font size of the options in sp, in pixels of the screen of [context]: it follows the system's text scale,
     * non-linearly where the system scales so.
     */
    @JvmStatic
    fun spToPixels(context: Context, sp: Double): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), context.resources.displayMetrics)
}

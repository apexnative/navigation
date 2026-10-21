package dev.apexnative.navigation.utils

/** What the navigation decides from a color itself. */
object ColorUtils {
    private const val RED_WEIGHT = 0.299
    private const val GREEN_WEIGHT = 0.587
    private const val BLUE_WEIGHT = 0.114
    private const val CHANNEL_MAX = 255.0
    private const val CHANNEL_MASK = 0xFF

    /**
     * The ARGB [color] is light: its luminance `0.299 R + 0.587 G + 0.114 B`, the channels from 0 to 1, is above one
     * half. The system bars take dark icons over a light color.
     */
    @JvmStatic
    fun isLight(color: Int): Boolean {
        val red = (color shr 16) and CHANNEL_MASK
        val green = (color shr 8) and CHANNEL_MASK
        val blue = color and CHANNEL_MASK
        return (RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue) / CHANNEL_MAX > 0.5
    }
}

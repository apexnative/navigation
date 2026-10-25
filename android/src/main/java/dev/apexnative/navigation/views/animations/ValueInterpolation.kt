package dev.apexnative.navigation.views.animations

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The values between two ends: value = from + f·(to − from) per component, with f the curve's value; at the start of
 * the time exactly `from`, at its end exactly `to`, whatever the curve gave. Numbers (places, sizes, radii, each
 * component apart), and colors in the perceptual Lab space (D65) with the alpha linear apart. Pure: the colors are
 * converted here, by the formulas of AndroidX's `ColorUtils` (sRGB, D65 white 95.047 / 100 / 108.883).
 */
internal object ValueInterpolation {
    /** A number at [fraction] of the way (the curve's value), [time] the part of the time: its end is exactly [to]. */
    fun number(from: Float, to: Float, fraction: Float, time: Float = fraction): Float = when {
        time <= 0f -> from
        time >= 1f -> to
        else -> from + fraction * (to - from)
    }

    /**
     * An angle in degrees at [fraction] of the way along the shorter arc, as iOS turns a shared element: 0 → 270 turns
     * back by 90, 170 → −170 turns on by 20; half a turn goes on. At the end of the time exactly [to].
     */
    fun angle(from: Float, to: Float, fraction: Float, time: Float = fraction): Float {
        if (time <= 0f) return from
        if (time >= 1f) return to
        val forward = ((to - from) % FULL_TURN + FULL_TURN) % FULL_TURN
        val turn = if (forward > HALF_TURN) forward - FULL_TURN else forward
        return from + fraction * turn
    }

    /**
     * A color (an sRGB color int, as React Native keeps a background; none is transparent) at [fraction] of the way
     * from [from] to [to]: L, a and b in Lab (D65), the alpha linear, the result rounded; at the end of the time
     * exactly [to].
     */
    fun color(from: Int?, to: Int?, fraction: Float, time: Float = fraction): Int {
        val start = from ?: TRANSPARENT
        val end = to ?: TRANSPARENT
        if (time <= 0f) return start
        if (time >= 1f) return end
        val a = toLab(start)
        val b = toLab(end)
        val f = fraction.toDouble()
        val rgb = fromLab(a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1]), a[2] + f * (b[2] - a[2]))
        val alpha = (alphaOf(start) + f * (alphaOf(end) - alphaOf(start))).roundToInt().coerceIn(0, MAX)
        return (alpha shl ALPHA_SHIFT) or (rgb and RGB_MASK)
    }

    /** L, a, b of an sRGB color int (its alpha aside). */
    fun toLab(color: Int): DoubleArray {
        val r = linear(channel(color, RED_SHIFT))
        val g = linear(channel(color, GREEN_SHIFT))
        val b = linear(channel(color, 0))
        val x = pivot((r * 0.4124 + g * 0.3576 + b * 0.1805) * 100 / WHITE_X)
        val y = pivot((r * 0.2126 + g * 0.7152 + b * 0.0722) * 100 / WHITE_Y)
        val z = pivot((r * 0.0193 + g * 0.1192 + b * 0.9505) * 100 / WHITE_Z)
        return doubleArrayOf(maxOf(0.0, 116 * y - 16), 500 * (x - y), 200 * (y - z))
    }

    /** The opaque sRGB color int of L, a, b, rounded per channel. */
    fun fromLab(l: Double, a: Double, b: Double): Int {
        val fy = (l + 16) / 116
        val fx = a / 500 + fy
        val fz = fy - b / 200
        val xr = fx.pow(3).let { if (it > EPSILON) it else (116 * fx - 16) / KAPPA }
        val yr = if (l > KAPPA * EPSILON) fy.pow(3) else l / KAPPA
        val zr = fz.pow(3).let { if (it > EPSILON) it else (116 * fz - 16) / KAPPA }
        val x = xr * WHITE_X / 100
        val y = yr * WHITE_Y / 100
        val z = zr * WHITE_Z / 100
        val red = encoded(x * 3.2406 + y * -1.5372 + z * -0.4986)
        val green = encoded(x * -0.9689 + y * 1.8758 + z * 0.0415)
        val blue = encoded(x * 0.0557 + y * -0.2040 + z * 1.0570)
        return (MAX shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    private fun channel(color: Int, shift: Int): Double = ((color ushr shift) and MAX) / MAX.toDouble()

    private fun alphaOf(color: Int): Int = (color ushr ALPHA_SHIFT) and MAX

    // sRGB's transfer: from the encoded value to the linear light, and back to a rounded channel.
    private fun linear(value: Double): Double =
        if (value < 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun encoded(value: Double): Int {
        val v = if (value > 0.0031308) 1.055 * value.pow(1 / 2.4) - 0.055 else 12.92 * value
        return (v * MAX).roundToInt().coerceIn(0, MAX)
    }

    private fun pivot(value: Double): Double = if (value > EPSILON) cbrt(value) else (KAPPA * value + 16) / 116

    private const val TRANSPARENT = 0
    private const val MAX = 255
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val RGB_MASK = 0x00FFFFFF
    private const val WHITE_X = 95.047
    private const val WHITE_Y = 100.0
    private const val WHITE_Z = 108.883
    private const val EPSILON = 0.008856
    private const val KAPPA = 903.3
    private const val FULL_TURN = 360f
    private const val HALF_TURN = 180f
}

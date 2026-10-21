package dev.apexnative.navigation.views.animations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The values between two ends: the last frame is exactly the target whatever the curve gave; colors in Lab, the alpha
 * apart, ending exactly on `to`.
 */
class ValuesTest {
    private val overshoot = Curves.Overshoot(1.0)

    @Test
    fun theLastFrameIsExactlyTheTarget() {
        // Past the target on the way (overshoot), exactly the target at the end of the time.
        assertTrue(ValueInterpolation.number(0f, 100f, overshoot.getInterpolation(0.75f), 0.75f) > 100f)
        assertEquals(100f, ValueInterpolation.number(0f, 100f, overshoot.getInterpolation(1f), 1f), 0f)
        val spring = Curves.of(null, 300L)
        assertEquals(1.0, spring.value(1.0), 0.0)
        assertEquals(7f, ValueInterpolation.number(3f, 7f, 1.2f, time = 1f), 0f)
        assertEquals(3f, ValueInterpolation.number(3f, 7f, -0.2f, time = 0f), 0f)
        assertEquals(5f, ValueInterpolation.number(3f, 7f, 0.5f), 0f)
    }

    @Test
    fun colorsGoThroughLabAndEndExactly() {
        val red = 0xFFFF0000.toInt()
        val blue = 0x800000FF.toInt()
        assertEquals(red, ValueInterpolation.color(red, blue, 0f))
        assertEquals(blue, ValueInterpolation.color(red, blue, 1f))
        // Rounded after Lab on the way, exactly `to` at the end of the time whatever the curve gave.
        assertEquals(blue, ValueInterpolation.color(red, blue, 0.999f, time = 1f))
        val middle = ValueInterpolation.color(red, blue, 0.5f)
        // The alpha goes linearly by itself: 255 → 128 is 192 (191.5 rounded).
        assertEquals(192, middle ushr 24)
        // In Lab the middle of red and blue is a purple brighter than the sRGB average's blue part.
        assertTrue((middle shr 16 and 0xFF) > 100)
        assertTrue((middle and 0xFF) > 100)
        // A missing color is transparent.
        assertEquals(0, ValueInterpolation.color(null, red, 0f))
        assertEquals(255, ValueInterpolation.color(null, red, 1f) ushr 24)
    }

    // D65 sRGB ↔ Lab, as AndroidX's ColorUtils: a round trip gives every channel back.
    @Test
    fun labRoundTrips() {
        for (color in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF336699.toInt(), 0xFFFF8800.toInt(),
            0xFF00FF00.toInt(), 0xFF808080.toInt())) {
            val lab = ValueInterpolation.toLab(color)
            val back = ValueInterpolation.fromLab(lab[0], lab[1], lab[2])
            for (shift in listOf(16, 8, 0)) {
                val a = color shr shift and 0xFF
                val b = back shr shift and 0xFF
                assertTrue("${Integer.toHexString(color)} → ${Integer.toHexString(back)}", abs(a - b) <= 1)
            }
        }
        // White is L = 100, a = b = 0; black is L = 0.
        val white = ValueInterpolation.toLab(0xFFFFFFFF.toInt())
        assertEquals(100.0, white[0], 0.05)
        assertEquals(0.0, white[1], 0.05)
        assertEquals(0.0, white[2], 0.05)
        assertEquals(0.0, ValueInterpolation.toLab(0xFF000000.toInt())[0], 1e-9)
    }
}

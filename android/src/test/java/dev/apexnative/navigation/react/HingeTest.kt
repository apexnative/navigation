package dev.apexnative.navigation.react

import android.graphics.Rect
import dev.apexnative.navigation.utils.WindowFolds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hinge's value from the window's fold and the sensor's angle, as `ANN.HingeChanged` and `getHinge` give it: the
 * states, `closed` without a fold or under 90°, the angle to a tenth.
 */
class HingeTest {
    private fun fold(halfOpened: Boolean) = WindowFolds.Fold(
        bounds = Rect(),
        vertical = true,
        separating = halfOpened,
        halfOpened = halfOpened,
        occludes = false,
    )

    @Test
    fun noFoldAndNoSensorIsNoHinge() {
        assertNull(Hinge.valueOf(null, hasSensor = false, angle = null))
    }

    @Test
    fun aWindowWithoutAFoldIsClosedWithOrWithoutAnAngle() {
        assertEquals(Hinge.Value("closed", null), Hinge.valueOf(null, hasSensor = true, angle = null))
        assertEquals(Hinge.Value("closed", 170.0), Hinge.valueOf(null, hasSensor = true, angle = 170f))
        assertEquals(Hinge.Value("closed", 20.0), Hinge.valueOf(null, hasSensor = true, angle = 20f))
    }

    @Test
    fun anAngleUnder90IsClosedThoughTheWindowHasAFold() {
        assertEquals(Hinge.Value("closed", 60.0), Hinge.valueOf(fold(halfOpened = true), hasSensor = true, angle = 60f))
    }

    @Test
    fun theFoldTellsTheOpenStates() {
        assertEquals(
            Hinge.Value("partiallyOpen", 120.0),
            Hinge.valueOf(fold(halfOpened = true), hasSensor = true, angle = 120f),
        )
        assertEquals(Hinge.Value("fullyOpen", null), Hinge.valueOf(fold(halfOpened = false), hasSensor = false, null))
    }

    @Test
    fun theAngleGoesToATenth() {
        assertEquals(179.9, Hinge.valueOf(fold(halfOpened = false), hasSensor = true, angle = 179.9f)?.angle)
        assertEquals(135.3, Hinge.valueOf(fold(halfOpened = true), hasSensor = true, angle = 135.26f)?.angle)
        // The sensor's noise under a tenth is the same value: it isn't sent again.
        assertEquals(
            Hinge.valueOf(fold(halfOpened = true), hasSensor = true, angle = 135.31f),
            Hinge.valueOf(fold(halfOpened = true), hasSensor = true, angle = 135.29f),
        )
    }
}

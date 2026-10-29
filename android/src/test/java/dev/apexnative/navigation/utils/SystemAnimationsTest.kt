package dev.apexnative.navigation.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether the system's animations are off: the animator's answer from Android 8, the duration scale below it. */
class SystemAnimationsTest {
    private fun unexpected(): Nothing = throw AssertionError("not asked on this version")

    @Test
    fun fromAndroid8TheAnimatorAnswers() {
        assertTrue(SystemAnimations.areOff(26, { false }) { unexpected() })
        assertFalse(SystemAnimations.areOff(36, { true }) { unexpected() })
    }

    @Test
    fun belowAndroid8TheDurationScaleAtZero() {
        assertTrue(SystemAnimations.areOff(24, { unexpected() }) { 0f })
        assertFalse(SystemAnimations.areOff(25, { unexpected() }) { 0.5f })
        assertFalse(SystemAnimations.areOff(25, { unexpected() }) { 1f })
    }

    @Test
    fun withoutTheApplicationTheyPlay() {
        assertFalse(SystemAnimations.areOff())
    }
}

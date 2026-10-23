package dev.apexnative.navigation.utils

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared cache of option images lets its images go when the system is low on memory, not as the UI hides. */
class OptionImagesTest {
    @Test
    fun theUiHiddenKeepsTheImages() {
        assertFalse(OptionImages.clearsOnTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
    }

    @Test
    fun lowMemoryInTheBackgroundLetsThemGo() {
        assertTrue(OptionImages.clearsOnTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        // TRIM_MEMORY_COMPLETE, which Android 14 no longer sends.
        assertTrue(OptionImages.clearsOnTrim(80))
    }
}

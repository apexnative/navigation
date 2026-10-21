package dev.apexnative.navigation.system

import dev.apexnative.navigation.utils.SystemUiUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** The status bar's height. */
class StatusBarHeightTest {
    @Test
    fun theWindowsInsetFirst() {
        assertEquals(0, SystemUiUtils.statusBarHeight(inset = 0, systemDimension = 63, density = 2.625f))
        assertEquals(110, SystemUiUtils.statusBarHeight(inset = 110, systemDimension = 63, density = 2.625f))
    }

    @Test
    fun beforeTheInsetsTheSystemsDimension() {
        assertEquals(63, SystemUiUtils.statusBarHeight(inset = null, systemDimension = 63, density = 2.625f))
    }

    @Test
    fun withoutEither24Dp() {
        assertEquals(63, SystemUiUtils.statusBarHeight(inset = null, systemDimension = 0, density = 2.625f))
        assertEquals(24, SystemUiUtils.statusBarHeight(inset = null, systemDimension = 0, density = 1f))
    }
}

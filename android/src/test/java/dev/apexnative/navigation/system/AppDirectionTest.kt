package dev.apexnative.navigation.system

import android.view.View
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's direction on Android. */
class AppDirectionTest {
    private fun direction(value: String?) = Options().apply { value?.let { layout.direction = Text(it) } }

    @Test
    fun rightToLeftNowByTheValue() {
        assertFalse(AppDirection.rtlNow("ltr", localeIsRtl = true))
        assertTrue(AppDirection.rtlNow("rtl", localeIsRtl = false))
        assertTrue(AppDirection.rtlNow("locale", localeIsRtl = true))
        assertFalse(AppDirection.rtlNow("locale", localeIsRtl = false))
    }

    @Test
    fun theViewsDirectionOfEachValue() {
        assertEquals(View.LAYOUT_DIRECTION_LTR, AppDirection.viewDirectionOf("ltr"))
        assertEquals(View.LAYOUT_DIRECTION_RTL, AppDirection.viewDirectionOf("rtl"))
        assertEquals(View.LAYOUT_DIRECTION_LOCALE, AppDirection.viewDirectionOf("locale"))
        assertNull(AppDirection.viewDirectionOf("sideways"))
    }

    @Test
    fun aDrawableTakesTheViewsDirectionResolved() {
        val ltr = View.LAYOUT_DIRECTION_LTR
        val rtl = View.LAYOUT_DIRECTION_RTL
        val locale = View.LAYOUT_DIRECTION_LOCALE
        assertEquals(rtl, AppDirection.resolvedViewDirection(rtl, localeIsRtl = false, rtlSupported = true))
        assertEquals(ltr, AppDirection.resolvedViewDirection(ltr, localeIsRtl = true, rtlSupported = true))
        // `locale` is the device's language, never the raw value, which a drawable doesn't take for right to left.
        assertEquals(rtl, AppDirection.resolvedViewDirection(locale, localeIsRtl = true, rtlSupported = true))
        assertEquals(ltr, AppDirection.resolvedViewDirection(locale, localeIsRtl = false, rtlSupported = true))
        // Without `supportsRtl` every view lays out left to right.
        assertEquals(ltr, AppDirection.resolvedViewDirection(rtl, localeIsRtl = true, rtlSupported = false))
        assertEquals(ltr, AppDirection.resolvedViewDirection(View.LAYOUT_DIRECTION_INHERIT, true, true))
    }

    @Test
    fun theRootsOwnOverTheDefaults() {
        assertEquals("rtl", AppDirection.ofRoot(direction("rtl"), direction("ltr")))
        assertEquals("ltr", AppDirection.ofRoot(direction(null), direction("ltr")))
        assertNull(AppDirection.ofRoot(direction(null), direction(null)))
    }

    @Test
    fun newDefaultsActOnlyWhenTheirDirectionChanged() {
        val defaults = AppDirection.Defaults()
        assertEquals("ltr", defaults.changed("ltr"))
        // A root's own `rtl` was applied meanwhile; the same defaults again change nothing.
        assertNull(defaults.changed("ltr"))
        assertEquals("rtl", defaults.changed("rtl"))
        // No key: nothing to apply, the window stays as it is.
        assertNull(defaults.changed(null))
        assertEquals("rtl", defaults.changed("rtl"))
    }
}

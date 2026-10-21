package dev.apexnative.navigation.react

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "R R" of the developer tools: two releases of R within a second, not in a text field. */
class DoubleTapReloadTest {
    private val reload = DoubleTapReload()

    @Test
    fun twoReleasesWithinASecondReload() {
        assertFalse(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 0))
        assertTrue(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 999))
    }

    @Test
    fun aSecondApartIsTooLate() {
        assertFalse(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 0))
        assertFalse(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 1000))
        assertTrue(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 1500))
    }

    @Test
    fun aReloadStartsCountingAnew() {
        reload.onKeyUp(KeyEvent.KEYCODE_R, false, 0)
        assertTrue(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 100))
        assertFalse(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 200))
    }

    @Test
    fun typingInATextFieldAndOtherKeysDontCount() {
        assertFalse(reload.onKeyUp(KeyEvent.KEYCODE_R, true, 0))
        assertFalse(reload.onKeyUp(KeyEvent.KEYCODE_R, true, 100))
        assertFalse(reload.onKeyUp(KeyEvent.KEYCODE_A, false, 150))
        assertFalse(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 200))
        assertTrue(reload.onKeyUp(KeyEvent.KEYCODE_R, false, 300))
    }
}

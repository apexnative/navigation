package dev.apexnative.navigation.options

import dev.apexnative.navigation.options.params.Bool
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a screen keeps the room of the status bar: hidden, drawn under it, and the default of an overlay. */
class StatusBarOptionsTest {
    private fun statusBar(visible: Boolean? = null, drawBehind: Boolean? = null) = StatusBarOptions().apply {
        if (visible != null) this.visible = Bool(visible)
        if (drawBehind != null) this.drawBehind = Bool(drawBehind)
    }

    @Test
    fun aScreenKeepsTheRoomUnlessHiddenOrDrawnUnder() {
        assertFalse(statusBar().isHiddenOrDrawBehind(inOverlay = false))
        assertTrue(statusBar(visible = false).isHiddenOrDrawBehind(inOverlay = false))
        assertTrue(statusBar(drawBehind = true).isHiddenOrDrawBehind(inOverlay = false))
        assertFalse(statusBar(drawBehind = false).isHiddenOrDrawBehind(inOverlay = false))
    }

    @Test
    fun anOverlayIsDrawnUnderTheStatusBarUnlessDrawBehindIsFalse() {
        assertTrue(statusBar().isHiddenOrDrawBehind(inOverlay = true))
        assertTrue(statusBar(drawBehind = true).isHiddenOrDrawBehind(inOverlay = true))
        assertFalse(statusBar(drawBehind = false).isHiddenOrDrawBehind(inOverlay = true))
        assertTrue(statusBar(visible = false, drawBehind = false).isHiddenOrDrawBehind(inOverlay = true))
    }
}

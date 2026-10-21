package dev.apexnative.navigation.viewcontrollers.component

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The readiness of screens by id: the safe area view's signals are sticky and heard once, a listener removes only
 * itself, a destroy and the reload reset forget the id, and a screen waits unless the core marked its root as one that
 * renders at once.
 */
class ScreenReadinessTest {
    private val heard = mutableListOf<String>()

    private fun listener(name: String) = object : ScreenReadiness.Listener {
        override fun onLaidOut() {
            heard += "$name:laidOut"
        }

        override fun onContentRendered() {
            heard += "$name:rendered"
        }
    }

    @After
    fun forget() = ScreenReadiness.reset()

    @Test
    fun aScreenNobodyToldOfIsUnknown() {
        assertNull(ScreenReadiness.isContentRendered("Home1"))
        ScreenReadiness.wrapped("Home1")
        assertEquals(false, ScreenReadiness.isContentRendered("Home1"))
    }

    @Test
    fun theSignalsAreStickyAndHeardOnce() {
        ScreenReadiness.setListener("Home1", listener("home"))
        ScreenReadiness.laidOut("Home1")
        ScreenReadiness.laidOut("Home1")
        ScreenReadiness.contentRendered("Home1")
        ScreenReadiness.contentRendered("Home1")
        assertEquals(listOf("home:laidOut", "home:rendered"), heard)
        assertEquals(true, ScreenReadiness.isContentRendered("Home1"))
    }

    @Test
    fun aListenerRemovesOnlyItself() {
        val first = listener("first")
        val second = listener("second")
        ScreenReadiness.setListener("Home1", first)
        ScreenReadiness.setListener("Home1", second)
        ScreenReadiness.removeListener("Home1", first)
        ScreenReadiness.contentRendered("Home1")
        assertEquals(listOf("second:rendered"), heard)
    }

    @Test
    fun aDestroyForgetsTheId() {
        ScreenReadiness.setListener("Home1", listener("home"))
        ScreenReadiness.contentRendered("Home1")
        ScreenReadiness.remove("Home1")
        assertNull(ScreenReadiness.isContentRendered("Home1"))
        heard.clear()
        ScreenReadiness.contentRendered("Home1")
        assertTrue(heard.isEmpty())
    }

    @Test
    fun theReloadResetForgetsEverything() {
        ScreenReadiness.contentRendered("Home1")
        ScreenReadiness.wrapped("Profile2")
        ScreenReadiness.setRendersAtOnce("Title3", true)
        ScreenReadiness.setListener("Details4", listener("details"))
        ScreenReadiness.reset()
        assertNull(ScreenReadiness.isContentRendered("Home1"))
        assertNull(ScreenReadiness.isContentRendered("Profile2"))
        assertTrue(ScreenReadiness.waitsForReadiness("Title3"))
        ScreenReadiness.contentRendered("Details4")
        assertTrue(heard.isEmpty())
    }

    @Test
    fun aScreenWaitsUnlessItsRootRendersAtOnce() {
        assertTrue(ScreenReadiness.waitsForReadiness("Home1"))
        ScreenReadiness.setRendersAtOnce("Title3", true)
        assertFalse(ScreenReadiness.waitsForReadiness("Title3"))
        ScreenReadiness.setRendersAtOnce("Title3", false)
        assertTrue(ScreenReadiness.waitsForReadiness("Title3"))
    }
}

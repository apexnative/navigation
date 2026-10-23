package dev.apexnative.navigation.viewcontrollers.component

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The readiness of screens by id: the safe area view's signals are kept and heard once per source, a listener that
 * comes later hears them at once, several listeners wait on one id and each is removed by its key, a listener hears
 * only the sources it accepts (an old root's signal with the same id is not its own), a destroy and the reload reset
 * forget the id, and an id renders at once while one of its roots marks it.
 */
class ScreenReadinessTest {
    private val heard = mutableListOf<String>()

    // A listener of the React surface [surface]; of every surface without one.
    private fun listener(name: String, surface: Int? = null) = object : ScreenReadiness.Listener {
        override fun accepts(source: Int): Boolean = surface == null || source == surface

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
    fun theSignalsAreKeptAndHeardOnce() {
        ScreenReadiness.addListener("Home1", "home", listener("home"))
        ScreenReadiness.laidOut("Home1", SURFACE)
        ScreenReadiness.laidOut("Home1", SURFACE)
        ScreenReadiness.contentRendered("Home1", SURFACE)
        ScreenReadiness.contentRendered("Home1", SURFACE)
        assertEquals(listOf("home:laidOut", "home:rendered"), heard)
        assertEquals(true, ScreenReadiness.isContentRendered("Home1"))
    }

    @Test
    fun aListenerThatComesLaterHearsWhatWasSaidAtOnce() {
        ScreenReadiness.laidOut("Home1", SURFACE)
        ScreenReadiness.contentRendered("Home1", SURFACE)
        assertTrue(heard.isEmpty())
        ScreenReadiness.addListener("Home1", "home", listener("home", SURFACE))
        assertEquals(listOf("home:laidOut", "home:rendered"), heard)
    }

    @Test
    fun aListenerThatStopsInItsReplayHearsNothingMore() {
        ScreenReadiness.laidOut("Home1", SURFACE)
        ScreenReadiness.contentRendered("Home1", SURFACE)
        val once = object : ScreenReadiness.Listener {
            override fun onLaidOut() {
                heard += "once:laidOut"
                ScreenReadiness.removeListener("Home1", "once")
            }

            override fun onContentRendered() {
                heard += "once:rendered"
            }
        }
        ScreenReadiness.addListener("Home1", "once", once)
        assertEquals(listOf("once:laidOut"), heard)
    }

    @Test
    fun severalListenersWaitOnOneId() {
        ScreenReadiness.addListener("Title3", "first", listener("first"))
        ScreenReadiness.addListener("Title3", "second", listener("second"))
        ScreenReadiness.contentRendered("Title3", SURFACE)
        assertEquals(listOf("first:rendered", "second:rendered"), heard)
    }

    @Test
    fun aListenerIsRemovedByItsKeyAndTheOthersKeepTheirs() {
        ScreenReadiness.addListener("Home1", "first", listener("first"))
        ScreenReadiness.addListener("Home1", "second", listener("second"))
        ScreenReadiness.removeListener("Home1", "first")
        ScreenReadiness.contentRendered("Home1", SURFACE)
        assertEquals(listOf("second:rendered"), heard)
        heard.clear()
        ScreenReadiness.removeListener("Home1", "first")
        ScreenReadiness.contentRendered("Home1", OTHER_SURFACE)
        assertEquals(listOf("second:rendered"), heard)
    }

    @Test
    fun theSignalOfAnotherRootWithTheSameIdIsNotOwn() {
        ScreenReadiness.contentRendered("Home1", OLD_SURFACE)
        ScreenReadiness.addListener("Home1", "new", listener("new", SURFACE))
        assertTrue(heard.isEmpty())
        assertEquals(false, ScreenReadiness.isContentRendered("Home1", listener("new", SURFACE)))
        ScreenReadiness.contentRendered("Home1", OLD_SURFACE)
        assertTrue(heard.isEmpty())
        ScreenReadiness.contentRendered("Home1", SURFACE)
        assertEquals(listOf("new:rendered"), heard)
        assertEquals(true, ScreenReadiness.isContentRendered("Home1", listener("new", SURFACE)))
    }

    @Test
    fun twoLiveRootsOfOneIdEachHearTheirOwnRender() {
        ScreenReadiness.addListener("Title3", "a", listener("a", SURFACE))
        ScreenReadiness.addListener("Title3", "b", listener("b", OTHER_SURFACE))
        ScreenReadiness.contentRendered("Title3", SURFACE)
        assertEquals(listOf("a:rendered"), heard)
        ScreenReadiness.contentRendered("Title3", OTHER_SURFACE)
        assertEquals(listOf("a:rendered", "b:rendered"), heard)
    }

    @Test
    fun aDestroyForgetsTheId() {
        ScreenReadiness.contentRendered("Home1", SURFACE)
        ScreenReadiness.remove("Home1")
        assertNull(ScreenReadiness.isContentRendered("Home1"))
        ScreenReadiness.addListener("Home1", "home", listener("home"))
        assertTrue(heard.isEmpty())
    }

    @Test
    fun theReloadResetForgetsEverything() {
        ScreenReadiness.contentRendered("Home1", SURFACE)
        ScreenReadiness.wrapped("Profile2")
        ScreenReadiness.setRendersAtOnce("Title3", "root", true)
        ScreenReadiness.addListener("Details4", "details", listener("details"))
        ScreenReadiness.reset()
        assertNull(ScreenReadiness.isContentRendered("Home1"))
        assertNull(ScreenReadiness.isContentRendered("Profile2"))
        assertTrue(ScreenReadiness.waitsForReadiness("Title3"))
        ScreenReadiness.contentRendered("Details4", SURFACE)
        assertTrue(heard.isEmpty())
        ScreenReadiness.addListener("Home1", "home", listener("home"))
        assertTrue(heard.isEmpty())
    }

    @Test
    fun anIdRendersAtOnceWhileOneOfItsRootsMarksIt() {
        assertTrue(ScreenReadiness.waitsForReadiness("Home1"))
        ScreenReadiness.setRendersAtOnce("Title3", "first", true)
        ScreenReadiness.setRendersAtOnce("Title3", "second", true)
        ScreenReadiness.contentRendered("Title3", SURFACE)
        assertFalse(ScreenReadiness.waitsForReadiness("Title3"))
        ScreenReadiness.setRendersAtOnce("Title3", "first", false)
        assertFalse(ScreenReadiness.waitsForReadiness("Title3"))
        assertEquals(true, ScreenReadiness.isContentRendered("Title3"))
        ScreenReadiness.setRendersAtOnce("Title3", "second", false)
        assertTrue(ScreenReadiness.waitsForReadiness("Title3"))
        assertNull(ScreenReadiness.isContentRendered("Title3"))
    }

    private companion object {
        const val SURFACE = 11
        const val OTHER_SURFACE = 21
        const val OLD_SURFACE = 1
    }
}

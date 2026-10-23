package dev.apexnative.navigation.slots

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The slot views by slot id: a change of a container reaches the views under its id only; a view leaves when it
 * unmounts, when its id changes, and when the collector takes it.
 */
class SlotViewsTest {
    // A stand-in for a slot view: equal only to itself, as a view is.
    private class Slot(val name: String) {
        override fun toString() = name
    }

    private val views = SlotViews<Slot>()

    @Test
    fun aChangeReachesOnlyTheViewUnderItsId() {
        val home = Slot("home")
        val profile = Slot("profile")
        views.register("Component1", home)
        views.register("Component2", profile)

        assertEquals(listOf(home), views.views("Component1"))
        assertEquals(listOf(profile), views.views("Component2"))
        assertTrue(views.views("Component3").isEmpty())
    }

    @Test
    fun registeringAgainUnderTheSameIdKeepsOneEntry() {
        val home = Slot("home")
        views.register("Component1", home)
        views.register("Component1", home)

        assertEquals(listOf(home), views.views("Component1"))
    }

    @Test
    fun anUnmountedViewHearsNothing() {
        val home = Slot("home")
        views.register("Component1", home)
        views.unregister(home)

        assertTrue(views.views("Component1").isEmpty())
        // Leaving twice, or without having come, is harmless.
        views.unregister(home)
        views.unregister(Slot("settings"))
    }

    @Test
    fun aViewWithANewIdLeavesItsOldOne() {
        val home = Slot("home")
        views.register("Component1", home)
        views.register("Component2", home)

        assertTrue(views.views("Component1").isEmpty())
        assertEquals(listOf(home), views.views("Component2"))

        views.unregister(home)
        assertTrue(views.views("Component2").isEmpty())
    }

    @Test
    fun twoViewsUnderOneIdBothHearInTheOrderTheyCame() {
        val first = Slot("first")
        val second = Slot("second")
        views.register("Component1", first)
        views.register("Component1", second)

        assertEquals(listOf(first, second), views.views("Component1"))

        views.unregister(first)
        assertEquals(listOf(second), views.views("Component1"))
    }

    @Test
    fun aCollectedViewIsGone() {
        val kept = Slot("kept")
        views.register("Component1", kept)
        registerUnreferenced("Component1")
        registerUnreferenced("Component2")

        // The views are held weakly: once nothing else holds one, the collector takes it.
        var tries = 0
        while ((views.views("Component1").size > 1 || views.views("Component2").isNotEmpty()) && tries++ < 50) {
            System.gc()
            Thread.sleep(10)
        }
        assertEquals(listOf(kept), views.views("Component1"))
        assertTrue(views.views("Component2").isEmpty())
    }

    // A view only the map holds.
    private fun registerUnreferenced(slotId: String) {
        views.register(slotId, Slot("unreferenced"))
    }
}

package dev.apexnative.navigation.viewcontrollers.navigator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The navigation activities of `Navigators`, with plain objects for activities. */
class ActivityTrackerTest {
    private class Activity(var finishing: Boolean = false, var destroyed: Boolean = false)

    private val tracker = ActivityTracker<Activity>({ it.finishing }, { it.destroyed })

    @Test
    fun noneAtFirst() {
        assertNull(tracker.current())
        assertTrue(tracker.all().isEmpty())
    }

    @Test
    fun theNewestIsCurrent() {
        val first = Activity()
        val second = Activity()
        tracker.add(first)
        tracker.add(second)
        assertSame(second, tracker.current())
        assertEquals(listOf(first, second), tracker.all())
    }

    @Test
    fun aFinishingActivityIsNotCurrent() {
        val first = Activity()
        val second = Activity(finishing = true)
        tracker.add(first)
        tracker.add(second)
        assertSame(first, tracker.current())
        assertEquals(listOf(first, second), tracker.all())
    }

    @Test
    fun aDestroyedActivityIsNeitherCurrentNorLive() {
        val first = Activity()
        val second = Activity(destroyed = true)
        tracker.add(first)
        tracker.add(second)
        assertSame(first, tracker.current())
        assertEquals(listOf(first), tracker.all())
    }

    @Test
    fun removed() {
        val first = Activity()
        tracker.add(first)
        tracker.remove(first)
        assertNull(tracker.current())
    }

    @Test
    fun aCollectedActivityIsGone() {
        val kept = Activity()
        tracker.add(kept)
        addUnreferenced()
        // The tracker holds activities weakly: once nothing else holds one, the collector takes it.
        var tries = 0
        while (tracker.all().size > 1 && tries++ < 50) {
            System.gc()
            Thread.sleep(10)
        }
        assertEquals(listOf(kept), tracker.all())
        assertSame(kept, tracker.current())
    }

    // An activity only the tracker holds.
    private fun addUnreferenced() {
        tracker.add(Activity())
    }
}

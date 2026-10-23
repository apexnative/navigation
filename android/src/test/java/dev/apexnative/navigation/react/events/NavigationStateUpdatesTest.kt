package dev.apexnative.navigation.react.events

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The navigation state's posted update and its hold: one update per pass of the main loop; what is asked while held,
 * and a posted update that runs while held, goes once, at the last release.
 */
class NavigationStateUpdatesTest {
    private val asked = mutableListOf<String>()
    private val posted = mutableListOf<() -> Unit>()
    private var generation = 1
    private val hold = NavigationState.Updates(post = { posted += it }, generation = { generation }) {
        asked += "update"
    }

    // The next pass of the main loop.
    private fun nextPass() {
        val tasks = posted.toList()
        posted.clear()
        tasks.forEach { it() }
    }

    @Test
    fun theAsksOfOnePassMakeOneUpdate() {
        hold.setNeedsUpdate()
        hold.setNeedsUpdate()
        assertEquals(1, posted.size)
        nextPass()
        assertEquals(listOf("update"), asked)
        hold.setNeedsUpdate()
        nextPass()
        assertEquals(listOf("update", "update"), asked)
    }

    @Test
    fun whatIsAskedWhileHeldGoesOnceAtTheLastRelease() {
        hold.hold()
        hold.hold()
        hold.setNeedsUpdate()
        hold.setNeedsUpdate()
        hold.release()
        nextPass()
        assertEquals(emptyList<String>(), asked)
        assertEquals(1, hold.count)
        hold.release()
        nextPass()
        assertEquals(listOf("update"), asked)
        assertEquals(0, hold.count)
    }

    @Test
    fun anUpdatePostedBeforeTheHoldWaitsForTheRelease() {
        hold.setNeedsUpdate()
        hold.hold()
        nextPass()
        assertEquals(emptyList<String>(), asked)
        hold.release()
        nextPass()
        assertEquals(listOf("update"), asked)
    }

    @Test
    fun anUpdateOfAJavaScriptThatWentIsDroppedAndANewAskStillGoes() {
        hold.setNeedsUpdate()
        generation++
        nextPass()
        assertEquals(emptyList<String>(), asked)
        hold.setNeedsUpdate()
        generation++
        hold.setNeedsUpdate()
        nextPass()
        assertEquals(listOf("update"), asked)
    }

    @Test
    fun aReleaseWithNothingAskedAsksNothing() {
        hold.hold()
        hold.release()
        nextPass()
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun theCountNeverGoesBelowZero() {
        hold.release()
        assertEquals(0, hold.count)
        hold.hold()
        hold.release()
        hold.release()
        assertEquals(0, hold.count)
        // A release too many doesn't eat the next hold.
        hold.hold()
        hold.setNeedsUpdate()
        nextPass()
        assertEquals(emptyList<String>(), asked)
        hold.release()
        nextPass()
        assertEquals(listOf("update"), asked)
    }

    @Test
    fun aHoldLeftByAJavaScriptThatWentHoldsNothingOfTheNext() {
        // The old JavaScript's `setRoot` held the updates and never released them.
        hold.hold()
        hold.setNeedsUpdate()
        generation++
        hold.dropHolds()
        assertEquals(0, hold.count)
        nextPass()
        assertEquals(emptyList<String>(), asked)
        // The new JavaScript's next update goes in the next pass.
        hold.setNeedsUpdate()
        nextPass()
        assertEquals(listOf("update"), asked)
    }
}

package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.react.events.FakeContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the stack's queue does for its bar: the waits for the components of the bar drawn in advance and the motion of
 * the bar's row told before each change of the current child.
 */
class StackBarTest {
    private val h = StackQueueHarness()
    private val queue: StackQueue
        get() = h.queue

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    // The waits.

    @Test
    fun aPushWithWaitForRenderWaitsForTheBarAfterTheContent() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        val bar = FakeContent(ready = false).also { h.readiness.bars[b] = it }
        h.push(b)
        b.content.mount()
        // The content is ready; the bar's title and background are not: B still waits.
        assertEquals(listOf("A"), h.shown())
        bar.mount()
        assertEquals(listOf("A", "B"), h.shown())
        assertTrue(h.events().none { it.startsWith("markReady") })
    }

    @Test
    fun theBarWaitsHalfASecondAtMostAfterTheContentAndMarksNothing() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.readiness.bars[b] = FakeContent(ready = false)
        h.push(b)
        b.content.mount()
        h.scheduler.advance(StackQueue.BAR_WAIT_MILLIS - 1)
        assertEquals(listOf("A"), h.shown())
        h.scheduler.advance(1)
        assertEquals(listOf("A", "B"), h.shown())
        assertTrue(h.events().none { it.startsWith("markReady") })
    }

    @Test
    fun aPushWithoutWaitForRenderDoesNotWaitForTheBar() {
        h.start(h.screen("A"))
        val b = h.screen("B")
        h.readiness.bars[b] = FakeContent(ready = false)
        h.push(b)
        assertEquals(listOf("A", "B"), h.shown())
    }

    @Test
    fun aPushWithWaitForRenderFalseDoesNotWaitForTheBar() {
        h.start(h.screen("A"))
        val b = h.screen("B", options = h.options { animations.push.waitForRender = Bool(false) })
        h.readiness.bars[b] = FakeContent(ready = false)
        h.push(b)
        assertEquals(listOf("A", "B"), h.shown())
    }

    @Test
    fun aSetStackRootWaitsForContentAndBarWithinOneHalfSecond() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = true)
        val bar = FakeContent(ready = false).also { h.readiness.bars[b] = it }
        h.setStackRoot(b)
        assertEquals(listOf("A"), h.shown())
        h.scheduler.advance(StackQueue.SET_STACK_ROOT_WAIT_MILLIS - 1)
        assertEquals(listOf("A"), h.shown())
        bar.mount()
        assertEquals(listOf("B"), h.shown())
    }

    @Test
    fun aSetStackRootAtItsLimitMarksOnlyTheContentNotReady() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false)
        h.readiness.bars[b] = FakeContent(ready = false)
        h.setStackRoot(b)
        h.scheduler.advance(StackQueue.SET_STACK_ROOT_WAIT_MILLIS)
        assertEquals(listOf("B"), h.shown())
        assertEquals(1, h.events().count { it == "markReady:B" })
    }

    @Test
    fun aSetStackRootWhoseContentWasReadyMarksNothingAtItsLimit() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = true)
        h.readiness.bars[b] = FakeContent(ready = false)
        h.setStackRoot(b)
        h.scheduler.advance(StackQueue.SET_STACK_ROOT_WAIT_MILLIS)
        assertEquals(listOf("B"), h.shown())
        assertTrue(h.events().none { it.startsWith("markReady") })
    }

    @Test
    fun aSetStackRootWithWaitForRenderWaitsForTheContentThenTheBar() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false, options = h.options { animations.setStackRoot.waitForRender = Bool(true) })
        h.readiness.bars[b] = FakeContent(ready = false)
        h.setStackRoot(b)
        h.scheduler.advance(5_000)
        assertEquals(listOf("A"), h.shown())
        b.content.mount()
        h.scheduler.advance(StackQueue.BAR_WAIT_MILLIS - 1)
        assertEquals(listOf("A"), h.shown())
        h.scheduler.advance(1)
        assertEquals(listOf("B"), h.shown())
        assertTrue(h.events().none { it.startsWith("markReady") })
    }

    @Test
    fun aPopDuringTheBarPhaseEndsTheWaitingPush() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.readiness.bars[b] = FakeContent(ready = false)
        h.push(b)
        b.content.mount()
        h.pop(b)
        assertEquals(listOf("A"), h.shown())
        assertEquals(listOf("A"), h.entries())
        h.scheduler.advance(StackQueue.BAR_WAIT_MILLIS)
        assertEquals(listOf("A"), h.shown())
    }

    // Review fixes.

    @Test
    fun aTransitionTakenOverDoesNotSettleTheTopBeforeTheNewOneEnds() {
        h.start(h.screen("A"))
        val b = h.screen("B")
        h.push(b)
        val before = h.host.topSettled
        // Back before the push ended: the pop takes both screens over, the push ends, and the bar's row stays with the
        // pop: the top settles only at the pop's own end.
        h.pop(b)
        assertEquals(before, h.host.topSettled)
        h.display.finishAll()
        assertEquals(before + 1, h.host.topSettled)
    }

    @Test
    fun aMergeOfTheBarsVisibilityCountsTheTopsRoomAgain() {
        val a = h.screen("A")
        h.start(a)
        h.stack.mergeChildOptions(Options().apply { topBar.visible = Bool(false) }, a)
        // The stack's view doesn't show in the tests: the merge applies as its top shows.
        h.stack.applyChildOptions(Options(), a)
        assertTrue("topInset:A" in h.calls)
    }

    @Test
    fun aMergeOfTheHideOnScrollOfTheSearchCountsTheTopsRoomAgain() {
        val a = h.screen("A")
        h.start(a)
        h.stack.mergeChildOptions(Options().apply { topBar.searchBar.hideOnScroll = Bool(true) }, a)
        h.stack.applyChildOptions(Options(), a)
        assertTrue("topInset:A" in h.calls)
    }

    // The bar's row.

    @Test
    fun theRowGoesWithThePushThePopAndTheFadeThrough() {
        h.start(h.screen("A"))
        val b = h.screen("B")
        h.push(b)
        h.display.finishAll()
        h.pop(b)
        h.display.finishAll()
        h.setStackRoot(h.screen("C"))
        h.display.finishAll()
        h.setStackRoot(h.screen("D"), asPush = true)
        assertEquals(listOf("Push", "Pop", "FadeThrough", "Push"), h.host.barMotions)
        assertEquals(listOf("none", "none", "none", "none"), h.host.barTravels)
    }

    @Test
    fun theRowChangesAtOnceWithoutTheDefaultTransition() {
        h.start(h.screen("A"))
        h.push(h.screen("B", options = h.options { animations.push.enabled = Bool(false) }))
        h.push(h.screen("C", options = h.options {
            animations.push.content.enter.alpha.from = Fraction(0.0)
        }), from = "B")
        h.setStackRoot(h.screen("D"), animated = false)
        assertEquals(listOf("none", "none", "none"), h.host.barMotions)
        // Only the animation of its own takes the bar with the screens.
        assertEquals(listOf("none", "Forward", "none"), h.host.barTravels)
    }

    // A pop of its own turned off (`enabled: false`) changes the bar at once: nothing travels.
    @Test
    fun aPopOfItsOwnTurnedOffDoesntTakeTheBar() {
        h.start(h.screen("A"), h.screen("B", options = h.options {
            animations.pop.content.exit.alpha.to = Fraction(0.0)
            animations.pop.enabled = Bool(false)
        }))
        h.pop()
        assertEquals(listOf("none"), h.host.barTravels)
    }

    @Test
    fun aSilentCutChangesTheRowAtOnce() {
        h.start(h.screen("A"), h.screen("B"))
        h.stack.popToFirstSilently()
        assertEquals(listOf("none"), h.host.barMotions)
        assertFalse(h.host.barMotions.contains("Pop"))
    }
}

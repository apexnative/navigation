package dev.apexnative.navigation.viewcontrollers.navigator

import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.NullBool
import dev.apexnative.navigation.react.events.FakeScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule of the splash's hold of the window: when the first root counts as drawn. */
class FirstRootDrawTest {
    private val scheduler = FakeScheduler()
    private var calls = 0
    private val draw = FirstRootDraw(scheduler) { calls++ }

    @Test
    fun aRootThatGoesUpIsDrawn() {
        draw.rootSet(NullBool())
        assertFalse(draw.isDrawn)
        draw.rootUp()
        assertTrue(draw.isDrawn)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun anUnsetWaitHoldsAtMostItsLimit() {
        draw.rootSet(NullBool())
        scheduler.advance(FirstRootDraw.LIMIT_MILLIS - 1)
        assertFalse(draw.isDrawn)
        scheduler.advance(1)
        assertTrue(draw.isDrawn)
    }

    @Test
    fun waitForRenderTrueHoldsWithoutALimit() {
        draw.rootSet(Bool(true))
        assertEquals(0, scheduler.pending)
        scheduler.advance(60_000)
        assertFalse(draw.isDrawn)
        draw.rootUp()
        assertTrue(draw.isDrawn)
    }

    @Test
    fun theLimitCountsFromTheFirstSetRootThatHasOne() {
        draw.rootSet(Bool(true))
        scheduler.advance(1_000)
        draw.rootSet(NullBool())
        scheduler.advance(FirstRootDraw.LIMIT_MILLIS - 1)
        assertFalse(draw.isDrawn)
        draw.rootSet(NullBool())
        scheduler.advance(1)
        assertTrue(draw.isDrawn)
    }

    @Test
    fun waitForRenderFalseIsDrawnAtOnce() {
        draw.rootSet(Bool(false))
        assertTrue(draw.isDrawn)
    }

    @Test
    fun aRootDestroyedOnItsWayEndsTheHold() {
        draw.rootSet(Bool(true))
        draw.rootDestroyed()
        assertTrue(draw.isDrawn)
    }

    @Test
    fun drawnOnceForGood() {
        draw.rootSet(NullBool())
        draw.rootUp()
        draw.rootDestroyed()
        draw.rootSet(Bool(false))
        scheduler.advance(FirstRootDraw.LIMIT_MILLIS)
        assertEquals(1, calls)
    }

    @Test
    fun anActivityThatGoesStopsTheLimit() {
        draw.rootSet(NullBool())
        draw.cancel()
        scheduler.advance(FirstRootDraw.LIMIT_MILLIS)
        assertFalse(draw.isDrawn)
        assertEquals(0, calls)
    }
}

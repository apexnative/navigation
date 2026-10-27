package dev.apexnative.navigation.viewcontrollers.stack.fab

import dev.apexnative.navigation.viewcontrollers.bottomtabs.TabBarMotion
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarMotion
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabMotion.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How the FAB goes with a change of its stack's top: the move a transition gives it, and the values of the new FAB's
 * arrival and of the old one's departure.
 */
class FabMotionTest {
    @Test
    fun theMoveFollowsTheTransition() {
        assertEquals(Move.WithPush, FabMotion.moveOf(animated = true, own = false, BarMotion.Push, shown = false))
        assertEquals(Move.WithPop, FabMotion.moveOf(animated = true, own = false, BarMotion.Pop, shown = false))
        assertEquals(Move.FadeThrough, FabMotion.moveOf(true, own = false, BarMotion.FadeThrough, shown = false))
        assertEquals(Move.Swap, FabMotion.moveOf(animated = true, own = true, BarMotion.Push, shown = false))
        assertEquals(Move.AtOnce, FabMotion.moveOf(animated = false, own = false, BarMotion.Push, shown = false))
        // A back gesture's preview showed the new top's FAB already.
        assertEquals(Move.AtOnce, FabMotion.moveOf(animated = true, own = false, BarMotion.Pop, shown = true))
    }

    // The FAB rides as the tab bar does with the same screens: along the line, fading after the same hold.
    @Test
    fun theSystemsPushAndPopRideAsTheTabBar() {
        for (rtl in listOf(false, true)) {
            for ((move, tabs) in listOf(Move.WithPush to TabBarMotion.Move.WithPush, Move.WithPop to TabBarMotion.Move.WithPop)) {
                for (arriving in listOf(true, false)) {
                    val fab = requireNotNull(if (arriving) FabMotion.arrival(move, rtl) else FabMotion.departure(move, rtl))
                    val bar = requireNotNull(TabBarMotion.ride(tabs, arriving, rtl))
                    assertEquals(bar.fromX, fab.fromX)
                    assertEquals(bar.toX, fab.toX)
                    assertEquals(bar.fromAlpha, fab.fromAlpha)
                    assertEquals(bar.toAlpha, fab.toAlpha)
                    assertEquals(bar.alphaDelay, fab.alphaDelay)
                    assertEquals(bar.alphaDuration, fab.alphaDuration)
                    assertEquals(bar.duration, fab.duration)
                    assertEquals(1f, fab.fromScale)
                    assertEquals(1f, fab.toScale)
                }
            }
        }
    }

    @Test
    fun aPushBringsTheNewOneFromTheEndAndTakesTheOldOneToTheStart() {
        val arrival = requireNotNull(FabMotion.arrival(Move.WithPush, rtl = false))
        assertEquals(96f, arrival.fromX)
        assertEquals(0f, arrival.toX)
        assertEquals(50L, arrival.alphaDelay)
        val departure = requireNotNull(FabMotion.departure(Move.WithPush, rtl = false))
        assertEquals(-96f, departure.toX)
        assertEquals(0f, departure.toAlpha)
        // Right to left, the other way.
        assertEquals(-96f, requireNotNull(FabMotion.arrival(Move.WithPush, rtl = true)).fromX)
        assertEquals(96f, requireNotNull(FabMotion.departure(Move.WithPush, rtl = true)).toX)
    }

    @Test
    fun theFadeThroughFadesTheOldOneOutFirstAndGrowsTheNewOne() {
        val departure = requireNotNull(FabMotion.departure(Move.FadeThrough, rtl = false))
        assertEquals(0f, departure.toAlpha)
        assertEquals(105L, departure.alphaDuration)
        val arrival = requireNotNull(FabMotion.arrival(Move.FadeThrough, rtl = false))
        assertEquals(0f, arrival.fromAlpha)
        assertEquals(105L, arrival.alphaDelay)
        assertEquals(195L, arrival.alphaDuration)
        assertEquals(0.92f, arrival.fromScale)
        assertEquals(1f, arrival.toScale)
        assertEquals(300L, arrival.duration)
        assertEquals(0f, arrival.fromX)
        assertEquals(0f, arrival.toX)
    }

    @Test
    fun theSwapShrinksTheOldOneAndGrowsTheNewOne() {
        val departure = requireNotNull(FabMotion.departure(Move.Swap, rtl = false))
        assertEquals(0f, departure.toScale)
        assertEquals(1f, departure.toAlpha)
        assertEquals(200L, departure.duration)
        val arrival = requireNotNull(FabMotion.arrival(Move.Swap, rtl = false))
        assertEquals(0.6f, arrival.fromScale)
        assertEquals(1f, arrival.toScale)
        assertEquals(1f, arrival.fromAlpha)
        assertEquals(200L, arrival.duration)
    }

    @Test
    fun atOnceNothingRides() {
        assertNull(FabMotion.arrival(Move.AtOnce, rtl = false))
        assertNull(FabMotion.departure(Move.AtOnce, rtl = false))
    }
}

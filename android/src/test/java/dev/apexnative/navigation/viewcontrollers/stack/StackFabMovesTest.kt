package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * How the queue tells the FAB to go with each change of its top, from the start of the transition: with the system's
 * push and pop and the fade through of `setStackRoot`, by Material's swap with an animation of the screens' own, at
 * once without animation; nothing while a push waits for its screen.
 */
class StackFabMovesTest {
    private val h = StackQueueHarness()

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    @Test
    fun theSystemsTransitionsCarryTheFab() {
        h.start(h.screen("A"))
        val b = h.screen("B")
        h.push(b)
        h.display.finishAll()
        h.pop(b)
        h.display.finishAll()
        h.setStackRoot(h.screen("C"))
        h.display.finishAll()
        h.setStackRoot(h.screen("D"), asPush = true)
        assertEquals(listOf("WithPush", "WithPop", "FadeThrough", "WithPush"), h.host.fabMoves)
    }

    @Test
    fun anAnimationOfItsOwnSwapsAndNoneIsAtOnce() {
        h.start(h.screen("A"))
        h.push(h.screen("B", options = h.options {
            animations.push.content.enter.alpha.from = Fraction(0.0)
        }))
        h.display.finishAll()
        h.push(h.screen("C", options = h.options { animations.push.enabled = Bool(false) }), from = "B")
        h.setStackRoot(h.screen("D"), animated = false)
        assertEquals(listOf("Swap", "AtOnce", "AtOnce"), h.host.fabMoves)
    }

    @Test
    fun aSilentCutAndAStackOffScreenGoAtOnce() {
        h.start(h.screen("A"), h.screen("B"))
        h.stack.popToFirstSilently()
        h.display.isOnScreen = false
        h.push(h.screen("C"))
        assertEquals(listOf("AtOnce", "AtOnce"), h.host.fabMoves)
    }

    @Test
    fun aPushThatWaitsForItsScreenMovesNothingUntilItStarts() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        assertTrue(h.host.fabMoves.isEmpty())
        b.content.mount()
        assertEquals(listOf("WithPush"), h.host.fabMoves)
    }
}

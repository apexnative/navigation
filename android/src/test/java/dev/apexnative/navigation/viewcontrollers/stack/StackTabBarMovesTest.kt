package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * How the queue tells the tab bar to go with each change of its top: with the system's push and pop, by a slide with an
 * animation of its own or a `setStackRoot` that fades, at once without animation; nothing while a push waits for its
 * screen.
 */
class StackTabBarMovesTest {
    private val h = StackQueueHarness()

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    @Test
    fun theSystemsPushAndPopRideAndSetStackRootSlidesOrRides() {
        h.start(h.screen("A"))
        val b = h.screen("B")
        h.push(b)
        h.display.finishAll()
        h.pop(b)
        h.display.finishAll()
        h.setStackRoot(h.screen("C"))
        h.display.finishAll()
        h.setStackRoot(h.screen("D"), asPush = true)
        assertEquals(listOf("WithPush", "WithPop", "Slide", "WithPush"), h.host.tabBarMoves)
    }

    @Test
    fun anAnimationOfItsOwnSlidesAndNoneIsAtOnce() {
        h.start(h.screen("A"))
        h.push(h.screen("B", options = h.options {
            animations.push.content.enter.alpha.from = Fraction(0.0)
        }))
        h.display.finishAll()
        h.push(h.screen("C", options = h.options {
            animations.push.bottomTabs.exit.translationY.to = Fraction(80.0)
        }), from = "B")
        h.display.finishAll()
        h.push(h.screen("D", options = h.options { animations.push.enabled = Bool(false) }), from = "C")
        h.setStackRoot(h.screen("E"), animated = false)
        assertEquals(listOf("Slide", "Slide", "AtOnce", "AtOnce"), h.host.tabBarMoves)
        // The tabs' side of its own goes with the slide; an animation of the screens' own has none to give.
        assertEquals(listOf(false, true, false, false), h.host.tabBarSides)
    }

    // A side with nothing that plays is no side: the system's ride stays; `enabled: false` is one — the bar shows or
    // hides at once, by the tabs.
    @Test
    fun aSideThatDoesntPlayKeepsTheRideAndATurnedOffOneSlidesAtOnce() {
        h.start(h.screen("A"))
        h.push(h.screen("B", options = h.options { animations.push.bottomTabs.enter.enabled = Bool(true) }))
        h.display.finishAll()
        h.push(h.screen("C", options = h.options { animations.push.bottomTabs.exit.enabled = Bool(false) }), from = "B")
        assertEquals(listOf("WithPush", "Slide"), h.host.tabBarMoves)
        assertEquals(listOf(false, true), h.host.tabBarSides)
    }

    @Test
    fun aSilentCutAndAStackOffScreenGoAtOnce() {
        h.start(h.screen("A"), h.screen("B"))
        h.stack.popToFirstSilently()
        h.display.isOnScreen = false
        h.push(h.screen("C"))
        assertEquals(listOf("AtOnce", "AtOnce"), h.host.tabBarMoves)
    }

    @Test
    fun aPushThatWaitsForItsScreenMovesNothingUntilItStarts() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        assertTrue(h.host.tabBarMoves.isEmpty())
        b.content.mount()
        assertEquals(listOf("WithPush"), h.host.tabBarMoves)
    }
}

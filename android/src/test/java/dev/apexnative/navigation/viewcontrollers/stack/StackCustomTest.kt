package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.ElementTransitions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.options.SharedElements
import dev.apexnative.navigation.options.StackAnimationOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.Text
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The stack's transitions of their own: what the queue asks the display for each screen, the bars' row and visibility,
 * the wait for the elements' targets as a wait of the queue, and the owners when transitions overlap.
 */
class StackCustomTest {
    private val h = StackQueueHarness()
    private val display = h.display

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    private fun withElements(animation: StackAnimationOptions, id: String = "Avatar") {
        animation.elementTransitions = ElementTransitions(listOf(ElementTransitionOptions(id).apply {
            alpha.from = Fraction(0.0)
        }))
    }

    private fun withShared(animation: StackAnimationOptions) {
        animation.sharedElements = SharedElements(listOf(SharedElementTransitionOptions(Text("Row"), Text("Avatar"))))
    }

    private fun fadeIn(options: Options) {
        options.animations.push.content.enter.alpha.from = Fraction(0.0)
    }

    // The arriving screen plays its `enter` from its alpha at rest, the covered one stands.
    @Test
    fun aPushOfItsOwn() {
        val a = h.screen("A")
        h.start(a)
        val b = h.screen("B", options = h.options(::fadeIn))
        h.push(b)
        h.assertEvents("will:B")
        assertEquals(listOf("Custom:B:alpha:1.0", "Custom:A:-:1.0"), display.starts)
        // No row motion: the bar goes with the screens; it would show or hide animated; the tab bar slides.
        assertEquals(listOf("none"), h.host.barMotions)
        assertEquals(listOf("Forward"), h.host.barTravels)
        assertEquals(listOf("animated"), h.host.barVisibilities)
        assertEquals(listOf("Slide"), h.host.tabBarMoves)
        assertTrue(display.isRunning(b))
        assertFalse(display.isRunning(a))
        display.finish(b)
        h.assertEvents("didDisappear:A", "didAppear:B", "stateNow", "state", "push1 resolve B", "completed:push")
        h.assertViews("raise:B", "detach:A")
    }

    // A push with only `content.exit`: the new screen is there on the first frame, the old one goes by `exit`.
    @Test
    fun onlyTheExitSide() {
        val a = h.screen("A")
        h.start(a)
        val b = h.screen("B", options = h.options { animations.push.content.exit.translationX.to = Fraction(-50.0) })
        h.push(b)
        assertEquals(listOf("Custom:B:-:1.0", "Custom:A:translationX:1.0"), display.starts)
        assertFalse(display.isRunning(b))
        display.finish(a)
        h.assertEvents(
            "will:B", "didDisappear:A", "didAppear:B", "stateNow", "state", "push1 resolve B", "completed:push",
        )
    }

    // A screen that waited hidden plays its `alpha` from 0 to its alpha at rest, not to 0.
    @Test
    fun theAlphaBeforeTheWaitsHide() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false, options = h.options {
            animations.push.waitForRender = Bool(true)
            fadeIn(this)
        })
        h.push(b)
        assertEquals(0f, display.valuesOf(b).alpha)
        b.content.mount()
        assertEquals("Custom:B:alpha:1.0", display.starts.first())
    }

    // The screen that came by its own `enter` keeps its end values; the covered one rests.
    @Test
    fun theArrivingScreenKeepsItsEnd() {
        val a = h.screen("A")
        h.start(a)
        val b = h.screen("B", options = h.options { animations.push.content.enter.alpha.to = Fraction(0.5) })
        h.push(b)
        display.valuesOf(b).alpha = 0.5f
        display.finish(b)
        assertEquals(0.5f, display.valuesOf(b).alpha)
        assertEquals(1f, display.valuesOf(a).alpha)
    }

    // A pop of its own: the leaving top's `animations.pop`; the revealed screen rests at the end.
    @Test
    fun aPopOfItsOwn() {
        val a = h.screen("A")
        val b = h.screen("B", options = h.options { animations.pop.content.exit.translationY.to = Fraction(300.0) })
        h.start(a, b)
        h.pop()
        assertEquals(listOf("Custom:B:translationY:1.0", "Custom:A:-:1.0"), display.starts)
        assertEquals(listOf("none"), h.host.barMotions)
        // The bar goes with the screens, the leaving one over it.
        assertEquals(listOf("Back"), h.host.barTravels)
        assertEquals(listOf("Slide"), h.host.tabBarMoves)
        display.finish(b)
        h.assertEvents(
            "will:A", "didDisappear:B", "didAppear:A", "stateNow", "popped:B", "destroyed:B", "state",
            "pop1 resolve B", "completed:pop",
        )
        assertEquals(1f, display.valuesOf(a).alpha)
    }

    // The revealed screen of a pop of its own that still renders as a lower screen (transparent under the others) shows
    // with its alpha at rest; one a running transition still moves starts where it is.
    @Test
    fun aPopRevealsALowerScreenStillRenderingOpaque() {
        val a = h.screen("A", ready = false)
        val b = h.screen("B", options = h.options { animations.pop.content.exit.translationX.to = Fraction(100.0) })
        h.start(a, b)
        h.queue.renderLowerScreens()
        assertEquals(0f, display.valuesOf(a).alpha)
        h.clear()
        h.pop()
        assertEquals(listOf("Custom:B:translationX:1.0", "Custom:A:-:1.0"), display.starts)
    }

    // A pop with only `content.enter`: the leaving screen stands opaque over the revealed one to the end.
    @Test
    fun aPopWithOnlyTheEnterSide() {
        val a = h.screen("A")
        val b = h.screen("B", options = h.options { animations.pop.content.enter.scaleX.from = Fraction(0.9) })
        h.start(a, b)
        h.pop()
        assertEquals(listOf("Custom:B:-:1.0", "Custom:A:scaleX:1.0"), display.starts)
        assertTrue(display.isRunning(a))
        assertFalse(h.journal.any { it.startsWith("destroyed:B") })
        display.finish(a)
        assertTrue(h.journal.contains("destroyed:B"))
    }

    // A push with elements waits for their targets after its content, its screen in the container; the bars change at
    // once; the screen fades in, it carries the elements.
    @Test
    fun aPushWithElementsWaitsForItsTargets() {
        val a = h.screen("A")
        h.start(a)
        val b = h.screen("B", options = h.options { withElements(animations.push) })
        h.push(b)
        h.assertViews("placeWaiting:B", "awaitTargets:A>B")
        assertTrue(h.journal.isEmpty())
        assertTrue(display.targetsWaiting(b))
        display.findTargets(b)
        h.assertEvents("will:B")
        assertEquals(listOf("Custom:B:- fade-in elements:A>B:0.0", "Custom:A:-:1.0"), display.starts)
        assertEquals(listOf("none"), h.host.barMotions)
        assertEquals(listOf("none"), h.host.barTravels)
        assertEquals(listOf("at once"), h.host.barVisibilities)
        assertEquals(listOf("AtOnce"), h.host.tabBarMoves)
        display.finish(b)
        assertTrue(h.journal.contains("push1 resolve B"))
        assertEquals(1f, display.valuesOf(b).alpha)
    }

    // With elements Android takes the screen's own `enter` only when it is a single `alpha`.
    @Test
    fun withElementsASingleAlphaIsTheFade() {
        h.start(h.screen("A"))
        val b = h.screen("B", options = h.options {
            withShared(animations.push)
            fadeIn(this)
            animations.push.content.exit.alpha.to = Fraction(0.0)
        })
        h.push(b)
        display.findTargets(b)
        // The covered screen stands with elements whatever its `exit`.
        assertEquals(listOf("Custom:B:alpha elements:A>B:1.0", "Custom:A:-:1.0"), display.starts)
        h.clear()
        val c = h.screen("C", options = h.options {
            withShared(animations.push)
            animations.push.content.enter.alpha.from = Fraction(0.0)
            animations.push.content.enter.scaleX.from = Fraction(0.8)
        })
        display.finishAll()
        h.push(c, from = "B")
        display.findTargets(c)
        assertEquals("Custom:C:- fade-in elements:B>C:0.0", display.starts.first())
    }

    // `waitForRender: false`: no wait for the targets, the transition starts at once.
    @Test
    fun withoutWaitForRenderTheElementsDontWait() {
        h.start(h.screen("A"))
        val b = h.screen("B", options = h.options {
            withElements(animations.push)
            animations.push.waitForRender = Bool(false)
        })
        h.push(b)
        assertFalse(display.awaitsTargets(b))
        assertEquals("Custom:B:- fade-in elements:A>B:0.0", display.starts.first())
    }

    // `waitForRender: true`: the content as long as it takes, then the targets.
    @Test
    fun theContentFirstThenTheTargets() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false, options = h.options {
            withElements(animations.push)
            animations.push.waitForRender = Bool(true)
        })
        h.push(b)
        h.assertViews("placeWaiting:B")
        b.content.mount()
        h.assertViews("awaitTargets:A>B")
        display.findTargets(b)
        assertTrue(display.isRunning(b))
    }

    // A pop during the wait for the targets trims the push: it resolves, its screen never appeared, nothing goes into a
    // layer.
    @Test
    fun aPopDuringTheTargetsWaitTrimsThePush() {
        val a = h.screen("A")
        h.start(a)
        val b = h.screen("B", options = h.options { withElements(animations.push) })
        h.push(b)
        h.clear()
        h.pop(b)
        h.assertViews("stopTargets:B", "remove:B")
        assertTrue(display.starts.isEmpty())
        assertTrue(h.journal.contains("push1 resolve B"))
        assertFalse(h.journal.contains("will:B"))
        assertTrue(h.queue.isAtRest)
        assertEquals(0, h.scheduler.pending)
    }

    // A stack destroyed during the wait for targets stops it; one destroyed during a transition of its own stops its
    // animators (the display puts the elements back).
    @Test
    fun aDestroyStopsTheWaitAndTheAnimators() {
        h.start(h.screen("A"))
        val b = h.screen("B", options = h.options { withElements(animations.push) })
        h.push(b)
        h.clear()
        h.stack.destroy()
        assertTrue(h.views.contains("stopTargets:B"))
        assertTrue(h.journal.any { it.startsWith("push1 reject") })
        val other = StackQueueHarness()
        other.install()
        try {
            other.start(other.screen("A"))
            val c = other.screen("C", options = other.options(::fadeIn))
            other.push(c)
            other.clear()
            other.stack.destroy()
            assertTrue(other.views.contains("stop:C"))
            assertTrue(other.display.animating.isEmpty())
        } finally {
            other.uninstall()
        }
    }

    // A pop during a push of its own plays: the push resolves at the cut, the leaving screen plays its pop from where
    // it is (its own, when it has one).
    @Test
    fun aPopDuringACustomPush() {
        val a = h.screen("A")
        h.start(a)
        val b = h.screen("B", options = h.options {
            fadeIn(this)
            animations.pop.content.exit.alpha.to = Fraction(0.0)
        })
        h.push(b)
        h.clear()
        h.pop()
        assertTrue(h.journal.contains("push1 resolve B"))
        assertEquals(listOf("Custom:B:alpha:1.0", "Custom:A:-:1.0"), display.starts)
        h.assertViews("reveal:A", "stop:B")
        display.finishAll()
        assertTrue(h.journal.contains("pop2 resolve B"))
    }

    // Two quick backs with pops of their own: the second takes the screen the first revealed; the first ends without
    // putting that screen to rest.
    @Test
    fun twoQuickBacksWithPopsOfTheirOwn() {
        val pop = Options().apply { animations.pop.content.exit.translationX.to = Fraction(100.0) }
        val a = h.screen("A")
        val b = h.screen("B", options = pop.copy())
        val c = h.screen("C", options = pop.copy())
        h.start(a, b, c)
        h.back()
        h.back()
        assertEquals(
            listOf("Custom:C:translationX:1.0", "Custom:B:-:1.0", "Custom:B:translationX:1.0", "Custom:A:-:1.0"),
            display.starts,
        )
        display.valuesOf(b).alpha = 0.4f
        display.finish(c)
        // The first back ended: B is the second one's, not put to rest by the first.
        assertEquals(0.4f, display.valuesOf(b).alpha)
        display.finish(b)
        assertEquals(listOf("A"), h.shown())
    }

    // Elements of a `setStackRoot` play as at a push and wait the same way: within what is left of its 0.5 s without
    // `waitForRender`.
    @Test
    fun aSetStackRootWithElementsWaitsWithinItsLimit() {
        val a = h.screen("A")
        h.start(a)
        val c = h.screen("C", ready = false, options = h.options { withElements(animations.setStackRoot) })
        h.setStackRoot(c)
        h.assertViews("placeWaiting:C")
        h.scheduler.advance(StackQueue.SET_STACK_ROOT_WAIT_MILLIS)
        // The content's limit passed: the targets get nothing of it, their wait ends at once.
        h.assertViews("awaitTargets:A>C")
        assertFalse(display.targetsWaiting(c))
        display.findTargets(c)
        assertEquals(listOf("Custom:C:- fade-in elements:A>C:0.0", "Custom:A:-:1.0"), display.starts)
        display.finishAll()
        assertEquals(0, h.scheduler.pending)
    }

    // A `setStackRoot` of its own: the new top's `content.enter`, the old top's `content.exit`, the other old screens
    // hidden at once; the row at once, not the fade through's picture.
    @Test
    fun aSetStackRootOfItsOwn() {
        val a = h.screen("A")
        val b = h.screen("B")
        h.start(a, b)
        display.raise(a)
        h.clear()
        val c = h.screen("C", options = h.options {
            animations.setStackRoot.content.enter.alpha.from = Fraction(0.0)
            animations.setStackRoot.content.exit.scaleX.to = Fraction(0.9)
        })
        h.setStackRoot(c)
        assertEquals(listOf("Custom:C:alpha:1.0", "Custom:B:scaleX:1.0"), display.starts)
        assertTrue(h.views.contains("hide:A"))
        assertEquals(listOf("none"), h.host.barMotions)
        assertEquals(listOf("Forward"), h.host.barTravels)
        display.finishAll()
        assertTrue(h.journal.any { it.startsWith("setStackRoot1 resolve C") })
    }

    // `asPush` plays the new top's push animation — its own `content` wins over `setStackRoot`'s — without elements.
    @Test
    fun asPushPlaysThePushWithoutElements() {
        val a = h.screen("A")
        h.start(a)
        val c = h.screen("C", options = h.options {
            fadeIn(this)
            withElements(animations.push)
            animations.setStackRoot.content.enter.scaleX.from = Fraction(0.5)
        })
        h.setStackRoot(c, asPush = true)
        assertFalse(h.views.any { it.startsWith("awaitTargets") })
        assertEquals("Custom:C:alpha:1.0", display.starts.first())
        assertEquals(listOf("none"), h.host.barMotions)
        // A push without its own `content` is the system's push.
        display.finishAll()
        display.starts.clear()
        val d = h.screen("D", options = h.options { withElements(animations.push) })
        h.setStackRoot(d, asPush = true)
        assertEquals(listOf("PushIn:D:0.0:96.0", "PushCover:C:1.0:0.0"), display.starts)
    }

    // `animations.setStackRoot.enabled`: `false` sets the screens at once, without the wait for rendering.
    @Test
    fun aSetStackRootTurnedOffDoesntWait() {
        h.start(h.screen("A"))
        val c = h.screen("C", ready = false)
        h.setStackRoot(c, animated = false)
        assertFalse(h.views.contains("placeWaiting:C"))
        assertTrue(h.journal.any { it.startsWith("setStackRoot1 resolve C") })
    }

    // The bars' sides go with the transition: the arriving screen's `animations.push.topBar` and `.bottomTabs`.
    @Test
    fun theBarsSidesGoWithTheTransition() {
        h.start(h.screen("A"))
        h.push(h.screen("B", options = h.options {
            animations.push.topBar.exit.translationY.to = Fraction(-56.0)
            animations.push.bottomTabs.exit.alpha.to = Fraction(0.0)
        }))
        assertEquals(listOf("side"), h.host.barVisibilities)
        assertEquals(listOf("Slide"), h.host.tabBarMoves)
        assertEquals(listOf(true), h.host.tabBarSides)
        // The row still rides with the system's push: the bars' sides don't make the transition own.
        assertEquals(listOf("Push"), h.host.barMotions)
        assertEquals(listOf("none"), h.host.barTravels)
        assertEquals(listOf("PushIn:B:0.0:96.0", "PushCover:A:1.0:0.0"), display.starts)
    }
}

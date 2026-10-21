package dev.apexnative.navigation.viewcontrollers.component

import android.app.Activity
import dev.apexnative.navigation.react.events.FakeScheduler
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The core's one wait for screens to render ([RenderWait]): its limit and what it marks, the bars' components within
 * the limit or after the content, the rule for content of unknown readiness, a retake of its screens, its cancel, the
 * delivery of the readiness signals, and the screens a wait for a layout waits for.
 */
class RenderWaitTest {
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val scheduler = FakeScheduler()
    private val readiness = Signals()
    private var ended = 0

    private fun screen(id: String) = TestScreen(activity, registry, id)

    private fun wait(
        limit: Long?,
        bar: RenderWait.Bar = RenderWait.Bar.WithinLimit,
        unknown: Boolean = false,
        deliver: (() -> Unit) -> Unit = { it() },
    ) = RenderWait(readiness, scheduler, limit, bar, unknown, deliver, onEnd = { ended++ })

    @Test
    fun readyScreensEndTheWaitAtOnceWithoutATimer() {
        val home = screen("Home")
        wait(limit = 500).start(listOf(home))
        assertEquals(1, ended)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun theLimitMarksOnlyTheContentNotReadyAndEndsOnce() {
        val home = screen("Home")
        val profile = screen("Profile")
        readiness.notReady += "Profile"
        val wait = wait(limit = 500)
        wait.start(listOf(home, profile))
        scheduler.advance(499)
        assertEquals(0, ended)
        scheduler.advance(1)
        assertEquals(listOf("Profile"), readiness.marked)
        assertEquals(1, ended)
        assertFalse(wait.isWaiting)
        readiness.mount("Profile")
        assertEquals(1, ended)
    }

    @Test
    fun theBarsWithinTheLimitEndTheWaitWhenTheyDraw() {
        val home = screen("Home")
        readiness.barsWaiting += "Home"
        wait(limit = 500).start(listOf(home))
        assertEquals(0, ended)
        readiness.drawBar("Home")
        assertEquals(1, ended)
        assertEquals(0, scheduler.pending)
        assertTrue(readiness.marked.isEmpty())
    }

    @Test
    fun theBarsAfterTheContentGetHalfASecondAndMarkNothing() {
        val home = screen("Home")
        readiness.notReady += "Home"
        readiness.barsWaiting += "Home"
        wait(limit = null, bar = RenderWait.Bar.AfterContent).start(listOf(home))
        assertEquals(0, scheduler.pending)
        scheduler.advance(10_000)
        readiness.mount("Home")
        scheduler.advance(RenderWait.BAR_PHASE_MILLIS - 1)
        assertEquals(0, ended)
        scheduler.advance(1)
        assertEquals(1, ended)
        assertTrue(readiness.marked.isEmpty())
    }

    @Test
    fun contentOfUnknownReadinessCountsAfterOneSecondWhenTheRuleIsOn() {
        val home = screen("Home")
        readiness.notReady += "Home"
        readiness.unknown += "Home"
        wait(limit = null, bar = RenderWait.Bar.AfterContent, unknown = true).start(listOf(home))
        scheduler.advance(RenderWait.UNKNOWN_CONTENT_MILLIS - 1)
        assertEquals(0, ended)
        scheduler.advance(1)
        assertEquals(listOf("Home"), readiness.marked)
        assertEquals(1, ended)
    }

    @Test
    fun withoutTheRuleAWaitWithoutALimitHasNoTimer() {
        val home = screen("Home")
        readiness.notReady += "Home"
        readiness.unknown += "Home"
        wait(limit = null, bar = RenderWait.Bar.AfterContent).start(listOf(home))
        assertEquals(0, scheduler.pending)
        scheduler.advance(10_000)
        assertEquals(0, ended)
    }

    @Test
    fun contentThatReportedGetsNoOneSecondRule() {
        val home = screen("Home")
        readiness.notReady += "Home"
        wait(limit = null, bar = RenderWait.Bar.AfterContent, unknown = true).start(listOf(home))
        scheduler.advance(10_000)
        assertEquals(0, ended)
        assertTrue(readiness.marked.isEmpty())
    }

    @Test
    fun aRetakeWaitsForTheNewScreensWithinTheLimitThatRuns() {
        val home = screen("Home")
        val details = screen("Details")
        readiness.notReady += listOf("Home", "Details")
        val wait = wait(limit = 500)
        wait.start(listOf(home))
        scheduler.advance(300)
        wait.retake(listOf(details))
        // The screen that went is no longer waited for.
        readiness.mount("Home")
        assertEquals(0, ended)
        scheduler.advance(200)
        assertEquals(listOf("Details"), readiness.marked)
        assertEquals(1, ended)
    }

    @Test
    fun aRetakeOfReadyScreensEndsTheWait() {
        val home = screen("Home")
        val details = screen("Details")
        readiness.notReady += "Home"
        val wait = wait(limit = 500)
        wait.start(listOf(home))
        wait.retake(listOf(details))
        assertEquals(1, ended)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun aRetakeKeepsTheBarPhaseThatRuns() {
        val home = screen("Home")
        val details = screen("Details")
        readiness.barsWaiting += "Home"
        readiness.notReady += "Details"
        val wait = wait(limit = null, bar = RenderWait.Bar.AfterContent)
        wait.start(listOf(home))
        scheduler.advance(300)
        wait.retake(listOf(details))
        scheduler.advance(RenderWait.BAR_PHASE_MILLIS - 300)
        assertEquals(1, ended)
    }

    @Test
    fun aCancelledWaitEndsNeverAndLeavesNoTimer() {
        val home = screen("Home")
        readiness.notReady += "Home"
        val wait = wait(limit = 500)
        wait.start(listOf(home))
        wait.cancel()
        assertEquals(0, scheduler.pending)
        readiness.mount("Home")
        scheduler.advance(1_000)
        assertEquals(0, ended)
        assertTrue(readiness.marked.isEmpty())
    }

    @Test
    fun theSignalsComeThroughTheDelivery() {
        val home = screen("Home")
        readiness.notReady += "Home"
        val posted = mutableListOf<() -> Unit>()
        wait(limit = null, bar = RenderWait.Bar.AfterContent, deliver = { posted += it }).start(listOf(home))
        readiness.mount("Home")
        assertEquals(0, ended)
        posted.toList().forEach { it() }
        assertEquals(1, ended)
    }

    @Test
    fun aWaitForALayoutWaitsForTheWaitedChildrenAtEveryLevel() {
        val root = TestLayout(activity, registry, "Root")
        val tabs = object : TestLayout(activity, registry, "Tabs") {
            override fun waitedChildren(): List<ViewController<*>> = children.toList()
        }
        val home = screen("Home")
        val stack = TestLayout(activity, registry, "Stack1")
        val profile = screen("Profile")
        val settings = screen("Settings")
        root.add(tabs)
        tabs.add(home)
        tabs.add(stack)
        stack.add(profile)
        stack.add(settings)
        assertEquals(listOf("Home", "Settings"), RenderWait.waitedScreens(root).map { it.id })
        assertEquals(listOf("Settings"), RenderWait.waitedScreens(stack).map { it.id })
        assertEquals(listOf("Home"), RenderWait.waitedScreens(home).map { it.id })
    }

    // Screens are ready unless listed; [mount] and [drawBar] make one ready and tell who waits.
    // The screens made with the layer that don't show (the unselected tabs of `together`).

    @Test
    fun unseenScreensCountWithinHalfASecondAlsoWithoutALimit() {
        val home = screen("Home")
        val profile = screen("Profile")
        readiness.notReady += "Profile"
        wait(limit = null, bar = RenderWait.Bar.AfterContent).start(listOf(home), unseen = listOf(profile))
        scheduler.advance(RenderWait.UNSEEN_LIMIT_MILLIS - 1)
        assertEquals(0, ended)
        scheduler.advance(1)
        assertEquals(listOf("Profile"), readiness.marked)
        assertEquals(1, ended)
    }

    @Test
    fun unseenScreensReadyInTimeEndTheWaitWithTheShownOnes() {
        val home = screen("Home")
        val profile = screen("Profile")
        readiness.notReady += "Profile"
        wait(limit = 500).start(listOf(home), unseen = listOf(profile))
        assertEquals(0, ended)
        readiness.mount("Profile")
        assertEquals(1, ended)
        assertTrue(readiness.marked.isEmpty())
    }

    @Test
    fun theLimitOfTheWaitMarksTheUnseenToo() {
        val home = screen("Home")
        val profile = screen("Profile")
        readiness.notReady += "Home"
        readiness.notReady += "Profile"
        wait(limit = 500).start(listOf(home), unseen = listOf(profile))
        scheduler.advance(500)
        assertEquals(listOf("Home", "Profile"), readiness.marked)
        assertEquals(1, ended)
    }

    @Test
    fun theBarsOfUnseenScreensDontCount() {
        val home = screen("Home")
        val profile = screen("Profile")
        readiness.barsWaiting += "Profile"
        wait(limit = 500).start(listOf(home), unseen = listOf(profile))
        assertEquals(1, ended)
    }

    @Test
    fun theShownScreensStillHoldAWaitWithoutALimitAfterTheUnseen() {
        val home = screen("Home")
        val profile = screen("Profile")
        readiness.notReady += "Home"
        readiness.notReady += "Profile"
        wait(limit = null, bar = RenderWait.Bar.AfterContent).start(listOf(home), unseen = listOf(profile))
        scheduler.advance(10_000)
        assertEquals(listOf("Profile"), readiness.marked)
        assertEquals(0, ended)
        readiness.mount("Home")
        assertEquals(1, ended)
    }

    private class Signals : Readiness {
        val notReady = mutableSetOf<String>()
        val unknown = mutableSetOf<String>()
        val barsWaiting = mutableSetOf<String>()
        val marked = mutableListOf<String>()
        private val content = mutableMapOf<String, MutableList<() -> Unit>>()
        private val bars = mutableMapOf<String, MutableList<() -> Unit>>()

        fun mount(id: String) {
            notReady -= id
            content.remove(id)?.forEach { it() }
        }

        fun drawBar(id: String) {
            barsWaiting -= id
            bars.remove(id)?.forEach { it() }
        }

        override fun isReady(screen: ViewController<*>): Boolean = screen.id !in notReady

        override fun whenReady(screen: ViewController<*>, listener: () -> Unit) {
            if (isReady(screen)) listener() else content.getOrPut(screen.id) { mutableListOf() } += listener
        }

        override fun markReady(screen: ViewController<*>) {
            marked += screen.id
        }

        override fun isRenderUnknown(screen: ViewController<*>): Boolean = screen.id in unknown

        override fun areBarComponentsReady(screen: ViewController<*>): Boolean = screen.id !in barsWaiting

        override fun whenBarComponentsReady(screen: ViewController<*>, listener: () -> Unit) {
            if (areBarComponentsReady(screen)) listener() else bars.getOrPut(screen.id) { mutableListOf() } += listener
        }
    }
}

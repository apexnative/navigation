package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What a stack does with its screens beside its commands: the lower screens of a stack made with several make their
 * content in the window after the top appeared; the push of a screen with its own root behind its switch; the options
 * its screens give the layouts around, without the bar, the animations and the FAB; which screen measures its safe
 * area.
 */
class StackScreensTest {
    private val h = StackQueueHarness()

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    // Lower screens.

    @Test
    fun theLowerScreensMakeTheirContentAfterTheTopAppeared() {
        val a = h.screen("A")
        val b = h.screen("B", ready = false)
        h.start(a, b, h.screen("C"))
        assertTrue(h.views.isEmpty())
        h.stack.onViewDidAppear()
        // A is ready at once and goes out; B stays placed until its content is ready or the limit.
        h.assertViews("placeLower:A", "detach:A", "placeLower:B")
        h.scheduler.advance(StackQueue.LOWER_SCREEN_MILLIS)
        h.assertViews("detach:B")
        // They wait for nobody: nothing is marked ready.
        assertTrue(h.events().none { it.startsWith("markReady") })
        assertEquals(0, h.scheduler.pending)
    }

    @Test
    fun aLowerScreenOutAsItsContentIsReady() {
        val b = h.screen("B", ready = false)
        h.start(b, h.screen("C"))
        h.stack.onViewDidAppear()
        h.assertViews("placeLower:B")
        b.content.mount()
        h.assertViews("detach:B")
        assertEquals(0, h.scheduler.pending)
        // Only once.
        h.stack.onViewDidAppear()
        assertTrue(h.views.isEmpty())
    }

    @Test
    fun aPopThatReachesAPlacedLowerScreenEndsItsWait() {
        val b = h.screen("B", ready = false)
        h.start(h.screen("A"), b, h.screen("C"))
        h.stack.onViewDidAppear()
        h.clear()
        h.pop()
        h.display.finishAll()
        h.scheduler.advance(StackQueue.LOWER_SCREEN_MILLIS)
        // B is the top now: its placement ended, and it stays in the container.
        assertFalse(h.views.contains("detach:B"))
        assertTrue(b in h.display.inContainer)
    }

    @Test
    fun theLowerScreensWaitForTheStackToShow() {
        h.start(h.screen("A"), h.screen("B"))
        h.display.isOnScreen = false
        h.stack.onViewDidAppear()
        assertTrue(h.views.isEmpty())
        h.display.isOnScreen = true
        h.stack.onViewDidAppear()
        h.assertViews("placeLower:A", "detach:A")
    }

    // Options up the layouts.

    @Test
    fun onlyTheTopsOptionsGoUpWithoutTheStacksGroups() {
        val around = Around(h)
        h.stack.parentController = around
        val a = h.screen("A")
        val b = h.screen("B")
        h.start(a, b)
        val options = Options().apply {
            topBar.title.text = Text("Profile")
            fab.id = Text("add")
            animations.push.enabled = Bool(false)
            bottomTab.text = Text("Inbox")
        }
        h.stack.applyChildOptions(options, a)
        assertTrue(around.applied.isEmpty())
        h.stack.applyChildOptions(options, b)
        val up = around.applied.single()
        assertFalse(up.topBar.title.text.hasValue())
        assertFalse(up.fab.id.hasValue())
        assertFalse(up.animations.push.enabled.hasValue())
        assertEquals("Inbox", up.bottomTab.text.get())
    }

    @Test
    fun aMergeWithTheStacksIdKeepsItsGroupsAtTheStack() {
        val around = Around(h)
        h.stack.parentController = around
        h.start(h.screen("A"))
        h.stack.mergeOptions(Options().apply {
            fab.id = Text("add")
            bottomTab.badge = Text("1")
        })
        assertEquals("add", h.stack.options.fab.id.get())
        val up = around.merged.single()
        assertFalse(up.fab.id.hasValue())
        assertTrue(up.bottomTab.badge.hasValue())
    }

    @Test
    fun aMergeIntoAHiddenStackAppliesAsItsTopShows() {
        val a = h.screen("A")
        h.start(a)
        h.calls.clear()
        // The stack doesn't show (a tab not selected): the merges with its id and into its top wait for its show.
        h.stack.mergeOptions(Options().apply { topBar.height = Fraction(80.0) })
        a.mergeOptions(Options().apply { fab.id = Text("add") })
        assertTrue(h.calls.isEmpty())
        h.stack.applyChildOptions(Options(), a)
        assertEquals(listOf("topInset:A"), h.calls)
        // Once.
        h.calls.clear()
        h.stack.applyChildOptions(Options(), a)
        assertTrue(h.calls.isEmpty())
    }

    @Test
    fun theKeysOfEntriesThatLeftGo() {
        val real = StackQueueHarness(realHost = true)
        real.install()
        try {
            real.start(real.screen("A"))
            val b = real.screen("B")
            real.push(b, key = "Details:k")
            real.display.finishAll()
            assertEquals("Details:k", real.stack.keyOf(b))
            real.pop()
            real.display.finishAll()
            assertEquals(null, real.stack.keyOf(b))
        } finally {
            real.uninstall()
        }
    }

    @Test
    fun theTopAndTheWaitingScreenMeasureTheirSafeArea() {
        val a = h.screen("A")
        h.start(a)
        val b = h.waiting("B")
        h.push(b)
        assertTrue(h.stack.isTopOfStack(a))
        assertTrue(h.stack.isTopOfStack(b))
        b.content.mount()
        assertFalse(h.stack.isTopOfStack(a))
        h.display.finishAll()
    }

    @Test
    fun theStacksNodesStartWithItsEntries() {
        h.start(h.screen("A"), h.screen("B"))
        h.push(h.waiting("C"))
        assertEquals(listOf("A", "B", "C"), h.stack.childControllers.map { it.id })
        assertEquals("B", h.stack.currentChild?.id)
        h.stack.destroy()
    }

    // The layout around the stack: it records what its child gives it.
    private class Around(h: StackQueueHarness) : TestLayout(h.activity, h.registry, "Tabs1") {
        val applied = mutableListOf<Options>()
        val merged = mutableListOf<Options>()

        override fun applyChildOptions(options: Options, child: ViewController<*>) {
            applied += options
        }

        override fun mergeChildOptions(options: Options, child: ViewController<*>) {
            merged += options
        }
    }
}

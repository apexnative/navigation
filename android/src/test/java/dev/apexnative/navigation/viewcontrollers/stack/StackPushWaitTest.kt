package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.ElementTransitions
import dev.apexnative.navigation.options.Options
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
 * The wait of a push for its screen's first mount: without `animations.push.waitForRender` it renders hidden under the
 * top, [StackQueue.PUSH_WAIT_MILLIS] at most; `false` starts at once, and so do a stack off screen and a screen without
 * React content. The mount reaches the push behind the calls JavaScript made before it (`PendingCalls`), content
 * mounted already too: a `mergeOptions` of the same commit applies before the transition starts. The targets of
 * elements share the 0.5 s; a pop, a `setStackRoot` or a destroy during the wait leaves nothing for a late signal.
 */
class StackPushWaitTest {
    private val h = StackQueueHarness()

    @Before
    fun install() {
        h.install()
        h.host.reactContent = true
    }

    @After
    fun uninstall() = h.uninstall()

    private fun assertSettled() {
        assertTrue(h.queue.isAtRest)
        assertEquals(0, h.scheduler.pending)
    }

    @Test
    fun withoutWaitForRenderAPushWaitsForTheFirstMount() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false)
        h.push(b)
        h.assertViews("placeWaiting:B")
        assertTrue(h.events().isEmpty())
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS - 1)
        assertTrue(h.events().isEmpty())
        b.content.mount()
        h.assertEvents("will:B")
        h.assertViews("raise:B")
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun withoutWaitForRenderAPushStartsAtTheLimit() {
        h.start(h.screen("A"))
        h.push(h.screen("B", ready = false))
        h.assertViews("placeWaiting:B")
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS)
        h.assertEvents("markReady:B", "will:B")
        h.assertViews("raise:B")
        h.display.finishAll()
        assertSettled()
    }

    // Content mounted already still goes behind the calls: a `mergeOptions` of its commit may be on its way.
    @Test
    fun contentReadyAtOnceStartsBehindTheCalls() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        h.push(h.screen("B"))
        h.assertViews("placeWaiting:B")
        assertTrue(h.events().isEmpty())
        h.runCalls()
        h.assertViews("raise:B")
        h.assertEvents("will:B")
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun contentReadyAtOnceStartsAtTheLimitWhileTheCallsDontComeBack() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        h.push(h.screen("B"))
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS - 1)
        h.assertViews("placeWaiting:B")
        h.scheduler.advance(1)
        // Mounted already: nothing to mark.
        h.assertEvents("will:B")
        h.assertViews("raise:B")
        h.runCalls()
        assertTrue(h.views.isEmpty())
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun waitForRenderTrueWithContentReadyAtOnceStartsBehindTheCalls() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        h.push(h.waiting("B", ready = true))
        h.assertViews("placeWaiting:B")
        h.runCalls()
        h.assertViews("raise:B")
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun aNativeScreenReadyAtOnceStartsInTheSameTurn() {
        h.host.reactContent = false
        h.holdsCalls = true
        h.start(h.screen("A"))
        h.push(h.waiting("B", ready = true))
        h.assertViews("placeWaiting:B", "raise:B")
        assertTrue(h.heldCalls.isEmpty())
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun waitForRenderFalseStartsAtOnce() {
        h.start(h.screen("A"))
        h.push(h.screen("B", ready = false, options = h.options { animations.push.waitForRender = Bool(false) }))
        h.assertViews("raise:B")
        h.display.finishAll()
        // The dispatcher holds its events until its content mounts: only the queue is at rest.
        assertTrue(h.queue.isAtRest)
    }

    @Test
    fun aStackOffScreenDoesNotWait() {
        h.start(h.screen("A"))
        h.display.isOnScreen = false
        h.push(h.screen("B", ready = false))
        assertEquals(listOf("A", "B"), h.shown())
        assertTrue("placeWaiting:B" !in h.views)
        assertSettled()
    }

    @Test
    fun aScreenWithoutReactContentStartsAtOnce() {
        h.host.reactContent = false
        h.start(h.screen("A"))
        h.push(h.screen("B", ready = false))
        h.assertViews("raise:B")
        h.display.finishAll()
        // The dispatcher holds its events until its content mounts: only the queue is at rest.
        assertTrue(h.queue.isAtRest)
    }

    @Test
    fun theMergeOfTheMountsCommitAppliesBeforeTheStart() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false)
        val titles = mutableListOf<String?>()
        h.host.whenCurrentChildChanged = {
            titles += h.queue.currentChild?.resolveCurrentOptions(DefaultOptions.current)?.topBar?.title?.text?.get()
        }
        h.push(b)
        h.assertViews("placeWaiting:B")
        // The mount comes in the turn before the commit's `mergeOptions` reached the main thread: it waits behind it.
        b.content.mount()
        assertTrue(h.views.isEmpty())
        assertTrue(h.events().isEmpty())
        b.mergeOptions(Options().apply { topBar.title.text = Text("Saved: 8") })
        h.runCalls()
        h.assertViews("raise:B")
        h.assertEvents("will:B")
        assertEquals(listOf<String?>("Saved: 8"), titles)
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun theLimitHoldsWhileTheMountWaitsBehindTheCalls() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false)
        h.push(b)
        b.content.mount()
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS)
        h.assertViews("placeWaiting:B", "raise:B")
        h.assertEvents("will:B")
        // The calls come back after the start: nothing more moves.
        h.runCalls()
        assertTrue(h.views.isEmpty())
        h.display.finishAll()
        assertSettled()
    }

    // Elements: their targets take what is left of the 0.5 s.

    private fun withElements() = h.options {
        animations.push.elementTransitions = ElementTransitions(listOf(ElementTransitionOptions("Avatar").apply {
            alpha.from = Fraction(0.0)
        }))
    }

    @Test
    fun theTargetsShareThePushLimit() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false, options = withElements())
        h.push(b)
        h.scheduler.advance(300)
        b.content.mount()
        h.assertViews("placeWaiting:B", "awaitTargets:A>B")
        assertTrue(h.display.targetsWaiting(b))
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS - 301)
        assertTrue(h.display.targetsWaiting(b))
        h.scheduler.advance(1)
        assertFalse(h.display.targetsWaiting(b))
        h.display.findTargets(b)
        h.assertEvents("will:B")
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun atTheContentsLimitTheTargetsGetNothing() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false, options = withElements())
        h.push(b)
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS)
        h.assertEvents("markReady:B")
        h.assertViews("placeWaiting:B", "awaitTargets:A>B")
        assertFalse(h.display.targetsWaiting(b))
        h.display.findTargets(b)
        h.assertEvents("will:B")
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun aTargetsWaitThatEndsInTimeLeavesNoLimit() {
        h.start(h.screen("A"))
        val b = h.screen("B", options = withElements())
        h.push(b)
        h.display.findTargets(b)
        h.display.finishAll()
        assertSettled()
    }

    // A pop, a `setStackRoot` or a destroy during the wait: the push ends, and the signal that comes late (the mount,
    // or the calls it waited behind) moves nothing.

    private fun assertNothingLate(b: QueueScreen) {
        h.clear()
        b.content.mount()
        h.runCalls()
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS)
        assertTrue(h.views.isEmpty())
        assertTrue(h.events().isEmpty())
        assertTrue(h.display.starts.isEmpty())
    }

    private fun waitingPush(mounted: Boolean): QueueScreen {
        h.holdsCalls = true
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false)
        h.push(b)
        if (mounted) b.content.mount()
        h.assertViews("placeWaiting:B")
        return b
    }

    private fun popDuring(mounted: Boolean) {
        val b = waitingPush(mounted)
        h.pop(b)
        assertTrue(h.journal.contains("push1 resolve B"))
        assertFalse(h.journal.contains("will:B"))
        assertSettled()
        assertNothingLate(b)
    }

    @Test
    fun aPopDuringTheLimitedWait() = popDuring(mounted = false)

    @Test
    fun aPopWhileTheMountWaitsBehindTheCalls() = popDuring(mounted = true)

    private fun setStackRootDuring(mounted: Boolean) {
        val b = waitingPush(mounted)
        h.setStackRoot(h.screen("C"))
        assertTrue(h.journal.contains("push1 resolve B"))
        assertFalse(h.journal.contains("will:B"))
        h.display.finishAll()
        assertTrue(h.journal.any { it.startsWith("setStackRoot2 resolve C") })
        assertEquals(listOf("C"), h.shown())
        assertSettled()
        assertNothingLate(b)
        assertEquals(listOf("C"), h.shown())
    }

    @Test
    fun aSetStackRootDuringTheLimitedWait() = setStackRootDuring(mounted = false)

    @Test
    fun aSetStackRootWhileTheMountWaitsBehindTheCalls() = setStackRootDuring(mounted = true)

    private fun destroyDuring(mounted: Boolean) {
        val b = waitingPush(mounted)
        h.stack.destroy()
        assertTrue(h.journal.any { it.startsWith("push1 reject") })
        assertFalse(h.journal.contains("will:B"))
        assertEquals(0, h.scheduler.pending)
        assertNothingLate(b)
    }

    @Test
    fun aDestroyDuringTheLimitedWait() = destroyDuring(mounted = false)

    @Test
    fun aDestroyWhileTheMountWaitsBehindTheCalls() = destroyDuring(mounted = true)

    // One tree: a prepared push (`preparePush`) waits for its slot's mount, then behind the calls.

    private fun pushPrepared(): QueueScreen {
        val b = h.screen("B", ready = false, slot = true).apply { isPreparedPush = true }
        h.prepared.add(b, h.stack)
        val layout = h.layout("B", name = "slot:Details")
        h.commands.pushChecked(h.stack, StackQueueHarness.STACK, layout, h.answer("push"), "push")
        h.assertViews("awaitPrepared:B")
        return b
    }

    @Test
    fun aPreparedPushStartsBehindTheCallsAfterItsMount() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        val b = pushPrepared()
        h.display.mountPrepared(b)
        h.assertEvents("markReady:B")
        assertTrue(h.views.isEmpty())
        h.runCalls()
        h.assertEvents("will:B")
        h.assertViews("raise:B")
        h.display.finishAll()
        assertSettled()
    }

    @Test
    fun aPreparedPushStartsAtTheLimitWhileTheCallsDontComeBack() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        val b = pushPrepared()
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS - 100)
        h.display.mountPrepared(b)
        h.scheduler.advance(100)
        h.assertEvents("markReady:B", "will:B")
        h.assertViews("raise:B")
        h.runCalls()
        assertTrue(h.views.isEmpty())
        h.display.finishAll()
        assertSettled()
    }

    // The calls come back before the cap: the transition starts then, the cap goes with the wait.
    @Test
    fun aPreparedPushsCapAfterTheCallsDoesNothing() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        val b = pushPrepared()
        h.display.mountPrepared(b)
        h.runCalls()
        h.assertEvents("markReady:B", "will:B")
        h.assertViews("raise:B")
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS)
        assertTrue(h.views.isEmpty())
        assertTrue(h.events().isEmpty())
        h.display.finishAll()
        assertSettled()
    }

    // The cap and the display's own limit come together while the calls are held: one start, and the start that comes
    // back from the calls later does nothing.
    @Test
    fun aPreparedPushsCapWithTheDisplaysLimit() {
        h.holdsCalls = true
        h.start(h.screen("A"))
        pushPrepared()
        h.scheduler.advance(StackQueue.PUSH_WAIT_MILLIS)
        h.assertEvents("markReady:B", "will:B")
        h.assertViews("raise:B")
        h.runCalls()
        assertTrue(h.views.isEmpty())
        assertTrue(h.events().isEmpty())
        h.display.finishAll()
        assertSettled()
    }
}

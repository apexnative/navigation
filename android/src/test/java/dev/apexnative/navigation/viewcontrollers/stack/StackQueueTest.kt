package dev.apexnative.navigation.viewcontrollers.stack

import androidx.activity.OnBackPressedCallback
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.stack.StackQueueHarness.Companion.STACK
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The queue of a stack cell by cell: at rest, while a push or a `setStackRoot` waits, while Android's transitions
 * overlap, and over every state (off screen, without a view, destroyed, reloaded, failing). Each checks the order of
 * what reaches JavaScript with the real answers (`WillAppear` new → `DidDisappear` old → `DidAppear` new → state →
 * `ScreenPopped` top to bottom → the destroys → the promise → `CommandCompleted`), and that nothing hangs: every
 * command answered once, no timer left, every view at rest.
 */
class StackQueueTest {
    private val h = StackQueueHarness()
    private val display = h.display
    private val queue: StackQueue
        get() = h.queue

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    // Nothing waits, nothing moves, every view of what shows is at rest, every command answered once.
    private fun assertSettled() {
        assertTrue("animators left: ${display.animating.map { it.id }}", display.animating.isEmpty())
        assertEquals("timers left", 0, h.scheduler.pending)
        assertTrue("not at rest", queue.isAtRest)
        for (screen in display.inContainer) {
            assertEquals("alpha of ${screen.id}", 1f, display.valuesOf(screen).alpha)
            assertEquals("translation of ${screen.id}", 0f, display.valuesOf(screen).translation)
        }
        val answers = h.journal.filter { " resolve " in it || " reject " in it }.map { it.substringBefore(' ') }
        assertEquals("answered twice: $answers", answers.distinct(), answers)
    }

    // At rest.

    @Test
    fun case01RestPush() {
        val a = h.screen("A")
        h.start(a)
        val b = h.screen("B")
        h.push(b)
        h.assertEvents("will:B")
        h.assertViews("raise:B")
        assertEquals(listOf("PushIn:B:0.0:96.0", "PushCover:A:1.0:0.0"), display.starts)
        display.finishAll()
        h.assertEvents("didDisappear:A", "didAppear:B", "state", "push1 resolve B", "completed:push")
        // The covered screen leaves the container at the end, not before.
        h.assertViews("detach:A")
        assertEquals(listOf("A", "B"), h.shown())
        assertSettled()
    }

    @Test
    fun case02RestPushWithoutAnimation() {
        h.start(h.screen("A"))
        h.push(h.screen("B", options = h.options { animations.push.enabled = Bool(false) }))
        h.assertEvents(
            "will:B", "didDisappear:A", "didAppear:B", "state", "push1 resolve B", "completed:push",
        )
        assertTrue(display.starts.isEmpty())
        assertSettled()
    }

    @Test
    fun case03RestPop() {
        val b = h.screen("B")
        h.start(h.screen("A"), b)
        h.pop()
        h.assertEvents("will:A")
        h.assertViews("reveal:A")
        assertEquals(listOf("leave:B", "front:A"), h.calls)
        assertEquals(listOf("PopOut:B:1.0:0.0", "PopReveal:A:1.0:-96.0"), display.starts)
        display.finishAll()
        h.assertEvents(
            "didDisappear:B", "didAppear:A", "state", "popped:B", "destroyed:B", "pop1 resolve B",
            "completed:pop",
        )
        assertTrue(b.isDestroyed)
        assertSettled()
    }

    @Test
    fun case04RestPopOfALowerScreen() {
        val b = h.screen("B")
        h.start(h.screen("A"), b, h.screen("C"))
        h.pop(from = b)
        h.assertViews("reveal:A", "hide:B")
        assertEquals(listOf("PopOut:C:1.0:0.0", "PopReveal:A:1.0:-96.0"), display.starts)
        display.finishAll()
        h.assertEvents(
            "will:A", "didDisappear:C", "didAppear:A", "state", "popped:C", "popped:B", "destroyed:C",
            "destroyed:B", "pop1 resolve B", "completed:pop",
        )
        assertEquals(listOf("A"), h.entries())
        assertSettled()
    }

    @Test
    fun case05RestPopToSeveral() {
        val a = h.screen("A")
        h.start(a, h.screen("B"), h.screen("C"), h.screen("D"))
        h.popTo(a)
        display.finishAll()
        h.assertEvents(
            "will:A", "didDisappear:D", "didAppear:A", "state", "popped:D", "popped:C", "popped:B",
            "destroyed:D", "destroyed:C", "destroyed:B", "popTo1 resolve A", "completed:popTo",
        )
        assertSettled()
    }

    @Test
    fun case06RestPopToRoot() {
        h.start(h.screen("A"), h.screen("B"), h.screen("C"))
        h.popToRoot()
        display.finishAll()
        h.assertEvents(
            "will:A", "didDisappear:C", "didAppear:A", "state", "popped:C", "popped:B", "destroyed:C",
            "destroyed:B", "popToRoot1 resolve A", "completed:popToRoot",
        )
        assertSettled()
    }

    @Test
    fun case07RestSetStackRoot() {
        h.start(h.screen("A"), h.screen("B"))
        val c = h.screen("C")
        val d = h.screen("D", ready = false)
        h.setStackRoot(c, d)
        h.assertViews("placeWaiting:D")
        assertTrue(h.events().isEmpty())
        // JavaScript sees the new stack at once.
        assertEquals(listOf("C", "D"), h.entries())
        d.content.mount()
        h.assertEvents("will:D")
        assertEquals(listOf("leave:A", "leave:B"), h.calls)
        assertEquals(listOf("FadeIn:D:0.0:0.0", "FadeOut:B:1.0:0.0"), display.starts)
        display.finishAll()
        h.assertEvents(
            "didDisappear:B", "didAppear:D", "state", "popped:B", "popped:A", "destroyed:B", "destroyed:A",
            "setStackRoot1 resolve D", "completed:setStackRoot",
        )
        // The lower new screen makes its content after the end, in the window.
        h.assertViews("raise:D", "remove:B", "placeLower:C", "detach:C")
        assertTrue(c.options.topBar.buttons.back.visible.isFalse)
        assertTrue(d.options.topBar.buttons.back.visible.isTrue)
        assertSettled()
    }

    @Test
    fun case08RestSetStackRootAsPush() {
        h.start(h.screen("A"))
        h.setStackRoot(h.screen("C"), asPush = true)
        assertEquals(listOf("PushIn:C:0.0:96.0", "PushCover:A:1.0:0.0"), display.starts)
        display.finishAll()
        h.assertEvents(
            "will:C", "didDisappear:A", "didAppear:C", "state", "popped:A", "destroyed:A",
            "setStackRoot1 resolve C", "completed:setStackRoot",
        )
        assertSettled()
    }

    @Test
    fun case09RestSetStackRootWithAnIdOfTheOldStack() {
        val old = h.screen("X")
        h.start(h.screen("A"), old)
        val new = h.screen("X")
        h.setStackRoot(new)
        display.finishAll()
        // No ScreenPopped for the old X: JavaScript would forget the new one; it goes last.
        h.assertEvents(
            "will:X", "didDisappear:X", "didAppear:X", "state", "popped:A", "destroyed:A", "destroyed:X",
            "setStackRoot1 resolve X", "completed:setStackRoot",
        )
        assertTrue(old.isDestroyed)
        assertSame(new, h.registry.find("X"))
        assertSettled()
    }

    @Test
    fun case10RestNoneOfPopToTheTop() {
        val b = h.screen("B")
        h.start(h.screen("A"), b)
        h.popTo(b)
        h.assertEvents("state", "popTo1 resolve B", "completed:popTo")
        assertTrue(display.starts.isEmpty())
        assertSettled()
    }

    @Test
    fun case11RestPopToRootOfOneScreen() {
        h.start(h.screen("A"))
        h.commands.popToRoot("A", h.answer("popToRoot"), "pop the stack of the screen 'A'")
        h.assertEvents("state", "popToRoot1 resolve A", "completed:popToRoot")
        assertSettled()
    }

    @Test
    fun case12RestKeyOnTheTop() {
        h.start(h.screen("A"), h.screen("B"), keys = listOf(null, "Details:k"))
        h.commands.push("B", h.layout("C", key = "Details:k"), h.answer("push"), "push the screen 'C'")
        h.assertEvents("state", "push1 resolve B", "completed:push")
        assertNull(h.registry.find("C"))
        assertSettled()
    }

    @Test
    fun case13RestKeyDeeper() {
        h.start(h.screen("A"), h.screen("B"), h.screen("C"), keys = listOf("Details:k", null, null))
        h.commands.push("C", h.layout("D", key = "Details:k"), h.answer("push"), "push the screen 'D'")
        assertEquals(listOf("PopOut:C:1.0:0.0", "PopReveal:A:1.0:-96.0"), display.starts)
        display.finishAll()
        h.assertEvents(
            "will:A", "didDisappear:C", "didAppear:A", "state", "popped:C", "popped:B", "destroyed:C",
            "destroyed:B", "push1 resolve A", "completed:push",
        )
        assertNull(h.registry.find("D"))
        assertSettled()
    }

    @Test
    fun case14RestPairLeavesWithBothScreens() {
        val pair = QueuePair(h.activity, h.registry, "P", h.journal, listOf(h.screen("p1"), h.screen("p2")))
        h.start(h.screen("A"))
        h.push(pair)
        display.finishAll()
        h.clear()
        h.pop()
        display.finishAll()
        val popped = h.events().filter { it.startsWith("popped:") || it.startsWith("destroyed:") }
        assertEquals(listOf("popped:p1", "popped:p2", "destroyed:p1", "destroyed:p2", "destroyed:P"), popped)
        assertSettled()
    }

    // Waiting: a push waits (B with `waitForRender: true`, not rendered, over A).

    @Test
    fun case15WaitingPushWaitsWithoutALimit() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        h.assertViews("placeWaiting:B")
        h.scheduler.advance(10_000)
        assertTrue(h.events().isEmpty())
        assertSame(b, queue.waitingScreen)
        b.content.mount()
        h.assertEvents("will:B")
        h.assertViews("raise:B")
        assertEquals("PushIn:B:0.0:96.0", display.starts.first())
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case16WaitingPushOfAScreenThatRendersNothing() {
        h.start(h.screen("A"))
        val b = h.waiting("B").apply { renderUnknown = true }
        h.push(b)
        h.scheduler.advance(StackQueue.NULL_CONTENT_MILLIS)
        h.assertEvents("markReady:B", "will:B")
        h.assertViews("placeWaiting:B", "raise:B")
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case17WaitingPushThenPush() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        h.push(h.screen("C"), from = "B")
        assertTrue(h.events().isEmpty())
        assertEquals(listOf("A", "B", "C"), h.entries())
        b.content.mount()
        h.assertEvents("will:B", "will:C")
        display.finishAll()
        h.assertEvents(
            "didDisappear:A", "state", "push1 resolve B", "completed:push",
            "didDisappear:B", "didAppear:C", "state", "push2 resolve C", "completed:push",
        )
        assertSettled()
    }

    @Test
    fun case18WaitingPushThenPop() {
        val a = h.screen("A")
        h.start(a)
        val b = h.waiting("B")
        h.push(b)
        h.pop()
        h.assertEvents(
            "destroyed:B", "state", "push1 resolve B", "completed:push", "state", "pop2 resolve B", "completed:pop",
        )
        h.assertViews("placeWaiting:B", "remove:B")
        assertFalse(a.isDestroyed)
        b.content.mount()
        assertTrue(h.events().isEmpty())
        assertSettled()
    }

    @Test
    fun case19WaitingPushThenBack() {
        h.start(h.screen("A"))
        h.push(h.waiting("B"))
        val settled = h.host.settled
        assertTrue(h.back())
        h.assertEvents("destroyed:B", "state", "push1 resolve B", "completed:push")
        assertTrue(h.host.settled > settled)
        assertSettled()
    }

    @Test
    fun case20WaitingPushThenPopToDeeper() {
        val x = h.screen("X")
        h.start(x, h.screen("A"))
        h.push(h.waiting("B"))
        h.popTo(x)
        h.assertEvents("destroyed:B", "state", "push1 resolve B", "completed:push", "will:X")
        assertEquals("PopOut:A:1.0:0.0", display.starts.first())
        display.finishAll()
        h.assertEvents(
            "didDisappear:A", "didAppear:X", "state", "popped:A", "destroyed:A", "popTo2 resolve X",
            "completed:popTo",
        )
        assertSettled()
    }

    @Test
    fun case21WaitingPushThenSetStackRoot() {
        h.start(h.screen("A"))
        h.push(h.waiting("B"))
        h.setStackRoot(h.screen("C"))
        display.finishAll()
        h.assertEvents(
            "destroyed:B", "state", "push1 resolve B", "completed:push", "will:C", "didDisappear:A", "didAppear:C",
            "state", "popped:A", "destroyed:A", "setStackRoot2 resolve C", "completed:setStackRoot",
        )
        assertSettled()
    }

    @Test
    fun case22WaitingPushThenTheSplitViewsSetRoot() {
        h.start(h.screen("A"))
        h.push(h.waiting("B"))
        h.splitSetRoot(h.screen("C"))
        // No wait and no animation for the split view.
        assertTrue(display.starts.isEmpty())
        h.assertEvents(
            "destroyed:B", "state", "push1 resolve B", "completed:push", "will:C", "didDisappear:A", "didAppear:C",
            "stateNow", "popped:A", "destroyed:A", "listener success C",
        )
        assertSettled()
    }

    @Test
    fun case23WaitingPushThenPopToItsScreen() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        h.popTo(b)
        assertTrue(h.events().isEmpty())
        b.content.mount()
        // The `none` answers as the push starts its transition, before the push's own end.
        h.assertEvents("will:B", "state", "popTo2 resolve B", "completed:popTo")
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case24WaitingPushThenAKeyOnTheTop() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b, key = "Details:k")
        h.commands.push("B", h.layout("C", key = "Details:k"), h.answer("push"), "push the screen 'C'")
        h.assertEvents("state", "push2 resolve B", "completed:push")
        assertNull(h.registry.find("C"))
        assertSame(b, queue.waitingScreen)
        b.content.mount()
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case25QueuedPushRemovedByBack() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        h.push(h.screen("C"), from = "B")
        assertTrue(h.back())
        h.assertEvents("destroyed:C", "state", "push2 resolve C", "completed:push")
        assertSame(b, queue.waitingScreen)
        b.content.mount()
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case26QueuedPushesRemovedByPopTo() {
        val a = h.screen("A")
        h.start(a)
        h.push(h.waiting("B"))
        h.push(h.screen("C"), from = "B")
        h.popTo(a)
        h.assertEvents(
            "destroyed:B", "state", "push1 resolve B", "completed:push",
            "destroyed:C", "state", "push2 resolve C", "completed:push",
            "state", "popTo3 resolve A", "completed:popTo",
        )
        assertTrue(display.starts.isEmpty())
        assertSettled()
    }

    @Test
    fun case27WaitingPushAndAHoldOfTheScreenSeen() {
        h.start(h.screen("A", options = h.options { hardwareBackButton.popStackOnPress = Bool(false) }))
        val b = h.waiting("B")
        h.push(b)
        assertTrue(h.back())
        h.assertEvents("pressed:A:ApexNavigation.hardwareBackButton")
        assertSame(b, queue.waitingScreen)
        b.content.mount()
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case28PreparedPushStartsAtTheLimit() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false, slot = true).apply { isPreparedPush = true }
        h.prepared.add(b, h.stack)
        h.commands.pushChecked(h.stack, STACK, h.layout("B", name = "slot:Details"), h.answer("push"), "push")
        h.assertViews("awaitPrepared:B")
        h.scheduler.advance(FakeStackDisplay.PREPARED_LIMIT)
        h.assertEvents("markReady:B", "will:B")
        h.assertViews("raise:B")
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case29PreparedPushInterrupted() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false, slot = true).apply { isPreparedPush = true }
        h.prepared.add(b, h.stack)
        h.commands.pushChecked(h.stack, STACK, h.layout("B", name = "slot:Details"), h.answer("push"), "push")
        // Pushed now: its destroy takes the slot's content away.
        b.isPreparedPush = false
        h.pop()
        h.assertEvents(
            "destroyed:B", "slotDestroyed:B", "state", "push1 resolve B", "completed:push", "state",
            "pop2 resolve B", "completed:pop",
        )
        // Its wait for the slot's mount and its pre-draw do nothing any more.
        h.scheduler.advance(FakeStackDisplay.PREPARED_LIMIT)
        assertTrue(h.events().isEmpty())
        assertSettled()
    }

    @Test
    fun case30CancelPreparedPushAfterThePush() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false, slot = true).apply { isPreparedPush = true }
        h.prepared.add(b, h.stack)
        h.commands.pushChecked(h.stack, STACK, h.layout("B", name = "slot:Details"), h.answer("push"), "push")
        h.commands.cancelPreparedPush("B")
        assertFalse(b.isDestroyed)
        assertSame(b, queue.waitingScreen)
        display.mountPrepared(b)
        display.finishAll()
        assertSettled()
    }

    // Waiting: a `setStackRoot` waits (R = [C, D] over [A, B], D not rendered).

    private fun waitingRoot(): Pair<QueueScreen, QueueScreen> {
        h.start(h.screen("A"), h.screen("B"))
        val c = h.screen("C")
        val d = h.screen("D", ready = false)
        h.setStackRoot(c, d)
        h.clear()
        return c to d
    }

    @Test
    fun case31WaitingSetStackRootAtItsLimit() {
        waitingRoot()
        h.scheduler.advance(StackQueue.SET_STACK_ROOT_WAIT_MILLIS)
        // The events held for D go at once.
        h.assertEvents("markReady:D", "will:D")
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case32WaitingSetStackRootReadyEarly() {
        val (_, d) = waitingRoot()
        h.scheduler.advance(200)
        d.content.mount()
        h.assertEvents("will:D")
        h.scheduler.advance(1_000)
        assertTrue(h.events().isEmpty())
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case33PushOverAWaitingSetStackRoot() {
        val (_, d) = waitingRoot()
        val e = h.screen("E")
        h.push(e, from = "D")
        h.assertEvents(
            "didDisappear:B", "state", "popped:B", "popped:A", "destroyed:B", "destroyed:A",
            "setStackRoot1 resolve D", "completed:setStackRoot", "will:E",
        )
        assertEquals(listOf("C", "D", "E"), h.entries())
        display.finishAll()
        h.assertEvents("didAppear:E", "state", "push2 resolve E", "completed:push")
        d.content.mount()
        h.pop()
        display.finishAll()
        // D appears when a pop reveals it, not before.
        assertEquals(listOf("will:D", "didDisappear:E", "didAppear:D"), h.events().take(3))
        assertSettled()
    }

    @Test
    fun case34SetStackRootOverAWaitingOne() {
        waitingRoot()
        h.setStackRoot(h.screen("F"))
        display.finishAll()
        h.assertEvents(
            "didDisappear:B", "state", "popped:B", "popped:A", "destroyed:B", "destroyed:A",
            "setStackRoot1 resolve D", "completed:setStackRoot",
            "will:F", "didAppear:F", "state", "popped:D", "popped:C", "destroyed:D", "destroyed:C",
            "setStackRoot2 resolve F", "completed:setStackRoot",
        )
        assertSettled()
    }

    @Test
    fun case35PopOverAWaitingSetStackRoot() {
        waitingRoot()
        h.pop()
        display.finishAll()
        h.assertEvents(
            "didDisappear:B", "state", "popped:B", "popped:A", "destroyed:B", "destroyed:A",
            "setStackRoot1 resolve D", "completed:setStackRoot",
            "will:C", "didAppear:C", "state", "popped:D", "destroyed:D", "pop2 resolve D",
            "completed:pop",
        )
        assertSettled()
    }

    @Test
    fun case36BackWithOneEntryOfAWaitingSetStackRoot() {
        h.start(h.screen("A"), h.screen("B"))
        val d = h.screen("D", ready = false)
        h.setStackRoot(d)
        assertFalse(h.stack.canHandleBack())
        assertFalse(h.back())
        assertSame(d, queue.waitingScreen)
        d.content.mount()
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case37PopToTheTopOfAWaitingSetStackRoot() {
        val (_, d) = waitingRoot()
        h.popTo(d)
        assertTrue(h.events().isEmpty())
        d.content.mount()
        h.assertEvents("will:D", "state", "popTo2 resolve D", "completed:popTo")
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case38TheSplitViewsSetRootOverAWaitingSetStackRoot() {
        waitingRoot()
        h.splitSetRoot(h.screen("F"))
        h.assertEvents(
            "didDisappear:B", "state", "popped:B", "popped:A", "destroyed:B", "destroyed:A",
            "setStackRoot1 resolve D", "completed:setStackRoot",
            "will:F", "didAppear:F", "stateNow", "popped:D", "popped:C", "destroyed:D", "destroyed:C",
            "listener success F",
        )
        assertSettled()
    }

    // Transitions on Android: they overlap, each owns the screens it moves.

    @Test
    fun case39PushThenPushOverlap() {
        h.start(h.screen("A"))
        val b = h.screen("B")
        h.push(b)
        display.move(b, 0.25f)
        h.push(h.screen("C"), from = "B")
        // B goes on from where it is, without a jump.
        assertEquals("PushCover:B:0.25:72.0", display.starts.last())
        h.assertViews("raise:B", "raise:C", "stop:B")
        display.finishAll()
        h.assertEvents(
            "will:B", "will:C", "didDisappear:A", "state", "push1 resolve B", "completed:push",
            "didDisappear:B", "didAppear:C", "state", "push2 resolve C", "completed:push",
        )
        h.assertViews("detach:A", "detach:B")
        assertSettled()
    }

    @Test
    fun case40PushThenPopCancelsThePush() {
        val a = h.screen("A")
        h.start(a)
        val b = h.screen("B")
        h.push(b)
        display.move(b, 0.5f)
        h.clear()
        h.pop()
        // The push ends at once, without DidAppear of B or DidDisappear of A.
        h.assertEvents("state", "push1 resolve B", "completed:push")
        assertEquals(listOf("PopOut:B:0.5:48.0", "PopReveal:A:1.0:0.0"), display.starts)
        display.finishAll()
        h.assertEvents(
            "didDisappear:B", "state", "popped:B", "destroyed:B", "pop2 resolve B", "completed:pop",
        )
        assertSettled()
    }

    @Test
    fun case41PushThenPopWithoutAnimation() {
        h.start(h.screen("A"))
        h.push(h.screen("B", options = h.options { animations.pop.enabled = Bool(false) }))
        h.clear()
        h.pop()
        h.assertEvents(
            "state", "push1 resolve B", "completed:push", "didDisappear:B", "state", "popped:B",
            "destroyed:B", "pop2 resolve B", "completed:pop",
        )
        assertTrue(display.animating.isEmpty())
        assertSettled()
    }

    @Test
    fun case42PushThenBack() {
        h.start(h.screen("A"))
        h.push(h.screen("B"))
        h.clear()
        assertTrue(h.back())
        display.finishAll()
        h.assertEvents(
            "state", "push1 resolve B", "completed:push", "didDisappear:B", "stateNow", "popped:B",
            "destroyed:B",
        )
        assertSettled()
    }

    @Test
    fun case43PushThenPopToBelowIt() {
        val x = h.screen("X")
        h.start(x, h.screen("A"))
        h.push(h.screen("B"))
        h.clear()
        h.popTo(x)
        h.assertViews("reveal:X", "stop:B", "stop:A", "hide:A")
        display.finishAll()
        h.assertEvents(
            "state", "push1 resolve B", "completed:push", "will:X", "didDisappear:B", "didDisappear:A",
            "didAppear:X", "state", "popped:B", "popped:A", "destroyed:B", "destroyed:A",
            "popTo2 resolve X", "completed:popTo",
        )
        assertSettled()
    }

    @Test
    fun case44DoubleBackOverlaps() {
        val a = h.screen("A")
        h.start(h.screen("X"), a, h.screen("B"))
        h.back()
        display.move(a, 0.5f)
        h.back()
        // The second pop takes A over from where it is; the first goes on with B.
        assertEquals("PopOut:A:1.0:-48.0", display.starts[2])
        display.finishAll()
        h.assertEvents(
            "will:A", "will:X", "didDisappear:B", "stateNow", "popped:B", "destroyed:B",
            "didDisappear:A", "didAppear:X", "stateNow", "popped:A", "destroyed:A",
        )
        assertSettled()
    }

    @Test
    fun case45DoublePopCommandsAnswerAtTheirEnds() {
        h.start(h.screen("X"), h.screen("A"), h.screen("B"))
        h.pop()
        h.pop()
        display.finishAll()
        val answers = h.events().filter { " resolve " in it }
        assertEquals(listOf("pop1 resolve B", "pop2 resolve A"), answers)
        assertSettled()
    }

    @Test
    fun case46PopThenPushOverlap() {
        val a = h.screen("A")
        h.start(a, h.screen("B"))
        h.pop()
        h.push(h.screen("C"), from = "A")
        display.finishAll()
        h.assertEvents(
            "will:A", "will:C", "didDisappear:B", "state", "popped:B", "destroyed:B", "pop1 resolve B",
            "completed:pop", "didDisappear:A", "didAppear:C", "state", "push2 resolve C",
            "completed:push",
        )
        assertSettled()
    }

    @Test
    fun case47PopThenPushWithoutAnimation() {
        h.start(h.screen("A"), h.screen("B"))
        h.pop()
        h.push(h.screen("C", options = h.options { animations.push.enabled = Bool(false) }), from = "A")
        // The push answers first: it has no transition (C9).
        h.assertEvents(
            "will:A", "will:C", "didDisappear:A", "didAppear:C", "state", "push2 resolve C",
            "completed:push",
        )
        display.finishAll()
        h.assertEvents(
            "didDisappear:B", "state", "popped:B", "destroyed:B", "pop1 resolve B", "completed:pop",
        )
        assertSettled()
    }

    @Test
    fun case48PushThenSetStackRoot() {
        h.start(h.screen("A"))
        h.push(h.screen("B"))
        val c = h.screen("C", ready = false)
        h.setStackRoot(c)
        assertTrue(display.isRunning(h.registry.find("B") ?: error("B")))
        c.content.mount()
        display.finishAll()
        h.assertEvents(
            "will:B", "state", "push1 resolve B", "completed:push", "will:C", "didDisappear:B",
            "didDisappear:A", "didAppear:C", "state", "popped:B", "popped:A", "destroyed:B", "destroyed:A",
            "setStackRoot2 resolve C", "completed:setStackRoot",
        )
        assertSettled()
    }

    @Test
    fun case49SetStackRootThenPush() {
        h.start(h.screen("A"), h.screen("B"))
        h.setStackRoot(h.screen("C"))
        h.push(h.screen("D"), from = "C")
        display.finishAll()
        // The end of setStackRoot stops only what it destroys: the push plays to its end.
        h.assertEvents(
            "will:C", "will:D", "didDisappear:B", "state", "popped:B", "popped:A", "destroyed:B",
            "destroyed:A", "setStackRoot1 resolve C", "completed:setStackRoot", "didDisappear:C",
            "didAppear:D", "state", "push2 resolve D", "completed:push",
        )
        assertSettled()
    }

    @Test
    fun case50SetStackRootTwice() {
        h.start(h.screen("A"), h.screen("B"))
        h.setStackRoot(h.screen("C"))
        h.setStackRoot(h.screen("F"))
        h.assertEvents(
            "will:C", "didDisappear:B", "didAppear:C", "state", "popped:B", "popped:A", "destroyed:B",
            "destroyed:A", "setStackRoot1 resolve C", "completed:setStackRoot", "will:F",
        )
        display.finishAll()
        h.assertEvents(
            "didDisappear:C", "didAppear:F", "state", "popped:C", "destroyed:C",
            "setStackRoot2 resolve F", "completed:setStackRoot",
        )
        assertSettled()
    }

    @Test
    fun case51SetStackRootThenPop() {
        h.start(h.screen("A"))
        h.setStackRoot(h.screen("C"), h.screen("D"))
        h.pop()
        display.finishAll()
        // The setStackRoot isn't cancelled: the pop takes D over, and each ends by itself.
        h.assertEvents(
            "will:D", "will:C", "didDisappear:A", "state", "popped:A", "destroyed:A",
            "setStackRoot1 resolve D", "completed:setStackRoot", "didDisappear:D", "didAppear:C", "state",
            "popped:D", "destroyed:D", "pop2 resolve D", "completed:pop",
        )
        assertSettled()
    }

    @Test
    fun case52PopThenSetStackRoot() {
        h.start(h.screen("A"), h.screen("B"))
        h.pop()
        h.setStackRoot(h.screen("C"))
        display.finishAll()
        h.assertEvents(
            "will:A", "will:C", "didDisappear:B", "state", "popped:B", "destroyed:B", "pop1 resolve B",
            "completed:pop", "didDisappear:A", "didAppear:C", "state", "popped:A", "destroyed:A",
            "setStackRoot2 resolve C", "completed:setStackRoot",
        )
        assertSettled()
    }

    @Test
    fun case53NoneDuringAPush() {
        h.start(h.screen("A"))
        val b = h.screen("B")
        h.push(b)
        h.popTo(b)
        h.assertEvents("will:B", "state", "popTo2 resolve B", "completed:popTo")
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case54WaitingPushOverARunningPop() {
        val b = h.screen("B")
        h.start(h.screen("A"), b)
        h.pop()
        h.push(h.waiting("C"), from = "A")
        assertTrue(h.back())
        h.assertEvents("will:A", "destroyed:C", "state", "push2 resolve C", "completed:push")
        assertTrue(display.isRunning(b))
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case55TheSplitViewsSetRootDuringAPush() {
        h.start(h.screen("A"))
        h.push(h.screen("B"))
        h.splitSetRoot(h.screen("C"))
        h.assertEvents(
            "will:B", "state", "push1 resolve B", "completed:push", "will:C", "didDisappear:B",
            "didDisappear:A", "didAppear:C", "stateNow", "popped:B", "popped:A", "destroyed:B", "destroyed:A",
            "listener success C",
        )
        assertSettled()
    }

    @Test
    fun case56AnAnimatorTheSystemCancels() {
        h.start(h.screen("A"))
        val b = h.screen("B")
        h.push(b)
        display.cancelBySystem(b)
        display.finishAll()
        val ends = h.events().count { it == "push1 resolve B" }
        assertEquals(1, ends)
        h.clear()
        // A late end of an animator of the ended push does nothing.
        display.ends.forEach { it.second() }
        assertTrue(h.events().isEmpty())
        assertSettled()
    }

    // Over every state.

    // The stack goes off screen: its screens disappear first (a tab unselected).
    private fun hide() {
        display.isOnScreen = false
        h.stack.onViewDisappear()
    }

    private fun show() {
        display.isOnScreen = true
        h.stack.onViewWillAppear()
        h.stack.onViewDidAppear()
    }

    @Test
    fun case57PushOffScreen() {
        h.start(h.screen("A"))
        hide()
        h.clear()
        h.push(h.screen("B", options = h.options { animations.push.waitForRender = Bool(true) }))
        h.assertEvents("state", "push1 resolve B", "completed:push")
        h.assertViews("raise:B", "detach:A")
        show()
        h.assertEvents("will:B", "didAppear:B")
        assertSettled()
    }

    @Test
    fun case58PopOffScreen() {
        h.start(h.screen("A"), h.screen("B"))
        hide()
        h.clear()
        h.pop()
        h.assertEvents("state", "popped:B", "destroyed:B", "pop1 resolve B", "completed:pop")
        show()
        h.assertEvents("will:A", "didAppear:A")
        assertSettled()
    }

    @Test
    fun case59SetStackRootOffScreen() {
        h.start(h.screen("A"), h.screen("B"))
        hide()
        h.clear()
        h.setStackRoot(h.screen("C", ready = false))
        h.assertEvents(
            "state", "popped:B", "popped:A", "destroyed:B", "destroyed:A", "setStackRoot1 resolve C",
            "completed:setStackRoot",
        )
        assertSettled()
    }

    @Test
    fun case60ASequenceOffScreen() {
        h.start(h.screen("A"))
        hide()
        h.clear()
        h.push(h.screen("B"))
        h.push(h.screen("C"), from = "B")
        h.pop()
        h.setStackRoot(h.screen("D"))
        val answers = h.events().filter { " resolve " in it }
        assertEquals(
            listOf("push1 resolve B", "push2 resolve C", "pop3 resolve C", "setStackRoot4 resolve D"), answers,
        )
        assertEquals(listOf("D"), h.shown())
        assertSettled()
    }

    @Test
    fun case61WithoutAView() {
        h.start(h.screen("A"))
        hide()
        display.hasView = false
        h.clear()
        h.push(h.screen("B"))
        h.pop()
        h.setStackRoot(h.screen("C"))
        // No view is made or moved; the screens that leave get their ScreenPopped and go.
        assertTrue(h.views.isEmpty())
        h.assertEvents(
            "state", "push1 resolve B", "completed:push",
            "state", "popped:B", "destroyed:B", "pop2 resolve B", "completed:pop",
            "state", "popped:A", "destroyed:A", "setStackRoot3 resolve C", "completed:setStackRoot",
        )
        assertSettled()
    }

    @Test
    fun case62TheStackGoesOffScreenWhileAPushWaits() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        hide()
        h.assertEvents("didDisappear:A", "state", "push1 resolve B", "completed:push")
        assertFalse(h.journal.contains("markReady:B"))
        show()
        assertTrue(h.events().isEmpty())
        b.content.mount()
        h.assertEvents("will:B", "didAppear:B")
        assertSettled()
    }

    @Test
    fun case63TheStackGoesOffScreenWhileAPushRuns() {
        val b = h.screen("B")
        h.start(h.screen("A"))
        h.push(b)
        hide()
        h.clear()
        display.finishAll()
        h.assertEvents("state", "push1 resolve B", "completed:push")
        show()
        h.assertEvents("will:B", "didAppear:B")
        assertSettled()
    }

    @Test
    fun case64DestroyedWhileAPushWaits() {
        h.start(h.screen("A"))
        h.push(h.waiting("B"), from = "A")
        h.stack.destroy()
        h.assertEvents(
            "push1 reject ${ErrorTexts.screenNotShown("A")}", "didDisappear:A", "destroyed:A", "destroyed:B",
        )
        assertEquals(0, h.scheduler.pending)
    }

    @Test
    fun case65DestroyedWithQueuedCommands() {
        h.start(h.screen("A"))
        h.push(h.waiting("B"), from = "A")
        val c = h.screen("C")
        h.push(c, from = "B")
        h.popTo(c)
        h.stack.destroy()
        val answers = h.events().filter { " reject " in it || " resolve " in it }
        assertEquals(
            listOf(
                "push1 reject ${ErrorTexts.screenNotShown("A")}",
                "push2 reject ${ErrorTexts.screenNotShown("B")}",
                "popTo3 reject ${ErrorTexts.screenNotShown("C")}",
            ),
            answers,
        )
        assertEquals(0, h.scheduler.pending)
    }

    @Test
    fun case66DestroyedWhileTransitionsRun() {
        h.start(h.screen("A"), h.screen("B"))
        h.pop(from = h.registry.find("B"))
        h.push(h.screen("C"), from = "A")
        h.clear()
        // Each animator stopped ends every other one from its cancel: the list the destroy walks changes under it.
        display.onStop = { display.ends.toList().forEach { it.second() } }
        h.stack.destroy()
        val answers = h.events().filter { " reject " in it || " resolve " in it }
        assertEquals(
            listOf("pop1 reject ${ErrorTexts.screenNotShown("B")}", "push2 reject ${ErrorTexts.screenNotShown("A")}"),
            answers,
        )
        h.clear()
        display.ends.forEach { it.second() }
        // Each command rejected once, at the destroy; nothing after it.
        assertTrue(h.events().isEmpty())
        assertTrue(display.animating.isEmpty())
        val setStackRoot = StackQueueHarness()
        setStackRoot.install()
        try {
            setStackRoot.start(setStackRoot.screen("A"))
            setStackRoot.setStackRoot(setStackRoot.screen("C"))
            setStackRoot.stack.destroy()
            assertTrue(setStackRoot.events().contains("setStackRoot1 reject ${ErrorTexts.screenNotShown(STACK)}"))
            assertTrue(setStackRoot.display.animating.isEmpty())
        } finally {
            setStackRoot.uninstall()
        }
    }

    @Test
    fun case66bTheRunningCommandsRejectAtTheDestroy() {
        h.start(h.screen("A"), h.screen("B"))
        h.pop(from = h.registry.find("B"))
        h.push(h.screen("C"), from = "A")
        h.clear()
        h.stack.destroy()
        val answers = h.events().filter { " reject " in it || " resolve " in it }
        assertEquals(
            listOf("pop1 reject ${ErrorTexts.screenNotShown("B")}", "push2 reject ${ErrorTexts.screenNotShown("A")}"),
            answers,
        )
    }

    @Test
    fun case67DestroyedWithAPreparedScreen() {
        h.start(h.screen("A"))
        val p = h.screen("P", slot = true).apply { isPreparedPush = true }
        p.parentController = h.stack
        h.prepared.add(p, h.stack)
        h.stack.destroy()
        // The stack's own destroy takes the screens prepared for it.
        assertTrue(p.isDestroyed)
        assertFalse(h.prepared.isPrepared(p))
        assertFalse(h.events().contains("slotDestroyed:P"))
    }

    @Test
    fun case68ReloadAnswersNothing() {
        h.start(h.screen("A"))
        h.push(h.screen("B"))
        h.push(h.waiting("C"), from = "B")
        h.push(h.screen("D"), from = "C")
        h.clear()
        ReloadCoordinator.advance()
        h.stack.destroy()
        display.ends.forEach { it.second() }
        assertTrue(h.events().none { " resolve " in it || " reject " in it })
        assertTrue(h.events().none { it.startsWith("will:") || it.startsWith("did") || it.startsWith("popped:") })
        assertEquals(0, h.scheduler.pending)
    }

    @Test
    fun case69ACommandFromAScreenThatLeaves() {
        h.start(h.screen("A"), h.screen("B"))
        h.back()
        h.clear()
        h.commands.pop("B", h.answer("pop"), "pop the screen 'B'")
        h.assertEvents("pop1 reject ${ErrorTexts.screenNotShown("B")}")
        display.finishAll()
    }

    @Test
    fun case70APushFromAScreenThatLeaves() {
        h.start(h.screen("A"), h.screen("B"))
        h.pop()
        h.clear()
        h.commands.push("B", h.layout("C"), h.answer("push"), "push the screen 'C'")
        h.assertEvents("push2 reject ${ErrorTexts.screenNotShown("B")}")
        assertNull(h.registry.find("C"))
        display.finishAll()
    }

    @Test
    fun case71BackIsComputedAgainAfterEachOperation() {
        // The stack's own host computes the back callback of every activity again (BackCallbackState.update).
        val real = StackQueueHarness(realHost = true)
        real.install()
        val navigator = Navigator(real.activity)
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }
        BackCallbackState.attach(navigator, callback)
        try {
            real.start(real.screen("A"))
            // The activity's navigation has no root, so each computation disables a callback left enabled.
            callback.isEnabled = true
            real.push(real.screen("B"))
            real.display.finishAll()
            assertFalse("after a push", callback.isEnabled)
            callback.isEnabled = true
            real.popTo(real.registry.find("B") ?: error("B"))
            assertFalse("after a none", callback.isEnabled)
            callback.isEnabled = true
            real.display.failure = IllegalStateException("no view")
            real.push(real.screen("C"))
            assertTrue(real.events().any { " reject " in it })
            assertFalse("after a rejection", callback.isEnabled)
        } finally {
            BackCallbackState.detach(navigator)
            real.uninstall()
        }
    }

    @Test
    fun case71bBackIsComputedAsAnOperationIsAcceptedAndAsAWaitingPushBegins() {
        // Without a frame that computes it: the entries change at the acceptance, and the screen whose options count
        // (`hardwareBackButton.systemAnimation`) when the push that waited for it begins, after its command.
        val real = StackQueueHarness(realHost = true)
        real.install()
        val navigator = Navigator(real.activity)
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }
        BackCallbackState.attach(navigator, callback)
        try {
            real.start(real.screen("A"))
            val b = real.waiting("B")
            callback.isEnabled = true
            real.push(b)
            assertNotSame(b, real.queue.currentChild)
            assertFalse("at the acceptance", callback.isEnabled)
            callback.isEnabled = true
            b.content.mount()
            assertSame(b, real.queue.currentChild)
            assertFalse("as the push begins", callback.isEnabled)
            callback.isEnabled = true
            assertTrue(real.back())
            assertFalse("at the acceptance of back", callback.isEnabled)
            real.display.finishAll()
        } finally {
            BackCallbackState.detach(navigator)
            real.uninstall()
        }
    }

    @Test
    fun case72BlurOnUnmountAtTheStartOfACutAndOfAReplace() {
        val a = h.screen("A")
        h.start(a, h.screen("B"), h.screen("C"))
        h.popTo(a)
        assertEquals(listOf("leave:C", "leave:B", "front:A"), h.calls)
        display.finishAll()
        h.calls.clear()
        h.setStackRoot(h.screen("D"))
        assertEquals(listOf("leave:A"), h.calls)
        display.finishAll()
    }

    @Test
    fun case73AFailureWhileAnOperationRuns() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        h.push(h.screen("C"), from = "B")
        display.failure = IllegalStateException("no room")
        b.content.mount()
        // B rejects and goes silently; C, queued behind it, runs.
        h.assertEvents(
            "destroyed:B", "push1 reject ${ErrorTexts.couldNot("push the screen 'B'", "no room")}", "will:C",
        )
        assertEquals(listOf("A", "C"), h.entries())
        display.finishAll()
        assertSettled()
    }

    @Test
    fun case74HeldEventsGoAfterTheAnswer() {
        h.start(h.screen("A"))
        val b = h.screen("B", ready = false)
        h.push(b)
        display.finishAll()
        // The content of B hasn't mounted: its appearance waits for it, the transition doesn't.
        h.assertEvents("didDisappear:A", "state", "push1 resolve B", "completed:push")
        b.content.mount()
        h.assertEvents("will:B", "didAppear:B")
        assertSettled()
    }


    @Test
    fun anActivityNotSeenRunsAtOnceAndTheNewTopStillAppears() {
        h.start(h.screen("A"))
        display.canAnimate = false
        h.push(h.screen("B"))
        h.assertEvents(
            "will:B", "didDisappear:A", "didAppear:B", "state", "push1 resolve B", "completed:push",
        )
        h.pop()
        h.assertEvents(
            "will:A", "didDisappear:B", "didAppear:A", "state", "popped:B", "destroyed:B",
            "pop2 resolve B", "completed:pop",
        )
        assertTrue(display.starts.isEmpty())
        assertSettled()
    }

    @Test
    fun aScreenWhoseSafeAreaViewReportedWaitsWithoutALimit() {
        h.start(h.screen("A"))
        val b = h.waiting("B").apply { renderUnknown = true }
        h.push(b)
        // A safe area view reports the id before the second is out: the 1 s no longer applies.
        h.scheduler.advance(500)
        b.renderUnknown = false
        h.scheduler.advance(10_000)
        assertTrue(h.events().isEmpty())
        assertSame(b, queue.waitingScreen)
        b.content.mount()
        h.assertEvents("will:B")
        display.finishAll()
        assertSettled()
    }

    @Test
    fun aPushBehindANoneStillCompletesAWaitingSetStackRoot() {
        val (_, d) = waitingRoot()
        h.popTo(d)
        h.push(h.screen("E"), from = "D")
        val events = h.events()
        val completed = events.indexOf("setStackRoot1 resolve D")
        val none = events.indexOf("popTo2 resolve D")
        val push = events.indexOf("will:E")
        assertTrue(events.toString(), completed in 0 until none && none < push)
        display.finishAll()
    }

    @Test
    fun aNoneIsNotRejectedByALaterCommand() {
        val (_, d) = waitingRoot()
        h.popTo(d)
        h.setStackRoot(h.screen("F"))
        assertTrue(h.events().toString(), "popTo2 resolve D" in h.events())
        display.finishAll()
        assertSettled()
    }

    @Test
    fun theLowerScreensOfASetStackRootCompletedAtOnceMakeTheirContent() {
        waitingRoot()
        h.push(h.screen("E"), from = "D")
        assertTrue(h.views.toString(), "placeLower:C" in h.views)
        display.finishAll()
    }

    @Test
    fun anOperationIntoADestroyedStackRejects() {
        h.start(h.screen("A"))
        h.stack.destroy()
        h.clear()
        val b = h.screen("B")
        queue.push(b, h.answer("push"), "A", "push the screen 'B'")
        queue.none("A", null, h.answer("popTo"), "A", "pop to the screen 'A'")
        assertEquals(
            listOf("push1 reject ${ErrorTexts.screenNotShown("A")}", "popTo2 reject ${ErrorTexts.screenNotShown("A")}"),
            h.events().filter { " reject " in it },
        )
        assertTrue(b.isDestroyed)
    }

    @Test
    fun aLowerScreenThatFailsIsLoggedAndTheStackGoesOn() {
        h.start(h.screen("A"), h.screen("B"), h.screen("C"))
        display.lowerFailure = IllegalStateException("no room")
        h.stack.onViewDidAppear()
        assertTrue(h.journal.any { it.startsWith("log:") && "no room" in it })
        // The next lower screen still makes its content.
        assertTrue(h.views.toString(), "placeLower:B" in h.views)
    }

    @Test
    fun theTopSettlesOnlyAtTheEndOfItsOwnTransition() {
        h.start(h.screen("A"))
        h.push(h.screen("B"))
        h.push(h.screen("C"), from = "B")
        val before = h.host.topSettled
        // B's push ends first, with C still coming in: the FAB doesn't follow yet.
        display.finish(h.registry.find("A") ?: error("A"))
        assertEquals(before, h.host.topSettled)
        display.finishAll()
        assertEquals(before + 1, h.host.topSettled)
        assertSettled()
    }


    @Test
    fun aCutThatFailsAfterItTookScreensOffWhatShowsDestroysThem() {
        val a = h.screen("A")
        val b = h.screen("B")
        val c = h.screen("C")
        h.start(a, b, c)
        display.animateFailure = IllegalStateException("no room")
        h.popTo(a)
        // B and C left what shows before the failure: they go silently, their views out of the container.
        assertTrue(b.isDestroyed)
        assertTrue(c.isDestroyed)
        assertFalse(display.isPlaced(c))
        assertEquals(
            "popTo1 reject ${ErrorTexts.couldNot("pop to the screen 'A'", "no room")}",
            h.events().first { " reject " in it },
        )
        assertEquals(listOf("A"), h.entries())
        assertEquals(listOf("A"), h.shown())
        assertSettled()
    }

    @Test
    fun aReplaceThatFailsAfterItTookScreensOffWhatShowsDestroysThem() {
        val a = h.screen("A")
        val b = h.screen("B")
        h.start(a, b)
        display.animateFailure = IllegalStateException("no room")
        h.setStackRoot(h.screen("C"), animated = true)
        h.scheduler.advance(StackQueue.SET_STACK_ROOT_WAIT_MILLIS)
        assertTrue(h.events().any { " reject " in it })
        assertTrue(a.isDestroyed)
        assertTrue(b.isDestroyed)
        assertFalse(display.isPlaced(a))
        assertFalse(display.isPlaced(b))
        assertEquals(listOf("C"), h.shown())
        assertTrue(queue.isAtRest)
        assertEquals(0, h.scheduler.pending)
    }

    @Test
    fun aWaitingPushWhoseScreenFailsToGoStillAnswersAndTheCutRuns() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        b.destroyFailure = IllegalStateException("no surface")
        h.pop()
        // The teardown's failure is logged; the push still answers, and the pop is queued and answers.
        val events = h.events()
        assertTrue(events.toString(), "push1 resolve B" in events)
        assertTrue(events.toString(), events.any { it.startsWith("pop2 resolve") })
        assertTrue(
            events.toString(),
            "log:[ApexNavigation] ${ErrorTexts.couldNot("destroy the screen 'B'", "no surface")}" in events,
        )
        assertEquals(listOf("A"), h.entries())
        assertSettled()
    }


    @Test
    fun aWaitingPushWhoseViewFailsToLeaveIsStillDestroyed() {
        h.start(h.screen("A"))
        val b = h.waiting("B")
        h.push(b)
        display.removeFailure = IllegalStateException("no room")
        h.pop()
        // The view and the node go each on their own: the screen is destroyed though its view failed to leave.
        val events = h.events()
        assertTrue(b.isDestroyed)
        assertTrue(events.toString(), "push1 resolve B" in events)
        assertTrue(events.toString(), events.any { it.startsWith("pop2 resolve") })
        assertTrue(
            events.toString(),
            "log:[ApexNavigation] ${ErrorTexts.couldNot("remove the view of the screen 'B'", "no room")}" in events,
        )
        assertEquals(listOf("A"), h.entries())
        assertTrue(queue.isAtRest)
    }

    @Test
    fun aFailedAnimatedSetStackRootLeavesItsNewTopAtRest() {
        h.start(h.screen("A"), h.screen("B"))
        val c = h.screen("C")
        display.animateFailure = IllegalStateException("no room")
        h.setStackRoot(c, animated = true)
        h.scheduler.advance(StackQueue.SET_STACK_ROOT_WAIT_MILLIS)
        assertTrue(h.events().any { " reject " in it })
        assertEquals(listOf("C"), h.shown())
        // It shows, so it is opaque and in its place, as at the end of a transition.
        assertEquals(1f, display.valuesOf(c).alpha)
        assertEquals(0f, display.valuesOf(c).translation)
    }
}

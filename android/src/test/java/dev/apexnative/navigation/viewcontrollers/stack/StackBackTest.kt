package dev.apexnative.navigation.viewcontrollers.stack

import androidx.activity.OnBackPressedCallback
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.layout.RootLayout
import dev.apexnative.navigation.options.ButtonOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandRunner
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.parent.ReturnsScreenFromColumn
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Back on Android: the stack's step in its order and its mirror without acting, the holds and their ids, the back
 * arrow's rule of which screens have it and its press, the tabs' returns, and the back callback the runner and
 * `setBackListeners` recompute.
 */
class StackBackTest {
    private val h = StackQueueHarness()

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    // A screen that holds system back ([hardware]) or the back button, with the back button's [buttonId].
    private fun holding(id: String, hardware: Boolean = true, buttonId: String? = null) =
        h.screen(id, options = h.options {
            if (hardware) {
                hardwareBackButton.popStackOnPress = Bool(false)
            } else {
                topBar.buttons.back.popStackOnPress = Bool(false)
            }
            buttonId?.let { topBar.buttons.back.id = Text(it) }
        })

    @Test
    fun backCutsTheTopWithTwoScreensOrMore() {
        h.start(h.screen("A"), h.screen("B"))
        assertTrue(h.stack.canHandleBack())
        assertTrue(h.back())
        assertEquals(listOf("A"), h.entries())
        h.display.finishAll()
        assertTrue("popped:B" in h.events())
    }

    @Test
    fun backOnTheFirstScreenGoesOnDownTheOrder() {
        h.start(h.screen("A"))
        assertFalse(h.stack.canHandleBack())
        assertFalse(h.back())
    }

    @Test
    fun aHoldingScreenTakesBackAndCountsForTheCallback() {
        h.start(h.screen("A"), holding("B"))
        assertTrue(h.stack.canHandleBack())
        assertTrue(h.back())
        h.assertEvents("pressed:B:ApexNavigation.hardwareBackButton")
        assertEquals(listOf("A", "B"), h.entries())
    }

    @Test
    fun theHoldOfTheStacksOptionsCounts() {
        h.stack.options = Options().apply { hardwareBackButton.popStackOnPress = Bool(false) }
        h.start(h.screen("A"), h.screen("B"))
        assertTrue(h.back())
        h.assertEvents("pressed:B:ApexNavigation.hardwareBackButton")
    }

    @Test
    fun aHoldOnTheFirstScreenDoesntKeepTheAppFromLeaving() {
        h.start(holding("A"))
        assertFalse(h.stack.canHandleBack())
        assertFalse(h.back())
        assertTrue(h.events().isEmpty())
    }

    @Test
    fun theFirstScreenOfASlidingPaneClosesIt() {
        val columns = Columns(h)
        h.stack.parentController = columns
        h.start(h.screen("A"))
        assertTrue(h.back())
        assertSame(h.stack, columns.asked)
    }

    // The preview of a pop during the back gesture.

    @Test
    fun aBackGesturePreviewsTheDefaultPop() {
        val a = h.screen("A")
        h.start(a)
        a.view
        h.push(h.screen("B"))
        h.display.finishAll()
        assertSame(h.stack, h.stack.stackPoppedByBack())
    }

    @Test
    fun previewFalseOfThePopLeavesTheGestureWithoutAPreview() {
        val a = h.screen("A")
        h.start(a)
        a.view
        h.push(h.screen("B", options = h.options { animations.pop.preview = Bool(false) }))
        h.display.finishAll()
        assertNull(h.stack.stackPoppedByBack())
        // Back still pops it, on the gesture's release.
        assertTrue(h.stack.canHandleBack())
    }

    // The back arrow.

    @Test
    fun theArrowsRuleAsScreensJoin() {
        val first = h.screen("A")
        val firstShown = h.screen("A2", options = h.options { topBar.buttons.back.visible = Bool(true) })
        val second = h.screen("B")
        val withButtons = h.screen("C", options = h.options { topBar.buttons.left = listOf(ButtonOptions()) })
        val hidden = h.screen("D", options = h.options { topBar.buttons.back.visible = Bool(false) })
        h.stack.join(first, first = true, key = null)
        h.stack.join(firstShown, first = true, key = null)
        h.stack.join(second, first = false, key = null)
        h.stack.join(withButtons, first = false, key = null)
        h.stack.join(hidden, first = false, key = null)
        assertTrue(first.options.topBar.buttons.back.visible.isFalse)
        assertTrue(firstShown.options.topBar.buttons.back.visible.isTrue)
        assertTrue(second.options.topBar.buttons.back.visible.isTrue)
        assertFalse(withButtons.options.topBar.buttons.back.visible.hasValue())
        assertTrue(hidden.options.topBar.buttons.back.visible.isFalse)
    }

    @Test
    fun theStacksVisibleFalseKeepsTheArrowAway() {
        h.stack.options = Options().apply { topBar.buttons.back.visible = Bool(false) }
        val b = h.screen("B")
        h.stack.join(b, first = false, key = null)
        assertFalse(b.options.topBar.buttons.back.visible.hasValue())
    }

    @Test
    fun theArrowsPressLeavesOrHolds() {
        h.start(h.screen("A"), h.screen("B"))
        h.stack.onBackArrowPressed()
        assertEquals(listOf("A"), h.entries())
        h.display.finishAll()
        h.clear()
        h.push(holding("C", hardware = false))
        h.display.finishAll()
        h.clear()
        h.stack.onBackArrowPressed()
        h.assertEvents("pressed:C:ApexNavigation.back")
        h.push(holding("D", hardware = false, buttonId = "leave"), from = "C")
        h.display.finishAll()
        h.clear()
        h.stack.onBackArrowPressed()
        h.assertEvents("pressed:D:leave")
    }

    @Test
    fun theArrowOnTheFirstScreenOfASlidingPaneClosesIt() {
        val columns = Columns(h)
        h.stack.parentController = columns
        h.start(h.screen("A"))
        h.stack.onBackArrowPressed()
        assertSame(h.stack, columns.asked)
    }

    // The tabs' returns.

    @Test
    fun theReTapOfATabGoesBackToTheFirstScreenUnlessHeld() {
        h.start(h.screen("A"), h.screen("B"), holding("C", hardware = false))
        assertTrue(h.stack.popToFirstByUser())
        h.assertEvents("pressed:C:ApexNavigation.back")
        h.clear()
        h.push(h.screen("D"), from = "C")
        h.display.finishAll()
        h.clear()
        assertTrue(h.stack.popToFirstByUser())
        h.display.finishAll()
        assertEquals(listOf("A"), h.entries())
    }

    @Test
    fun popToRootOnBlurIsSilent() {
        h.start(h.screen("A"), h.screen("B"), h.screen("C"))
        // The tab was left: its stack's screens disappeared.
        h.display.isOnScreen = false
        h.stack.onViewDisappear()
        h.clear()
        h.stack.popToFirstSilently()
        // No animation, no appearance events; each screen that leaves gets its ScreenPopped.
        assertTrue(h.display.starts.isEmpty())
        h.assertEvents("stateNow", "popped:C", "popped:B", "destroyed:C", "destroyed:B")
        h.display.isOnScreen = true
        h.stack.onViewWillAppear()
        h.stack.onViewDidAppear()
        h.assertEvents("will:A", "didAppear:A")
    }

    // The back callback.

    @Test
    fun setBackListenersEnablesTheCallbackOnTheFirstScreen() {
        val navigator = Navigator(h.activity)
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }
        BackCallbackState.attach(navigator, callback)
        try {
            assertFalse(callback.isEnabled)
            BackCallbackState.setJsListeners(true)
            assertTrue(callback.isEnabled)
            BackCallbackState.setJsListeners(false)
            assertFalse(callback.isEnabled)
        } finally {
            BackCallbackState.detach(navigator)
        }
    }

    @Test
    fun theCallbackIsOffOnTheOnlyScreenByDefault() {
        assertFalse(callbackOverTheOnlyScreen(Options()))
    }

    @Test
    fun systemAnimationFalseKeepsTheCallbackOnOverTheOnlyScreen() {
        assertTrue(callbackOverTheOnlyScreen(Options().apply { hardwareBackButton.systemAnimation = Bool(false) }))
    }

    // Whether the back callback is enabled once a root of one React screen with [options] showed.
    private fun callbackOverTheOnlyScreen(options: Options): Boolean {
        val navigator = Navigator(h.activity)
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }
        BackCallbackState.attach(navigator, callback)
        try {
            val journal = Journal()
            val home = LayoutNode(
                id = "Home1",
                type = LayoutType.Component,
                name = "Home",
                options = options,
                key = null,
                passProps = null,
                functions = emptyList(),
                columns = emptyList(),
                arrangement = null,
                children = emptyList(),
            )
            val answer = CommandAnswer(
                "setRoot",
                "setRoot1",
                FakePromise(journal),
                ReloadCoordinator.generation,
                FakeEvents(journal),
                { 0L },
            )
            navigator.setRoot(RootLayout(home, emptyList(), emptyList()), answer)
            ScreenReadiness.contentRendered("Home1")
            assertFalse(navigator.canHandleBack())
            return callback.isEnabled
        } finally {
            BackCallbackState.detach(navigator)
            navigator.destroyWithActivity()
        }
    }

    @Test
    fun theRunnerComputesBackAgainAfterEveryCommand() {
        val tasks = ArrayDeque<Runnable>()
        var computed = 0
        val journal = Journal()
        val runner = CommandRunner(
            post = { tasks.addLast(it) },
            activityPresent = { true },
            events = FakeEvents(journal),
            log = {},
            afterCommand = { computed++ },
        )
        runner.run("pop", "pop1", "pop the screen 'A'", FakePromise(journal), check = {}) { _, answer ->
            answer.reject("the screen 'A' is the first screen of its stack")
        }
        runner.run("pop", "pop2", "pop the screen 'A'", FakePromise(journal), check = {}) { _, _ ->
            throw IllegalStateException("broken")
        }
        runner.runVoid("setDefaultOptions", "set the default options") {}
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
        assertEquals(3, computed)
    }

    // A layout around the stack that returns screens from its columns.
    private class Columns(h: StackQueueHarness) :
        TestLayout(h.activity, h.registry, "Split1"), ReturnsScreenFromColumn {
        var asked: ViewController<*>? = null

        override fun returnsScreenFromColumn(screen: ViewController<*>, done: Runnable): Boolean {
            asked = screen
            return true
        }
    }
}

package dev.apexnative.navigation.viewcontrollers.bottomtabs

import android.app.Activity
import android.widget.FrameLayout
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.BottomTabsOptions
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.LayoutDirection
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.react.events.FakeContent
import dev.apexnative.navigation.react.events.NavigationState
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.RenderWait
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.stack.FakeStackDisplay
import dev.apexnative.navigation.viewcontrollers.stack.QueueReadiness
import dev.apexnative.navigation.viewcontrollers.stack.QueueScreen
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.stack.StackPresenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Calls
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.RecordingPresenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bottom tabs over stacks of test screens, without views (the tabs say they show), with the real dispatcher of the
 * events on a fake clock: the order of a press, a repeat, a long press and a choice from code, the first tab, the
 * one-shot options, `popToRootOnBlur`, back, the waits and the merges passed up without `bottomTabs`.
 */
class BottomTabsControllerTest {
    private val journal = mutableListOf<String>()
    private val fixture = DispatcherFixture(EventJournal(journal))
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val calls = mutableListOf<String>()
    private val views = mutableListOf<String>()
    private val displays = HashMap<StackController, FakeStackDisplay>()

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        ScreenReadiness.reset()
    }

    private fun screen(id: String, ready: Boolean = true) =
        QueueScreen(activity, registry, id, journal, calls, Options(), FakeContent(ready))

    private fun stack(id: String, vararg screens: QueueScreen, options: Options = Options()): StackController {
        val stack = StackController(activity, registry, id, StackPresenter(activity), options)
        val display = FakeStackDisplay(views, fixture.scheduler)
        stack.useQueueParts(display, QueueReadiness(journal), null)
        stack.preparedPushes = { null }
        stack.setInitialChildren(screens.toList(), emptyList())
        displays[stack] = display
        return stack
    }

    private fun tabs(
        vararg tabs: ViewController<*>,
        options: Options = Options(),
        presenter: BottomTabsPresenter = BottomTabsPresenter(activity),
    ) = BottomTabsController(activity, registry, TABS, presenter, options, tabs.toList()).also { it.shows = { true } }

    // Three tabs of one screen each, the first shown; the journal cleared.
    private fun shown(options: Options = Options(), first: Options = Options()): BottomTabsController {
        val tabs = tabs(
            stack("Stack1", screen("A1"), options = first),
            stack("Stack2", screen("B1")),
            stack("Stack3", screen("C1")),
            options = options,
        )
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        journal.clear()
        return tabs
    }

    private fun events(): List<String> = journal.toList().also { journal.clear() }

    // The ends of the fades that run, in order; each ends its fade once.
    private val fades = mutableListOf<() -> Unit>()

    // [shown] with tab views that fade on screen until the test ends the fade.
    private fun fading(): BottomTabsController {
        val tabs = shown()
        tabs.tabViews = object : BottomTabsController.TabViews {
            override fun holds(tab: ViewController<*>): Boolean = true

            override fun attach(tab: ViewController<*>, visible: Boolean) = Unit

            override fun switch(
                shown: ViewController<*>,
                hidden: ViewController<*>,
                animated: Boolean,
                onEnd: () -> Unit,
            ): () -> Unit {
                if (!animated) {
                    onEnd()
                    return {}
                }
                var ended = false
                val end = {
                    if (!ended) {
                        ended = true
                        onEnd()
                    }
                }
                fades += end
                return end
            }
        }
        return tabs
    }

    // A switch on screen fades.

    @Test
    fun aSwitchOnScreenSendsItsAppearanceAndSelectionAtTheEndOfTheFade() {
        val tabs = fading()
        tabs.onTabPressed(1)
        assertEquals(listOf("tabPressed:$TABS:1:B1:false", "will:B1"), events())
        assertEquals(1, tabs.selectedIndex)
        fades.single()()
        assertEquals(
            listOf("didDisappear:A1", "didAppear:B1", "stateNeeded", "tabSelected:$TABS:1:0", "stateNeeded"),
            events(),
        )
    }

    @Test
    fun aPressDuringAFadeEndsItFirst() {
        val tabs = fading()
        tabs.onTabPressed(1)
        events()
        tabs.onTabPressed(2)
        assertEquals(
            listOf(
                "didDisappear:A1",
                "didAppear:B1",
                "stateNeeded",
                "tabSelected:$TABS:1:0",
                "stateNeeded",
                "tabPressed:$TABS:2:C1:false",
                "will:C1",
            ),
            events(),
        )
        fades.last()()
        assertEquals(
            listOf("didDisappear:B1", "didAppear:C1", "stateNeeded", "tabSelected:$TABS:2:1", "stateNeeded"),
            events(),
        )
    }

    @Test
    fun aChoiceFromCodeDuringAFadeEndsItFirst() {
        val tabs = fading()
        tabs.onTabPressed(1)
        events()
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(2.0) })
        val sent = events()
        assertEquals(listOf("didDisappear:A1", "didAppear:B1"), sent.take(2))
        assertTrue("tabSelected:$TABS:1:0" in sent)
        assertEquals(listOf("will:C1", "stateNow"), sent.takeLast(2))
        assertEquals(2, tabs.selectedIndex)
    }

    // `selectTab` resolves on this state: the command after it acts in the new tab, not in the one still fading out.
    @Test
    fun aChoiceFromCodeOnScreenSendsTheStateAtTheStartOfTheFade() {
        val tabs = fading()
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(2.0) })
        assertEquals(listOf("will:C1", "stateNow"), events())
        assertEquals(NavigationState.State("C1", "C1", 2), NavigationState.stateOf(tabs))
        fades.single()()
        assertEquals(listOf("didDisappear:A1", "didAppear:C1", "stateNeeded", "stateNow"), events())
    }

    @Test
    fun tabsCoveredDuringAFadeShowTheNewTabOnlyWhenTheyShowAgain() {
        val tabs = fading()
        tabs.onTabPressed(1)
        events()
        // A modal over the tabs: they and the tabs' screens disappear.
        tabs.onViewDisappear()
        tabs.shows = { false }
        events()
        fades.single()()
        // No DidAppear under the modal; the selection is told, as for a screen that disappeared before it appeared.
        assertEquals(listOf("tabSelected:$TABS:1:0", "stateNeeded"), events())
        tabs.shows = { true }
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        assertEquals(listOf("will:B1", "didAppear:B1"), events().filter { !it.startsWith("state") })
    }

    @Test
    fun destroyingTheTabsDuringAFadeSendsNothingOfIt() {
        val tabs = fading()
        tabs.onTabPressed(1)
        events()
        tabs.destroy()
        events()
        fades.single()()
        assertEquals(emptyList<String>(), events())
    }

    private fun options(change: Options.() -> Unit) = Options().apply(change)

    // A press.

    @Test
    fun aPressOfAnotherTabGoesInTheOrderOfTheSpec() {
        val tabs = shown()
        tabs.onTabPressed(1)
        assertEquals(
            listOf(
                "tabPressed:$TABS:1:B1:false",
                "will:B1",
                "didDisappear:A1",
                "didAppear:B1",
                "stateNeeded",
                "tabSelected:$TABS:1:0",
                "stateNeeded",
            ),
            events(),
        )
        assertEquals(1, tabs.selectedIndex)
    }

    @Test
    fun theSelectedEventWaitsForAHeldDidAppearAndNeverGetsLost() {
        val late = screen("B1", ready = false)
        val tabs = tabs(stack("Stack1", screen("A1")), stack("Stack2", late), stack("Stack3", screen("C1")))
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        journal.clear()
        tabs.onTabPressed(1)
        // The left tab's DidDisappear asks for no state: it goes after Selected.
        assertEquals(listOf("tabPressed:$TABS:1:B1:false", "didDisappear:A1"), events())
        // Another press before B1 rendered: the event of the first goes before anything of the second.
        tabs.onTabPressed(2)
        val next = events()
        assertEquals("tabSelected:$TABS:1:0", next.first())
        assertEquals("tabPressed:$TABS:2:C1:false", next[2])
    }

    @Test
    fun theSelectedEventGoesRightAfterTheDidAppearItWaitedFor() {
        val late = screen("B1", ready = false)
        val tabs = tabs(stack("Stack1", screen("A1")), stack("Stack2", late))
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        journal.clear()
        tabs.onTabPressed(1)
        journal.clear()
        late.content.mount()
        assertEquals(listOf("will:B1", "didAppear:B1", "stateNeeded", "tabSelected:$TABS:1:0", "stateNeeded"), events())
    }

    @Test
    fun theDestroyOfTheTabsDropsAWaitingSelectedEvent() {
        val late = screen("B1", ready = false)
        val tabs = tabs(stack("Stack1", screen("A1")), stack("Stack2", late))
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        tabs.onTabPressed(1)
        tabs.destroy()
        journal.clear()
        late.content.mount()
        assertTrue(events().none { it.startsWith("tabSelected") })
    }

    @Test
    fun aRepeatSendsPressedOnlyAndGoesBackToTheFirstScreen() {
        val stack = stack("Stack1", screen("A1"), screen("A2"))
        val tabs = tabs(stack, stack("Stack2", screen("B1")))
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        journal.clear()
        tabs.onTabPressed(0)
        displays.getValue(stack).finishAll()
        val sent = events()
        assertEquals("tabPressed:$TABS:0:A2:true", sent.first())
        assertTrue(sent.none { it.startsWith("tabSelected") })
        assertTrue("popped:A2" in sent)
        assertEquals(listOf("A1"), stack.childControllers.map { it.id })
    }

    @Test
    fun aRepeatWithPopToRootOnPressFalseDoesNothingMore() {
        val first = options { bottomTab.popToRootOnPress = Bool(false) }
        val stack = stack("Stack1", screen("A1"), screen("A2"), options = first)
        val tabs = tabs(stack, stack("Stack2", screen("B1")))
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        journal.clear()
        tabs.onTabPressed(0)
        assertEquals(listOf("tabPressed:$TABS:0:A2:true"), events())
        assertEquals(2, stack.childControllers.size)
    }

    @Test
    fun selectTabOnPressFalseSendsPressedAndKeepsTheTab() {
        val button = stack("Stack2", screen("B1"), options = options { bottomTab.selectTabOnPress = Bool(false) })
        val tabs = tabs(stack("Stack1", screen("A1")), button)
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        journal.clear()
        tabs.onTabPressed(1)
        assertEquals(listOf("tabPressed:$TABS:1:B1:false"), events())
        assertEquals(0, tabs.selectedIndex)
    }

    @Test
    fun aLongPressSendsItsEventAndSelectsNothing() {
        val tabs = shown()
        tabs.onTabLongPressed(2)
        assertEquals(listOf("tabLongPressed:$TABS:2"), events())
        assertEquals(0, tabs.selectedIndex)
    }

    // From code.

    @Test
    fun aChoiceFromCodeSendsNoPressEventsAndTheStateAtOnce() {
        val tabs = shown()
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(2.0) })
        assertEquals(
            listOf("will:C1", "stateNow", "didDisappear:A1", "didAppear:C1", "stateNeeded", "stateNow"),
            events(),
        )
        assertEquals(2, tabs.selectedIndex)
        // One shot: not kept, a later merge doesn't select again.
        assertFalse(tabs.options.bottomTabs.currentTabIndex.hasValue())
        tabs.onTabPressed(0)
        journal.clear()
        tabs.mergeOptions(options { bottomTabs.visible = Bool(true) })
        assertEquals(0, tabs.selectedIndex)
    }

    @Test
    fun aChoiceFromAScreenInsideTheTabsSelects() {
        val a1 = screen("A1")
        val tabs = tabs(stack("Stack1", a1), stack("Stack2", screen("B1")))
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        a1.mergeOptions(options { bottomTabs.currentTabId = Text("B1") })
        assertEquals(1, tabs.selectedIndex)
    }

    @Test
    fun anIndexOutOfRangeOrAnUnknownIdChangesNothing() {
        val tabs = shown()
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(5.0) })
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(-1.0) })
        tabs.mergeOptions(options { bottomTabs.currentTabId = Text("Unknown") })
        assertEquals(0, tabs.selectedIndex)
        assertTrue(events().none { it.startsWith("will") })
    }

    @Test
    fun theIdWinsOverTheIndex() {
        val tabs = shown()
        tabs.mergeOptions(options {
            bottomTabs.currentTabIndex = Fraction(1.0)
            bottomTabs.currentTabId = Text("Stack3")
        })
        assertEquals(2, tabs.selectedIndex)
    }

    @Test
    fun theSelectedTabAgainFromCodeDoesNothing() {
        val tabs = shown()
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(0.0) })
        assertTrue(events().isEmpty())
    }

    // The first tab.

    @Test
    fun theFirstTabComesFromTheLayoutsOwnOptions() {
        assertEquals(1, tabs(stack("S1", screen("A")), stack("S2", screen("B")), options = options {
            bottomTabs.currentTabIndex = Fraction(1.0)
        }).selectedIndex)
        assertEquals(1, tabs(stack("S3", screen("C")), stack("S4", screen("D")), options = options {
            bottomTabs.currentTabIndex = Fraction(0.0)
            bottomTabs.currentTabId = Text("D")
        }).selectedIndex)
        assertEquals(0, tabs(stack("S5", screen("E")), stack("S6", screen("F")), options = options {
            bottomTabs.currentTabIndex = Fraction(4.0)
        }).selectedIndex)
        assertEquals(0, tabs(stack("S7", screen("G")), stack("S8", screen("H")), options = options {
            bottomTabs.currentTabId = Text("Unknown")
        }).selectedIndex)
    }

    // popToRootOnBlur.

    @Test
    fun theLeftTabGoesBackToItsFirstScreenSilentlyAfterSelected() {
        val first = stack("Stack1", screen("A1"), screen("A2"), options = options {
            bottomTab.popToRootOnBlur = Bool(true)
        })
        val tabs = tabs(first, stack("Stack2", screen("B1")))
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        displays.getValue(first).isOnScreen = false
        journal.clear()
        tabs.onTabPressed(1)
        val sent = events()
        val selected = sent.indexOf("tabSelected:$TABS:1:0")
        assertTrue(selected > 0)
        assertEquals(listOf("popped:A2", "destroyed:A2"), sent.drop(selected + 1).filter { !it.startsWith("state") })
        assertTrue(sent.none { it == "will:A1" || it == "didAppear:A1" })
        assertEquals(listOf("A1"), first.childControllers.map { it.id })
    }

    @Test
    fun aChoiceFromCodeRunsPopToRootOnBlurToo() {
        val first = stack("Stack1", screen("A1"), screen("A2"), options = options {
            bottomTab.popToRootOnBlur = Bool(true)
        })
        val tabs = tabs(first, stack("Stack2", screen("B1")))
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        displays.getValue(first).isOnScreen = false
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(1.0) })
        assertEquals(listOf("A1"), first.childControllers.map { it.id })
    }

    // Back.

    private val listener = object : CommandListener {
        override fun onSuccess(childId: String) = Unit

        override fun onError(message: String) = Unit
    }

    private fun backOn(mode: String) = options { hardwareBackButton.bottomTabsOnPress = Text(mode) }

    @Test
    fun previousGoesBackThroughTheHistoryAndAddsNothingToIt() {
        val tabs = shown(backOn("previous"))
        assertFalse(tabs.canHandleBack())
        tabs.onTabPressed(1)
        tabs.onTabPressed(2)
        assertTrue(tabs.canHandleBack())
        assertTrue(tabs.handleBack(listener))
        assertEquals(1, tabs.selectedIndex)
        assertTrue(tabs.handleBack(listener))
        assertEquals(0, tabs.selectedIndex)
        // Back's own selections added nothing: the history is empty now.
        assertFalse(tabs.canHandleBack())
        assertFalse(tabs.handleBack(listener))
    }

    @Test
    fun aBackSelectionIsAChoiceFromCode() {
        val tabs = shown(backOn("previous"))
        tabs.onTabPressed(1)
        journal.clear()
        tabs.handleBack(listener)
        val sent = events()
        assertTrue(sent.none { it.startsWith("tabPressed") || it.startsWith("tabSelected") })
        assertEquals("stateNow", sent.last())
    }

    @Test
    fun firstGoesToTheFirstTabAndNotFromIt() {
        val tabs = shown(backOn("first"))
        assertFalse(tabs.canHandleBack())
        tabs.onTabPressed(2)
        assertTrue(tabs.canHandleBack())
        assertTrue(tabs.handleBack(listener))
        assertEquals(0, tabs.selectedIndex)
        assertFalse(tabs.handleBack(listener))
    }

    @Test
    fun exitOrNothingLeavesBackToTheApp() {
        val exit = shown(backOn("exit"))
        exit.onTabPressed(1)
        assertFalse(exit.canHandleBack())
        assertFalse(exit.handleBack(listener))
        val unset = tabs(stack("S1", screen("X")), stack("S2", screen("Y")))
        assertFalse(unset.canHandleBack())
    }

    @Test
    fun theSelectedTabsStackTakesBackFirst() {
        val stack = stack("Stack1", screen("A1"), screen("A2"))
        val tabs = tabs(stack, stack("Stack2", screen("B1")), options = backOn("first"))
        assertTrue(tabs.canHandleBack())
        assertTrue(tabs.handleBack(listener))
        assertEquals(0, tabs.selectedIndex)
    }

    // The waits.

    @Test
    fun aWaitForTabsOfTogetherCountsTheOthersAsUnseen() {
        val tabs = tabs(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")), stack("Stack3", screen("C1")))
        assertEquals(listOf("A1"), RenderWait.waitedScreens(tabs).map { it.id })
        assertEquals(listOf("B1", "C1"), RenderWait.unseenScreens(tabs).map { it.id })
    }

    @Test
    fun theOtherModesWaitForTheSelectedTabOnly() {
        val lazy = options { bottomTabs.tabsAttachMode = Text("onSwitchToTab") }
        val tabs = tabs(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")), options = lazy)
        assertEquals(listOf("A1"), RenderWait.waitedScreens(tabs).map { it.id })
        assertTrue(RenderWait.unseenScreens(tabs).isEmpty())
        val after = options { bottomTabs.tabsAttachMode = Text("afterInitialTab") }
        val second = tabs(stack("Stack3", screen("C1")), stack("Stack4", screen("D1")), options = after)
        assertTrue(RenderWait.unseenScreens(second).isEmpty())
    }

    @Test
    fun theScreensOfUnselectedTabsGetNoAppearance() {
        shown()
        val tabs = tabs(stack("S1", screen("X1")), stack("S2", screen("Y1")))
        journal.clear()
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        assertTrue(events().none { it.endsWith(":Y1") })
    }

    // Merges up.

    @Test
    fun mergesGoUpWithoutBottomTabs() {
        val parent = Recording(activity, registry)
        val tabs = tabs(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")))
        tabs.parentController = parent
        tabs.mergeOptions(options {
            bottomTabs.visible = Bool(false)
            layout.direction = Text("rtl")
        })
        val merged = parent.merges.single()
        assertFalse(merged.bottomTabs.hasValue())
        assertEquals("rtl", merged.layout.direction.get(null))
    }

    @Test
    fun aMergeOfTheTabsOwnIndexSelectsWithoutAView() {
        val tabs = tabs(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")))
        tabs.shows = { false }
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(1.0) })
        assertEquals(1, tabs.selectedIndex)
        assertNull(tabs.peekView())
        assertTrue(events().none { it.startsWith("will") })
    }

    // Visibility and the stack's transitions.

    private val bar = Recorder(activity)

    private fun withBar(vararg stacks: StackController, options: Options = Options()): BottomTabsController {
        val tabs = tabs(*stacks, options = options, presenter = bar)
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        bar.calls.clear()
        journal.clear()
        return tabs
    }

    @Test
    fun aMergeOfVisibleSlidesWithAnimateAndGoesAtOnceWithout() {
        val tabs = withBar(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")))
        tabs.mergeOptions(options { bottomTabs.visible = Bool(false) })
        assertTrue("visible:false:animated" in bar.calls)
        tabs.mergeOptions(options {
            bottomTabs.visible = Bool(true)
            bottomTabs.animate = Bool(false)
        })
        assertTrue("visible:true:now" in bar.calls)
        // Not on screen: at once whatever `animate` says.
        tabs.shows = { false }
        bar.calls.clear()
        tabs.mergeOptions(options {
            bottomTabs.visible = Bool(false)
            bottomTabs.animate = Bool(true)
        })
        assertTrue("visible:false:now" in bar.calls)
    }

    private fun push(stack: StackController, node: QueueScreen, from: String) {
        stack.join(node, first = false, key = null)
        stack.queue.push(node, null, from, "push the screen '${node.id}'")
    }

    @Test
    fun thePushOfAScreenWithoutTheBarRidesAndRestsAtTheEnd() {
        val first = stack("Stack1", screen("A1"))
        val tabs = withBar(first, stack("Stack2", screen("B1")))
        val hidden = QueueScreen(activity, registry, "A2", journal, calls, options { bottomTabs.visible = Bool(false) })
        push(first, hidden, "A1")
        assertEquals(listOf("transition:WithPush:false:true"), bar.calls.filter { it.startsWith("transition") })
        // While it rides, the new top's options don't move the bar themselves.
        tabs.applyChildOptions(hidden.resolveCurrentOptions(), first)
        assertTrue(bar.calls.none { it.startsWith("visible") })
        displays.getValue(first).finishAll()
        assertEquals("rest:false", bar.calls.last())
    }

    @Test
    fun aPushThatKeepsTheBarMovesNothingAndAnUnselectedTabsPushNeither() {
        val first = stack("Stack1", screen("A1"))
        val second = stack("Stack2", screen("B1"))
        withBar(first, second)
        push(first, screen("A2"), "A1")
        push(second, QueueScreen(activity, registry, "B2", journal, calls, options {
            bottomTabs.visible = Bool(false)
        }), "B1")
        assertTrue(bar.calls.none { it.startsWith("transition") || it.startsWith("rest") })
    }

    @Test
    fun aPushThatWaitsForItsScreenDoesntMoveTheBar() {
        val first = stack("Stack1", screen("A1"))
        withBar(first, stack("Stack2", screen("B1")))
        val waiting = QueueScreen(activity, registry, "A2", journal, calls, options {
            bottomTabs.visible = Bool(false)
            animations.push.waitForRender = Bool(true)
        }, FakeContent(false))
        push(first, waiting, "A1")
        assertTrue(bar.calls.none { it.startsWith("transition") })
        waiting.content.mount()
        assertEquals(listOf("transition:WithPush:false:true"), bar.calls.filter { it.startsWith("transition") })
    }

    @Test
    fun aTabSwitchPutsARidingBarAtRest() {
        val first = stack("Stack1", screen("A1"))
        val tabs = withBar(first, stack("Stack2", screen("B1")))
        push(first, QueueScreen(activity, registry, "A2", journal, calls, options {
            bottomTabs.visible = Bool(false)
        }), "A1")
        tabs.onTabPressed(1)
        assertTrue("rest:false" in bar.calls)
    }

    // The attach modes on the tabs.

    private val attached = mutableListOf<String>()

    private fun withViews(mode: String): BottomTabsController {
        val tabs = tabs(
            stack("Stack1", screen("A1")),
            stack("Stack2", screen("B1")),
            stack("Stack3", screen("C1")),
            options = options { bottomTabs.tabsAttachMode = Text(mode) },
        )
        tabs.tabViews = object : BottomTabsController.TabViews {
            private val held = HashMap<String, Boolean>()

            override fun holds(tab: ViewController<*>): Boolean = tab.id in held

            override fun attach(tab: ViewController<*>, visible: Boolean) {
                if (tab.id !in held) attached += tab.id
                held[tab.id] = visible
            }

            override fun switch(
                shown: ViewController<*>,
                hidden: ViewController<*>,
                animated: Boolean,
                onEnd: () -> Unit,
            ): () -> Unit {
                onEnd()
                return {}
            }
        }
        tabs.attachAtCreation()
        return tabs
    }

    @Test
    fun togetherAttachesEveryTabAtOnce() {
        val tabs = withViews("together")
        assertEquals(listOf("Stack1", "Stack2", "Stack3"), attached)
        // Made but not selected: they measure their safe area though invisible.
        assertTrue(tabs.measuresThoughNotShown(tabs.childControllers.elementAt(1)))
        assertFalse(tabs.measuresThoughNotShown(tabs.childControllers.first()))
    }

    @Test
    fun afterInitialTabAttachesTheOthersOnceTheFirstAppeared() {
        val tabs = withViews("afterInitialTab")
        assertEquals(listOf("Stack1"), attached)
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        assertEquals(listOf("Stack1", "Stack2", "Stack3"), attached)
    }

    @Test
    fun aTabChosenBeforeTheFirstAppearedIsAttachedAtOnce() {
        val tabs = withViews("afterInitialTab")
        tabs.mergeOptions(options { bottomTabs.currentTabIndex = Fraction(2.0) })
        assertEquals(listOf("Stack1", "Stack3"), attached)
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        // The common attach skips the tabs attached already.
        assertEquals(listOf("Stack1", "Stack3", "Stack2"), attached)
    }

    @Test
    fun onSwitchToTabAttachesATabAtItsFirstSelection() {
        val tabs = withViews("onSwitchToTab")
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        assertEquals(listOf("Stack1"), attached)
        tabs.onTabPressed(2)
        assertEquals(listOf("Stack1", "Stack3"), attached)
    }

    // The items.

    @Test
    fun aMergeFromAScreenOfAnUnselectedTabChangesItsTabsItem() {
        val b1 = screen("B1")
        withBar(stack("Stack1", screen("A1")), stack("Stack2", b1))
        b1.mergeOptions(options { bottomTab.badge = Text("4") })
        assertTrue("Badge:1:4" in bar.calls)
    }

    @Test
    fun aMergeWithTheTabsIdLiesUnderEveryTabsOwn() {
        val own = options { bottomTab.text = Text("Profile") }
        val tabs = withBar(stack("Stack1", screen("A1")), stack("Stack2", screen("B1"), options = own))
        tabs.mergeOptions(options { bottomTab.text = Text("All") })
        assertTrue("Title:0:All" in bar.calls)
        assertTrue("Title:1:Profile" in bar.calls)
    }

    @Test
    fun theRepaintGoesBarFirstThenEveryTabWithTheScreenOverTheTab() {
        val tab = options { bottomTab.textColor = ThemeColour(1, 1) }
        val a1 = QueueScreen(activity, registry, "A1", journal, calls, options {
            bottomTab.textColor = ThemeColour(2, 2)
        })
        val tabs = withBar(stack("Stack1", a1, options = tab), stack("Stack2", screen("B1"), options = tab))
        tabs.repaint(tabs.resolveShownOptions(DefaultOptions.current))
        val repaint = bar.calls.filter { it.startsWith("repaintBar") || it.startsWith("Colors") }
        assertEquals(listOf("repaintBar", "Colors:0:2", "Colors:1:1"), repaint)
    }

    @Test
    fun aRepaintReachesAHiddenTabOnlyWhenItIsSelected() {
        val repaints = Calls()
        val a1 = TestScreen(activity, registry, "A1", Options(), RecordingPresenter(activity, repaints))
        val b1 = TestScreen(activity, registry, "B1", Options(), RecordingPresenter(activity, repaints))
        val tabs = tabs(a1, b1, presenter = bar)
        tabs.onViewWillAppear()
        tabs.onViewDidAppear()
        listOf(a1, b1).forEach { it.getView() }
        // The pass over what shows (`Navigator.repaintLayer`) reaches the selected tab; the tabs' own repaint doesn't
        // repaint the hidden one.
        tabs.repaint(tabs.resolveShownOptions(DefaultOptions.current))
        assertEquals(emptyList<String>(), repaints.entries)
        tabs.onTabPressed(1)
        assertEquals(listOf("repaint:B1"), repaints.entries)
        // Once: shown again without a new repaint, it isn't repainted.
        tabs.onTabPressed(0)
        tabs.onTabPressed(1)
        assertEquals(listOf("repaint:B1"), repaints.entries)
    }

    @Test
    fun theHiddenItemsUnderACustomRowKeepNoTestId() {
        val cell = options { bottomTab.component.name = Text("Cell") }
        tabs(
            stack("Stack1", screen("A1"), options = cell),
            stack("Stack2", screen("B1"), options = cell),
            presenter = bar,
        )
        assertFalse(bar.itemTestIds)
        val plain = Recorder(activity)
        tabs(stack("Stack3", screen("C1")), presenter = plain)
        assertTrue(plain.itemTestIds)
    }

    // A presenter without views that records what it is asked.
    private class Recorder(activity: Activity) : BottomTabsPresenter(activity) {
        val calls = mutableListOf<String>()

        override fun run(calls: List<TabItems.Call>) {
            calls.forEach {
                this.calls += when (it) {
                    is TabItems.Call.Title -> "Title:${it.index}:${it.text}"
                    is TabItems.Call.Badge -> "Badge:${it.index}:${it.text}"
                    is TabItems.Call.Colors -> "Colors:${it.index}:${it.titleInactive}"
                    else -> it.javaClass.simpleName
                }
            }
        }

        override fun setVisible(shown: Boolean, animate: Boolean) {
            calls += "visible:$shown:${if (animate) "animated" else "now"}"
            super.setVisible(shown, animate)
        }

        override fun transition(
            move: TabBarMotion.Move,
            shown: Boolean,
            animate: Boolean,
            side: BottomTabsEnterExitAnimationOptions?,
        ) {
            calls += "transition:${move.name}:$shown:$animate" + if (side != null) ":side" else ""
            super.transition(move, shown, animate, side)
        }

        override fun rest(shown: Boolean) {
            calls += "rest:$shown"
            super.rest(shown)
        }

        override fun repaintBar(options: BottomTabsOptions, direction: LayoutDirection) {
            calls += "repaintBar"
        }
    }

    // A layout around the tabs that records the merges that reach it.
    private class Recording(activity: Activity, registry: ChildControllersRegistry) :
        ParentController<FrameLayout>(activity, registry, "Around", Presenter(activity, Options()), Options()) {
        val merges = mutableListOf<Options>()

        override fun getChildControllers(): Collection<ViewController<*>> = emptyList()

        override fun getCurrentChild(): ViewController<*>? = null

        override fun mergeChildOptions(options: Options, child: ViewController<*>) {
            merges += options
        }

        override fun createView(): FrameLayout = FrameLayout(activity)
    }

    private companion object {
        const val TABS = "Tabs1"
    }
}

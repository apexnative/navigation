package dev.apexnative.navigation.viewcontrollers.bottomtabs

import android.app.Activity
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.react.events.FakeContent
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.stack.FakeStackDisplay
import dev.apexnative.navigation.viewcontrollers.stack.QueueReadiness
import dev.apexnative.navigation.viewcontrollers.stack.QueueScreen
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.stack.StackPresenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * The press path of the tabs, one source for the bar and the custom row ([BottomTabsController.onTabPressed]): the
 * whole order of events for another tab, the selected tab again and `selectTabOnPress: false`, a long press, a press
 * while a switch still fades, and the payloads (`screenId` the top of the tab's stack, the tab's own screen, or none;
 * `bottomTabsId` the tabs' id). The order is the contract of `BottomTabPressedEvent` and `BottomTabSelectedEvent` on
 * both platforms.
 */
class TabPressesTest {
    private val journal = mutableListOf<String>()
    private val fixture = DispatcherFixture(EventJournal(journal))
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val calls = mutableListOf<String>()
    private val views = mutableListOf<String>()
    private val displays = HashMap<StackController, FakeStackDisplay>()

    // The ends of the fades that run, in order.
    private val fades = mutableListOf<() -> Unit>()

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        ScreenReadiness.reset()
    }

    private fun options(change: Options.() -> Unit) = Options().apply(change)

    private fun screen(id: String, options: Options = Options()) =
        QueueScreen(activity, registry, id, journal, calls, options, FakeContent(ready = true))

    private fun stack(id: String, vararg screens: QueueScreen, options: Options = Options()): StackController {
        val stack = StackController(activity, registry, id, StackPresenter(activity), options)
        val display = FakeStackDisplay(views, fixture.scheduler)
        stack.useQueueParts(display, QueueReadiness(journal), null)
        stack.preparedPushes = { null }
        stack.setInitialChildren(screens.toList(), emptyList())
        displays[stack] = display
        return stack
    }

    // The tabs shown, the first tab selected, the journal cleared.
    private fun shown(vararg tabs: ViewController<*>): BottomTabsController {
        val presenter = BottomTabsPresenter(activity)
        val controller = BottomTabsController(activity, registry, TABS, presenter, Options(), tabs.toList())
        controller.shows = { true }
        controller.onViewWillAppear()
        controller.onViewDidAppear()
        journal.clear()
        return controller
    }

    // [shown] with tab views whose switch fades on screen until the test ends it.
    private fun fading(vararg tabs: ViewController<*>): BottomTabsController {
        val controller = shown(*tabs)
        controller.tabViews = object : BottomTabsController.TabViews {
            override fun holds(tab: ViewController<*>): Boolean = true

            override fun attach(tab: ViewController<*>, visible: Boolean) = Unit

            override fun switch(
                shown: ViewController<*>,
                hidden: ViewController<*>,
                animated: Boolean,
                onEnd: () -> Unit,
            ): () -> Unit {
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
        return controller
    }

    private fun events(): List<String> = journal.toList().also { journal.clear() }

    // Another tab.

    @Test
    fun aTabSelectedByThePressComputesBackAgain() {
        // No command ends here: the switch computes what back does (`bottomTabsOnPress`, the stack of the new tab).
        val navigator = Navigator(activity)
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }
        BackCallbackState.attach(navigator, callback)
        try {
            val tabs = shown(stack("Stack1", screen("A1")), stack("Stack2", screen("B1"), screen("B2")))
            // A stale value the computation corrects: the navigation has no root, so back leaves the app.
            callback.isEnabled = true
            tabs.onTabPressed(1)
            assertFalse(callback.isEnabled)
        } finally {
            BackCallbackState.detach(navigator)
        }
    }

    @Test
    fun anotherTabSendsPressedThenTheSwitchThenSelected() {
        val tabs = fading(stack("Stack1", screen("A1")), stack("Stack2", screen("B1"), screen("B2")))
        tabs.onTabPressed(1)
        // Pressed with the top of the tab's stack, before the selection; the new tab's WillAppear as the fade starts.
        assertEquals(listOf("tabPressed:$TABS:1:B2:false", "will:B2"), events())
        assertEquals(1, tabs.selectedIndex)
        fades.single()()
        // At the end: the switch's appearance, then Selected (selected, unselected, the tabs' id), then the state.
        assertEquals(
            listOf("didDisappear:A1", "didAppear:B2", "stateNeeded", "tabSelected:$TABS:1:0", "stateNeeded"),
            events(),
        )
    }

    @Test
    fun theLeftTabsPopToRootOnBlurComesRightAfterSelected() {
        val blur = options { bottomTab.popToRootOnBlur = Bool(true) }
        val first = stack("Stack1", screen("A1"), screen("A2"), options = blur)
        val tabs = shown(first, stack("Stack2", screen("B1")))
        displays.getValue(first).isOnScreen = false
        tabs.onTabPressed(1)
        assertEquals(
            listOf(
                "tabPressed:$TABS:1:B1:false",
                "will:B1",
                "didDisappear:A2",
                "didAppear:B1",
                "stateNeeded",
                "tabSelected:$TABS:1:0",
                "popped:A2",
                "destroyed:A2",
                "stateNeeded",
            ),
            events().filter { it != "stateNow" },
        )
    }

    @Test
    fun aTabThatIsAScreenNamesItselfAndATabWithoutAScreenNamesNone() {
        val tabs = shown(stack("Stack1", screen("A1")), screen("B1"), Empty(activity, registry))
        tabs.onTabPressed(1)
        assertEquals("tabPressed:$TABS:1:B1:false", events().first())
        tabs.onTabPressed(2)
        // No screen: no screenId (the emitter leaves the key out), and Selected at once, as no DidAppear comes.
        assertEquals(
            listOf("tabPressed:$TABS:2:null:false", "didDisappear:B1", "tabSelected:$TABS:2:1", "stateNeeded"),
            events(),
        )
    }

    // The selected tab again.

    @Test
    fun theSelectedTabAgainSendsPressedThenThePopsOwnEventsAndNoSelected() {
        val first = stack("Stack1", screen("A1"), screen("A2"))
        val tabs = shown(first, stack("Stack2", screen("B1")))
        tabs.onTabPressed(0)
        assertEquals(listOf("tabPressed:$TABS:0:A2:true", "will:A1"), events())
        displays.getValue(first).finishAll()
        // The pop of a user's back: the appearance, the state, then ScreenPopped; never Selected.
        assertEquals(
            listOf("didDisappear:A2", "didAppear:A1", "stateNow", "popped:A2", "destroyed:A2"),
            events().filter { it != "stateNeeded" },
        )
        assertEquals(0, tabs.selectedIndex)
        assertEquals(listOf("A1"), first.childControllers.map { it.id })
    }

    @Test
    fun theSelectedTabAgainOnItsFirstScreenSendsPressedOnly() {
        val tabs = shown(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")))
        tabs.onTabPressed(0)
        // The content scrolls to the top; no event goes for it.
        assertEquals(listOf("tabPressed:$TABS:0:A1:true"), events())
    }

    @Test
    fun theSelectedTabAgainWithPopToRootOnPressFalseSendsPressedOnly() {
        val keep = options { bottomTab.popToRootOnPress = Bool(false) }
        val first = stack("Stack1", screen("A1"), screen("A2"), options = keep)
        val tabs = shown(first, stack("Stack2", screen("B1")))
        tabs.onTabPressed(0)
        assertEquals(listOf("tabPressed:$TABS:0:A2:true"), events())
        assertEquals(2, first.childControllers.size)
    }

    // selectTabOnPress: false.

    @Test
    fun selectTabOnPressFalseSendsPressedOnlyForAnotherTabAndTheSelectedOne() {
        val button = options { bottomTab.selectTabOnPress = Bool(false) }
        val first = stack("Stack1", screen("A1"), screen("A2"), options = button)
        val tabs = shown(first, stack("Stack2", screen("B1"), options = button))
        tabs.onTabPressed(1)
        assertEquals(listOf("tabPressed:$TABS:1:B1:false"), events())
        assertEquals(0, tabs.selectedIndex)
        tabs.onTabPressed(0)
        assertEquals(listOf("tabPressed:$TABS:0:A2:true"), events())
        assertEquals(2, first.childControllers.size)
    }

    @Test
    fun selectTabOnPressOfTheShownScreenCountsOverItsTabs() {
        val button = options { bottomTab.selectTabOnPress = Bool(false) }
        val tabs = shown(stack("Stack1", screen("A1")), stack("Stack2", screen("B1", button)))
        tabs.onTabPressed(1)
        assertEquals(listOf("tabPressed:$TABS:1:B1:false"), events())
        assertEquals(0, tabs.selectedIndex)
    }

    // A long press.

    @Test
    fun aLongPressSendsLongPressedOnly() {
        val tabs = shown(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")))
        tabs.onTabLongPressed(1)
        tabs.onTabLongPressed(0)
        assertEquals(listOf("tabLongPressed:$TABS:1", "tabLongPressed:$TABS:0"), events())
        assertEquals(0, tabs.selectedIndex)
    }

    // A press while a switch still fades: the switch ends and its Selected goes before the press's first event.

    @Test
    fun theSameTabAgainDuringItsFadeEndsTheSwitchBeforeItsPressed() {
        val tabs = fading(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")))
        tabs.onTabPressed(1)
        events()
        tabs.onTabPressed(1)
        assertEquals(
            listOf(
                "didDisappear:A1",
                "didAppear:B1",
                "stateNeeded",
                "tabSelected:$TABS:1:0",
                "stateNeeded",
                "tabPressed:$TABS:1:B1:true",
            ),
            events(),
        )
    }

    @Test
    fun aLongPressDuringAFadeEndsTheSwitchBeforeItsEvent() {
        val tabs = fading(stack("Stack1", screen("A1")), stack("Stack2", screen("B1")), stack("Stack3", screen("C1")))
        tabs.onTabPressed(1)
        events()
        tabs.onTabLongPressed(2)
        assertEquals(
            listOf(
                "didDisappear:A1",
                "didAppear:B1",
                "stateNeeded",
                "tabSelected:$TABS:1:0",
                "stateNeeded",
                "tabLongPressed:$TABS:2",
            ),
            events(),
        )
    }

    @Test
    fun aButtonTabDuringAFadeEndsTheSwitchBeforeItsPressed() {
        val button = options { bottomTab.selectTabOnPress = Bool(false) }
        val tabs = fading(
            stack("Stack1", screen("A1")),
            stack("Stack2", screen("B1")),
            stack("Stack3", screen("C1"), options = button),
        )
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
            ),
            events(),
        )
        assertEquals(1, tabs.selectedIndex)
    }

    // A tab that is a layout without a screen.
    private class Empty(activity: Activity, registry: ChildControllersRegistry) :
        ParentController<FrameLayout>(activity, registry, "Empty1", Presenter(activity, Options()), Options()) {
        override fun getChildControllers(): Collection<ViewController<*>> = emptyList()

        override fun getCurrentChild(): ViewController<*>? = null

        override fun createView(): FrameLayout = FrameLayout(activity)
    }

    private companion object {
        const val TABS = "Tabs1"
    }
}

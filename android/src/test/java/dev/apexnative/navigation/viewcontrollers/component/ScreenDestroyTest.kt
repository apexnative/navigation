package dev.apexnative.navigation.viewcontrollers.component

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.slots.SlotContainers
import dev.apexnative.navigation.slots.SlotScreenView
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A React screen reports through its content's record, and goes once: DidDisappear when it showed, out of the registry,
 * its content stopped, its data gone; a slot screen's `ANN.SlotDestroyed` only while the id is its own, not for a
 * prepared push, not for a JavaScript that went.
 */
class ScreenDestroyTest {
    private val fixture = DispatcherFixture()
    private val entries = fixture.journal.entries
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    private fun screen(id: String, name: String) =
        ComponentViewController(activity, registry, id, name, Presenter(activity, Options()), Options())

    private fun slotOf(screen: ComponentViewController) = screen.view.reactView as SlotScreenView

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        listOf("Home1", "Details2").forEach(SlotContainers::remove)
        ScreenReadiness.reset()
    }

    @Test
    fun aSlotScreenAppearsOnceItsContentMounted() {
        val home = screen("Home1", "slot:Home")
        val slot = slotOf(home)
        home.onViewWillAppear()
        home.onViewDidAppear()
        assertTrue(entries.isEmpty())
        slot.slotContentDidMount()
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
        assertEquals("will:Home1:Home:Component", fixture.journal.payloads.first())
    }

    @Test
    fun aReactScreenAppearsByTheLimit() {
        val home = screen("Home1", "Home")
        home.getView()
        home.onViewDidAppear()
        assertTrue(entries.isEmpty())
        fixture.scheduler.advance(AppearanceDispatcher.HOLD_LIMIT_MILLIS)
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
    }

    @Test
    fun aReactViewTakesTheTypeOfItsFirstCall() {
        val title = ReactView(activity, "Title3", "TitleView")
        title.sendComponentWillStart(ComponentType.TopBarTitle)
        title.sendComponentStart(ComponentType.TopBarTitle)
        title.markReadyForTest()
        title.sendComponentStop(ComponentType.TopBarTitle)
        assertEquals(
            listOf(
                "will:Title3:TitleView:TopBarTitle",
                "didAppear:Title3:TitleView:TopBarTitle",
                "didDisappear:Title3:TitleView:TopBarTitle",
            ),
            fixture.journal.payloads,
        )
    }

    @Test
    fun aShownScreenDisappearsThenItsContentStops() {
        val home = screen("Home1", "slot:Home")
        val slot = slotOf(home)
        slot.slotContentDidMount()
        home.onViewDidAppear()
        entries.clear()
        ScreenReadiness.setRendersAtOnce("Home1", this, true)
        home.destroy()
        assertEquals(listOf("didDisappear:Home1", "stateNeeded", "slotDestroyed:Home1"), entries)
        assertTrue(home.isDestroyed)
        assertNull(registry.find("Home1"))
        assertNull(SlotContainers.get("Home1"))
        assertTrue(ScreenReadiness.waitsForReadiness("Home1"))
        home.destroy()
        assertEquals(3, entries.size)
    }

    @Test
    fun aScreenWhoseWillAppearIsHeldGoesWithoutAppearanceEvents() {
        val home = screen("Home1", "slot:Home")
        slotOf(home)
        home.onViewDidAppear()
        home.destroy()
        assertEquals(listOf("slotDestroyed:Home1"), entries)
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aScreenNeverShownMakesNoViewToGo() {
        val home = screen("Home1", "Home")
        home.destroy()
        assertTrue(home.isDestroyed)
        assertNull(home.peekView())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun aPreparedPushSendsNoSlotDestroyed() {
        val details = screen("Details2", "slot:Details")
        slotOf(details).isPreparedPush = true
        details.destroy()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun aLaterScreenWithTheSameIdKeepsTheSlot() {
        val first = SlotScreenView(activity, "Details2", "Details")
        val later = SlotScreenView(activity, "Details2", "Details")
        first.destroy()
        assertTrue(entries.isEmpty())
        assertSame(later, SlotContainers.get("Details2"))
        later.destroy()
        assertEquals(listOf("slotDestroyed:Details2"), entries)
    }

    @Test
    fun theScreensOfAJavaScriptThatWentGoSilently() {
        val home = screen("Home1", "slot:Home")
        slotOf(home).slotContentDidMount()
        home.onViewDidAppear()
        entries.clear()
        ReloadCoordinator.advance()
        home.destroy()
        assertTrue(entries.isEmpty())
        assertTrue(home.isDestroyed)
    }

    @Test
    fun aLayoutDestroysItsChildrenAndOnlyTheShownOneDisappears() {
        val home = screen("Home1", "slot:Home")
        val details = screen("Details2", "slot:Details")
        val stack = TestLayout(activity, registry, "Stack1")
        stack.add(home)
        stack.add(details)
        slotOf(home).slotContentDidMount()
        slotOf(details).slotContentDidMount()
        stack.onViewDidAppear()
        entries.clear()
        stack.destroy()
        assertEquals(
            listOf("slotDestroyed:Home1", "didDisappear:Details2", "stateNeeded", "slotDestroyed:Details2"),
            entries,
        )
        assertTrue(registry.all().isEmpty())
    }

    @Test
    fun aDestroyedScreenMakesNoView() {
        val first = screen("Home1", "slot:Home")
        first.destroy()
        val later = screen("Home1", "slot:Home")
        val slot = slotOf(later)
        val error = assertThrows(IllegalStateException::class.java) { first.view }
        assertEquals("the node 'Home1' is destroyed", error.message)
        assertNull(first.peekView())
        assertSame(slot, SlotContainers.get("Home1"))
    }

    @Test
    fun aLaterScreenWithTheSameIdKeepsTheData() {
        val first = screen("Home1", "Home")
        val later = screen("Home1", "Home")
        ScreenReadiness.setRendersAtOnce("Home1", this, true)
        first.destroy()
        assertSame(later, registry.find("Home1"))
        assertFalse(ScreenReadiness.waitsForReadiness("Home1"))
        later.destroy()
        assertTrue(ScreenReadiness.waitsForReadiness("Home1"))
    }

    @Test
    fun aScreenAloneWithItsIdTakesItsDataAway() {
        val home = screen("Home1", "Home")
        ScreenReadiness.setRendersAtOnce("Home1", this, true)
        home.destroy()
        assertTrue(ScreenReadiness.waitsForReadiness("Home1"))
    }

    @Test
    fun aScreenThatAppearsMakesItsViewAndOneThatDisappearsMakesNone() {
        val home = screen("Home1", "slot:Home")
        home.onViewDisappear()
        assertNull(home.peekView())
        home.onViewWillAppear()
        slotOf(home).slotContentDidMount()
        assertEquals(listOf("will:Home1"), entries)
        val details = screen("Details2", "slot:Details")
        details.onViewDidAppear()
        slotOf(details).slotContentDidMount()
        assertEquals(listOf("will:Home1", "will:Details2", "didAppear:Details2", "stateNeeded"), entries)
    }

    @Test
    fun aDestroyedReactViewWithAHeldWillAppearSendsNothingLater() {
        val title = ReactView(activity, "Title3", "TitleView")
        title.sendComponentWillStart(ComponentType.TopBarTitle)
        title.destroy()
        fixture.scheduler.advance(AppearanceDispatcher.HOLD_LIMIT_MILLIS)
        assertTrue(entries.isEmpty())
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aDestroyedReactViewThatAppearedDisappears() {
        val title = ReactView(activity, "Title3", "TitleView")
        title.sendComponentStart(ComponentType.TopBarTitle)
        fixture.scheduler.advance(AppearanceDispatcher.HOLD_LIMIT_MILLIS)
        entries.clear()
        title.destroy()
        assertEquals(listOf("didDisappear:Title3", "stateNeeded"), entries)
    }

    @Test
    fun aDestroyedSlotScreenViewWithAHeldWillAppearSendsOnlySlotDestroyed() {
        val details = SlotScreenView(activity, "Details2", "Details")
        details.sendComponentStart(ComponentType.Component)
        details.destroy()
        fixture.scheduler.advance(AppearanceDispatcher.HOLD_LIMIT_MILLIS)
        assertEquals(listOf("slotDestroyed:Details2"), entries)
    }

    @Test
    fun onlyTheOwnerOfTheSlotSetsItsTouches() {
        val first = SlotScreenView(activity, "Details2", "Details")
        val later = SlotScreenView(activity, "Details2", "Details")
        first.setPassThroughTouches(true)
        assertSame(later, SlotContainers.get("Details2"))
        later.destroy()
        later.setPassThroughTouches(true)
        assertNull(SlotContainers.get("Details2"))
    }

    // A command's readiness wait ended for the view's content.
    private fun ReactView.markReadyForTest() = appearance.markReady()
}

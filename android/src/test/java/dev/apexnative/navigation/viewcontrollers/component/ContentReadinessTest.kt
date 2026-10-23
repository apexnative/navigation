package dev.apexnative.navigation.viewcontrollers.component

import android.app.Activity
import dev.apexnative.navigation.react.ReactView
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.slots.SlotContainers
import dev.apexnative.navigation.slots.SlotScreenView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * When the content of a screen is ready: a React root once its safe area view said it rendered, or, without one, once
 * its surface's root has children; a slot screen once its content mounted and rendered (or mounted, without a safe area
 * view). What waits runs once; the held appearance events go when it is ready, or at a command's limit.
 */
class ContentReadinessTest {
    private val fixture = DispatcherFixture()
    private val entries = fixture.journal.entries
    private val activity = Activity()
    private var told = 0

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        ScreenReadiness.reset()
        listOf("Home1", "Details2").forEach(SlotContainers::remove)
    }

    @Test
    fun aReactRootIsReadyOnceItsSafeAreaViewSaysItRendered() {
        val home = ReactView(activity, "Home1", "Home")
        ScreenReadiness.wrapped("Home1")
        home.whenReady { told++ }
        home.contentChildCount = { 1 }
        home.checkReadiness()
        assertFalse(home.isReady())
        assertEquals(0, told)
        ScreenReadiness.contentRendered("Home1")
        assertTrue(home.isReady())
        assertEquals(1, told)
        home.whenReady { told++ }
        assertEquals(2, told)
    }

    @Test
    fun aReactRootTakesOnlyTheRenderOfItsOwnSurface() {
        val home = ReactView(activity, "Home1", "Home")
        home.ownSource = { NEW_SURFACE }
        ScreenReadiness.wrapped("Home1")
        home.whenReady { told++ }
        ScreenReadiness.contentRendered("Home1", OLD_SURFACE)
        assertFalse(home.isReady())
        assertEquals(0, told)
        ScreenReadiness.contentRendered("Home1", NEW_SURFACE)
        assertTrue(home.isReady())
        assertEquals(1, told)
    }

    @Test
    fun aReactRootWhoseSurfaceRenderedBeforeItWaitedIsReadyAtOnce() {
        ScreenReadiness.contentRendered("Home1", NEW_SURFACE)
        val home = ReactView(activity, "Home1", "Home")
        home.ownSource = { NEW_SURFACE }
        home.whenReady { told++ }
        assertEquals(1, told)
    }

    @Test
    fun twoRootsOfOneBarComponentIdEachWaitForTheirOwnRender() {
        val first = ReactView(activity, "Title3", "Title", rendersAtOnce = true).apply { ownSource = { OLD_SURFACE } }
        val second = ReactView(activity, "Title3", "Title", rendersAtOnce = true).apply { ownSource = { NEW_SURFACE } }
        ScreenReadiness.wrapped("Title3")
        first.whenReady { told++ }
        second.whenReady { told += 10 }
        ScreenReadiness.contentRendered("Title3", OLD_SURFACE)
        assertEquals(1, told)
        assertFalse(second.isReady())
        first.destroy()
        assertFalse(ScreenReadiness.waitsForReadiness("Title3"))
        ScreenReadiness.contentRendered("Title3", NEW_SURFACE)
        assertEquals(11, told)
        second.destroy()
        assertTrue(ScreenReadiness.waitsForReadiness("Title3"))
    }

    @Test
    fun aReactRootWithoutASafeAreaViewIsReadyOnceItsRootHasChildren() {
        val home = ReactView(activity, "Home1", "Home")
        home.whenReady { told++ }
        home.whenReady { told++ }
        home.checkReadiness()
        assertFalse(home.isReady())
        home.contentChildCount = { 1 }
        home.checkReadiness()
        home.checkReadiness()
        assertTrue(home.isReady())
        assertEquals(2, told)
        // Ready for good: the children may go, it stays ready.
        home.contentChildCount = { 0 }
        assertTrue(home.isReady())
    }

    @Test
    fun theHeldAppearanceGoesWhenTheRootGetsReady() {
        val home = ReactView(activity, "Home1", "Home")
        ScreenReadiness.wrapped("Home1")
        home.sendComponentStart(ComponentType.Component)
        assertTrue(entries.isEmpty())
        ScreenReadiness.contentRendered("Home1")
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun theHeldAppearanceGoesAtACommandsLimit() {
        val home = ReactView(activity, "Home1", "Home")
        ScreenReadiness.wrapped("Home1")
        home.sendComponentStart(ComponentType.Component)
        home.appearance.markReady()
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
    }

    @Test
    fun aDestroyedRootTellsNothing() {
        val home = ReactView(activity, "Home1", "Home")
        home.whenReady { told++ }
        home.destroy()
        ScreenReadiness.contentRendered("Home1")
        assertEquals(0, told)
    }

    @Test
    fun aSlotScreenIsReadyOnceMountedAndRendered() {
        val details = SlotScreenView(activity, "Details2", "Details")
        details.whenReady { told++ }
        ScreenReadiness.wrapped("Details2")
        details.slotContentDidMount()
        assertFalse(details.isReady())
        ScreenReadiness.contentRendered("Details2")
        assertTrue(details.isReady())
        assertEquals(1, told)
    }

    @Test
    fun aSlotScreenRenderedBeforeItsMountIsReadyAtTheMount() {
        val details = SlotScreenView(activity, "Details2", "Details")
        details.whenReady { told++ }
        ScreenReadiness.wrapped("Details2")
        ScreenReadiness.contentRendered("Details2")
        assertFalse(details.isReady())
        details.slotContentDidMount()
        assertTrue(details.isReady())
        assertEquals(1, told)
    }

    @Test
    fun aSlotScreenWithoutASafeAreaViewIsReadyOnceMounted() {
        val details = SlotScreenView(activity, "Details2", "Details")
        details.whenReady { told++ }
        details.slotContentDidMount()
        details.slotContentDidMount()
        assertTrue(details.isReady())
        assertEquals(1, told)
        details.whenReady { told++ }
        assertEquals(2, told)
    }

    @Test
    fun aSlotScreensHeldAppearanceWaitsForItsRender() {
        val details = SlotScreenView(activity, "Details2", "Details")
        ScreenReadiness.wrapped("Details2")
        details.sendComponentStart(ComponentType.Component)
        details.slotContentDidMount()
        assertTrue(entries.isEmpty())
        ScreenReadiness.contentRendered("Details2")
        assertEquals(listOf("will:Details2", "didAppear:Details2", "stateNeeded"), entries)
    }

    @Test
    fun aSlotScreenThatNeverRendersAppearsAtTheHoldLimit() {
        val details = SlotScreenView(activity, "Details2", "Details")
        ScreenReadiness.wrapped("Details2")
        details.sendComponentStart(ComponentType.Component)
        details.slotContentDidMount()
        fixture.scheduler.advance(AppearanceDispatcher.HOLD_LIMIT_MILLIS)
        assertEquals(listOf("will:Details2", "didAppear:Details2", "stateNeeded"), entries)
    }

    private companion object {
        // Two React surfaces: the one of an old root, the one of the root that waits.
        const val OLD_SURFACE = 1
        const val NEW_SURFACE = 11
    }
}

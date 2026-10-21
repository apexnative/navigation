package dev.apexnative.navigation.viewcontrollers.parent

import android.app.Activity
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.ReportingScreen
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The moves of a container's children in the dispatcher's order: `WillAppear` of what comes at the start, then
 * `DidDisappear` of what left and `DidAppear` of what came, as the split view's panes and the pair's screens move.
 */
class ShownChildrenTest {
    private val fixture = DispatcherFixture()
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val list = ReportingScreen(activity, registry, "List")
    private val details = ReportingScreen(activity, registry, "Details")
    private val compact = ReportingScreen(activity, registry, "Compact")
    private var shown: List<ViewController<*>> = emptyList()
    private val children = ShownChildren { shown }

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    // The container appears showing [now], as its own appearance tells its children (ParentController).
    private fun appear(vararg now: ViewController<*>) {
        shown = now.toList()
        children.containerWillAppear()
        shown.forEach { it.onViewWillAppear() }
        children.containerDidAppear()
        shown.forEach { it.onViewDidAppear() }
        children.moved()
        fixture.journal.entries.clear()
    }

    private fun events(): List<String> = fixture.journal.entries.filter { it != "stateNeeded" && it != "stateNow" }

    @Test
    fun closingTheSlidingPaneSendsWillAppearAtTheStartAndTheRestAtTheEnd() {
        appear(details)
        children.moveStarted(listOf(list, details))
        assertEquals(listOf("will:List"), events())
        // More frames of the slide send nothing more.
        children.moveStarted(listOf(list, details))
        shown = listOf(list)
        children.moved()
        assertEquals(listOf("will:List", "didDisappear:Details", "didAppear:List"), events())
    }

    @Test
    fun compactToWideSendsTheDisappearanceBeforeTheAppearances() {
        appear(compact)
        shown = listOf(list, details)
        children.moved()
        assertEquals(
            listOf("will:List", "will:Details", "didDisappear:Compact", "didAppear:List", "didAppear:Details"),
            events(),
        )
        // The state is the posted update of the pass, never sent at once.
        assertEquals("stateNeeded", fixture.journal.entries.last())
        assertTrue("stateNow" !in fixture.journal.entries)
    }

    @Test
    fun aSlideThatGoesBackMakesTheComingPaneDisappear() {
        appear(details)
        children.moveStarted(listOf(list, details))
        children.moved()
        assertEquals(listOf("will:List", "didDisappear:List"), events())
    }

    @Test
    fun nothingMovesBeforeTheContainerAppearedNorWhenNothingChanged() {
        shown = listOf(details)
        children.moveStarted(listOf(list, details))
        children.moved()
        assertEquals(emptyList<String>(), fixture.journal.entries)
        appear(details)
        children.moved()
        assertEquals(emptyList<String>(), fixture.journal.entries)
    }

    @Test
    fun theContainerThatDisappearedForgetsItsChildren() {
        appear(details)
        children.containerDisappeared()
        details.onViewDisappear()
        fixture.journal.entries.clear()
        shown = listOf(list)
        children.moved()
        assertEquals(emptyList<String>(), fixture.journal.entries)
    }
}

package dev.apexnative.navigation.viewcontrollers

import android.app.Activity
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.ReportingScreen
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The layouts say when their children appear and disappear: will/did appear to the children they show, disappear to
 * every child, of which those that didn't appear tell nothing.
 */
class AppearanceForwardingTest {
    private val fixture = DispatcherFixture()
    private val entries = fixture.journal.entries
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    private fun screen(id: String) = ReportingScreen(activity, registry, id)

    private fun layout(id: String, vararg children: ViewController<*>) =
        TestLayout(activity, registry, id).apply { children.forEach(::add) }

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    @Test
    fun aLayoutTellsItsCurrentChildAndEveryChildOnDisappear() {
        val home = screen("Home")
        val details = screen("Details")
        val stack = layout("Stack1", home, details)
        stack.onViewWillAppear()
        assertEquals(listOf("will:Details"), entries)
        stack.onViewDidAppear()
        stack.onViewDisappear()
        assertEquals(
            listOf("will:Details", "didAppear:Details", "stateNeeded", "didDisappear:Details", "stateNeeded"),
            entries,
        )
    }

    @Test
    fun nestedLayoutsTellTheScreenAtTheEnd() {
        val home = screen("Home")
        val settings = screen("Settings")
        val first = layout("Stack1", home)
        val second = layout("Stack2", settings)
        val tabs = layout("Tabs1", first, second)
        tabs.current = first
        tabs.onViewDidAppear()
        assertEquals(listOf("will:Home", "didAppear:Home", "stateNeeded"), entries)
        entries.clear()
        tabs.current = second
        AppearanceDispatcher.beginTransition(listOf(second))
        AppearanceDispatcher.endTransition(listOf(first), listOf(second), null, null, null)
        assertEquals(
            listOf(
                "will:Settings",
                "didDisappear:Home",
                "stateNeeded",
                "didAppear:Settings",
                "stateNeeded",
                "stateNow",
            ),
            entries,
        )
    }

    @Test
    fun aLayoutThatShowsSeveralChildrenTellsThemAll() {
        val center = screen("Home")
        val side = screen("Settings")
        val menu = object : TestLayout(activity, registry, "Menu1") {
            override fun visibleChildren(): List<ViewController<*>> = children.toList()
        }
        menu.add(center)
        menu.add(side)
        menu.onViewDidAppear()
        menu.onViewDisappear()
        assertEquals(
            listOf(
                "will:Home",
                "didAppear:Home",
                "stateNeeded",
                "will:Settings",
                "didAppear:Settings",
                "stateNeeded",
                "didDisappear:Home",
                "stateNeeded",
                "didDisappear:Settings",
                "stateNeeded",
            ),
            entries,
        )
    }

    @Test
    fun theLayoutsListenersHearItAfterItsChildren() {
        val stack = layout("Stack1", screen("Home"))
        stack.addOnAppearedListener { entries += "appeared:Stack1" }
        stack.onViewDidAppear()
        assertEquals(listOf("will:Home", "didAppear:Home", "stateNeeded", "appeared:Stack1"), entries)
    }

    @Test
    fun hiddenChildrenTellNothing() {
        val stack = layout("Stack1", screen("Home"), screen("Details"))
        stack.onViewDisappear()
        assertEquals(emptyList<String>(), entries)
    }
}

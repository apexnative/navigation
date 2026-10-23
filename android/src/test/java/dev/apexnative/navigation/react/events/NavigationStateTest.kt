package dev.apexnative.navigation.react.events

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.viewcontrollers.bottomtabs.BottomTabsController
import dev.apexnative.navigation.viewcontrollers.bottomtabs.BottomTabsPresenter
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The state [NavigationState] finds in a layout: the screen at the end of the current children, and the selected tab
 * of the nearest tabs it is in, with nested tabs too.
 */
class NavigationStateTest {
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    private fun screen(id: String) = TestScreen(activity, registry, id)

    // A layout of one child, as a stack of one screen or a side menu's center.
    private fun around(id: String, child: ViewController<*>) = TestLayout(activity, registry, id).also { it.add(child) }

    private fun tabs(id: String, selected: Int, vararg tabs: ViewController<*>) = BottomTabsController(
        activity,
        registry,
        id,
        BottomTabsPresenter(activity),
        Options().apply { bottomTabs.currentTabIndex = Fraction(selected.toDouble()) },
        tabs.toList(),
    )

    @Test
    fun oneLevelOfTabsGivesItsSelectedTab() {
        val root = tabs("Tabs1", 1, around("Stack1", screen("Home")), around("Stack2", screen("Settings")))
        assertEquals(NavigationState.State("Settings", null, 1), NavigationState.stateOf(root))
    }

    @Test
    fun nestedTabsGiveTheTabOfTheNearestTabs() {
        val inner = tabs(
            "Tabs2",
            2,
            around("Stack1", screen("Home")),
            around("Stack2", screen("Profile")),
            around("Stack3", screen("Details")),
        )
        val root = tabs("Tabs1", 0, around("SideMenu1", inner), around("Stack4", screen("Settings")))
        assertEquals(NavigationState.State("Details", null, 2), NavigationState.stateOf(root))
    }

    @Test
    fun aScreenOutOfTabsHasNoTab() {
        val root = around("Stack1", screen("Home"))
        assertEquals(NavigationState.State("Home", null, null), NavigationState.stateOf(root))
    }

    @Test
    fun aLayoutWithoutAScreenGivesNone() {
        val empty = NavigationState.State(null, null, null)
        assertEquals(empty, NavigationState.stateOf(null))
        assertEquals(empty, NavigationState.stateOf(tabs("Tabs1", 0, TestLayout(activity, registry, "Stack1"))))
    }
}

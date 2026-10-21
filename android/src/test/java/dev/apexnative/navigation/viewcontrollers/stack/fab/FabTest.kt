package dev.apexnative.navigation.viewcontrollers.stack.fab

import android.app.Activity
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.FabOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.stack.StackController
import dev.apexnative.navigation.viewcontrollers.stack.StackPresenter
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPlan.Step
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPlacement.Edges
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPlacement.Place
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPlacement.Tabs
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The FAB of a stack (`fab.*`): its source, what a change of the top or a merge does with it, where it sits, and a
 * press on the FAB of a screen that went.
 */
class FabTest {
    private val journal = EventJournal()
    private val fixture = DispatcherFixture(journal)
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        DefaultOptions.set(Options())
    }

    private fun fab(id: String? = null, actions: List<FabOptions>? = null) = FabOptions().apply {
        id?.let { this.id = Text(it) }
        this.actions = actions
    }

    // The source.

    @Test
    fun theTopTakesTheFabOfTheStackAndOfTheDefaults() {
        val stack = StackController(activity, registry, "Stack1", StackPresenter(activity), Options().apply {
            fab.id = Text("compose")
        })
        val home = TestScreen(activity, registry, "Home")
        stack.setInitialChildren(listOf(home), emptyList())
        assertEquals("compose", FabPlan.idOf(stack.resolvedOf(home).fab))
        stack.options = Options()
        DefaultOptions.set(Options().apply { fab.id = Text("add") })
        assertEquals("add", FabPlan.idOf(stack.resolvedOf(home).fab))
        home.options = Options().apply { fab.id = Text("own") }
        assertEquals("own", FabPlan.idOf(stack.resolvedOf(home).fab))
    }

    @Test
    fun aScreenOutOfAStackHasNoStackToShowAFab() {
        val home = TestScreen(activity, registry, "Home", Options().apply { fab.id = Text("add") })
        // Nothing around it shows a FAB: only a stack does.
        assertEquals(null, home.findParentStack())
    }

    // The steps.

    @Test
    fun theStepsOfATopThatChanges() {
        assertEquals(Step.None, FabPlan.step(null, false, fab()))
        assertEquals(Step.Create, FabPlan.step(null, false, fab("add")))
        assertEquals(Step.Update, FabPlan.step("add", false, fab("add")))
        assertEquals(Step.Replace, FabPlan.step("add", false, fab("edit")))
        assertEquals(Step.Remove, FabPlan.step("add", false, fab()))
    }

    @Test
    fun actionsMakeAMenuAndAnEmptyListMakesAButtonAgain() {
        val menu = fab("add", listOf(fab("one"), fab("two")))
        assertTrue(FabPlan.isMenu(menu))
        assertEquals(Step.Replace, FabPlan.step("add", false, menu))
        assertEquals(Step.Update, FabPlan.step("add", true, menu))
        // A merge of `actions: []` over the menu's options.
        val merged = menu.copy().also { it.mergeWith(fab(actions = emptyList())) }
        assertFalse(FabPlan.isMenu(merged))
        assertEquals(Step.Replace, FabPlan.step("add", true, merged))
    }

    @Test
    fun anEmptyIdIsNoFab() {
        assertEquals(null, FabPlan.idOf(fab("")))
        assertEquals(Step.Remove, FabPlan.step("add", false, fab("")))
    }

    // The press.

    @Test
    fun aPressOnTheFabOfAScreenThatWentDoesNothing() {
        val home = TestScreen(activity, registry, "Home")
        FabPresenter.press(home, "add")
        home.destroy()
        FabPresenter.press(home, "add")
        FabPresenter.press(null, "add")
        assertEquals(listOf("pressed:Home:add"), journal.entries)
    }

    // The place.

    private fun place(
        navigationBar: Edges = Edges(bottom = 48),
        cutout: Edges = Edges(),
        tabs: Tabs? = null,
        rtl: Boolean = false,
        alignStart: Boolean = false,
        shadowInset: Int = 0,
    ) = FabPlacement.place(navigationBar, cutout, tabs, density = 2f, rtl, alignStart, shadowInset)

    @Test
    fun overTheNavigationBarAtTheEndOfTheLine() {
        assertEquals(Place(onLeft = false, sideMargin = 32, bottomMargin = 80), place())
    }

    @Test
    fun overTheTabBarAtRestWhenItIsHigher() {
        assertEquals(232, place(tabs = Tabs(restingHeight = 200, compact = false)).bottomMargin)
        // A compact tab bar also adds the navigation bar.
        assertEquals(280, place(tabs = Tabs(restingHeight = 200, compact = true)).bottomMargin)
        // A tab bar lower than the navigation bar: the navigation bar decides.
        assertEquals(80, place(tabs = Tabs(restingHeight = 20, compact = false)).bottomMargin)
    }

    @Test
    fun withoutTabsTheNavigationBarDecides() {
        assertEquals(32, place(navigationBar = Edges()).bottomMargin)
    }

    @Test
    fun theSideFollowsTheDirection() {
        assertFalse(place().onLeft)
        assertTrue(place(alignStart = true).onLeft)
        assertTrue(place(rtl = true).onLeft)
        assertFalse(place(rtl = true, alignStart = true).onLeft)
    }

    @Test
    fun theSideInsetsOfTheNavigationBarAndTheCutoutCount() {
        assertEquals(32 + 96, place(navigationBar = Edges(right = 96)).sideMargin)
        assertEquals(32 + 60, place(cutout = Edges(left = 60), alignStart = true).sideMargin)
        assertEquals(32 + 96, place(navigationBar = Edges(right = 96), cutout = Edges(right = 40)).sideMargin)
    }

    @Test
    fun aMenuKeepsRoomForItsShadows() {
        val plain = place()
        val menu = place(shadowInset = 32)
        assertEquals(plain.sideMargin - 32, menu.sideMargin)
        assertEquals(plain.bottomMargin - 32, menu.bottomMargin)
    }
}

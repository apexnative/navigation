package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.options.LargeTitleOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure rules of the bar: the places of the right buttons, the room the bar takes, the large title's mode and
 * collapse and the row's timeline.
 */
class TopBarRulesTest {
    // Places.

    private fun buttons(vararg priorities: Int, pinned: Set<Int> = emptySet()) =
        priorities.mapIndexed { index, priority -> ButtonPlaces.Button(priority, index in pinned) }

    @Test
    fun appCompatsCountOfPlacesForTheWindow() {
        assertEquals(2, ButtonPlaces.maxActionButtons(320, 640, 320))
        assertEquals(3, ButtonPlaces.maxActionButtons(411, 891, 411))
        assertEquals(4, ButtonPlaces.maxActionButtons(500, 400, 400))
        assertEquals(5, ButtonPlaces.maxActionButtons(891, 411, 411))
    }

    @Test
    fun buttonsThatFitStayInTheBarWithoutOverflow() {
        val plan = ButtonPlaces.plan(buttons(1, 1, 1), maxPlaces = 3, taken = 0, overflowItems = 0)
        assertEquals(listOf(0, 1, 2), plan.inBar)
        assertTrue(plan.inOverflow.isEmpty())
        assertFalse(plan.showsOverflow)
    }

    @Test
    fun moreButtonsThanPlacesKeepTheFirstAndLeaveOnePlaceForTheOverflow() {
        val plan = ButtonPlaces.plan(buttons(1, 1, 1, 1), maxPlaces = 3, taken = 0, overflowItems = 0)
        assertEquals(listOf(0, 1), plan.inBar)
        assertEquals(listOf(2, 3), plan.inOverflow)
        assertTrue(plan.showsOverflow)
    }

    @Test
    fun overflowItemsTakeAPlaceForTheOverflow() {
        val plan = ButtonPlaces.plan(buttons(1, 1, 1), maxPlaces = 3, taken = 0, overflowItems = 2)
        assertEquals(listOf(0, 1), plan.inBar)
        assertEquals(listOf(2), plan.inOverflow)
        assertTrue(plan.showsOverflow)
    }

    @Test
    fun theHighestPriorityStaysTiesByTheListsOrder() {
        val plan = ButtonPlaces.plan(
            buttons(ButtonPlaces.LOW, ButtonPlaces.STANDARD, ButtonPlaces.HIGH, ButtonPlaces.STANDARD),
            maxPlaces = 3,
            taken = 0,
            overflowItems = 0,
        )
        assertEquals(listOf(1, 2), plan.inBar)
        assertEquals(listOf(0, 3), plan.inOverflow)
    }

    @Test
    fun pinnedButtonsAlwaysStayAndTakeTheirPlaces() {
        val plan = ButtonPlaces.plan(buttons(1, 1, 1, 1, pinned = setOf(3)), maxPlaces = 3, taken = 0, overflowItems = 0)
        assertEquals(listOf(0, 3), plan.inBar)
        assertEquals(listOf(1, 2), plan.inOverflow)
    }

    @Test
    fun theMagnifierTakesAPlace() {
        val plan = ButtonPlaces.plan(buttons(1, 1, 1), maxPlaces = 3, taken = 1, overflowItems = 0)
        assertEquals(listOf(0), plan.inBar)
        assertEquals(listOf(1, 2), plan.inOverflow)
    }

    @Test
    fun aWiderWindowCountsAgain() {
        val list = buttons(1, 1, 1, 1)
        val narrow = ButtonPlaces.plan(list, ButtonPlaces.maxActionButtons(411, 891, 411), 0, 0)
        val wide = ButtonPlaces.plan(list, ButtonPlaces.maxActionButtons(891, 411, 411), 0, 0)
        assertEquals(2, narrow.inBar.size)
        assertEquals(4, wide.inBar.size)
    }

    @Test
    fun priorityByItsValue() {
        assertEquals(ButtonPlaces.HIGH, ButtonPlaces.priorityOf("high"))
        assertEquals(ButtonPlaces.LOW, ButtonPlaces.priorityOf("low"))
        assertEquals(ButtonPlaces.STANDARD, ButtonPlaces.priorityOf(null))
    }

    // Room.

    private val insets = TopBarRoom.Insets(statusBar = 72, cutout = 90)

    @Test
    fun underTheBarForEachCause() {
        assertFalse(TopBarRoom.isUnderBar(Options(), false))
        assertTrue(TopBarRoom.isUnderBar(Options().apply { topBar.drawBehind = Bool(true) }, false))
        assertTrue(TopBarRoom.isUnderBar(Options().apply { topBar.visible = Bool(false) }, false))
        assertTrue(TopBarRoom.isUnderBar(Options().apply { topBar.hideOnScroll = Bool(true) }, false))
        assertTrue(TopBarRoom.isUnderBar(Options().apply { topBar.searchBar.hideOnScroll = Bool(true) }, false))
        assertTrue(TopBarRoom.isUnderBar(Options(), largeTitleShown = true))
    }

    @Test
    fun theStripIsTheStatusBarAtLeastTheCutout() {
        assertEquals(90, TopBarRoom.strip(Options(), insets, inSheet = false))
        assertEquals(100, TopBarRoom.strip(Options(), TopBarRoom.Insets(100, 0), inSheet = false))
    }

    @Test
    fun aHiddenStatusBarLeavesTheCutoutOnly() {
        val hidden = Options().apply { statusBar.visible = Bool(false) }
        assertEquals(90, TopBarRoom.strip(hidden, insets, inSheet = false))
        // A merge of drawBehind with the status bar hidden adds no strip.
        hidden.statusBar.drawBehind = Bool(true)
        assertEquals(90, TopBarRoom.strip(hidden, TopBarRoom.Insets(72, 90), inSheet = false))
        assertEquals(0, TopBarRoom.strip(hidden, TopBarRoom.Insets(72, 0), inSheet = false))
    }

    @Test
    fun drawBehindFalsePutsTheBarBelowTheStatusBar() {
        val below = Options().apply { statusBar.drawBehind = Bool(false) }
        assertEquals(0, TopBarRoom.strip(below, insets, inSheet = false))
        assertEquals(10 + 72, TopBarRoom.margin(below, insets, inSheet = false, topMargin = 10))
        assertEquals(10, TopBarRoom.margin(Options(), insets, inSheet = false, topMargin = 10))
    }

    @Test
    fun noStripInASheet() {
        assertEquals(0, TopBarRoom.strip(Options(), insets, inSheet = true))
    }

    @Test
    fun theTopInsetFromTheConfiguredHeight() {
        val bar = TopBarRoom.Bar(strip = 90, margin = 0, topMargin = 0, height = 168)
        assertEquals(90 + 168, TopBarRoom.topInset(false, bar, searchHeight = 0))
        assertEquals(0, TopBarRoom.topInset(true, bar, searchHeight = 0))
        // Below the status bar the screen keeps the status bar's room itself.
        val below = TopBarRoom.Bar(strip = 0, margin = 72 + 10, topMargin = 10, height = 168)
        assertEquals(10 + 168, TopBarRoom.topInset(false, below, searchHeight = 0))
        assertEquals(90 + 168 + 189, TopBarRoom.topInset(false, bar, searchHeight = 189))
    }

    @Test
    fun theBarKeepsTheStatusBarRoomOnlyWithAStripWhileItShows() {
        val strip = TopBarRoom.Bar(strip = 90, margin = 0, topMargin = 0, height = 168)
        val none = TopBarRoom.Bar(strip = 0, margin = 72, topMargin = 0, height = 168)
        assertTrue(TopBarRoom.keepsStatusBarRoom(Options(), strip))
        assertFalse(TopBarRoom.keepsStatusBarRoom(Options(), none))
        assertFalse(TopBarRoom.keepsStatusBarRoom(Options().apply { topBar.visible = Bool(false) }, strip))
    }

    @Test
    fun theTopEdgeIsFreeWithTheHideOnScrollOrALargeTitle() {
        assertFalse(TopBarRoom.leavesTopEdgeFree(Options(), false))
        assertTrue(TopBarRoom.leavesTopEdgeFree(Options().apply { topBar.hideOnScroll = Bool(true) }, false))
        assertTrue(TopBarRoom.leavesTopEdgeFree(Options().apply { topBar.searchBar.hideOnScroll = Bool(true) }, false))
        assertTrue(TopBarRoom.leavesTopEdgeFree(Options(), true))
        assertFalse(TopBarRoom.leavesTopEdgeFree(Options().apply { topBar.drawBehind = Bool(true) }, false))
    }

    // Large title.

    @Test
    fun theLargeTitleModeGoesDownTheStackForAutomatic() {
        val always = LargeTitleOptions.ALWAYS
        val never = LargeTitleOptions.NEVER
        val automatic = LargeTitleOptions.AUTOMATIC
        assertTrue(LargeTitleCollapse.isShown(listOf(automatic), 0, true))
        assertTrue(LargeTitleCollapse.isShown(listOf(always, automatic, automatic), 2, true))
        assertFalse(LargeTitleCollapse.isShown(listOf(always, never, automatic), 2, true))
        assertFalse(LargeTitleCollapse.isShown(listOf(never), 0, true))
        assertFalse(LargeTitleCollapse.isShown(listOf(always), 0, barVisible = false))
    }

    @Test
    fun inlineIsNever() {
        val options = LargeTitleOptions().apply { displayMode = dev.apexnative.navigation.options.params.Text("inline") }
        assertEquals(LargeTitleOptions.NEVER, options.mode())
        assertEquals(LargeTitleOptions.ALWAYS, LargeTitleOptions().apply { visible = Bool(true) }.mode())
    }

    @Test
    fun theCollapseMath() {
        assertEquals(0f, LargeTitleCollapse.fraction(-10f, 88f), 0f)
        assertEquals(0.5f, LargeTitleCollapse.fraction(44f, 88f), 0.001f)
        assertEquals(1f, LargeTitleCollapse.fraction(500f, 88f), 0f)
        assertEquals(88f, LargeTitleCollapse.shift(500f, 88f), 0f)
        assertEquals(0.25f, LargeTitleCollapse.largeTitleAlpha(0.75f), 0.001f)
        assertEquals(0f, LargeTitleCollapse.rowTitleAlpha(0f), 0f)
        assertEquals(1f, LargeTitleCollapse.rowTitleAlpha(1f), 0f)
        // The curve (0.8, 0, 0.8, 0.15) stays low most of the way.
        assertTrue(LargeTitleCollapse.rowTitleAlpha(0.5f) < 0.2f)
        assertTrue(LargeTitleCollapse.largeTitleHasAccessibility(0.4f))
        assertFalse(LargeTitleCollapse.largeTitleHasAccessibility(0.6f))
    }

    // The row's timeline.

    private val linear: (Float) -> Float = { it }

    @Test
    fun thePushTimeline() {
        val start = RowTimeline.frame(BarMotion.Push, 0, linear)
        assertEquals(1f, start.snapshotAlpha, 0f)
        assertEquals(0f, start.rowAlpha, 0f)
        assertEquals(96f, start.rowShift, 0.001f)
        assertEquals(0f, RowTimeline.frame(BarMotion.Push, 50, linear).rowAlpha, 0f)
        assertEquals(0.5f, RowTimeline.frame(BarMotion.Push, 50, linear).snapshotAlpha, 0.001f)
        assertEquals(0f, RowTimeline.frame(BarMotion.Push, 100, linear).snapshotAlpha, 0f)
        assertEquals(0.5f, RowTimeline.frame(BarMotion.Push, 125, linear).rowAlpha, 0.001f)
        assertEquals(1f, RowTimeline.frame(BarMotion.Push, 200, linear).rowAlpha, 0f)
        val end = RowTimeline.frame(BarMotion.Push, 450, linear)
        assertEquals(-96f, end.snapshotShift, 0.001f)
        assertEquals(0f, end.rowShift, 0.001f)
        assertEquals(450L, RowTimeline.duration(BarMotion.Push))
    }

    @Test
    fun thePopTimelineIsMirrored() {
        assertEquals(-96f, RowTimeline.frame(BarMotion.Pop, 0, linear).rowShift, 0.001f)
        assertEquals(0f, RowTimeline.frame(BarMotion.Pop, 35, linear).rowAlpha, 0f)
        assertEquals(1f, RowTimeline.frame(BarMotion.Pop, 185, linear).rowAlpha, 0f)
        assertEquals(96f, RowTimeline.frame(BarMotion.Pop, 450, linear).snapshotShift, 0.001f)
    }

    @Test
    fun theFadeThroughTimeline() {
        assertEquals(300L, RowTimeline.duration(BarMotion.FadeThrough))
        val middle = RowTimeline.frame(BarMotion.FadeThrough, 105, linear)
        assertEquals(0f, middle.snapshotAlpha, 0f)
        assertEquals(0f, middle.rowAlpha, 0f)
        assertEquals(0f, middle.rowShift, 0f)
        assertEquals(1f, RowTimeline.frame(BarMotion.FadeThrough, 300, linear).rowAlpha, 0f)
    }
}

package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.options.BottomTabsOptions
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions of the tabs without views: what a press does, which tab a one-shot choice names, the history of back,
 * the attach modes.
 */
class TabRulesTest {
    // A press.

    @Test
    fun aPressOfAnotherTabSelectsIt() {
        assertEquals(TabSelection.Action.Select, TabSelection.press(false, true, true))
        // `popToRootOnPress` is only the repeat's.
        assertEquals(TabSelection.Action.Select, TabSelection.press(false, true, false))
    }

    @Test
    fun selectTabOnPressFalseKeepsTheSelection() {
        assertEquals(TabSelection.Action.Stay, TabSelection.press(false, false, true))
    }

    @Test
    fun aRepeatPopsOrScrollsUnlessAnOptionSaysNo() {
        assertEquals(TabSelection.Action.Repeat, TabSelection.press(true, true, true))
        assertEquals(TabSelection.Action.Nothing, TabSelection.press(true, true, false))
        assertEquals(TabSelection.Action.Nothing, TabSelection.press(true, false, true))
    }

    // A one-shot choice.

    private fun options(index: Double? = null, id: String? = null) = BottomTabsOptions().apply {
        index?.let { currentTabIndex = Fraction(it) }
        id?.let { currentTabId = Text(it) }
    }

    private val ids = mapOf("Stack1" to 0, "Details7" to 1, "Stack3" to 2)

    private fun choice(options: BottomTabsOptions) = TabSelection.choice(options, 3) { ids[it] }

    @Test
    fun anIndexInRangeIsTruncated() {
        assertEquals(TabSelection.Choice.Tab(2), choice(options(index = 2.7)))
        assertEquals(TabSelection.Choice.Tab(0), choice(options(index = 0.0)))
    }

    @Test
    fun anIndexOutOfRangeOrNegativeIsOneWarningAndNoChange() {
        assertEquals(TabSelection.Choice.OutOfRange(3), choice(options(index = 3.0)))
        assertEquals(TabSelection.Choice.OutOfRange(-1), choice(options(index = -1.0)))
        assertEquals(
            "[ApexNavigation] bottomTabs.currentTabIndex 3 is out of range: the layout has 3 tabs",
            TabSelection.outOfRangeText(3, 3),
        )
    }

    @Test
    fun anIdWinsOverTheIndexAndFindsALayoutInsideATab() {
        assertEquals(TabSelection.Choice.Tab(1), choice(options(index = 2.0, id = "Details7")))
        assertEquals(TabSelection.Choice.Tab(2), choice(options(id = "Stack3")))
    }

    @Test
    fun anUnknownIdAloneChoosesNothing() {
        assertEquals(TabSelection.Choice.None, choice(options(id = "Unknown")))
        assertEquals(TabSelection.Choice.None, choice(options()))
    }

    // The history of back.

    @Test
    fun theHistoryKeepsNoIndexTwiceInARowAndBackTakesTheEntry() {
        val history = TabHistory()
        history.left(0)
        history.left(0)
        history.left(2)
        assertEquals(listOf(0, 2), history.all)
        assertTrue(history.hasOtherThan(1))
        assertEquals(2, history.takeFor(1))
        assertEquals(0, history.takeFor(2))
        assertNull(history.takeFor(0))
        assertTrue(history.isEmpty)
    }

    @Test
    fun backSkipsAnEntryOfTheCurrentTab() {
        val history = TabHistory()
        history.left(1)
        history.left(0)
        assertTrue(history.hasOtherThan(0))
        assertEquals(1, history.takeFor(0))
        assertFalse(history.hasOtherThan(1))
    }

    // The attach modes.

    @Test
    fun theAttachModesSayWhichTabsJoinAndWhichAWaitCounts() {
        assertEquals(TabsAttach.Mode.Together, TabsAttach.modeOf(Text("together")))
        assertEquals(TabsAttach.Mode.Together, TabsAttach.modeOf(Text("other")))
        assertEquals(TabsAttach.Mode.AfterInitialTab, TabsAttach.modeOf(Text("afterInitialTab")))
        assertEquals(TabsAttach.Mode.OnSwitchToTab, TabsAttach.modeOf(Text("onSwitchToTab")))
        assertEquals(listOf(0, 1, 2), TabsAttach.atCreation(TabsAttach.Mode.Together, 1, 3))
        assertEquals(listOf(1), TabsAttach.atCreation(TabsAttach.Mode.AfterInitialTab, 1, 3))
        assertEquals(listOf(1), TabsAttach.atCreation(TabsAttach.Mode.OnSwitchToTab, 1, 3))
        assertEquals(listOf(0, 1, 2), TabsAttach.afterFirstAppearance(TabsAttach.Mode.AfterInitialTab, 3))
        assertTrue(TabsAttach.afterFirstAppearance(TabsAttach.Mode.OnSwitchToTab, 3).isEmpty())
        assertEquals(listOf(0, 2), TabsAttach.unseenWaited(TabsAttach.Mode.Together, 1, 3))
        assertTrue(TabsAttach.unseenWaited(TabsAttach.Mode.AfterInitialTab, 1, 3).isEmpty())
    }
}

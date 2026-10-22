package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.options.BottomTabOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.NoneText
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.views.bottomtabs.BottomTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the items of the bar get from the options (`bottomTab`): every field at creation, at a merge only the fields it
 * names with their resolved values, the badge and the dot held.
 */
class TabItemsTest {
    private val items = TabItems { colour -> colour.get(true, null) }

    private fun colour(argb: Int) = ThemeColour(argb, argb)

    private fun tab(change: BottomTabOptions.() -> Unit) = BottomTabOptions().apply(change)

    @Test
    fun anItemGetsEveryFieldAtCreation() {
        val calls = items.create(0, tab {
            text = Text("Home")
            icon = Text("home.png")
            iconColor = colour(1)
            textColor = colour(2)
            fontSize = Fraction(12.0)
            selectedFontSize = Fraction(14.0)
            iconWidth = Fraction(24.5)
            testID = Text("home.tab")
            disableIconTint = Bool(true)
            badge = Text("3")
            badgeColor = colour(5)
        })
        assertTrue(TabItems.Call.Title(0, "Home") in calls)
        assertTrue(TabItems.Call.Icon(0, "home.png") in calls)
        // The selected icon's color is the icon's when it has none (`bottomTab.selectedIconColor`).
        assertTrue(TabItems.Call.Colors(0, 1, 1, null, 2) in calls)
        assertTrue(TabItems.Call.FontSizes(0, 14f, 12f) in calls)
        assertTrue(TabItems.Call.IconSize(0, 24.5, null) in calls)
        assertTrue(TabItems.Call.TestId(0, "home.tab") in calls)
        assertTrue(TabItems.Call.TintDisabled(0, true, false) in calls)
        assertTrue(TabItems.Call.Badge(0, "3", 5, false, null) in calls)
    }

    @Test
    fun aMergeChangesOnlyTheFieldsItNames() {
        items.create(0, tab { text = Text("Home") })
        val merge = tab { fontSize = Fraction(15.0) }
        val resolved = tab {
            text = Text("Home")
            fontSize = Fraction(15.0)
            selectedFontSize = Fraction(16.0)
        }
        assertEquals(listOf(TabItems.Call.FontSizes(0, 16f, 15f)), items.merge(0, merge, resolved))
        val selectedSize = tab { selectedFontSize = Fraction(16.0) }
        assertEquals(listOf(TabItems.Call.FontSizes(0, 16f, 15f)), items.merge(0, selectedSize, resolved))
    }

    @Test
    fun aBadgeColorAloneSetsTheBadgeItShowsAgain() {
        items.create(1, tab { badge = Text("7") })
        val calls = items.merge(1, tab { badgeColor = colour(9) }, tab { badgeColor = colour(9) })
        assertEquals(listOf(TabItems.Call.Badge(1, "7", 9, false, null)), calls)
    }

    @Test
    fun anEmptyOrNullBadgeTakesItAwayAndTheDotComesBack() {
        items.create(0, tab {
            badge = Text("1")
            dotIndicator.visible = Bool(true)
            dotIndicator.color = colour(4)
        })
        val cleared = tab { badge = NoneText() }
        assertEquals(listOf(TabItems.Call.Badge(0, "", null, true, 4)), items.merge(0, cleared, cleared))
        val empty = tab { badge = Text("") }
        assertEquals(listOf(TabItems.Call.Badge(0, "", null, true, 4)), items.merge(0, empty, empty))
    }

    @Test
    fun theDotMergesByFieldAndOnlyVisibleTurnsItOn() {
        items.create(0, tab { dotIndicator.color = colour(4) })
        assertEquals("", items.badgeOf(0))
        val merge = tab { dotIndicator.visible = Bool(true) }
        val resolved = tab {
            dotIndicator.color = colour(4)
            dotIndicator.visible = Bool(true)
        }
        assertEquals(listOf(TabItems.Call.Badge(0, "", null, true, 4)), items.merge(0, merge, resolved))
    }

    @Test
    fun theBadgeHoldsThroughAMergeOfAnotherFieldAndTheRepaint() {
        // A badge merged into a lower screen: the tab's resolved options don't have it, the item keeps it.
        items.create(0, tab { text = Text("Home") })
        items.merge(0, tab { badge = Text("2") }, tab { badge = Text("2") })
        val calls = items.merge(0, tab { text = Text("Start") }, tab { text = Text("Start") })
        assertEquals(listOf(TabItems.Call.Title(0, "Start")), calls)
        val repaint = items.repaint(0, tab { badgeColor = colour(3) })
        assertTrue(TabItems.Call.Badge(0, "2", 3, false, null) in repaint)
    }

    @Test
    fun theTitlesModeFollowsTitleDisplayMode() {
        assertEquals(BottomTabs.TitleState.ALWAYS_SHOW, TabItems.titleState(Text("alwaysShow"), true))
        assertEquals(BottomTabs.TitleState.SHOW_WHEN_ACTIVE, TabItems.titleState(Text("showWhenActive"), true))
        assertEquals(
            BottomTabs.TitleState.SHOW_WHEN_ACTIVE_FORCE,
            TabItems.titleState(Text("showWhenActiveForce"), true),
        )
        assertEquals(BottomTabs.TitleState.ALWAYS_HIDE, TabItems.titleState(Text("alwaysHide"), false))
        assertEquals(BottomTabs.TitleState.AUTO, TabItems.titleState(Text(null), true))
        assertEquals(BottomTabs.TitleState.ALWAYS_SHOW, TabItems.titleState(Text(null), false))
    }
}

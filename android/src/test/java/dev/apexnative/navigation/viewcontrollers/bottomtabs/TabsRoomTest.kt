package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.customrow.BottomTabsCustomRowOptions
import dev.apexnative.navigation.viewcontrollers.stack.fab.FabPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic of the tab bar without views: the room of the screens, the top of the bar, the bottom edge, the FAB
 * at rest, the motion of the bar with a stack's transition, the custom row's place, overshoot and background.
 */
class TabsRoomTest {
    private val shown = TabsRoom.Screen(hidden = false, drawBehind = false, hideOnScroll = false)
    private val stretch = TabsRoom.Bar(height = 210, navigationBar = 63, compact = false, bottomMargin = 0)

    @Test
    fun aStretchedBarKeepsItsHeightAndTheNavigationBar() {
        assertEquals(273, TabsRoom.bottomInset(stretch, shown, around = 0))
        assertEquals(0, stretch.offset)
        // The layouts around add theirs.
        assertEquals(283, TabsRoom.bottomInset(stretch, shown, around = 10))
    }

    @Test
    fun aCompactBarFloatsOverTheNavigationBarAndItsMargin() {
        val compact = TabsRoom.Bar(height = 210, navigationBar = 63, compact = true, bottomMargin = 40)
        assertEquals(210, compact.containerHeight)
        assertEquals(103, compact.offset)
        assertEquals(313, TabsRoom.bottomInset(compact, shown, around = 0))
    }

    @Test
    fun theBottomMarginOfAStretchedBarAddsOnce() {
        val bar = stretch.copy(bottomMargin = 40)
        assertEquals(313, TabsRoom.bottomInset(bar, shown, around = 0))
    }

    @Test
    fun aScreenUnderTheBarKeepsNoRoomForIt() {
        assertEquals(5, TabsRoom.bottomInset(stretch, shown.copy(hidden = true), around = 5))
        assertEquals(0, TabsRoom.bottomInset(stretch, shown.copy(drawBehind = true), around = 0))
        assertEquals(0, TabsRoom.bottomInset(stretch, shown.copy(hideOnScroll = true), around = 0))
    }

    @Test
    fun aRowHigherThanTheBarAddsWhatItCovers() {
        val bar = stretch.copy(rowOvershoot = 30)
        assertEquals(303, TabsRoom.bottomInset(bar, shown, around = 0))
        assertEquals(2000 - 303, TabsRoom.tabsTop(bar, shown, tabsBottom = 2000))
    }

    @Test
    fun theTopOfTheBarIsWhereItRestsUnlessTheScreenHidesIt() {
        assertEquals(2000 - 273, TabsRoom.tabsTop(stretch, shown, tabsBottom = 2000))
        // Drawn behind or hiding on scroll, the safe area still counts the bar.
        assertEquals(2000 - 273, TabsRoom.tabsTop(stretch, shown.copy(drawBehind = true), tabsBottom = 2000))
        assertNull(TabsRoom.tabsTop(stretch, shown.copy(hidden = true), tabsBottom = 2000))
    }

    @Test
    fun theBottomEdgeIsFreeOnlyWhileTheBarHidesOnScrollAndShows() {
        assertTrue(TabsRoom.leavesBottomEdgeFree(shown.copy(hideOnScroll = true)))
        assertFalse(TabsRoom.leavesBottomEdgeFree(shown))
        assertFalse(TabsRoom.leavesBottomEdgeFree(shown.copy(hideOnScroll = true, hidden = true)))
    }

    @Test
    fun theFabKeepsClearOfTheBarAtRestUnlessTheScreenHidesIt() {
        assertEquals(FabPlacement.Tabs(273, false), TabsRoom.fabTabs(stretch, shown))
        assertEquals(FabPlacement.Tabs(273, false), TabsRoom.fabTabs(stretch, shown.copy(drawBehind = true)))
        assertEquals(FabPlacement.Tabs(273, false), TabsRoom.fabTabs(stretch, shown.copy(hideOnScroll = true)))
        assertNull(TabsRoom.fabTabs(stretch, shown.copy(hidden = true)))
        val compact = TabsRoom.Bar(height = 210, navigationBar = 63, compact = true, bottomMargin = 40)
        assertEquals(FabPlacement.Tabs(250, true), TabsRoom.fabTabs(compact, shown))
    }

    // The motion.

    @Test
    fun aPushToAScreenWithoutTheBarTakesItAwayWithTheCoveredScreen() {
        val ride = TabBarMotion.ride(TabBarMotion.Move.WithPush, showing = false, rtl = false)
        assertEquals(TabBarMotion.Ride(0f, -96f, 1f, 0f, 50, 83, 450), ride)
    }

    @Test
    fun aPopBackToAScreenWithTheBarBringsItWithTheRevealedScreen() {
        val ride = TabBarMotion.ride(TabBarMotion.Move.WithPop, showing = true, rtl = false)
        assertEquals(TabBarMotion.Ride(-96f, 0f, 0f, 1f, 35, 83, 450), ride)
    }

    @Test
    fun theOppositeCasesAndRightToLeftAreMirrored() {
        assertEquals(
            TabBarMotion.Ride(96f, 0f, 0f, 1f, 50, 83, 450),
            TabBarMotion.ride(TabBarMotion.Move.WithPush, showing = true, rtl = false),
        )
        assertEquals(
            TabBarMotion.Ride(0f, 96f, 1f, 0f, 35, 83, 450),
            TabBarMotion.ride(TabBarMotion.Move.WithPop, showing = false, rtl = false),
        )
        assertEquals(
            TabBarMotion.Ride(0f, 96f, 1f, 0f, 50, 83, 450),
            TabBarMotion.ride(TabBarMotion.Move.WithPush, showing = false, rtl = true),
        )
    }

    @Test
    fun anOwnAnimationSlidesAndNoAnimationIsAtOnce() {
        assertNull(TabBarMotion.ride(TabBarMotion.Move.Slide, showing = true, rtl = false))
        assertNull(TabBarMotion.ride(TabBarMotion.Move.AtOnce, showing = true, rtl = false))
        assertEquals(TabBarMotion.Slide(0f, 1f, 300), TabBarMotion.slide(showing = false))
        assertEquals(TabBarMotion.Slide(1f, 0f, 300), TabBarMotion.slide(showing = true))
    }

    // The custom row.

    @Test
    fun theRowLiesOverTheBarLessItsMarginsWithItsContentAboveItsBottomMargin() {
        val content = CustomTabRowGeometry.content(barHeight = 210, height = null, extra = 47)
        assertEquals(257, content)
        val place = CustomTabRowGeometry.place(0, 1080, 2200, margin = 42, content = content, bottomMargin = 20)
        assertEquals(CustomTabRowGeometry.Place(42, 2200 - 257 - 20, 1038, 2200, 257), place)
        // The visible row ends the bottom margin above the bar's bottom, not at it.
        assertEquals(20, CustomTabRowGeometry.gap(place))
        val flush = CustomTabRowGeometry.place(0, 1080, 2200, margin = 42, content = content, bottomMargin = 0)
        assertEquals(0, CustomTabRowGeometry.gap(flush))
    }

    @Test
    fun aRowHeightIsExactAndItsOvershootComesFromTheOptions() {
        assertEquals(400, CustomTabRowGeometry.content(barHeight = 210, height = 400, extra = 47))
        assertEquals(190, CustomTabRowGeometry.overshoot(barHeight = 210, content = 400, bottomMargin = 0))
        assertEquals(210, CustomTabRowGeometry.overshoot(barHeight = 210, content = 400, bottomMargin = 20))
        assertEquals(0, CustomTabRowGeometry.overshoot(barHeight = 210, content = 100, bottomMargin = 0))
    }

    @Test
    fun theRowsBackgroundIsItsColorElseClearOrTheThemesBackground() {
        val theme = 0xFF123456.toInt()
        assertEquals(0xFF00FF00.toInt(), CustomTabRowGeometry.background(0xFF00FF00.toInt(), null, theme))
        assertEquals(0, CustomTabRowGeometry.background(null, BottomTabsCustomRowOptions.BackgroundEffect.NONE, theme))
        val faint = 0xF0123456.toInt()
        val glass = BottomTabsCustomRowOptions.BackgroundEffect.GLASS
        val blur = BottomTabsCustomRowOptions.BackgroundEffect.BLUR
        assertEquals(faint, CustomTabRowGeometry.background(null, glass, theme))
        assertEquals(faint, CustomTabRowGeometry.background(null, blur, theme))
        assertEquals(faint, CustomTabRowGeometry.background(null, null, theme))
    }

    @Test
    fun theRowIsPutAgainOnlyWhenItsPlaceChanged() {
        val place = CustomTabRowGeometry.Place(42, 1900, 1038, 2200, 257)
        assertTrue(CustomTabRowGeometry.needsPlacing(null, place))
        assertFalse(CustomTabRowGeometry.needsPlacing(place, place.copy()))
        assertTrue(CustomTabRowGeometry.needsPlacing(place, place.copy(top = 1890)))
    }

    @Test
    fun theCellsStateShowsTheDotOnlyWithoutABadge() {
        assertEquals(TabCellState(1, true, null, true, 7), TabCellState.of(1, true, "", true, 7))
        assertEquals(TabCellState(1, false, "3", false, 7), TabCellState.of(1, false, "3", true, 7))
        assertEquals(TabCellState(0, false, null, false, null), TabCellState.of(0, false, "", false, null))
    }
}

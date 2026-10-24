package dev.apexnative.navigation.viewcontrollers.component

import dev.apexnative.navigation.react.ScreenEnvironment
import dev.apexnative.navigation.viewcontrollers.component.ScreenSafeArea.Box
import dev.apexnative.navigation.viewcontrollers.component.ScreenSafeArea.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic of a screen on Android: its insets against the system, the header and the tabs, counted inside its
 * view's padding and without translations, in dp; when it measures; the edges its safe area keeps; its room and
 * keyboard; the band of its background.
 */
class ScreenSafeAreaTest {
    // A 1080 × 2400 window at density 2.5: status bar 100, navigation bar 50, no cutout.
    private val system = Box(0, 100, 0, 50)

    private fun insets(root: Box, headerBottom: Int? = null, tabsTop: Int? = null, sides: Box = system) =
        ScreenSafeArea.insets(1080, 2400, sides, root, headerBottom, tabsTop, 2.5f)

    @Test
    fun aFullWindowRootIsCoveredByTheSystemBars() {
        assertEquals(ScreenEnvironment.Insets(40.0, 0.0, 20.0, 0.0), insets(Box(0, 0, 1080, 2400)))
    }

    @Test
    fun theHeaderCoversTheTopWhenItReachesLowerThanTheSystem() {
        assertEquals(40.0, insets(Box(0, 0, 1080, 2400), headerBottom = 50).top, 0.0)
        assertEquals(100.0, insets(Box(0, 0, 1080, 2400), headerBottom = 250).top, 0.0)
    }

    @Test
    fun theTabsCoverTheBottomWhenTheyStartHigherThanTheSystem() {
        assertEquals(20.0, insets(Box(0, 0, 1080, 2400), tabsTop = 2380).bottom, 0.0)
        assertEquals(80.0, insets(Box(0, 0, 1080, 2400), tabsTop = 2200).bottom, 0.0)
    }

    @Test
    fun theSidesAreTheSystemBarsAndTheCutout() {
        val landscape = Box(120, 60, 90, 0)
        assertEquals(
            ScreenEnvironment.Insets(24.0, 36.0, 0.0, 48.0),
            ScreenSafeArea.insets(2400, 1080, landscape, Box(0, 0, 2400, 1080), null, null, 2.5f),
        )
    }

    @Test
    fun aRootInsideTheViewsPaddingIsNotCoveredTwice() {
        // The screen's view keeps the navigation bar (50) and the side bar (120) as its padding: the root ends above.
        val root = Box(120, 0, 1080, 2350)
        assertEquals(ScreenEnvironment.Insets(40.0, 0.0, 0.0, 0.0), insets(root, sides = Box(120, 100, 0, 50)))
    }

    @Test
    fun theRootIsWhereItRestsWithoutTranslationsAndLessTheScrolls() {
        val steps = listOf(Step(left = 30, top = 200, parentScrollX = 0, parentScrollY = 40), Step(10, 0, 0, 0))
        assertEquals(Box(40, 160, 1040, 2160), ScreenSafeArea.placeInWindow(steps, 1000, 2000))
        assertEquals(Box(0, 0, 1080, 2400), ScreenSafeArea.placeInWindow(listOf(Step(0, 0, 0, 0)), 1080, 2400))
    }

    @Test
    fun aScreenMeasuresOnlyShownInItsPlaceAndOnTopOfItsStack() {
        fun measures(shown: Boolean, pending: Boolean, top: Boolean, has: Boolean) =
            ScreenSafeArea.measuresNow(shown, pending, top, has)
        assertTrue(measures(shown = true, pending = false, top = true, has = true))
        assertFalse(measures(shown = false, pending = false, top = true, has = true))
        assertFalse(measures(shown = true, pending = true, top = true, has = false))
        assertFalse(measures(shown = true, pending = false, top = false, has = true))
        assertTrue(measures(shown = true, pending = false, top = false, has = false))
    }

    @Test
    fun theSafeAreaKeepsEveryEdgeUnlessTheBarsHideOnScroll() {
        val all = setOf("top", "right", "bottom", "left")
        assertEquals(all, ScreenSafeArea.paddedEdges(null, topFree = false, bottomFree = false))
        assertEquals(all - "top", ScreenSafeArea.paddedEdges(null, topFree = true, bottomFree = false))
        assertEquals(all - "bottom", ScreenSafeArea.paddedEdges(null, topFree = false, bottomFree = true))
    }

    @Test
    fun explicitEdgesAreExactlyThose() {
        val explicit = ScreenSafeArea.paddedEdges(listOf("top", "middle"), topFree = true, bottomFree = true)
        assertEquals(setOf("top"), explicit)
        assertEquals(emptySet<String>(), ScreenSafeArea.paddedEdges(emptyList(), topFree = false, bottomFree = false))
    }

    @Test
    fun thePaddingIsTheInsetsOnTheKeptEdges() {
        val insets = ScreenEnvironment.Insets(40.0, 5.0, 20.0, 7.0)
        val padding = ScreenSafeArea.padding(insets, setOf("right", "bottom", "left"))
        assertEquals(ScreenEnvironment.Insets(0.0, 5.0, 20.0, 7.0), padding)
    }

    @Test
    fun javaScriptGetsWhatStillCoversTheContentNeverBelowZero() {
        val insets = ScreenEnvironment.Insets(40.0, 5.0, 20.0, 7.0)
        val padding = ScreenEnvironment.Insets(40.0, 0.0, 30.0, 7.0)
        assertEquals(ScreenEnvironment.Insets(0.0, 5.0, 0.0, 0.0), ScreenSafeArea.uncovered(insets, padding))
    }

    @Test
    fun theBottomRoomIsTheLargerOfTheNavigationBarAndTheKeyboardLessTheTabs() {
        val bars = Box(30, 100, 40, 50)
        assertEquals(Box(30, 0, 40, 50), ScreenSafeArea.roomPadding(bars, 0, false, true, 0))
        assertEquals(Box(30, 0, 40, 800), ScreenSafeArea.roomPadding(bars, 800, false, true, 0))
        assertEquals(Box(30, 0, 40, 600), ScreenSafeArea.roomPadding(bars, 800, false, true, 200))
        assertEquals(0, ScreenSafeArea.roomPadding(bars, 0, false, true, 200).bottom)
    }

    @Test
    fun withoutAdjustResizeTheKeyboardCoversTheScreen() {
        val bars = Box(0, 100, 0, 50)
        assertEquals(50, ScreenSafeArea.roomPadding(bars, 800, false, adjustsForKeyboard = false, 0).bottom)
        assertEquals(0, ScreenSafeArea.roomPadding(bars, 800, true, adjustsForKeyboard = false, 0).bottom)
    }

    @Test
    fun aScreenDrawsUnderTheNavigationBarUnlessItsColorIsOpaque() {
        assertTrue(ScreenSafeArea.drawsUnderNavigationBar(null, null))
        assertTrue(ScreenSafeArea.drawsUnderNavigationBar(null, 0x80FFFFFF.toInt()))
        assertFalse(ScreenSafeArea.drawsUnderNavigationBar(null, 0xFF000000.toInt()))
        assertTrue(ScreenSafeArea.drawsUnderNavigationBar(true, 0xFF000000.toInt()))
        assertFalse(ScreenSafeArea.drawsUnderNavigationBar(false, null))
    }

    @Test
    fun theStatusBarRoomGoesWhenHiddenDrawnUnderInASheetOrASideMenuOrKeptAround() {
        assertEquals(100, ScreenSafeArea.statusBarRoom(100, false, false, false, false))
        assertEquals(0, ScreenSafeArea.statusBarRoom(100, true, false, false, false))
        assertEquals(0, ScreenSafeArea.statusBarRoom(100, false, true, false, false))
        assertEquals(0, ScreenSafeArea.statusBarRoom(100, false, false, true, false))
        assertEquals(0, ScreenSafeArea.statusBarRoom(100, false, false, false, true))
    }

    @Test
    fun theBackgroundStaysOutOfTheStatusBarUnlessAnExceptionHolds() {
        assertEquals(100, ScreenSafeArea.backgroundBand(100, false, false, false, false, topRoom = 0))
        assertEquals(0, ScreenSafeArea.backgroundBand(100, hiddenOrDrawBehind = true, false, false, false, 0))
        assertEquals(0, ScreenSafeArea.backgroundBand(100, false, inSideMenu = true, false, false, 0))
        assertEquals(0, ScreenSafeArea.backgroundBand(100, false, false, inSheet = true, false, 0))
        assertEquals(0, ScreenSafeArea.backgroundBand(100, false, false, false, inPair = true, 0))
        assertEquals(0, ScreenSafeArea.backgroundBand(100, false, false, false, false, topRoom = 280))
    }
}

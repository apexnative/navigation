package dev.apexnative.navigation.viewcontrollers.sidemenu

import android.view.KeyEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SideMenuOptions
import dev.apexnative.navigation.options.SideMenuSideOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure rules of a side menu's options on Android: `visible` as a command, the sizes, the `testID`s, what a merge
 * names, the pass-on without `sideMenu`, when the menus join the drawer.
 */
class SideMenuRulesTest {
    private fun side(change: SideMenuSideOptions.() -> Unit) = SideMenuSideOptions().apply(change)

    private fun menu(change: SideMenuOptions.() -> Unit) = SideMenuOptions().apply(change)

    // Two pixels a dp.
    private val toPixels: (Double) -> Int = { (it * 2).toInt() }

    @Test
    fun visibleGivesCommandsLeftFirstWithTheirAnimate() {
        val merge = menu {
            right.visible = Bool(false)
            left.visible = Bool(true)
            left.animate = Bool(false)
        }
        assertEquals(
            listOf(
                SideMenuRules.Command(Side.Left, visible = true, animate = false),
                SideMenuRules.Command(Side.Right, visible = false, animate = true),
            ),
            SideMenuRules.commands(merge),
        )
        // `animate` alone is no command.
        assertTrue(SideMenuRules.commands(menu { left.animate = Bool(false) }).isEmpty())
    }

    @Test
    fun theLayoutOpensItsVisibleSidesAtTheFirstShow() {
        assertEquals(listOf(Side.Right), SideMenuRules.firstShow(menu { right.visible = Bool(true) }))
        assertTrue(SideMenuRules.firstShow(menu { left.visible = Bool(false) }).isEmpty())
        assertEquals(
            listOf(Side.Left, Side.Right),
            SideMenuRules.firstShow(menu {
                left.visible = Bool(true)
                right.visible = Bool(true)
            }),
        )
    }

    @Test
    fun ownOptionsNeverKeepVisible() {
        // What the side menu resolves from never has `visible`: resolved options make no command.
        val own = Options().apply { sideMenu.left.visible = Bool(true) }
        own.clearOneShotOptions()
        assertTrue(SideMenuRules.commands(own.sideMenu).isEmpty())
    }

    @Test
    fun enabledIsTrueUnlessFalse() {
        assertTrue(SideMenuRules.isEnabled(menu {}, Side.Left))
        assertFalse(SideMenuRules.isEnabled(menu { right.enabled = Bool(false) }, Side.Right))
        assertTrue(SideMenuRules.isEnabled(menu { right.enabled = Bool(false) }, Side.Left))
        assertTrue(SideMenuRules.touchesEnabled(menu { left.enabled = Bool(true) }))
        assertFalse(SideMenuRules.touchesEnabled(menu { left.width = Fraction(200.0) }))
    }

    @Test
    fun aWidthIsGivenOrTheDrawersOwnAndClampedToTheWidthLessTheGap() {
        assertEquals(400, SideMenuRules.wantedWidth(side { width = Fraction(200.0) }, toPixels))
        assertNull(SideMenuRules.wantedWidth(side {}, toPixels))
        assertEquals(0, SideMenuRules.wantedWidth(side { width = Fraction(-5.0) }, toPixels))
        // A side menu 1000 px wide with a gap of 128 px.
        assertEquals(400, SideMenuRules.drawerWidth(400, 1000, 128))
        assertEquals(872, SideMenuRules.drawerWidth(2000, 1000, 128))
        assertEquals(MATCH_PARENT, SideMenuRules.drawerWidth(null, 1000, 128))
        assertEquals(0, SideMenuRules.drawerWidth(400, 100, 128))
    }

    @Test
    fun aHeightIsGivenOrTheWholeHeight() {
        assertEquals(300, SideMenuRules.height(side { height = Fraction(150.0) }, toPixels))
        assertNull(SideMenuRules.height(side {}, toPixels))
    }

    @Test
    fun theOffsetFromRestOfEachDrawer() {
        // The left drawer, 300 wide, closed at -300 and on its way at -100; at rest at 0.
        assertEquals(300, SideMenuRules.offsetFromRest(Side.Left, 1000, -300, 0))
        assertEquals(100, SideMenuRules.offsetFromRest(Side.Left, 1000, -100, 200))
        assertEquals(0, SideMenuRules.offsetFromRest(Side.Left, 1000, 0, 300))
        // The right drawer, closed past the right edge; at rest its right edge on the side menu's.
        assertEquals(-300, SideMenuRules.offsetFromRest(Side.Right, 1000, 1000, 1300))
        assertEquals(-100, SideMenuRules.offsetFromRest(Side.Right, 1000, 800, 1100))
        assertEquals(0, SideMenuRules.offsetFromRest(Side.Right, 1000, 700, 1000))
    }

    @Test
    fun theTestIdsOfTheParts() {
        assertEquals(SideMenuRules.TestIdSet(null, null, null, null), SideMenuRules.testIds(menu {}))
        assertEquals(
            SideMenuRules.TestIdSet("Menu", "Menu.center", "Menu.left", "Menu.right"),
            SideMenuRules.testIds(menu { testID = Text("Menu") }),
        )
        // A side's own wins; without the side menu's, only the side has one.
        assertEquals(
            SideMenuRules.TestIdSet("Menu", "Menu.center", "Drawer", "Menu.right"),
            SideMenuRules.testIds(menu {
                testID = Text("Menu")
                left.testID = Text("Drawer")
            }),
        )
        assertEquals(
            SideMenuRules.TestIdSet(null, null, null, "Settings"),
            SideMenuRules.testIds(menu { right.testID = Text("Settings") }),
        )
    }

    @Test
    fun whatAMergeNames() {
        assertTrue(SideMenuRules.touchesTestIds(menu { right.testID = Text("Settings") }))
        assertFalse(SideMenuRules.touchesTestIds(menu { right.enabled = Bool(false) }))
        assertTrue(SideMenuRules.touchesBackground(Options().apply { layout.backgroundColor = ThemeColour(1, 2) }))
        assertFalse(SideMenuRules.touchesBackground(Options()))
        // A width or a height resizes the menus at the merge itself.
        assertTrue(SideMenuRules.touchesWidths(menu { left.width = Fraction(200.0) }))
        assertTrue(SideMenuRules.touchesWidths(menu { right.width = Fraction(320.5) }))
        assertFalse(SideMenuRules.touchesWidths(menu { left.height = Fraction(400.0) }))
        assertTrue(SideMenuRules.touchesHeights(menu { right.height = Fraction(400.0) }))
        assertFalse(SideMenuRules.touchesHeights(menu { right.width = Fraction(320.0) }))
    }

    @Test
    fun theLayoutsAroundGetEverythingButSideMenu() {
        val options = Options().apply {
            sideMenu.left.visible = Bool(true)
            sideMenu.left.enabled = Bool(false)
            bottomTabs.visible = Bool(false)
        }
        val passed = SideMenuRules.withoutSideMenu(options)
        assertFalse(passed.sideMenu.hasValue())
        assertTrue(passed.bottomTabs.visible.isFalse)
        assertTrue(options.sideMenu.left.enabled.isFalse)
    }

    @Test
    fun theDrawerLeavesBackToTheActivity() {
        assertTrue(SideMenuRules.leavesKeyToActivity(KeyEvent.KEYCODE_BACK))
        assertFalse(SideMenuRules.leavesKeyToActivity(KeyEvent.KEYCODE_ENTER))
    }

    // `menusAttachMode`.

    @Test
    fun theAttachModeIsTheOwnElseTheDefaultElseTogether() {
        val none = menu {}
        assertEquals(SideMenuRules.AttachMode.Together, SideMenuRules.attachModeOf(none, none))
        val onOpen = menu { menusAttachMode = Text("onOpen") }
        val afterCenter = menu { menusAttachMode = Text("afterCenter") }
        assertEquals(SideMenuRules.AttachMode.OnOpen, SideMenuRules.attachModeOf(onOpen, afterCenter))
        assertEquals(SideMenuRules.AttachMode.AfterCenter, SideMenuRules.attachModeOf(none, afterCenter))
        val together = menu { menusAttachMode = Text("together") }
        assertEquals(SideMenuRules.AttachMode.Together, SideMenuRules.attachModeOf(together, onOpen))
    }

    @Test
    fun theMenusJoinWithTheSideMenuOrAfterItAppearedByTheMode() {
        val both = listOf(Side.Left, Side.Right)
        assertEquals(both, SideMenuRules.attachedAtCreation(SideMenuRules.AttachMode.Together))
        assertTrue(SideMenuRules.attachedAtCreation(SideMenuRules.AttachMode.AfterCenter).isEmpty())
        assertTrue(SideMenuRules.attachedAtCreation(SideMenuRules.AttachMode.OnOpen).isEmpty())
        assertEquals(both, SideMenuRules.attachedAfterAppearance(SideMenuRules.AttachMode.AfterCenter))
        assertTrue(SideMenuRules.attachedAfterAppearance(SideMenuRules.AttachMode.Together).isEmpty())
        assertTrue(SideMenuRules.attachedAfterAppearance(SideMenuRules.AttachMode.OnOpen).isEmpty())
    }
}

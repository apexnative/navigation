package dev.apexnative.navigation.viewcontrollers.sidemenu

import android.app.Activity
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.options.BottomTabsEnterExitAnimationOptions
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.bottomtabs.TabBarMotion
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.navigator.LayoutFactory
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.Side
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The side menu node without views (a fake drawer, the side menu "shows" as the test says), with the real dispatcher
 * of the events on a fake clock: its children, its current child and what shows and waits with it, `isInSideMenu`, the
 * shown options with a menu open, `visible` from a merge and from the layout, the locks of the appearance row, the
 * pass-on to the layouts around, back, the factory.
 */
class SideMenuControllerTest {
    private val journal = mutableListOf<String>()
    private val fixture = DispatcherFixture(EventJournal(journal))
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val calls = mutableListOf<String>()
    private val drawer = FakeDrawer(calls)
    private var onScreen = true

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        DefaultOptions.set(Options())
    }

    private fun screen(id: String, options: Options = Options()) = ReportingMenuScreen(activity, registry, id, options)

    private fun options(change: Options.() -> Unit) = Options().apply(change)

    private fun menu(
        center: ViewController<*> = screen("Center"),
        left: ViewController<*>? = screen("Left"),
        right: ViewController<*>? = screen("Right"),
        options: Options = Options(),
    ) = SideMenuController(activity, registry, MENU, SideMenuPresenter(activity), options, center, left, right).also {
        it.drawer = drawer
        drawer.motion = it.motion
        it.shows = { onScreen }
    }

    // A side menu that appeared, the journals cleared.
    private fun shown(menu: SideMenuController): SideMenuController {
        menu.onViewWillAppear()
        menu.onViewDidAppear()
        journal.clear()
        calls.clear()
        return menu
    }

    private fun events(): List<String> = journal.toList().also { journal.clear() }

    private fun commands(): List<String> = calls.toList().also { calls.clear() }

    private fun visible(side: Side, value: Boolean, animate: Boolean? = null) = options {
        val options = if (side == Side.Left) sideMenu.left else sideMenu.right
        options.visible = Bool(value)
        animate?.let { options.animate = Bool(it) }
    }

    // The node.

    @Test
    fun theCurrentChildIsTheOpenMenuElseTheCenter() {
        val center = screen("Center")
        val left = screen("Left")
        val menu = shown(menu(center, left))
        assertEquals(listOf(center, left, menu.childControllers.last()), menu.childControllers.toList())
        assertSame(center, menu.currentChild)
        assertEquals(listOf(center), menu.visibleChildren())
        assertEquals(listOf(center), menu.waitedChildren())
        menu.mergeOptions(visible(Side.Left, true, animate = false))
        assertSame(left, menu.currentChild)
        assertEquals(listOf(center, left), menu.visibleChildren())
        assertEquals(listOf(center), menu.waitedChildren())
        assertSame(menu, registry.find(MENU))
    }

    @Test
    fun theMenusAreInTheSideMenuTheCenterIsNot() {
        val stack = TestLayout(activity, registry, "LeftStack")
        val inStack = TestScreen(activity, registry, "Settings")
        stack.add(inStack)
        val center = screen("Center")
        val menu = menu(center, stack)
        assertTrue(stack.isInSideMenu)
        assertTrue(inStack.isInSideMenu)
        assertTrue(menu.childControllers.last().isInSideMenu)
        assertFalse(center.isInSideMenu)
        assertTrue(menu.measuresThoughNotShown(stack))
        assertFalse(menu.measuresThoughNotShown(center))
        // Without a view nothing moves.
        assertEquals(0, menu.childOffsetFromRest(stack))
    }

    @Test
    fun anOpenMenuShowsWithTheCentersSideMenuOverItsOwn() {
        val center = screen("Center", options {
            sideMenu.left.enabled = Bool(false)
            sideMenu.left.width = Fraction(220.0)
        })
        val left = screen("Left", options {
            sideMenu.left.width = Fraction(300.0)
            sideMenu.right.width = Fraction(250.0)
            bottomTabs.visible = Bool(false)
        })
        val menu = shown(menu(center, left))
        assertEquals(220.0, menu.resolveShownOptions(DefaultOptions.current).sideMenu.left.width.get(null))
        assertFalse(menu.resolveShownOptions(DefaultOptions.current).bottomTabs.visible.hasValue())
        menu.mergeOptions(visible(Side.Left, true, animate = false))
        val shown = menu.resolveShownOptions(DefaultOptions.current)
        assertTrue(shown.sideMenu.left.enabled.isFalse)
        assertEquals(220.0, shown.sideMenu.left.width.get(null))
        assertEquals(250.0, shown.sideMenu.right.width.get(null))
        assertTrue(shown.bottomTabs.visible.isFalse)
        // The menu's own shown options are not changed.
        assertEquals(300.0, left.resolveShownOptions(DefaultOptions.current).sideMenu.left.width.get(null))
    }

    // `visible`.

    @Test
    fun aVisibleMergedIntoAClosedMenusScreenOpensIt() {
        val left = screen("Left")
        val menu = shown(menu(left = left))
        left.mergeOptions(visible(Side.Left, true))
        // Unlocked already by the appearance (`enabled` unset).
        assertEquals(listOf("close:right:true", "open:left:true"), commands())
        drawer.frame(Side.Left, 0.5f)
        drawer.settle(Side.Left)
        assertEquals(listOf("will:Left", "didAppear:Left", "stateNeeded", "stateNeeded"), events())
        assertSame(left, menu.currentChild)
        // The screen keeps no `visible` of its own.
        assertFalse(left.options.sideMenu.left.visible.hasValue())
    }

    @Test
    fun aVisibleMergedIntoTheSideMenuWhileItDoesntShowOpensAtOnce() {
        val menu = menu()
        onScreen = false
        menu.mergeOptions(visible(Side.Right, true))
        assertEquals(listOf("close:left:false", "lock:right:false", "open:right:false"), commands())
        assertEquals(Side.Right, menu.motion.openSide)
        assertFalse(menu.options.sideMenu.right.visible.hasValue())
        // Its screen's events come with the side menu.
        assertEquals(listOf("stateNeeded"), events())
        menu.onViewWillAppear()
        menu.onViewDidAppear()
        assertEquals(
            listOf("will:Center", "will:Right", "didAppear:Center", "stateNeeded", "didAppear:Right", "stateNeeded"),
            events(),
        )
    }

    @Test
    fun theLayoutsVisibleOpensAtTheFirstShowWithoutAnimationOnce() {
        val menu = menu(options = visible(Side.Left, true))
        menu.onViewWillAppear()
        assertEquals(listOf("close:right:false", "lock:left:false", "open:left:false"), commands().take(3))
        assertEquals(listOf("stateNeeded", "will:Center", "will:Left"), events())
        menu.onViewDidAppear()
        assertEquals(listOf("didAppear:Center", "stateNeeded", "didAppear:Left", "stateNeeded"), events())
        assertEquals(Side.Left, menu.motion.openSide)
        // Closed, then shown again: the layout's `visible` acted once.
        menu.mergeOptions(visible(Side.Left, false, animate = false))
        menu.onViewDisappear()
        menu.onViewWillAppear()
        menu.onViewDidAppear()
        assertNull(menu.motion.openSide)
        assertFalse(commands().any { it.startsWith("open") })
    }

    @Test
    fun visibleOfTheDefaultsOrOfResolvedOptionsOpensNothing() {
        DefaultOptions.set(visible(Side.Left, true))
        val center = screen("Center", visible(Side.Left, true))
        val menu = shown(menu(center))
        center.onViewDisappear()
        center.onViewWillAppear()
        menu.mergeOptions(options { sideMenu.left.width = Fraction(200.0) })
        assertFalse(commands().any { it.startsWith("open") })
        assertNull(menu.motion.openSide)
    }

    // The appearance row: `enabled` of the screen on top of the center, as it resolves now.

    @Test
    fun theLockFollowsTheCentersScreenAtEachAppearance() {
        val center = screen("Center", options { sideMenu.left.enabled = Bool(false) })
        shown(menu(center))
        assertTrue(drawer.isLocked(Side.Left))
        assertFalse(drawer.isLocked(Side.Right))
        center.changeOptions { it.sideMenu.left.enabled = Bool(true) }
        center.onViewDisappear()
        center.onViewWillAppear()
        assertFalse(drawer.isLocked(Side.Left))
    }

    @Test
    fun theDefaultsEnabledCounts() {
        DefaultOptions.set(options { sideMenu.right.enabled = Bool(false) })
        shown(menu())
        assertTrue(drawer.isLocked(Side.Right))
    }

    @Test
    fun aMergeOfEnabledLocksAtOnceAndAnOpenMenuStaysOpen() {
        val center = screen("Center")
        val menu = shown(menu(center))
        menu.mergeOptions(visible(Side.Left, true, animate = false))
        menu.mergeOptions(options { sideMenu.left.enabled = Bool(false) })
        assertFalse(drawer.isLocked(Side.Left))
        assertEquals(Side.Left, menu.motion.openSide)
        menu.mergeOptions(visible(Side.Left, false, animate = false))
        assertTrue(drawer.isLocked(Side.Left))
    }

    // What goes up.

    @Test
    fun mergesGoUpWithoutSideMenu() {
        val parent = RecordingParent(activity, registry)
        val center = screen("Center")
        val menu = menu(center)
        parent.add(menu)
        center.mergeOptions(options {
            sideMenu.left.visible = Bool(true)
            bottomTabs.visible = Bool(false)
        })
        menu.mergeOptions(options { sideMenu.right.enabled = Bool(false) })
        assertEquals(2, parent.merged.size)
        assertTrue(parent.merged.none { it.sideMenu.hasValue() })
        assertTrue(parent.merged.first().bottomTabs.visible.isFalse)
    }

    @Test
    fun onlyTheCurrentPartsAppearanceAndTabBarGoUp() {
        val parent = RecordingParent(activity, registry)
        val center = screen("Center", options { bottomTabs.visible = Bool(false) })
        val left = screen("Left")
        val menu = menu(center, left)
        parent.add(menu)
        shown(menu)
        assertEquals(1, parent.applied.size)
        assertTrue(parent.applied.single().bottomTabs.visible.isFalse)
        assertFalse(parent.applied.single().sideMenu.hasValue())
        // A menu screen appearing while the center is current doesn't reach the layouts around.
        left.onViewWillAppear()
        assertEquals(1, parent.applied.size)
        menu.tabBarTransition(left, TabBarMotion.Move.AtOnce)
        menu.tabBarTransition(center, TabBarMotion.Move.AtOnce)
        menu.tabBarSettled(left)
        menu.tabBarSettled(center)
        assertEquals(listOf("transition", "settled"), parent.tabBar)
    }

    @Test
    fun theCentersOptionsGoUpOnceAsAnOpenMenuCloses() {
        val parent = RecordingParent(activity, registry)
        val center = screen("Center")
        val left = screen("Left")
        val menu = menu(center, left)
        parent.add(menu)
        shown(menu)
        menu.mergeOptions(visible(Side.Left, true, animate = false))
        parent.applied.clear()
        // A pick in the menu: the center's screen shows other options while the menu is current.
        center.changeOptions { it.bottomTabs.visible = Bool(false) }
        center.onViewDisappear()
        center.onViewWillAppear()
        assertTrue(parent.applied.isEmpty())
        menu.mergeOptions(visible(Side.Left, false, animate = false))
        assertEquals(1, parent.applied.size)
        assertTrue(parent.applied.single().bottomTabs.visible.isFalse)
        assertFalse(parent.applied.single().sideMenu.hasValue())
        // A menu that never opened gives nothing more.
        drawer.drag(Side.Right)
        drawer.frame(Side.Right, 0.3f)
        drawer.release(Side.Right, 0f)
        drawer.settle(Side.Right)
        assertEquals(1, parent.applied.size)
    }

    // Back and buttons.

    @Test
    fun backClosesAnOpenMenuElseGoesToTheCenter() {
        val menu = shown(menu())
        assertFalse(menu.canHandleBack())
        assertFalse(menu.handleBack(NoListener))
        menu.mergeOptions(visible(Side.Right, true, animate = false))
        calls.clear()
        assertTrue(menu.canHandleBack())
        assertTrue(menu.handleBack(NoListener))
        assertEquals(listOf("close:right:true"), commands())
    }

    @Test
    fun aPressAddressedToTheSideMenuGoesToTheCenter() {
        val menu = shown(menu())
        menu.mergeOptions(visible(Side.Left, true, animate = false))
        journal.clear()
        menu.sendOnNavigationButtonPressed("Done")
        assertEquals(listOf("pressed:Center:Done"), events())
    }

    // Destroy.

    @Test
    fun aDestroyedSideMenuHearsNothingAndItsChildrenGo() {
        val left = screen("Left")
        val menu = shown(menu(left = left))
        menu.mergeOptions(visible(Side.Left, true))
        menu.destroy()
        journal.clear()
        drawer.frame(Side.Left, 0.5f)
        drawer.settle(Side.Left)
        assertTrue(events().isEmpty())
        assertTrue(left.isDestroyed)
        assertNull(registry.find(MENU))
        menu.destroy()
    }

    // The factory.

    @Test
    fun theFactoryMakesASideMenuAndNotASideAlone() {
        val factory = LayoutFactory(activity, registry)
        val layout = node(
            LayoutType.SideMenuRoot,
            "Menu1",
            node(LayoutType.SideMenuLeft, "Left1", node(LayoutType.Component, "Settings1", name = "Settings")),
            node(LayoutType.SideMenuCenter, "Center1", node(LayoutType.Component, "Home1", name = "Home")),
        )
        assertTrue(factory.canMake(layout))
        val made = factory.create(layout)
        assertTrue(made is SideMenuController)
        assertSame(made, registry.find("Menu1"))
        assertNull(registry.find("Left1"))
        assertEquals(listOf("Home1", "Settings1"), (made as SideMenuController).childControllers.map { it.id })
        assertTrue(made.childControllers.last().isInSideMenu)
        try {
            factory.create(layout.children.first())
            fail("a side alone is made")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("SideMenuLeft") == true)
        }
        made.destroy()
    }

    private fun node(type: LayoutType, id: String, vararg children: LayoutNode, name: String? = null) = LayoutNode(
        id = id,
        type = type,
        name = name,
        options = Options(),
        key = null,
        passProps = null,
        functions = emptyList(),
        columns = emptyList(),
        arrangement = null,
        children = children.toList(),
    )

    // A layout around the side menu: what reaches it from its child.
    private class RecordingParent(activity: Activity, registry: ChildControllersRegistry) :
        TestLayout(activity, registry, "Around") {
        val merged = mutableListOf<Options>()
        val applied = mutableListOf<Options>()
        val tabBar = mutableListOf<String>()

        override fun mergeChildOptions(options: Options, child: ViewController<*>) {
            merged += options
        }

        override fun applyChildOptions(options: Options, child: ViewController<*>) {
            applied += options
        }

        override fun tabBarTransition(
            child: ViewController<*>,
            move: TabBarMotion.Move,
            side: BottomTabsEnterExitAnimationOptions?,
        ) {
            tabBar += "transition"
        }

        override fun tabBarSettled(child: ViewController<*>) {
            tabBar += "settled"
        }
    }

    private object NoListener : CommandListener {
        override fun onSuccess(childId: String) = Unit

        override fun onError(message: String) = Unit
    }

    private companion object {
        const val MENU = "Menu"
    }
}

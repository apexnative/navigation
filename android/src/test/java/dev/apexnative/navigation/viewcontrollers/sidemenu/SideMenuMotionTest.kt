package dev.apexnative.navigation.viewcontrollers.sidemenu

import android.app.Activity
import dev.apexnative.navigation.options.SideMenuOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.react.events.FakeContent
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.Side
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The motion of a side menu's drawer with a fake `DrawerLayout` and the real dispatcher of the events on a fake clock:
 * the events and the state at each rest, the commands, the locks, back, a drawer that doesn't call back, nothing after
 * a destroy. `state`, `back` and `shown` are the host's requests at a rest.
 */
class SideMenuMotionTest {
    private val journal = mutableListOf<String>()
    private val fixture = DispatcherFixture(EventJournal(journal))
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val calls = mutableListOf<String>()
    private val leftContent = FakeContent(ready = true)
    private val left = ReportingMenuScreen(activity, registry, "Left", content = leftContent)
    private val right = ReportingMenuScreen(activity, registry, "Right")
    private val drawer = FakeDrawer(calls)
    private val host = FakeMotionHost(journal, left, right)
    private val motion = SideMenuMotion({ drawer }, host).also { drawer.motion = it }

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    private fun events(): List<String> = journal.toList().also { journal.clear() }

    private fun commands(): List<String> = calls.toList().also { calls.clear() }

    // The menu on [side] open by a jump, the journals cleared.
    private fun openAtOnce(side: Side) {
        motion.command(side, visible = true, animate = false, shown = true)
        journal.clear()
        calls.clear()
    }

    // Opening and closing.

    @Test
    fun anAnimatedOpenGivesWillAppearAtItsFirstFrameThenDidAppearThenTheState() {
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        assertEquals(listOf("close:right:true", "lock:left:false", "open:left:true"), commands())
        assertTrue(events().isEmpty())
        assertNull(motion.openSide)
        drawer.frame(Side.Left, 0.3f)
        assertEquals(listOf("will:Left"), events())
        drawer.frame(Side.Left, 0.8f)
        assertTrue(events().isEmpty())
        drawer.settle(Side.Left)
        assertEquals(listOf("didAppear:Left", "stateNeeded", "state", "back", "shown"), events())
        assertEquals(Side.Left, motion.openSide)
    }

    @Test
    fun anOpenWithoutAnimationGivesWillAppearThenDidAppearAndItsCloseDidDisappear() {
        motion.command(Side.Left, visible = true, animate = false, shown = true)
        assertEquals(listOf("close:right:false", "lock:left:false", "open:left:false"), commands())
        // As on iOS: WillAppear first, also when the drawer jumps open.
        assertEquals(listOf("will:Left", "didAppear:Left", "stateNeeded", "state", "back", "shown"), events())
        assertEquals(Side.Left, motion.openSide)
        motion.command(Side.Left, visible = false, animate = false, shown = true)
        assertEquals(listOf("close:left:false"), commands())
        assertEquals(listOf("didDisappear:Left", "stateNeeded", "center", "state", "back", "shown"), events())
        assertNull(motion.openSide)
        assertEquals(listOf(Side.Left), drawer.hidden)
    }

    @Test
    fun aCloseKeepsTheMenuOpenUntilItsEnd() {
        openAtOnce(Side.Left)
        motion.command(Side.Left, visible = false, animate = true, shown = true)
        assertEquals(listOf("close:left:true"), commands())
        drawer.frame(Side.Left, 0.4f)
        assertEquals(Side.Left, motion.openSide)
        assertTrue(events().isEmpty())
        drawer.settle(Side.Left)
        assertEquals(listOf("didDisappear:Left", "stateNeeded", "center", "state", "back", "shown"), events())
        assertNull(motion.openSide)
    }

    @Test
    fun aDragReleasedBackGivesWillAppearThenDidDisappearAndTheLock() {
        host.enabled[Side.Left] = true
        drawer.drag(Side.Left)
        drawer.frame(Side.Left, 0.2f)
        assertEquals(listOf("will:Left"), events())
        // `enabled: false` comes while the menu is on screen: its lock waits for its rest.
        host.enabled[Side.Left] = false
        motion.applyLocks()
        assertFalse(drawer.isLocked(Side.Left))
        drawer.release(Side.Left, 0f)
        drawer.settle(Side.Left)
        assertEquals(listOf("didDisappear:Left", "stateNeeded", "state", "back", "shown"), events())
        assertTrue(drawer.isLocked(Side.Left))
        assertNull(motion.openSide)
    }

    @Test
    fun aDragToTheEndOpensTheMenu() {
        drawer.drag(Side.Right)
        drawer.frame(Side.Right, 0.6f)
        drawer.release(Side.Right, 1f)
        drawer.settle(Side.Right)
        assertEquals(listOf("will:Right", "didAppear:Right", "stateNeeded", "state", "back", "shown"), events())
        assertEquals(Side.Right, motion.openSide)
    }

    @Test
    fun anOpenClosesTheOtherMenuFirstAndTheOpenSideIsTheNewOne() {
        openAtOnce(Side.Right)
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        assertEquals(listOf("close:right:true", "lock:left:false", "open:left:true"), commands())
        drawer.frame(Side.Right, 0.5f)
        drawer.frame(Side.Left, 0.5f)
        // The left's end comes before the right's (two settles of the same length).
        drawer.settle(Side.Left)
        drawer.settle(Side.Right)
        assertEquals(
            listOf(
                "will:Left",
                "didAppear:Left",
                "stateNeeded",
                "state",
                "back",
                "shown",
                "didDisappear:Right",
                "stateNeeded",
                "state",
                "back",
                "shown",
            ),
            events(),
        )
        assertEquals(Side.Left, motion.openSide)
    }

    @Test
    fun bothSidesInOneMergeOpenOnlyTheRight() {
        val merge = SideMenuOptions().apply {
            left.visible = Bool(true)
            right.visible = Bool(true)
        }
        SideMenuRules.commands(merge).forEach { motion.command(it.side, it.visible, it.animate, shown = true) }
        assertEquals(
            listOf(
                "close:right:true",
                "lock:left:false",
                "open:left:true",
                "close:left:true",
                "lock:right:false",
                "open:right:true",
            ),
            commands(),
        )
        drawer.frame(Side.Right, 0.5f)
        drawer.settle(Side.Right)
        // The left one never moved: it rests closed as the right one starts, with nothing to tell of its screen.
        assertEquals(
            listOf("state", "back", "shown", "will:Right", "didAppear:Right", "stateNeeded", "state", "back", "shown"),
            events(),
        )
        assertEquals(Side.Right, motion.openSide)
        assertEquals(0f, drawer.offsetOf(Side.Left))
        assertFalse(motion.isOnScreen(Side.Left))
    }

    @Test
    fun theLastCommandWinsAndTurnsACloseRound() {
        openAtOnce(Side.Left)
        motion.command(Side.Left, visible = false, animate = true, shown = true)
        drawer.frame(Side.Left, 0.5f)
        // `animate: false` while the drawer moves runs animated from where it is.
        motion.command(Side.Left, visible = true, animate = false, shown = true)
        assertEquals(listOf("close:left:true", "close:right:true", "open:left:true"), commands())
        drawer.settle(Side.Left)
        // The drawer turned round: it never closed, so nothing is told.
        assertTrue(events().isEmpty())
        assertEquals(Side.Left, motion.openSide)
    }

    @Test
    fun anOpeningTurnedRoundRestsClosedWithItsDidDisappear() {
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        drawer.frame(Side.Left, 0.4f)
        motion.command(Side.Left, visible = false, animate = true, shown = true)
        drawer.settle(Side.Left)
        assertEquals(listOf("will:Left", "didDisappear:Left", "stateNeeded", "state", "back", "shown"), events())
        assertNull(motion.openSide)
    }

    @Test
    fun anOpenCutBeforeItsFirstFrameTellsNothingAndClosesNothing() {
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        motion.command(Side.Left, visible = false, animate = true, shown = true)
        drawer.settle(Side.Left)
        assertEquals(listOf("state", "back", "shown"), events())
        assertNull(motion.openSide)
        assertFalse(motion.isOnScreen(Side.Left))
    }

    @Test
    fun visibleFalseOfAMenuNotOnScreenDoesNothing() {
        motion.command(Side.Right, visible = false, animate = true, shown = true)
        assertTrue(commands().isEmpty())
        assertTrue(events().isEmpty())
    }

    @Test
    fun aSideTheLayoutDoesntHaveIsAWarningOnly() {
        val drawer = FakeDrawer(calls, setOf(Side.Left))
        val motion = SideMenuMotion({ drawer }, FakeMotionHost(journal, left, null)).also { drawer.motion = it }
        motion.command(Side.Right, visible = true, animate = true, shown = true)
        motion.command(Side.Right, visible = false, animate = true, shown = true)
        assertEquals(listOf("missing:right"), events())
        assertTrue(commands().isEmpty())
        // The other side isn't closed where there is none.
        motion.command(Side.Left, visible = true, animate = false, shown = true)
        assertEquals(listOf("lock:left:false", "open:left:false"), commands())
    }

    // The side menu not on screen.

    @Test
    fun offScreenTheMenuOpensAtOnceAndTellsNothingUntilTheSideMenuAppears() {
        host.appeared = false
        motion.command(Side.Left, visible = true, animate = true, shown = false)
        assertEquals(listOf("close:right:false", "lock:left:false", "open:left:false"), commands())
        assertEquals(listOf("state", "back", "shown"), events())
        assertEquals(Side.Left, motion.openSide)
    }

    @Test
    fun aDrawerThatDoesntCallBackHasItsResultRecorded() {
        drawer.callsBack = false
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        assertEquals(listOf("will:Left", "didAppear:Left", "stateNeeded", "state", "back", "shown"), events())
        assertEquals(Side.Left, motion.openSide)
        motion.command(Side.Left, visible = false, animate = true, shown = true)
        assertEquals(listOf("didDisappear:Left", "stateNeeded", "center", "state", "back", "shown"), events())
        assertNull(motion.openSide)
    }

    @Test
    fun aDrawerThatDoesntCallBackOpenedWithoutAnimationSendsWillAppearThenDidAppear() {
        drawer.callsBack = false
        motion.command(Side.Left, visible = true, animate = false, shown = true)
        assertEquals(listOf("will:Left", "didAppear:Left", "stateNeeded", "state", "back", "shown"), events())
    }

    @Test
    fun theOpenSideStaysAcrossTheSideMenusDisappearance() {
        openAtOnce(Side.Left)
        motion.containerDisappeared()
        host.appeared = false
        assertEquals(Side.Left, motion.openSide)
        assertTrue(motion.canGoBack)
    }

    // Locks.

    @Test
    fun aLockedMenuOpenedFromCodeIsUnlockedWhileOpenAndLockedAgainAtItsRest() {
        host.enabled[Side.Left] = false
        motion.applyLocks()
        assertTrue(drawer.isLocked(Side.Left))
        assertFalse(drawer.isLocked(Side.Right))
        openAtOnce(Side.Left)
        assertFalse(drawer.isLocked(Side.Left))
        // `enabled: false` merged while it is open: it stays open, and a drag can close it.
        motion.applyLocks()
        assertFalse(drawer.isLocked(Side.Left))
        assertEquals(Side.Left, motion.openSide)
        drawer.drag(Side.Left)
        drawer.frame(Side.Left, 0.3f)
        drawer.release(Side.Left, 0f)
        drawer.settle(Side.Left)
        assertTrue(drawer.isLocked(Side.Left))
        assertNull(motion.openSide)
    }

    @Test
    fun theDeferredLockIsReadFromTheOptionsAtTheRest() {
        host.enabled[Side.Left] = false
        openAtOnce(Side.Left)
        // The screen that set it left the top meanwhile.
        host.enabled[Side.Left] = true
        motion.command(Side.Left, visible = false, animate = false, shown = true)
        assertFalse(drawer.isLocked(Side.Left))
    }

    // Back.

    @Test
    fun backClosesAnOpenMenuWithAnimation() {
        openAtOnce(Side.Left)
        assertTrue(motion.canGoBack)
        assertTrue(motion.back())
        assertEquals(listOf("close:left:true"), commands())
        // During the close the menu is still open: back is taken and nothing more happens.
        drawer.frame(Side.Left, 0.5f)
        assertTrue(motion.canGoBack)
        assertTrue(motion.back())
        drawer.settle(Side.Left)
        assertFalse(motion.canGoBack)
        assertFalse(motion.back())
    }

    @Test
    fun backDuringAnOpeningIsTheCenters() {
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        drawer.frame(Side.Left, 0.5f)
        assertFalse(motion.canGoBack)
        assertFalse(motion.back())
    }

    // The dispatcher's order (the events, then the state).

    @Test
    fun theStateWaitsForAHeldDidAppear() {
        val content = FakeContent(ready = false)
        val held = ReportingMenuScreen(activity, registry, "Held", content = content)
        val drawer = FakeDrawer(calls)
        val motion = SideMenuMotion({ drawer }, FakeMotionHost(journal, held, null)).also { drawer.motion = it }
        motion.command(Side.Left, visible = true, animate = false, shown = true)
        assertEquals(Side.Left, motion.openSide)
        assertEquals(listOf("back", "shown"), events())
        content.mount()
        assertEquals(listOf("will:Held", "didAppear:Held", "stateNeeded", "state"), events())
    }

    @Test
    fun aJumpOpenClosedBeforeItsContentMountedTellsNothing() {
        val content = FakeContent(ready = false)
        val held = ReportingMenuScreen(activity, registry, "Held", content = content)
        val drawer = FakeDrawer(calls)
        val motion = SideMenuMotion({ drawer }, FakeMotionHost(journal, held, null)).also { drawer.motion = it }
        motion.command(Side.Left, visible = true, animate = false, shown = true)
        motion.command(Side.Left, visible = false, animate = false, shown = true)
        assertEquals(listOf("back", "shown", "state", "center", "state", "back", "shown"), events())
        content.mount()
        assertTrue(events().isEmpty())
    }

    @Test
    fun aJumpOpenThenAnAnimatedOneGiveTheirEventsEachTime() {
        openAtOnce(Side.Left)
        motion.command(Side.Left, visible = false, animate = false, shown = true)
        journal.clear()
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        drawer.frame(Side.Left, 0.5f)
        drawer.settle(Side.Left)
        assertEquals(listOf("will:Left", "didAppear:Left", "stateNeeded", "state", "back", "shown"), events())
    }

    // A drawer of no width: `DrawerLayout` slides it by NaN at each layout.

    @Test
    fun aSlideOfNoNumberStartsNothingAndTheSideStillLocks() {
        host.enabled[Side.Left] = false
        drawer.frame(Side.Left, Float.NaN)
        assertTrue(events().isEmpty())
        assertFalse(motion.isOnScreen(Side.Left))
        motion.applyLocks()
        assertTrue(drawer.isLocked(Side.Left))
    }

    // Destroy.

    @Test
    fun nothingHappensAfterADestroy() {
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        calls.clear()
        motion.destroy()
        drawer.frame(Side.Left, 0.5f)
        drawer.settle(Side.Left)
        motion.command(Side.Right, visible = true, animate = true, shown = true)
        motion.applyLocks()
        assertFalse(motion.back())
        assertTrue(events().isEmpty())
        assertTrue(commands().isEmpty())
        assertNull(motion.openSide)
    }

    // The system's animations off.

    @Test
    fun withTheSystemsAnimationsOffACommandAndBackJumpButASettlingDrawerStillMoves() {
        val drawer = FakeDrawer(calls, setOf(Side.Left))
        val host = FakeMotionHost(journal, left, null)
        val motion = SideMenuMotion({ drawer }, host, animationsOff = { true }).also { drawer.motion = it }
        val moves = { commands().filterNot { it.startsWith("lock") } }
        motion.command(Side.Left, visible = true, animate = true, shown = true)
        assertEquals(listOf("open:left:false"), moves())
        assertEquals(Side.Left, motion.openSide)
        assertTrue(motion.back())
        assertEquals(listOf("close:left:false"), moves())
        assertNull(motion.openSide)
        // The drag and the settle aren't animators: a command while the drawer settles goes on from where it is.
        drawer.drag(Side.Left)
        drawer.frame(Side.Left, 0.5f)
        drawer.release(Side.Left, 1f)
        motion.command(Side.Left, visible = false, animate = true, shown = true)
        assertEquals(listOf("close:left:true"), moves())
    }

    // A menu starts to show.

    @Test
    fun aMenuStartsToShowAtItsFirstMovementOnceUntilItRestsClosed() {
        drawer.drag(Side.Left)
        assertTrue(host.started.isEmpty())
        drawer.frame(Side.Left, 0.2f)
        drawer.frame(Side.Left, 0.6f)
        assertEquals(listOf(Side.Left), host.started)
        drawer.release(Side.Left, 0f)
        drawer.settle(Side.Left)
        drawer.drag(Side.Left)
        drawer.frame(Side.Left, 0.1f)
        assertEquals(listOf(Side.Left, Side.Left), host.started)
    }

    @Test
    fun aMenuOpenedByACommandStartsBeforeTheDrawerMoves() {
        motion.command(Side.Right, visible = true, animate = true, shown = true)
        assertEquals(listOf(Side.Right), host.started)
        drawer.frame(Side.Right, 0.5f)
        motion.command(Side.Right, visible = true, animate = true, shown = true)
        assertEquals(listOf(Side.Right), host.started)
        // A close starts nothing.
        motion.command(Side.Left, visible = false, animate = true, shown = true)
        assertEquals(listOf(Side.Right), host.started)
    }
}

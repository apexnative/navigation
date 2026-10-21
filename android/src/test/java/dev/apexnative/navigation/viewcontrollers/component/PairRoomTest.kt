package dev.apexnative.navigation.viewcontrollers.component

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.parent.KeepsStatusBarRoom
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The room of a pair: its screens apply the pair's room as theirs would apply, and the pair's room at its top is the
 * screens' rule ([ScreenFrame.topRoomOf]) with the options it shows with. The pair itself reads the window's metrics,
 * which only a device has: a layout that keeps the status bar's room stands for it here.
 */
class PairRoomTest {
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    // A pair: it keeps the room of its screens, and records each time its room applies.
    private inner class Pair : TestLayout(activity, registry, "Pair1"), KeepsStatusBarRoom {
        val applied = mutableListOf<String>()

        override fun applyTopInset() {
            applied += "top"
        }

        override fun applyBottomInset() {
            applied += "bottom"
        }
    }

    // A stack whose bar shows: its room under the bar is [room], and it keeps the status bar's room when [keeps].
    private inner class Stack(
        private val room: Int,
        private val keeps: Boolean,
    ) : TestLayout(activity, registry, "Stack1") {
        override fun getTopInset(child: ViewController<*>): Int = room

        override fun keepsStatusBarRoomOf(child: ViewController<*>): Boolean = keeps
    }

    private fun options(drawBehind: Boolean = false) = Options().apply {
        if (drawBehind) statusBar.drawBehind = Bool(true)
    }

    @Test
    fun aScreenOfAPairAppliesThePairsRoom() {
        val pair = Pair()
        val screen = TestScreen(activity, registry, "Home1")
        pair.add(screen)
        ScreenFrame(screen, consumesInsets = false).applyRoom()
        assertEquals(listOf("top", "bottom"), pair.applied)
    }

    @Test
    fun underABarThatKeepsTheStatusBarsRoomThePairCountsItOnce() {
        val stack = Stack(room = STATUS_BAR + BAR, keeps = true)
        val pair = TestLayout(activity, registry, "Pair1")
        stack.add(pair)
        assertEquals(STATUS_BAR + BAR, ScreenFrame.topRoomOf(pair, options(), STATUS_BAR))
    }

    @Test
    fun underABarDrawnBelowTheStatusBarThePairStartsBelowBoth() {
        val stack = Stack(room = BAR, keeps = false)
        val pair = TestLayout(activity, registry, "Pair1")
        stack.add(pair)
        assertEquals(STATUS_BAR + BAR, ScreenFrame.topRoomOf(pair, options(), STATUS_BAR))
    }

    @Test
    fun aPrimaryDrawnBehindTheStatusBarKeepsNoRoomForIt() {
        val pair = TestLayout(activity, registry, "Pair1")
        assertEquals(0, ScreenFrame.topRoomOf(pair, options(drawBehind = true), STATUS_BAR))
        val stack = Stack(room = BAR, keeps = false)
        stack.add(pair)
        assertEquals(BAR, ScreenFrame.topRoomOf(pair, options(drawBehind = true), STATUS_BAR))
    }

    private companion object {
        const val STATUS_BAR = 63
        const val BAR = 168
    }
}

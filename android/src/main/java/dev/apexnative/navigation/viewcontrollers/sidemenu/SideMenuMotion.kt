package dev.apexnative.navigation.viewcontrollers.sidemenu

import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.utils.SystemAnimations
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The rules of a side menu's drawer on Android, without views: what the drawer's callbacks mean for the menu screens'
 * appearance, the open side, the locks, the state and back, and how a command (`sideMenu.<side>.visible`, back) drives
 * the drawer. The drawer is behind [Drawer] (`SideMenuView`), the node behind [Host]. Main thread only.
 *
 * - "Open" ([openSide]) is from the end of the opening to the end of the closing: the side menu's current child, the
 *  state and back change only at the end of each motion.
 * - A side is "on screen" from the command that opens it, or its first movement, until it rests closed: it is never
 *  locked then, and `visible: false` closes it. It rests closed at `onDrawerClosed`, or, for a side
 *   that never opened (a drag released back, an opening turned round), when the drawer comes to rest with it hidden:
 *   the drawer calls `onDrawerClosed` only for a drawer that had opened.
 * - The menu screen's events: WillAppear at the side's first movement, DidAppear when it opened, DidDisappear when it
 *   rests closed; only while the side menu has appeared ([Host.appeared]) — otherwise they come with the side menu's
 *   own appearance (its visible children). An open without animation sends both too, WillAppear first, as on iOS:
 *   the drawer jumps to its open offset before it calls the open.
 * - At each rest: one state update after the menu's events, the back callback, and the layers that follow the shown
 *  screen.
 * - A drawer that doesn't call back (not laid out since it joined the window, or not in one: `DrawerLayout` only
 *  writes its state then) gets the result recorded here, as if it had called back.
 */
internal class SideMenuMotion(
    private val drawer: () -> Drawer,
    private val host: Host,
    /** The system's animations are off ([SystemAnimations]): a command or back moves the drawer at once. */
    private val animationsOff: () -> Boolean = { SystemAnimations.areOff() },
) {
    /** A side of the side menu: physical, `left` stays on the left in RTL too. */
    enum class Side(
        /** The name in the options and the logs. */
        val jsName: String,
    ) {
        Left("left"),
        Right("right"),
        ;

        /** The side across. */
        val other: Side
            get() = if (this == Left) Right else Left
    }

    /** How the drawer moves, as its listener says: at rest, dragged by the user, or settling to a place. */
    enum class DrawerState { Idle, Dragging, Settling }

    /** The drawer of the side menu (`SideMenuView`); the unit tests give their own. */
    interface Drawer {
        /** The layout has a menu on [side]. */
        fun has(side: Side): Boolean

        /** The drawer calls its listener back: it is in the window and laid out since it joined it. */
        val callsBack: Boolean

        /** Opens the menu on [side]: `openDrawer`. */
        fun open(side: Side, animate: Boolean)

        /** Closes the menu on [side]: `closeDrawer`. */
        fun close(side: Side, animate: Boolean)

        /** The user can't open the menu on [side] by a gesture ([locked]), or can. */
        fun lock(side: Side, locked: Boolean)

        /** Some of the menu on [side] shows. */
        fun isVisible(side: Side): Boolean

        /** The menu on [side] shows no more, before the drawer hides it itself. */
        fun hide(side: Side)
    }

    /** What the motion asks of its side menu. */
    interface Host {
        /** The side menu has appeared, and hasn't disappeared since. */
        val appeared: Boolean

        /** The node in the menu on [side]; none when the layout has no menu there. */
        fun child(side: Side): ViewController<*>?

        /** `sideMenu.<side>.enabled` of the side menu's options now. */
        fun isEnabled(side: Side): Boolean

        /**
         * The menu on [side] starts to show: the drawer's first movement, or a command that opens it. Before its
         * screen hears that it appears.
         */
        fun starts(side: Side)

        /** `visible: true` for a side the layout doesn't have: a warning, nothing else. */
        fun missingSide(side: Side)

        /** The navigation state may have changed: one update in this pass of the main loop. */
        fun stateChanged()

        /** What back does may have changed. */
        fun backChanged()

        /** The screen the side menu shows changed (a sheet's hold, a waiting layer). */
        fun shownChanged()

        /**
         * The open menu closed and the center is the current part again: what the center shows goes up once to the
         * layouts around, which heard nothing of the center while the menu was current (a menu pick that pushed).
         */
        fun centerCurrentAgain()
    }

    /** The side whose menu is open; none while only the center shows, or a menu only opens. */
    var openSide: Side? = null
        private set

    // The sides on screen: from their open command or first movement until they rest closed.
    private val onScreen = HashSet<Side>(2)
    // The sides whose menu screen was told it starts to appear in this appearance of the side menu.
    private val appearing = HashSet<Side>(2)
    // The drawer is dragged or settles.
    private var moving = false
    private var destroyed = false

    /** The menu on [side] is on screen: open, or on its way, until it rests closed. */
    fun isOnScreen(side: Side): Boolean = side in onScreen || openSide == side

    // The drawer's listener.

    /** The menu on [side] moved to [offset] (0 hidden, 1 open): its first movement starts its appearance. */
    fun slid(side: Side, offset: Float) {
        // Not more than 0 also takes a NaN: `DrawerLayout` divides by the drawer's width, which may be 0.
        if (destroyed || !(offset > 0f)) return
        if (!isOnScreen(side)) host.starts(side)
        onScreen += side
        if (side in appearing || !host.appeared) return
        appearing += side
        host.child(side)?.onViewWillAppear()
    }

    /** The menu on [side] opened (`onDrawerOpened`). */
    fun opened(side: Side) {
        if (destroyed) return
        openSide = side
        onScreen += side
        val child = host.child(side)
        if (host.appeared && child != null) {
            appearing += side
            child.onViewDidAppear()
        }
        rested(child)
    }

    /** The menu on [side] rests closed (`onDrawerClosed`, or the rest of a side that never opened). */
    fun closed(side: Side) {
        if (destroyed || !isOnScreen(side)) return
        drawer().hide(side)
        val wasOpen = openSide == side
        if (wasOpen) openSide = null
        onScreen -= side
        appearing -= side
        if (host.appeared) host.child(side)?.onViewDisappear()
        // The lock wanted while it showed applies now, as the options say now.
        applyLock(side)
        if (wasOpen) host.centerCurrentAgain()
        rested(null)
    }

    /** The drawer is dragged, settles or comes to rest (`onDrawerStateChanged`). */
    fun stateChanged(state: DrawerState) {
        if (destroyed) return
        moving = state != DrawerState.Idle
        if (moving) return
        val drawer = drawer()
        for (side in Side.entries) {
            if (side in onScreen && openSide != side && !drawer.isVisible(side)) closed(side)
        }
    }

    // The side menu as a container.

    /** The side menu appears: its open menu appears with it (its visible children). */
    fun containerAppeared() {
        appearing.clear()
        openSide?.let { appearing += it }
    }

    /** The side menu disappeared, with every child: the open menu stays open. */
    fun containerDisappeared() {
        appearing.clear()
    }

    // Commands.

    /**
     * `sideMenu.<side>.visible`: [visible] opens the menu on [side], closing the other one first, whatever its
     * `enabled`; false closes it when it is on screen. [animate] is the command's `animate`; [shown] says whether the
     * side menu is on screen. While the drawer moves the command runs animated from where it is: a drawer can't jump
     * while it settles. Off screen it runs without animation. A side the layout doesn't have: a warning for an open,
     * nothing else.
     */
    fun command(side: Side, visible: Boolean, animate: Boolean, shown: Boolean) {
        if (destroyed) return
        val drawer = drawer()
        if (!drawer.has(side)) {
            if (visible) host.missingSide(side)
            return
        }
        val animated = when {
            moving -> true
            !shown -> false
            else -> animate
        }
        if (!visible) {
            if (isOnScreen(side)) move(side, open = false, animate = animated)
            return
        }
        // Never decided by where the other menu is: a side asked to open in the same merge hasn't moved yet.
        if (drawer.has(side.other)) move(side.other, open = false, animate = animated)
        if (!isOnScreen(side)) host.starts(side)
        onScreen += side
        // A locked menu opened from code can be dragged closed while it is open.
        drawer.lock(side, locked = false)
        move(side, open = true, animate = animated)
    }

    /** Back: an open menu closes, with animation, and back is used; false without one. */
    fun back(): Boolean {
        val side = openSide ?: return false
        if (destroyed) return false
        move(side, open = false, animate = true)
        return true
    }

    /** Back would close a menu: one is open. */
    val canGoBack: Boolean
        get() = !destroyed && openSide != null

    /**
     * The locks of the sides as the side menu's options say now: `enabled: false` locks a side the user then can't open
     * by a gesture; a side on screen stays unlocked until it rests closed.
     */
    fun applyLocks() {
        if (destroyed) return
        Side.entries.forEach(::applyLock)
    }

    /** The side menu is destroyed: nothing reacts to the drawer from now on. */
    fun destroy() {
        destroyed = true
    }

    private fun applyLock(side: Side) {
        val drawer = drawer()
        if (!drawer.has(side)) return
        drawer.lock(side, locked = !isOnScreen(side) && !host.isEnabled(side))
    }

    // The drawer opens or closes [side]; one that doesn't call back has its result recorded here. With the system's
    // animations off it jumps, unless it moves: the drag and the settle aren't animators, and it can't jump while it
    // settles.
    private fun move(side: Side, open: Boolean, animate: Boolean) {
        val drawer = drawer()
        val animated = animate && (moving || !animationsOff())
        val silent = !drawer.callsBack
        if (open) drawer.open(side, animated) else drawer.close(side, animated)
        if (silent) record(side, open)
    }

    private fun record(side: Side, open: Boolean) {
        if (!open) return closed(side)
        if (openSide == side) return
        slid(side, 1f)
        opened(side)
    }

    // The end of a motion: the state after the open menu's DidAppear (held while its content mounts), then back and
    // the layers that follow the shown screen.
    private fun rested(open: ViewController<*>?) {
        if (open != null) {
            AppearanceDispatcher.afterDidAppear(AppearanceDispatcher.visibleNode(open)) {
                if (!destroyed) host.stateChanged()
            }
        } else {
            host.stateChanged()
        }
        host.backChanged()
        host.shownChanged()
    }
}

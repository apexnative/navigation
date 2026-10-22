package dev.apexnative.navigation.viewcontrollers.component

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.parent.KeepsStatusBarRoom
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The room a screen's view keeps for the bars, and what the system covers: the room at the top (the status bar, then
 * what the layouts around give: the header when the screen isn't under it) and at the bottom (the tabs when it isn't
 * under them) as the margins of the view; the side bars, the navigation bar and the keyboard as its inner padding, from
 * the window insets the view gets through its own listener. A screen of a pair keeps no room: the pair keeps it for
 * both, by the same rule ([topRoomOf]), and each time the screen's room would apply it applies the pair's. Used by the
 * React screen and the native screen. Main thread only.
 */
internal class ScreenFrame(
    private val screen: ViewController<*>,
    // A native screen's container takes the insets and passes nothing on; a React screen's view lets them go on to its
    // content as they are.
    private val consumesInsets: Boolean,
) {
    /** The window insets the screen's view got last; none before the first. */
    var windowInsets: WindowInsetsCompat? = null
        private set

    /** Runs after each new window insets, once the room and the padding took them (the background's band). */
    var onInsetsApplied: (() -> Unit)? = null

    /**
     * The height of the status bar, from the insets the view got, or the window's before them; shown or not: whether
     * the screen keeps its room is for its own options to say (`statusBar.visible`, `drawBehind`), not for what another
     * layer did to the status bar.
     */
    val statusBarHeight: Int
        get() = (windowInsets ?: windowInsetsOfActivity())
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top ?: 0

    /** The screen is a screen of a pair (`KeepsStatusBarRoom`): the pair keeps the room of both. */
    val isInPair: Boolean
        get() = screen.parentController is KeepsStatusBarRoom

    /** The screen's view takes the window insets through [view]'s own listener. */
    fun attach(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            windowInsets = insets
            applyPadding(target, insets)
            applyRoom()
            onInsetsApplied?.invoke()
            if (consumesInsets) WindowInsetsCompat.CONSUMED else insets
        }
    }

    /**
     * The view joined a window: its room applies; it asks for the window insets only when it has none yet, so a
     * screen that comes back doesn't make the window send them to every view again.
     */
    fun onAttached(view: View) {
        applyRoom()
        if (windowInsets == null) ViewCompat.requestApplyInsets(view)
    }

    /** The padding applies again from the insets the view has, or from new ones (`layout.adjustResize`). */
    fun applyInsetsAgain() {
        val view = screen.peekView() ?: return
        val insets = windowInsets
        if (insets != null) applyPadding(view, insets)
        ViewCompat.requestApplyInsets(view)
    }

    /** The room at the top of the screen: the status bar's, then what the layouts around give. */
    fun topRoom(): Int = if (isInPair) 0 else topRoomOf(screen, resolvedOptions(), statusBarHeight)

    /** The room at the bottom of the screen: what the layouts around give (the tabs). */
    fun bottomRoom(): Int = if (isInPair) 0 else screen.parentController?.getBottomInset(screen) ?: 0

    /**
     * The room applies as the margins of the screen's view; nothing changes when they are the same. A screen of a pair
     * applies the pair's room: its view joining the window, new insets, a merge that changes the room and the tabs'
     * room reach the pair through its screens.
     */
    fun applyRoom() {
        if (isInPair) {
            screen.parentController?.applyTopInset()
            screen.parentController?.applyBottomInset()
        }
        val view = screen.peekView() ?: return
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val top = topRoom()
        val bottom = bottomRoom()
        if (params.topMargin == top && params.bottomMargin == bottom) return
        params.topMargin = top
        params.bottomMargin = bottom
        view.layoutParams = params
    }

    // The side bars, the navigation bar unless the screen draws under it, and the keyboard unless the screen keeps
    // its size; the tabs' room below the screen is already out of the keyboard's way.
    private fun applyPadding(view: View, insets: WindowInsetsCompat) {
        val options = resolvedOptions()
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val navigationBar = options.navigationBar
        val padding = ScreenSafeArea.roomPadding(
            systemBars = ScreenSafeArea.Box(bars.left, bars.top, bars.right, bars.bottom),
            imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
            drawsUnderNavigationBar = ScreenSafeArea.drawsUnderNavigationBar(
                drawBehind = if (navigationBar.drawBehind.hasValue()) navigationBar.drawBehind.isTrue else null,
                barColor = navigationBar.backgroundColor.get(screen.activity, null),
            ),
            adjustsForKeyboard = !options.layout.adjustResize.isFalse,
            bottomRoom = roomBelow(),
        )
        if (view.paddingLeft == padding.left && view.paddingTop == padding.top && view.paddingRight == padding.right &&
            view.paddingBottom == padding.bottom
        ) {
            return
        }
        view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
    }

    // What the screen, or the pair it is in, keeps below it for the tabs.
    private fun roomBelow(): Int {
        val parent = screen.parentController
        return if (parent is KeepsStatusBarRoom) parent.getBottomInset() else bottomRoom()
    }

    private fun windowInsetsOfActivity(): WindowInsetsCompat? {
        val decor = screen.activity.window?.peekDecorView() ?: return null
        return ViewCompat.getRootWindowInsets(decor)
    }

    private fun resolvedOptions(): Options = screen.resolveCurrentOptions(DefaultOptions.current)

    companion object {
        /**
         * The room at the top of [node], a screen or a pair, shown with [options]: the status bar's ([statusBarHeight],
         * `ScreenSafeArea.statusBarRoom`), then what the layouts around give.
         */
        fun topRoomOf(node: ViewController<*>, options: Options, statusBarHeight: Int): Int {
            val parent = node.parentController
            val statusBar = ScreenSafeArea.statusBarRoom(
                statusBarHeight = statusBarHeight,
                hiddenOrDrawBehind = options.statusBar.isHiddenOrDrawBehind(node.isInOverlay),
                inSheet = node.isInSheet,
                inSideMenu = node.isInSideMenu,
                keptAround = parent?.keepsStatusBarRoomOf(node) == true,
            )
            return statusBar + (parent?.getTopInset(node) ?: 0)
        }
    }
}

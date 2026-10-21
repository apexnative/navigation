package dev.apexnative.navigation.views.sidemenu

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.drawerlayout.widget.DrawerLayout
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuMotion.Side
import dev.apexnative.navigation.viewcontrollers.sidemenu.SideMenuRules

/**
 * The view of a side menu on Android: the Material drawer (`DrawerLayout`) with a part for each side the layout has —
 * [center] filling it, the left menu at the left edge and the right one at the right edge, in RTL too (physical sides)
 * —, each holding the view of its node. The drawer draws its default dimming (black 60 %) and shadow, keeps an open
 * menu open as its size changes, and handles the edge swipe, the tap on the dimming and the drag back itself; the side
 * menu's node drives it ([SideMenuMotion]).
 *
 * It isn't fit to the system windows: it draws no band under the status bar and leaves the insets to the screens,
 * whose safe area keeps their content off the status bar. It keeps no saved state: a recreated activity doesn't open a
 * menu behind the node. `DrawerLayout` needs an exact size: a container that measures it otherwise gets the size it
 * offers as exact. Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class SideMenuView(context: Context) : DrawerLayout(context), SideMenuMotion.Drawer {
    /** The part of the center. */
    val center = FrameLayout(context)

    private val parts = arrayOfNulls<FrameLayout>(Side.entries.size)
    // The width each side wants, in pixels; none: the drawer's own (the width less 64 dp).
    private val wantedWidths = arrayOfNulls<Int>(Side.entries.size)
    // `DrawerLayout`'s least gap between an open drawer and the far edge, as it computes it.
    private val minDrawerMargin = (MIN_DRAWER_MARGIN_DP * resources.displayMetrics.density + 0.5f).toInt()
    private var listener: DrawerListener? = null

    init {
        fitsSystemWindows = false
        setStatusBarBackground(null)
        isSaveEnabled = false
        addView(center, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** [content] fills the center. */
    fun setCenter(content: View) {
        center.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** The part of [side], holding [content]: a drawer at that edge, the whole height of the side menu. */
    fun addPart(side: Side, content: View): FrameLayout {
        val part = FrameLayout(context)
        part.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(part, LayoutParams(MATCH_PARENT, MATCH_PARENT, gravityOf(side)))
        parts[side.ordinal] = part
        return part
    }

    /** The part of [side]; none when the layout has no menu there. */
    fun partOf(side: Side): FrameLayout? = parts[side.ordinal]

    /** The width the menu on [side] wants, in pixels; none for the drawer's own. It applies at the next layout. */
    fun setWantedWidth(side: Side, width: Int?) {
        if (wantedWidths[side.ordinal] == width) return
        wantedWidths[side.ordinal] = width
        requestLayout()
    }

    /** The height of the menu on [side], in pixels, pinned to the top; none for the whole height. */
    fun setPartHeight(side: Side, height: Int?) {
        val part = partOf(side) ?: return
        part.layoutParams.height = height ?: MATCH_PARENT
        part.requestLayout()
    }

    /** How far the menu on [side] is from where it rests open, along the width. */
    fun offsetFromRest(side: Side): Int {
        val part = partOf(side) ?: return 0
        return SideMenuRules.offsetFromRest(side, width, part.left, part.right)
    }

    /** The drawer's callbacks reach [motion] from now on. */
    fun follow(motion: SideMenuMotion) {
        unfollow()
        val drawerListener = Listener(motion)
        listener = drawerListener
        addDrawerListener(drawerListener)
    }

    /** The drawer's callbacks reach nobody. */
    fun unfollow() {
        listener?.let(::removeDrawerListener)
        listener = null
    }

    // A given width is measured as it is by the drawer: it is clamped here to the width less the drawer's gap, at
    // every measure (the first, a rotation, a split screen).
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        for (side in Side.entries) {
            val part = partOf(side) ?: continue
            part.layoutParams.width = SideMenuRules.drawerWidth(wantedWidths[side.ordinal], width, minDrawerMargin)
        }
        super.onMeasure(exactly(widthMeasureSpec), exactly(heightMeasureSpec))
    }

    // Back is the navigation's: a focused drawer would close a visible menu itself before the activity's back callback,
    // the modals over it and JavaScript's `BackHandler` hear it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (SideMenuRules.leavesKeyToActivity(keyCode)) false else super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (SideMenuRules.leavesKeyToActivity(keyCode)) false else super.onKeyUp(keyCode, event)

    // The drawer of the motion.

    override fun has(side: Side): Boolean = partOf(side) != null

    override val callsBack: Boolean
        get() = isAttachedToWindow && isLaidOut

    override fun open(side: Side, animate: Boolean) {
        partOf(side)?.let { openDrawer(it, animate) }
    }

    override fun close(side: Side, animate: Boolean) {
        partOf(side)?.let { closeDrawer(it, animate) }
    }

    override fun lock(side: Side, locked: Boolean) {
        if (partOf(side) == null) return
        val mode = if (locked) LOCK_MODE_LOCKED_CLOSED else LOCK_MODE_UNLOCKED
        val gravity = gravityOf(side)
        if (getDrawerLockMode(gravity) != mode) setDrawerLockMode(mode, gravity)
    }

    override fun isVisible(side: Side): Boolean = partOf(side)?.let(::isDrawerVisible) == true

    override fun hide(side: Side) {
        partOf(side)?.visibility = INVISIBLE
    }

    private fun sideOf(view: View): Side? = Side.entries.firstOrNull { parts[it.ordinal] === view }

    private inner class Listener(private val motion: SideMenuMotion) : DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            sideOf(drawerView)?.let { motion.slid(it, slideOffset) }
        }

        override fun onDrawerOpened(drawerView: View) {
            sideOf(drawerView)?.let(motion::opened)
        }

        override fun onDrawerClosed(drawerView: View) {
            sideOf(drawerView)?.let(motion::closed)
        }

        override fun onDrawerStateChanged(newState: Int) {
            motion.stateChanged(
                when (newState) {
                    DrawerLayout.STATE_IDLE -> SideMenuMotion.DrawerState.Idle
                    DrawerLayout.STATE_DRAGGING -> SideMenuMotion.DrawerState.Dragging
                    else -> SideMenuMotion.DrawerState.Settling
                },
            )
        }
    }

    private companion object {
        // `DrawerLayout`'s MIN_DRAWER_MARGIN.
        const val MIN_DRAWER_MARGIN_DP = 64

        fun gravityOf(side: Side): Int = if (side == Side.Left) Gravity.LEFT else Gravity.RIGHT

        fun exactly(spec: Int): Int =
            if (View.MeasureSpec.getMode(spec) == View.MeasureSpec.EXACTLY) {
                spec
            } else {
                View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(spec), View.MeasureSpec.EXACTLY)
            }
    }
}

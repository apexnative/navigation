package dev.apexnative.navigation.views.stack

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.views.stack.topbar.TopBar
import java.util.Collections
import java.util.WeakHashMap

/**
 * The view of a stack: its screens fill it, bottom to top; its [topBar] lies over them at the top; the FAB, its menu
 * and the menu's scrim lie over everything. A screen that comes in goes right under the bar. A screen in the container
 * moves between its places without leaving it, so it doesn't leave and join the window again; a covered screen that
 * comes back gets the last window insets the stack got before it joins, and nothing asks the window to send them again
 * (CoordinatorLayout asks for insets only as it joins the window itself and only with `fitsSystemWindows`, which this
 * view doesn't set). Main thread only.
 */
@SuppressLint("ViewConstructor")
class StackLayout(context: Context) : CoordinatorLayout(context) {
    /** The bar over the screens. */
    val topBar = TopBar(context)

    /** The window insets the stack got last; none before the first. */
    var lastInsets: WindowInsetsCompat? = null
        private set

    /** Runs after each new window insets: the bar and the FAB take their places again. */
    internal var onInsets: (() -> Unit)? = null

    init {
        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP
        })
        // The insets go on to every child as they came: each screen takes what concerns it itself; the bar's strip is
        // its stack's to count, from these insets and the options of its screen on top.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            lastInsets = insets
            onInsets?.invoke()
            insets
        }
    }

    // The views of the screens in the container: the others (the search's scrim and suggestions, which lie between the
    // screens and the bar) stay over a screen that comes in.
    private val screenViews: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())

    // Where the screens end: right over the topmost screen view, under anything else between them and the bar.
    private val screensEnd: Int
        get() {
            var end = 0
            for (index in 0 until childCount) {
                if (getChildAt(index) in screenViews) end = index + 1
            }
            return end
        }

    /** [view] is a child of this container. */
    fun holds(view: View): Boolean = view.parent === this

    /** [view] goes right under the bar, over the screens. */
    fun placeUnderBar(view: View) {
        if (holds(view)) {
            detachViewFromParent(view)
            screenViews += view
            attachViewToParent(view, screensEnd, view.layoutParams)
            moved()
        } else {
            addScreen(view, screensEnd)
        }
    }

    /** [view] goes right under [anchor]; under the bar when [anchor] isn't in the container. */
    fun placeUnder(view: View, anchor: View?) {
        if (anchor == null || !holds(anchor) || anchor === view) return placeUnderBar(view)
        if (holds(view)) {
            detachViewFromParent(view)
            attachViewToParent(view, indexOfChild(anchor), view.layoutParams)
            moved()
        } else {
            addScreen(view, indexOfChild(anchor))
        }
    }

    /** [view] goes under every screen. */
    fun placeAtBottom(view: View) {
        if (holds(view)) {
            detachViewFromParent(view)
            attachViewToParent(view, 0, view.layoutParams)
            moved()
        } else {
            addScreen(view, 0)
        }
    }

    /** [view] goes over everything: the FAB or its menu. */
    fun addOverAll(view: View, params: LayoutParams) {
        (view.parent as? ViewGroup)?.removeView(view)
        addView(view, params)
    }

    /** [view] leaves the container, alive. */
    fun take(view: View) {
        screenViews -= view
        if (holds(view)) removeView(view)
    }

    // A screen joins the container: with the stack's last insets first, when it missed some while it was out of it.
    private fun addScreen(view: View, index: Int) {
        (view.parent as? ViewGroup)?.removeView(view)
        val params = (view.layoutParams as? MarginLayoutParams)?.let(::LayoutParams)
            ?: LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        params.width = LayoutParams.MATCH_PARENT
        params.height = LayoutParams.MATCH_PARENT
        screenViews += view
        addView(view, index, params)
    }

    /**
     * Gives [view] the stack's last window insets before it joins the container, when they differ from [kept], the
     * last it got: it lays out once, in its place. Never asks the window to send them again.
     */
    fun giveInsets(view: View, kept: WindowInsetsCompat?) {
        val last = lastInsets ?: return
        if (kept == last) return
        ViewCompat.dispatchApplyWindowInsets(view, last)
    }

    private fun moved() {
        requestLayout()
        invalidate()
    }

    // The edges of the screens in a transition: drawn beside each, under it, where it moves and as transparent as it.
    private val edges = HashMap<View, Edge>()

    private class Edge(val picture: Drawable, val width: Int, val atStart: Boolean)

    /**
     * [view] extends its start edge ([atStart]) or its end edge by [width] pixels while it moves, as the system's
     * transitions between activities extend a window: its background fills the room it moves away from, so the screen
     * under it never shows at its edge. [dropEdge] ends it. Each frame of the move calls [edgesMoved].
     */
    fun extendEdge(view: View, atStart: Boolean, width: Int) {
        val left = atStart != (layoutDirection == LAYOUT_DIRECTION_RTL)
        val picture = ScreenEdge.pictureOf(view) ?: return
        edges[view] = Edge(picture, width, left)
        invalidate()
    }

    /** [view] moves no more: its edge goes. */
    fun dropEdge(view: View) {
        if (edges.remove(view) != null) invalidate()
    }

    /** A screen with an extended edge moved: the edges are drawn again where the screens are now. */
    fun edgesMoved() {
        if (edges.isNotEmpty()) invalidate()
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        edges[child]?.let { drawEdge(canvas, child, it) }
        return super.drawChild(canvas, child, drawingTime)
    }

    // At the child's place to the fraction of a pixel, as its own translation draws it, and one pixel under it: no seam
    // of the screen below shows between them.
    private fun drawEdge(canvas: Canvas, child: View, edge: Edge) {
        if (child.visibility != VISIBLE || child.alpha <= 0f) return
        val x = if (edge.atStart) child.left + child.translationX - edge.width else child.right + child.translationX - 1f
        val saved = canvas.save()
        canvas.translate(x, child.top + child.translationY)
        edge.picture.setBounds(0, 0, edge.width + 1, child.height)
        edge.picture.alpha = (child.alpha * 255).toInt()
        edge.picture.draw(canvas)
        canvas.restoreToCount(saved)
    }
}

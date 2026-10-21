package dev.apexnative.navigation.views.element

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/**
 * The layer of a transition's elements: full size over the screens' common parent (a stack's view; for a modal, between
 * the modals layer and the overlays layer), there only while the transition runs. Each view in it lies where its layout
 * params say — `leftMargin`, `topMargin` and its size, in the layer's pixels — and nothing re-places it: Fabric's views
 * carry no margins of their own. Nothing in it is clipped by the parents it came from, it takes no touches,
 * accessibility doesn't see it. Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class TransitionLayer(context: Context) : ViewGroup(context) {
    // The newest frame Fabric gave a view while it was here (a commit lays it out in its parent's coordinates).
    private val reactFrames = HashMap<View, Rect>()

    init {
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** The layer's top left corner on the screen, in pixels: what the views in it are placed from. */
    val originOnScreen: Point
        get() {
            val location = IntArray(2)
            getLocationOnScreen(location)
            return Point(location[0], location[1])
        }

    /**
     * Joins [parent] at [index] (its top without one), laid out at once at the parent's size: its views are in their
     * places on the frame the transition starts.
     */
    fun attachTo(parent: ViewGroup, index: Int = parent.childCount) {
        // The parent turns these into its own kind of params.
        parent.addView(this, index.coerceIn(0, parent.childCount), LayoutParams(MATCH, MATCH))
        measure(
            MeasureSpec.makeMeasureSpec(parent.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(parent.height, MeasureSpec.EXACTLY),
        )
        layout(0, 0, parent.width, parent.height)
    }

    /** [view] lies at [left], [top] (the layer's pixels) at the size [width] × [height], laid out at once. */
    fun put(view: View, left: Int, top: Int, width: Int, height: Int) {
        val params = MarginLayoutParams(width, height).apply {
            leftMargin = left
            topMargin = top
        }
        addView(view, params)
        layOut(view, left, top, width, height)
    }

    /** [view], in the layer, lies at [left], [top] at the size [width] × [height] from now on. */
    fun place(view: View, left: Int, top: Int, width: Int, height: Int) {
        noteReactFrame(view)
        (view.layoutParams as? MarginLayoutParams)?.let {
            it.leftMargin = left
            it.topMargin = top
            it.width = width
            it.height = height
        }
        layOut(view, left, top, width, height)
    }

    private fun layOut(view: View, left: Int, top: Int, width: Int, height: Int) {
        view.measure(
            MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height.coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
        view.layout(left, top, left + width, top + height)
    }

    /**
     * The newest frame React gave [view] while it was in the layer, in its parent's coordinates; none when React
     * didn't lay it out meanwhile. A frame other than the one the layer gave it can only be React's.
     */
    fun frameFromReact(view: View): Rect? {
        noteReactFrame(view)
        return reactFrames.remove(view)
    }

    // The view's frame isn't where the layer put it: React laid it out since.
    private fun noteReactFrame(view: View) {
        val params = view.layoutParams as? MarginLayoutParams ?: return
        val placed = view.left == params.leftMargin && view.top == params.topMargin &&
            view.width == params.width && view.height == params.height
        if (!placed) reactFrames[view] = Rect(view.left, view.top, view.right, view.bottom)
    }

    /** The layer leaves its parent. */
    fun detach() {
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(params.width.coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(params.height.coerceAtLeast(0), MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as? MarginLayoutParams ?: continue
            noteReactFrame(child)
            child.layout(
                params.leftMargin,
                params.topMargin,
                params.leftMargin + child.measuredWidth,
                params.topMargin + child.measuredHeight,
            )
        }
    }

    override fun checkLayoutParams(params: LayoutParams?): Boolean = params is MarginLayoutParams

    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(0, 0)

    override fun generateLayoutParams(params: LayoutParams?): LayoutParams = MarginLayoutParams(params)

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false

    override fun shouldDelayChildPressedState(): Boolean = false

    companion object {
        private const val MATCH = LayoutParams.MATCH_PARENT

        /** Whether [view] is in a transition's layer now (an element of another transition). */
        fun holds(view: View): Boolean {
            var parent = view.parent
            while (parent != null) {
                if (parent is TransitionLayer) return true
                parent = parent.parent
            }
            return false
        }
    }
}

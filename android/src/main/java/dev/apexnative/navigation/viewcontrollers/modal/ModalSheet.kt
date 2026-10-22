package dev.apexnative.navigation.viewcontrollers.modal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import dev.apexnative.navigation.options.SheetDetent
import dev.apexnative.navigation.options.SheetOptions
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.dp
import kotlin.math.roundToInt

/**
 * A modal shown as a Material bottom sheet (`pageSheet`, `formSheet`, `popover`, or `modal.sheet` without a style): the
 * modal's view in a container with a [BottomSheetBehavior], in the modals' CoordinatorLayout, over a scrim. It stops at
 * the detents of its options — `medium` at half the height, `large` at the full height under the status bar, a number
 * at that height in dp — and the user drags it between them. In a window wider than [MAX_WIDTH_DP] it stands in the
 * middle at that width. A drag down past the smallest one closes it, unless `swipeToDismiss` is false: then the sheet
 * stays, and the try is told. The screen under it stays in place.
 */
internal class ModalSheet(
    private val layout: CoordinatorLayout,
    private val content: View,
    private val events: Events,
) {
    interface Events {
        /** The user dragged the sheet to another detent. */
        fun onDetentChanged(detent: SheetDetent)

        /** The user closed the sheet: dragged it down or tapped the scrim. The modal goes. */
        fun onClosedByUser()

        /** The user tried to close a sheet that keeps them (`swipeToDismiss: false`). */
        fun onCloseAttempted()
    }

    // Material 3 views: the app's Material 3 theme, or the navigation's neutral one.
    private val context: Context = MaterialTheme.context(layout.context)
    private val scrim = View(context)
    private val container = SheetContainer(context)
    private val grabber = BottomSheetDragHandleView(context)
    private val behavior = SheetBehavior()
    private var options = SheetOptions()
    private var swipeToDismiss = true
    private var detents: List<SheetDetent> = listOf(SheetDetent.Large)
    private var selected: SheetDetent? = null
    private var topGap = 0
    private var dragged = false
    private var shown: (() -> Unit)? = null
    private var closing: (() -> Unit)? = null

    // A list inside the sheet scrolls with it, as the Material behavior expects: up at half height the sheet goes up
    // first, down at the top of the list the sheet comes down. The behavior follows views that scroll nested.
    private val scrollViews = ViewTreeObserver.OnGlobalLayoutListener {
        if (behavior.dragAnywhere) enableNestedScrolling(container)
    }

    /** The sheet is down, out of sight: the modal can go at once. */
    val isClosed: Boolean
        get() = behavior.state == BottomSheetBehavior.STATE_HIDDEN

    init {
        scrim.setBackgroundColor(Color.BLACK)
        scrim.alpha = 0f
        // A tap on the dimmed screen closes the sheet, as on iOS; a drag on it does nothing.
        val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = dims()
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                closeByUser()
                return true
            }
        })
        scrim.setOnTouchListener { _, event -> taps.onTouchEvent(event) }
        container.clipToOutline = true
        container.outlineProvider = object : ViewOutlineProvider() {
            // The bottom corners are under the screen's bottom: only the top ones round.
            override fun getOutline(view: View, outline: Outline) {
                val radius = cornerRadius().toFloat()
                outline.setRoundRect(0, 0, view.width, view.height + radius.roundToInt(), radius)
            }
        }
        // The sheet is under the status bar: its screens leave no room for it at their top. In the middle of a wide
        // window it reaches no side, and keeps room only for the part of a side bar that is over it.
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(types)
            val laidOut = container.width > 0
            val left = if (laidOut) (bars.left - container.left).coerceAtLeast(0) else bars.left
            val right = if (laidOut) (bars.right - (layout.width - container.right)).coerceAtLeast(0) else bars.right
            WindowInsetsCompat.Builder(insets).setInsets(types, Insets.of(left, 0, right, bars.bottom)).build()
        }
        // Its sides move with the window's width (a rotation, a resize): the room for the side bars follows.
        container.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (left != oldLeft || right != oldRight) container.post { ViewCompat.requestApplyInsets(container) }
        }
        container.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        container.addView(grabber, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        behavior.isHideable = true
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.halfExpandedRatio = 0.5f
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) = onState(newState)
            override fun onSlide(sheet: View, slideOffset: Float) = updateScrim()
        })
        container.viewTreeObserver.addOnGlobalLayoutListener(scrollViews)
    }

    /** Slides the sheet up to its selected detent; [onShown] once it is there. */
    fun show(sheetOptions: SheetOptions, swipeToDismiss: Boolean, onShown: () -> Unit) {
        topGap = ViewCompat.getRootWindowInsets(layout)?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
        layout.addView(scrim, CoordinatorLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        // As tall as its largest detent (maxHeight), not moved down by a margin: the behavior counts its states from
        // where the sheet is laid out.
        // In a wide window it stands in the middle, as wide as a Material modal sheet at most.
        behavior.maxWidth = MAX_WIDTH_DP.dp
        val params = CoordinatorLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.behavior = behavior
        layout.addView(container, params)
        shown = onShown
        update(sheetOptions, swipeToDismiss, merge = null)
        selected = sheetOptions.selectedDetent?.takeIf { it in detents }
        // Laid out hidden first, so it slides up from the bottom.
        container.post { if (closing == null) behavior.state = stateOf(selected ?: detents.first()) }
    }

    /**
     * New resolved options: the detents and the look. The sheet moves only as [SheetStops] says, after a `mergeOptions`
     * of [merge]; without one (the shown screen changed, the show) it stays where it is.
     */
    fun update(sheetOptions: SheetOptions, swipeToDismiss: Boolean, merge: SheetOptions?) {
        options = sheetOptions
        this.swipeToDismiss = swipeToDismiss
        detents = sheetOptions.detents?.takeIf { it.isNotEmpty() }?.sortedBy { heightOf(it) } ?: listOf(SheetDetent.Large)
        grabber.visibility = if (sheetOptions.grabber.isTrue) View.VISIBLE else View.GONE
        behavior.dragAnywhere = sheetOptions.expandsOnScroll.get(true)
        container.invalidateOutline()
        configure()
        if (merge == null || closing != null) return
        val settled = detentOf(behavior.state)
        if (settled != null) behavior.isHideable = swipeToDismiss && settled == detents.first()
        val target = SheetStops.target(settled, detents, merge.selectedDetent, sheetOptions.selectedDetent)
        if (target != null) {
            selected = target
            behavior.state = stateOf(target)
        }
        updateScrim()
    }

    /** Slides the sheet down; [onClosed] once it is out of sight. */
    fun close(onClosed: () -> Unit) {
        closing = onClosed
        if (isClosed) {
            finishClosing()
            return
        }
        behavior.isHideable = true
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    /** Takes the sheet's views away; the modal's view is the caller's. */
    fun remove() {
        container.viewTreeObserver.removeOnGlobalLayoutListener(scrollViews)
        container.removeView(content)
        layout.removeView(container)
        layout.removeView(scrim)
    }

    /** The sheet's `testID`: its container, and `<id>.grabber`, `<id>.scrim`; none takes them away. */
    fun setTestIds(testId: String?) {
        TestIds.apply(container, testId)
        TestIds.applyOver(grabber, testId?.let { "$it.grabber" })
        TestIds.apply(scrim, testId?.let { "$it.scrim" })
    }

    private fun closeByUser() {
        if (!swipeToDismiss) {
            events.onCloseAttempted()
            return
        }
        behavior.isHideable = true
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    // How the Material behavior stops at the detents: with half and full height both, at its three states (the
    // smallest at the collapsed one); otherwise at the collapsed state (the smaller detent) and the expanded one, which
    // is only as tall as the largest detent.
    private fun configure() {
        val height = detents.filterIsInstance<SheetDetent.Height>().firstOrNull()
        val halfAndFull = SheetDetent.Medium in detents && SheetDetent.Large in detents
        behavior.isFitToContents = !halfAndFull
        behavior.expandedOffset = topGap
        behavior.maxHeight = heightOf(detents.last())
        val collapsed = if (halfAndFull) height else detents.takeIf { it.size > 1 }?.first()
        behavior.skipCollapsed = collapsed == null
        // Without a detent of its own the collapsed state is the smallest detent: a sheet that isn't hideable settles
        // there, not at Material's peek height.
        behavior.peekHeight = heightOf(collapsed ?: detents.first())
    }

    private fun stateOf(detent: SheetDetent): Int = when {
        detent == detents.last() -> BottomSheetBehavior.STATE_EXPANDED
        detent == SheetDetent.Medium && !behavior.isFitToContents -> BottomSheetBehavior.STATE_HALF_EXPANDED
        else -> BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun detentOf(state: Int): SheetDetent? = when (state) {
        BottomSheetBehavior.STATE_EXPANDED -> detents.last()
        BottomSheetBehavior.STATE_HALF_EXPANDED -> SheetDetent.Medium
        BottomSheetBehavior.STATE_COLLAPSED -> detents.first()
        else -> null
    }

    /** The height of the sheet at the detent, in px. */
    private fun heightOf(detent: SheetDetent): Int {
        val full = (if (layout.height > 0) layout.height else layout.rootView.height) - topGap
        return when (detent) {
            SheetDetent.Large -> full
            SheetDetent.Medium -> (full + topGap) / 2
            is SheetDetent.Height -> detent.dp.toFloat().dp.coerceAtMost(full)
        }
    }

    private fun onState(state: Int) {
        when (state) {
            BottomSheetBehavior.STATE_DRAGGING -> dragged = true
            BottomSheetBehavior.STATE_HIDDEN -> {
                when {
                    closing != null -> finishClosing()
                    else -> {
                        // Dragged down or the scrim tapped: the modal goes.
                        closing = {}
                        events.onClosedByUser()
                    }
                }
            }
            BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_HALF_EXPANDED, BottomSheetBehavior.STATE_COLLAPSED -> {
                if (closing != null) return
                val detent = detentOf(state)
                // A drag down closes the sheet from its smallest detent only; from a larger one it stops at the next.
                behavior.isHideable = swipeToDismiss && detent == detents.first()
                shown?.let {
                    shown = null
                    it()
                }
                if (dragged && detent != null && detent != selected) {
                    selected = detent
                    events.onDetentChanged(detent)
                }
                dragged = false
            }
        }
        updateScrim()
    }

    private fun finishClosing() {
        val done = closing ?: return
        closing = {}
        done()
    }

    // The screen under the sheet dims as the sheet goes up past `largestUndimmedDetent` (from the bottom without
    // one), to the dim of a Material modal sheet at the next detent; while it isn't dimmed, it takes touches.
    private fun updateScrim() {
        val height = layout.height - container.top
        val undimmed = options.largestUndimmedDetent?.takeIf { it in detents }
        val from = undimmed?.let { heightOf(it) } ?: 0
        val to = detents.firstOrNull { heightOf(it) > from }?.let { heightOf(it) } ?: return run { setDim(0f) }
        setDim(((height - from).toFloat() / (to - from)).coerceIn(0f, 1f))
    }

    private fun setDim(fraction: Float) {
        scrim.alpha = fraction * SCRIM_ALPHA
    }

    private fun dims() = scrim.alpha > 0f

    private fun cornerRadius(): Int = if (options.cornerRadius.hasValue()) options.cornerRadius.get().toFloat().dp else 28.dp

    private fun enableNestedScrolling(view: View) {
        if (view is ScrollView && !view.isNestedScrollingEnabled) view.isNestedScrollingEnabled = true
        if (view is ViewGroup) for (i in 0 until view.childCount) enableNestedScrolling(view.getChildAt(i))
    }

    /** With `expandsOnScroll: false` only the top of the sheet, around the grabber, drags it; the rest scrolls. */
    private inner class SheetBehavior : BottomSheetBehavior<FrameLayout>() {
        var dragAnywhere = true
        private var ignoring = false
        private var downY = 0f
        private var downAtSmallest = false

        // A sheet that keeps the user doesn't go down (it isn't hideable): a drag down from its smallest detent is a
        // try to close it, told when the finger lifts.
        private fun watchCloseAttempt(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    downAtSmallest = detentOf(state) == detents.first()
                }
                MotionEvent.ACTION_UP -> {
                    if (!swipeToDismiss && closing == null && downAtSmallest && event.rawY - downY > ATTEMPT_DISTANCE_DP.dp) {
                        events.onCloseAttempted()
                    }
                }
            }
        }

        override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: FrameLayout, event: MotionEvent): Boolean {
            if (ignores(child, event)) return false
            return super.onInterceptTouchEvent(parent, child, event)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(parent: CoordinatorLayout, child: FrameLayout, event: MotionEvent): Boolean {
            if (ignores(child, event)) return false
            return super.onTouchEvent(parent, child, event)
        }

        override fun onStartNestedScroll(
            coordinatorLayout: CoordinatorLayout, child: FrameLayout, directTargetChild: View, target: View, axes: Int, type: Int
        ): Boolean = dragAnywhere && super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type)

        private fun ignores(child: View, event: MotionEvent): Boolean {
            watchCloseAttempt(event)
            if (dragAnywhere) return false
            if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoring = event.y - child.top > GRAB_AREA_DP.dp
            val ignore = ignoring
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) ignoring = false
            return ignore
        }
    }

    companion object {
        /** The widest a sheet is, in dp: the Material modal bottom sheet's `maxWidth`. */
        const val MAX_WIDTH_DP = 640

        /** The dim of a Material modal bottom sheet's scrim. */
        private const val SCRIM_ALPHA = 0.32f
        private const val GRAB_AREA_DP = 48
        private const val ATTEMPT_DISTANCE_DP = 48
    }
}

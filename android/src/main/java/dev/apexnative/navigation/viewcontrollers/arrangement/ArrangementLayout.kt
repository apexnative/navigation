package dev.apexnative.navigation.viewcontrollers.arrangement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.utils.WindowFolds
import dev.apexnative.navigation.viewcontrollers.modal.SheetContainer
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener
import dev.apexnative.navigation.views.component.ComponentLayout

/**
 * The view of a pair of screens ([ArrangementController]), React or native: lays them out as [ArrangementPlanner]
 * decides from its size, its direction, the folds and the size of the window; keeps the secondary out of the window
 * while only the primary shows; and gives each screen the window insets of the edges of the pair it touches.
 */
@SuppressLint("ViewConstructor")
open class ArrangementLayout internal constructor(
    context: Context,
    private val definition: ArrangementDefinition,
    private val primary: ViewGroup,
    private val secondary: ViewGroup,
    private val folds: () -> List<WindowFolds.Fold>,
    private val largeWindow: () -> Boolean,
    private val onArranged: (ArrangementPlan) -> Unit,
) : ViewGroup(context) {
    /** How the screens lie, as last laid out; null before the first layout. */
    internal var plan: ArrangementPlan? = null
        private set

    // The plan the screens were measured and given their insets for.
    private var measured: ArrangementPlan? = null
    // The window insets the pair was last given, as its parent gives them (a pane of a split view keeps only its own
    // sides); a pair given them before it is attached (a pop) has no root insets yet.
    private var lastInsets: WindowInsets? = null

    init {
        // The primary last: drawn over the secondary, and the first to get a touch.
        addView(secondary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(primary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        // Where the layout put the pair last: a fold is placed against it before the pair's own place is final.
        prepare(planFor(width, height, left, top))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // In its final place a fold can cross the pair where the measure didn't see it: a pair laid out the first time.
        val next = planFor(r - l, b - t, l, t)
        if (next != measured) prepare(next)
        placeSecondary(next.secondary != null)
        layoutScreen(primary, next.primary)
        next.secondary?.let { layoutScreen(secondary, it) }
        // One over the other, the covered screen isn't read out.
        secondary.importantForAccessibility = if (next.layout == ArrangementPlan.Layout.OVERLAY) {
            IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        if (next != plan) {
            plan = next
            onArranged(next)
        }
    }

    override fun shouldDelayChildPressedState() = false

    // The screens take the insets of the edges of the pair they touch: side by side, the bar of the left screen has no
    // room for the navigation bar on the right; one above the other, the upper screen keeps none for the one at the
    // bottom. Not applied to this view itself: the controller's listener leaves them as they are.
    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        lastInsets = insets
        val plan = measured ?: return super.dispatchApplyWindowInsets(insets)
        val all = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        dispatchTo(primary, all, plan.primary, insets)
        plan.secondary?.let { dispatchTo(secondary, all, it, insets) }
        return insets
    }

    private fun dispatchTo(screen: View, all: WindowInsetsCompat, frame: Rect, insets: WindowInsets) {
        val sides = keepSides(
            all,
            left = frame.left <= 0,
            top = frame.top <= 0,
            right = frame.right >= measuredWidth,
            bottom = frame.bottom >= measuredHeight,
        )
        screen.dispatchApplyWindowInsets(sides.toWindowInsets() ?: insets)
    }

    private fun keepSides(
        all: WindowInsetsCompat,
        left: Boolean,
        top: Boolean,
        right: Boolean,
        bottom: Boolean,
    ): WindowInsetsCompat {
        if (left && top && right && bottom) return all
        val builder = WindowInsetsCompat.Builder(all)
        for (type in SIDE_TYPES) {
            val sides = all.getInsets(type)
            builder.setInsets(
                type,
                Insets.of(
                    if (left) sides.left else 0,
                    if (top) sides.top else 0,
                    if (right) sides.right else 0,
                    if (bottom) sides.bottom else 0,
                ),
            )
        }
        return builder.build()
    }

    // The screens' insets for the plan, then their sizes in it: the insets set their padding, which the measure counts.
    private fun prepare(next: ArrangementPlan) {
        val sidesChanged = measured?.let { it.primary != next.primary || it.secondary != next.secondary } ?: true
        measured = next
        if (sidesChanged) (lastInsets ?: rootWindowInsets)?.let { dispatchApplyWindowInsets(it) }
        measureScreen(primary, next.primary)
        next.secondary?.let { measureScreen(secondary, it) }
    }

    private fun layoutScreen(screen: View, frame: Rect) =
        screen.layout(frame.left, frame.top, frame.right, frame.bottom)

    private fun measureScreen(screen: View, frame: Rect) = screen.measure(
        MeasureSpec.makeMeasureSpec(frame.width(), MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(frame.height(), MeasureSpec.EXACTLY),
    )

    // Out of the window while only the primary shows, as on iOS: the screen disappears, and comes back under the
    // primary when the pair has room for it again.
    private fun placeSecondary(shown: Boolean) {
        val attached = secondary.parent === this
        if (shown == attached) return
        if (shown) {
            addViewInLayout(secondary, 0, secondary.layoutParams ?: generateDefaultLayoutParams(), true)
        } else {
            removeViewInLayout(secondary)
        }
    }

    private fun planFor(width: Int, height: Int, x: Int, y: Int): ArrangementPlan {
        val origin = parentOriginInWindow().apply { offset(x, y) }
        val inWindow = Rect(origin.x, origin.y, origin.x + width, origin.y + height)
        val fold = folds().firstNotNullOfOrNull { it.across(inWindow) }
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        return ArrangementPlanner.plan(definition, width, height, rtl, fold, largeWindow())
    }

    // Where the layout puts the parent in the window, without the moves of animations: a pair sliding in on a push is
    // placed against the fold where it comes to rest. A sheet counts where it rests, not where a drag moved it.
    private fun parentOriginInWindow(): Point {
        var x = 0
        var y = 0
        var current = parent as? View
        while (current != null) {
            x += current.left
            y += current.top
            if (current is SheetContainer) y -= current.shift
            val next = current.parent as? View
            if (next != null) {
                x -= next.scrollX
                y -= next.scrollY
            }
            current = next
        }
        return Point(x, y)
    }

    private companion object {
        val SIDE_TYPES = intArrayOf(
            WindowInsetsCompat.Type.statusBars(),
            WindowInsetsCompat.Type.navigationBars(),
            WindowInsetsCompat.Type.captionBar(),
            WindowInsetsCompat.Type.displayCutout(),
            WindowInsetsCompat.Type.ime(),
            WindowInsetsCompat.Type.systemGestures(),
            WindowInsetsCompat.Type.mandatorySystemGestures(),
            WindowInsetsCompat.Type.tappableElement(),
        )
    }
}

/**
 * The view of a pair whose primary is a React screen: for the stack around it stands for the primary, so the bar
 * follows the primary's scroll (`topBar.hideOnScroll`). A native primary has no scroll for the bar to follow.
 */
@SuppressLint("ViewConstructor")
internal class ReactArrangementLayout(
    context: Context,
    definition: ArrangementDefinition,
    private val primary: ComponentLayout,
    secondary: ViewGroup,
    folds: () -> List<WindowFolds.Fold>,
    largeWindow: () -> Boolean,
    onArranged: (ArrangementPlan) -> Unit,
) : ArrangementLayout(context, definition, primary, secondary, folds, largeWindow, onArranged), IReactView {
    override fun isReady(): Boolean = primary.isReady

    override fun asView(): ViewGroup = this

    override fun sendOnNavigationButtonPressed(buttonId: String) = primary.sendOnNavigationButtonPressed(buttonId)

    override fun getScrollEventListener(): ScrollEventListener = primary.scrollEventListener

    override fun dispatchTouchEventToJs(event: MotionEvent) = primary.dispatchTouchEventToJs(event)

    override fun isRendered(): Boolean = primary.isRendered

    // The screens' controllers destroy their views.
    override fun destroy() = Unit
}

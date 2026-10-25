package dev.apexnative.navigation.views.bottomtabs

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RestrictTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.viewcontrollers.bottomtabs.CustomTabRowGeometry

/**
 * The view of bottom tabs: the view of each tab made so far fills it, the selected one visible and the others invisible
 * but laid out, so a switch shows a tab at once and its screens render; the bar's [container] lies over them at the
 * bottom, the module's bar ([bar], `R.id.bottomTabs`) in it. The container's distance from the bottom ([barOffset]:
 * `bottomMargin`, and the navigation bar under a compact bar) is this view's own inset, not a layout parameter a root
 * may ignore. The custom [row], when the tabs have one, lies right after the container and over the bar. The window
 * insets go on to every child as they came. Main thread only.
 */
@SuppressLint("ViewConstructor")
@RestrictTo(RestrictTo.Scope.LIBRARY)
class BottomTabsLayout(context: Context) : FrameLayout(context) {
    /** The module's bar. */
    val bar = BottomTabs(context)

    /** What draws around the bar, on the theme of the navigation's Material views: its default colors come from it. */
    internal val container = BottomTabsContainer(MaterialTheme.context(context), bar)

    /** The navigation bar's height at the bottom of the window (`navigationBars`), from the last window insets. */
    internal var navigationBarInset = 0
        private set

    /** The window insets reached this view: the bar's room and the screens' room may have changed. */
    internal var onInsets: (() -> Unit)? = null

    /**
     * The custom row of the tabs, when every tab draws its cell (`bottomTab.component`): a child right after the
     * container, laid out over the bar, going with the container.
     */
    internal var row: CustomTabRow? = null
        set(value) {
            if (field === value) return
            field?.let {
                removeView(it)
                it.container = null
            }
            field = value
            if (value == null) return
            (value.parent as? ViewGroup)?.removeView(value)
            value.container = container
            value.place = null
            // Its frame is absolute: the layout below puts it in its parameters.
            addView(value, indexOfChild(container) + 1, LayoutParams(0, 0, Gravity.TOP or Gravity.LEFT))
        }

    /** The container's distance from the bottom, in pixels. */
    internal var barOffset = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        // The shadow of the container is drawn over the screens, out of its own bounds.
        clipChildren = false
        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (bottom != navigationBarInset) {
                navigationBarInset = bottom
                onInsets?.invoke()
            }
            insets
        }
    }

    /**
     * How far the container goes down to leave the screen whole: from the top of what the bar shows — the container, or
     * the custom row that rises above it — to this view's bottom, in pixels.
     */
    internal val hideDistance: Int
        get() {
            val rowTop = row?.takeIf { it.visibility != View.GONE }?.top ?: container.top
            return (height - minOf(container.top, rowTop)).coerceAtLeast(0)
        }

    /** [view] of a tab joins under the bar, invisible until its tab shows. */
    internal fun addTab(view: View) {
        if (view.parent === this) return
        (view.parent as? ViewGroup)?.removeView(view)
        view.visibility = View.INVISIBLE
        addView(view, indexOfChild(container), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** [view] of a tab holds this view: it is attached. */
    internal fun holdsTab(view: View?): Boolean = view != null && view.parent === this

    /** A compact bar is as wide as its tabs and floats in the middle; a stretched one goes the whole width. */
    internal fun setCompact(compact: Boolean) {
        bar.compact = compact
        val params = container.layoutParams as LayoutParams
        val width = if (compact) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
        val gravity = if (compact) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.BOTTOM
        if (params.width == width && params.gravity == gravity) return
        params.width = width
        params.gravity = gravity
        container.layoutParams = params
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (barOffset != 0) container.offsetTopAndBottom(-barOffset)
        row?.let(::placeRow)
    }

    // The row over the bar where the bar rests (its container's motion the row takes before each frame), when its place
    // changed: laid out there at once, and kept in its parameters, so the next passes measure it and put it there the
    // same way. A hidden bar keeps the row where it was.
    private fun placeRow(row: CustomTabRow) {
        if (container.visibility == View.GONE || bar.width == 0) return
        val barLeft = container.left + bar.left
        val barTop = container.top + bar.top
        val place = row.placeOver(
            barLeft = barLeft,
            barRight = barLeft + bar.width,
            barBottom = barTop + bar.height,
            barHeight = bar.height,
            tabsHeight = height,
            navigationBar = navigationBarInset,
        )
        if (!CustomTabRowGeometry.needsPlacing(row.place, place)) return
        row.place = place
        val params = row.layoutParams as LayoutParams
        params.width = place.right - place.left
        params.height = place.bottom - place.top
        params.leftMargin = place.left
        params.topMargin = place.top
        row.measure(
            MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY),
        )
        row.layout(place.left, place.top, place.right, place.bottom)
    }
}

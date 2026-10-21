package dev.apexnative.navigation.views.bottomtabs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.apexnative.navigation.customrow.BottomTabsCustomRowOptions
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.viewcontrollers.bottomtabs.CustomTabRowGeometry

/**
 * The custom row of bottom tabs on Android (`bottomTabs.customRow`): a container over the bar, in the window's content,
 * that holds the tabs' cells in one row of equal widths in tab order. It owns its place, its look, its visibility and
 * the touches: each cell's place takes them, the React elements in the cell don't, and a tap or a long press of a place
 * goes to [onPress] or [onLongPress] with the tab's index. Where it lies is the placer's to say (`CustomTabRowPlacer`);
 * what it looks like its tabs'. Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class CustomTabRow(context: Context) : FrameLayout(context) {
    /** How the row looks, in pixels and ARGB. */
    data class Look(val background: Int, val cornerRadius: Float, val elevation: Float, val testId: String?)

    private val cells = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val places = ArrayList<FrameLayout>()
    private val shape = GradientDrawable()
    private var look: Look? = null
    // The part of the row's height below its visible part: `customRow.bottomMargin`, in pixels.
    private var gap = 0

    /** A cell's place was tapped. */
    var onPress: ((Int) -> Unit)? = null

    /** A cell's place was long-pressed. */
    var onLongPress: ((Int) -> Unit)? = null

    /** `bottomTabs.customRow` of the tabs, resolved: the placer reads its height and margins. */
    var options = BottomTabsCustomRowOptions()

    /**
     * Where the placer put the row last, in its parent; none before it did. The frame holds `bottomMargin` below the
     * content: the row draws, clips, casts its shadow, lays its cells and takes touches only over the content, and the
     * margin below lets the touches through to what is under it.
     */
    var place: CustomTabRowGeometry.Place? = null
        set(value) {
            field = value
            val next = value?.let(CustomTabRowGeometry::gap) ?: 0
            if (next != gap) applyGap(next)
        }

    /**
     * Whether the tabs of the row wait to show (): the row is out of accessibility and takes no touches then.
     */
    var hiddenFromAccessibility: () -> Boolean = { false }

    init {
        background = shape
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val bottom = (view.height - gap).coerceAtLeast(0)
                outline.setRoundRect(0, 0, view.width, bottom, look?.cornerRadius ?: 0f)
                // As the background's own outline: a clear row casts no shadow.
                outline.alpha = Color.alpha(look?.background ?: 0) / OPAQUE
            }
        }
        clipToOutline = true
        addView(cells, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // The background, the outline and the cells end [next] pixels above the row's bottom.
    private fun applyGap(next: Int) {
        gap = next
        background = if (next > 0) InsetDrawable(shape, 0, 0, 0, next) else shape
        val params = cells.layoutParams as LayoutParams
        params.bottomMargin = next
        cells.layoutParams = params
        invalidateOutline()
    }

    /**
     * The row holds [views], each in a place of its own carrying its tab's testID ([testIds]): the same views again
     * when the row joins the window again; a view leaves any parent it had first.
     */
    fun hold(views: List<CustomBottomTabItemView>, testIds: List<String?>) {
        cells.removeAllViews()
        places.clear()
        views.forEachIndexed { index, view ->
            (view.parent as? ViewGroup)?.removeView(view)
            val cell = CellPlace(context, index)
            cell.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            TestIds.apply(cell, testIds.getOrNull(index))
            places += cell
            cells.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /** The testID of the cell's place of the tab at [index]. */
    fun setTestId(index: Int, testId: String?) {
        places.getOrNull(index)?.let { TestIds.apply(it, testId) }
    }

    // While its tabs wait to show, the row is transparent and takes no touches: they reach the root the user sees. A
    // touch that starts in the margin below the visible row isn't the row's either.
    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (hiddenFromAccessibility()) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y >= height - gap) return false
        return super.dispatchTouchEvent(event)
    }

    /** Whether the row shows its cells: it hides with the bar. */
    fun showWith(bar: View) {
        val wanted = if (bar.visibility != View.GONE) View.VISIBLE else View.GONE
        if (visibility != wanted) visibility = wanted
    }

    /** Draws [look]; nothing changes when it is the one drawn. */
    fun apply(look: Look) {
        if (look == this.look) return
        val testIdChanged = look.testId != this.look?.testId
        this.look = look
        shape.setColor(look.background)
        shape.cornerRadius = look.cornerRadius
        invalidateOutline()
        elevation = look.elevation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineSpotShadowColor = SPOT_SHADOW
            outlineAmbientShadowColor = AMBIENT_SHADOW
        }
        if (testIdChanged) TestIds.apply(this, look.testId)
        invalidate()
    }

    // A cell's place: the touches of the cell are its own, never the React elements' inside.
    @SuppressLint("ViewConstructor")
    private inner class CellPlace(context: Context, private val index: Int) : FrameLayout(context) {
        init {
            isClickable = true
            isLongClickable = true
            setOnClickListener { onPress?.invoke(index) }
            setOnLongClickListener {
                onLongPress?.invoke(index)
                true
            }
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true
    }

    private companion object {
        // A light shadow: black with these alphas.
        const val SPOT_SHADOW = 0x4C000000
        const val AMBIENT_SHADOW = 0x1E000000
        const val OPAQUE = 255f
    }
}

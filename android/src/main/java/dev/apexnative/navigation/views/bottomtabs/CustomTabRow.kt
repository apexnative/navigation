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
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.apexnative.navigation.customrow.BottomTabsCustomRowOptions
import dev.apexnative.navigation.utils.TestIds
import dev.apexnative.navigation.utils.UiUtils
import dev.apexnative.navigation.viewcontrollers.bottomtabs.CustomTabRowGeometry

/**
 * The custom row of bottom tabs on Android (`bottomTabs.customRow`): a container over the bar, a child of the tabs'
 * view right after the bar's container, that holds the tabs' cells in one row of equal widths in tab order. It owns its
 * look, its visibility and the touches: each cell's place takes them, the React elements in the cell don't, and a tap
 * or a long press of a place goes to [onPress] or [onLongPress] with the tab's index. The tabs' view lays it out over
 * the bar ([placeOver]); before each frame it goes with the bar's [container]. What it looks like is its tabs'. Main
 * thread only.
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

    /** `bottomTabs.customRow` of the tabs, resolved: its height and margins say where the row lies. */
    var options = BottomTabsCustomRowOptions()

    /**
     * The bar's container the row goes with: its motion (a ride, a slide, the hide on scroll), its alpha, its height
     * over the screens and whether it shows. The tabs' view sets it as the row joins it.
     */
    var container: View? = null

    // Before each frame of the window the row is in: it goes with the container.
    private val following = ViewTreeObserver.OnPreDrawListener {
        container?.let(::follow)
        true
    }

    /**
     * Where the tabs' view put the row last, in it; none before it did. The frame holds `bottomMargin` below the
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

    /**
     * Where the row lies over the bar whose edges are [barLeft], [barRight] and [barBottom] and whose height is
     * [barHeight], in the tabs' view of [tabsHeight] with the navigation bar's [navigationBar] at its bottom: its
     * height and margins from [options].
     */
    fun placeOver(barLeft: Int, barRight: Int, barBottom: Int, barHeight: Int, tabsHeight: Int, navigationBar: Int) =
        CustomTabRowGeometry.inTabs(
            barLeft = barLeft,
            barRight = barRight,
            barBottom = barBottom,
            tabsHeight = tabsHeight,
            navigationBar = navigationBar,
            margin = UiUtils.dpToPixels(
                context,
                options.horizontalMargin.get(CustomTabRowGeometry.HORIZONTAL_MARGIN_DP.toDouble()),
            ),
            content = CustomTabRowGeometry.content(
                barHeight = barHeight,
                height = options.height.get(null)?.let { UiUtils.dpToPixels(context, it) },
                extra = UiUtils.dpToPixels(context, CustomTabRowGeometry.EXTRA_HEIGHT_DP.toDouble()),
            ),
            bottomMargin = UiUtils.dpToPixels(context, options.bottomMargin.get(0.0)),
        )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(following)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(following)
        super.onDetachedFromWindow()
    }

    // The row hides with the bar and takes its motion and its alpha (the tabs' own alpha and motion, and their
    // parents', the row has as their child); it stands as high as the container, and leaves accessibility while its
    // tabs wait to show.
    private fun follow(container: View) {
        val wanted = if (container.visibility != View.GONE) View.VISIBLE else View.GONE
        if (visibility != wanted) visibility = wanted
        if (alpha != container.alpha) alpha = container.alpha
        if (translationX != container.translationX) translationX = container.translationX
        if (translationY != container.translationY) translationY = container.translationY
        val lift = CustomTabRowGeometry.lift(container.z, elevation)
        if (translationZ != lift) translationZ = lift
        val mode = if (hiddenFromAccessibility()) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        if (importantForAccessibility != mode) importantForAccessibility = mode
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

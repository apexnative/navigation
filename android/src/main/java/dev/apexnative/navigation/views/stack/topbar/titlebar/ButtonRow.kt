package dev.apexnative.navigation.views.stack.topbar.titlebar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.R as MaterialR
import com.google.android.material.theme.overlay.MaterialThemeOverlay
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.UiUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The buttons of one side of the bar's row, from the start of the line: the back button ([navigationIcon]), the
 * buttons ([setButtons]), what the core always puts after them ([setTrailing]: the search's magnifier) and "⋮"
 * ([overflowButton]). They stand in the middle of the row, at most 56 dp high, as a Material 3 toolbar stands its
 * buttons, with the toolbar's theme (its ripples); right to left the line mirrors. Main thread only.
 */
@SuppressLint("ViewConstructor")
class ButtonRow(context: Context) : ViewGroup(themed(context)) {
    private val maxButtonHeight = UiUtils.dpToPx(this.context, MAX_BUTTON_HEIGHT_DP).roundToInt()
    private var navigation: AppCompatImageButton? = null
    private var buttons: List<View> = emptyList()
    private var trailing: View? = null
    private var overflow: OverflowButton? = null
    private var overflowColor: Int? = null

    /** The back button's icon; none hides the button. */
    var navigationIcon: Drawable?
        get() = navigation?.takeIf { it.visibility == VISIBLE }?.drawable
        set(value) {
            if (value == null) {
                navigation?.let {
                    it.setImageDrawable(null)
                    it.visibility = GONE
                }
                return
            }
            val button = navigation ?: AppCompatImageButton(context, null, AppCompatR.attr.toolbarNavigationButtonStyle)
                .also {
                    navigation = it
                    arrange()
                }
            button.setImageDrawable(value)
            button.visibility = VISIBLE
        }

    /** The back button's label: its accessibility label and tooltip. */
    var navigationContentDescription: CharSequence?
        get() = navigation?.contentDescription
        set(value) {
            val button = navigation ?: return
            button.contentDescription = value
            TooltipCompat.setTooltipText(button, value)
        }

    /** The back button's view while it shows; none. */
    val navigationButton: View?
        get() = navigation?.takeIf { it.visibility == VISIBLE }

    private var navigationListener: OnClickListener? = null

    /** What a press of the back button does. */
    fun setNavigationOnClickListener(listener: OnClickListener?) {
        navigationListener = listener
        navigation?.setOnClickListener(listener)
    }

    /** The buttons, in the line's order from its start. A view in another parent moves here. */
    fun setButtons(views: List<View>) {
        buttons = views
        arrange()
    }

    /** What stands after the buttons, before "⋮"; none. */
    fun setTrailing(view: View?) {
        if (trailing === view) return
        trailing = view
        arrange()
    }

    /** What stands after the buttons now. */
    val trailingView: View?
        get() = trailing

    /**
     * "⋮", made the first time it is asked for and shown with [setOverflowShown]: the theme's
     * `actionOverflowButtonStyle`, labelled "More options" in the user's language.
     */
    val overflowButton: View
        get() = overflow ?: OverflowButton(context).also {
            it.visibility = GONE
            overflowColor?.let { color -> ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(color)) }
            overflow = it
            arrange()
        }

    /** "⋮" shows at the end of the line, or not. */
    fun setOverflowShown(shown: Boolean) {
        if (!shown && overflow == null) return
        overflowButton.visibility = if (shown) VISIBLE else GONE
    }

    /** The color of "⋮", now or once it is made. */
    fun setOverflowColor(color: Int) {
        overflowColor = color
        overflow?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(color)) }
    }

    // The children in the line's order, so accessibility reads them in it too; a view that stays where it is isn't
    // moved, which would detach it.
    private fun arrange() {
        val wanted = listOfNotNull(navigation) + buttons + listOfNotNull(trailing, overflow)
        for (index in childCount - 1 downTo 0) {
            if (getChildAt(index) !in wanted) removeViewAt(index)
        }
        wanted.forEachIndexed { index, view ->
            if (index < childCount && getChildAt(index) === view) return@forEachIndexed
            (view.parent as? ViewGroup)?.removeView(view)
            addView(view, index, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        navigation?.setOnClickListener(navigationListener)
    }

    // As a toolbar, the row takes the touches around its buttons: they don't reach a screen under the bar.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = true

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val buttonHeight = min(height, maxButtonHeight)
        var used = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            val widthSpec = if (widthMode == MeasureSpec.UNSPECIFIED) {
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            } else {
                MeasureSpec.makeMeasureSpec(max(widthSize - used, 0), MeasureSpec.AT_MOST)
            }
            // The back button takes the whole height a toolbar gives its buttons; the others as much as they need.
            val heightMode = if (child === navigation) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST
            child.measure(widthSpec, MeasureSpec.makeMeasureSpec(buttonHeight, heightMode))
            used += child.measuredWidth
        }
        setMeasuredDimension(resolveSize(used, widthMeasureSpec), height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        var start = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            val w = child.measuredWidth
            val x = if (rtl) width - start - w else start
            val top = (height - child.measuredHeight) / 2
            child.layout(x, top, x + w, top + child.measuredHeight)
            start += w
        }
    }

    // "⋮" as a toolbar draws it: its ripple a square around the icon, its label as its tooltip.
    @SuppressLint("ViewConstructor")
    private class OverflowButton(context: Context) :
        AppCompatImageView(context, null, AppCompatR.attr.actionOverflowButtonStyle) {
        init {
            isClickable = true
            isFocusable = true
            TooltipCompat.setTooltipText(this, contentDescription)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val background = background ?: return
            if (drawable == null) return
            val half = max(w, h) / 2
            val centerX = (w + paddingLeft - paddingRight) / 2
            val centerY = (h + paddingTop - paddingBottom) / 2
            DrawableCompat.setHotspotBounds(background, centerX - half, centerY - half, centerX + half, centerY + half)
        }
    }

    private companion object {
        // A toolbar's `maxButtonHeight`.
        const val MAX_BUTTON_HEIGHT_DP = 56f

        // The theme a Material 3 toolbar gives its buttons (`toolbarStyle`'s overlay: its ripples), kept in the
        // current light or dark mode.
        fun themed(context: Context): Context {
            val wrapped = MaterialThemeOverlay.wrap(
                context,
                null,
                AppCompatR.attr.toolbarStyle,
                MaterialR.style.Widget_Material3_Toolbar,
            )
            if (wrapped !== context) MaterialTheme.followOverlay(wrapped)
            return wrapped
        }
    }
}

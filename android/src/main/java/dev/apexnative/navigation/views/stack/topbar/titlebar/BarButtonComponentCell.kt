package dev.apexnative.navigation.views.stack.topbar.titlebar

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import dev.apexnative.navigation.utils.UiUtils
import kotlin.math.max

/**
 * The cell of a React button in the bar (`topBar.leftButtons[].component`): the action view of the button's menu item,
 * as an icon's: 12 dp on each side, at least 48 dp wide, as high as the row, the content in its middle. The content (a
 * React root, or later a view of the screen's own tree) measures `AT_MOST` — the width at most what the row gives, the
 * height at most the row's — or exactly its `component.width` / `component.height` when the options give them
 * ([contentWidth], [contentHeight], pixels). The cell takes the menu's id; the content never gets one (React Native
 * rejects a root with an id). It never looks inside the content.
 */
@SuppressLint("ViewConstructor")
internal class BarButtonComponentCell(context: Context) : ViewGroup(context) {
    private val side = UiUtils.dpToPx(context, SIDE_DP)
    private val minWidth = UiUtils.dpToPx(context, MIN_WIDTH_DP)

    /** The content's width as the options set it, or null for its own. */
    var contentWidth: Int? = null
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** The content's height as the options set it, or null for its own. */
    var contentHeight: Int? = null
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** The view the cell shows; none takes it away. */
    var content: View? = null
        set(value) {
            if (field === value) return
            field?.let { if (it.parent === this) removeView(it) }
            field = value
            if (value != null) {
                (value.parent as? ViewGroup)?.removeView(value)
                addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val child = content
        var childWidth = 0
        if (child != null) {
            val maxWidth = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 2 else availableWidth - 2 * side
            val widthSpec = contentWidth?.let { MeasureSpec.makeMeasureSpec(it, MeasureSpec.EXACTLY) }
                ?: MeasureSpec.makeMeasureSpec(max(maxWidth, 0), MeasureSpec.AT_MOST)
            val heightSpec = contentHeight?.let { MeasureSpec.makeMeasureSpec(it, MeasureSpec.EXACTLY) }
                ?: MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
            child.measure(widthSpec, heightSpec)
            childWidth = child.measuredWidth
        }
        setMeasuredDimension(max(childWidth + 2 * side, minWidth), height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val child = content ?: return
        val left = (r - l - child.measuredWidth) / 2
        val top = (b - t - child.measuredHeight) / 2
        child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
    }

    private companion object {
        const val SIDE_DP = 12
        const val MIN_WIDTH_DP = 48
    }
}

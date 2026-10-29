package dev.apexnative.navigation.views.stack.topbar.titlebar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.Button
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.widget.TextViewCompat
import dev.apexnative.navigation.utils.UiUtils
import kotlin.math.roundToInt

/**
 * A button of the bar with an icon or a text, drawn as a toolbar draws the items of its menu: the theme's
 * `actionButtonStyle` (48 dp at least, 12 dp at the sides, the bar's ripple), its `actionMenuTextAppearance` and
 * `actionMenuTextColor`, the icon at most 32 dp and in the middle. With an icon the text doesn't show; it labels the
 * button for accessibility and is its tooltip instead, unless the button has a label of its own. Main thread only.
 */
@SuppressLint("ViewConstructor")
internal class BarButtonView(context: Context) :
    AppCompatTextView(context, null, AppCompatR.attr.actionButtonStyle) {
    private val minimumWidth = minWidth
    private val maxIconSize = UiUtils.dpToPx(context, MAX_ICON_DP).roundToInt()
    private val sidePadding = UiUtils.dpToPx(context, SIDE_DP).roundToInt()
    private var icon: Drawable? = null

    init {
        gravity = Gravity.CENTER
        isFocusable = true
        val vertical = UiUtils.dpToPx(context, VERTICAL_DP).roundToInt()
        setPaddingRelative(sidePadding, vertical, sidePadding, vertical)
        restyle()
    }

    /** The text appearance and color of the theme as it is now: a light/dark switch changes them. */
    fun restyle() {
        themed(AppCompatR.attr.actionMenuTextAppearance) { getResourceId(0, 0) }
            .takeIf { it != 0 }
            ?.let { TextViewCompat.setTextAppearance(this, it) }
        themed(AppCompatR.attr.actionMenuTextColor) { getColorStateList(0) }?.let(::setTextColor)
    }

    private fun <T> themed(attr: Int, read: TypedArray.() -> T): T {
        val values = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            values.read()
        } finally {
            values.recycle()
        }
    }

    /**
     * What the button shows: [icon], or [title] without one. [label] is its accessibility label, else the title when
     * only the icon shows; that title is its tooltip too. [tooltip] false leaves the long press to the caller.
     */
    fun show(title: CharSequence, icon: Drawable?, label: CharSequence?, tooltip: Boolean) {
        this.icon = icon?.also { fit(it) }
        setCompoundDrawablesRelative(icon, null, null, null)
        text = if (icon == null) title else null
        val hidden = title.takeIf { icon != null && it.isNotEmpty() }
        contentDescription = label?.takeIf { it.isNotEmpty() } ?: hidden
        TooltipCompat.setTooltipText(this, if (tooltip) hidden else null)
        requestLayout()
    }

    // TalkBack and UI tests see a button, as they see a toolbar's item.
    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    // An icon larger than the bar's item takes keeps its shape at 32 dp.
    private fun fit(icon: Drawable) {
        var width = icon.intrinsicWidth.coerceAtLeast(0)
        var height = icon.intrinsicHeight.coerceAtLeast(0)
        if (width > maxIconSize) {
            height = (height * maxIconSize.toFloat() / width).roundToInt()
            width = maxIconSize
        }
        if (height > maxIconSize) {
            width = (width * maxIconSize.toFloat() / height).roundToInt()
            height = maxIconSize
        }
        icon.setBounds(0, 0, width, height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val centered = icon?.takeIf { text.isNullOrEmpty() }
        if (centered == null) setPaddingRelative(sidePadding, paddingTop, sidePadding, paddingBottom)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // At least the theme's width, within what the row gives.
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val target = if (mode == MeasureSpec.AT_MOST) {
            minOf(MeasureSpec.getSize(widthMeasureSpec), minimumWidth)
        } else {
            minimumWidth
        }
        if (mode != MeasureSpec.EXACTLY && minimumWidth > 0 && measuredWidth < target) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
        // The icon alone stands in the middle of the button.
        if (centered != null) {
            val start = (measuredWidth - centered.bounds.width()) / 2
            setPaddingRelative(start, paddingTop, paddingEnd, paddingBottom)
        }
    }

    private companion object {
        const val MAX_ICON_DP = 32f
        const val SIDE_DP = 12f
        const val VERTICAL_DP = 4f
    }
}

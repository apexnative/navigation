package dev.apexnative.navigation.views.stack.topbar

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.appbar.MaterialToolbar
import dev.apexnative.navigation.utils.MaterialTheme
import dev.apexnative.navigation.utils.UiUtils
import kotlin.math.max
import kotlin.math.min

/**
 * The row of the bar: the left buttons with the back button at the start ([leftButtonBar]), the right buttons and "⋮"
 * at the end ([rightButtonBar]), both Material 3 toolbars, and between them the title and the subtitle, or a React
 * title ([titleComponent]). It lays them out in its own coordinates, so a shift of the row (`topBar.title.topMargin`)
 * moves them all; [sideInsets] keep them off the system bars and the cutout at the sides. Right to left, everything
 * mirrors. Main thread only.
 */
@SuppressLint("ViewConstructor")
class TitleBar(context: Context) : ViewGroup(context) {
    /** How a title stands between the buttons. */
    enum class Alignment { Start, Center, Fill }

    /** The back button (its navigation button) and the left buttons. */
    val leftButtonBar = MaterialToolbar(context).also(::plain)

    /** The right buttons, the search's magnifier and "⋮". */
    val rightButtonBar = MaterialToolbar(context).also(::plain)

    /** The title's text: one line, cut with "…" at its end (`topBar.title.text`). */
    val titleView = AppCompatTextView(context).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SIZE_SP)
    }

    /** The subtitle's text, under the title; gone without one (`topBar.subtitle.text`). */
    val subtitleView = AppCompatTextView(context).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_SP, SUBTITLE_SIZE_SP)
        visibility = GONE
    }

    /** The fonts the title and the subtitle start with: a screen's font builds on them. */
    val initialTitleTypeface = titleView.typeface
    val initialSubtitleTypeface = subtitleView.typeface

    private val gap = UiUtils.dpToPx(context, GAP_DP)
    private val sideInsetsBox = IntArray(2)

    /** The React title, in place of the text; none without one. */
    var titleComponent: View? = null
        set(value) {
            if (field === value) return
            field?.let { if (it.parent === this) removeView(it) }
            field = value
            if (value != null) {
                (value.parent as? ViewGroup)?.removeView(value)
                addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
            val text = if (value == null) VISIBLE else GONE
            titleView.visibility = text
            if (value != null) subtitleView.visibility = GONE
        }

    /** `topBar.title.alignment`: the text title's place. */
    var titleAlignment = Alignment.Start
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** `topBar.subtitle.alignment`: the subtitle's place (only start or center). */
    var subtitleAlignment = Alignment.Start
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** `topBar.title.component.alignment`: the React title's place (center without one). */
    var componentAlignment = Alignment.Center
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        addView(leftButtonBar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(titleView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(subtitleView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(rightButtonBar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    /** The room at the left and the right side for the system bars and the cutout, pixels. */
    fun sideInsets(left: Int, right: Int) {
        if (sideInsetsBox[0] == left && sideInsetsBox[1] == right) return
        sideInsetsBox[0] = left
        sideInsetsBox[1] = right
        requestLayout()
    }

    /** The subtitle's text; empty hides it. */
    fun setSubtitle(text: CharSequence?) {
        subtitleView.text = text
        subtitleView.visibility = if (text.isNullOrEmpty() || titleComponent != null) GONE else VISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val inner = max(width - sideInsetsBox[0] - sideInsetsBox[1], 0)
        val exactHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        alignButtons(leftButtonBar, height)
        alignButtons(rightButtonBar, height)
        leftButtonBar.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST), exactHeight)
        val rightRoom = max(inner - leftButtonBar.measuredWidth, 0)
        rightButtonBar.measure(MeasureSpec.makeMeasureSpec(rightRoom, MeasureSpec.AT_MOST), exactHeight)
        val between = max(inner - leftButtonBar.measuredWidth - rightButtonBar.measuredWidth, 0)
        val textRoom = max(between - gap - (if (titleAlignment == Alignment.Fill) gap else 0), 0)
        val atMostHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        val textSpec = MeasureSpec.makeMeasureSpec(textRoom, MeasureSpec.AT_MOST)
        if (titleView.visibility != GONE) titleView.measure(textSpec, atMostHeight)
        if (subtitleView.visibility != GONE) subtitleView.measure(textSpec, atMostHeight)
        titleComponent?.let {
            val spec = if (componentAlignment == Alignment.Fill) {
                MeasureSpec.makeMeasureSpec(max(between - 2 * gap, 0), MeasureSpec.EXACTLY)
            } else {
                MeasureSpec.makeMeasureSpec(between, MeasureSpec.AT_MOST)
            }
            it.measure(spec, atMostHeight)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val startInset = if (rtl) sideInsetsBox[1] else sideInsetsBox[0]
        val endInset = if (rtl) sideInsetsBox[0] else sideInsetsBox[1]
        val inner = width - startInset - endInset
        // Positions from the start of the line; mirrored right to left.
        fun place(view: View, start: Int, top: Int) {
            val w = view.measuredWidth
            val x = if (rtl) width - startInset - start - w else startInset + start
            view.layout(x, top, x + w, top + view.measuredHeight)
        }
        // The back button and the left buttons at the start of the line, the right buttons at its end: right to left
        // the whole row mirrors, and each toolbar mirrors what it holds.
        val startBar = leftButtonBar.measuredWidth
        val endBar = rightButtonBar.measuredWidth
        place(leftButtonBar, 0, 0)
        place(rightButtonBar, inner - endBar, 0)
        val component = titleComponent
        if (component != null) {
            val start = when (componentAlignment) {
                Alignment.Fill -> startBar + gap
                else -> centered(component.measuredWidth, inner, startBar, endBar)
            }
            place(component, start, (height - component.measuredHeight) / 2)
            return
        }
        val titleHeight = if (titleView.visibility != GONE) titleView.measuredHeight else 0
        val subtitleHeight = if (subtitleView.visibility != GONE) subtitleView.measuredHeight else 0
        var top = (height - titleHeight - subtitleHeight) / 2
        if (titleView.visibility != GONE) {
            val start = when (titleAlignment) {
                Alignment.Center -> centered(titleView.measuredWidth, inner, startBar, endBar)
                else -> startBar + gap
            }
            place(titleView, start, top)
            top += titleHeight
        }
        if (subtitleView.visibility != GONE) {
            val start = when (subtitleAlignment) {
                Alignment.Center -> centered(subtitleView.measuredWidth, inner, startBar, endBar)
                else -> startBar + gap
            }
            place(subtitleView, start, top)
        }
    }

    // In the middle of the row, moved off the buttons when it would lie over them.
    private fun centered(width: Int, inner: Int, startBar: Int, endBar: Int): Int {
        val middle = (inner - width) / 2
        return min(max(middle, startBar), max(inner - endBar - width, startBar))
    }

    // A toolbar stands its back button and its menu at the top of its minimum height, centered in it (`buttonGravity`
    // is top): with the row's height as that minimum, they stand in the middle of the row, as the title does.
    private fun alignButtons(toolbar: MaterialToolbar, height: Int) {
        if (toolbar.minimumHeight != height) toolbar.minimumHeight = height
    }

    // A toolbar that holds only buttons: no insets of its own around them (the row places them).
    private fun plain(toolbar: MaterialToolbar) {
        // Before the toolbar makes its menu, which takes the popup theme once: "⋮" in the current mode.
        toolbar.popupTheme = MaterialTheme.popupTheme(toolbar.context)
        toolbar.setContentInsetsRelative(0, 0)
        toolbar.contentInsetStartWithNavigation = 0
        toolbar.contentInsetEndWithActions = 0
        toolbar.setPadding(0, 0, 0, 0)
    }

    private companion object {
        const val GAP_DP = 16
        const val TITLE_SIZE_SP = 22f
        const val SUBTITLE_SIZE_SP = 14f
    }
}

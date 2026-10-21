package dev.apexnative.navigation.viewcontrollers.splitview

import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * The panes of a split view, and its compact layout, which shows in their place when the width doesn't take the
 * panes side by side.
 */
class SplitViewLayout(context: Context, panesWidth: Int) : FrameLayout(context) {
    val panes = SplitPanes(context)

    /**
     * The least width that takes the panes side by side: the primary pane's and the secondary one's. A new one
     * (`splitView.width` after creation) decides again at the next measure.
     */
    var panesWidth: Int = panesWidth
        internal set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private var compact: View? = null

    /** The width takes the panes side by side; null before the first measure. */
    var isWide: Boolean? = null
        private set

    /** The compact layout shows in place of the panes. */
    var isCompact = false
        private set

    /** Before the panes measure, when the width goes between the panes side by side and not. */
    var onWidthChanged: Runnable? = null

    init {
        addView(panes, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setCompact(view: View) {
        compact = view
        view.visibility = GONE
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wide = MeasureSpec.getSize(widthMeasureSpec) >= panesWidth
        if (wide != isWide) {
            isWide = wide
            isCompact = compact != null && !wide
            panes.visibility = if (isCompact) GONE else VISIBLE
            compact?.visibility = if (isCompact) VISIBLE else GONE
            panes.isSideBySide = wide
            onWidthChanged?.run()
            rootWindowInsets?.let { panes.dispatchApplyWindowInsets(it) }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

package dev.apexnative.navigation.viewcontrollers.splitview

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.slidingpanelayout.widget.SlidingPaneLayout

/**
 * The primary and the secondary pane. Side by side each keeps clear of the system bars and the camera cutout on its
 * own side of the screen only: the bar of the secondary pane has no room for a cutout on the other side.
 */
class SplitPanes(context: Context) : SlidingPaneLayout(context) {
    /** The panes are side by side. */
    var isSideBySide = false

    /**
     * Puts [next] in the place of the pane [old], with the structure and the params [old] had. The library wraps the
     * second view it is given in a view of its own that holds back touches (`addView` with one child there), and its
     * `removeView` takes that wrapper away: a plain remove and add would wrap a new primary pane, or give a new
     * secondary one the wrapper's params, and lose its width and weight. So the secondary pane's content changes
     * inside the wrapper, and a new primary pane goes in before the old one leaves, while two children are there.
     */
    internal fun replacePane(old: View, next: View) {
        val holder = old.parent as? ViewGroup ?: return
        val params = old.layoutParams
        if (holder === this) {
            // A copy: the width that `splitView.width` changes later is the new pane's own.
            addView(next, indexOfChild(old), if (params is LayoutParams) LayoutParams(params) else params)
            removeView(old)
        } else {
            val index = holder.indexOfChild(old)
            holder.removeView(old)
            holder.addView(next, index, params)
        }
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (!isSideBySide) return super.dispatchApplyWindowInsets(insets)
        val own = onApplyWindowInsets(insets)
        if (own.isConsumed) return own
        val all = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val primaryShown = getChildAt(0)?.visibility != GONE
        for (i in 0 until childCount) {
            // The primary pane is at the start, the secondary one at the end, and at the start too without the
            // primary one beside it.
            val start = i == 0 || !primaryShown
            val end = i != 0
            val sides = keepSides(all, if (rtl) end else start, if (rtl) start else end)
            getChildAt(i).dispatchApplyWindowInsets(sides.toWindowInsets() ?: insets)
        }
        return insets
    }

    private fun keepSides(all: WindowInsetsCompat, left: Boolean, right: Boolean): WindowInsetsCompat {
        if (left && right) return all
        val builder = WindowInsetsCompat.Builder(all)
        for (type in SIDE_TYPES) {
            val sides = all.getInsets(type)
            builder.setInsets(
                type,
                Insets.of(if (left) sides.left else 0, sides.top, if (right) sides.right else 0, sides.bottom)
            )
        }
        return builder.build()
    }

    private companion object {
        val SIDE_TYPES = intArrayOf(
            WindowInsetsCompat.Type.statusBars(),
            WindowInsetsCompat.Type.navigationBars(),
            WindowInsetsCompat.Type.captionBar(),
            WindowInsetsCompat.Type.displayCutout(),
            WindowInsetsCompat.Type.systemGestures(),
            WindowInsetsCompat.Type.mandatorySystemGestures(),
            WindowInsetsCompat.Type.tappableElement(),
        )
    }
}

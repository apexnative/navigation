package dev.apexnative.navigation.utils

import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView

/**
 * Scrolls the first view under [root] that scrolls up and down back to its top, depth first: a screen's list. A
 * React Native list is a [ScrollView]; a RecyclerView is told by its name, so the module doesn't depend on it.
 */
fun scrollToTop(root: View): Boolean {
    val view = firstVerticalScroller(root) ?: return false
    when (view) {
        is ScrollView -> view.smoothScrollTo(view.scrollX, 0)
        is NestedScrollView -> view.smoothScrollTo(view.scrollX, 0)
        is AbsListView -> view.smoothScrollToPosition(0)
        else -> runCatching {
            view.javaClass.getMethod("smoothScrollToPosition", Int::class.javaPrimitiveType).invoke(view, 0)
        }
    }
    return true
}

private fun firstVerticalScroller(view: View): View? {
    if (view.visibility != View.VISIBLE) return null
    if (view.canScrollVertically(-1)) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) firstVerticalScroller(view.getChildAt(index))?.let { return it }
    }
    return null
}

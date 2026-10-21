package dev.apexnative.navigation.views.element

import android.view.View
import android.view.ViewGroup

/**
 * The order the views of a transition lie in its layer: the order they are drawn in their hierarchy. Along each view's
 * path from the common root, the first step where two paths part decides: the higher `elevation` is drawn later, then
 * the later index among the siblings; an ancestor comes before what is in it. Fabric applies `zIndex` as the order of
 * children, so the index holds it. The comparison is pure.
 */
internal object DrawOrder {
    /** One step of a path: the view's `elevation` and its index among its siblings. */
    data class Step(val elevation: Float, val index: Int)

    /** Paths compared, drawn first first. */
    val comparator: Comparator<List<Step>> = Comparator { a, b ->
        for (i in 0 until minOf(a.size, b.size)) {
            val byElevation = a[i].elevation.compareTo(b[i].elevation)
            if (byElevation != 0) return@Comparator byElevation
            val byIndex = a[i].index.compareTo(b[i].index)
            if (byIndex != 0) return@Comparator byIndex
        }
        a.size.compareTo(b.size)
    }

    /** The path of [view] from [root] down, one step per view below [root]; empty when [root] doesn't hold it. */
    fun pathOf(view: View, root: View): List<Step> {
        val steps = ArrayList<Step>()
        var current: View = view
        while (current !== root) {
            val parent = current.parent as? ViewGroup ?: return emptyList()
            steps += Step(current.elevation, parent.indexOfChild(current))
            current = parent
        }
        return steps.asReversed()
    }

    /** [items] (their views by [viewOf]) in the order they are drawn under [root]. */
    fun <T> sorted(items: List<T>, root: View, viewOf: (T) -> View): List<T> {
        val paths = items.associateWith { pathOf(viewOf(it), root) }
        return items.sortedWith { a, b -> comparator.compare(paths.getValue(a), paths.getValue(b)) }
    }
}

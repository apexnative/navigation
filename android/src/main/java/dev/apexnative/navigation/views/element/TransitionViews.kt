package dev.apexnative.navigation.views.element

import android.view.View
import android.view.ViewGroup
import com.facebook.react.R as ReactR
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.SharedElementTransitionOptions

/**
 * The views of a transition's elements in its two screens: each screen is walked once, at its first lookup, for the ids
 * looked for there — the screen that goes ([fromRoot]) for the pairs' `fromId`s and the elements' ids, the one that
 * comes ([toRoot]) for the pairs' `toId`s and the elements' ids the other doesn't have. A lookup finds what
 * `ReactFindViewUtil.findView` finds in that screen as it was at its walk (depth first, the first match). Main thread
 * only.
 */
internal class TransitionViews(
    pairs: List<SharedElementTransitionOptions>,
    elements: List<ElementTransitionOptions>,
    private val fromRoot: View?,
    private val toRoot: View?,
) {
    // The ids each screen is walked for: the screen that goes for the sources and the elements, the one that comes for
    // the targets (and the elements the other screen doesn't have, added at its walk).
    private val leavingIds = HashSet<String>()
    private val arrivingIds = HashSet<String>()
    private val elementIds = ArrayList<String>()
    private var leavingViews: Map<String, View>? = null
    private var arrivingViews: Map<String, View>? = null

    init {
        for (pair in pairs) {
            pair.fromId.get(null)?.takeIf { it.isNotEmpty() }?.let(leavingIds::add)
            pair.toId.get(null)?.takeIf { it.isNotEmpty() }?.let(arrivingIds::add)
        }
        for (element in elements) {
            if (element.id.isEmpty()) continue
            leavingIds += element.id
            elementIds += element.id
        }
    }

    /** The view with [nativeId] in the screen that goes; none when none has it or it isn't one of the transition's. */
    fun leaving(nativeId: String): View? {
        val views = leavingViews ?: findViews(fromRoot, leavingIds).also { leavingViews = it }
        return views[nativeId]
    }

    /** The view with [nativeId] in the screen that comes; none when none has it or it isn't one of the transition's. */
    fun arriving(nativeId: String): View? {
        val views = arrivingViews ?: run {
            // An element is looked for here only when the screen that goes doesn't have it: the walk stops sooner.
            for (id in elementIds) if (leaving(id) == null) arrivingIds += id
            findViews(toRoot, arrivingIds).also { arrivingViews = it }
        }
        return views[nativeId]
    }

    companion object {
        /** The views with the `nativeID`s of [nativeIds] under [root], in one walk ([firstByKey]). */
        fun findViews(root: View?, nativeIds: Set<String>): Map<String, View> {
            if (root == null || nativeIds.isEmpty()) return emptyMap()
            return firstByKey(
                root,
                nativeIds,
                keyOf = { it.getTag(ReactR.id.view_tag_native_id) as? String },
                childCount = { (it as? ViewGroup)?.childCount ?: 0 },
                childAt = { view, index -> (view as ViewGroup).getChildAt(index) },
            )
        }
    }
}

/**
 * For each key of [keys], the first node with it in a depth-first walk from [root] (the node, then its children in
 * order): what a search for that one key finds, for all of them in one walk, which stops once every key is found. A key
 * no node has is left out. Inline: the walk calls no function objects.
 */
internal inline fun <N : Any> firstByKey(
    root: N,
    keys: Set<String>,
    keyOf: (N) -> String?,
    childCount: (N) -> Int,
    childAt: (N, Int) -> N,
): Map<String, N> {
    val found = HashMap<String, N>()
    if (keys.isEmpty()) return found
    // A stack of the nodes still to visit, the next one on top: the order of the recursive search without its depth.
    val stack = ArrayList<N>()
    stack.add(root)
    while (stack.isNotEmpty()) {
        val node = stack.removeAt(stack.size - 1)
        val key = keyOf(node)
        if (key != null && key in keys && key !in found) {
            found[key] = node
            if (found.size == keys.size) break
        }
        for (index in childCount(node) - 1 downTo 0) stack.add(childAt(node, index))
    }
    return found
}

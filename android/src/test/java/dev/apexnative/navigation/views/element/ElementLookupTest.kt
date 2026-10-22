package dev.apexnative.navigation.views.element

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The one walk that finds a transition's elements finds, for every id, the node the search by that one id finds
 * (depth first, the first match), and stops once all are found.
 */
class ElementLookupTest {
    private class Node(val key: String?, val children: List<Node> = emptyList())

    private fun walk(root: Node, keys: Set<String>, visited: MutableList<Node>? = null) = firstByKey(
        root,
        keys,
        keyOf = { visited?.add(it); it.key },
        childCount = { it.children.size },
        childAt = { node, index -> node.children[index] },
    )

    // The search by one id, as `ReactFindViewUtil.findView` makes it.
    private fun findOne(node: Node, key: String): Node? {
        if (node.key == key) return node
        for (child in node.children) findOne(child, key)?.let { return it }
        return null
    }

    @Test
    fun theWalkFindsWhatTheSearchByOneIdFinds() {
        val random = Random(7)
        repeat(200) {
            val keys = listOf("a", "b", "c", "d", "e")
            fun tree(depth: Int): Node = Node(
                key = keys.getOrNull(random.nextInt(keys.size * 3)),
                children = if (depth == 0) emptyList() else List(random.nextInt(4)) { tree(depth - 1) },
            )
            val root = tree(5)
            val wanted = keys.filter { random.nextBoolean() }.toSet() + "missing"
            val found = walk(root, wanted)
            for (key in wanted) {
                val one = findOne(root, key)
                if (one == null) assertFalse(key in found) else assertSame(one, found[key])
            }
        }
    }

    @Test
    fun theFirstMatchWinsAndTheWalkStopsOnceAllAreFound() {
        val firstA = Node("a")
        val laterA = Node("a")
        val b = Node("b")
        val unvisited = Node("c")
        val root = Node(null, listOf(Node(null, listOf(firstA, b)), laterA, unvisited))
        val visited = ArrayList<Node>()
        val found = walk(root, setOf("a", "b"), visited)
        assertSame(firstA, found["a"])
        assertSame(b, found["b"])
        assertFalse(laterA in visited)
        assertFalse(unvisited in visited)
        assertTrue(walk(root, emptySet()).isEmpty())
    }
}

package dev.apexnative.navigation.viewcontrollers.child

import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The nodes of an activity by id: every node a command may name, in the root, the modals and the overlays, and the
 * screens made but not shown yet. A node is in it from its creation to its destruction. Main thread only.
 */
class ChildControllersRegistry {
    private val nodes = LinkedHashMap<String, ViewController<*>>()

    fun onChildCreated(child: ViewController<*>) {
        nodes[child.id] = child
    }

    fun onChildDestroyed(child: ViewController<*>) {
        if (nodes[child.id] === child) nodes.remove(child.id)
    }

    /** The node with [id]; none when there isn't one. */
    fun find(id: String): ViewController<*>? = nodes[id]

    /** Every node, as they were made. */
    fun all(): List<ViewController<*>> = nodes.values.toList()

    fun clear() {
        nodes.clear()
    }
}

package dev.apexnative.navigation.slots

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * The slot views by their slot id, held weakly, so a change of a container reaches only the views of its id. A view is
 * under one id at a time: registering it under a new one (its `nativeID` changed, or Fabric reused it) takes it out
 * from under the old one. Two live views under one id both stay, in the order they came, and both hear the change, as
 * every view with that id did when the change went to all of them. Main thread only.
 */
internal class SlotViews<T : Any> {
    private val byId = HashMap<String, MutableList<WeakReference<T>>>()
    private val idOf = WeakHashMap<T, String>()

    fun register(slotId: String, view: T) {
        if (idOf[view] == slotId) return
        unregister(view)
        idOf[view] = slotId
        byId.getOrPut(slotId) { ArrayList(1) }.add(WeakReference(view))
    }

    fun unregister(view: T) {
        val slotId = idOf.remove(view) ?: return
        prune(slotId) { it === view }
    }

    /** The live views under [slotId], a copy: a view may register or leave while it hears the change. */
    fun views(slotId: String): List<T> {
        prune(slotId) { false }
        return byId[slotId]?.mapNotNull { it.get() }.orEmpty()
    }

    // Drops the collected views under the id and the ones `drop` picks; an id with no views left goes.
    private fun prune(slotId: String, drop: (T) -> Boolean) {
        val views = byId[slotId] ?: return
        views.removeAll { reference -> reference.get()?.let(drop) ?: true }
        if (views.isEmpty()) byId.remove(slotId)
    }
}

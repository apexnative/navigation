package dev.apexnative.navigation.slots

import android.view.ViewGroup
import java.lang.ref.WeakReference

/** A container that hears when the content of its slot mounted: it waits for that before the screen counts as shown. */
interface SlotContainer {
    fun slotContentDidMount()
}

/**
 * The native containers the slots of the app's React tree show their children in, by slot id, and the slot views by
 * the same id: a change of a container goes straight to the views of its id. A destroyed container and a collected
 * view drop out on their own.
 */
object SlotContainers {
    private class Entry(val view: WeakReference<ViewGroup>, val passThrough: Boolean)

    private val containers = HashMap<String, Entry>()
    private val slots = SlotViews<SlotView>()

    /** `passThrough`: touches outside the slot's content go to the screens below, as in an overlay for a toast. */
    fun put(slotId: String, container: ViewGroup, passThrough: Boolean = false) {
        containers[slotId] = Entry(WeakReference(container), passThrough)
        changed(slotId)
    }

    fun get(slotId: String): ViewGroup? = containers[slotId]?.view?.get()

    fun passesTouchesThrough(slotId: String): Boolean = containers[slotId]?.passThrough == true

    fun remove(slotId: String) {
        containers.remove(slotId)
        changed(slotId)
    }

    /** The container of a slot appeared or changed its size: the slot views under its id, and only they, hear it. */
    fun changed(slotId: String) {
        slots.views(slotId).forEach { it.containerChanged(slotId) }
    }

    /** The slot view goes under its slot id, and out from under the id it had. Main thread only. */
    internal fun register(slotId: String, slot: SlotView) = slots.register(slotId, slot)

    /** The slot view unmounted or lost its slot id: no container change reaches it. Main thread only. */
    internal fun unregister(slot: SlotView) = slots.unregister(slot)
}

package dev.apexnative.navigation.slots

import android.view.ViewGroup
import java.lang.ref.WeakReference

/** A container that hears when the content of its slot mounted: it waits for that before the screen counts as shown. */
interface SlotContainer {
    fun slotContentDidMount()
}

/**
 * The native containers the slots of the app's React tree show their children in, by slot id. A destroyed container
 * drops out on its own.
 */
object SlotContainers {
    private class Entry(val view: WeakReference<ViewGroup>, val passThrough: Boolean)

    private val containers = HashMap<String, Entry>()
    private val slots = LinkedHashSet<SlotView>()

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

    /** The container of a slot appeared or changed its size. */
    fun changed(slotId: String) {
        slots.toList().forEach { it.containerChanged(slotId) }
    }

    internal fun listen(slot: SlotView) = slots.add(slot)

    internal fun stopListening(slot: SlotView) = slots.remove(slot)
}

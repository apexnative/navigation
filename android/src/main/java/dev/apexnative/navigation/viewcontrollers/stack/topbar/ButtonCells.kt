package dev.apexnative.navigation.viewcontrollers.stack.topbar

import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The cells of the bar's React buttons (BarButtonComponentCell), by screen and `componentId`: a button of the stack's
 * or the defaults' options has one `componentId` on every screen, and each screen shows its own root in its own cell,
 * so the destroy of one screen never empties the cell another shows. A cell lives while its screen names the button.
 * [make] makes a cell, [empty] takes its content out. Main thread only.
 */
internal class ButtonCells<C : Any>(private val make: () -> C, private val empty: (C) -> Unit) {
    private val cells = HashMap<Pair<ViewController<*>, String>, C>()

    /** The cell of [screen]'s button [componentId]: kept, or made now. */
    fun cellOf(screen: ViewController<*>, componentId: String): C = cells.getOrPut(screen to componentId, make)

    /** [screen] names only [componentIds] now: the cells of the others go. */
    fun retain(screen: ViewController<*>, componentIds: Set<String>) =
        drop { it.first === screen && it.second !in componentIds }

    /** [screen] is destroyed: its cells go, and no other's. */
    fun screenDestroyed(screen: ViewController<*>) = drop { it.first === screen }

    /** The stack is destroyed: every cell goes. */
    fun clear() = drop { true }

    private fun drop(which: (Pair<ViewController<*>, String>) -> Boolean) {
        cells.keys.filter(which).forEach { key -> cells.remove(key)?.let(empty) }
    }
}

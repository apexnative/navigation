package dev.apexnative.navigation.viewcontrollers.stack.topbar

/**
 * Where the buttons of the bar go. The right ones: the bar has as many places as AppCompat gives a toolbar for the
 * window now ([maxActionButtons]), one less for "⋮" when it shows; the pinned buttons take theirs first, then the
 * buttons with the highest `priority` (ties by the list's order); the rest go into "⋮". In the line ([line]) the left
 * buttons stand from its start in the list's order, and the right ones toward its end, the first one last. A pure
 * function.
 */
internal object ButtonPlaces {
    /** A right button as the count sees it: its `priority` ([HIGH], [STANDARD], [LOW]) and `pinned`. */
    class Button(val priority: Int, val pinned: Boolean)

    /** The plan: the indices of the list in the bar and in "⋮", each in the list's order, and whether "⋮" shows. */
    class Plan(val inBar: List<Int>, val inOverflow: List<Int>, val showsOverflow: Boolean)

    const val HIGH = 2
    const val STANDARD = 1
    const val LOW = 0

    /** The `priority` of a button by its option's value: `high`, `low`, else standard. */
    fun priorityOf(value: String?): Int = when (value) {
        "high" -> HIGH
        "low" -> LOW
        else -> STANDARD
    }

    /**
     * How many buttons AppCompat puts in a toolbar for a window [widthDp] × [heightDp] dp with the smallest width
     * [smallestDp] (`ActionBarPolicy.getMaxActionButtons`, appcompat 1.7.0): "⋮" counts among them when it shows.
     */
    fun maxActionButtons(widthDp: Int, heightDp: Int, smallestDp: Int): Int = when {
        smallestDp > 600 || widthDp > 600 || (widthDp > 960 && heightDp > 720) || (widthDp > 720 && heightDp > 960) -> 5
        widthDp >= 500 || (widthDp > 640 && heightDp > 480) || (widthDp > 480 && heightDp > 640) -> 4
        widthDp >= 360 -> 3
        else -> 2
    }

    /**
     * The buttons of a side in the bar, [inBar] in the list's order, in the line's order from its start: the left ones
     * as they are, the right ones the other way round, so the first right button stands at the end of the line.
     */
    fun <T> line(inBar: List<T>, left: Boolean): List<T> = if (left) inBar else inBar.reversed()

    /**
     * The plan for [buttons] with [maxPlaces] places, [taken] of them already used by what the core always puts in the
     * bar (the search's magnifier), and [overflowItems] items of `overflowMenu`, which put "⋮" in the bar whatever
     * the buttons are.
     */
    fun plan(buttons: List<Button>, maxPlaces: Int, taken: Int, overflowItems: Int): Plan {
        val free = (maxPlaces - taken).coerceAtLeast(0)
        val needsOverflow = overflowItems > 0 || buttons.size > free
        var places = if (needsOverflow) (free - 1).coerceAtLeast(0) else free
        val inBar = HashSet<Int>()
        buttons.forEachIndexed { index, button ->
            if (button.pinned) {
                inBar += index
                places--
            }
        }
        val byPriority = buttons.indices.filter { !buttons[it].pinned }
            .sortedWith(compareByDescending<Int> { buttons[it].priority }.thenBy { it })
        for (index in byPriority) {
            if (places <= 0) break
            inBar += index
            places--
        }
        val overflow = buttons.indices.filter { it !in inBar }
        return Plan(buttons.indices.filter { it in inBar }, overflow, overflowItems > 0 || overflow.isNotEmpty())
    }
}

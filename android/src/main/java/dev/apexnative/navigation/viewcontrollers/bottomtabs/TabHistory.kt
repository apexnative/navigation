package dev.apexnative.navigation.viewcontrollers.bottomtabs

/**
 * The tabs chosen before, for `hardwareBackButton.bottomTabsOnPress: 'previous'` (
 * `hardwareBackButton.bottomTabsOnPress`): each selection by the user or from code puts the tab it left here while the
 * option is `previous`, never the same index twice in a row; a selection made by back adds nothing, and back takes the
 * last entry, so back doesn't go to and fro between two tabs. Main thread only.
 */
internal class TabHistory {
    private val entries = ArrayList<Int>()

    /** Nothing to go back to. */
    val isEmpty: Boolean
        get() = entries.isEmpty()

    /** The entries, oldest first. */
    val all: List<Int>
        get() = entries

    /** The selection left the tab at [index]. */
    fun left(index: Int) {
        if (entries.lastOrNull() != index) entries += index
    }

    /** Whether back would find a tab other than [current]. */
    fun hasOtherThan(current: Int): Boolean = entries.any { it != current }

    /** The tab back goes to from [current]: the last entry that isn't it, taken out with those after it; none. */
    fun takeFor(current: Int): Int? {
        while (entries.isNotEmpty()) {
            val last = entries.removeAt(entries.size - 1)
            if (last != current) return last
        }
        return null
    }

    fun clear() = entries.clear()
}

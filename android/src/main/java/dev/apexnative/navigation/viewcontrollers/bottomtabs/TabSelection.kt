package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.options.BottomTabsOptions

/**
 * What a press of a tab does (also for a cell of the custom row), and which tab a one-shot choice names: the decisions
 * alone, without views, the same for every source of a press (the bar, a cell of the row). The tabs run what they say.
 */
internal object TabSelection {
    /** What a press of a tab leads to, after its `ANN.BottomTabPressed`. */
    enum class Action {
        /** Another tab becomes the selected one (`ANN.BottomTabSelected` after it). */
        Select,

        /** `bottomTab.selectTabOnPress: false`: the selection stays where it is. */
        Stay,

        /** The selected tab again: its stack goes back to its first screen, or its content scrolls to the top. */
        Repeat,

        /** The selected tab again with `popToRootOnPress: false` or `selectTabOnPress: false`: nothing more. */
        Nothing,
    }

    /**
     * The press of a tab that [wasSelected] or not, with its resolved `bottomTab.selectTabOnPress` and
     * `popToRootOnPress` ([selectTabOnPress], [popToRootOnPress]: `false` only when the options say so).
     */
    fun press(wasSelected: Boolean, selectTabOnPress: Boolean, popToRootOnPress: Boolean): Action = when {
        !wasSelected && selectTabOnPress -> Action.Select
        !wasSelected -> Action.Stay
        selectTabOnPress && popToRootOnPress -> Action.Repeat
        else -> Action.Nothing
    }

    /** The tab a one-shot choice names. */
    sealed class Choice {
        /** None of the two options is set, or an unknown id alone. */
        object None : Choice()

        /** The tab at [index], in layout order. */
        data class Tab(val index: Int) : Choice()

        /** `currentTabIndex` out of range: [index] (truncated) for the log; nothing changes. */
        data class OutOfRange(val index: Int) : Choice()
    }

    /**
     * The tab [options] choose among [count] tabs: `currentTabId` when [indexOfId] finds a tab that is that id or holds
     * a layout with it; else `currentTabIndex`, truncated to a whole number, when it is in range, or
     * [Choice.OutOfRange] (a negative one too); else none. Read only from the options that bring them (the layout's
     * own, a merge's), never from resolved options.
     */
    fun choice(options: BottomTabsOptions, count: Int, indexOfId: (String) -> Int?): Choice {
        val id = options.currentTabId.get(null)
        if (!id.isNullOrEmpty()) indexOfId(id)?.let { return Choice.Tab(it) }
        val wanted = options.currentTabIndex.get(null) ?: return Choice.None
        val index = wanted.toInt()
        return if (index in 0 until count && wanted >= 0) Choice.Tab(index) else Choice.OutOfRange(index)
    }

    /** The one text of an index out of range on both platforms. */
    fun outOfRangeText(index: Int, count: Int): String =
        "[ApexNavigation] bottomTabs.currentTabIndex $index is out of range: the layout has $count tabs"
}

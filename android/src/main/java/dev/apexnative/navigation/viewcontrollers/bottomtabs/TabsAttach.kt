package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.options.params.Text

/**
 * When the views of the tabs are made and join the tabs' view (`bottomTabs.tabsAttachMode`): the decisions alone,
 * without views.
 */
internal object TabsAttach {
    enum class Mode {
        /** Every tab with the tabs, the unselected ones invisible but laid out: they render at once. */
        Together,

        /** The selected tab first; the others once it appeared, or a tab chosen before that at its selection. */
        AfterInitialTab,

        /** The selected tab only; another at its first selection, without a wait. */
        OnSwitchToTab,
    }

    /** The mode of `bottomTabs.tabsAttachMode`, resolved with the defaults; `together` when unset. */
    fun modeOf(value: Text): Mode = when (value.get(null)) {
        "afterInitialTab" -> Mode.AfterInitialTab
        "onSwitchToTab" -> Mode.OnSwitchToTab
        else -> Mode.Together
    }

    /** The tabs whose views join as the tabs' view is made, [selected] among [count]. */
    fun atCreation(mode: Mode, selected: Int, count: Int): List<Int> =
        if (mode == Mode.Together) (0 until count).toList() else listOf(selected)

    /** The tabs whose views join once the first tab appeared: every other with `afterInitialTab`. */
    fun afterFirstAppearance(mode: Mode, count: Int): List<Int> =
        if (mode == Mode.AfterInitialTab) (0 until count).toList() else emptyList()

    /**
     * The tabs a wait for the tabs counts though they don't show: with `together`, every tab but the selected one; none
     * otherwise.
     */
    fun unseenWaited(mode: Mode, selected: Int, count: Int): List<Int> =
        if (mode == Mode.Together) (0 until count).filter { it != selected } else emptyList()
}

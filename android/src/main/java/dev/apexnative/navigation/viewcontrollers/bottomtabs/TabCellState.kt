package dev.apexnative.navigation.viewcontrollers.bottomtabs

/**
 * What the native side tells the React component of a tab's cell (`bottomTab.component`): its tab's index, whether it
 * is selected, the badge's text (none without one) and the dot, shown only without a badge (it takes the dot's place),
 * with its color when the options give one (`dotColor`). The props JavaScript keeps for the cell stay there by its
 * `componentId`.
 */
internal data class TabCellState(
    val tabIndex: Int,
    val selected: Boolean,
    val badge: String?,
    val dot: Boolean,
    val dotColor: Int?,
) {
    companion object {
        /** The state of the cell of the tab at [index], from the badge text and the dot its item shows. */
        fun of(index: Int, selected: Boolean, badge: String, dotVisible: Boolean, dotColor: Int?): TabCellState =
            TabCellState(
                tabIndex = index,
                selected = selected,
                badge = badge.ifEmpty { null },
                dot = dotVisible && badge.isEmpty(),
                dotColor = dotColor,
            )
    }
}

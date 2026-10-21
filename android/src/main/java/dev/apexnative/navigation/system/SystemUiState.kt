package dev.apexnative.navigation.system

/**
 * What the system UI of an activity shows: the status bar, the navigation bar, the requested orientation and, without
 * edge to edge, whether the window resizes for the keyboard, as [SystemUiRules] computes them from the screens that
 * decide. The owner keeps the state it applied last and writes only the fields that differ ([changedFrom]). Colors are
 * ARGB.
 */
internal data class SystemUiState(
    /** The status bar shows (`statusBar.visible`). */
    val statusBarShown: Boolean,
    /** The color behind the status bar. */
    val statusBarColor: Int,
    /** Light icons in the status bar (for a dark background); dark icons otherwise. */
    val statusBarLightIcons: Boolean,
    /** The navigation bar shows (`navigationBar.visible`). */
    val navigationBarShown: Boolean,
    /** The color behind the navigation bar. */
    val navigationBarColor: Int,
    /** Light buttons in the navigation bar (for a dark background); dark buttons otherwise. */
    val navigationBarLightButtons: Boolean,
    /** The activity's requested orientation, one of `ActivityInfo.SCREEN_ORIENTATION_*`. */
    val orientation: Int,
    /**
     * The keyboard covers the bottom of the screen and the window doesn't resize for it (`layout.adjustResize: false`
     * without edge to edge); else the window's own soft input mode.
     */
    val keyboardCovers: Boolean,
) {
    /** One field of the state, as it is written to the window. */
    enum class Field {
        StatusBarShown,
        StatusBarColor,
        StatusBarIcons,
        NavigationBarShown,
        NavigationBarColor,
        NavigationBarButtons,
        Orientation,
        KeyboardCovers,
    }

    /** The fields that differ from [previous], in the order they are written; every field without one. */
    fun changedFrom(previous: SystemUiState?): List<Field> {
        if (previous == null) return Field.entries.toList()
        return Field.entries.filter { valueOf(it) != previous.valueOf(it) }
    }

    private fun valueOf(field: Field): Any = when (field) {
        Field.StatusBarShown -> statusBarShown
        Field.StatusBarColor -> statusBarColor
        Field.StatusBarIcons -> statusBarLightIcons
        Field.NavigationBarShown -> navigationBarShown
        Field.NavigationBarColor -> navigationBarColor
        Field.NavigationBarButtons -> navigationBarLightButtons
        Field.Orientation -> orientation
        Field.KeyboardCovers -> keyboardCovers
    }
}

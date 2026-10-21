package dev.apexnative.navigation.layout

/** The type of a node of the layout JSON, with the name JavaScript sends. */
internal enum class LayoutType(
    /** The name in the layout JSON. */
    val jsName: String,
) {
    Component("Component"),
    ExternalComponent("ExternalComponent"),
    Stack("Stack"),
    BottomTabs("BottomTabs"),
    SideMenuRoot("SideMenuRoot"),
    SideMenuLeft("SideMenuLeft"),
    SideMenuCenter("SideMenuCenter"),
    SideMenuRight("SideMenuRight"),
    SplitView("SplitView"),
    Arrangement("Arrangement"),
    ;

    /** A screen: a React screen or a native one. */
    val isScreen: Boolean
        get() = this == Component || this == ExternalComponent

    /** A side of a side menu: a place in the menu, not a layout of its own. */
    val isSideMenuSide: Boolean
        get() = this == SideMenuLeft || this == SideMenuCenter || this == SideMenuRight

    companion object {
        private val byName = entries.associateBy { it.jsName }

        /** The type JavaScript names [name]; none for a name the layout doesn't know. */
        @JvmStatic
        fun fromName(name: String?): LayoutType? = name?.let(byName::get)
    }
}

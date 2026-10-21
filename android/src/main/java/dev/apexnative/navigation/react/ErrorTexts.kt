package dev.apexnative.navigation.react

/**
 * Every text the native side rejects a command with or writes to the log for a command without a promise: one function
 * per case, the same words as iOS. Other code takes its texts from here. JavaScript prefixes them with `[navigation]
 * <command>:`.
 */
internal object ErrorTexts {
    fun screenNotShown(id: String) = "the screen '$id' is not shown"

    fun notInStack(id: String) = "the screen '$id' is not in a stack"

    fun firstScreenOfStack(id: String) = "the screen '$id' is the first screen of its stack"

    fun notInThisStack(id: String) = "the screen '$id' is not in this stack"

    fun notInModal(id: String) = "the screen '$id' is not in a modal"

    fun notInOverlay(id: String) = "the screen '$id' is not in an overlay"

    fun notInSplitView(id: String) = "the screen '$id' is not in a split view"

    fun neitherSplitViewNorStack(id: String) = "the screen '$id' is neither in a split view nor in a stack"

    fun noSuchColumn(id: String, column: String) = "the split view of the screen '$id' has no '$column' column"

    fun columnIsStack(id: String, column: String) =
        "the '$column' column of the split view of the screen '$id' is a stack: it takes a screen, not a stack"

    fun columnNotStack(id: String, column: String) =
        "the '$column' column of the split view of the screen '$id' is not a stack"

    fun noScreensForStack(id: String) = "there are no screens to put in the stack of the screen '$id'"

    fun alreadyShown(id: String) = "the screen '$id' shows already: an id goes to one screen"

    fun setRootReplaced(id: String) =
        "setRoot was replaced by a newer setRoot before the root '$id' finished showing"

    fun rootDestroyed(id: String) = "the root '$id' was destroyed before it showed"

    fun modalWithoutRoot(id: String) = "the modal '$id' can't be dismissed: there is no root under it"

    fun nowhereToShow() = "the navigation has nowhere to show screens: the app has no activity or window"

    fun nativeScreenNotRegistered(name: String) = "no native screen is registered as '$name'"

    fun nativeScreenNotCreated(name: String) =
        "the native screen '$name' could not be created: its creator returned nothing"

    fun unknownNodeType(type: String) = "the layout has a node of unknown type '$type'"

    fun nodeWithoutId(type: String) = "the layout has a node of type '$type' without an id"

    fun sideMenuChild(id: String, type: String) =
        "the side menu '$id' has a child of type '$type': it takes SideMenuLeft, SideMenuCenter and SideMenuRight"

    fun stackTakesScreens(id: String, type: String) = "the stack '$id' takes screens, not a '$type'"

    fun emptyStack(id: String) = "the stack '$id' has no screens"

    fun emptyTabs(id: String) = "the tabs '$id' have no tabs"

    fun tooManyTabs(id: String, count: Int) = "the tabs '$id' have $count tabs: Android shows at most 5"

    fun splitViewColumns(id: String) = "the split view '$id' needs its primary and its secondary column"

    fun pairUnsupported(id: String) = "the pair '$id' needs iOS 27.1 and an app built with its SDK"

    fun pairNeedsTwoScreens(id: String) = "the pair '$id' needs two screens: its primary and its secondary"

    fun unreadableOption(path: String, id: String) =
        "the option '$path' of the screen '$id' has a value the navigation can't read"

    /** `setDefaultOptions` with a value it can't read; the default options before stay. */
    fun unreadableDefaultOption(path: String) =
        "the option '$path' of the default options has a value the navigation can't read"

    fun previewGone(id: String) = "the preview '$id' went without being opened"

    /** Any other failure: [what] the command could not do, with its screen, and [reason]. */
    fun couldNot(what: String, reason: String) = "could not $what: $reason"

    fun nativeCallReloaded(name: String, id: String) =
        "JavaScript reloaded before the call '$name' of the screen '$id' was answered"

    fun nativeCallNotRunning(name: String, id: String) =
        "the navigation is not running: the call '$name' of the screen '$id' can't be answered"

    /** `mergeOptions` without a navigation activity; written to the log, `mergeOptions` has no promise. */
    fun mergeOptionsNoActivity() = nowhereToShow()

    /** A command whose area the native core doesn't do yet. */
    fun notDoneYet(method: String) = "could not run '$method': the native core doesn't do it yet"

    // The other cases of the layout checks: the same style.

    fun nodeWithoutName(type: String, id: String) = "the layout has a node '$id' of type '$type' without a name"

    fun rootMissing() = "the layout of setRoot has no root"

    fun tabsInTabs(id: String) = "the tabs '$id' have a tab of type 'BottomTabs': tabs don't go inside tabs"

    fun sideMenuSides(id: String) =
        "the side menu '$id' takes one SideMenuCenter and at most one SideMenuLeft and one SideMenuRight"

    fun sideChildCount(sideId: String, menuId: String) =
        "the side '$sideId' of the side menu '$menuId' needs exactly one child"

    fun sideMenuInSide(menuId: String) =
        "the side menu '$menuId' has a side menu in a side: a side menu doesn't go inside a side menu"

    fun sideOutsideMenu(id: String, type: String) = "the layout has a node '$id' of type '$type' outside a side menu"

    fun splitViewInColumn(id: String) =
        "the split view '$id' has a column of type 'SplitView': a split view doesn't go inside a split view"

    fun idGivenTwice(id: String) = "the layout gives the id '$id' to two nodes"

    /** The reason of [couldNot] for an exception: its message, else the name of its class. */
    fun reasonOf(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName.ifEmpty { e.javaClass.name }
}

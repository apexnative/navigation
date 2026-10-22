// Made by scripts/contract.mjs from src/interfaces/contract/errors.json: change that file and run the script again.

package dev.apexnative.navigation.react

/**
 * Every text the native side rejects a command with or writes to the log for a command without a promise: one function
 * per case, the same words as iOS. Other code takes its texts from here. JavaScript prefixes them with `[navigation]
 * <command>:`.
 */
internal object ErrorTexts {
    // Where the screen is.

    fun screenNotShown(componentId: String) = "the screen '$componentId' is not shown"

    fun notInStack(componentId: String) = "the screen '$componentId' is not in a stack"

    fun firstScreenOfStack(componentId: String) = "the screen '$componentId' is the first screen of its stack"

    fun notInThisStack(componentId: String) = "the screen '$componentId' is not in this stack"

    fun notInModal(componentId: String) = "the screen '$componentId' is not in a modal"

    fun notInOverlay(componentId: String) = "the screen '$componentId' is not in an overlay"

    fun notInSplitView(componentId: String) = "the screen '$componentId' is not in a split view"

    fun neitherSplitViewNorStack(componentId: String) =
        "the screen '$componentId' is neither in a split view nor in a stack"

    fun noSuchColumn(componentId: String, column: String) =
        "the split view of the screen '$componentId' has no '$column' column"

    fun columnIsStack(componentId: String, column: String) =
        "the '$column' column of the split view of the screen '$componentId' is a stack: it takes a screen, not a stack"

    fun columnNotStack(componentId: String, column: String) =
        "the '$column' column of the split view of the screen '$componentId' is not a stack"

    fun noScreensForStack(componentId: String) = "there are no screens to put in the stack of the screen '$componentId'"

    fun alreadyShown(componentId: String) = "the screen '$componentId' shows already: an id goes to one screen"

    // Root, modals, the app.

    fun setRootReplaced(rootId: String) =
        "setRoot was replaced by a newer setRoot before the root '$rootId' finished showing"

    fun rootDestroyed(rootId: String) = "the root '$rootId' was destroyed before it showed"

    fun modalWithoutRoot(componentId: String) = "the modal '$componentId' can't be dismissed: there is no root under it"

    fun nowhereToShow() = "the navigation has nowhere to show screens: the app has no activity or window"

    // Native screens.

    fun nativeScreenNotRegistered(name: String) = "no native screen is registered as '$name'"

    fun nativeScreenNotCreated(name: String) =
        "the native screen '$name' could not be created: its creator returned nothing"

    /** The error of a call of a native screen whose JavaScript reloaded before it answered. */
    fun nativeCallReloaded(name: String, componentId: String) =
        "JavaScript reloaded before the call '$name' of the screen '$componentId' was answered"

    /** The error of a call of a native screen while no JavaScript can answer it. */
    fun nativeCallNotRunning(name: String, componentId: String) =
        "the navigation is not running: the call '$name' of the screen '$componentId' can't be answered"

    // The layout.

    fun unknownNodeType(type: String) = "the layout has a node of unknown type '$type'"

    fun nodeWithoutId(type: String) = "the layout has a node of type '$type' without an id"

    fun sideMenuChild(menuId: String, type: String) =
        "the side menu '$menuId' has a child of type '$type': it takes SideMenuLeft, SideMenuCenter and SideMenuRight"

    fun stackTakesScreens(stackId: String, type: String) = "the stack '$stackId' takes screens, not a '$type'"

    fun emptyStack(stackId: String) = "the stack '$stackId' has no screens"

    fun emptyTabs(tabsId: String) = "the tabs '$tabsId' have no tabs"

    /** Only Android checks it: its tab bar shows at most 5 tabs. */
    fun tooManyTabs(tabsId: String, count: Int) = "the tabs '$tabsId' have $count tabs: Android shows at most 5"

    fun splitViewColumns(splitViewId: String) =
        "the split view '$splitViewId' needs its primary and its secondary column"

    /** Only iOS checks it: Android has pairs on every version. */
    fun pairUnsupported(pairId: String) = "the pair '$pairId' needs iOS 27.1 and an app built with its SDK"

    fun pairNeedsTwoScreens(pairId: String) = "the pair '$pairId' needs two screens: its primary and its secondary"

    fun nodeWithoutName(type: String, nodeId: String) = "the layout has a node '$nodeId' of type '$type' without a name"

    fun rootMissing() = "the layout of setRoot has no root"

    fun tabsInTabs(tabsId: String) = "the tabs '$tabsId' have a tab of type 'BottomTabs': tabs don't go inside tabs"

    fun sideMenuSides(menuId: String) =
        "the side menu '$menuId' takes one SideMenuCenter and at most one SideMenuLeft and one SideMenuRight"

    fun sideChildCount(sideId: String, menuId: String) =
        "the side '$sideId' of the side menu '$menuId' needs exactly one child"

    fun sideMenuInSide(menuId: String) =
        "the side menu '$menuId' has a side menu in a side: a side menu doesn't go inside a side menu"

    fun sideOutsideMenu(nodeId: String, type: String) =
        "the layout has a node '$nodeId' of type '$type' outside a side menu"

    fun splitViewInColumn(splitViewId: String) =
        "the split view '$splitViewId' has a column of type 'SplitView': a split view doesn't go inside a split view"

    fun idGivenTwice(nodeId: String) = "the layout gives the id '$nodeId' to two nodes"

    // Options, previews, any other failure.

    fun unreadableOption(path: String, componentId: String) =
        "the option '$path' of the screen '$componentId' has a value the navigation can't read"

    /** An unreadable value of `setDefaultOptions`: the default options belong to no screen. */
    fun unreadableDefaultOption(path: String) =
        "the option '$path' of the default options has a value the navigation can't read"

    fun previewGone(previewId: String) = "the preview '$previewId' went without being opened"

    /** Any other failure: what could not be done and why, `could not <what>: <reason>`. */
    fun couldNot(what: String, reason: String) = "could not $what: $reason"

    /** A command whose part of the native core is not written yet. */
    fun notDoneYet(method: String) = "could not run '$method': the native core doesn't do it yet"

    /** The reason of [couldNot] for an exception: its message, else the name of its class. */
    fun reasonOf(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName.ifEmpty { e.javaClass.name }
}

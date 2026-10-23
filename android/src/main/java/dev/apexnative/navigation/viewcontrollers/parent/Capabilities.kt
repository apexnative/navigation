package dev.apexnative.navigation.viewcontrollers.parent

import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

// The capabilities of a node: what the core asks a layout for where it needs the behavior of a split view or a pair,
// instead of checking its class. A node without one answers "no". The names are the same on iOS:
// returnsScreenFromColumn, screensLeavingWithNode, childOffsetFromRest, optionsScreen, keepsStatusBarRoom.

/** "Returns a screen from a column": the pop of the only screen of a column goes back to the column before. */
interface ReturnsScreenFromColumn {
    /**
     * [screen], the only screen of its column, goes back: the column goes away, and [done] runs once it has stopped
     * moving. False when this isn't the node's case: the core pops as in any stack, and [done] doesn't run.
     */
    fun returnsScreenFromColumn(screen: ViewController<*>, done: Runnable): Boolean
}

/** "Screens that leave with the node": a node taken off a stack takes these screens with it (`ANN.ScreenPopped`). */
interface ScreensLeavingWithNode {
    val screensLeavingWithNode: List<ViewController<*>>
}

/**
 * "Child offset from rest": a child that moves (a side menu's drawer, a sliding pane) counts its safe area where it
 * comes to rest; a screen adds what every layout on its way gives (`ComponentViewController.updateSafeArea`).
 */
interface ChildOffsetFromRest {
    /** How far [child] is from where it rests, in pixels along the width; 0 for a child at rest. */
    fun childOffsetFromRest(child: ViewController<*>): Int
}

/** "The screen whose options are the node's": the bar, the search, the status bar and the animations take its. */
interface OptionsScreen {
    val optionsScreen: ViewController<*>
}

/** "Keeps the status bar room for its screens": the node keeps it at its own top, its screens keep none. */
interface KeepsStatusBarRoom

/** A node that shows screens in its columns (the split view): `showInColumn` and `setColumnShown` go to it. */
interface ShowsColumns {
    /**
     * Shows [screen] in [column], in place of what it shows; [listener] once it shows, or with the error reason, which
     * names [from], the screen the command comes from. [goesInStack] says whether [screen]'s layout is what a stack
     * takes (a screen, a native screen, a pair), not a container. False when the node doesn't take it now (its compact
     * layout shows): the core pushes the screen over [from] instead.
     */
    fun showScreen(
        screen: ViewController<*>,
        column: String,
        goesInStack: Boolean,
        from: ViewController<*>,
        listener: CommandListener,
    ): Boolean

    /** Shows or hides [column]; [done] once the columns have stopped moving. */
    fun setColumnShown(column: String, shown: Boolean, done: Runnable)
}

/** The nearest node with the capability [T] from [node] up through the layouts around it; none without one. */
inline fun <reified T : Any> nodeWith(node: ViewController<*>): T? {
    var current: ViewController<*>? = node
    while (current != null) {
        if (current is T) return current
        current = current.parentController
    }
    return null
}

package dev.apexnative.navigation.viewcontrollers.navigator

import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * A layer of the activity that waits to show: the root of a `setRoot` on its way today, a modal that waits for its
 * screens in its stage. While it waits, what is in it is in the window but not on screen: a stack there runs its
 * commands at once; a layout there (a stack, the tabs, a side menu, a split view, a pair) tells the layer when the
 * screens it shows change, so the layer waits for the screens it shows now. The nodes ask through
 * [Navigator.isInWaitingLayer] and [Navigator.shownScreensChanged], never a layer by name. Main thread only.
 */
internal interface WaitingLayer {
    /** The node at the top of the layer while it waits to show; none while it doesn't wait. */
    val waitingNode: ViewController<*>?

    /** A layout inside [waitingNode] changed the screens it shows: the layer's wait follows those it shows now. */
    fun shownScreensChanged()
}

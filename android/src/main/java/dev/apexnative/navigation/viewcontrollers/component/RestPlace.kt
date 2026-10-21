package dev.apexnative.navigation.viewcontrollers.component

import android.view.View
import dev.apexnative.navigation.viewcontrollers.modal.SheetContainer

/**
 * Where a view is in the window from layout only: the places of the views up to the window, each less its parent's
 * scroll, without the translations of animations, and a sheet's container where the sheet rests, not where a drag took
 * it. The one rule for the three parts of a screen's safe area: the screen's React root, the bottom of the stack's
 * header and the top of the tabs, so they count the same place while something around them moves. Main thread only.
 */
internal object RestPlace {
    /** The views from [view] up to the window, each with its place in its parent and its parent's scroll. */
    fun stepsToWindow(view: View): List<ScreenSafeArea.Step> {
        val steps = ArrayList<ScreenSafeArea.Step>()
        var current: View = view
        while (true) {
            val parent = current.parent as? View
            val top = if (current is SheetContainer) SheetContainer.restTop(current.top, current.shift) else current.top
            steps += ScreenSafeArea.Step(current.left, top, parent?.scrollX ?: 0, parent?.scrollY ?: 0)
            current = parent ?: break
        }
        return steps
    }

    /** [view]'s top in the window, at rest. */
    fun topInWindow(view: View): Int = ScreenSafeArea.placeInWindow(stepsToWindow(view), view.width, view.height).top
}

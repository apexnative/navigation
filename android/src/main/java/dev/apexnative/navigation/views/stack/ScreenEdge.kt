package dev.apexnative.navigation.views.stack

import android.graphics.drawable.Drawable
import android.view.View

/**
 * What fills the room beside a moving screen in a transition (`StackLayout.extendEdge`), where the system's
 * transitions between activities stretch a window's edge (`<extend>` of `activity_open_*` and `activity_close_*`): the
 * screen's own background, which is what its edge shows unless its content covers it. Taken without drawing the
 * screen, at the start of every push and pop. Main thread only.
 */
internal object ScreenEdge {
    /** A copy of [view]'s background, stretched across the room; none without one. */
    fun pictureOf(view: View): Drawable? = view.background?.constantState?.newDrawable(view.resources)?.mutate()
}

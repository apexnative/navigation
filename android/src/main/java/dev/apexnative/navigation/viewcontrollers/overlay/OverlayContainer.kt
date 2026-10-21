package dev.apexnative.navigation.viewcontrollers.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * The view of one overlay in the overlays layer: full size, later above earlier, the overlay's root view in it. It
 * decides at each touch's down whether the overlay takes the gesture ([takesTouch], [OverlayTouches]); when it doesn't,
 * it gives every event of the gesture away and the layers under get it (before React Native's root sees it). One it
 * takes stays the overlay's, even where no view of it handles it. While the overlay waits to show it gives every touch
 * away ([isWaiting]).
 */
@SuppressLint("ViewConstructor")
internal class OverlayContainer(context: Context) : FrameLayout(context) {
    /** The overlay waits to show: touches go to the layers under it. */
    var isWaiting = true

    /** Whether the overlay takes the gesture that starts with this down. */
    var takesTouch: (MotionEvent) -> Boolean = { true }

    // The overlay takes the gesture that runs.
    private var taking = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (isWaiting) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) taking = takesTouch(event)
        if (!taking) return false
        super.dispatchTouchEvent(event)
        return true
    }
}

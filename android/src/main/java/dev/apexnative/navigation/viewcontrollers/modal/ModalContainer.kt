package dev.apexnative.navigation.viewcontrollers.modal

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout

/**
 * The view of one modal in the modals layer: full size, newer above older; the modal's root
 * view is its child, or, for a sheet, the scrim and the container of its Material sheet, whose behavior needs a
 * `CoordinatorLayout` and sees only this modal's touches. While the modal waits to show it is transparent and gives
 * every touch away, so the screen under keeps them ([isWaiting]).
 */
@SuppressLint("ViewConstructor")
internal class ModalContainer(context: Context) : CoordinatorLayout(context) {
    /** The modal waits to show: touches go to the layers under it. */
    var isWaiting = true

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = !isWaiting && super.dispatchTouchEvent(event)
}

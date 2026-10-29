package dev.apexnative.navigation.views.stack

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View

/**
 * What fills the room beside a moving screen in a transition (`StackLayout.extendEdge`), where the system's
 * transitions between activities stretch a window's edge (`<extend>` of `activity_open_*` and `activity_close_*`): the
 * column of pixels at the screen's edge, stretched across the room, as the system stretches the window's. What the
 * screen shows there goes on beside it — a gradient its content draws, not only its own background. Taken at the start
 * of every push and pop. Main thread only.
 */
internal object ScreenEdge {
    /**
     * The column of pixels at [view]'s left edge ([left]) or right edge, made to be stretched; its background when the
     * screen isn't laid out as it shows ([laidOut]: not after a rotation while it was out of the stack) or can't be drawn
     * in software (a `HARDWARE` bitmap in it, a view that fails to draw), none without one either.
     */
    fun pictureOf(view: View, left: Boolean, laidOut: Boolean): Drawable? =
        (if (laidOut) columnOf(view, left) else null) ?: backgroundOf(view)

    // One pixel wide and as tall as the screen: the view draws itself into a canvas that keeps that column only. Any
    // view of the screen may fail to draw in software (a hardware bitmap, a view that wants a hardware canvas): the
    // screen's background stands in then, as the transition must go on.
    private fun columnOf(view: View, left: Boolean): Drawable? {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return null
        return try {
            val column = Bitmap.createBitmap(1, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(column)
            if (!left) canvas.translate(-(width - 1).toFloat(), 0f)
            view.draw(canvas)
            BitmapDrawable(view.resources, column)
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun backgroundOf(view: View): Drawable? =
        view.background?.constantState?.newDrawable(view.resources)?.mutate()
}

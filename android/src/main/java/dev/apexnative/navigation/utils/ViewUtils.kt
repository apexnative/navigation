package dev.apexnative.navigation.utils

import android.graphics.Point
import android.view.View

/** Where views are. */
object ViewUtils {
    /** The top left corner of [view] on the screen, in pixels. */
    @JvmStatic
    fun getLocationOnScreen(view: View): Point {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Point(location[0], location[1])
    }
}

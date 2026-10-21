package dev.apexnative.navigation.views.element.animators

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import dev.apexnative.navigation.utils.ViewUtils
import dev.apexnative.navigation.utils.computeInheritedScale
import dev.apexnative.navigation.views.element.TransitionLayer

/**
 * Where a shared element flies in its transition's layer: from where `from` is on the screen, at the scale of its
 * parents, to `to`'s place in the layer (its margins and size). Pixels of the layer.
 */
internal class FlightGeometry(
    /** `from`'s place and size in the layer. */
    val from: RectF,
    /** `to`'s place and size in the layer. */
    val to: RectF,
    /** The scale `from`'s parents give it along x: its corners shrink with it. */
    val fromScale: Float,
) {
    /** The width and height `to` is laid out at meanwhile: the larger of the two sizes. */
    val largest: Pair<Int, Int>
        get() = maxOf(from.width(), to.width()).toInt() to maxOf(from.height(), to.height()).toInt()

    companion object {
        /** The flight of [from] to [to], which is in [layer] already; none when [to] has no place there. */
        fun of(from: View, to: View, layer: TransitionLayer): FlightGeometry? {
            val place = to.layoutParams as? ViewGroup.MarginLayoutParams ?: return null
            val origin = layer.originOnScreen
            val onScreen = ViewUtils.getLocationOnScreen(from)
            val (scaleX, scaleY) = computeInheritedScale(from)
            val left = (onScreen.x - origin.x).toFloat()
            val top = (onScreen.y - origin.y).toFloat()
            val fromRect = RectF(left, top, left + from.width * scaleX, top + from.height * scaleY)
            val toLeft = place.leftMargin.toFloat()
            val toTop = place.topMargin.toFloat()
            val toRect = RectF(toLeft, toTop, toLeft + to.width, toTop + to.height)
            return FlightGeometry(fromRect, toRect, scaleX)
        }
    }
}

/** An outline of the size and corners a flying view shows meanwhile: its own bounds are larger. */
internal class FlightOutline : android.view.ViewOutlineProvider() {
    var width = 0
    var height = 0
    var radius = 0f

    override fun getOutline(view: View, outline: android.graphics.Outline) {
        outline.setRoundRect(0, 0, width, height, radius)
    }
}

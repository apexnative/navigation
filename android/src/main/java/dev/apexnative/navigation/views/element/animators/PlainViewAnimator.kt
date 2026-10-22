package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.view.View
import android.view.ViewOutlineProvider
import com.facebook.react.uimanager.BackgroundStyleApplicator
import com.facebook.react.uimanager.style.BorderRadiusProp
import dev.apexnative.navigation.utils.borderRadius
import dev.apexnative.navigation.views.animations.ValueInterpolation
import dev.apexnative.navigation.views.element.TransitionLayer
import kotlin.math.roundToInt

/**
 * A shared React view without children moves itself: from `from`'s place on the screen to its own by a translation; its
 * size and corner radius by an outline that grows from `from`'s (at the scale of its parents) to its own while it is
 * laid out at the larger of the two sizes; its own radius taken off meanwhile and put back as it was; its background
 * color from `from`'s to its own in Lab, ending exactly on its own (`BackgroundStyleApplicator`); its rotation from
 * `from`'s to its own. Main thread only.
 */
internal class PlainViewAnimator(
    private val from: View,
    private val to: View,
    private val layer: TransitionLayer,
) {
    /** The animator; none when [to] has no place in the layer. */
    fun create(): Animator? {
        val geometry = FlightGeometry.of(from, to, layer) ?: return null
        val fromRadius = from.borderRadius * geometry.fromScale
        val toRadius = to.borderRadius
        val fromColor = BackgroundStyleApplicator.getBackgroundColor(from)
        val toColor = BackgroundStyleApplicator.getBackgroundColor(to)
        val fromRotation = from.rotation
        val toRotation = to.rotation
        val ownRadius = BackgroundStyleApplicator.getBorderRadius(to, RADIUS)
        val outline = FlightOutline()
        var savedProvider: ViewOutlineProvider? = null
        var savedClip = false
        val (width, height) = geometry.largest
        val toRect = geometry.to
        return MoveAnimator().apply {
            fun frame(time: Float) {
                val f = curve.getInterpolation(time)
                val left = ValueInterpolation.number(geometry.from.left, toRect.left, f, time)
                val top = ValueInterpolation.number(geometry.from.top, toRect.top, f, time)
                to.translationX = left - toRect.left
                to.translationY = top - toRect.top
                val from = geometry.from
                outline.width = ValueInterpolation.number(from.width(), toRect.width(), f, time).roundToInt()
                outline.height = ValueInterpolation.number(from.height(), toRect.height(), f, time).roundToInt()
                outline.radius = ValueInterpolation.number(fromRadius, toRadius, f, time)
                to.invalidateOutline()
                if (fromColor != toColor) {
                    val color = ValueInterpolation.color(fromColor, toColor, f, time)
                    BackgroundStyleApplicator.setBackgroundColor(to, color)
                }
                to.rotation = ValueInterpolation.number(fromRotation, toRotation, f, time)
            }
            onStart {
                savedProvider = to.outlineProvider
                savedClip = to.clipToOutline
                if (ownRadius != null) BackgroundStyleApplicator.setBorderRadius(to, RADIUS, null)
                to.outlineProvider = outline
                to.clipToOutline = true
                layer.place(to, toRect.left.toInt(), toRect.top.toInt(), width, height)
                frame(0f)
            }
            onFrame { frame(it) }
            // A cancelled flight ends too: the view goes back whole.
            onEnd {
                to.translationX = 0f
                to.translationY = 0f
                to.rotation = toRotation
                if (fromColor != toColor) BackgroundStyleApplicator.setBackgroundColor(to, toColor)
                if (ownRadius != null) BackgroundStyleApplicator.setBorderRadius(to, RADIUS, ownRadius)
                to.outlineProvider = savedProvider
                to.clipToOutline = savedClip
                layer.place(to, toRect.left.toInt(), toRect.top.toInt(), toRect.width().toInt(),
                    toRect.height().toInt())
            }
        }
    }

    private companion object {
        val RADIUS = BorderRadiusProp.BORDER_RADIUS
    }
}

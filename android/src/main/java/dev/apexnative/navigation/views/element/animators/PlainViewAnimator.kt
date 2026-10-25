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
 * A shared React view without children moves itself: from where `from` is drawn to where it is drawn in its screen
 * ([FlightGeometry]); its size and corner radius by an outline that grows from `from`'s (at the scale it is drawn at)
 * to its own while it is laid out at the larger of the two sizes, unscaled and turned about the outline's middle; its
 * own radius taken off meanwhile and put back as it was; its background color from `from`'s to its own in Lab, ending
 * exactly on its own (`BackgroundStyleApplicator`). Main thread only.
 */
internal class PlainViewAnimator(
    private val from: View,
    private val to: View,
    /** Where [to] was in its screen: what it flies to. */
    private val toStandIn: View?,
    private val layer: TransitionLayer,
) {
    /** The animator; none when [to] has no place in the layer. */
    fun create(): Animator? {
        val geometry = FlightGeometry.of(from, to, toStandIn, layer) ?: return null
        val fromRadius = from.borderRadius * geometry.from.scale
        val toRadius = to.borderRadius * geometry.to.scale
        val fromColor = BackgroundStyleApplicator.getBackgroundColor(from)
        val toColor = BackgroundStyleApplicator.getBackgroundColor(to)
        val ownRadius = BackgroundStyleApplicator.getBorderRadius(to, RADIUS)
        val outline = FlightOutline()
        val moved = MovedProperties(to)
        var savedProvider: ViewOutlineProvider? = null
        var savedClip = false
        val (width, height) = geometry.largest
        return MoveAnimator().apply {
            fun frame(time: Float) {
                val f = curve.getInterpolation(time)
                val at = geometry.at(f, time)
                outline.width = at.width.roundToInt()
                outline.height = at.height.roundToInt()
                outline.radius = ValueInterpolation.number(fromRadius, toRadius, f, time)
                moved.show(geometry, at, outline.width, outline.height)
                to.invalidateOutline()
                if (fromColor != toColor) {
                    val color = ValueInterpolation.color(fromColor, toColor, f, time)
                    BackgroundStyleApplicator.setBackgroundColor(to, color)
                }
            }
            onStart {
                savedProvider = to.outlineProvider
                savedClip = to.clipToOutline
                if (ownRadius != null) BackgroundStyleApplicator.setBorderRadius(to, RADIUS, null)
                to.outlineProvider = outline
                to.clipToOutline = true
                layer.place(to, geometry.toLeft, geometry.toTop, width, height)
                frame(0f)
            }
            onFrame { frame(it) }
            // A cancelled flight ends too: the view goes back whole.
            onEnd {
                if (fromColor != toColor) BackgroundStyleApplicator.setBackgroundColor(to, toColor)
                if (ownRadius != null) BackgroundStyleApplicator.setBorderRadius(to, RADIUS, ownRadius)
                to.outlineProvider = savedProvider
                to.clipToOutline = savedClip
                moved.end(geometry, landed) { w, h -> layer.place(to, geometry.toLeft, geometry.toTop, w, h) }
            }
        }
    }

    private companion object {
        val RADIUS = BorderRadiusProp.BORDER_RADIUS
    }
}

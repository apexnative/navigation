package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.view.View
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.react.views.image.ReactImageView
import dev.apexnative.navigation.views.element.TransitionLayer
import kotlin.math.roundToInt

/**
 * A shared React image: from where `from` is drawn to where it is drawn in its screen ([FlightGeometry]), laid out at
 * the size of each frame, unscaled and turned about its middle; its content by Fresco's scale type going from `from`'s
 * fit to its own (`ScalingUtils.InterpolatingScaleType`); Fresco's own fade off meanwhile. Everything goes back as it
 * was at the end. Main thread only.
 */
internal class ReactImageAnimator(
    private val from: ReactImageView,
    private val to: ReactImageView,
    /** Where [to] was in its screen: what it flies to. */
    private val toStandIn: View?,
    private val layer: TransitionLayer,
) {
    /** The animator; none when [to] has no place in the layer or no hierarchy. */
    fun create(): Animator? {
        if (!from.hasHierarchy() || !to.hasHierarchy()) return null
        val geometry = FlightGeometry.of(from, to, toStandIn, layer) ?: return null
        val toType = to.hierarchy.actualImageScaleType ?: ScalingUtils.ScaleType.CENTER_CROP
        val fromType = from.hierarchy.actualImageScaleType ?: ScalingUtils.ScaleType.CENTER_CROP
        val scale = ScalingUtils.InterpolatingScaleType(fromType, toType)
        val moved = MovedProperties(to)
        var savedFade = 0
        return MoveAnimator().apply {
            fun frame(time: Float) {
                val f = curve.getInterpolation(time)
                val at = geometry.at(f, time)
                val width = at.width.roundToInt()
                val height = at.height.roundToInt()
                layer.place(to, geometry.toLeft, geometry.toTop, width, height)
                moved.show(geometry, at, width, height)
                scale.setValue(if (time >= 1f) 1f else f)
                to.invalidate()
            }
            onStart {
                savedFade = to.hierarchy.fadeDuration
                to.hierarchy.fadeDuration = 0
                to.hierarchy.setActualImageScaleType(scale)
                frame(0f)
            }
            onFrame { frame(it) }
            onEnd {
                to.hierarchy.setActualImageScaleType(toType)
                to.hierarchy.fadeDuration = savedFade
                moved.end(geometry, landed) { w, h -> layer.place(to, geometry.toLeft, geometry.toTop, w, h) }
            }
        }
    }
}

package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.react.views.image.ReactImageView
import dev.apexnative.navigation.views.animations.ValueInterpolation
import dev.apexnative.navigation.views.element.TransitionLayer
import kotlin.math.roundToInt

/**
 * A shared React image: its place by a translation from `from`'s on the screen; its content by Fresco's scale type
 * going from `from`'s fit to its own (`ScalingUtils.InterpolatingScaleType`); the part seen from `from`'s size to its
 * own (it is laid out at the size of each frame); its rotation from `from`'s to its own; Fresco's own fade off
 * meanwhile. Everything goes back as it was at the end. Main thread only.
 */
internal class ReactImageAnimator(
    private val from: ReactImageView,
    private val to: ReactImageView,
    private val layer: TransitionLayer,
) {
    /** The animator; none when [to] has no place in the layer or no hierarchy. */
    fun create(): Animator? {
        if (!from.hasHierarchy() || !to.hasHierarchy()) return null
        val geometry = FlightGeometry.of(from, to, layer) ?: return null
        val toRect = geometry.to
        val toType = to.hierarchy.actualImageScaleType ?: ScalingUtils.ScaleType.CENTER_CROP
        val fromType = from.hierarchy.actualImageScaleType ?: ScalingUtils.ScaleType.CENTER_CROP
        val scale = ScalingUtils.InterpolatingScaleType(fromType, toType)
        val fromRotation = from.rotation
        val toRotation = to.rotation
        var savedFade = 0
        return MoveAnimator().apply {
            fun frame(time: Float) {
                val f = curve.getInterpolation(time)
                val left = ValueInterpolation.number(geometry.from.left, toRect.left, f, time)
                val top = ValueInterpolation.number(geometry.from.top, toRect.top, f, time)
                val width = ValueInterpolation.number(geometry.from.width(), toRect.width(), f, time).roundToInt()
                val height = ValueInterpolation.number(geometry.from.height(), toRect.height(), f, time).roundToInt()
                layer.place(to, toRect.left.toInt(), toRect.top.toInt(), width, height)
                to.translationX = left - toRect.left
                to.translationY = top - toRect.top
                scale.setValue(if (time >= 1f) 1f else f)
                to.rotation = ValueInterpolation.number(fromRotation, toRotation, f, time)
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
                to.translationX = 0f
                to.translationY = 0f
                to.rotation = toRotation
                layer.place(to, toRect.left.toInt(), toRect.top.toInt(), toRect.width().toInt(),
                    toRect.height().toInt())
            }
        }
    }
}

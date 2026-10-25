package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.graphics.Matrix
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.facebook.react.views.view.ReactViewGroup
import dev.apexnative.navigation.utils.borderRadius
import dev.apexnative.navigation.views.animations.ValueInterpolation
import dev.apexnative.navigation.views.element.TransitionLayer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A shared image of another kind (an `ImageView` that isn't React Native's): from where `from` is drawn to where it is
 * drawn in its screen ([FlightGeometry]), unscaled and turned about its middle; its image matrix from `from`'s fit
 * (`centerCrop`, `fitXY`, `fitCenter`, `centerInside`) to its own, the part seen from `from`'s size to its own, the
 * corner radius of the parent that clips it (`borderRadius` with `overflow: hidden`) by its outline. Another fit: the
 * pair moves without the change of its matrix, and a line goes to the log. Everything goes back as it was at the end.
 * Main thread only.
 */
internal class ImageMatrixAnimator(
    private val from: ImageView,
    private val to: ImageView,
    /** Where [to] was in its screen: what it flies to. */
    private val toStandIn: View?,
    private val layer: TransitionLayer,
    /** The parents the views are clipped by in their screens: [to]'s is the one it had before the layer. */
    private val fromParent: View?,
    private val toParent: View?,
    private val id: String,
    private val log: (String) -> Unit,
) {
    /** The animator; none when [to] has no place in the layer. */
    fun create(): Animator? {
        val geometry = FlightGeometry.of(from, to, toStandIn, layer) ?: return null
        val fromFit = from.scaleType
        val toFit = to.scaleType
        // The image whose matrix changes; none when a fit isn't one of the four or there is no image to fit.
        val image = to.drawable?.takeIf {
            fromFit in FITS && toFit in FITS && it.intrinsicWidth > 0 && it.intrinsicHeight > 0
        }
        val fits = image != null
        if (!fits) log("The image '$id' fits by ${fromFit.name} and ${toFit.name}: it moves without a change of fit")
        val fromRadius = clipRadius(fromParent) * geometry.from.scale
        val toRadius = clipRadius(toParent) * geometry.to.scale
        val outline = FlightOutline()
        val moved = MovedProperties(to)
        var savedProvider: ViewOutlineProvider? = null
        var savedClip = false
        val savedMatrix = Matrix(to.imageMatrix)
        val start = FloatArray(MATRIX)
        val end = FloatArray(MATRIX)
        val values = FloatArray(MATRIX)
        val matrix = Matrix()
        return MoveAnimator().apply {
            fun frame(time: Float) {
                val f = curve.getInterpolation(time)
                val at = geometry.at(f, time)
                val width = at.width.roundToInt()
                val height = at.height.roundToInt()
                layer.place(to, geometry.toLeft, geometry.toTop, width, height)
                moved.show(geometry, at, width, height)
                outline.width = width
                outline.height = height
                outline.radius = ValueInterpolation.number(fromRadius, toRadius, f, time)
                to.invalidateOutline()
                if (image != null) {
                    val imageWidth = image.intrinsicWidth
                    val imageHeight = image.intrinsicHeight
                    fitMatrix(fromFit, imageWidth, imageHeight, width, height).getValues(start)
                    fitMatrix(toFit, imageWidth, imageHeight, width, height).getValues(end)
                    for (i in 0 until MATRIX) values[i] = ValueInterpolation.number(start[i], end[i], f, time)
                    matrix.setValues(values)
                    to.imageMatrix = matrix
                }
            }
            onStart {
                savedProvider = to.outlineProvider
                savedClip = to.clipToOutline
                to.outlineProvider = outline
                to.clipToOutline = true
                if (fits) to.scaleType = ImageView.ScaleType.MATRIX
                frame(0f)
            }
            onFrame { frame(it) }
            onEnd {
                if (fits) {
                    to.scaleType = toFit
                    to.imageMatrix = savedMatrix
                }
                to.outlineProvider = savedProvider
                to.clipToOutline = savedClip
                moved.end(geometry, landed) { w, h -> layer.place(to, geometry.toLeft, geometry.toTop, w, h) }
            }
        }
    }

    // The radius of a React parent that clips what is in it; 0 for another.
    private fun clipRadius(parent: View?): Float =
        (parent as? ReactViewGroup)?.takeIf { it.overflow == HIDDEN }?.borderRadius ?: 0f

    private companion object {
        const val MATRIX = 9
        const val HIDDEN = "hidden"
        val FITS = setOf(
            ImageView.ScaleType.CENTER_CROP,
            ImageView.ScaleType.FIT_XY,
            ImageView.ScaleType.FIT_CENTER,
            ImageView.ScaleType.CENTER_INSIDE,
        )

        // The matrix of a fit of an image of [imageWidth] × [imageHeight] into [width] × [height].
        fun fitMatrix(fit: ImageView.ScaleType, imageWidth: Int, imageHeight: Int, width: Int, height: Int): Matrix {
            val sx = width.toFloat() / imageWidth
            val sy = height.toFloat() / imageHeight
            val matrix = Matrix()
            if (fit == ImageView.ScaleType.FIT_XY) {
                matrix.setScale(sx, sy)
                return matrix
            }
            val scale = when (fit) {
                ImageView.ScaleType.CENTER_CROP -> max(sx, sy)
                ImageView.ScaleType.CENTER_INSIDE -> min(1f, min(sx, sy))
                else -> min(sx, sy)
            }
            matrix.setScale(scale, scale)
            matrix.postTranslate((width - imageWidth * scale) / 2f, (height - imageHeight * scale) / 2f)
            return matrix
        }
    }
}

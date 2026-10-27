package dev.apexnative.navigation.views.element.animators

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import com.facebook.react.views.image.ReactImageView
import com.facebook.react.views.view.ReactViewGroup
import dev.apexnative.navigation.utils.borderRadius
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A shared image flies as its image, fitted all the way: the copy goes from where `from` is drawn to where `to` is
 * drawn in its screen ([FlightGeometry]), clipped to its corners, and the image of `to` in it goes from the part and
 * place `from` shows its image at (its fit, `resizeMode`) to those of `to`, keeping its proportions where both fits
 * do. The image is `to`'s own drawable, drawn on the layer's canvas and only read: an animated image plays on. The
 * corners are the image's own radius, else that of a parent that clips it (`overflow: hidden`) and that it fills.
 */
internal class ImageMorph private constructor(
    private val start: ImageEnd,
    private val end: ImageEnd,
    private val image: Drawable,
    // Where the image's drawable draws the image, in the coordinates it draws in.
    private val drawn: RectF,
) : FlightPicture() {
    private val flight = FlightGeometry(start.end, end.end)
    private var at = start.end
    private var progress = 0f
    private val rect = RectF()
    private val imageRect = FloatArray(RECT)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // What is drawn over the mask keeps only the mask's pixels: the corners stay smooth.
    private val contentPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }

    override fun update(time: Float, progress: Float) {
        this.progress = progress
        at = flight.at(progress, time)
        for (i in 0 until RECT) imageRect[i] = lerp(start.image[i], end.image[i])
    }

    override fun draw(canvas: Canvas) {
        rect.set(-at.width / 2, -at.height / 2, at.width / 2, at.height / 2)
        val imageWidth = imageRect[2] - imageRect[0]
        val imageHeight = imageRect[3] - imageRect[1]
        if (rect.isEmpty || imageWidth <= 0 || imageHeight <= 0 || drawn.isEmpty) return
        val alpha = (lerp(start.alpha, end.alpha) * 255).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        val radius = lerp(start.radius, end.radius)
        // The copy turns about its center, mirrored where a view is.
        canvas.translate(at.centerX, at.centerY)
        canvas.rotate(at.rotation)
        canvas.scale(at.mirrorX, at.mirrorY)
        canvas.saveLayer(rect, null)
        canvas.drawRoundRect(rect, radius, radius, maskPaint)
        contentPaint.alpha = alpha
        canvas.saveLayer(rect, contentPaint)
        canvas.translate(imageRect[0], imageRect[1])
        canvas.scale(imageWidth / drawn.width(), imageHeight / drawn.height())
        canvas.translate(-drawn.left, -drawn.top)
        image.draw(canvas)
    }

    private fun lerp(from: Float, to: Float) = from + (to - from) * progress

    // One end of the flight: where the view is drawn, where its image shows in it (left, top, right, bottom from the
    // view's center, at the size it is drawn at), its corner radius there and its opacity.
    private class ImageEnd(val end: FlightEnd, val image: FloatArray, val radius: Float, val alpha: Float)

    companion object {
        private const val RECT = 4

        // How far from its parent's size an image still fills it, in pixels.
        private const val FILLS = 1
        private const val HIDDEN = "hidden"

        /** The morph of [from] into [to] in [layer]; none when an image isn't there to fit. */
        fun of(from: ImageView, to: ImageView, layer: View): ImageMorph? {
            val image = to.drawable ?: return null
            val start = endOf(from, layer) ?: return null
            val end = endOf(to, layer) ?: return null
            val drawn = drawnRect(to) ?: return null
            return ImageMorph(start, end, image, drawn)
        }

        private fun endOf(view: ImageView, layer: View): ImageEnd? {
            if (view.width <= 0 || view.height <= 0) return null
            val shown = shownRect(view) ?: return null
            val end = LayerPlace.of(view, layer).end(view.width, view.height)
            val image = imageFromCenter(
                floatArrayOf(shown.left, shown.top, shown.right, shown.bottom),
                view.width, view.height, end.width, end.height,
            )
            return ImageEnd(end, image, cornerRadius(view) * end.scale, view.alpha)
        }

        // Where the drawable draws the image when the view draws it: Fresco's image where its fit puts it in the
        // drawable, another image at its drawable's bounds (the view's matrix comes on top).
        private fun drawnRect(view: ImageView): RectF? {
            val rect = if (view is ReactImageView) {
                if (!view.hasHierarchy()) return null
                RectF().also { view.hierarchy.getActualImageBounds(it) }
            } else {
                RectF(view.drawable?.bounds ?: return null)
            }
            return rect.takeUnless { it.isEmpty }
        }

        // Where the image shows in the view, in its pixels.
        private fun shownRect(view: ImageView): RectF? {
            val rect = drawnRect(view) ?: return null
            if (view !is ReactImageView) view.imageMatrix.mapRect(rect)
            rect.offset(view.paddingLeft.toFloat(), view.paddingTop.toFloat())
            return rect
        }

        private fun cornerRadius(view: ImageView): Float {
            view.borderRadius.takeIf { it > 0f }?.let { return it }
            val parent = view.parent as? ReactViewGroup ?: return 0f
            val fills = abs(parent.width - view.width) <= FILLS && abs(parent.height - view.height) <= FILLS
            return if (fills && parent.overflow == HIDDEN) parent.borderRadius else 0f
        }

        /**
         * The rect [shown] (left, top, right, bottom) of a view of [width] × [height], from the view's center, at the
         * size [shownWidth] × [shownHeight] the view is drawn at.
         */
        internal fun imageFromCenter(
            shown: FloatArray,
            width: Int,
            height: Int,
            shownWidth: Float,
            shownHeight: Float,
        ): FloatArray {
            val scaleX = shownWidth / width
            val scaleY = shownHeight / height
            return floatArrayOf(
                (shown[0] - width / 2f) * scaleX,
                (shown[1] - height / 2f) * scaleY,
                (shown[2] - width / 2f) * scaleX,
                (shown[3] - height / 2f) * scaleY,
            )
        }
    }
}

package dev.apexnative.navigation.views.element.animators

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.View
import dev.apexnative.navigation.utils.borderRadius
import kotlin.math.roundToInt

/**
 * A shared view moves as two pictures of itself, as on iOS: of the view that goes away and of the one that comes, each
 * with its children. The copy goes from where the one is drawn — its center, its size at the scale it is drawn at, its
 * angle and corner radius — to where the other is drawn in its screen ([FlightGeometry]), the angle along the shorter
 * arc; both pictures fill it, and the picture of the destination shows over the other on the way. Where a parent cuts
 * a view (`overflow: hidden`, a scroll view, the screen's edge), only the part that shows flies, the mask going from
 * one end's part to the other's ([FlightMask]), as on iOS. The pictures are drawing commands, taken before the views
 * hide: an avatar's letter grows sharp, and what changes in the views meanwhile shows when they are back.
 */
internal class ViewMorph private constructor(
    private val start: ViewPicture,
    private val end: ViewPicture,
) : FlightPicture() {
    private val flight = FlightGeometry(start.end, end.end)
    private val cut = FlightMask.of(start.visible, end.visible)?.let(::MaskCut)
    private var at = start.end
    private var progress = 0f
    private val rect = RectF()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // What is drawn over the mask keeps only the mask's pixels: the corners stay smooth (a clip path on the hardware
    // canvas is not anti-aliased).
    private val contentPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }

    override fun update(time: Float, progress: Float) {
        this.progress = progress
        at = flight.at(progress, time)
        cut?.update(time, progress)
    }

    override fun draw(canvas: Canvas) {
        rect.set(-at.width / 2, -at.height / 2, at.width / 2, at.height / 2)
        if (rect.isEmpty) return
        val radius = lerp(start.radius, end.radius)
        // The copy turns about its center, mirrored where a view is.
        canvas.translate(at.centerX, at.centerY)
        canvas.rotate(at.rotation)
        canvas.scale(at.mirrorX, at.mirrorY)
        canvas.saveLayer(rect, null)
        canvas.drawRoundRect(rect, radius, radius, maskPaint)
        cut?.draw(canvas, rect)
        canvas.saveLayer(rect, contentPaint)
        draw(canvas, start, start.alpha)
        draw(canvas, end, end.alpha * progress)
    }

    // A picture fills the copy, as on iOS.
    private fun draw(canvas: Canvas, view: ViewPicture, shown: Float) {
        val alpha = (shown * 255).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        val width = view.picture.width.toFloat()
        val height = view.picture.height.toFloat()
        val count = canvas.save()
        canvas.translate(rect.left, rect.top)
        canvas.scale(rect.width() / width, rect.height() / height)
        // The picture fades as a whole: its children and background don't show through each other.
        if (alpha < 255) canvas.saveLayerAlpha(0f, 0f, width, height, alpha)
        canvas.drawPicture(view.picture)
        canvas.restoreToCount(count)
    }

    private fun lerp(from: Float, to: Float) = from + (to - from) * progress

    companion object {
        /**
         * The morph of [from] into [to] in [layer], each cut by its parents up to the view of its screen ([fromRoot],
         * [toRoot]); none when a picture of either can't be taken.
         */
        fun of(from: View, to: View, fromRoot: View?, toRoot: View?, layer: View): ViewMorph? {
            val start = ViewPicture.of(from, LayerPlace.of(from, layer, fromRoot)) ?: return null
            val end = ViewPicture.of(to, LayerPlace.of(to, layer, toRoot)) ?: return null
            return ViewMorph(start, end)
        }
    }
}

// A view's picture, where it is drawn in the transition layer, its corner radius there, its opacity and the part of it
// that shows.
private class ViewPicture(
    val picture: Picture,
    val end: FlightEnd,
    val radius: Float,
    val alpha: Float,
    val visible: VisiblePart,
) {
    companion object {
        fun of(view: View, place: LayerPlace): ViewPicture? {
            if (view.width <= 0 || view.height <= 0) return null
            val picture = recordPicture(view) ?: return null
            val end = place.end(view.width, view.height)
            val radius = view.borderRadius
            return ViewPicture(
                picture, end, radius * end.scale, view.alpha, place.visible(view.width, view.height, radius),
            )
        }
    }
}

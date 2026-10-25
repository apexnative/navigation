package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import com.facebook.react.views.view.ReactViewGroup
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.utils.borderRadius
import kotlin.math.roundToInt

// A view with children moves as two pictures of itself, as on iOS: of the view that goes away and of the one that
// comes, each with its children. The copy goes from where the one is drawn — its center, its size at the scale it is
// drawn at, its angle and corner radius — to where the other is drawn in its screen (FlightGeometry), the angle along
// the shorter arc; both pictures fill it, and the picture of the destination shows over the other on the way. The
// pictures are drawing commands: an avatar's letter grows sharp. They are drawn in the overlay of the transition layer
// while `to` is hidden, and nothing else moves the pair. A view without children moves itself, its outline growing, as
// before.
class ViewChangeAnimator internal constructor(
    from: View,
    to: View,
    // Where `to` was in its screen: what the copy flies to.
    private val toStandIn: View?,
) : PropertyAnimatorCreator<ReactViewGroup>(from, to, ReactViewGroup::class.java) {
    private val pictures by lazy { pictures() }

    override fun shouldAnimateProperty(fromChild: ReactViewGroup, toChild: ReactViewGroup): Boolean =
            (fromChild.childCount > 0 || toChild.childCount > 0) && pictures != null

    override fun create(options: SharedElementTransitionOptions): Animator {
        val (start, end) = checkNotNull(pictures)
        val layer = to.parent as ViewGroup
        val drawable = ViewMorphDrawable(start, end)
        // Everything changes between the start and the end of the animation: a cancelled one ends too.
        return MoveAnimator().apply {
            onStart {
                drawable.setBounds(0, 0, layer.width, layer.height)
                layer.overlay.add(drawable)
                to.alpha = 0f
            }
            onFrame {
                drawable.update(it, curve.getInterpolation(it))
                layer.invalidate()
            }
            // A flight that landed where `to` shows only in its screen keeps its last picture: the transition takes the
            // layer away with it and shows `to` in its place.
            val holds = FlightEnd.holdsAtEnd(to, toStandIn, layer)
            onEnd {
                if (!holds || !landed) {
                    layer.overlay.remove(drawable)
                    layer.invalidate()
                    to.alpha = end.alpha
                }
            }
        }
    }

    // `to` is in the transition layer already: the pictures are drawn in its coordinates. `from` is where it is drawn
    // on the screen, `to` where it is drawn once back in its screen.
    private fun pictures(): Pair<ViewPicture, ViewPicture>? {
        val layer = to.parent as? ViewGroup ?: return null
        val start = ViewPicture.of(from as ReactViewGroup, LayerPlace.of(from, layer)) ?: return null
        val end = ViewPicture.of(to as ReactViewGroup, LayerPlace.of(to, layer, toStandIn)) ?: return null
        return start to end
    }
}

// A view's picture, where it is drawn in the transition layer, its corner radius there and its opacity.
private class ViewPicture(val picture: Picture, val end: FlightEnd, val radius: Float, val alpha: Float) {
    companion object {
        fun of(view: ReactViewGroup, place: LayerPlace): ViewPicture? {
            if (view.width <= 0 || view.height <= 0) return null
            val picture = recordPicture(view) ?: return null
            val end = place.end(view.width, view.height)
            return ViewPicture(picture, end, view.borderRadius * end.scale, view.alpha)
        }
    }
}

// The copy where the move has brought it: its center, size, angle and corner radius go from the start to the end along
// the transition's curve, and so does the picture of the end over the picture of the start.
private class ViewMorphDrawable(private val start: ViewPicture, private val end: ViewPicture) : Drawable() {
    private val flight = FlightGeometry(start.end, end.end, 0, 0)
    private var at = start.end
    private var progress = 0f
    private val rect = RectF()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // What is drawn over the mask keeps only the mask's pixels: the corners stay smooth (a clip path on the hardware
    // canvas is not anti-aliased).
    private val contentPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }

    /** The copy at [time] (the part of the time), [progress] the curve's value. */
    fun update(time: Float, progress: Float) {
        this.progress = progress
        at = flight.at(progress, time)
    }

    override fun draw(canvas: Canvas) {
        rect.set(-at.width / 2, -at.height / 2, at.width / 2, at.height / 2)
        if (rect.isEmpty) return
        val radius = lerp(start.radius, end.radius)
        // The copy turns about its center, mirrored where a view is.
        val count = canvas.save()
        canvas.translate(at.centerX, at.centerY)
        canvas.rotate(at.rotation)
        canvas.scale(at.mirrorX, at.mirrorY)
        canvas.saveLayer(rect, null)
        canvas.drawRoundRect(rect, radius, radius, maskPaint)
        canvas.saveLayer(rect, contentPaint)
        draw(canvas, start, start.alpha)
        draw(canvas, end, end.alpha * progress)
        canvas.restoreToCount(count)
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

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

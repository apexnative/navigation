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
import dev.apexnative.navigation.utils.ViewUtils
import dev.apexnative.navigation.utils.borderRadius
import dev.apexnative.navigation.utils.computeInheritedScale
import kotlin.math.roundToInt

// A view with children moves as two pictures of itself, as on iOS: of the view that goes away and of the one that
// comes, each with its children. The copy goes from the place, size and corner radius of the one to those of the other,
// both pictures fill it, and the picture of the destination shows over the other on the way. The pictures are drawing
// commands: an avatar's letter grows sharp. They are drawn in the overlay of the transition layer while `to` is hidden,
// and nothing else moves the pair. A view without children moves itself, its outline growing, as before.
class ViewChangeAnimator(from: View, to: View) : PropertyAnimatorCreator<ReactViewGroup>(from, to, ReactViewGroup::class.java) {
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
                drawable.progress = curve.getInterpolation(it)
                layer.invalidate()
            }
            onEnd {
                layer.overlay.remove(drawable)
                layer.invalidate()
                to.alpha = end.alpha
            }
        }
    }

    // `to` is in the transition layer already, at its margins (the coordinates of the other animators); `from` is where
    // it is on the screen, at the scale of its parents.
    private fun pictures(): Pair<ViewPicture, ViewPicture>? {
        if (to.parent !is ViewGroup) return null
        val toPlace = to.layoutParams as? ViewGroup.MarginLayoutParams ?: return null
        val fromPlace = ViewUtils.getLocationOnScreen(from)
            .apply { ViewUtils.getLocationOnScreen(to.parent as View).let { offset(-it.x, -it.y) } }
        val (scaleX, scaleY) = computeInheritedScale(from)
        val fromRect = RectF(0f, 0f, from.width * scaleX, from.height * scaleY).apply {
            offset(fromPlace.x.toFloat(), fromPlace.y.toFloat())
        }
        val toRect = RectF(0f, 0f, to.width.toFloat(), to.height.toFloat()).apply {
            offset(toPlace.leftMargin.toFloat(), toPlace.topMargin.toFloat())
        }
        val start = ViewPicture.of(from as ReactViewGroup, fromRect, scaleX) ?: return null
        val end = ViewPicture.of(to as ReactViewGroup, toRect, 1f) ?: return null
        return start to end
    }
}

// A view's picture, its place in the transition layer, its corner radius there and its opacity.
private class ViewPicture(val picture: Picture, val rect: RectF, val radius: Float, val alpha: Float) {
    companion object {
        fun of(view: ReactViewGroup, rect: RectF, scale: Float): ViewPicture? {
            if (view.width <= 0 || view.height <= 0) return null
            val picture = recordPicture(view) ?: return null
            return ViewPicture(picture, rect, view.borderRadius * scale, view.alpha)
        }
    }
}

// The copy where the move has brought it: its place, size and corner radius go from the start to the end along the
// transition's curve, and so does the picture of the end over the picture of the start.
private class ViewMorphDrawable(private val start: ViewPicture, private val end: ViewPicture) : Drawable() {
    var progress = 0f
    private val rect = RectF()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // What is drawn over the mask keeps only the mask's pixels: the corners stay smooth (a clip path on the hardware
    // canvas is not anti-aliased).
    private val contentPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }

    override fun draw(canvas: Canvas) {
        rect.set(
            lerp(start.rect.left, end.rect.left),
            lerp(start.rect.top, end.rect.top),
            lerp(start.rect.right, end.rect.right),
            lerp(start.rect.bottom, end.rect.bottom)
        )
        if (rect.isEmpty) return
        val radius = lerp(start.radius, end.radius)
        val count = canvas.saveLayer(rect, null)
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

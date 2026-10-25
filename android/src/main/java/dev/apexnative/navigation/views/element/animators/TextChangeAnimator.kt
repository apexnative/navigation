package dev.apexnative.navigation.views.element.animators

import android.animation.Animator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Picture
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.text.Layout
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.facebook.react.views.text.ReactTextView
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.utils.TextViewUtils
import dev.apexnative.navigation.views.animations.ValueInterpolation
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// The text moves as two pictures of itself, of the text that goes away and of the text that comes, the same text or
// another. Both keep their proportions, scaled by the font size from a point of the first line, and one turns into the
// other in a quarter of the move: at its start when the text grows, at its end when it shrinks, so the larger picture
// shows most of the way (as on iOS). Each text is where it is drawn, at the scale and angle its parents and its own
// properties give it; the angle goes along the shorter arc (FlightGeometry). The pictures are drawing commands, not
// pixels: they stay sharp at any scale and keep the colors, emoji and background of the text. They are drawn in the
// overlay of the transition layer while `to` is hidden.
class TextChangeAnimator internal constructor(
    from: View,
    to: View,
    // Where `to` was in its screen: what the text flies to.
    private val toStandIn: View?,
) : PropertyAnimatorCreator<ReactTextView>(from, to, ReactTextView::class.java) {
    private val pictures by lazy { pictures() }

    override fun shouldAnimateProperty(fromChild: ReactTextView, toChild: ReactTextView): Boolean {
        val (start, end) = pictures ?: return false
        return fromChild.text.toString() != toChild.text.toString() ||
            TextViewUtils.getTextSize(fromChild) != TextViewUtils.getTextSize(toChild) || start.drawnElsewhere(end)
    }

    override fun create(options: SharedElementTransitionOptions): Animator {
        val (start, end) = checkNotNull(pictures)
        val layer = to.parent as ViewGroup
        val drawable = TextMorphDrawable(start, end)
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
    // on the screen, `to` where it is drawn once back in its screen. Texts that line up the same way go from that point
    // of their first lines (the same text lies over itself); others from the start of the line, as on iOS: a centered
    // text no longer sticks out of a text at the start.
    private fun pictures(): Pair<TextPicture, TextPicture>? {
        val layer = to.parent as? ViewGroup ?: return null
        val fromLayout = (from as TextView).layout ?: return null
        val toLayout = (to as TextView).layout ?: return null
        val edge = lineEdge(fromLayout).takeIf { it == lineEdge(toLayout) } ?: LineEdge.START
        val start = TextPicture.of(from, LayerPlace.of(from, layer), edge) ?: return null
        val end = TextPicture.of(to, LayerPlace.of(to, layer, toStandIn), edge) ?: return null
        return start to end
    }
}

// A text's picture, the point of its first line it is scaled from (in the view) and where that point is drawn in the
// transition layer, its font size, the scale and angle it is drawn at, and its opacity.
private class TextPicture(
    val picture: Picture,
    val anchor: PointF,
    val position: PointF,
    val fontSize: Float,
    val scale: Float,
    val rotation: Float,
    val mirrorX: Float,
    val mirrorY: Float,
    val alpha: Float
) {
    /** The font size it shows at. */
    val shownSize: Float get() = fontSize * scale

    /** Whether [other] is drawn elsewhere, larger or smaller, or turned. */
    fun drawnElsewhere(other: TextPicture): Boolean =
        abs(position.x - other.position.x) > SAME_PLACE || abs(position.y - other.position.y) > SAME_PLACE ||
            abs(scale - other.scale) > SAME_SCALE || abs(rotation - other.rotation) > SAME_ANGLE ||
            mirrorX != other.mirrorX || mirrorY != other.mirrorY

    companion object {
        // Within half a pixel, a thousandth of the scale and a tenth of a degree: the same.
        private const val SAME_PLACE = 0.5f
        private const val SAME_SCALE = 0.001f
        private const val SAME_ANGLE = 0.1f

        fun of(view: TextView, place: LayerPlace, edge: LineEdge): TextPicture? {
            val layout = view.layout ?: return null
            val fontSize = TextViewUtils.getTextSize(view).takeIf { it > 0 } ?: view.textSize
            if (view.width <= 0 || view.height <= 0 || layout.lineCount == 0 || fontSize <= 0) return null
            // Without a picture (recording failed) the text goes to its place at once, as a different text does.
            val picture = recordPicture(view) ?: return null
            val anchor = PointF(
                view.totalPaddingLeft + edgeX(layout, edge),
                view.totalPaddingTop + layout.getLineBaseline(0).toFloat()
            )
            val position = place.point(anchor.x, anchor.y)
            val scale = min(abs(place.scaleX), abs(place.scaleY))
            return TextPicture(
                picture, anchor, PointF(position[0], position[1]), fontSize, scale, place.rotation,
                mirrorX = if (place.scaleX < 0) -1f else 1f,
                mirrorY = if (place.scaleY < 0) -1f else 1f,
                alpha = view.alpha,
            )
        }

        // That point of the first line, in the direction of the text.
        private fun edgeX(layout: Layout, edge: LineEdge): Float {
            val left = layout.getLineLeft(0)
            val right = layout.getLineRight(0)
            val rightToLeft = layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
            return when (edge) {
                LineEdge.CENTER -> (left + right) / 2
                LineEdge.START -> if (rightToLeft) right else left
                LineEdge.END -> if (rightToLeft) left else right
            }
        }
    }
}

// Where the lines of a text line up: their start, center or end, in the direction of the text.
private enum class LineEdge { START, CENTER, END }

// ALIGN_LEFT and ALIGN_RIGHT are hidden constants, for Gravity.LEFT and RIGHT.
private fun lineEdge(layout: Layout): LineEdge {
    val rightToLeft = layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
    return when (layout.getParagraphAlignment(0).name) {
        "ALIGN_CENTER" -> LineEdge.CENTER
        "ALIGN_OPPOSITE" -> LineEdge.END
        "ALIGN_LEFT" -> if (rightToLeft) LineEdge.END else LineEdge.START
        "ALIGN_RIGHT" -> if (rightToLeft) LineEdge.START else LineEdge.END
        else -> LineEdge.START
    }
}

// Both pictures where the move has brought the text: the font size it shows at, its angle and the point of the first
// line go from the start to the end along the transition's curve, the pictures change over by the clock.
private class TextMorphDrawable(private val start: TextPicture, private val end: TextPicture) : Drawable() {
    private val grows = end.shownSize >= start.shownSize
    private var progress = 0f
    private var time = 0f
    private var changeOver = 0f
    private var mirrorX = 1f
    private var mirrorY = 1f

    fun update(time: Float, progress: Float) {
        this.progress = progress
        this.time = time
        val changeOverStart = if (grows) 0f else 1f - CHANGE_OVER
        changeOver = ((time - changeOverStart) / CHANGE_OVER).coerceIn(0f, 1f)
    }

    override fun draw(canvas: Canvas) {
        val x = start.position.x + (end.position.x - start.position.x) * progress
        val y = start.position.y + (end.position.y - start.position.y) * progress
        val fontSize = start.shownSize + (end.shownSize - start.shownSize) * progress
        val rotation = ValueInterpolation.angle(start.rotation, end.rotation, progress, time)
        mirrorX = ValueInterpolation.number(start.mirrorX, end.mirrorX, progress, time)
        mirrorY = ValueInterpolation.number(start.mirrorY, end.mirrorY, progress, time)
        draw(canvas, start, x, y, fontSize, rotation, 1f - changeOver)
        draw(canvas, end, x, y, fontSize, rotation, changeOver)
    }

    private fun draw(
        canvas: Canvas,
        text: TextPicture,
        x: Float,
        y: Float,
        fontSize: Float,
        rotation: Float,
        shown: Float,
    ) {
        val alpha = (shown * text.alpha * 255).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        val scale = fontSize / text.fontSize
        val count = canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        canvas.scale(scale * mirrorX, scale * mirrorY)
        canvas.translate(-text.anchor.x, -text.anchor.y)
        // The picture fades as a whole: its letters and background don't show through each other.
        if (alpha < 255) {
            canvas.saveLayerAlpha(0f, 0f, text.picture.width.toFloat(), text.picture.height.toFloat(), alpha)
        }
        canvas.drawPicture(text.picture)
        canvas.restoreToCount(count)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val CHANGE_OVER = 0.25f
    }
}

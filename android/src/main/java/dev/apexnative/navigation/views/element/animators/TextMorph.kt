package dev.apexnative.navigation.views.element.animators

import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.PointF
import android.text.Layout
import android.view.View
import android.widget.TextView
import com.facebook.react.views.text.ReactTextView
import dev.apexnative.navigation.utils.TextViewUtils
import dev.apexnative.navigation.views.animations.ValueInterpolation
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The text moves as two pictures of itself, of the text that goes away and of the text that comes, the same text or
 * another. Both keep their proportions, scaled by the font size from a point of the first line, and one turns into the
 * other in a quarter of the move: at its start when the text grows, at its end when it shrinks, so the larger picture
 * shows most of the way (as on iOS). Each text is where it is drawn, at the scale and angle its parents and its own
 * properties give it; the angle goes along the shorter arc ([FlightGeometry]). The pictures are drawing commands, not
 * pixels: they stay sharp at any scale and keep the colors, emoji and background of the text.
 */
internal class TextMorph private constructor(private val start: TextPicture, private val end: TextPicture) :
    FlightPicture() {
    /** Whether the text changes on the way: another text, another size, or drawn elsewhere. */
    val changes: Boolean
        get() = start.text != end.text || start.fontSize != end.fontSize || start.drawnElsewhere(end)

    private val grows = end.shownSize >= start.shownSize
    private var progress = 0f
    private var time = 0f
    private var changeOver = 0f
    private var mirrorX = 1f
    private var mirrorY = 1f

    override fun update(time: Float, progress: Float) {
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

    companion object {
        private const val CHANGE_OVER = 0.25f

        /**
         * The morph of [from] into [to] in [layer]; none when a text isn't laid out or its picture can't be taken.
         * Texts that line up the same way go from that point of their first lines (the same text lies over itself);
         * others from the start of the line, as on iOS: a centered text no longer sticks out of a text at the start.
         */
        fun of(from: ReactTextView, to: ReactTextView, layer: View): TextMorph? {
            val fromLayout = from.layout ?: return null
            val toLayout = to.layout ?: return null
            val edge = lineEdge(fromLayout).takeIf { it == lineEdge(toLayout) } ?: LineEdge.START
            val start = TextPicture.of(from, LayerPlace.of(from, layer), edge) ?: return null
            val end = TextPicture.of(to, LayerPlace.of(to, layer), edge) ?: return null
            return TextMorph(start, end)
        }
    }
}

// A text's picture and its text, the point of its first line it is scaled from (in the view) and where that point is
// drawn in the transition layer, its font size, the scale and angle it is drawn at, and its opacity.
private class TextPicture(
    val text: String,
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
                view.text.toString(), picture, anchor, PointF(position[0], position[1]), fontSize, scale, place.rotation,
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

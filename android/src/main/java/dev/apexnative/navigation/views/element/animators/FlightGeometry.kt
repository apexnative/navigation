package dev.apexnative.navigation.views.element.animators

import android.view.View
import android.view.ViewGroup
import com.facebook.react.uimanager.ReactOverflowView
import dev.apexnative.navigation.utils.borderRadius
import dev.apexnative.navigation.views.animations.ValueInterpolation
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * How a view lies in its parent and is drawn there: its frame's corner, its translation, scale and rotation about its
 * pivot, and its parent's scroll. What `View.getMatrix` does, the scale first, without the tilts (`rotationX`,
 * `rotationY`) but for a half turn about x or y: React Native draws a mirror that way ([of]). Pixels and degrees,
 * clockwise.
 */
internal class ViewPlacement(
    val left: Float,
    val top: Float,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val pivotX: Float = 0f,
    val pivotY: Float = 0f,
    val parentScrollX: Float = 0f,
    val parentScrollY: Float = 0f,
) {
    /** The point [x], [y] of the view in its parent's coordinates. */
    fun toParent(x: Float, y: Float): FloatArray {
        val dx = (x - pivotX) * scaleX
        val dy = (y - pivotY) * scaleY
        val radians = Math.toRadians(rotation.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        return floatArrayOf(
            dx * cos - dy * sin + pivotX + translationX + left - parentScrollX,
            dx * sin + dy * cos + pivotY + translationY + top - parentScrollY,
        )
    }

    /**
     * The point [x], [y] of the parent in the view's coordinates: [toParent] undone. None when a scale of 0 flattens
     * the view.
     */
    fun toChild(x: Float, y: Float): FloatArray? {
        if (scaleX == 0f || scaleY == 0f) return null
        val dx = x - (pivotX + translationX + left - parentScrollX)
        val dy = y - (pivotY + translationY + top - parentScrollY)
        val radians = Math.toRadians(rotation.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        return floatArrayOf(
            (dx * cos + dy * sin) / scaleX + pivotX,
            (-dx * sin + dy * cos) / scaleY + pivotY,
        )
    }

    /** Whether the view shows mirrored: one of its scales is negative. */
    val mirrors: Boolean get() = scaleX * scaleY < 0

    companion object {
        // How close to half a turn a tilt counts as a flip, in degrees.
        private const val FLIP_TOLERANCE = 0.5f
        private const val HALF_TURN = 180f

        /** [view] as it lies now. */
        fun of(view: View): ViewPlacement {
            val parent = view.parent as? View
            return withTilts(
                left = view.left.toFloat(),
                top = view.top.toFloat(),
                translationX = view.translationX,
                translationY = view.translationY,
                scaleX = view.scaleX,
                scaleY = view.scaleY,
                rotation = view.rotation,
                rotationX = view.rotationX,
                rotationY = view.rotationY,
                pivotX = view.pivotX,
                pivotY = view.pivotY,
                parentScrollX = parent?.scrollX?.toFloat() ?: 0f,
                parentScrollY = parent?.scrollY?.toFloat() ?: 0f,
            )
        }

        /**
         * The placement of a view with these properties. React Native splits a mirror (`scaleX: -1`) into both scales
         * negated and half a turn about x: a view turns about z after its scale and tilts about y, then x, last, and
         * half a turn about x flips y after the turn, which is the same as flipping y first and turning the other way.
         * Other tilts are left out.
         */
        fun withTilts(
            left: Float,
            top: Float,
            translationX: Float,
            translationY: Float,
            scaleX: Float,
            scaleY: Float,
            rotation: Float,
            rotationX: Float,
            rotationY: Float,
            pivotX: Float,
            pivotY: Float,
            parentScrollX: Float,
            parentScrollY: Float,
        ): ViewPlacement {
            val flipsY = abs(abs(rotationX) - HALF_TURN) < FLIP_TOLERANCE
            val flipsX = abs(abs(rotationY) - HALF_TURN) < FLIP_TOLERANCE
            return ViewPlacement(
                left = left,
                top = top,
                translationX = translationX,
                translationY = translationY,
                scaleX = if (flipsX) -scaleX else scaleX,
                scaleY = if (flipsY) -scaleY else scaleY,
                rotation = if (flipsX != flipsY) -rotation else rotation,
                pivotX = pivotX,
                pivotY = pivotY,
                parentScrollX = parentScrollX,
                parentScrollY = parentScrollY,
            )
        }
    }
}

/**
 * Where the points of a view are drawn in a transition's layer: through its own placement and each parent's ([chain],
 * the view first, up to the top view of the window but not it), then moved by [offsetX], [offsetY] (where the top view
 * is from the layer's corner). The angle and the scale are those of the whole chain: a turned or scaled parent turns
 * or scales what is in it. [clips] are the parents that cut what the view draws, the innermost first.
 */
internal class LayerPlace(
    private val chain: List<ViewPlacement>,
    private val offsetX: Float,
    private val offsetY: Float,
    private val clips: List<Clip> = emptyList(),
) {
    /** The angle the view is drawn at, degrees, clockwise: a mirrored parent turns what is in it the other way. */
    val rotation: Float = chain.fold(0f) { inner, placement ->
        placement.rotation + if (placement.mirrors) -inner else inner
    }

    /** The scale it is drawn at along its own x (a mirror is negative). */
    val scaleX: Float = chain.fold(1f) { product, placement -> product * placement.scaleX }

    /** The scale it is drawn at along its own y. */
    val scaleY: Float = chain.fold(1f) { product, placement -> product * placement.scaleY }

    /** The point [x], [y] of the view in the layer. */
    fun point(x: Float, y: Float): FloatArray {
        var point = floatArrayOf(x, y)
        for (placement in chain) point = placement.toParent(point[0], point[1])
        return floatArrayOf(point[0] + offsetX, point[1] + offsetY)
    }

    /** The flight's end of a view of [width] × [height] drawn here. */
    fun end(width: Int, height: Int): FlightEnd {
        val center = point(width / 2f, height / 2f)
        return FlightEnd(
            centerX = center[0],
            centerY = center[1],
            width = width * abs(scaleX),
            height = height * abs(scaleY),
            rotation = rotation,
            mirrorX = if (scaleX < 0) -1f else 1f,
            mirrorY = if (scaleY < 0) -1f else 1f,
            scale = min(abs(scaleX), abs(scaleY)),
        )
    }

    /**
     * The part of a view of [width] × [height] drawn here that its [clips] leave visible, as iOS works it out: the
     * view's bounds cut by the bounds of each clip, brought into the view's coordinates (the box around them where a
     * turn tilts them), with the corners of the last clip that cut something. Uncut, it is the whole view with its own
     * [cornerRadius] (in its pixels).
     */
    fun visible(width: Int, height: Int, cornerRadius: Float): VisiblePart {
        val w = width.toFloat()
        val h = height.toFloat()
        val visible = floatArrayOf(0f, 0f, w, h)
        var radius = 0f
        for (clip in clips) {
            val cut = intersection(visible, boundsInView(clip))
            if (!cut.contentEquals(visible)) radius = clip.radius
            cut.copyInto(visible)
        }
        val clipped = abs(visible[0]) > SAME_RECT || abs(visible[1]) > SAME_RECT ||
            abs(visible[2] - w) > SAME_RECT || abs(visible[3] - h) > SAME_RECT
        val shown = if (clipped) visible else floatArrayOf(0f, 0f, w, h)
        val scaleX = abs(scaleX)
        val scaleY = abs(scaleY)
        return VisiblePart(
            left = (shown[0] - w / 2) * scaleX,
            top = (shown[1] - h / 2) * scaleY,
            right = (shown[2] - w / 2) * scaleX,
            bottom = (shown[3] - h / 2) * scaleY,
            radius = (if (clipped) radius else cornerRadius) * min(scaleX, scaleY),
            clipped = clipped,
        )
    }

    // The bounds of [clip] in the view's coordinates: the box around its corners brought down the chain. Empty when a
    // placement on the way flattens the view.
    private fun boundsInView(clip: Clip): FloatArray {
        val box = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (corner in 0 until CORNERS) {
            var point: FloatArray? = floatArrayOf(
                if (corner % 2 == 0) 0f else clip.width,
                if (corner < 2) 0f else clip.height,
            )
            for (level in clip.level - 1 downTo 0) point = point?.let { chain[level].toChild(it[0], it[1]) }
            point ?: return FloatArray(RECT)
            box[0] = min(box[0], point[0])
            box[1] = min(box[1], point[1])
            box[2] = max(box[2], point[0])
            box[3] = max(box[3], point[1])
        }
        return box
    }

    companion object {
        private const val CORNERS = 4
        private const val RECT = 4

        // How close two rects count as the same, in pixels: a visible part that is the whole view needs no mask.
        private const val SAME_RECT = 0.5f
        private const val VISIBLE = "visible"

        /**
         * Where [view] is drawn in [layer]. With [clipRoot] (the view of its screen), the parents up to it that cut
         * what they draw are its clips: as on iOS, the parents above the screen don't count.
         */
        fun of(view: View, layer: View, clipRoot: View? = null): LayerPlace {
            val chain = arrayListOf(ViewPlacement.of(view))
            val clips = ArrayList<Clip>()
            var clipping = clipRoot != null
            var parent = view.parent as? View
            while (parent != null) {
                if (clipping) {
                    clipOf(parent, chain.size)?.let(clips::add)
                    clipping = parent !== clipRoot
                }
                val above = parent.parent as? View ?: break
                chain += ViewPlacement.of(parent)
                parent = above
            }
            // The window's top view (no parent view) lies where it is on the screen, with no properties of its own.
            val base = parent ?: view
            val screen = IntArray(2)
            base.getLocationOnScreen(screen)
            val origin = IntArray(2)
            layer.getLocationOnScreen(origin)
            return LayerPlace(chain, (screen[0] - origin[0]).toFloat(), (screen[1] - origin[1]).toFloat(), clips)
        }

        /** The rect [a] cut by [b] (left, top, right, bottom); an empty one at 0, 0 when they don't meet, as on iOS. */
        internal fun intersection(a: FloatArray, b: FloatArray): FloatArray {
            val left = max(a[0], b[0])
            val top = max(a[1], b[1])
            val right = min(a[2], b[2])
            val bottom = min(a[3], b[3])
            return if (left < right && top < bottom) floatArrayOf(left, top, right, bottom) else FloatArray(RECT)
        }

        // The clip of [parent], at [level] of the chain, when it cuts what it draws to its bounds: a React Native view
        // whose `overflow` isn't visible (a scroll view's is `scroll` unless set) with its corners, and any view whose
        // own parent keeps its children in (`clipChildren`: Android cuts each child to its bounds, square), as the
        // screen's own views do. React Native's views let their children out, so their `overflow` alone counts.
        private fun clipOf(parent: View, level: Int): Clip? {
            val overflows = parent is ReactOverflowView && parent.overflow.let { it != null && it != VISIBLE }
            val keptIn = (parent.parent as? ViewGroup)?.clipChildren == true
            if (!overflows && !keptIn) return null
            val radius = if (overflows) parent.borderRadius else 0f
            return Clip(level, parent.width.toFloat(), parent.height.toFloat(), radius)
        }
    }
}

/**
 * A parent that cuts what a view draws to its bounds: its [level] in the view's chain (1 the view's parent: its
 * coordinates come down to the view's through the placements before it), its size and its corner radius. Pixels.
 */
internal class Clip(val level: Int, val width: Float, val height: Float, val radius: Float = 0f)

/**
 * The part of a shared element's copy that shows at one end of its flight: left, top, right, bottom from the copy's
 * center at the size it is drawn at, and its corner radius there. [clipped]: a parent cuts the view; else it is the
 * whole copy with its own corners.
 */
internal class VisiblePart(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
    val clipped: Boolean,
)

/**
 * The mask of a copy cut by its views' parents, as on iOS: a rounded rect that goes from the part of the copy that
 * shows at one end to the part that shows at the other, evenly.
 */
internal class FlightMask private constructor(private val from: VisiblePart, private val to: VisiblePart) {
    /**
     * Fills [out] with the mask at [fraction] of the way (the curve's value), [time] the part of the time: left, top,
     * right, bottom from the copy's center, then the corner radius.
     */
    fun at(fraction: Float, time: Float, out: FloatArray) {
        out[0] = ValueInterpolation.number(from.left, to.left, fraction, time)
        out[1] = ValueInterpolation.number(from.top, to.top, fraction, time)
        out[2] = ValueInterpolation.number(from.right, to.right, fraction, time)
        out[3] = ValueInterpolation.number(from.bottom, to.bottom, fraction, time)
        out[4] = ValueInterpolation.number(from.radius, to.radius, fraction, time)
    }

    companion object {
        /** The values [at] fills. */
        const val SIZE = 5

        /** The mask between [from] and [to]; none when neither is cut, and the copy shows whole. */
        fun of(from: VisiblePart, to: VisiblePart): FlightMask? =
            if (from.clipped || to.clipped) FlightMask(from, to) else null
    }
}

/**
 * One end of a shared element's flight in its layer: where its center is drawn, the size it shows (its own at the
 * scale of its chain), its angle, whether it shows mirrored, and its scale (what its corners shrink by). Pixels.
 */
internal class FlightEnd(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
    val mirrorX: Float = 1f,
    val mirrorY: Float = 1f,
    val scale: Float = 1f,
)

/**
 * Where a shared element flies in its transition's layer: from where `from` is drawn to where `to` is drawn in its
 * screen, both through their own and their parents' translation, scale and rotation. The center, the size and the
 * mirror go evenly, the angle along the shorter arc (as on iOS).
 */
internal class FlightGeometry(val from: FlightEnd, val to: FlightEnd) {
    /** The flight at [fraction] of the way (the curve's value), [time] the part of the time. */
    fun at(fraction: Float, time: Float): FlightEnd = FlightEnd(
        centerX = ValueInterpolation.number(from.centerX, to.centerX, fraction, time),
        centerY = ValueInterpolation.number(from.centerY, to.centerY, fraction, time),
        width = ValueInterpolation.number(from.width, to.width, fraction, time),
        height = ValueInterpolation.number(from.height, to.height, fraction, time),
        rotation = ValueInterpolation.angle(from.rotation, to.rotation, fraction, time),
        mirrorX = ValueInterpolation.number(from.mirrorX, to.mirrorX, fraction, time),
        mirrorY = ValueInterpolation.number(from.mirrorY, to.mirrorY, fraction, time),
        scale = ValueInterpolation.number(from.scale, to.scale, fraction, time),
    )
}

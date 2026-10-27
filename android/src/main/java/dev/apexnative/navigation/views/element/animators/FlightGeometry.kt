package dev.apexnative.navigation.views.element.animators

import android.view.View
import dev.apexnative.navigation.views.animations.ValueInterpolation
import kotlin.math.abs
import kotlin.math.cos
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
 * or scales what is in it.
 */
internal class LayerPlace(
    private val chain: List<ViewPlacement>,
    private val offsetX: Float,
    private val offsetY: Float,
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

    companion object {
        /** Where [view] is drawn in [layer]. */
        fun of(view: View, layer: View): LayerPlace {
            val chain = arrayListOf(ViewPlacement.of(view))
            var parent = view.parent as? View
            while (parent != null) {
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
            return LayerPlace(chain, (screen[0] - origin[0]).toFloat(), (screen[1] - origin[1]).toFloat())
        }
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

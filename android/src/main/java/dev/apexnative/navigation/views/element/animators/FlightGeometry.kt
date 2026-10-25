package dev.apexnative.navigation.views.element.animators

import android.view.View
import android.view.ViewGroup
import dev.apexnative.navigation.views.animations.ValueInterpolation
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
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

        /** [view] as it lies now; [frameOf] the view whose frame and parent it takes (a stand-in where it was). */
        fun of(view: View, frameOf: View = view): ViewPlacement {
            val parent = frameOf.parent as? View
            return withTilts(
                left = frameOf.left.toFloat(),
                top = frameOf.top.toFloat(),
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
        /**
         * Where [view] is drawn in [layer]. With [standIn], [view] counts as lying where that one lies (its frame and
         * parents) with its own properties: an element that moved into the layer, at the place it goes back to.
         */
        fun of(view: View, layer: View, standIn: View? = null): LayerPlace {
            val placed = standIn ?: view
            val chain = arrayListOf(ViewPlacement.of(view, placed))
            var parent = placed.parent as? View
            while (parent != null) {
                val above = parent.parent as? View ?: break
                chain += ViewPlacement.of(parent)
                parent = above
            }
            // The window's top view (no parent view) lies where it is on the screen, with no properties of its own.
            val base = parent ?: placed
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
) {
    /** Whether [other] is drawn at the same place, size, angle and mirror: within half a pixel and 0.1°. */
    fun sameAs(other: FlightEnd): Boolean =
        abs(centerX - other.centerX) < SAME_PLACE && abs(centerY - other.centerY) < SAME_PLACE &&
            abs(width - other.width) < SAME_PLACE && abs(height - other.height) < SAME_PLACE &&
            abs(rotation - other.rotation) < SAME_ANGLE && mirrorX == other.mirrorX && mirrorY == other.mirrorY

    companion object {
        private const val SAME_PLACE = 0.5f
        private const val SAME_ANGLE = 0.1f

        /**
         * Whether `to` shows elsewhere in its screen than in the layer with its own properties alone ([standIn] is
         * where it was): a parent of its scales or turns it. A flight that landed then keeps it where it lands until
         * the transition gives it back, or it would jump to its place in the layer meanwhile.
         */
        fun holdsAtEnd(to: View, standIn: View?, layer: View): Boolean {
            if (standIn == null) return false
            val inScreen = LayerPlace.of(to, layer, standIn).end(to.width, to.height)
            return !inScreen.sameAs(LayerPlace.of(to, layer).end(to.width, to.height))
        }
    }
}

/**
 * Where a shared element flies in its transition's layer: from where `from` is drawn to where `to` is drawn once it
 * is back in its screen, both through their own and their parents' translation, scale and rotation. The center, the
 * size and the mirror go evenly, the angle along the shorter arc (as on iOS). `to` lies in the layer at its margins
 * ([toLeft], [toTop]) meanwhile.
 */
internal class FlightGeometry(
    val from: FlightEnd,
    val to: FlightEnd,
    val toLeft: Int,
    val toTop: Int,
    /** Whether `to` keeps the flight's last frame at the end ([FlightEnd.holdsAtEnd]). */
    val holdsAtEnd: Boolean = false,
) {
    /** The width and height `to` is laid out at meanwhile: the larger of the two sizes. */
    val largest: Pair<Int, Int>
        get() = maxOf(from.width, to.width).roundToInt() to maxOf(from.height, to.height).roundToInt()

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

    /**
     * The translation and pivot that draw a view laid out at its margins with [width] × [height] (or showing that part
     * of a larger layout from its corner) at [frame]'s center and angle, unscaled: x, y of the translation, then of
     * the pivot.
     */
    fun moveOf(frame: FlightEnd, width: Int, height: Int): FloatArray = floatArrayOf(
        frame.centerX - (toLeft + width / 2f),
        frame.centerY - (toTop + height / 2f),
        width / 2f,
        height / 2f,
    )

    companion object {
        /**
         * The flight of [from] to [to], which is in [layer] already in place of [toStandIn] (the stub where it was);
         * none when [to] has no place there.
         */
        fun of(from: View, to: View, toStandIn: View?, layer: View): FlightGeometry? {
            val place = to.layoutParams as? ViewGroup.MarginLayoutParams ?: return null
            val fromEnd = LayerPlace.of(from, layer).end(from.width, from.height)
            val toEnd = LayerPlace.of(to, layer, toStandIn).end(to.width, to.height)
            val holds = FlightEnd.holdsAtEnd(to, toStandIn, layer)
            return FlightGeometry(fromEnd, toEnd, place.leftMargin, place.topMargin, holds)
        }
    }
}

/**
 * The properties a moving `to` is drawn with in the layer when its flight starts, put back when it ends: the
 * flight draws it unscaled (but mirrored where an end is) and untilted at the flight's angle about the middle of what
 * shows.
 */
internal class MovedProperties(private val view: View) {
    private val translationX = view.translationX
    private val translationY = view.translationY
    private val scaleX = view.scaleX
    private val scaleY = view.scaleY
    private val rotation = view.rotation
    private val rotationX = view.rotationX
    private val rotationY = view.rotationY
    private val pivotX = view.pivotX
    private val pivotY = view.pivotY
    private val ownWidth = view.width
    private val ownHeight = view.height

    /** [view] at [frame], laid out at [width] × [height] from its margins (or showing that part from its corner). */
    fun show(geometry: FlightGeometry, frame: FlightEnd, width: Int, height: Int) {
        val move = geometry.moveOf(frame, width, height)
        view.pivotX = move[2]
        view.pivotY = move[3]
        view.translationX = move[0]
        view.translationY = move[1]
        view.scaleX = frame.mirrorX
        view.scaleY = frame.mirrorY
        view.rotation = frame.rotation
        view.rotationX = 0f
        view.rotationY = 0f
    }

    /**
     * [view] at the end of a flight that [landed]: kept at the flight's last frame, laid out by [layOut] at the size it
     * shows, when the geometry holds it there; else as it was at the start, laid out by [layOut] at its own size.
     */
    fun end(geometry: FlightGeometry, landed: Boolean, layOut: (width: Int, height: Int) -> Unit) {
        if (geometry.holdsAtEnd && landed) {
            val width = geometry.to.width.roundToInt()
            val height = geometry.to.height.roundToInt()
            layOut(width, height)
            show(geometry, geometry.to, width, height)
        } else {
            restore()
            layOut(ownWidth, ownHeight)
        }
    }

    /** [view] as it was at the start. */
    fun restore() {
        view.translationX = translationX
        view.translationY = translationY
        view.scaleX = scaleX
        view.scaleY = scaleY
        view.rotation = rotation
        view.rotationX = rotationX
        view.rotationY = rotationY
        view.pivotX = pivotX
        view.pivotY = pivotY
    }
}

/** An outline of the size and corners a flying view shows meanwhile: its own bounds are larger. */
internal class FlightOutline : android.view.ViewOutlineProvider() {
    var width = 0
    var height = 0
    var radius = 0f

    override fun getOutline(view: View, outline: android.graphics.Outline) {
        outline.setRoundRect(0, 0, width, height, radius)
    }
}

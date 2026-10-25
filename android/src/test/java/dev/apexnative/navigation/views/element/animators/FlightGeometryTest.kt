package dev.apexnative.navigation.views.element.animators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Where a shared element flies: each end is where the view is drawn through its own and its parents' translation,
 * scale and rotation; the angle goes along the shorter arc and ends exactly on `to`'s; a moving `to` turns about the
 * middle of what shows.
 */
class FlightGeometryTest {
    @Test
    fun aViewTurnsAboutItsPivot() {
        val placement = ViewPlacement(left = 10f, top = 20f, rotation = 90f, pivotX = 50f, pivotY = 25f)
        assertPoint(60f, 45f, placement.toParent(50f, 25f))
        // The top left corner of a 100 × 50 view turned a quarter clockwise is at the top right of its turned frame.
        assertPoint(85f, -5f, placement.toParent(0f, 0f))
    }

    @Test
    fun aScaledParentMovesAndShrinksWhatIsInIt() {
        val child = ViewPlacement(left = 100f, top = 100f, pivotX = 50f, pivotY = 50f)
        val parent = ViewPlacement(left = 0f, top = 0f, scaleX = 0.5f, scaleY = 0.5f, pivotX = 100f, pivotY = 100f)
        val end = LayerPlace(listOf(child, parent), offsetX = 5f, offsetY = 7f).end(100, 100)
        assertEquals(130f, end.centerX, DELTA)
        assertEquals(132f, end.centerY, DELTA)
        assertEquals(50f, end.width, DELTA)
        assertEquals(50f, end.height, DELTA)
        assertEquals(0.5f, end.scale, DELTA)
        assertEquals(0f, end.rotation, DELTA)
    }

    @Test
    fun theAnglesOfTheChainAddUpAndTheScrollIsTakenOff() {
        val child =
            ViewPlacement(left = 0f, top = 300f, rotation = 30f, pivotX = 20f, pivotY = 10f, parentScrollY = 200f)
        val parent = ViewPlacement(left = 0f, top = 0f, rotation = 15f, pivotX = 0f, pivotY = 0f)
        val place = LayerPlace(listOf(child, parent), 0f, 0f)
        assertEquals(45f, place.rotation, DELTA)
        // The child's center is at (20, 110) in the parent, turned 15° about the parent's corner.
        val center = place.point(20f, 10f)
        val radians = Math.toRadians(15.0)
        assertEquals((20 * Math.cos(radians) - 110 * Math.sin(radians)).toFloat(), center[0], DELTA)
        assertEquals((20 * Math.sin(radians) + 110 * Math.cos(radians)).toFloat(), center[1], DELTA)
    }

    @Test
    fun aReactNativeMirrorIsAMirror() {
        // React Native sets `scaleX: -1` as both scales negated and half a turn about x.
        val mirror = rn(scaleX = -2f, scaleY = -2f, rotationX = 180f)
        val end = LayerPlace(listOf(mirror), 0f, 0f).end(20, 20)
        assertEquals(40f, end.width, DELTA)
        assertEquals(-1f, end.mirrorX, 0f)
        assertEquals(1f, end.mirrorY, 0f)
        assertPoint(-2f, 0f, mirror.toParent(1f, 0f))
        assertPoint(0f, 2f, mirror.toParent(0f, 1f))
    }

    @Test
    fun aMirrorTurnedIsDrawnAsReactNativeMeantIt() {
        // `[{ scaleX: -1 }, { rotate: '30deg' }]`: turned 30°, then mirrored. React Native splits it into the scales
        // −1, −1, half a turn about x and 30° about z.
        val placement = rn(scaleX = -1f, scaleY = -1f, rotationX = -180f, rotation = 30f)
        val radians = Math.toRadians(30.0)
        assertPoint(-Math.cos(radians).toFloat(), Math.sin(radians).toFloat(), placement.toParent(1f, 0f))
        assertPoint(Math.sin(radians).toFloat(), Math.cos(radians).toFloat(), placement.toParent(0f, 1f))
    }

    @Test
    fun aMirroredParentTurnsWhatIsInItTheOtherWay() {
        val child = ViewPlacement(left = 0f, top = 0f, rotation = 30f)
        val parent = rn(scaleX = -1f, scaleY = -1f, rotationX = 180f)
        val place = LayerPlace(listOf(child, parent), 0f, 0f)
        assertEquals(-30f, place.rotation, DELTA)
        assertEquals(-1f, place.end(10, 10).mirrorX, 0f)
    }

    @Test
    fun aViewWhoseParentScalesItHoldsItsEnd() {
        val own = FlightEnd(centerX = 50f, centerY = 50f, width = 100f, height = 100f, rotation = 0f)
        assertTrue(own.sameAs(FlightEnd(50.2f, 49.9f, 100f, 100f, 0.05f)))
        assertFalse(own.sameAs(FlightEnd(50f, 50f, 50f, 50f, 0f)))
        assertFalse(own.sameAs(FlightEnd(50f, 50f, 100f, 100f, 0f, mirrorX = -1f)))
    }

    @Test
    fun theAngleTakesTheShorterArcAndEndsOnTo() {
        assertEquals(45f, flight(0f, 90f).at(0.5f, 0.5f).rotation, DELTA)
        assertEquals(90f, flight(0f, 180f).at(0.5f, 0.5f).rotation, DELTA)
        assertEquals(0f, flight(45f, -45f).at(0.5f, 0.5f).rotation, DELTA)
        // 270° is −90°: the element turns back a quarter, not on three quarters.
        assertEquals(-45f, flight(0f, 270f).at(0.5f, 0.5f).rotation, DELTA)
        assertEquals(180f, abs(flight(170f, -170f).at(0.5f, 0.5f).rotation), DELTA)
        // The ends are exactly the views' own angles.
        assertEquals(0f, flight(0f, 270f).at(0f, 0f).rotation, 0f)
        assertEquals(270f, flight(0f, 270f).at(1f, 1f).rotation, 0f)
    }

    @Test
    fun theCenterAndTheSizeGoEvenly() {
        val geometry = FlightGeometry(
            FlightEnd(centerX = 0f, centerY = 0f, width = 100f, height = 50f, rotation = 0f),
            FlightEnd(centerX = 200f, centerY = 100f, width = 300f, height = 150f, rotation = 0f),
            toLeft = 50,
            toTop = 25,
        )
        val half = geometry.at(0.5f, 0.5f)
        assertEquals(100f, half.centerX, DELTA)
        assertEquals(50f, half.centerY, DELTA)
        assertEquals(200f, half.width, DELTA)
        assertEquals(100f, half.height, DELTA)
        assertEquals(300 to 150, geometry.largest)
    }

    @Test
    fun aMovingViewTurnsAboutTheMiddleOfWhatShows() {
        val geometry = flight(0f, 0f)
        val frame = FlightEnd(centerX = 60f, centerY = 45f, width = 100f, height = 50f, rotation = 30f)
        // Laid out at 100 × 50 from its margins (10, 20): no translation, the pivot in its middle.
        val move = geometry.moveOf(frame, 100, 50)
        assertEquals(0f, move[0], DELTA)
        assertEquals(0f, move[1], DELTA)
        assertEquals(50f, move[2], DELTA)
        assertEquals(25f, move[3], DELTA)
        // Showing 40 × 20 of a larger layout from its corner: the middle of those.
        val part = geometry.moveOf(frame, 40, 20)
        assertEquals(30f, part[0], DELTA)
        assertEquals(15f, part[1], DELTA)
        assertEquals(20f, part[2], DELTA)
        assertEquals(10f, part[3], DELTA)
    }

    private fun flight(from: Float, to: Float) = FlightGeometry(
        FlightEnd(0f, 0f, 10f, 10f, from),
        FlightEnd(0f, 0f, 10f, 10f, to),
        toLeft = 10,
        toTop = 20,
    )

    private fun rn(
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotation: Float = 0f,
        rotationX: Float = 0f,
    ) = ViewPlacement.withTilts(
        left = 0f, top = 0f, translationX = 0f, translationY = 0f, scaleX = scaleX, scaleY = scaleY,
        rotation = rotation, rotationX = rotationX, rotationY = 0f, pivotX = 0f, pivotY = 0f,
        parentScrollX = 0f, parentScrollY = 0f,
    )

    private fun assertPoint(x: Float, y: Float, point: FloatArray) {
        assertEquals(x, point[0], DELTA)
        assertEquals(y, point[1], DELTA)
    }

    private companion object {
        const val DELTA = 0.001f
    }
}

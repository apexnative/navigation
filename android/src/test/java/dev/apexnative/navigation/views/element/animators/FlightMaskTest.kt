package dev.apexnative.navigation.views.element.animators

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The part of a shared element's copy that shows, as on iOS: the view's bounds cut by its clipping parents, brought
 * into its coordinates through the chain, then from the copy's center at the size it is drawn at; the mask goes from
 * one end's part to the other's.
 */
class FlightMaskTest {
    @Test
    fun aPointBroughtDownIsWhereItCameFrom() {
        val placement = ViewPlacement(
            left = 10f, top = 20f, translationX = 3f, translationY = -4f, scaleX = -2f, scaleY = 0.5f,
            rotation = 30f, pivotX = 50f, pivotY = 25f, parentScrollX = 7f, parentScrollY = 11f,
        )
        val parent = placement.toParent(12f, 34f)
        val back = placement.toChild(parent[0], parent[1])!!
        assertEquals(12f, back[0], DELTA)
        assertEquals(34f, back[1], DELTA)
        assertNull(ViewPlacement(left = 0f, top = 0f, scaleY = 0f).toChild(1f, 1f))
    }

    @Test
    fun aCellHalfOutOfItsListShowsItsTopHalf() {
        // A 100 × 100 cell at 450 in a list's content scrolled by 100, in a list 400 high: its top 50 show.
        val cell = ViewPlacement(left = 0f, top = 450f)
        val content = ViewPlacement(left = 0f, top = 0f, parentScrollY = 100f)
        val place = LayerPlace(listOf(cell, content), 0f, 0f, listOf(Clip(level = 2, width = 400f, height = 400f)))
        val part = place.visible(100, 100, cornerRadius = 12f)
        assertTrue(part.clipped)
        assertRect(floatArrayOf(-50f, -50f, 50f, 0f), part)
        // The list's square corners, not the cell's own.
        assertEquals(0f, part.radius, DELTA)
    }

    @Test
    fun aParentThatHoldsTheWholeViewCutsNothing() {
        val view = ViewPlacement(left = 10f, top = 10f)
        val clips = listOf(Clip(level = 1, width = 200f, height = 200f, radius = 30f))
        val part = LayerPlace(listOf(view), 0f, 0f, clips).visible(100, 50, cornerRadius = 8f)
        assertFalse(part.clipped)
        assertRect(floatArrayOf(-50f, -25f, 50f, 25f), part)
        assertEquals(8f, part.radius, DELTA)
    }

    @Test
    fun theCornersAreThoseOfTheLastParentThatCut() {
        // The inner parent cuts the view's right side, the outer one, rounded, holds it whole: the inner one's corners.
        val view = ViewPlacement(left = 0f, top = 0f)
        val inner = ViewPlacement(left = 0f, top = 0f)
        val clips = listOf(
            Clip(level = 1, width = 60f, height = 100f, radius = 4f),
            Clip(level = 2, width = 500f, height = 500f, radius = 40f),
        )
        val part = LayerPlace(listOf(view, inner), 0f, 0f, clips).visible(100, 100, cornerRadius = 0f)
        assertRect(floatArrayOf(-50f, -50f, 10f, 50f), part)
        assertEquals(4f, part.radius, DELTA)
    }

    @Test
    fun thePartGrowsWithTheScaleTheViewIsDrawnAt() {
        // Drawn twice as large from its corner, a 100 × 100 view shows its top left quarter in a 100 × 100 parent.
        val view = ViewPlacement(left = 0f, top = 0f, scaleX = 2f, scaleY = 2f)
        val clips = listOf(Clip(level = 1, width = 100f, height = 100f, radius = 8f))
        val part = LayerPlace(listOf(view), 0f, 0f, clips).visible(100, 100, cornerRadius = 4f)
        assertRect(floatArrayOf(-100f, -100f, 0f, 0f), part)
        assertEquals(16f, part.radius, DELTA)
    }

    @Test
    fun aTurnedViewIsCutWhereItsTurnedPartLeavesTheParent() {
        // A 100 × 20 view at the parent's corner, turned a quarter clockwise about its center, stands from y −40 to 60:
        // its first 40 pixels (now on top) are above the parent.
        val view = ViewPlacement(left = 0f, top = 0f, rotation = 90f, pivotX = 50f, pivotY = 10f)
        val clips = listOf(Clip(level = 1, width = 100f, height = 100f))
        val part = LayerPlace(listOf(view), 0f, 0f, clips).visible(100, 20, cornerRadius = 0f)
        assertRect(floatArrayOf(-10f, -10f, 50f, 10f), part)
    }

    @Test
    fun aParentMovedAsideCutsInItsOwnFrame() {
        // The view starts 50 to the parent's left: its right half shows.
        val view = ViewPlacement(left = -50f, top = 0f)
        val clips = listOf(Clip(level = 1, width = 100f, height = 20f))
        val part = LayerPlace(listOf(view), 0f, 0f, clips).visible(100, 20, cornerRadius = 0f)
        assertRect(floatArrayOf(0f, -10f, 50f, 10f), part)
    }

    @Test
    fun aViewOutOfSightShowsNothing() {
        val view = ViewPlacement(left = 0f, top = 500f)
        val clips = listOf(Clip(level = 1, width = 100f, height = 400f))
        val part = LayerPlace(listOf(view), 0f, 0f, clips).visible(100, 100, cornerRadius = 0f)
        assertTrue(part.clipped)
        // An empty part at the view's corner, as iOS's `CGRectZero`.
        assertRect(floatArrayOf(-50f, -50f, -50f, -50f), part)
    }

    @Test
    fun aFlattenedChainShowsNothing() {
        val view = ViewPlacement(left = 0f, top = 0f)
        val parent = ViewPlacement(left = 0f, top = 0f, scaleX = 0f)
        val clips = listOf(Clip(level = 2, width = 400f, height = 400f))
        val part = LayerPlace(listOf(view, parent), 0f, 0f, clips).visible(100, 100, cornerRadius = 0f)
        assertTrue(part.clipped)
        assertEquals(part.left, part.right, DELTA)
    }

    @Test
    fun withinHalfAPixelTheViewIsWhole() {
        val view = ViewPlacement(left = 0f, top = 0.3f)
        val clips = listOf(Clip(level = 1, width = 100f, height = 100f, radius = 9f))
        val part = LayerPlace(listOf(view), 0f, 0f, clips).visible(100, 100, cornerRadius = 2f)
        assertFalse(part.clipped)
        assertRect(floatArrayOf(-50f, -50f, 50f, 50f), part)
        assertEquals(2f, part.radius, DELTA)
    }

    @Test
    fun aCopyThatShowsWholeAtBothEndsHasNoMask() {
        val whole = VisiblePart(-50f, -50f, 50f, 50f, radius = 0f, clipped = false)
        assertNull(FlightMask.of(whole, whole))
    }

    @Test
    fun theMaskGoesEvenlyFromOneEndsPartToTheOthers() {
        val cut = VisiblePart(-50f, -50f, 50f, 0f, radius = 0f, clipped = true)
        val whole = VisiblePart(-100f, -100f, 100f, 100f, radius = 20f, clipped = false)
        val mask = FlightMask.of(cut, whole)!!
        val out = FloatArray(FlightMask.SIZE)
        mask.at(0.5f, 0.5f, out)
        assertArrayEquals(floatArrayOf(-75f, -75f, 75f, 50f, 10f), out, DELTA)
        // The ends are exactly the parts, whatever the curve gave.
        mask.at(0.9f, 0f, out)
        assertArrayEquals(floatArrayOf(-50f, -50f, 50f, 0f, 0f), out, DELTA)
        mask.at(1.1f, 1f, out)
        assertArrayEquals(floatArrayOf(-100f, -100f, 100f, 100f, 20f), out, DELTA)
    }

    @Test
    fun rectsThatDontMeetCutToAnEmptyOne() {
        assertArrayEquals(
            floatArrayOf(0f, 0f, 0f, 0f),
            LayerPlace.intersection(floatArrayOf(0f, 0f, 10f, 10f), floatArrayOf(20f, 0f, 30f, 10f)),
            0f,
        )
        assertArrayEquals(
            floatArrayOf(5f, 0f, 10f, 10f),
            LayerPlace.intersection(floatArrayOf(0f, 0f, 10f, 10f), floatArrayOf(5f, -5f, 30f, 30f)),
            0f,
        )
    }

    private fun assertRect(expected: FloatArray, part: VisiblePart) {
        assertArrayEquals(expected, floatArrayOf(part.left, part.top, part.right, part.bottom), DELTA)
    }

    private companion object {
        const val DELTA = 0.001f
    }
}

package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.options.SheetDetent
import dev.apexnative.navigation.options.SheetDetent.Large
import dev.apexnative.navigation.options.SheetDetent.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stops a sheet keeps of its detents, how the Material behavior rests at them, and where a shown sheet moves: only
 * to a `selectedDetent` that arrives, or off a stop that is gone.
 */
class SheetStopsTest {
    private val stops = listOf(Medium, Large)

    // A layout 2000 px tall: medium at 1000, large at 1900, a number in px as it is.
    private val heightOf: (SheetDetent) -> Int = {
        when (it) {
            Medium -> 1000
            Large -> 1900
            SheetDetent.Content -> 600
            is SheetDetent.Height -> it.dp.toInt().coerceAtMost(1900)
        }
    }

    private fun height(px: Int) = SheetDetent.Height(px.toDouble())

    @Test
    fun theStopsAreTheDetentsByHeight() {
        val detents = listOf(Large, height(300), Medium)
        assertEquals(listOf(height(300), Medium, Large), SheetStops.stops(detents, heightOf, null))
    }

    @Test
    fun aHeightTwiceIsOneStopTheFirstTheOptionsGive() {
        assertEquals(listOf(height(300), Large), SheetStops.stops(listOf(Large, height(300), Large), heightOf, null))
        // 2500 is capped at large: the one the options give first stays.
        assertEquals(listOf(height(2500)), SheetStops.stops(listOf(height(2500), Large), heightOf, null))
    }

    @Test
    fun anyDetentStandsBetweenTheLowestAndTheHighest() {
        assertEquals(
            listOf(height(200), height(400), Large),
            SheetStops.stops(listOf(height(200), height(400), Large), heightOf, null),
        )
    }

    @Test
    fun ofMoreThanThreeTheSelectedOneStaysBetweenElseTheLowestOfTheRest() {
        val detents = listOf(height(200), height(400), Medium, Large)
        assertEquals(listOf(height(200), Medium, Large), SheetStops.stops(detents, heightOf, Medium))
        assertEquals(listOf(height(200), height(400), Large), SheetStops.stops(detents, heightOf, null))
        assertEquals(listOf(height(200), height(400), Large), SheetStops.stops(detents, heightOf, Large))
    }

    @Test
    fun oneStopFitsTheSheetAndSkipsTheCollapsedState() {
        val material = SheetStops.material(listOf(1900), 2000)
        assertTrue(material.fitToContents)
        assertTrue(material.skipCollapsed)
        assertEquals(1900, material.peekHeight)
        assertEquals(1900, material.maxHeight)
        assertNull(material.halfExpandedRatio)
    }

    @Test
    fun twoStopsAreTheCollapsedAndTheExpandedState() {
        val material = SheetStops.material(listOf(300, 1000), 2000)
        assertTrue(material.fitToContents)
        assertFalse(material.skipCollapsed)
        assertEquals(300, material.peekHeight)
        assertEquals(1000, material.maxHeight)
    }

    @Test
    fun threeStopsPutTheMiddleOneAtTheHalfExpandedState() {
        val material = SheetStops.material(listOf(200, 400, 1900), 2000)
        assertFalse(material.fitToContents)
        assertEquals(200, material.peekHeight)
        assertEquals(0.2f, requireNotNull(material.halfExpandedRatio), 0.0001f)
        assertEquals(100, material.expandedOffset)
        assertEquals(1900, material.maxHeight)
    }

    @Test
    fun aMergeWithoutSelectedDetentLeavesTheStopTheUserDraggedTo() {
        // Opened at medium, dragged to large: the resolved `selectedDetent` is still medium.
        assertNull(SheetStops.target(settled = Large, detents = stops, arrived = null, selected = Medium))
    }

    @Test
    fun anArrivedSelectedDetentMovesTheSheetAgainToTheSameValue() {
        assertEquals(Medium, SheetStops.target(settled = Large, detents = stops, arrived = Medium, selected = Medium))
    }

    @Test
    fun anArrivedSelectedDetentThatIsNotAStopIsIgnored() {
        val arrived = SheetDetent.Height(300.0)
        assertNull(SheetStops.target(settled = Large, detents = stops, arrived = arrived, selected = arrived))
    }

    @Test
    fun aStopThatIsGoneMovesTheSheetToTheSelectedOrTheLowest() {
        val detents = listOf(SheetDetent.Height(200.0), Large)
        assertEquals(Large, SheetStops.target(settled = Medium, detents = detents, arrived = null, selected = Large))
        assertEquals(
            SheetDetent.Height(200.0),
            SheetStops.target(settled = Medium, detents = detents, arrived = null, selected = Medium),
        )
    }

    @Test
    fun aSheetOnTheMoveStaysWithoutAnArrivedStop() {
        assertNull(SheetStops.target(settled = null, detents = stops, arrived = null, selected = Medium))
    }
}

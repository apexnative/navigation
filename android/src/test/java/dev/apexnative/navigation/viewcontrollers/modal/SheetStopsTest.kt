package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.options.SheetDetent
import dev.apexnative.navigation.options.SheetDetent.Large
import dev.apexnative.navigation.options.SheetDetent.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A shown sheet moves only to a `selectedDetent` that arrives, or off a stop that is gone. */
class SheetStopsTest {
    private val stops = listOf(Medium, Large)

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

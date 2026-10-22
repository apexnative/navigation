package dev.apexnative.navigation.viewcontrollers.modal

import dev.apexnative.navigation.options.ModalAnimationOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The default animations of a modal, the decisions of a show and a sheet's place of rest: values only.
 */
class ModalAnimationsTest {
    private val delta = 0.0001f

    @Test
    fun theDistanceIsTheWindowsHeightOrTheDisplaysWithoutOne() {
        assertEquals(1200f, ModalAnimations.distance(1200, 2400), delta)
        assertEquals(2400f, ModalAnimations.distance(0, 2400), delta)
    }

    @Test
    fun theShowIsTheSystemsSlideOverTheWindowBelow() {
        // `activity_translucent_open_enter`: 300 ms, `linear_out_slow_in` (0, 0, 0.2, 1).
        assertEquals(300L, ModalAnimations.SHOW_DURATION)
        assertEquals(1000f, ModalAnimations.show(0f, 1000f), delta)
        assertEquals(0f, ModalAnimations.show(1f, 1000f), delta)
        // The curve at x = 0.5: t solves 0.6·t²(1 − t) + t³ = 0.5, and y = 3t²(1 − t) + t³.
        val half = ModalAnimations.linearOutSlowIn(0.5f)
        assertEquals(0.8392f, half, delta)
        assertEquals(1000f * (1 - half), ModalAnimations.show(0.5f, 1000f), delta)
    }

    @Test
    fun theDismissalGoesDownSpeedingUp() {
        // `activity_translucent_close_exit`: 250 ms, `accelerate_quad`.
        assertEquals(250L, ModalAnimations.DISMISS_DURATION)
        assertEquals(0f, ModalAnimations.dismiss(0f, 1000f), delta)
        assertEquals(1000f, ModalAnimations.dismiss(1f, 1000f), delta)
        assertEquals(250f, ModalAnimations.dismiss(0.5f, 1000f), delta)
    }

    @Test
    fun theSheetStylesShowASheetAndWithoutAStyleASheetFieldDoes() {
        val sheet = Options().apply { modal.sheet.grabber = Bool(true) }
        assertTrue(ModalRecord.isSheet(sheet))
        assertFalse(ModalRecord.isSheet(Options()))
        for (style in listOf("pageSheet", "formSheet", "popover")) {
            assertTrue(style, ModalRecord.isSheet(Options().apply { modalPresentationStyle = Text(style) }))
            assertTrue(style, ModalRecord.isSheet(sheet.copy().apply { modalPresentationStyle = Text(style) }))
        }
        for (style in listOf("fullScreen", "currentContext", "overFullScreen", "overCurrentContext")) {
            assertFalse(style, ModalRecord.isSheet(sheet.copy().apply { modalPresentationStyle = Text(style) }))
        }
        // An unknown string reads as unset.
        assertTrue(ModalRecord.isSheet(sheet.copy().apply { modalPresentationStyle = Text("none") }))
        assertFalse(ModalRecord.isSheet(Options().apply { modalPresentationStyle = Text("none") }))
        assertTrue(ModalRecord.coversUnder(false, Options().apply { modalPresentationStyle = Text("none") }))
    }

    @Test
    fun onlyAFullScreenModalCoversTheLayerUnder() {
        assertTrue(ModalRecord.coversUnder(false, Options()))
        assertTrue(ModalRecord.coversUnder(false, Options().apply { modalPresentationStyle = Text("currentContext") }))
        assertFalse(ModalRecord.coversUnder(true, Options()))
        assertFalse(ModalRecord.coversUnder(false, Options().apply { modalPresentationStyle = Text("overFullScreen") }))
        val context = Options().apply { modalPresentationStyle = Text("overCurrentContext") }
        assertFalse(ModalRecord.coversUnder(false, context))
    }

    @Test
    fun aSheetCountsWhereItRests() {
        // Fully raised in a layout 2000 tall, 1900 tall: it rests at 100; dragged down to 700 it is 600 below.
        assertEquals(0, SheetContainer.shiftOf(top = 100, height = 1900, parentHeight = 2000))
        assertEquals(600, SheetContainer.shiftOf(top = 700, height = 1900, parentHeight = 2000))
        assertEquals(100, SheetContainer.restTop(top = 700, shift = 600))
        // A sheet as tall as half the layout rests at its middle.
        assertEquals(0, SheetContainer.shiftOf(top = 1000, height = 1000, parentHeight = 2000))
    }
}

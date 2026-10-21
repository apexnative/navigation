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
        assertEquals(300L, ModalAnimations.DURATION)
    }

    @Test
    fun theShowComesUpAndFadesInDecelerating() {
        assertEquals(ModalAnimations.Frame(1000f, 0f), ModalAnimations.show(0f, 1000f))
        assertEquals(ModalAnimations.Frame(0f, 1f), ModalAnimations.show(1f, 1000f))
        val half = ModalAnimations.show(0.5f, 1000f)
        assertEquals(250f, half.translationY, delta)
        assertEquals(0.75f, half.alpha, delta)
    }

    @Test
    fun theDismissalGoesDownAndFadesOutAcceleratingThenDecelerating() {
        assertEquals(0f, ModalAnimations.dismiss(0f, 1000f).translationY, delta)
        assertEquals(1f, ModalAnimations.dismiss(0f, 1000f).alpha, delta)
        assertEquals(1000f, ModalAnimations.dismiss(1f, 1000f).translationY, delta)
        val half = ModalAnimations.dismiss(0.5f, 1000f)
        assertEquals(500f, half.translationY, delta)
        assertEquals(0.5f, half.alpha, delta)
        assertTrue(ModalAnimations.accelerateDecelerate(0.25f) < 0.25f)
    }

    @Test
    fun aSheetNeedsASheetFieldAndASheetStyle() {
        val sheet = Options().apply { modal.sheet.grabber = Bool(true) }
        assertTrue(ModalRecord.isSheet(sheet))
        assertTrue(ModalRecord.isSheet(sheet.copy().apply { modalPresentationStyle = Text("pageSheet") }))
        assertTrue(ModalRecord.isSheet(sheet.copy().apply { modalPresentationStyle = Text("formSheet") }))
        assertFalse(ModalRecord.isSheet(sheet.copy().apply { modalPresentationStyle = Text("fullScreen") }))
        assertFalse(ModalRecord.isSheet(sheet.copy().apply { modalPresentationStyle = Text("overCurrentContext") }))
        assertFalse(ModalRecord.isSheet(Options()))
        // An unknown string reads as unset.
        assertTrue(ModalRecord.isSheet(sheet.copy().apply { modalPresentationStyle = Text("none") }))
        assertTrue(ModalRecord.coversUnder(false, Options().apply { modalPresentationStyle = Text("none") }))
    }

    @Test
    fun onlyAFullScreenModalCoversTheLayerUnder() {
        assertTrue(ModalRecord.coversUnder(false, Options()))
        assertTrue(ModalRecord.coversUnder(false, Options().apply { modalPresentationStyle = Text("popover") }))
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

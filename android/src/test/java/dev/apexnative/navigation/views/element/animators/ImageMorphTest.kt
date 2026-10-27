package dev.apexnative.navigation.views.element.animators

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ImageMorphTest {
    @Test
    fun anImageIsPlacedFromTheViewsCenter() {
        // A square image cropped into a 200 × 100 view: it shows 200 × 200, centered.
        val shown = floatArrayOf(0f, -50f, 200f, 150f)
        assertArrayEquals(
            floatArrayOf(-100f, -100f, 100f, 100f),
            ImageMorph.imageFromCenter(shown, 200, 100, 200f, 100f),
            DELTA,
        )
    }

    @Test
    fun anImageScalesWithTheSizeItsViewIsDrawnAt() {
        // The same view drawn at half its size by a parent: its image shows at half too.
        val shown = floatArrayOf(0f, -50f, 200f, 150f)
        assertArrayEquals(
            floatArrayOf(-50f, -50f, 50f, 50f),
            ImageMorph.imageFromCenter(shown, 200, 100, 100f, 50f),
            DELTA,
        )
    }

    @Test
    fun aFittedImageKeepsItsPadding() {
        // A wide image fitted into a square view: bands above and below it.
        val shown = floatArrayOf(0f, 25f, 100f, 75f)
        assertArrayEquals(
            floatArrayOf(-50f, -25f, 50f, 25f),
            ImageMorph.imageFromCenter(shown, 100, 100, 100f, 100f),
            DELTA,
        )
    }

    private companion object {
        const val DELTA = 0.001f
    }
}

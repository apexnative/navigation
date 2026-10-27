package dev.apexnative.navigation.views.element.animators

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun aThumbnailStandsForItsImageAndAnotherDoesnt() {
        assertTrue(ImageMorph.sameAspect(100f, 75f, 1600f, 1200f))
        assertTrue(ImageMorph.sameAspect(100f, 75f, 1600f, 1210f))
        assertFalse(ImageMorph.sameAspect(100f, 100f, 1600f, 1200f))
        assertFalse(ImageMorph.sameAspect(0f, 100f, 100f, 100f))
    }

    private companion object {
        const val DELTA = 0.001f
    }
}

package dev.apexnative.navigation.utils

import com.facebook.react.uimanager.LengthPercentage
import com.facebook.react.uimanager.LengthPercentageType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The corner radius an element transition takes from a view: in dp, or a part of the view's width. */
class BorderRadiusTest {
    @Test
    fun aRadiusInDpIsInPixelsOfTheDensity() {
        assertEquals(24f, resolveBorderRadius(LengthPercentage(12f, LengthPercentageType.POINT), 400, 2f), 0.001f)
    }

    @Test
    fun aRadiusInPercentIsAPartOfTheViewsWidth() {
        // A view 400 px wide (and of any height): 10 % is 40 px.
        assertEquals(40f, resolveBorderRadius(LengthPercentage(10f, LengthPercentageType.PERCENT), 400, 2f), 0.001f)
    }

    @Test
    fun aNegativeRadiusIsNone() {
        assertEquals(0f, resolveBorderRadius(LengthPercentage(-5f, LengthPercentageType.POINT), 400, 2f), 0f)
    }
}

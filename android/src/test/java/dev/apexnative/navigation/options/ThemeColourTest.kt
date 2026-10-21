package dev.apexnative.navigation.options

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.options.params.NullThemeColour
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.options.params.ThemeColour.Side
import dev.apexnative.navigation.utils.ColorUtils
import dev.apexnative.navigation.utils.DevelopmentLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Colors as Android reads them: each side of a pair, platform colors, no color, and what can't be read (unset, with a
 * development warning).
 */
class ThemeColourTest {
    private val warnings = mutableListOf<String>()

    @Before
    fun listen() {
        DevelopmentLog.listener = { level, text -> if (level == 'w') warnings += text }
    }

    @After
    fun stop() {
        DevelopmentLog.listener = null
    }

    private fun parse(value: Any?) = ThemeColour.parse(null, JavaOnlyMap.of("color", value), "color")

    private val light = OptionsFixture.LIGHT
    private val dark = OptionsFixture.DARK

    @Test
    fun aPair() {
        val colour = parse(OptionsFixture.color())
        assertTrue(colour.hasValue())
        assertEquals(light, colour.get(false, null))
        assertEquals(dark, colour.get(true, null))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun aBareNumberIsBothSides() {
        val colour = parse(light)
        assertEquals(light, colour.get(false, null))
        assertEquals(light, colour.get(true, null))
    }

    @Test
    fun anArgbNumberAboveIntMax() {
        // A color JavaScript gives unsigned still reads as the same ARGB.
        val colour = parse(0xFF102030L.toDouble())
        assertEquals(light, colour.get(false, null))
    }

    @Test
    fun transparentIsAColor() {
        val colour = parse(JavaOnlyMap.of("light", 0, "dark", 0))
        assertEquals(0, colour.get(false, 7))
        assertTrue(colour.hasColour())
    }

    @Test
    fun noColorSide() {
        val colour = parse(JavaOnlyMap.of("light", light, "dark", "NoColor"))
        assertTrue(colour.hasValue())
        assertEquals(light, colour.get(false, 7))
        assertEquals(7, colour.get(true, 7))
        assertEquals(Side.None, colour.sideOf(true))
    }

    @Test
    fun nullIsNoColorOnBothSides() {
        val colour = parse(null)
        assertTrue(colour.hasValue())
        assertEquals(Side.None, colour.sideOf(false))
        assertEquals(Side.None, colour.sideOf(true))
        assertTrue(colour.isNoColour())
        assertEquals(5, colour.get(5))
        assertEquals(0, colour.get())
        assertEquals(Side.None, parse("NoColor").sideOf(true))
    }

    @Test
    fun aMissingSideIsUnsetWithAWarning() {
        val colour = parse(JavaOnlyMap.of("light", light))
        assertTrue(colour.hasValue())
        assertNull(colour.sideOf(true))
        assertEquals(1, warnings.size)
        assertTrue(warnings[0], warnings[0].startsWith("the option 'color' has a color the navigation can't read"))
    }

    @Test
    fun anEmptyPairIsUnset() {
        val colour = parse(JavaOnlyMap())
        assertFalse(colour.hasValue())
        assertTrue(colour is NullThemeColour)
        assertEquals(2, warnings.size)
    }

    @Test
    fun aStringIsUnsetWithAWarning() {
        assertFalse(parse("red").hasValue())
        assertFalse(parse(JavaOnlyMap.of("light", "red", "dark", "blue")).hasValue())
        assertFalse(parse(true).hasValue())
        assertFalse(parse(Double.NaN).hasValue())
        assertEquals(5, warnings.size)
    }

    @Test
    fun missingKeyIsUnsetWithoutAWarning() {
        assertFalse(ThemeColour.parse(null, JavaOnlyMap(), "color").hasValue())
        assertFalse(ThemeColour.parse(null, null, "color").hasValue())
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun platformColorsKeepTheirPaths() {
        val paths = JavaOnlyMap.of("resource_paths", JavaOnlyArray.of("?attr/colorPrimary", "@color/brand"))
        val colour = parse(paths)
        assertEquals(Side.Platform(listOf("?attr/colorPrimary", "@color/brand")), colour.sideOf(false))
        assertEquals(colour.sideOf(false), colour.sideOf(true))
        val pair = parse(JavaOnlyMap.of("light", light, "dark", paths))
        assertEquals(Side.Argb(light), pair.sideOf(false))
        assertEquals(Side.Platform(listOf("?attr/colorPrimary", "@color/brand")), pair.sideOf(true))
        // Without a context nothing resolves: the default.
        assertEquals(9, pair.get(true, 9))
    }

    @Test
    fun aThemeAttributesPathGivesItsPackageAndName() {
        assertEquals(null to "colorSurface", ThemeColour.attributeOf("?attr/colorSurface"))
        assertEquals(null to "colorSurface", ThemeColour.attributeOf("?colorSurface"))
        assertEquals("android" to "colorAccent", ThemeColour.attributeOf("?android:attr/colorAccent"))
        assertEquals(null, ThemeColour.attributeOf("@color/brand"))
        assertEquals(null, ThemeColour.attributeOf("?"))
    }

    @Test
    fun platformColorWithoutPathsIsUnset() {
        assertFalse(parse(JavaOnlyMap.of("resource_paths", JavaOnlyArray())).hasValue())
        assertEquals(1, warnings.size)
    }

    @Test
    fun anotherObjectWithoutAContextIsUnset() {
        assertFalse(parse(JavaOnlyMap.of("semantic", JavaOnlyArray.of("label"))).hasValue())
        assertEquals(1, warnings.size)
    }

    // Merging side by side.

    @Test
    fun anUnsetSideComesFromBelow() {
        val below = ThemeColour(1, 2)
        val upper = ThemeColour(3, null)
        val merged = below.mergeWith(upper)
        assertEquals(3, merged.get(false, null))
        assertEquals(2, merged.get(true, null))
        assertEquals(merged, upper.withDefault(below))
    }

    @Test
    fun noColorReplacesBelow() {
        val merged = ThemeColour(1, 2).mergeWith(parse(null))
        assertEquals(Side.None, merged.sideOf(false))
        assertEquals(Side.None, merged.sideOf(true))
    }

    @Test
    fun unsetLeavesBelow() {
        val below = ThemeColour(1, 2)
        assertSame(below, below.mergeWith(NullThemeColour()))
        assertSame(below, NullThemeColour().withDefault(below))
    }

    @Test
    fun aFullPairReplaces() {
        val upper = ThemeColour(3, 4)
        assertSame(upper, ThemeColour(1, 2).mergeWith(upper))
    }

    @Test
    fun luminance() {
        assertTrue(ColorUtils.isLight(0xFFFFFFFF.toInt()))
        assertFalse(ColorUtils.isLight(0xFF000000.toInt()))
        assertTrue(ColorUtils.isLight(0xFF00FF00.toInt()))
        assertFalse(ColorUtils.isLight(0xFF0000FF.toInt()))
        assertTrue(ColorUtils.isLight(0xFF808080.toInt()))
        assertFalse(ColorUtils.isLight(0xFF7F7F7F.toInt()))
    }
}

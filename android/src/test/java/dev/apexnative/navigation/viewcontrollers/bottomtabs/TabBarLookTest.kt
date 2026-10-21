package dev.apexnative.navigation.viewcontrollers.bottomtabs

import dev.apexnative.navigation.options.BottomTabsOptions
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.views.bottomtabs.BottomTabsContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bar's container draws from `bottomTabs`, and the mode of the custom row.
 */
class TabBarLookTest {
    private val defaultBackground = 0xFFEEEEEE.toInt()

    // 320 dpi: 2 pixels a dp.
    private fun look(change: BottomTabsOptions.() -> Unit) = TabBarLook.of(
        BottomTabsOptions().apply(change),
        { colour -> colour.get(false, null) },
        defaultBackground,
        densityDpi = 320,
    )

    @Test
    fun withoutOptionsTheThemesBackgroundAndNothingElse() {
        assertEquals(BottomTabsContainer.Look(defaultBackground, null, TabBarLook.THIN_LINE_PX, null, 0f, 0f), look {})
    }

    @Test
    fun theLineShowsWhenItsColorOrItsWidthIsSetWidthInDp() {
        assertEquals(0xFF444444.toInt(), look { borderWidth = Fraction(2.0) }.borderColor)
        assertEquals(4f, look { borderWidth = Fraction(2.0) }.borderWidth)
        val colorOnly = look { borderColor = ThemeColour(0xFFFF0000.toInt(), null) }
        assertEquals(0xFFFF0000.toInt(), colorOnly.borderColor)
        assertEquals(TabBarLook.THIN_LINE_PX, colorOnly.borderWidth)
    }

    @Test
    fun theShadowOnlyWithShadowBlackByDefaultWithItsOpacity() {
        assertNull(look {}.shadow)
        val shadow = look {
            shadow.opacity = Fraction(0.5)
            shadow.radius = Fraction(4.0)
        }.shadow
        assertEquals(BottomTabsContainer.Shadow(0x7F000000, 4f), shadow)
    }

    @Test
    fun elevationAndCornersAreInDpAndAColorReplacesTheDefault() {
        val look = look {
            elevation = Fraction(3.0)
            cornerRadius = Fraction(16.0)
            backgroundColor = ThemeColour(0xFF112233.toInt(), null)
        }
        assertEquals(6f, look.elevation)
        assertEquals(32f, look.cornerRadius)
        assertEquals(0xFF112233.toInt(), look.background)
    }

    @Test
    fun everyTabOrNoneDrawsItsCell() {
        assertTrue(TabBarLook.usesRow(3, 3))
        assertFalse(TabBarLook.usesRow(0, 3))
        assertFalse(TabBarLook.usesRow(2, 3))
        assertNull(TabBarLook.mixedWarning(3, 3))
        assertNull(TabBarLook.mixedWarning(0, 3))
        assertEquals(
            "Mixed bottomTab.component usage detected (2 of 3 tabs). All tabs must declare a component or none — " +
                "falling back to native rendering for all tabs.",
            TabBarLook.mixedWarning(2, 3),
        )
    }
}

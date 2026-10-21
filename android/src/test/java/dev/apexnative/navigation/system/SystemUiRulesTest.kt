package dev.apexnative.navigation.system

import android.content.pm.ActivityInfo
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.system.SystemUiRules.Bars
import dev.apexnative.navigation.system.SystemUiRules.Edges
import dev.apexnative.navigation.system.SystemUiRules.NavigationMode
import dev.apexnative.navigation.system.SystemUiRules.Screen
import dev.apexnative.navigation.system.SystemUiRules.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The values of the system UI from the deciding screen's options. */
class SystemUiRulesTest {
    private val manifest = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private fun environment(
        edgeToEdge: Boolean = true,
        night: Boolean = false,
        threeButtons: Boolean = false,
        themeStatusBarColor: Int = SystemUiRules.TRANSPARENT,
        themeNavigationBarColor: Int? = null,
        windowBackground: Int? = null,
    ) = SystemUiRules.Environment(
        edgeToEdge = edgeToEdge,
        night = night,
        threeButtons = threeButtons,
        themeStatusBarColor = themeStatusBarColor,
        themeNavigationBarColor = themeNavigationBarColor,
        manifestOrientation = manifest,
        windowBackground = windowBackground,
        topBarDefaultColor = SURFACE,
        tabsDefaultColor = SURFACE_CONTAINER,
        colour = { it.get(null, null) },
    )

    private fun options(build: Options.() -> Unit = {}) = Options().apply(build)

    private fun colour(argb: Int) = ThemeColour(argb, argb)

    // `null` from JavaScript: no color on both sides.
    private fun noColour() = ThemeColour.parse(null, JavaOnlyMap.of("color", null), "color")

    private fun orientation(vararg values: String) = SystemUiRules.orientationMode(values.toList(), manifest)

    // The keyboard (`layout.adjustResize`).

    @Test
    fun theKeyboardCoversTheScreenOnlyWithoutEdgeToEdgeAndAdjustResizeFalse() {
        val keeps = options { layout.adjustResize = Bool(false) }
        val resizes = options { layout.adjustResize = Bool(true) }
        assertTrue(SystemUiRules.keyboardCovers(keeps, environment(edgeToEdge = false)))
        assertFalse(SystemUiRules.keyboardCovers(resizes, environment(edgeToEdge = false)))
        assertFalse(SystemUiRules.keyboardCovers(options(), environment(edgeToEdge = false)))
        assertFalse(SystemUiRules.keyboardCovers(keeps, environment(edgeToEdge = true)))
    }

    // Orientation (`layout.orientation`).

    @Test
    fun everyValueAloneGivesItsMode() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, orientation("all"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, orientation("sensor"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, orientation("landscape"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientation("portrait"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, orientation("upsideDown"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, orientation("sensorLandscape"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, orientation("sensorPortrait"))
    }

    @Test
    fun theUnionOfTheSidesPicksTheMode() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, orientation("portrait", "landscape"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, orientation("portrait", "upsideDown"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, orientation("upsideDown", "landscape"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, orientation("portrait", "upsideDown", "landscape"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, orientation("portrait", "sensorLandscape"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, orientation("landscape", "sensorLandscape"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, orientation("sensorPortrait", "landscape"))
    }

    @Test
    fun onlySensorValuesTurnByTheSensor() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            orientation("sensorLandscape", "sensorLandscape"),
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT, orientation("sensorPortrait", "portrait"))
    }

    @Test
    fun defaultNoValuesAndNoOptionGiveTheManifests() {
        val manifest = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        assertEquals(manifest, SystemUiRules.orientationMode(null, manifest))
        assertEquals(manifest, SystemUiRules.orientationMode(emptyList(), manifest))
        assertEquals(manifest, SystemUiRules.orientationMode(listOf("bogus"), manifest))
        assertEquals(manifest, SystemUiRules.orientationMode(listOf("default"), manifest))
        assertEquals(manifest, SystemUiRules.orientationMode(listOf("landscape", "default"), manifest))
    }

    @Test
    fun unknownValuesAreDropped() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, orientation("landscape", "bogus"))
    }

    // Status bar color (`statusBar.backgroundColor`, -translucent).

    @Test
    fun theStatusBarTakesItsOwnColorElseTheThemesRemembered() {
        val environment = environment(themeStatusBarColor = THEME)
        val own = options { statusBar.backgroundColor = colour(RED) }
        assertEquals(RED, SystemUiRules.statusBarColor(own, environment))
        assertEquals(THEME, SystemUiRules.statusBarColor(options(), environment))
        val none = options { statusBar.backgroundColor = noColour() }
        assertEquals(THEME, SystemUiRules.statusBarColor(none, environment))
    }

    @Test
    fun aHiddenStatusBarIsTransparentUnlessItHasItsOwnColor() {
        val environment = environment(themeStatusBarColor = THEME)
        val hidden = options { statusBar.visible = Bool(false) }
        assertEquals(SystemUiRules.TRANSPARENT, SystemUiRules.statusBarColor(hidden, environment))
        hidden.statusBar.backgroundColor = colour(RED)
        assertEquals(RED, SystemUiRules.statusBarColor(hidden, environment))
    }

    @Test
    fun translucentChangesOnlyAnOpaqueColor() {
        val opaque = options {
            statusBar.backgroundColor = colour(0xFF123456.toInt())
            statusBar.translucent = Bool(true)
        }
        assertEquals(0xA6123456.toInt(), SystemUiRules.statusBarColor(opaque, environment()))
        val partly = options {
            statusBar.backgroundColor = colour(0x80123456.toInt())
            statusBar.translucent = Bool(true)
        }
        assertEquals(0x80123456.toInt(), SystemUiRules.statusBarColor(partly, environment()))
    }

    // Status bar icons (`statusBar.style`).

    private fun lightIcons(screen: Screen, environment: SystemUiRules.Environment = environment()) =
        SystemUiRules.statusBarLightIcons(screen, environment)

    @Test
    fun theStyleDecides() {
        assertTrue(lightIcons(Screen(options { statusBar.style = Text("light") })))
        assertFalse(lightIcons(Screen(options { statusBar.style = Text("dark") }), environment(night = true)))
    }

    @Test
    fun withoutAStyleAColorThatIsntTransparentDecidesByItsRgb() {
        assertFalse(lightIcons(Screen(options { statusBar.backgroundColor = colour(WHITE) })))
        assertTrue(lightIcons(Screen(options { statusBar.backgroundColor = colour(0x80000000.toInt()) })))
        val translucent = options {
            statusBar.backgroundColor = colour(0xFF123456.toInt())
            statusBar.translucent = Bool(true)
        }
        assertTrue(lightIcons(Screen(translucent), environment(windowBackground = WHITE)))
    }

    @Test
    fun aTransparentStatusBarTakesTheTopBarUnderIt() {
        val dark = options { topBar.background.color = colour(0xFF101010.toInt()) }
        assertTrue(lightIcons(Screen(dark, inStack = true)))
        // Without a color of its own, the theme's surface (light here).
        assertFalse(lightIcons(Screen(options(), inStack = true), environment(night = true)))
    }

    @Test
    fun aTopBarNotUnderTheStatusBarLeavesItToTheWindowOrTheNightMode() {
        val environment = environment(night = true, windowBackground = WHITE)
        val bar = { build: Options.() -> Unit ->
            options {
                topBar.background.color = colour(0xFF101010.toInt())
                build()
            }
        }
        assertFalse(lightIcons(Screen(bar { statusBar.drawBehind = Bool(false) }, inStack = true), environment))
        assertFalse(lightIcons(Screen(bar { topBar.visible = Bool(false) }, inStack = true), environment))
        assertFalse(lightIcons(Screen(bar { statusBar.visible = Bool(false) }, inStack = true), environment))
        assertFalse(lightIcons(Screen(bar {}, inStack = true, inSheet = true), environment))
        assertFalse(lightIcons(Screen(bar {}), environment))
    }

    @Test
    fun nothingUnderTheStatusBarFollowsTheNightMode() {
        assertFalse(lightIcons(Screen(options()), environment(night = false)))
        assertTrue(lightIcons(Screen(options()), environment(night = true)))
    }

    // Navigation bar (`navigationBar.*`).

    private fun navigationBar(screen: Screen, environment: SystemUiRules.Environment) =
        SystemUiRules.navigationBarColor(screen, environment)

    private fun lightButtons(screen: Screen, environment: SystemUiRules.Environment) =
        SystemUiRules.navigationBarLightButtons(screen, environment)

    @Test
    fun underTheBarNothingOverGesturesAndTheScrimsUnderThreeButtons() {
        val screen = Screen(options())
        assertEquals(SystemUiRules.TRANSPARENT, navigationBar(screen, environment()))
        assertTrue(lightButtons(screen, environment(night = true)))
        val light = environment(threeButtons = true)
        assertEquals(SystemUiRules.LIGHT_SCRIM, navigationBar(screen, light))
        assertFalse(lightButtons(screen, light))
        val dark = environment(threeButtons = true, night = true)
        assertEquals(SystemUiRules.DARK_SCRIM, navigationBar(screen, dark))
        assertTrue(lightButtons(screen, dark))
    }

    @Test
    fun nullIsTheSameAsNoColor() {
        val none = Screen(options { navigationBar.backgroundColor = noColour() })
        assertEquals(SystemUiRules.LIGHT_SCRIM, navigationBar(none, environment(threeButtons = true)))
    }

    @Test
    fun aColorWithAlphaBelow255PutsTheScreensUnderTheBar() {
        val half = options { navigationBar.backgroundColor = colour(0x80FF0000.toInt()) }
        assertTrue(SystemUiRules.isUnderNavigationBar(half, environment()))
        assertEquals(0x80FF0000.toInt(), navigationBar(Screen(half), environment(threeButtons = true)))
        val opaque = options { navigationBar.backgroundColor = colour(RED) }
        assertFalse(SystemUiRules.isUnderNavigationBar(opaque, environment()))
        opaque.navigationBar.drawBehind = Bool(true)
        assertTrue(SystemUiRules.isUnderNavigationBar(opaque, environment()))
        assertEquals(RED, navigationBar(Screen(opaque), environment()))
    }

    @Test
    fun overTheBarTheOwnColorThenTheTabsThenTheThemeThenBlack() {
        val over = { build: Options.() -> Unit ->
            options {
                navigationBar.drawBehind = Bool(false)
                build()
            }
        }
        val environment = environment(themeNavigationBarColor = THEME)
        val own = over { navigationBar.backgroundColor = colour(RED) }
        assertEquals(RED, navigationBar(Screen(own, inTabs = true), environment))
        val tabs = over { bottomTabs.backgroundColor = colour(BLUE) }
        assertEquals(BLUE, navigationBar(Screen(tabs, inTabs = true), environment))
        assertEquals(SURFACE_CONTAINER, navigationBar(Screen(over {}, inTabs = true), environment))
        assertEquals(THEME, navigationBar(Screen(tabs), environment))
        assertEquals(SystemUiRules.BLACK, navigationBar(Screen(over {}), environment(threeButtons = false)))
        assertEquals(
            SystemUiRules.BLACK_THREE_BUTTONS,
            navigationBar(Screen(over {}), environment(threeButtons = true)),
        )
    }

    @Test
    fun theDefaultsTabsColorDoesntReachAScreenOutOfTabs() {
        val defaults = options { bottomTabs.backgroundColor = colour(BLUE) }
        val environment = environment(edgeToEdge = false)
        assertEquals(SystemUiRules.BLACK, navigationBar(Screen(defaults), environment))
    }

    @Test
    fun withoutEdgeToEdgeTheScreensAreOverTheBar() {
        val environment = environment(edgeToEdge = false, threeButtons = true)
        assertFalse(SystemUiRules.isUnderNavigationBar(options(), environment))
        assertEquals(SystemUiRules.BLACK_THREE_BUTTONS, navigationBar(Screen(options()), environment))
        assertTrue(lightButtons(Screen(options()), environment))
    }

    @Test
    fun theButtonsFollowTheBrightnessOfTheColor() {
        val white = Screen(options { navigationBar.backgroundColor = colour(WHITE) })
        assertFalse(lightButtons(white, environment(night = true)))
        val black = Screen(options { navigationBar.backgroundColor = colour(0xFF000000.toInt()) })
        assertTrue(lightButtons(black, environment()))
    }

    // The navigation bar's mode.

    private fun bars(navigation: Edges, tappable: Edges, shown: Boolean = true) = Bars(navigation, tappable, shown)

    @Test
    fun threeButtonsFromTheTappableInsetsOnTheBarsSide() {
        val gestures = SystemUiRules.initialMode(34)
        val bottom = bars(Edges(0, 0, 0, 48), Edges(0, 24, 0, 48))
        assertEquals(NavigationMode(true, Side.Bottom), SystemUiRules.navigationMode(34, bottom, gestures))
        val right = bars(Edges(0, 0, 48, 0), Edges(0, 24, 48, 0))
        assertEquals(NavigationMode(true, Side.Right), SystemUiRules.navigationMode(34, right, gestures))
        val left = bars(Edges(48, 0, 0, 0), Edges(48, 24, 0, 0))
        assertEquals(NavigationMode(true, Side.Left), SystemUiRules.navigationMode(34, left, gestures))
        // The status bar's tappable top alone is no button.
        val gesture = bars(Edges(0, 0, 0, 16), Edges(0, 24, 0, 0))
        assertEquals(NavigationMode(false, Side.Bottom), SystemUiRules.navigationMode(34, gesture, gestures))
    }

    @Test
    fun beforeTheInsetsGesturesFromAndroid10AndThreeButtonsBelow() {
        assertEquals(NavigationMode(false, Side.Bottom), SystemUiRules.initialMode(29))
        assertEquals(NavigationMode(true, Side.Bottom), SystemUiRules.initialMode(28))
        val last = SystemUiRules.initialMode(34)
        assertEquals(last, SystemUiRules.navigationMode(34, null, last))
        assertEquals(last, SystemUiRules.navigationMode(34, bars(Edges(0, 0, 0, 0), Edges(0, 0, 0, 0)), last))
    }

    @Test
    fun aHiddenBarKeepsTheLastMode() {
        val last = NavigationMode(true, Side.Bottom)
        val hidden = bars(Edges(0, 0, 0, 48), Edges(0, 0, 0, 0), shown = false)
        assertEquals(last, SystemUiRules.navigationMode(34, hidden, last))
    }

    @Test
    fun belowAndroid10AlwaysThreeButtons() {
        val bottom = bars(Edges(0, 0, 0, 48), Edges(0, 0, 0, 0), shown = false)
        assertEquals(
            NavigationMode(true, Side.Bottom),
            SystemUiRules.navigationMode(28, bottom, NavigationMode(true, Side.Right)),
        )
    }

    private companion object {
        const val RED = 0xFFFF0000.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val THEME = 0xFF336699.toInt()
        const val SURFACE = 0xFFFAFAFA.toInt()
        const val SURFACE_CONTAINER = 0xFFEEEEEE.toInt()
    }
}

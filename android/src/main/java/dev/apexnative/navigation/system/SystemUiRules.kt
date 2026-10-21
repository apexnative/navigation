package dev.apexnative.navigation.system

import android.content.pm.ActivityInfo
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.utils.ColorUtils

/**
 * The values of the system UI from the options of the screens that decide: the status bar's color and icons
 * (`statusBar.*`), the navigation bar's color and buttons (`navigationBar.*`), the requested orientation
 * (`layout.orientation`) and the navigation bar's mode. Pure: colors are ARGB by shifts and masks; the SDK level, edge
 * to edge, the night mode, the mode and the theme's colors come as values ([Environment]), and the colors of the
 * options are read by a given function, so the rules run in plain unit tests.
 */
internal object SystemUiRules {
    /** The screen that decides a group, as the rules need it. */
    class Screen(
        /** Its resolved options: with the app's defaults, or for an overlay its own and its parents' only. */
        val options: Options,
        /** It is in a stack (its top bar may lie under the status bar). */
        val inStack: Boolean = false,
        /** It is in bottom tabs (the navigation bar may take their color). */
        val inTabs: Boolean = false,
        /** It is in a modal shown as a sheet: no top bar under the status bar there. */
        val inSheet: Boolean = false,
    )

    /** What the rules take from the activity at the moment they run. */
    class Environment(
        /** The activity draws edge to edge (`NavigationActivity.isEdgeToEdge`). */
        val edgeToEdge: Boolean,
        /** The app shows its dark mode. */
        val night: Boolean,
        /** The navigation bar has three buttons (else gestures). */
        val threeButtons: Boolean,
        /** The theme's status bar color, remembered at the activity's creation. */
        val themeStatusBarColor: Int,
        /** The app theme's own `android:navigationBarColor`; none when the theme doesn't set it. */
        val themeNavigationBarColor: Int?,
        /** The activity's orientation of the manifest, remembered at its creation. */
        val manifestOrientation: Int,
        /** The window's background when it is an opaque color; none otherwise. */
        val windowBackground: Int?,
        /** The top bar's color without options (the theme's `colorSurface`). */
        val topBarDefaultColor: Int,
        /** The tab bar's color without options (the theme's `colorSurfaceContainer`). */
        val tabsDefaultColor: Int,
        /** The color an option gives now (its side of the mode, platform colors resolved); none for unset, `null`. */
        val colour: (ThemeColour) -> Int?,
    )

    /** The sides of a box, in pixels. */
    data class Edges(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** What the window's insets say of the navigation bar. */
    class Bars(
        /** The navigation bar's insets, shown or not. */
        val navigationBar: Edges,
        /** The tappable elements' insets while they show. */
        val tappable: Edges,
        /** The navigation bar shows now. */
        val navigationBarShown: Boolean,
    )

    /** Where the navigation bar is. */
    enum class Side { Bottom, Left, Right }

    /** The navigation bar's mode: three buttons or gestures, and its side. */
    data class NavigationMode(val threeButtons: Boolean, val side: Side)

    /** The state of the system UI from the screens that decide each group. */
    fun state(
        statusBar: Screen,
        navigationBar: Screen,
        orientation: Screen,
        keyboard: Screen,
        environment: Environment,
    ): SystemUiState = SystemUiState(
        statusBarShown = !statusBar.options.statusBar.visible.isFalse,
        statusBarColor = statusBarColor(statusBar.options, environment),
        statusBarLightIcons = statusBarLightIcons(statusBar, environment),
        navigationBarShown = !navigationBar.options.navigationBar.visible.isFalse,
        navigationBarColor = navigationBarColor(navigationBar, environment),
        navigationBarLightButtons = navigationBarLightButtons(navigationBar, environment),
        orientation = orientationMode(orientation.options.layout.orientation, environment.manifestOrientation),
        keyboardCovers = keyboardCovers(keyboard.options, environment),
    )

    // Keyboard.

    /**
     * Whether the keyboard covers the bottom of the screen without resizing the window (`layout.adjustResize`): with
     * `adjustResize: false` when the activity doesn't draw edge to edge, where the window fits the system windows and
     * the manifest's soft input mode would shrink it. With edge to edge the window never resizes, and the screen keeps
     * the keyboard out of its padding itself.
     */
    fun keyboardCovers(options: Options, environment: Environment): Boolean =
        !environment.edgeToEdge && options.layout.adjustResize.isFalse

    // Status bar.

    /**
     * The status bar's color (`statusBar.backgroundColor`, -translucent): its own color, else the theme's remembered
     * one (transparent with edge to edge), transparent while the status bar is hidden; with `translucent`, an opaque
     * color takes the alpha 0.65 and a color already partly transparent stays. At once, no animation.
     */
    fun statusBarColor(options: Options, environment: Environment): Int {
        val color = statusBarBaseColor(options, environment)
        return if (options.statusBar.translucent.isTrue && alphaOf(color) == OPAQUE) {
            (color and RGB) or (TRANSLUCENT_ALPHA shl ALPHA_SHIFT)
        } else {
            color
        }
    }

    // The color as the options or the default give it, before `translucent`.
    private fun statusBarBaseColor(options: Options, environment: Environment): Int {
        environment.colour(options.statusBar.backgroundColor)?.let { return it }
        return if (options.statusBar.visible.isFalse) TRANSPARENT else environment.themeStatusBarColor
    }

    /**
     * Light icons in the status bar (`statusBar.style`): `'light'` and `'dark'` say it; without a style the icons
     * contrast with what lies under them — the status bar's color before `translucent` when it isn't transparent (alpha
     * 0), else the top bar under the status bar, else the window's opaque background, else the night mode. Only alpha 0
     * is transparent; any other color decides by its RGB.
     */
    fun statusBarLightIcons(screen: Screen, environment: Environment): Boolean {
        val options = screen.options
        when (options.statusBar.style.get(null)) {
            LIGHT -> return true
            DARK -> return false
        }
        val color = statusBarBaseColor(options, environment).takeIf { alphaOf(it) > 0 }
            ?: topBarUnderStatusBar(screen, environment)?.takeIf { alphaOf(it) > 0 }
            ?: environment.windowBackground?.takeIf { alphaOf(it) == OPAQUE }
            ?: return environment.night
        return !ColorUtils.isLight(color)
    }

    // The color of the top bar that lies under the status bar: the screen's stack shows its bar, the status bar shows
    // and the bar goes under it, not in a sheet; its color is the resolved `topBar.background.color`, else the theme's.
    // None without such a bar.
    private fun topBarUnderStatusBar(screen: Screen, environment: Environment): Int? {
        val options = screen.options
        val under = screen.inStack && !screen.inSheet && !options.topBar.visible.isFalse &&
            !options.statusBar.visible.isFalse && !options.statusBar.drawBehind.isFalse
        if (!under) return null
        return environment.colour(options.topBar.background.color) ?: environment.topBarDefaultColor
    }

    // Navigation bar.

    /**
     * Whether the screens are under the navigation bar (`navigationBar.drawBehind`): with edge to edge, as
     * `navigationBar.drawBehind` says, or without it unless the bar's own color is opaque. The screens' padding takes
     * the same rule (`ScreenSafeArea.drawsUnderNavigationBar`).
     */
    fun isUnderNavigationBar(options: Options, environment: Environment): Boolean {
        if (!environment.edgeToEdge) return false
        val drawBehind = options.navigationBar.drawBehind
        if (drawBehind.hasValue()) return drawBehind.isTrue
        val own = environment.colour(options.navigationBar.backgroundColor)
        return own == null || alphaOf(own) != OPAQUE
    }

    /**
     * The navigation bar's color (`navigationBar.backgroundColor`). Screens under the bar: the own color over them;
     * without one (or `null`) nothing over gestures, the scrim under three buttons — light in the light theme, dark in
     * the dark one. Screens over the bar: the own color, else the tabs' color while the screen is in bottom tabs, else
     * the app theme's own `navigationBarColor`, else black (0.8 alpha under three buttons).
     */
    fun navigationBarColor(screen: Screen, environment: Environment): Int {
        val options = screen.options
        val own = environment.colour(options.navigationBar.backgroundColor)
        if (isUnderNavigationBar(options, environment)) {
            return own ?: when {
                !environment.threeButtons -> TRANSPARENT
                environment.night -> DARK_SCRIM
                else -> LIGHT_SCRIM
            }
        }
        return own ?: tabsColour(screen, environment) ?: environment.themeNavigationBarColor
            ?: if (environment.threeButtons) BLACK_THREE_BUTTONS else BLACK
    }

    // The color the tab bar paints with the screen's options (its resolved `bottomTabs.backgroundColor`, else the
    // theme's, as the tabs' presenter does); none out of tabs.
    private fun tabsColour(screen: Screen, environment: Environment): Int? {
        if (!screen.inTabs) return null
        return environment.colour(screen.options.bottomTabs.backgroundColor) ?: environment.tabsDefaultColor
    }

    /**
     * Light buttons in the navigation bar (`navigationBar.backgroundColor`): by the brightness of the color shown (so
     * dark buttons on the light scrim, light ones on the dark scrim); no color (alpha 0) by the night mode.
     */
    fun navigationBarLightButtons(screen: Screen, environment: Environment): Boolean {
        val color = navigationBarColor(screen, environment)
        return if (alphaOf(color) == 0) environment.night else !ColorUtils.isLight(color)
    }

    /**
     * The navigation bar's mode from the window's insets: below Android 10 always three buttons; then the bar's side is
     * where its insets are (the bottom first, else the left or the right one), three buttons when the tappable elements
     * take that side. While the bar is hidden or the insets say nothing, [last] stays.
     */
    fun navigationMode(sdk: Int, bars: Bars?, last: NavigationMode): NavigationMode {
        val side = bars?.let { sideOf(it.navigationBar) } ?: return last
        if (sdk < ANDROID_10) return NavigationMode(threeButtons = true, side = side)
        if (!bars.navigationBarShown) return last
        val tappable = when (side) {
            Side.Bottom -> bars.tappable.bottom
            Side.Left -> bars.tappable.left
            Side.Right -> bars.tappable.right
        }
        return NavigationMode(threeButtons = tappable > 0, side = side)
    }

    /** The mode before the window's first insets: three buttons below Android 10, gestures from it. */
    fun initialMode(sdk: Int): NavigationMode = NavigationMode(threeButtons = sdk < ANDROID_10, side = Side.Bottom)

    private fun sideOf(bar: Edges): Side? = when {
        bar.bottom > 0 -> Side.Bottom
        bar.left > 0 -> Side.Left
        bar.right > 0 -> Side.Right
        else -> null
    }

    // Orientation.

    /**
     * The requested orientation (`layout.orientation`): the union of the sides of the [values] picks the mode that
     * keeps the user's rotation lock — all four `FULL_USER`, portrait and the landscapes `USER`, the landscapes
     * `USER_LANDSCAPE`, both portraits `USER_PORTRAIT`, portrait `PORTRAIT`, upside down `REVERSE_PORTRAIT`, upside
     * down with the landscapes `FULL_USER` —; only when every value is `sensorLandscape` (or every one
     * `sensorPortrait`) the sensor's mode. `default` in the array, no values (empty or only unknown) and no option in
     * any layer ([values] null) give the manifest's ([manifest]).
     */
    fun orientationMode(values: List<String>?, manifest: Int): Int {
        val known = values?.filter { it in SIDES || it == DEFAULT } ?: return manifest
        if (known.isEmpty() || DEFAULT in known) return manifest
        if (known.all { it == SENSOR_LANDSCAPE }) return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (known.all { it == SENSOR_PORTRAIT }) return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        val sides = known.flatMapTo(HashSet()) { SIDES.getValue(it) }
        val portrait = PORTRAIT_SIDE in sides
        val upsideDown = UPSIDE_DOWN_SIDE in sides
        val landscape = LANDSCAPE_SIDE in sides
        return when {
            upsideDown && landscape -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            portrait && landscape -> ActivityInfo.SCREEN_ORIENTATION_USER
            landscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            portrait && upsideDown -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            upsideDown -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun alphaOf(color: Int): Int = color ushr ALPHA_SHIFT

    private const val LIGHT = "light"
    private const val DARK = "dark"
    private const val DEFAULT = "default"
    private const val SENSOR_LANDSCAPE = "sensorLandscape"
    private const val SENSOR_PORTRAIT = "sensorPortrait"
    private const val PORTRAIT_SIDE = "portrait"
    private const val UPSIDE_DOWN_SIDE = "upsideDown"
    private const val LANDSCAPE_SIDE = "landscape"

    // The sides of each value; both landscapes always go together.
    private val SIDES = mapOf(
        "all" to setOf(PORTRAIT_SIDE, UPSIDE_DOWN_SIDE, LANDSCAPE_SIDE),
        "sensor" to setOf(PORTRAIT_SIDE, UPSIDE_DOWN_SIDE, LANDSCAPE_SIDE),
        "landscape" to setOf(LANDSCAPE_SIDE),
        SENSOR_LANDSCAPE to setOf(LANDSCAPE_SIDE),
        "portrait" to setOf(PORTRAIT_SIDE),
        SENSOR_PORTRAIT to setOf(PORTRAIT_SIDE, UPSIDE_DOWN_SIDE),
        "upsideDown" to setOf(UPSIDE_DOWN_SIDE),
    )

    private const val ANDROID_10 = 29
    private const val ALPHA_SHIFT = 24
    private const val OPAQUE = 0xFF
    private const val RGB = 0x00FFFFFF
    // 0.65 of 255, rounded (`statusBar.translucent`).
    private const val TRANSLUCENT_ALPHA = 0xA6

    /** Transparent. */
    const val TRANSPARENT = 0

    /** The scrim under three buttons in the light theme: white at 0xE6. */
    const val LIGHT_SCRIM = 0xE6FFFFFF.toInt()

    /** The scrim under three buttons in the dark theme: `#1B1B1B` at 0x80. */
    const val DARK_SCRIM = 0x801B1B1B.toInt()

    /** The navigation bar over the screens without a color: black. */
    const val BLACK = 0xFF000000.toInt()

    /** The same under three buttons: black at 0.8. */
    const val BLACK_THREE_BUTTONS = 0xCC000000.toInt()
}

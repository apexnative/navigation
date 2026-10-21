package dev.apexnative.navigation.system

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.system.SystemUiLayers.Kind
import dev.apexnative.navigation.system.SystemUiLayers.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner of an activity's system UI: once per frame, only what changed, its own window only. */
class SystemUiOwnerTest {
    /** A window that records what is written, and runs the frame's task when the test says. */
    private class FakeWindow(
        override val sdk: Int = 34,
        override val isEdgeToEdge: Boolean = true,
        var bars: SystemUiRules.Bars? = null,
    ) : SystemUiWindow {
        val writes = mutableListOf<String>()
        val tasks = mutableListOf<Runnable>()
        var posts = 0
        var released = false
        var barsChanged: () -> Unit = {}

        fun frame() {
            val due = tasks.toList()
            tasks.clear()
            due.forEach(Runnable::run)
        }

        override fun start() = SystemUiWindow.Remembered(THEME, null, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        override fun attach(content: ViewGroup, barsChanged: () -> Unit) {
            this.barsChanged = barsChanged
        }

        override fun isNight() = false

        override fun windowBackground(): Int? = null

        override fun topBarDefaultColor() = WHITE

        override fun tabsDefaultColor() = WHITE

        override fun colourOf(colour: ThemeColour): Int? = colour.get(null, null)

        override fun bars() = bars

        override fun post(task: Runnable) {
            posts++
            tasks += task
        }

        override fun cancel(task: Runnable) {
            tasks -= task
        }

        override fun setStatusBarShown(shown: Boolean) {
            writes += "statusBarShown=$shown"
        }

        override fun setStatusBarColor(color: Int) {
            writes += "statusBarColor=${Integer.toHexString(color)}"
        }

        override fun setStatusBarLightIcons(light: Boolean) {
            writes += "statusBarLightIcons=$light"
        }

        override fun setNavigationBarShown(shown: Boolean) {
            writes += "navigationBarShown=$shown"
        }

        override fun setNavigationBarColor(color: Int) {
            writes += "navigationBarColor=${Integer.toHexString(color)}"
        }

        override fun setNavigationBarLightButtons(light: Boolean) {
            writes += "navigationBarLightButtons=$light"
        }

        override fun setOrientation(orientation: Int) {
            writes += "orientation=$orientation"
        }

        override fun setKeyboardCovers(covers: Boolean) {
            writes += "keyboardCovers=$covers"
        }

        var localeRtl = false
        val directions = mutableListOf<String>()

        override fun isLocaleRtl() = localeRtl

        override fun setDirection(layoutDirection: Int, rtl: Boolean) {
            directions += "$layoutDirection/$rtl"
        }

        override fun release() {
            released = true
        }
    }

    private var rootOptions = Options()
    private var layers: () -> List<Layer> = {
        listOf(Layer(Kind.Root, false, emptySet()) { SystemUiRules.Screen(rootOptions) })
    }
    private val window = FakeWindow()
    private val owner = SystemUiOwner { layers() }.also { it.start(window) }

    @Test
    fun manyInvalidationsInOneFrameApplyOnce() {
        owner.invalidate()
        owner.invalidate()
        owner.invalidateKeepingOrientation()
        assertEquals(1, window.posts)
        window.frame()
        assertEquals(7, window.writes.size)
        owner.invalidate()
        assertEquals(2, window.posts)
    }

    @Test
    fun theFirstApplyWritesEveryField() {
        owner.invalidate()
        window.frame()
        assertEquals(
            listOf(
                "statusBarShown=true",
                "statusBarColor=${Integer.toHexString(THEME)}",
                "statusBarLightIcons=true",
                "navigationBarShown=true",
                "navigationBarColor=0",
                "navigationBarLightButtons=false",
                "orientation=${ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}",
            ),
            window.writes,
        )
    }

    @Test
    fun laterAppliesWriteOnlyWhatChanged() {
        owner.invalidate()
        window.frame()
        window.writes.clear()
        // A merge of the title only: nothing of the system UI changes, the navigation bar least.
        rootOptions = rootOptions.copy().apply { topBar.title.text = Text("Profile") }
        owner.invalidate()
        window.frame()
        assertEquals(emptyList<String>(), window.writes)
        rootOptions = rootOptions.copy().apply { statusBar.visible = Bool(false) }
        owner.invalidate()
        window.frame()
        // Hidden: the default color is transparent while it is hidden, and the icons go by the night mode.
        assertEquals(
            listOf("statusBarShown=false", "statusBarColor=0", "statusBarLightIcons=false"),
            window.writes,
        )
    }

    @Test
    fun newDefaultOptionsKeepTheOrientationUnlessAnotherChangeAsks() {
        owner.invalidate()
        window.frame()
        window.writes.clear()
        rootOptions = rootOptions.copy().apply { layout.orientation = listOf("landscape") }
        owner.invalidateKeepingOrientation()
        window.frame()
        assertEquals(emptyList<String>(), window.writes)
        owner.invalidateKeepingOrientation()
        owner.invalidate()
        window.frame()
        assertEquals(listOf("orientation=${ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE}"), window.writes)
    }

    @Test
    fun withoutEdgeToEdgeTheKeyboardFollowsTheScreenOnTop() {
        val window = FakeWindow(isEdgeToEdge = false)
        val owner = SystemUiOwner { layers() }.also { it.start(window) }
        // No screen asks the keyboard to cover it: the first apply and a configuration change leave the app's mode.
        owner.invalidate()
        window.frame()
        owner.configurationChanged()
        window.frame()
        assertTrue(window.writes.none { it.startsWith("keyboardCovers") })
        window.writes.clear()
        // A merge of `layout.adjustResize: false`, the keyboard open or not: the window stops resizing for it.
        rootOptions = rootOptions.copy().apply { layout.adjustResize = Bool(false) }
        owner.invalidate()
        window.frame()
        assertEquals(listOf("keyboardCovers=true"), window.writes)
        window.writes.clear()
        owner.configurationChanged()
        window.frame()
        assertTrue(window.writes.none { it.startsWith("keyboardCovers") })
        window.writes.clear()
        // Another screen on top without the option: the window's own mode again.
        rootOptions = Options()
        owner.invalidate()
        window.frame()
        assertEquals(listOf("keyboardCovers=false"), window.writes)
    }

    @Test
    fun withEdgeToEdgeTheKeyboardModeIsNeverWritten() {
        rootOptions = rootOptions.copy().apply { layout.adjustResize = Bool(false) }
        owner.invalidate()
        window.frame()
        assertTrue(window.writes.none { it.startsWith("keyboardCovers") })
    }

    @Test
    fun aConfigurationChangeWritesEverythingAgain() {
        owner.invalidate()
        window.frame()
        window.writes.clear()
        owner.configurationChanged()
        window.frame()
        assertEquals(7, window.writes.size)
    }

    @Test
    fun withNoLayerNothingIsApplied() {
        layers = { emptyList() }
        owner.invalidate()
        window.frame()
        assertEquals(emptyList<String>(), window.writes)
    }

    @Test
    fun aGroupNoLayerDecidesStaysAsTheWindowHasIt() {
        val toast = Options().apply { statusBar.style = Text("light") }
        val overlay = Layer(Kind.Overlay, false, SystemUiLayers.namedGroups(toast)) { SystemUiRules.Screen(toast) }
        layers = { listOf(overlay) }
        owner.invalidate()
        window.frame()
        assertEquals(
            listOf("statusBarShown=true", "statusBarColor=${Integer.toHexString(THEME)}", "statusBarLightIcons=true"),
            window.writes,
        )
        window.writes.clear()
        // The root comes: the groups it decides now are written whole, the overlay keeps the status bar.
        layers = { listOf(Layer(Kind.Root, false, emptySet()) { SystemUiRules.Screen(rootOptions) }, overlay) }
        owner.invalidate()
        window.frame()
        assertEquals(
            listOf(
                "navigationBarShown=true",
                "navigationBarColor=0",
                "navigationBarLightButtons=false",
                "orientation=${ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}",
            ),
            window.writes,
        )
    }

    @Test
    fun theFirstDecisionOfTheOrientationIsWrittenEvenWhenHeld() {
        layers = { listOf(Layer(Kind.Overlay, false, emptySet()) { SystemUiRules.Screen(Options()) }) }
        owner.invalidate()
        window.frame()
        assertEquals(emptyList<String>(), window.writes)
        layers = { listOf(Layer(Kind.Root, false, emptySet()) { SystemUiRules.Screen(rootOptions) }) }
        owner.invalidateKeepingOrientation()
        window.frame()
        assertTrue("orientation=${ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}" in window.writes)
    }

    @Test
    fun releaseCancelsThePendingApplyAndNothingIsWrittenAfter() {
        owner.invalidate()
        owner.release()
        window.frame()
        assertTrue(window.released)
        assertEquals(emptyList<String>(), window.writes)
        owner.invalidate()
        owner.applyDirection("rtl")
        assertEquals(1, window.posts)
        window.frame()
        assertEquals(emptyList<String>(), window.writes)
        assertEquals(emptyList<String>(), window.directions)
    }

    @Test
    fun beforeStartNothingHappens() {
        val other = FakeWindow()
        val owner = SystemUiOwner { layers() }
        owner.invalidate()
        owner.applyDirection("rtl")
        assertEquals(0, other.posts)
        owner.start(other)
        assertEquals(0, other.posts)
        assertEquals(emptyList<String>(), other.directions)
    }

    @Test
    fun twoOwnersKeepTheirOwnState() {
        val second = FakeWindow()
        val secondOwner = SystemUiOwner { layers() }.also { it.start(second) }
        owner.invalidate()
        window.frame()
        secondOwner.invalidate()
        second.frame()
        assertEquals(7, second.writes.size)
        owner.release()
        secondOwner.configurationChanged()
        second.frame()
        assertEquals(14, second.writes.size)
        assertEquals(7, window.writes.size)
    }

    @Test
    fun withoutEdgeToEdgeThreeButtonsGiveTheTranslucentBlack() {
        val buttons = SystemUiRules.Bars(
            navigationBar = SystemUiRules.Edges(0, 0, 0, 48),
            tappable = SystemUiRules.Edges(0, 24, 0, 48),
            navigationBarShown = true,
        )
        val window = FakeWindow(isEdgeToEdge = false, bars = buttons)
        val owner = SystemUiOwner { layers() }.also { it.start(window) }
        owner.invalidate()
        window.frame()
        assertTrue("navigationBarColor=${Integer.toHexString(SystemUiRules.BLACK_THREE_BUTTONS)}" in window.writes)
    }

    @Test
    fun aNewModeOfTheNavigationBarAppliesAgain() {
        owner.attach(ViewGroupStub.make())
        owner.invalidate()
        window.frame()
        window.writes.clear()
        window.bars = SystemUiRules.Bars(
            navigationBar = SystemUiRules.Edges(0, 0, 0, 48),
            tappable = SystemUiRules.Edges(0, 0, 0, 48),
            navigationBarShown = true,
        )
        window.barsChanged()
        assertEquals(2, window.posts)
        window.frame()
        // Three buttons now: the light scrim; the buttons were dark already.
        assertEquals(listOf("navigationBarColor=${Integer.toHexString(SystemUiRules.LIGHT_SCRIM)}"), window.writes)
        window.barsChanged()
        assertEquals(2, window.posts)
    }

    @Test
    fun anOverlayWithOnlyAStyleShowsItOnTheThemesColor() {
        // The root's own color is red, the defaults' white; the toast names only the style.
        rootOptions = Options().apply { statusBar.backgroundColor = ThemeColour(RED, RED) }
        val toast = Options().apply { statusBar.style = Text("light") }
        layers = {
            listOf(
                Layer(Kind.Root, false, emptySet()) { SystemUiRules.Screen(rootOptions) },
                Layer(Kind.Overlay, false, SystemUiLayers.namedGroups(toast)) { SystemUiRules.Screen(toast) },
            )
        }
        owner.invalidate()
        window.frame()
        assertTrue("statusBarColor=${Integer.toHexString(THEME)}" in window.writes)
        assertTrue("statusBarLightIcons=true" in window.writes)
    }

    @Test
    fun theDirectionAppliesOnceAndNeverWithoutAValue() {
        owner.applyDirection(null)
        owner.applyDirection("rtl")
        owner.applyDirection("rtl")
        window.localeRtl = true
        owner.applyDirection("locale")
        assertEquals(listOf("1/true", "3/true"), window.directions)
    }

    private object ViewGroupStub {
        // The fake window ignores the content; a plain stub of the unit tests' Android stands for it.
        fun make(): ViewGroup = android.widget.FrameLayout(android.app.Activity())
    }

    private companion object {
        const val THEME = 0xFF336699.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFFF0000.toInt()
    }
}

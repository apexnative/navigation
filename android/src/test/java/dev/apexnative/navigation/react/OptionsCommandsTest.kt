package dev.apexnative.navigation.react

import android.app.Activity
import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `setDefaultOptions`, `mergeOptions` and `clearOptions`. */
class OptionsCommandsTest {
    private val tasks = ArrayDeque<Runnable>()
    private val logged = mutableListOf<String>()
    private var hasActivity = true
    private var repaints = 0
    private var currentRepaints = 0
    private val nodes = HashMap<String, ViewController<*>>()
    private val runner = CommandRunner(
        post = { tasks.addLast(it) },
        activityPresent = { hasActivity },
        events = FakeEvents(Journal()),
        log = { logged += it },
    )
    private val commands = OptionsCommands(
        runner = runner,
        context = { null },
        findNode = { nodes[it] },
        repaint = { repaints++ },
        repaintCurrent = { currentRepaints++ },
    )

    private fun runMain() {
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }

    private fun title(text: Any?) = JavaOnlyMap.of("topBar", JavaOnlyMap.of("title", JavaOnlyMap.of("text", text)))

    @Before
    fun emptyDefaults() {
        DefaultOptions.set(Options())
    }

    @After
    fun clearDefaults() {
        DefaultOptions.set(Options())
    }

    @Test
    fun defaultsAreStoredWithoutAnActivity() {
        hasActivity = false
        commands.setDefaultOptions(title("Home"))
        assertFalse(DefaultOptions.current.topBar.title.text.hasValue())
        runMain()
        assertEquals("Home", DefaultOptions.current.topBar.title.text.get())
        assertEquals(1, repaints)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun newDefaultsReplaceTheOldOnes() {
        commands.setDefaultOptions(title("Home"))
        commands.setDefaultOptions(JavaOnlyMap.of("statusBar", JavaOnlyMap.of("visible", false)))
        runMain()
        assertFalse(DefaultOptions.current.topBar.title.text.hasValue())
        assertTrue(DefaultOptions.current.statusBar.visible.isFalse)
        assertEquals(2, repaints)
    }

    @Test
    fun unreadableDefaultsKeepTheOldOnes() {
        commands.setDefaultOptions(title("Home"))
        runMain()
        val before = DefaultOptions.current
        commands.setDefaultOptions(title(Any()))
        runMain()
        assertSame(before, DefaultOptions.current)
        assertEquals(1, repaints)
        assertEquals(
            listOf(
                "[ApexNavigation] setDefaultOptions: the option 'topBar.title.text' of the default options has a " +
                    "value the navigation can't read",
            ),
            logged,
        )
    }

    @Test
    fun mergeIntoAnUnknownIdDoesNothing() {
        commands.mergeOptions("Gone", title("Home"))
        commands.mergeOptions("Gone", title(Any()))
        runMain()
        assertTrue(logged.isEmpty())
    }

    @Test
    fun mergeWithoutAnActivityLogsOnce() {
        hasActivity = false
        commands.mergeOptions("Home", title("Home"))
        runMain()
        assertEquals(
            listOf(
                "[ApexNavigation] mergeOptions: the navigation has nowhere to show screens: the app has no activity " +
                    "or window",
            ),
            logged,
        )
    }

    @Test
    fun mergeGoesIntoTheNode() {
        val home = TestScreen(Activity(), ChildControllersRegistry(), "Home")
        nodes["Home"] = home
        commands.mergeOptions("Home", title("Profile"))
        assertFalse(home.options.topBar.title.text.hasValue())
        runMain()
        assertEquals("Profile", home.options.topBar.title.text.get())
        assertTrue(logged.isEmpty())
    }

    @Test
    fun anUnreadableValueIsLoggedAndTheRestMerges() {
        val home = TestScreen(Activity(), ChildControllersRegistry(), "Home")
        nodes["Home"] = home
        val options = JavaOnlyMap.of(
            "topBar", JavaOnlyMap.of("title", JavaOnlyMap.of("text", Any())),
            "statusBar", JavaOnlyMap.of("visible", false),
        )
        commands.mergeOptions("Home", options)
        runMain()
        assertTrue(home.options.statusBar.visible.isFalse)
        assertEquals(
            listOf(
                "[ApexNavigation] mergeOptions: the option 'topBar.title.text' of the screen 'Home' has a value the " +
                    "navigation can't read",
            ),
            logged,
        )
    }

    private fun homeWithSubtitle(): TestScreen {
        val home = TestScreen(Activity(), ChildControllersRegistry(), "Home")
        nodes["Home"] = home
        commands.mergeOptions(
            "Home",
            JavaOnlyMap.of(
                "topBar",
                JavaOnlyMap.of("title", JavaOnlyMap.of("text", "Home"), "subtitle", JavaOnlyMap.of("text", "Profile")),
            ),
        )
        runMain()
        return home
    }

    @Test
    fun clearTakesThePathsOutOfTheOwnOptionsAndRepaints() {
        val home = homeWithSubtitle()
        commands.clearOptions("Home", JavaOnlyArray.of("topBar.subtitle"))
        assertTrue(home.options.topBar.subtitle.hasValue())
        runMain()
        assertFalse(home.options.topBar.subtitle.hasValue())
        assertEquals("Home", home.options.topBar.title.text.get())
        assertEquals(1, currentRepaints)
        assertEquals(0, repaints)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun clearWithUnknownPathsOrIdDoesNothing() {
        val home = homeWithSubtitle()
        val own = home.options
        commands.clearOptions("Home", JavaOnlyArray.of("topBar.nothing", "", 3))
        commands.clearOptions("Gone", JavaOnlyArray.of("topBar.subtitle"))
        runMain()
        assertSame(own, home.options)
        assertEquals(0, currentRepaints)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun clearWithoutAnActivityLogsOnce() {
        hasActivity = false
        commands.clearOptions("Home", JavaOnlyArray.of("topBar.subtitle"))
        runMain()
        assertEquals(
            listOf(
                "[ApexNavigation] clearOptions: the navigation has nowhere to show screens: the app has no activity " +
                    "or window",
            ),
            logged,
        )
    }
}

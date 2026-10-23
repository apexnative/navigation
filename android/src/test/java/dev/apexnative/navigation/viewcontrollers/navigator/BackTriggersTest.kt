package dev.apexnative.navigation.viewcontrollers.navigator

import android.app.Activity
import androidx.activity.OnBackPressedCallback
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.layout.RootLayout
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandRunner
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The triggers of the back callback that the navigator owns, without a frame that would compute it: a root that
 * shows, a reload that takes the layers, a layout that changes the screens it shows, new default options; and the
 * debug builds' check, which logs a callback left stale once per state and never changes it.
 */
class BackTriggersTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val activity = Activity()
    private val navigator = Navigator(activity)
    private val callback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    @Before
    fun install() {
        fixture.install()
        BackCallbackState.attach(navigator, callback)
    }

    @After
    fun uninstall() {
        BackCallbackState.detach(navigator)
        navigator.destroyWithActivity()
        DefaultOptions.set(Options())
        ScreenReadiness.reset()
        fixture.uninstall()
    }

    // What the callback must be now.
    private fun expected(): Boolean = navigator.canHandleBack() || navigator.keepsBackFromSystem()

    // A root of one React screen `Home1` with [options], shown.
    private fun showRoot(options: Options = Options()) {
        val home = LayoutNode(
            id = "Home1",
            type = LayoutType.Component,
            name = "Home",
            options = options,
            key = null,
            passProps = null,
            functions = emptyList(),
            columns = emptyList(),
            arrangement = null,
            children = emptyList(),
        )
        val answer = CommandAnswer(
            "setRoot",
            "setRoot1",
            FakePromise(journal),
            ReloadCoordinator.generation,
            FakeEvents(journal),
            { 0L },
        )
        navigator.setRoot(RootLayout(home, emptyList(), emptyList()), answer)
        ScreenReadiness.contentRendered("Home1")
    }

    private val keepsBack = Options().apply { hardwareBackButton.systemAnimation = Bool(false) }

    @Test
    fun aRootThatShowsComputesBackAgain() {
        showRoot(keepsBack)
        assertTrue(expected())
        assertEquals(expected(), callback.isEnabled)
    }

    @Test
    fun aReloadTakesTheCallbackOfTheRootThatWentOff() {
        showRoot(keepsBack)
        assertTrue(callback.isEnabled)
        navigator.destroy()
        assertFalse(expected())
        assertEquals(expected(), callback.isEnabled)
    }

    @Test
    fun aLayoutThatChangesItsScreensComputesBackAgain() {
        // A stale value the computation corrects: the navigation has no root, so back leaves the app.
        callback.isEnabled = true
        navigator.shownScreensChanged(TestLayout(activity, navigator.registry, "Tabs1"))
        assertEquals(expected(), callback.isEnabled)
        assertFalse(callback.isEnabled)
    }

    @Test
    fun newDefaultOptionsComputeBackAgainAtTheEndOfTheirCommand() {
        showRoot()
        assertFalse(callback.isEnabled)
        val runner = CommandRunner(
            post = { it.run() },
            activityPresent = { true },
            events = FakeEvents(journal),
            log = {},
        )
        runner.runVoid("setDefaultOptions", "set the default options") { DefaultOptions.set(keepsBack) }
        assertTrue(expected())
        assertEquals(expected(), callback.isEnabled)
    }

    // The debug check.

    @Test
    fun theCheckIsQuietWhileTheCallbackIsRight() {
        showRoot()
        val logged = mutableListOf<String>()
        val check = BackCallbackCheck { logged += it }
        assertNull(BackCallbackState.mismatchOf(navigator))
        check.sample(navigator, 0L)
        check.sample(navigator, BackCallbackCheck.SAMPLE_MILLIS)
        check.atBack(navigator)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun theCheckLogsAStaleCallbackOncePerStateAndLeavesIt() {
        showRoot(keepsBack)
        val logged = mutableListOf<String>()
        val check = BackCallbackCheck { logged += it }
        // A change without its trigger: the callback is off where back stays in the app.
        callback.isEnabled = false
        check.sample(navigator, 0L)
        assertTrue("a first sample waits for the next one", logged.isEmpty())
        check.sample(navigator, BackCallbackCheck.SAMPLE_MILLIS - 1)
        assertTrue("samples come at most every SAMPLE_MILLIS", logged.isEmpty())
        check.sample(navigator, BackCallbackCheck.SAMPLE_MILLIS)
        assertEquals(1, logged.size)
        assertTrue(logged.single(), "disabled where back stays in the app" in logged.single())
        assertTrue(logged.single(), "'Home1'" in logged.single())
        check.sample(navigator, 2 * BackCallbackCheck.SAMPLE_MILLIS)
        check.atBack(navigator)
        assertEquals("once per state", 1, logged.size)
        assertFalse("the check changes nothing", callback.isEnabled)
    }

    @Test
    fun theCheckAtBackLogsAtOnce() {
        showRoot()
        val logged = mutableListOf<String>()
        val check = BackCallbackCheck { logged += it }
        callback.isEnabled = true
        check.atBack(navigator)
        assertEquals(1, logged.size)
        assertTrue(logged.single(), "enabled where back leaves the app" in logged.single())
        assertTrue(callback.isEnabled)
    }
}

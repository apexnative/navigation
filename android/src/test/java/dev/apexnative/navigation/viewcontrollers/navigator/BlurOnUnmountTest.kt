package dev.apexnative.navigation.viewcontrollers.navigator

import android.app.Activity
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.component.ScreenFocus
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * `blurOnUnmount`: the old root's screens that have it in their resolved options (their own, a merge, the layouts
 * around, the defaults) let go of the focus as `setRoot`'s transition starts, before the new root's WillAppear and
 * before the old root is destroyed.
 */
class BlurOnUnmountTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val entries = journal.entries
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val blur = ScreenFocus.blur
    private val coordinator = RootCoordinator(
        layer = object : RootCoordinator.Layer {
            override fun place(root: ViewController<*>) = Unit

            override fun raise(root: ViewController<*>) = Unit

            override fun show(root: ViewController<*>) = Unit

            override fun remove(root: ViewController<*>) = Unit

            override fun fadeThrough(old: ViewController<*>, new: ViewController<*>, onEnd: () -> Unit): () -> Unit {
                onEnd()
                return {}
            }
        },
        readiness = object : Readiness {
            override fun isReady(screen: ViewController<*>) = true

            override fun whenReady(screen: ViewController<*>, listener: () -> Unit) = listener()

            override fun markReady(screen: ViewController<*>) = Unit
        },
        scheduler = fixture.scheduler,
        firstRoot = FirstRootDraw(fixture.scheduler) {},
        onRootChanged = {},
        onRootShown = {},
    )
    private var commands = 0

    // A screen whose content rendered: its events go at once.
    private fun screen(id: String, options: Options = Options()): ComponentViewController {
        val screen = ComponentViewController(activity, registry, id, "Home", Presenter(activity, Options()), options)
        ScreenReadiness.contentRendered(id)
        return screen
    }

    private fun blurring() = Options().apply { blurOnUnmount = Bool(true) }

    private fun setRoot(root: ViewController<*>) = coordinator.setRoot(
        { root },
        CommandAnswer("setRoot", "setRoot${++commands}", FakePromise(journal), ReloadCoordinator.generation,
            FakeEvents(journal), { 0L }),
    )

    @Before
    fun install() {
        fixture.install()
        ScreenFocus.blur = { entries += "blur" }
    }

    @After
    fun uninstall() {
        registry.all().forEach(ViewController<*>::destroy)
        fixture.uninstall()
        ScreenFocus.blur = blur
        DefaultOptions.set(Options())
        ScreenReadiness.reset()
    }

    @Test
    fun theOldRootsScreenLetsGoOfTheFocusAsTheTransitionStarts() {
        setRoot(screen("Home1", blurring()))
        entries.clear()
        setRoot(screen("Profile2"))
        assertEquals(listOf("blur", "will:Profile2", "didDisappear:Home1"), entries.take(3))
        assertEquals(1, entries.count { it == "blur" })
    }

    @Test
    fun aScreenWithoutTheOptionKeepsTheFocus() {
        setRoot(screen("Home1"))
        entries.clear()
        setRoot(screen("Profile2", blurring()))
        assertEquals(0, entries.count { it == "blur" })
    }

    @Test
    fun theOptionComesFromTheResolvedOptions() {
        val home = screen("Home1")
        val stack = TestLayout(activity, registry, "Stack1", blurring())
        stack.add(home)
        setRoot(stack)
        entries.clear()
        setRoot(screen("Profile2"))
        assertEquals(1, entries.count { it == "blur" })

        DefaultOptions.set(blurring())
        entries.clear()
        setRoot(screen("Details3"))
        assertEquals(1, entries.count { it == "blur" })

        DefaultOptions.set(Options())
        val details = coordinator.root
        details?.mergeOptions(blurring())
        entries.clear()
        setRoot(screen("Settings4"))
        assertEquals(1, entries.count { it == "blur" })
    }
}

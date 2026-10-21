package dev.apexnative.navigation.viewcontrollers.navigator

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The pending root and its stack: a root that waits for its screens waits for those it shows now when a stack of it
 * changes its top, so a screen a command took away is not waited for; a root destroyed while it waits ends its wait
 * with the answer (`rootScreensChanged`).
 */
class RootScreensChangedTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val ready = mutableSetOf<String>()
    private val waiting = mutableMapOf<String, MutableList<() -> Unit>>()
    private val readiness = object : Readiness {
        override fun isReady(screen: ViewController<*>): Boolean = screen.id in ready

        override fun whenReady(screen: ViewController<*>, listener: () -> Unit) {
            if (isReady(screen)) listener() else waiting.getOrPut(screen.id) { mutableListOf() } += listener
        }

        override fun markReady(screen: ViewController<*>) {
            journal.entries += "markReady:${screen.id}"
        }
    }
    private val layer = object : RootCoordinator.Layer {
        override fun place(root: ViewController<*>) = Unit

        override fun raise(root: ViewController<*>) = Unit

        override fun show(root: ViewController<*>) = Unit

        override fun remove(root: ViewController<*>) = Unit

        override fun fadeThrough(old: ViewController<*>, new: ViewController<*>, onEnd: () -> Unit): () -> Unit {
            onEnd()
            return {}
        }
    }
    private val coordinator = RootCoordinator(
        layer = layer,
        readiness = readiness,
        scheduler = fixture.scheduler,
        firstRoot = FirstRootDraw(fixture.scheduler) {},
        onRootChanged = {},
        onRootShown = {},
    )

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    private fun mount(id: String) {
        ready += id
        waiting.remove(id)?.forEach { it() }
    }

    private fun root(waitForRender: Boolean?): Pair<TestLayout, RootScreen> {
        val options = Options().apply { waitForRender?.let { animations.setRoot.enter.waitForRender = Bool(it) } }
        val stack = TestLayout(activity, registry, "Stack1", options)
        val first = RootScreen(activity, registry, "Home", Options(), native = false)
        stack.add(first)
        coordinator.setRoot({ stack }, CommandAnswer(
            "setRoot", "setRoot1", FakePromise(journal), ReloadCoordinator.generation, FakeEvents(journal), { 0L },
        ))
        return stack to first
    }

    @Test
    fun theRootWaitsForTheTopItShowsNow() {
        val (stack, home) = root(waitForRender = true)
        assertSame(stack, coordinator.waitingRoot)
        // A setStackRoot into the waiting root's stack replaced its top.
        val details = RootScreen(activity, registry, "Details", Options(), native = false)
        stack.add(details)
        home.destroy()
        coordinator.rootScreensChanged()
        assertSame(stack, coordinator.waitingRoot)
        mount("Details")
        assertTrue("resolve:Stack1" in journal.entries)
        assertEquals(null, coordinator.waitingRoot)
    }

    @Test
    fun aTopThatIsReadyEndsTheWait() {
        val (stack, _) = root(waitForRender = null)
        ready += "Details"
        stack.add(RootScreen(activity, registry, "Details", Options(), native = false))
        coordinator.rootScreensChanged()
        assertTrue("resolve:Stack1" in journal.entries)
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun theLimitMarksTheScreensWaitedForNow() {
        val (stack, _) = root(waitForRender = null)
        stack.add(RootScreen(activity, registry, "Details", Options(), native = false))
        coordinator.rootScreensChanged()
        fixture.scheduler.advance(RootCoordinator.WAIT_LIMIT_MILLIS)
        assertTrue("markReady:Details" in journal.entries)
        assertTrue("markReady:Home" !in journal.entries)
        assertTrue("resolve:Stack1" in journal.entries)
    }

    @Test
    fun aRootDestroyedWhileItWaitsAnswers() {
        val (stack, _) = root(waitForRender = true)
        stack.destroy()
        coordinator.rootScreensChanged()
        assertEquals(listOf("reject:${ErrorTexts.rootDestroyed("Stack1")}"), journal.entries.filter { "reject" in it })
        assertEquals(null, coordinator.waitingRoot)
    }
}

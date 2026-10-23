package dev.apexnative.navigation.react.events

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.parent.ScreensLeavingWithNode
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The end of a transition, and the events of the nodes around it: `ScreenPopped`, `ModalDismissed`,
 * `NativeScreenReleased`.
 */
class TransitionEventsTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val entries = journal.entries
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    private fun screen(id: String, name: String = id) = ReportingScreen(activity, registry, id, name)

    private fun answer(name: String) =
        CommandAnswer(name, "${name}3", FakePromise(journal), ReloadCoordinator.generation, FakeEvents(journal), { 0L })

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    @Test
    fun endTransitionGoesInTheOrderOfACommand() {
        val home = screen("Home")
        val details = screen("Details")
        home.onViewDidAppear()
        entries.clear()
        AppearanceDispatcher.beginTransition(listOf(details))
        AppearanceDispatcher.endTransition(
            hiding = listOf(home),
            showing = listOf(details),
            events = { entries += "events" },
            answer = answer("push"),
            value = "Details",
        )
        assertEquals(
            listOf(
                "will:Details",
                "didDisappear:Home",
                "stateNeeded",
                "didAppear:Details",
                "stateNeeded",
                "stateNow",
                "events",
                "state",
                "resolve:Details",
                "completed:push",
            ),
            entries,
        )
    }

    @Test
    fun aStateThatFailsKeepsTheEventsAndTheAnswer() {
        val logged = mutableListOf<String>()
        AppearanceDispatcher.log = { logged += it }
        AppearanceDispatcher.state = object : AppearanceDispatcher.StateUpdates {
            override fun setNeedsUpdate() = Unit

            override fun updateNow() = throw IllegalStateException("no root")

            override fun hold() = Unit

            override fun release() = Unit
        }
        AppearanceDispatcher.endTransition(emptyList(), emptyList(), { entries += "events" }, answer("push"), "Details")
        AppearanceDispatcher.endTransition(emptyList(), emptyList(), { entries += "userEvents" }, null, null)
        assertEquals(listOf("events", "state", "resolve:Details", "completed:push", "userEvents"), entries)
        assertEquals(
            listOf(
                "[ApexNavigation] push: could not report the navigation state: no root",
                "[ApexNavigation] transition: could not report the navigation state: no root",
            ),
            logged,
        )
    }

    @Test
    fun aUsersActionEndsWithoutAnAnswer() {
        val home = screen("Home")
        val details = screen("Details")
        details.onViewDidAppear()
        entries.clear()
        AppearanceDispatcher.beginTransition(listOf(home))
        AppearanceDispatcher.endTransition(listOf(details), listOf(home), { entries += "events" }, null, null)
        assertEquals(
            listOf(
                "will:Home",
                "didDisappear:Details",
                "stateNeeded",
                "didAppear:Home",
                "stateNeeded",
                "stateNow",
                "events",
            ),
            entries,
        )
    }

    @Test
    fun endTransitionAsksWillAppearWhereItWasNotAsked() {
        val details = screen("Details")
        AppearanceDispatcher.endTransition(emptyList(), listOf(details), null, null, null)
        assertEquals(listOf("will:Details", "didAppear:Details", "stateNeeded", "stateNow"), entries)
    }

    @Test
    fun aScreenPoppedAlone() {
        AppearanceDispatcher.screenPopped(screen("Details"))
        assertEquals(listOf("popped:Details"), entries)
    }

    @Test
    fun aPairPopsBothItsScreens() {
        val primary = screen("List")
        val secondary = screen("Details")
        AppearanceDispatcher.screenPopped(Pair(activity, registry, primary, secondary))
        assertEquals(listOf("popped:List", "popped:Details"), entries)
    }

    @Test
    fun aScreenOfAJavaScriptThatWentPopsSilently() {
        val details = screen("Details")
        ReloadCoordinator.advance()
        AppearanceDispatcher.screenPopped(details)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun modalDismissedNamesTheScreenTheModalShows() {
        val stack = TestLayout(activity, registry, "Stack1")
        stack.add(component("Home1", "Home"))
        stack.add(component("Details2", "slot:Details"))
        AppearanceDispatcher.modalDismissed(stack, 2)
        assertEquals(listOf("modalDismissed:Details2:Details:2"), entries)
    }

    @Test
    fun aModalThatShowsNoScreenNamesItsRootInItsEvents() {
        val modal = TestLayout(activity, registry, "Modal1")
        modal.add(TestLayout(activity, registry, "Tabs1"))
        AppearanceDispatcher.modalDismissed(modal, 1)
        AppearanceDispatcher.modalAttemptedToDismiss(modal)
        AppearanceDispatcher.sheetDetentChanged(modal, "large")
        // What [AppearanceDispatcher.modalScreen] gave names the same node again.
        AppearanceDispatcher.modalDismissed(AppearanceDispatcher.modalScreen(modal), 1)
        assertEquals(
            listOf("modalDismissed:Modal1::1", "attempted:Modal1", "detent:Modal1:large", "modalDismissed:Modal1::1"),
            entries,
        )
    }

    @Test
    fun aModalEventNamesTheScreenAtTheEndOfTheChain() {
        val modal = TestLayout(activity, registry, "Modal1")
        modal.add(component("Details2", "Details"))
        AppearanceDispatcher.modalAttemptedToDismiss(modal)
        AppearanceDispatcher.sheetDetentChanged(modal, "medium")
        assertEquals(listOf("attempted:Details2", "detent:Details2:medium"), entries)
    }

    @Test
    fun aModalOfAJavaScriptThatWentIsDismissedSilently() {
        val stack = TestLayout(activity, registry, "Stack1")
        stack.add(component("Home1", "Home"))
        ReloadCoordinator.advance()
        AppearanceDispatcher.modalDismissed(stack, 1)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun aNativeScreenIsReleasedOnce() {
        val profile = screen("Profile")
        AppearanceDispatcher.nativeScreenReleased(profile)
        AppearanceDispatcher.nativeScreenReleased(profile)
        assertEquals(listOf("released:Profile"), entries)
    }

    @Test
    fun aNativeScreenOfAJavaScriptThatWentIsReleasedSilently() {
        val profile = screen("Profile")
        ReloadCoordinator.advance()
        AppearanceDispatcher.nativeScreenReleased(profile)
        assertTrue(entries.isEmpty())
    }

    private fun component(id: String, name: String) =
        ComponentViewController(activity, registry, id, name, Presenter(activity, Options()), Options())

    // A pair: both its screens leave a stack with it.
    private class Pair(
        activity: Activity,
        registry: ChildControllersRegistry,
        primary: ViewController<*>,
        secondary: ViewController<*>,
    ) : TestLayout(activity, registry, "Pair1"), ScreensLeavingWithNode {
        override val screensLeavingWithNode: List<ViewController<*>> = listOf(primary, secondary)
    }
}

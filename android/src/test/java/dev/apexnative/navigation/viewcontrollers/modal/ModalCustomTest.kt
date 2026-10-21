package dev.apexnative.navigation.viewcontrollers.modal

import android.app.Activity
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.ElementTransitions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.navigator.RootScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The modals' animations of their own: what the coordinator asks of the views for a show and a dismissal, a side only,
 * `enabled: false`, the wait for the elements' targets in the modal's wait, and the sheet's motion that takes none of
 * them.
 */
class ModalCustomTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val views = FakeModalViews()
    private val readiness = IdReadiness()
    private val host = FakeModalHost(registry)
    private val modals = ModalCoordinator(views, readiness, fixture.scheduler, host)
    private var commands = 0

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    private fun screen(id: String, options: Options = Options(), ready: Boolean = true) =
        RootScreen(activity, registry, id, options, native = false).also { if (ready) readiness.ready += id }

    private fun answer(name: String) = CommandAnswer(
        name,
        "$name${++commands}",
        FakePromise(journal),
        ReloadCoordinator.generation,
        FakeEvents(journal),
        { 0L },
    )

    private fun show(node: ViewController<*>) = modals.show({ node }, answer("showModal"))

    private fun home(): ViewController<*> {
        val home = screen("Home")
        host.root = home
        home.onViewWillAppear()
        home.onViewDidAppear()
        journal.entries.clear()
        return home
    }

    private fun options(change: Options.() -> Unit) = Options().apply(change)

    // A modal shown at once, before the test goes on.
    private fun shown(id: String, options: Options): ViewController<*> {
        views.instant = true
        val node = screen(id, options)
        show(node)
        views.instant = false
        views.log.clear()
        journal.entries.clear()
        return node
    }

    @Test
    fun aShowOfItsOwnPlaysItsSidesOverTheRoot() {
        home()
        show(screen("Details", options {
            animations.showModal.enter.translationY.from = Fraction(400.0)
            animations.showModal.exit.scaleX.to = Fraction(0.9)
        }))
        assertEquals(listOf("place:Details", "present:Details:animated:own(translationY scaleX@Home)"), views.log)
        views.endShow("Details")
        assertTrue(journal.entries.contains("resolve:Details"))
    }

    // A show with only `exit`: the modal is there on the first frame, the screen under goes by `exit`.
    @Test
    fun aShowWithOnlyTheExitSide() {
        home()
        show(screen("Details", options { animations.showModal.exit.alpha.to = Fraction(0.0) }))
        assertEquals("present:Details:animated:own(- alpha@Home)", views.log.last())
    }

    // Without an animation of its own the default slide plays; `enter.enabled: false` turns the show off.
    @Test
    fun theDefaultAndTurnedOff() {
        home()
        show(screen("Details", options { animations.showModal.enter.waitForRender = Bool(true) }))
        assertEquals("present:Details:animated", views.log.last())
        views.endShow("Details")
        show(screen("Profile", options {
            animations.showModal.enter.enabled = Bool(false)
            animations.showModal.enter.alpha.from = Fraction(0.0)
        }))
        assertTrue(views.log.contains("present:Profile:at once"))
    }

    // A sheet moves only by its Material motion.
    @Test
    fun aSheetTakesNoneOfThem() {
        home()
        show(screen("Details", options {
            modal.sheet.grabber = Bool(true)
            animations.showModal.enter.alpha.from = Fraction(0.0)
        }))
        assertEquals("present:Details:animated", views.log.last())
    }

    // A dismissal with only `enter`: the modal stands to the end and goes; the screen under plays `enter`.
    @Test
    fun aDismissalOfItsOwn() {
        home()
        shown("Details", options {
            animations.dismissModal.enter.alpha.from = Fraction(0.5)
        })
        modals.dismiss("Details", null, answer("dismissModal"))
        assertEquals(listOf("putBackRoot:Home", "dismiss:Details:animated:own(- alpha@Home)"), views.log)
        views.endDismiss("Details")
        assertTrue(journal.entries.any { it == "resolve:Details" })
    }

    // `dismissModal`: the command's options merge first; `exit.enabled: false` closes without animation.
    @Test
    fun aDismissalTurnedOffByTheCommand() {
        home()
        shown("Details", options { animations.dismissModal.exit.alpha.to = Fraction(0.0) })
        modals.dismiss("Details", options { animations.dismissModal.exit.enabled = Bool(false) }, answer("dismiss"))
        assertTrue(views.log.contains("dismiss:Details:at once"))
    }

    // `dismissAllModals` takes the dismissal's animation of the top modal, over what the batch reveals.
    @Test
    fun dismissAllTakesTheTopModalsDismissal() {
        home()
        shown("Details", Options())
        shown("Profile", options { animations.dismissModal.exit.translationY.to = Fraction(600.0) })
        modals.dismissAll(null, null, answer("dismissAllModals"))
        assertEquals("dismiss:Profile:animated:own(translationY -@Home)", views.log.last())
    }

    // A show with elements waits for their targets after its content, still waiting; the modal fades in and the screen
    // under stands; a dismissal during that wait cancels the show.
    @Test
    fun aShowWithElementsWaitsForItsTargets() {
        home()
        val elements = options {
            animations.showModal.elementTransitions = ElementTransitions(listOf(ElementTransitionOptions("Avatar")))
            animations.showModal.exit.alpha.to = Fraction(0.0)
        }
        show(screen("Details", elements))
        assertEquals(listOf("place:Details", "awaitTargets:Details:Home"), views.log)
        assertTrue(journal.entries.isEmpty())
        assertTrue(views.awaitsTargets("Details"))
        views.findTargets("Details")
        assertEquals("present:Details:animated:own(- -@Home fade elements:Home>Details)", views.log.last())
        views.endShow("Details")
        views.log.clear()
        show(screen("Profile", elements))
        assertTrue(views.awaitsTargets("Profile"))
        modals.dismiss("Profile", null, answer("dismissModal"))
        assertEquals(listOf("place:Profile", "awaitTargets:Profile:Details", "stopTargets:Profile", "remove:Profile"),
            views.log)
        assertTrue(journal.entries.contains("resolve:Profile"))
        assertEquals(0, fixture.scheduler.pending)
    }

    // Without `waitForRender` the targets get what is left of the modal's 0.5 s; `false` waits for none.
    @Test
    fun theTargetsWaitWithinTheModalsLimit() {
        home()
        val elements = ElementTransitions(listOf(ElementTransitionOptions("Avatar")))
        show(screen("Details", options { animations.showModal.elementTransitions = elements }, ready = false))
        fixture.scheduler.advance(ModalCoordinator.WAIT_LIMIT_MILLIS)
        // The content's limit passed: the targets get nothing of it, their wait ends at once.
        assertTrue(views.awaitsTargets("Details"))
        assertFalse(views.targetsWaiting("Details"))
        views.findTargets("Details")
        assertTrue(views.log.last().startsWith("present:Details:animated:own("))
        views.endShow("Details")
        views.log.clear()
        show(screen("Profile", options {
            animations.showModal.elementTransitions = elements
            animations.showModal.enter.waitForRender = Bool(false)
        }))
        assertFalse(views.log.any { it.startsWith("awaitTargets:Profile") })
    }
}

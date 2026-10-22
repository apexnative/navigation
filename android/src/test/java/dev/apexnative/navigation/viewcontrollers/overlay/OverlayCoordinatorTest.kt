package dev.apexnative.navigation.viewcontrollers.overlay

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
import dev.apexnative.navigation.viewcontrollers.modal.IdReadiness
import dev.apexnative.navigation.viewcontrollers.navigator.RootScreen
import dev.apexnative.navigation.viewcontrollers.navigator.WaitingLayer
import dev.apexnative.navigation.viewcontrollers.overlay.OverlayTouches.Place
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The overlays of one activity: the wait, the events and the answers of a show and a dismissal, the same id replacing,
 * the lookups of `dismissOverlay`, `dismissAllOverlays` last to first, `interceptTouchOutside` kept from the options,
 * and the touch decision.
 */
class OverlayCoordinatorTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val entries = journal.entries
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val readiness = IdReadiness()
    private val views = Views()
    private val host = Host()
    private val overlays = OverlayCoordinator(views, readiness, fixture.scheduler, host)
    private var commands = 0

    private class Views : OverlayCoordinator.Views {
        val log = mutableListOf<String>()
        // Throws once, at the next removal: a view that fails.
        var failRemove = false

        override fun place(record: OverlayRecord) {
            log += "place:${record.rootId}"
        }

        override fun show(record: OverlayRecord) {
            log += "show:${record.rootId}"
        }

        override fun remove(record: OverlayRecord) {
            if (failRemove) {
                failRemove = false
                error("no view")
            }
            log += "remove:${record.rootId}"
        }

        override fun update(record: OverlayRecord, options: Options) {
            log += "update:${record.rootId}"
        }
    }

    private inner class Host : OverlayCoordinator.Host {
        val waiting = mutableListOf<WaitingLayer>()
        val logged = mutableListOf<String>()

        override fun find(id: String): ViewController<*>? = registry.find(id)

        override fun addWaitingLayer(layer: WaitingLayer) {
            waiting += layer
        }

        override fun removeWaitingLayer(layer: WaitingLayer) {
            waiting -= layer
        }

        override fun log(text: String) {
            logged += text
        }
    }

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

    private fun show(node: ViewController<*>) = overlays.show({ node }, answer("showOverlay"))

    private fun shown(id: String, options: Options = Options()): ViewController<*> {
        val node = screen(id, options)
        show(node)
        entries.clear()
        views.log.clear()
        return node
    }

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    @Test
    fun anOverlayWaitsForItsContentThenShowsAtOnce() {
        show(screen("Toast", ready = false))
        assertEquals(listOf("place:Toast"), views.log)
        assertTrue(entries.isEmpty())
        assertEquals(1, host.waiting.size)
        readiness.mount("Toast")
        assertEquals(
            listOf(
                "will:Toast",
                "didAppear:Toast",
                "stateNeeded",
                "stateNow",
                "state",
                "resolve:Toast",
                "completed:showOverlay",
            ),
            entries,
        )
        assertEquals(listOf("place:Toast", "update:Toast", "show:Toast"), views.log)
        assertTrue(host.waiting.isEmpty())
    }

    @Test
    fun anOverlaysNodesAreInAnOverlayBeforeItsViewsArePlaced() {
        val stack = TestLayout(activity, registry, "Overlay1")
        val inner = screen("Inner")
        stack.add(inner)
        var placedInOverlay = false
        val placing = object : OverlayCoordinator.Views {
            override fun place(record: OverlayRecord) {
                placedInOverlay = record.root.isInOverlay && inner.isInOverlay
            }

            override fun show(record: OverlayRecord) = Unit

            override fun remove(record: OverlayRecord) = Unit

            override fun update(record: OverlayRecord, options: Options) = Unit
        }
        OverlayCoordinator(placing, readiness, fixture.scheduler, host).show({ stack }, answer("showOverlay"))
        assertTrue(placedInOverlay)
        assertFalse(screen("Home").isInOverlay)
    }

    @Test
    fun itsWaitEndsAtTheLimit() {
        show(screen("Toast", ready = false))
        fixture.scheduler.advance(OverlayCoordinator.WAIT_LIMIT_MILLIS)
        assertEquals(listOf("Toast"), readiness.marked)
        assertTrue("resolve:Toast" in entries)
    }

    @Test
    fun anOverlayWithTheIdOfAShownOneReplacesItAfterItsWait() {
        val old = shown("Toast")
        // The readiness of the tests goes by id: the new screen with the old one's id renders later.
        readiness.ready -= "Toast"
        val new = screen("Toast", ready = false)
        show(new)
        assertSame(new, registry.find("Toast"))
        assertTrue(entries.isEmpty())
        readiness.mount("Toast")
        assertTrue(old.isDestroyed)
        assertSame(new, registry.find("Toast"))
        assertEquals(listOf("didDisappear:Toast"), entries.filter { it.startsWith("didDisappear") })
        assertEquals(listOf("resolve:Toast"), entries.filter { it.startsWith("resolve") })
        assertEquals(listOf("place:Toast", "remove:Toast", "update:Toast", "show:Toast"), views.log)
        assertEquals(1, overlays.overlays.size)
    }

    @Test
    fun aReplacedOverlayWhoseRemovalFailsRejectsTheNewShowAndLeavesNoView() {
        val old = shown("Toast")
        readiness.ready -= "Toast"
        val new = screen("Toast", ready = false)
        show(new)
        views.failRemove = true
        // The removal of the shown one comes in the wait's signal, under no runner.
        readiness.mount("Toast")
        assertEquals("reject:${ErrorTexts.couldNot("show the screen 'Toast'", "no view")}", entries.last())
        assertTrue(old.isDestroyed)
        assertTrue(new.isDestroyed)
        assertTrue(overlays.overlays.isEmpty())
        assertTrue(host.waiting.isEmpty())
        assertEquals(listOf("place:Toast", "remove:Toast"), views.log)
    }

    @Test
    fun aWaitThatFailsAsItStartsRejectsAndLeavesNoOverlay() {
        readiness.failWhenReady = true
        val toast = screen("Toast", ready = false)
        show(toast)
        // The wait starts under the record's guard: nothing stays placed or waiting.
        assertEquals("reject:${ErrorTexts.couldNot("show the screen 'Toast'", "no readiness")}", entries.last())
        assertTrue(toast.isDestroyed)
        assertTrue(overlays.overlays.isEmpty())
        assertTrue(host.waiting.isEmpty())
        assertEquals(listOf("place:Toast", "remove:Toast"), views.log)
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aWaitingOverlayOfTheSameIdIsCancelled() {
        val first = screen("Toast", ready = false)
        show(first)
        show(screen("Toast", ready = false))
        assertTrue(first.isDestroyed)
        assertEquals(listOf("state", "resolve:Toast", "completed:showOverlay"), entries)
        assertTrue(entries.none { it.startsWith("will") })
    }

    @Test
    fun dismissOverlayTakesTheRootsIdOnly() {
        val stack = TestLayout(activity, registry, "Overlay1")
        stack.add(screen("Inner"))
        show(stack)
        entries.clear()
        overlays.dismiss("Inner", answer("dismissOverlay"))
        overlays.dismiss("Nobody", answer("dismissOverlay"))
        assertEquals(
            listOf(
                "reject:${ErrorTexts.notInOverlay("Inner")}",
                "reject:${ErrorTexts.screenNotShown("Nobody")}",
            ),
            entries,
        )
        entries.clear()
        overlays.dismiss("Overlay1", answer("dismissOverlay"))
        assertEquals(
            listOf(
                "didDisappear:Inner",
                "stateNeeded",
                "stateNow",
                "state",
                "resolve:Overlay1",
                "completed:dismissOverlay",
            ),
            entries,
        )
        assertTrue(stack.isDestroyed)
        assertTrue(overlays.overlays.isEmpty())
    }

    @Test
    fun dismissOverlayOfAShownOneWithAWaitingReplacementTakesBoth() {
        val old = shown("Toast")
        // The readiness of the tests goes by id: the new screen with the old one's id renders later.
        readiness.ready -= "Toast"
        val new = screen("Toast", ready = false)
        show(new)
        overlays.dismiss("Toast", answer("dismissOverlay"))
        assertTrue(old.isDestroyed)
        assertTrue(new.isDestroyed)
        assertEquals(listOf("resolve:Toast", "resolve:Toast"), entries.filter { it.startsWith("resolve") })
        assertTrue(overlays.overlays.isEmpty())
        readiness.mount("Toast")
        assertTrue(entries.none { it.startsWith("will") })
    }

    @Test
    fun dismissAllOverlaysGoesLastToFirstAndAnswersEmpty() {
        shown("First")
        shown("Second")
        overlays.dismissAll(answer("dismissAllOverlays"))
        assertEquals(
            listOf("didDisappear:Second", "didDisappear:First"),
            entries.filter { it.startsWith("didDisappear") },
        )
        assertEquals(listOf("state", "resolve:", "completed:dismissAllOverlays"), entries.takeLast(3))
        entries.clear()
        overlays.dismissAll(answer("dismissAllOverlays"))
        assertEquals(listOf("state", "resolve:", "completed:dismissAllOverlays"), entries)
    }

    @Test
    fun interceptTouchOutsideIsKeptFromTheOptionsAndReadAgainAfterAMerge() {
        val blocks = Options().apply { overlay.interceptTouchOutside = Bool(true) }
        val dialog = shown("Dialog", blocks)
        assertTrue(overlays.overlays.single().intercepts)
        dialog.mergeOptions(Options().apply { overlay.interceptTouchOutside = Bool(false) })
        assertTrue(overlays.overlays.single().intercepts)
        overlays.optionsMergedInto(dialog)
        assertFalse(overlays.overlays.single().intercepts)
        assertTrue(overlays.isInOverlay(dialog))
    }

    @Test
    fun theActivityGoingAnswersAWaitingShowAndAReloadNothing() {
        shown("Toast")
        show(screen("Waiting", ready = false))
        overlays.destroyAll()
        assertTrue("resolve:Waiting" in entries)
        assertTrue("didDisappear:Toast" in entries)
        assertTrue(host.waiting.isEmpty())
        entries.clear()
        show(screen("Later", ready = false))
        entries.clear()
        ReloadCoordinator.advance()
        overlays.destroyAll()
        assertTrue(entries.isEmpty())
    }

    // A `setRoot`'s overlay sends no state of its own: it goes once, before the `setRoot`'s answer.
    @Test
    fun anOverlayOfASetRootSendsNoStateOfItsOwn() {
        var done = 0
        overlays.showInChain("Toast", { screen("Toast") }) { done++ }
        assertEquals(1, done)
        assertEquals(listOf("will:Toast", "didAppear:Toast", "stateNeeded"), entries)
    }

    // The bar and the tabs over a screen's content are the overlay's own views.
    @Test
    fun theBarsOverAScreenAreTheOverlays() {
        assertTrue(OverlayTouches.isOnBars(50f, headerBottom = 100, tabsTop = null))
        assertFalse(OverlayTouches.isOnBars(150f, headerBottom = 100, tabsTop = 900))
        assertTrue(OverlayTouches.isOnBars(900f, headerBottom = 100, tabsTop = 900))
        assertFalse(OverlayTouches.isOnBars(50f, headerBottom = null, tabsTop = null))
    }

    @Test
    fun theTouchDecision() {
        assertTrue(OverlayTouches.takes(true, Place.Outside, 0, 0))
        assertTrue(OverlayTouches.takes(false, Place.Content, 12, 11))
        assertFalse(OverlayTouches.takes(false, Place.Content, 11, 11))
        assertTrue(OverlayTouches.takes(false, Place.OwnView, -1, -1))
        assertFalse(OverlayTouches.takes(false, Place.Screen, -1, -1))
        assertFalse(OverlayTouches.takes(false, Place.Outside, -1, -1))
    }
}

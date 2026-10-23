package dev.apexnative.navigation.viewcontrollers.navigator

import android.app.Activity
import dev.apexnative.navigation.layout.LayoutNode
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.layout.RootLayout
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `setRoot` of an activity's navigation: what the layout factory makes (every type of node), and a React screen as
 * the root, which shows once its safe area view says its content rendered, or at the limit of the wait with its held
 * events marked ready.
 */
class NavigatorSetRootTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val entries = journal.entries
    private val activity = Activity()
    private val navigator = Navigator(activity)

    private fun node(type: LayoutType, id: String, name: String? = null, vararg children: LayoutNode) = LayoutNode(
        id = id,
        type = type,
        name = name,
        options = Options(),
        key = null,
        passProps = null,
        functions = emptyList(),
        columns = emptyList(),
        arrangement = null,
        children = children.toList(),
    )

    private fun screen(id: String, name: String = "Home") = node(LayoutType.Component, id, name)

    private fun answer() = CommandAnswer(
        "setRoot",
        "setRoot1",
        FakePromise(journal),
        ReloadCoordinator.generation,
        FakeEvents(journal),
        { 0L },
    )

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() {
        fixture.uninstall()
        ScreenReadiness.remove("Home1")
    }

    @Test
    fun theFactoryMakesScreens() {
        val factory = LayoutFactory(activity, navigator.registry)
        assertTrue(factory.canMake(screen("Home1")))
        assertTrue(factory.canMake(node(LayoutType.Stack, "Stack1", null, screen("Home1"))))
        val columns = listOf(screen("Home1"), screen("Details1", "Details"))
        assertTrue(factory.canMake(node(LayoutType.SplitView, "Split1", null, *columns.toTypedArray())))
        assertTrue(factory.canMake(node(LayoutType.Arrangement, "Pair1", null, *columns.toTypedArray())))
        val made = factory.create(screen("Home1", "slot:Home"))
        assertTrue(made is ComponentViewController)
        assertEquals("slot:Home", (made as ComponentViewController).componentName)
        assertEquals("Home", made.currentComponentName)
        assertSame(made, navigator.registry.find("Home1"))
        made.destroy()
    }

    @Test
    fun anOverlayStaysOverANewRootAndItsIdCantComeInTheLayout() {
        navigator.showOverlay(screen("Toast1", "Toast"), CommandAnswer(
            "showOverlay",
            "showOverlay1",
            FakePromise(journal),
            ReloadCoordinator.generation,
            FakeEvents(journal),
            { 0L },
        ))
        ScreenReadiness.contentRendered("Toast1")
        assertTrue("resolve:Toast1" in entries)
        entries.clear()
        navigator.setRoot(RootLayout(screen("Home1"), listOf(screen("Toast1", "Toast")), emptyList()), answer())
        assertEquals(listOf("reject:${ErrorTexts.alreadyShown("Toast1")}"), entries)
        navigator.setRoot(RootLayout(screen("Home1"), emptyList(), emptyList()), answer())
        ScreenReadiness.contentRendered("Home1")
        assertTrue("resolve:Home1" in entries)
        assertEquals(1, navigator.overlays.overlays.size)
        navigator.destroyWithActivity()
        assertTrue(navigator.overlays.overlays.isEmpty())
        ScreenReadiness.remove("Toast1")
    }

    @Test
    fun aReactScreenShowsOnceItsContentRendered() {
        navigator.setRoot(RootLayout(screen("Home1"), emptyList(), emptyList()), answer())
        assertTrue(entries.isEmpty())
        assertFalse(navigator.isFirstRootDrawn)
        ScreenReadiness.contentRendered("Home1")
        assertEquals(
            listOf(
                "will:Home1",
                "didAppear:Home1",
                "stateNeeded",
                "state",
                "resolve:Home1",
                "completed:setRoot",
            ),
            entries,
        )
        assertSame(navigator.registry.find("Home1"), navigator.root)
        assertTrue(navigator.isFirstRootDrawn)
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aReactScreenThatDoesntRenderShowsAtTheLimitWithItsEvents() {
        navigator.setRoot(RootLayout(screen("Home1"), emptyList(), emptyList()), answer())
        fixture.scheduler.advance(RootCoordinator.WAIT_LIMIT_MILLIS)
        assertEquals("will:Home1", entries.first())
        assertTrue("didAppear:Home1" in entries)
        assertTrue("resolve:Home1" in entries)
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aRootWithTheIdsOfTheCurrentOneWaitsForItsOwnContent() {
        navigator.setRoot(RootLayout(screen("Home1"), emptyList(), emptyList()), answer())
        ScreenReadiness.contentRendered("Home1")
        val first = navigator.root
        entries.clear()
        navigator.setRoot(RootLayout(screen("Home1"), emptyList(), emptyList()), answer())
        assertTrue(entries.isEmpty())
        assertSame(first, navigator.root)
        ScreenReadiness.contentRendered("Home1")
        assertTrue("resolve:Home1" in entries)
        assertTrue(first?.isDestroyed == true)
        assertSame(navigator.registry.find("Home1"), navigator.root)
        assertEquals(true, ScreenReadiness.isContentRendered("Home1"))
    }

    @Test
    fun theActivityGoingTakesTheRootAndEmptiesTheRegistry() {
        navigator.setRoot(RootLayout(screen("Home1"), emptyList(), emptyList()), answer())
        navigator.destroyWithActivity()
        assertEquals(listOf("state", "resolve:", "completed:setRoot"), entries)
        assertTrue(navigator.registry.all().isEmpty())
        assertEquals(null, navigator.root)
    }
}

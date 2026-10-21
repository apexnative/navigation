package dev.apexnative.navigation.react.events

import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.ScreenAppearance.Phase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The appearance of one record: one WillAppear and one DidAppear per appearance, DidDisappear only after a WillAppear
 * that went, the hold until the content mounted or 0.5 s, the state asked after each sent Did*, silence for a
 * JavaScript that went.
 */
class AppearanceDispatcherTest {
    private val fixture = DispatcherFixture()
    private val entries = fixture.journal.entries
    private val scheduler = fixture.scheduler

    private fun record(content: FakeContent? = null, id: String = "Home1", name: String = "Home") =
        ScreenAppearance(id, name, ComponentType.Component, content)

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    @Test
    fun willAppearOncePerAppearance() {
        val home = record()
        AppearanceDispatcher.willAppear(home)
        AppearanceDispatcher.willAppear(home)
        assertEquals(listOf("will:Home1"), entries)
        assertEquals(Phase.Appearing, home.phase)
    }

    @Test
    fun didAppearFromHiddenAsksWillAppearFirst() {
        val home = record()
        AppearanceDispatcher.didAppear(home)
        AppearanceDispatcher.didAppear(home)
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
        assertEquals(Phase.Shown, home.phase)
    }

    @Test
    fun didDisappearOnlyAfterAnAppearance() {
        val home = record()
        AppearanceDispatcher.didDisappear(home)
        assertTrue(entries.isEmpty())
        AppearanceDispatcher.didAppear(home)
        AppearanceDispatcher.didDisappear(home)
        AppearanceDispatcher.didDisappear(home)
        assertEquals(
            listOf("will:Home1", "didAppear:Home1", "stateNeeded", "didDisappear:Home1", "stateNeeded"),
            entries,
        )
        assertEquals(Phase.Hidden, home.phase)
    }

    @Test
    fun aCancelledSwipeGivesWillAppearThenDidDisappear() {
        val home = record()
        AppearanceDispatcher.willAppear(home)
        AppearanceDispatcher.didDisappear(home)
        assertEquals(listOf("will:Home1", "didDisappear:Home1", "stateNeeded"), entries)
    }

    @Test
    fun aNewPairAfterADisappearance() {
        val home = record()
        AppearanceDispatcher.didAppear(home)
        AppearanceDispatcher.didDisappear(home)
        entries.clear()
        AppearanceDispatcher.willAppear(home)
        AppearanceDispatcher.didAppear(home)
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
    }

    @Test
    fun theEventsCarryTheRecordsNameAndType() {
        val title = ScreenAppearance("Title2", "TitleView", ComponentType.TopBarTitle, null)
        AppearanceDispatcher.didAppear(title)
        AppearanceDispatcher.didDisappear(title)
        assertEquals(
            listOf(
                "will:Title2:TitleView:TopBarTitle",
                "didAppear:Title2:TitleView:TopBarTitle",
                "didDisappear:Title2:TitleView:TopBarTitle",
            ),
            fixture.journal.payloads,
        )
    }

    @Test
    fun theMountSendsTheHeldEventsInOrder() {
        val content = FakeContent()
        val home = record(content)
        AppearanceDispatcher.willAppear(home)
        AppearanceDispatcher.didAppear(home)
        assertTrue(entries.isEmpty())
        assertEquals(1, scheduler.pending)
        content.mount()
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun contentReadyAtOnceSendsAtOnce() {
        val home = record(FakeContent(ready = true))
        AppearanceDispatcher.didAppear(home)
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun theLimitSendsAfterHalfASecondFromTheFirstHeldEvent() {
        val home = record(FakeContent())
        AppearanceDispatcher.willAppear(home)
        scheduler.advance(300)
        AppearanceDispatcher.didAppear(home)
        scheduler.advance(199)
        assertTrue(entries.isEmpty())
        scheduler.advance(1)
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun afterTheLimitTheContentCountsAsMounted() {
        val home = record(FakeContent())
        AppearanceDispatcher.willAppear(home)
        scheduler.advance(AppearanceDispatcher.HOLD_LIMIT_MILLIS)
        AppearanceDispatcher.didAppear(home)
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
        assertTrue(home.isReady)
    }

    @Test
    fun markReadySendsTheHeldEvents() {
        val home = record(FakeContent())
        AppearanceDispatcher.didAppear(home)
        home.markReady()
        assertEquals(listOf("will:Home1", "didAppear:Home1", "stateNeeded"), entries)
        assertEquals(0, scheduler.pending)
    }

    @Test
    fun markReadyBeforeTheAppearanceSendsAtOnce() {
        val home = record(FakeContent())
        home.markReady()
        AppearanceDispatcher.willAppear(home)
        assertEquals(listOf("will:Home1"), entries)
    }

    @Test
    fun aDisappearanceWhileWillAppearIsHeldSendsNothing() {
        val content = FakeContent()
        val home = record(content)
        AppearanceDispatcher.willAppear(home)
        AppearanceDispatcher.didAppear(home)
        AppearanceDispatcher.didDisappear(home)
        assertEquals(0, scheduler.pending)
        assertEquals(Phase.Hidden, home.phase)
        content.mount()
        scheduler.advance(1_000)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun aHeldDidAppearIsDroppedAndTheNextAppearanceStartsAnew() {
        val content = FakeContent()
        val home = record(content)
        AppearanceDispatcher.didAppear(home)
        AppearanceDispatcher.didDisappear(home)
        AppearanceDispatcher.willAppear(home)
        assertTrue(entries.isEmpty())
        content.mount()
        assertEquals(listOf("will:Home1"), entries)
    }

    @Test
    fun aHeldDidAppearAsksTheStateWhenItGoes() {
        val content = FakeContent()
        val home = record(content)
        AppearanceDispatcher.didAppear(home)
        assertTrue("stateNeeded" !in entries)
        content.mount()
        assertEquals("stateNeeded", entries.last())
    }

    @Test
    fun aRecordOfAJavaScriptThatWentSendsNothing() {
        val home = record()
        ReloadCoordinator.advance()
        AppearanceDispatcher.didAppear(home)
        assertEquals(Phase.Shown, home.phase)
        AppearanceDispatcher.didDisappear(home)
        assertEquals(Phase.Hidden, home.phase)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun heldEventsOfAJavaScriptThatWentAreDropped() {
        val content = FakeContent()
        val home = record(content)
        AppearanceDispatcher.didAppear(home)
        ReloadCoordinator.advance()
        content.mount()
        assertTrue(entries.isEmpty())
        assertEquals(Phase.Shown, home.phase)
    }

    @Test
    fun aSendThatHidesTheRecordStopsTheFlush() {
        val content = FakeContent()
        val home = record(content)
        val journal = fixture.journal
        // JavaScript hides the screen as soon as it hears that it appears.
        AppearanceDispatcher.sender = object : AppearanceDispatcher.Sender by journal {
            override fun componentWillAppear(componentId: String, componentName: String, type: ComponentType) {
                journal.componentWillAppear(componentId, componentName, type)
                AppearanceDispatcher.didDisappear(home)
            }
        }
        AppearanceDispatcher.didAppear(home)
        content.mount()
        assertEquals(listOf("will:Home1", "didDisappear:Home1", "stateNeeded"), entries)
        assertEquals(Phase.Hidden, home.phase)
        assertTrue(home.held.isEmpty())
        assertEquals(0, scheduler.pending)
    }
}

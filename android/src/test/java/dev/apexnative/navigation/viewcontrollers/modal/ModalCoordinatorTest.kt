package dev.apexnative.navigation.viewcontrollers.modal

import android.app.Activity
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SheetDetent
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandListenerAdapter
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.modal.ModalRecord.State
import dev.apexnative.navigation.viewcontrollers.navigator.RootScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The modal records of one activity: the wait before a show, the order of the events of
 * a show, a dismissal, a close below and a batch, the answers once each, `ANN.ModalDismissed` once per close, the layer
 * under, back, the user's closes of a sheet, a `setRoot`, the activity's end and a reload.
 */
class ModalCoordinatorTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val entries = journal.entries
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val views = FakeModalViews()
    private val readiness = IdReadiness()
    private val host = FakeModalHost(registry)
    private val modals = ModalCoordinator(views, readiness, fixture.scheduler, host)
    private var commands = 0

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

    private fun dismiss(id: String, options: Options? = null) = modals.dismiss(id, options, answer("dismissModal"))

    private fun record(id: String): ModalRecord = modals.modals.first { it.rootId == id }

    // The root under the modals, shown, before the test starts.
    private fun home(): ViewController<*> {
        val home = screen("Home")
        host.root = home
        home.onViewWillAppear()
        home.onViewDidAppear()
        entries.clear()
        return home
    }

    // A modal shown at once (views that end at once), before the test starts.
    private fun shown(id: String, options: Options = Options()): ViewController<*> {
        val instant = views.instant
        views.instant = true
        val node = screen(id, options)
        show(node)
        views.instant = instant
        entries.clear()
        views.log.clear()
        return node
    }

    private fun style(name: String) = Options().apply { modalPresentationStyle = Text(name) }

    private fun sheet() = Options().apply { modal.sheet.grabber = Bool(true) }

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    // The show (rows 1, 3).

    @Test
    fun aModalWaitsUnseenForItsContentThenShowsInTheOrderOfATransition() {
        home()
        show(screen("Details", ready = false))
        assertEquals(listOf("place:Details"), views.log)
        assertTrue(entries.isEmpty())
        assertEquals(State.Waiting, record("Details").state)
        readiness.mount("Details")
        assertEquals(listOf("will:Details"), entries)
        assertEquals(State.Presenting, record("Details").state)
        views.endShow("Details")
        assertEquals(
            listOf(
                "will:Details",
                "didDisappear:Home",
                "stateNeeded",
                "didAppear:Details",
                "stateNeeded",
                "stateNow",
                "state",
                "resolve:Details",
                "completed:showModal",
            ),
            entries,
        )
        assertEquals(listOf("place:Details", "present:Details:animated", "takeOutRoot:Home"), views.log)
        assertEquals(State.Shown, record("Details").state)
    }

    @Test
    fun anUnsetWaitEndsAtItsLimitAndMarksTheContentReady() {
        home()
        show(screen("Details", ready = false))
        fixture.scheduler.advance(ModalCoordinator.WAIT_LIMIT_MILLIS - 1)
        assertTrue(entries.isEmpty())
        fixture.scheduler.advance(1)
        assertEquals(listOf("Details"), readiness.marked)
        assertEquals(listOf("will:Details"), entries)
    }

    @Test
    fun waitForRenderFalseShowsAtOnceAndTrueWaitsWithoutALimit() {
        home()
        val now = Options().apply { animations.showModal.enter.waitForRender = Bool(false) }
        show(screen("Details", now, ready = false))
        assertEquals(State.Presenting, record("Details").state)
        val later = Options().apply { animations.showModal.enter.waitForRender = Bool(true) }
        show(screen("Profile", later, ready = false))
        fixture.scheduler.advance(10_000)
        assertEquals(State.Waiting, record("Profile").state)
        readiness.mount("Profile")
        assertEquals(State.Presenting, record("Profile").state)
    }

    @Test
    fun enterDisabledWaitsAndComesAtOnce() {
        home()
        val options = Options().apply { animations.showModal.enter.enabled = Bool(false) }
        show(screen("Details", options, ready = false))
        assertTrue(entries.isEmpty())
        readiness.mount("Details")
        assertTrue("present:Details:at once" in views.log)
        assertTrue("resolve:Details" in entries)
    }

    @Test
    fun nobodySeesTheActivityTheModalComesAtOnce() {
        home()
        host.seen = false
        show(screen("Details"))
        assertTrue("present:Details:at once" in views.log)
        assertTrue("resolve:Details" in entries)
    }

    @Test
    fun theLayerUnderLeavesOnlyUnderAFullScreenModal() {
        home()
        shown("Sheet", sheet())
        shown("Over", style("overFullScreen"))
        shown("Context", style("overCurrentContext"))
        assertTrue(views.log.none { it.startsWith("takeOut") })
        val full = screen("Details")
        views.instant = true
        show(full)
        assertTrue("takeOut:Context" in views.log)
        assertTrue("didDisappear:Context" in entries)
        assertFalse("didDisappear:Home" in entries)
    }

    @Test
    fun aWaitingModalIsAWaitingLayerAndItsStackIsNotOnScreen() {
        home()
        val stack = TestLayout(activity, registry, "Stack1")
        val details = screen("Details", ready = false)
        stack.add(details)
        show(stack)
        assertTrue(host.isInWaitingLayer(details))
        readiness.mount("Details")
        assertFalse(host.isInWaitingLayer(details))
    }

    @Test
    fun anOlderWaitingModalShowsFirst() {
        home()
        views.instant = true
        show(screen("First", ready = false))
        show(screen("Second"))
        assertEquals(State.Waiting, record("Second").state)
        readiness.mount("First")
        assertEquals(
            listOf("present:First:animated", "present:Second:animated"),
            views.log.filter { it.startsWith("present") },
        )
    }

    @Test
    fun theVisibleLayoutIsTheTopModalThatComesInOrShows() {
        val home = home()
        assertNull(modals.visibleLayout)
        val details = screen("Details", ready = false)
        show(details)
        assertNull(modals.visibleLayout)
        readiness.mount("Details")
        assertSame(details, modals.visibleLayout)
        views.endShow("Details")
        assertSame(details, modals.visibleLayout)
        dismiss("Details")
        assertNull(modals.visibleLayout)
        assertSame(home, host.root)
    }

    // A dismissal (rows 4, 10).

    @Test
    fun aDismissalOfTheTopGoesInTheOrderOfATransition() {
        home()
        shown("Details")
        views.instant = true
        dismiss("Details")
        assertEquals(
            listOf(
                "will:Home",
                "didDisappear:Details",
                "stateNeeded",
                "didAppear:Home",
                "stateNeeded",
                "stateNow",
                "modalDismissed:Details:Details:1",
                "state",
                "resolve:Details",
                "completed:dismissModal",
            ),
            entries,
        )
        assertEquals(listOf("putBackRoot:Home", "dismiss:Details:animated", "remove:Details"), views.log)
        assertTrue(modals.modals.isEmpty())
        assertNull(registry.find("Details"))
    }

    @Test
    fun aSecondDismissalWaitsForTheFirstWithoutASecondEvent() {
        home()
        shown("Details")
        dismiss("Details")
        dismiss("Details")
        assertEquals(State.Dismissing, record("Details").state)
        views.endDismiss("Details")
        assertEquals(1, entries.count { it.startsWith("modalDismissed") })
        assertEquals(2, entries.count { it == "resolve:Details" })
        entries.clear()
        dismiss("Details")
        assertEquals(listOf("reject:${ErrorTexts.screenNotShown("Details")}"), entries)
    }

    @Test
    fun aDismissalDuringTheShowWaitsForItsEnd() {
        home()
        show(screen("Details"))
        dismiss("Details")
        assertEquals(State.Presenting, record("Details").state)
        views.endShow("Details")
        assertEquals(State.Dismissing, record("Details").state)
        assertTrue("resolve:Details" in entries)
        views.endDismiss("Details")
        assertEquals(1, entries.count { it.startsWith("modalDismissed") })
        assertEquals(2, entries.count { it == "resolve:Details" })
    }

    @Test
    fun aDismissalWhileItWaitsCancelsTheShowAndTouchesNothingUnder() {
        home()
        show(screen("Details", ready = false))
        dismiss("Details")
        assertEquals(
            listOf(
                "state",
                "resolve:Details",
                "completed:showModal",
                "modalDismissed:Details:Details:1",
                "state",
                "resolve:Details",
                "completed:dismissModal",
            ),
            entries,
        )
        assertEquals(listOf("place:Details", "remove:Details"), views.log)
        readiness.mount("Details")
        fixture.scheduler.advance(ModalCoordinator.WAIT_LIMIT_MILLIS)
        assertTrue(entries.none { it.startsWith("will:") })
        assertTrue(host.waiting.isEmpty())
    }

    @Test
    fun theOptionsOfADismissalMergeFirstAndTheirExitDecidesTheAnimation() {
        home()
        shown("Details")
        val options = Options().apply { animations.dismissModal.exit.enabled = Bool(false) }
        dismiss("Details", options)
        assertTrue("dismiss:Details:at once" in views.log)
        assertTrue("resolve:Details" in entries)
    }

    @Test
    fun theTextsOfALookupThatFinds() {
        home()
        dismiss("Nobody")
        dismiss("Home")
        assertEquals(
            listOf(
                "reject:${ErrorTexts.screenNotShown("Nobody")}",
                "reject:${ErrorTexts.notInModal("Home")}",
            ),
            entries,
        )
    }

    @Test
    fun aDismissalByAScreenInTheModalClosesItWithItsRootsId() {
        home()
        val stack = TestLayout(activity, registry, "Stack1")
        stack.add(screen("Details"))
        views.instant = true
        show(stack)
        entries.clear()
        dismiss("Details")
        assertTrue("modalDismissed:Details:Details:1" in entries)
        assertTrue("resolve:Stack1" in entries)
    }

    // Closed below (row 4, not top; row 11).

    @Test
    fun aModalClosedBelowIsReportedAtOnceAndTheTopRevealsTheRoot() {
        home()
        shown("First")
        shown("Second", style("overCurrentContext"))
        dismiss("First")
        assertEquals(
            listOf(
                "didDisappear:First",
                "stateNeeded",
                "stateNow",
                "modalDismissed:First:First:1",
                "state",
                "resolve:First",
                "completed:dismissModal",
            ),
            entries,
        )
        assertEquals(State.ClosedBelow, record("First").state)
        entries.clear()
        dismiss("First")
        assertEquals(listOf("state", "resolve:First", "completed:dismissModal"), entries)
        views.endDismiss("First")
        assertTrue(modals.modals.none { it.rootId == "First" })
        entries.clear()
        views.instant = true
        dismiss("Second")
        assertEquals("will:Home", entries.first())
        assertEquals(listOf("modalDismissed:Second:Second:1"), entries.filter { it.startsWith("modalDismissed") })
        assertTrue("putBackRoot:Home" in views.log)
    }

    @Test
    fun aModalClosedBelowOutOfTheWindowEndsAtOnce() {
        home()
        shown("First")
        shown("Second")
        dismiss("First")
        assertTrue("dismiss:First:at once" in views.log)
        assertTrue("remove:First" in views.log)
        assertTrue(modals.modals.none { it.rootId == "First" })
        assertEquals(1, entries.count { it.startsWith("modalDismissed") })
    }

    @Test
    fun aModalClosedBelowWhoseRecordWentResolvesALaterDismissalWithoutAnEvent() {
        home()
        shown("First")
        shown("Second")
        dismiss("First")
        assertTrue(modals.modals.none { it.rootId == "First" })
        entries.clear()
        dismiss("First")
        assertEquals(listOf("state", "resolve:First", "completed:dismissModal"), entries)
        entries.clear()
        shown("First")
        assertTrue(modals.modals.any { it.rootId == "First" })
    }

    // A modal still coming in that a covering modal above it takes out of the window.
    @Test
    fun aModalStillComingInUnderACoveringOneEndsItsShowFirst() {
        home()
        show(screen("Sheet", sheet()))
        show(screen("Login"))
        assertEquals(State.Presenting, record("Sheet").state)
        views.endShow("Login")
        assertTrue("stopped:Sheet" in views.log)
        assertTrue("settle:Sheet" in views.log)
        assertTrue("takeOut:Sheet" in views.log)
        assertEquals(State.Shown, record("Sheet").state)
        val order = entries.filter {
            it == "didAppear:Sheet" || it == "resolve:Sheet" || it == "didDisappear:Sheet" || it == "resolve:Login"
        }
        assertEquals(listOf("didAppear:Sheet", "resolve:Sheet", "didDisappear:Sheet", "resolve:Login"), order)
        assertFalse(views.isShowing("Sheet"))
    }

    @Test
    fun theModalUnderComesBackAtThePlaceOfTheOneThatGoes() {
        home()
        shown("First")
        shown("Over", style("overCurrentContext"))
        views.instant = true
        show(screen("Top"))
        assertTrue("takeOut:Over" in views.log)
        views.log.clear()
        dismiss("Top")
        assertEquals(listOf("putBack:Over@Top", "dismiss:Top:animated", "remove:Top"), views.log)
        assertTrue("didAppear:Over" in entries)
    }

    @Test
    fun aCancelOverAModalClosedBelowBringsBackWhatItCovered() {
        home()
        shown("First")
        show(screen("Waiting", ready = false))
        views.instant = true
        dismiss("First")
        assertTrue(modals.modals.none { it.rootId == "First" })
        entries.clear()
        dismiss("Waiting")
        assertTrue("putBackRoot:Home" in views.log)
        assertTrue("didAppear:Home" in entries)
    }

    // The batch (row 5, f).

    @Test
    fun dismissAllModalsSendsOneEventWithTheCountAndAnswersEmpty() {
        home()
        shown("First")
        shown("Second")
        views.instant = true
        modals.dismissAll(null, null, answer("dismissAllModals"))
        assertEquals(
            listOf(
                "will:Home",
                "didDisappear:Second",
                "stateNeeded",
                "didAppear:Home",
                "stateNeeded",
                "stateNow",
                "modalDismissed:Second:Second:2",
                "state",
                "resolve:",
                "completed:dismissAllModals",
            ),
            entries,
        )
        assertEquals(listOf("remove:First", "putBackRoot:Home", "dismiss:Second:animated", "remove:Second"), views.log)
        assertTrue(modals.modals.isEmpty())
        entries.clear()
        modals.dismissAll(null, null, answer("dismissAllModals"))
        assertEquals(listOf("state", "resolve:", "completed:dismissAllModals"), entries)
    }

    @Test
    fun theBatchAnswersAWaitingShowAndCountsIt() {
        home()
        shown("First")
        show(screen("Waiting", ready = false))
        views.instant = true
        modals.dismissAll(null, null, answer("dismissAllModals"))
        assertTrue("modalDismissed:Waiting:Waiting:2" in entries)
        val answers = entries.filter { it.startsWith("resolve:") }
        assertEquals(listOf("resolve:Waiting", "resolve:"), answers)
    }

    @Test
    fun aSecondDismissAllDuringTheBatchAnswersAtItsEnd() {
        home()
        shown("Details")
        modals.dismissAll(null, null, answer("dismissAllModals"))
        modals.dismissAll(null, null, answer("dismissAllModals"))
        assertTrue(entries.none { it.startsWith("resolve") })
        views.endDismiss("Details")
        assertEquals(2, entries.count { it == "resolve:" })
        assertEquals(1, entries.count { it.startsWith("modalDismissed") })
    }

    @Test
    fun aDismissalDuringTheBatchResolvesAtItsEnd() {
        home()
        shown("Details")
        modals.dismissAll(null, null, answer("dismissAllModals"))
        dismiss("Details")
        assertTrue(entries.none { it.startsWith("resolve") })
        views.endDismiss("Details")
        assertEquals(listOf("resolve:Details", "resolve:"), entries.filter { it.startsWith("resolve") })
    }

    // The layer its own dismissal revealed appears at the batch's end.
    @Test
    fun aModalLeavingOnItsOwnThatJoinsTheBatchShowsTheRevealedLayerAtItsEnd() {
        home()
        shown("Details")
        dismiss("Details")
        assertEquals(listOf("will:Home"), entries)
        modals.dismissAll(null, null, answer("dismissAllModals"))
        assertTrue(views.isDismissing("Details"))
        views.endDismiss("Details")
        assertTrue("didAppear:Home" in entries)
        assertEquals(listOf("modalDismissed:Details:Details:1"), entries.filter { it.startsWith("modalDismissed") })
        assertEquals(listOf("resolve:Details", "resolve:"), entries.filter { it.startsWith("resolve") })
        assertEquals(1, entries.count { it == "will:Home" })
    }

    // The show is cut, its screens disappear and its show resolves before the batch's answer.
    @Test
    fun aModalComingInThatJoinsTheBatchIsCutAndAnswered() {
        home()
        show(screen("Details"))
        modals.dismissAll(null, null, answer("dismissAllModals"))
        assertTrue("stopped:Details" in views.log)
        views.endDismiss("Details")
        assertFalse("didAppear:Details" in entries)
        assertTrue("didDisappear:Details" in entries)
        assertEquals(listOf("resolve:Details", "resolve:"), entries.filter { it.startsWith("resolve") })
        assertEquals(listOf("modalDismissed:Details:Details:1"), entries.filter { it.startsWith("modalDismissed") })
    }

    @Test
    fun anUnreadableOptionOfDismissAllRejectsWithTheTopScreen() {
        home()
        shown("Details")
        modals.dismissAll(Options(), "animations.dismissModal", answer("dismissAllModals"))
        assertEquals(listOf("reject:${ErrorTexts.unreadableOption("animations.dismissModal", "Details")}"), entries)
        assertEquals(State.Shown, record("Details").state)
    }

    // Back (row 7) and the last modal without a root.

    @Test
    fun backClosesTheTopModalWithoutAnAnswer() {
        home()
        shown("Details")
        assertEquals(true, modals.canHandleBack())
        views.instant = true
        assertEquals(true, modals.handleBack(CommandListenerAdapter()))
        assertTrue("modalDismissed:Details:Details:1" in entries)
        assertTrue(entries.none { it.startsWith("resolve") })
        assertNull(modals.handleBack(CommandListenerAdapter()))
        entries.clear()
        dismiss("Details")
        assertEquals(listOf("state", "resolve:Details", "completed:dismissModal"), entries)
    }

    @Test
    fun backOnAModalThatHoldsSendsTheButtonEvent() {
        home()
        val holds = Options().apply { hardwareBackButton.dismissModalOnPress = Bool(false) }
        shown("Details", holds)
        assertEquals(true, modals.handleBack(CommandListenerAdapter()))
        assertEquals(listOf("pressed:Details:ApexNavigation.hardwareBackButton"), entries)
        assertEquals(State.Shown, record("Details").state)
    }

    @Test
    fun theOnlyModalOverNoRootCantBeDismissed() {
        shown("Details")
        assertEquals(false, modals.canHandleBack())
        assertEquals(false, modals.handleBack(CommandListenerAdapter()))
        dismiss("Details")
        assertEquals(listOf("reject:${ErrorTexts.modalWithoutRoot("Details")}"), entries)
        assertEquals(State.Shown, record("Details").state)
        assertTrue(views.log.none { it.startsWith("dismiss") })
        val holds = Options().apply { hardwareBackButton.dismissModalOnPress = Bool(false) }
        shown("Profile", holds)
        assertEquals(true, modals.canHandleBack())
    }

    @Test
    fun backWhileTheModalWaitsCancelsItsShow() {
        home()
        show(screen("Details", ready = false))
        assertEquals(true, modals.handleBack(CommandListenerAdapter()))
        assertEquals(
            listOf("state", "resolve:Details", "completed:showModal", "modalDismissed:Details:Details:1"),
            entries,
        )
    }

    // The user (rows 6, 8) and the sheet.

    @Test
    fun theUserClosingASheetIsADismissalAndLaterCommandsResolve() {
        home()
        shown("Sheet", sheet())
        views.instant = true
        views.sheets.getValue("Sheet").onClosedByUser()
        assertEquals(listOf("modalDismissed:Sheet:Sheet:1"), entries.filter { it.startsWith("modalDismissed") })
        assertTrue(entries.none { it.startsWith("resolve") })
        entries.clear()
        dismiss("Sheet")
        assertEquals(listOf("state", "resolve:Sheet", "completed:dismissModal"), entries)
    }

    @Test
    fun aSheetClosedByTheUserWhileItComesInAnswersItsShowFirst() {
        home()
        show(screen("Sheet", sheet()))
        views.instant = true
        views.sheets.getValue("Sheet").onClosedByUser()
        assertTrue("stopped:Sheet" in views.log)
        val order = entries.filter { it == "resolve:Sheet" || it.startsWith("modalDismissed") }
        assertEquals(listOf("resolve:Sheet", "modalDismissed:Sheet:Sheet:1"), order)
    }

    @Test
    fun aSheetTellsTheTriesAndTheDetentsOfTheUser() {
        home()
        shown("Sheet", sheet())
        views.sheets.getValue("Sheet").onCloseAttempted()
        views.sheets.getValue("Sheet").onDetentChanged(SheetDetent.Medium)
        assertEquals(listOf("attempted:Sheet", "detent:Sheet:medium"), entries)
    }

    @Test
    fun aMergeIntoAModalUpdatesItsSheet() {
        home()
        val stack = TestLayout(activity, registry, "Stack1")
        val details = screen("Details")
        stack.add(details)
        views.instant = true
        show(stack)
        views.log.clear()
        modals.optionsMergedInto(details, Options())
        modals.visibleScreenChanged(stack)
        modals.optionsMergedInto(host.root ?: details, Options())
        assertEquals(listOf("update:Stack1:true", "update:Stack1:false"), views.log)
    }

    // A `setRoot` (rows 12 … 14).

    @Test
    fun aSetRootTakesTheModalsCommandsDontFindThemAndTheyComeBackWhenItEnds() {
        home()
        shown("Details")
        show(screen("Waiting", ready = false))
        modals.rootAccepted()
        assertEquals(listOf("state", "resolve:Waiting", "completed:showModal"), entries)
        assertEquals(State.WithOldRoot, record("Details").state)
        assertTrue(modals.isWithOldRoot(registry.find("Details") ?: error("no node")))
        entries.clear()
        dismiss("Details")
        assertEquals(listOf("reject:${ErrorTexts.screenNotShown("Details")}"), entries)
        assertEquals("Details", modals.visibleLayout?.id)
        modals.rootKept()
        assertEquals(State.Shown, record("Details").state)
    }

    @Test
    fun theModalsGoWithTheOldRootWithoutModalDismissed() {
        val home = home()
        shown("Details")
        modals.rootAccepted()
        modals.rootReplaced(home)
        assertEquals(listOf("putBackRoot:Home", "carry:Details"), views.log)
        assertTrue(modals.modals.isEmpty())
        assertTrue(modals.isWithOldRoot(registry.find("Details") ?: error("no node")))
        modals.replacedRootGone()
        assertEquals(listOf("didDisappear:Details", "stateNeeded"), entries)
        assertNull(registry.find("Details"))
    }

    @Test
    fun aModalShownWhileTheNewRootWaitsGoesWithTheOldOne() {
        val home = home()
        modals.rootAccepted()
        shown("Details")
        show(screen("Late", ready = false))
        modals.rootReplaced(home)
        assertTrue("carry:Details" in views.log)
        assertTrue("resolve:Late" in entries)
        modals.replacedRootGone()
        assertTrue(entries.none { it.startsWith("modalDismissed") })
    }

    // A dismissal on its way finishes on its own, with its event and its answer.
    @Test
    fun aDismissalOnItsWayWhenASetRootIsAcceptedFinishesOnItsOwn() {
        home()
        shown("Details")
        dismiss("Details")
        modals.rootAccepted()
        assertEquals(State.Dismissing, record("Details").state)
        views.endDismiss("Details")
        assertTrue("modalDismissed:Details:Details:1" in entries)
        assertTrue("resolve:Details" in entries)
    }

    // Cut at the replacement, gone with the old root, answered, no `ModalDismissed`.
    @Test
    fun aShowAndADismissalOnTheirWayGoWithTheOldRootAndAnswer() {
        val home = home()
        shown("First")
        dismiss("First")
        show(screen("Second"))
        modals.rootAccepted()
        assertEquals(State.WithOldRoot, record("Second").state)
        modals.rootReplaced(home)
        assertTrue("stopped:First" in views.log)
        assertTrue("stopped:Second" in views.log)
        modals.replacedRootGone()
        assertTrue("resolve:First" in entries)
        assertTrue("resolve:Second" in entries)
        assertTrue("didDisappear:Second" in entries)
        assertTrue(entries.none { it.startsWith("modalDismissed") })
    }

    // A modal taken while it comes in ends its show and stays taken; a command that waited for its show
    // waits for the old root (row 13).
    @Test
    fun aTakenModalEndsItsShowAndStaysTaken() {
        val home = home()
        show(screen("Details"))
        dismiss("Details")
        modals.rootAccepted()
        views.endShow("Details")
        assertEquals(State.WithOldRoot, record("Details").state)
        assertEquals(State.Shown, record("Details").before)
        assertTrue("didAppear:Details" in entries)
        assertEquals(1, entries.count { it == "resolve:Details" })
        assertFalse(views.isDismissing("Details"))
        modals.rootReplaced(home)
        modals.replacedRootGone()
        assertEquals(2, entries.count { it == "resolve:Details" })
        assertTrue(entries.none { it.startsWith("modalDismissed") })
    }

    // Back closes a taken modal as a shown one, and it stays taken for commands.
    @Test
    fun backClosesATakenModalWhichStaysTaken() {
        home()
        shown("Details")
        modals.rootAccepted()
        assertEquals(true, modals.handleBack(CommandListenerAdapter()))
        assertEquals(State.WithOldRoot, record("Details").state)
        assertEquals(State.Dismissing, record("Details").effective)
        assertTrue(modals.isWithOldRoot(registry.find("Details") ?: error("no node")))
        entries.clear()
        dismiss("Details")
        assertEquals(listOf("reject:${ErrorTexts.screenNotShown("Details")}"), entries)
        views.endDismiss("Details")
        assertTrue("modalDismissed:Details:Details:1" in entries)
        assertTrue(modals.modals.isEmpty())
    }

    // Back waits for the show's end, then closes it.
    @Test
    fun backDuringTheShowOfATakenModalClosesItAtItsEnd() {
        home()
        show(screen("Details"))
        modals.rootAccepted()
        assertEquals(true, modals.handleBack(CommandListenerAdapter()))
        views.endShow("Details")
        assertTrue(views.isDismissing("Details"))
    }

    // The user closing a taken sheet closes it, with its event.
    @Test
    fun theUserClosesATakenSheet() {
        home()
        shown("Sheet", sheet())
        modals.rootAccepted()
        views.instant = true
        views.sheets.getValue("Sheet").onClosedByUser()
        assertTrue("modalDismissed:Sheet:Sheet:1" in entries)
        assertTrue(modals.modals.isEmpty())
    }

    // The activity's end and a reload (rows 15, 16).

    @Test
    fun theActivityGoingAnswersTheShowsAndTheCommandsWithTheirIds() {
        home()
        shown("Details")
        dismiss("Details")
        show(screen("Waiting", ready = false))
        entries.clear()
        modals.destroyAll()
        assertTrue("resolve:Details" in entries)
        assertTrue("resolve:Waiting" in entries)
        assertTrue("didDisappear:Details" in entries)
        assertTrue(entries.none { it.startsWith("modalDismissed") })
        assertTrue(modals.modals.isEmpty())
        assertTrue(host.waiting.isEmpty())
    }

    @Test
    fun aReloadTakesTheModalsSilently() {
        home()
        shown("Details")
        show(screen("Waiting", ready = false))
        ReloadCoordinator.advance()
        modals.destroyAll()
        assertTrue(entries.isEmpty())
        assertNull(registry.find("Details"))
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aShowThatFailsRejectsAndLeavesNothing() {
        home()
        views.failPresent = true
        show(screen("Details"))
        assertEquals("reject:${ErrorTexts.couldNot("show the screen 'Details'", "no view")}", entries.last())
        assertTrue(entries.none { it.startsWith("resolve") })
        assertTrue(modals.modals.isEmpty())
        assertNull(registry.find("Details"))
    }

    // The steps that come later go through the coordinator's guard.

    @Test
    fun aFailureAtTheEndOfAShowRejectsAndLeavesNothing() {
        home()
        show(screen("Details"))
        views.failTakeOutRoot = true
        views.endShow("Details")
        assertEquals("reject:${ErrorTexts.couldNot("show the screen 'Details'", "no view")}", entries.last())
        assertTrue(entries.none { it.startsWith("resolve") })
        assertTrue(modals.modals.isEmpty())
        assertNull(registry.find("Details"))
        assertTrue(host.waiting.isEmpty())
    }

    @Test
    fun aFailureAtTheEndOfADismissalRejectsAndTheModalGoes() {
        home()
        shown("Details")
        dismiss("Details")
        views.failRemove = true
        views.endDismiss("Details")
        assertEquals(
            "reject:${ErrorTexts.couldNot("dismiss the modal of the screen 'Details'", "no view")}",
            entries.last(),
        )
        assertTrue(modals.modals.isEmpty())
        assertNull(registry.find("Details"))
    }

    @Test
    fun aBackThatFailsIsLoggedAndTheModalGoes() {
        home()
        shown("Details")
        views.failDismiss = true
        assertEquals(true, modals.handleBack(CommandListenerAdapter()))
        assertEquals(
            listOf("[ApexNavigation] ${ErrorTexts.couldNot("dismiss the modal of the screen 'Details'", "no view")}"),
            host.logged,
        )
        assertTrue(modals.modals.isEmpty())
        assertNull(registry.find("Details"))
    }

    @Test
    fun aFailureAtTheEndOfDismissAllModalsRejectsItAndEveryModalGoes() {
        home()
        shown("Details")
        shown("Settings")
        modals.dismissAll(null, null, answer("dismissAllModals"))
        views.failRemove = true
        views.endDismiss("Settings")
        assertEquals("reject:${ErrorTexts.couldNot("dismiss all modals", "no view")}", entries.last())
        assertTrue(modals.modals.isEmpty())
        assertNull(registry.find("Details"))
        assertNull(registry.find("Settings"))
    }


    @Test
    fun aWaitThatFailsAsItStartsRejectsAndTheNextModalShows() {
        home()
        readiness.failWhenReady = true
        show(screen("Details", ready = false))
        assertEquals("reject:${ErrorTexts.couldNot("show the screen 'Details'", "no readiness")}", entries.last())
        assertTrue(modals.modals.isEmpty())
        assertTrue(host.waiting.isEmpty())
        assertNull(registry.find("Details"))
        assertEquals(0, fixture.scheduler.pending)
        views.instant = true
        show(screen("Profile"))
        assertTrue("resolve:Profile" in entries)
    }

    @Test
    fun aModalUnderWhoseShowFailsAsTheTopCoversItGoesAndTheTopShows() {
        home()
        show(screen("Sheet", sheet()))
        show(screen("Login"))
        views.failSettle = true
        views.endShow("Login")
        assertTrue(entries.toString(), "reject:${ErrorTexts.couldNot("show the screen 'Sheet'", "no view")}" in entries)
        assertTrue(entries.toString(), "resolve:Login" in entries)
        assertTrue(modals.modals.none { it.rootId == "Sheet" })
        assertEquals(State.Shown, record("Login").state)
        assertTrue("takeOutRoot:Home" in views.log)
    }

    @Test
    fun aChainGoesOnWhenBacksUpdateFailsAtTheEndOfItsShow() {
        home()
        var done = 0
        modals.showInChain("Details", { screen("Details") }, animated = true) { done++ }
        assertTrue(views.isShowing("Details"))
        host.failBackChanged = true
        views.endShow("Details")
        assertEquals(1, done)
        assertEquals(State.Shown, record("Details").state)
        assertEquals(
            listOf("[ApexNavigation] ${ErrorTexts.couldNot("show the screen 'Details'", "no back")}"),
            host.logged,
        )
    }

    @Test
    fun aBackOfTheContentThatFailsLeavesAWaitingModalToShow() {
        home()
        val details = screen("Details", ready = false)
        show(details)
        details.failsBack = true
        assertEquals(true, modals.handleBack(CommandListenerAdapter()))
        assertEquals(
            listOf("[ApexNavigation] ${ErrorTexts.couldNot("go back from the screen 'Details'", "no back")}"),
            host.logged,
        )
        assertEquals(State.Waiting, record("Details").state)
        views.instant = true
        readiness.mount("Details")
        assertTrue("resolve:Details" in entries)
        assertTrue(entries.none { it.startsWith("reject") })
    }

    @Test
    fun aDismissAllModalsThatFailsAsItStartsLeavesNoBatch() {
        home()
        shown("Details")
        shown("Settings")
        views.failRemove = true
        assertThrows(IllegalStateException::class.java) {
            modals.dismissAll(null, null, answer("dismissAllModals"))
        }
        assertEquals("reject:${ErrorTexts.couldNot("dismiss all modals", "no view")}", entries.last())
        assertTrue(modals.modals.isEmpty())
        assertNull(registry.find("Details"))
        assertNull(registry.find("Settings"))
        entries.clear()
        modals.dismissAll(null, null, answer("dismissAllModals"))
        assertEquals(listOf("state", "resolve:", "completed:dismissAllModals"), entries)
    }
}

package dev.apexnative.navigation.viewcontrollers.navigator

import android.app.Activity
import android.widget.FrameLayout
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.ScreenAnimationOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.react.CommandAnswer
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.react.FakeEvents
import dev.apexnative.navigation.react.FakePromise
import dev.apexnative.navigation.react.Journal
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.react.events.AppearanceDispatcher
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.react.events.ScreenAppearance
import dev.apexnative.navigation.viewcontrollers.child.ChildController
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.Readiness
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `setRoot` after its checks (`setRoot`): the wait for the new root's screens, the order of its transition with the
 * real answer, last wins in each state of a `setRoot` on its way, and the ends of the activity (`''`) and of the
 * JavaScript (no answer, nothing sent); the modals over the old root and the modals and overlays of the layout, shown
 * after the root and before the answer.
 */
class RootCoordinatorTest {
    private val journal = Journal()
    private val fixture = DispatcherFixture(EventJournal(journal.entries))
    private val entries = journal.entries
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val layer = FakeLayer()
    private val readiness = FakeReadiness()
    private var drawnCalls = 0
    private val firstRoot = FirstRootDraw(fixture.scheduler) { drawnCalls++ }
    // What the root told the modals over it.
    private val overRoot = mutableListOf<String>()
    private val coordinator = RootCoordinator(
        layer = layer,
        readiness = readiness,
        scheduler = fixture.scheduler,
        firstRoot = firstRoot,
        onRootChanged = {},
        onRootShown = { entries += "shown" },
        isSeen = { seen },
        modals = object : RootCoordinator.ModalsOverRoot {
            override fun rootAccepted() {
                overRoot += "accepted"
            }

            override fun rootKept() {
                overRoot += "kept"
            }

            override fun rootReplaced(old: ViewController<*>?) {
                overRoot += "replaced:${old?.id}"
            }

            override fun replacedRootGone() {
                overRoot += "gone"
            }
        },
    )
    private var seen = true
    private var commands = 0
    // The layers made ahead and let go (LayerStep.prepare, LayerStep.discard), apart from the events.
    private val prepared = mutableListOf<String>()

    private fun screen(id: String, ready: Boolean = true, native: Boolean = false, options: Options = Options()) =
        RootScreen(activity, registry, id, options, native).also { if (ready) readiness.ready += id }

    private fun waitingFor(value: Boolean) = Options().apply { animations.setRoot.enter.waitForRender = Bool(value) }

    private fun answer() = CommandAnswer(
        "setRoot",
        "setRoot${++commands}",
        FakePromise(journal),
        ReloadCoordinator.generation,
        FakeEvents(journal),
        { 0L },
    )

    private fun setRoot(root: ViewController<*>, vararg layers: Step) =
        coordinator.setRoot({ root }, answer(), layers.toList())

    // A modal or an overlay of the layout: it starts when the root says, ends when the test says, or fails at once. A
    // step that [shows] a screen makes it appear as it ends, as a modal's screen does.
    private inner class Step(
        private val id: String,
        private val fails: Boolean = false,
        private val shows: ViewController<*>? = null,
        private val throws: Boolean = false,
    ) : RootCoordinator.LayerStep {
        private var done: (() -> Unit)? = null

        override fun prepare() {
            prepared += "prepare:$id"
        }

        override fun discard() {
            prepared += "discard:$id"
        }

        override fun show(done: () -> Unit): () -> Unit {
            entries += "layer:$id"
            if (throws) error("no layer")
            if (fails) {
                done()
                return {}
            }
            this.done = done
            return { entries += "stop:$id" }
        }

        fun finish() {
            shows?.let {
                AppearanceDispatcher.beginTransition(listOf(it))
                AppearanceDispatcher.endTransition(emptyList(), listOf(it), null, null, null, reportsState = false)
            }
            done?.invoke()
        }
    }

    // A root that shows now, before the test starts.
    private fun shown(root: ViewController<*>) {
        setRoot(root)
        layer.endAnimation()
        entries.clear()
        layer.log.clear()
    }

    // The layers made ahead (prepare): with the root, before its wait; they show only after it.

    @Test
    fun theLayersArePreparedWithTheRootAndShowAfterIt() {
        shown(screen("Home"))
        setRoot(screen("Profile", ready = false), Step("Details"), Step("Login"))
        assertEquals(listOf("prepare:Details", "prepare:Login"), prepared)
        assertTrue(entries.none { it.startsWith("layer:") })
        readiness.mount("Profile")
        layer.endAnimation()
        assertEquals(listOf("layer:Details"), entries.filter { it.startsWith("layer:") })
        assertEquals(listOf("prepare:Details", "prepare:Login"), prepared)
    }

    @Test
    fun aNewerSetRootWhileTheRootWaitsDiscardsEveryPreparedLayer() {
        shown(screen("Home"))
        setRoot(screen("Profile", ready = false), Step("Details"), Step("Login"))
        setRoot(screen("Settings"))
        assertEquals(listOf("prepare:Details", "prepare:Login", "discard:Details", "discard:Login"), prepared)
        assertTrue(entries.none { it.startsWith("layer:") })
    }

    @Test
    fun aNewerSetRootWhileTheLayersShowStopsTheOneThatShowsAndDiscardsTheRest() {
        shown(screen("Home"))
        setRoot(screen("Profile"), Step("Details"), Step("Login"))
        layer.endAnimation()
        setRoot(screen("Settings"))
        assertTrue(entries.toString(), "stop:Details" in entries)
        assertEquals(listOf("prepare:Details", "prepare:Login", "discard:Login"), prepared)
    }

    @Test
    fun aRootDestroyedWhileItWaitsDiscardsItsPreparedLayers() {
        shown(screen("Home"))
        val profile = screen("Profile", ready = false)
        setRoot(profile, Step("Details"))
        profile.destroy()
        coordinator.rootScreensChanged()
        assertEquals(listOf("prepare:Details", "discard:Details"), prepared)
    }

    private fun replaced(id: String) = "reject:${ErrorTexts.setRootReplaced(id)}"

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    @Test
    fun theFirstRootShowsAtOnceOverTheSplash() {
        val home = screen("Home")
        setRoot(home)
        assertEquals(
            listOf(
                "will:Home",
                "didAppear:Home",
                "stateNeeded",
                "stateNow",
                "state",
                "resolve:Home",
                "completed:setRoot",
                "shown",
            ),
            entries,
        )
        assertEquals(listOf("place:Home", "raise:Home", "show:Home"), layer.log)
        assertSame(home, coordinator.root)
        assertTrue(firstRoot.isDrawn)
        assertEquals(1, drawnCalls)
    }

    @Test
    fun aNewRootReplacesTheOldOneInTheOrderOfATransition() {
        val home = screen("Home", native = true)
        shown(home)
        val profile = screen("Profile")
        setRoot(profile)
        assertEquals(listOf("will:Profile"), entries)
        assertEquals(listOf("place:Profile", "raise:Profile", "fade:Home->Profile"), layer.log)
        assertSame(profile, coordinator.root)
        layer.endAnimation()
        assertEquals(
            listOf(
                "will:Profile",
                "didDisappear:Home",
                "stateNeeded",
                "released:Home",
                "didAppear:Profile",
                "stateNeeded",
                "stateNow",
                "state",
                "resolve:Profile",
                "completed:setRoot",
                "shown",
            ),
            entries,
        )
        assertEquals(listOf("place:Profile", "raise:Profile", "fade:Home->Profile", "remove:Home"), layer.log)
        assertTrue(home.isDestroyed)
        assertNull(registry.find("Home"))
    }

    @Test
    fun anUnsetWaitEndsAtItsLimitAndMarksTheScreensReady() {
        val home = screen("Home", ready = false)
        setRoot(home)
        fixture.scheduler.advance(RootCoordinator.WAIT_LIMIT_MILLIS - 1)
        assertTrue(entries.isEmpty())
        assertFalse(firstRoot.isDrawn)
        fixture.scheduler.advance(1)
        assertEquals(listOf("Home"), readiness.marked)
        assertEquals("will:Home", entries.first())
        assertTrue("resolve:Home" in entries)
        assertTrue(firstRoot.isDrawn)
    }

    @Test
    fun aRootThatRendersInTimeShowsThenAndLeavesNoLimit() {
        val home = screen("Home", ready = false)
        setRoot(home)
        fixture.scheduler.advance(100)
        readiness.mount("Home")
        assertTrue("resolve:Home" in entries)
        assertTrue(readiness.marked.isEmpty())
        assertEquals(0, fixture.scheduler.pending)
    }

    // The components of the bars join the wait.

    @Test
    fun anUnsetWaitCountsTheBarWithinItsOneLimitAndMarksOnlyTheContent() {
        val home = screen("Home", ready = true)
        readiness.barsWaiting += "Home"
        setRoot(home)
        fixture.scheduler.advance(100)
        assertTrue(entries.isEmpty())
        readiness.drawBar("Home")
        assertTrue("resolve:Home" in entries)
        assertTrue(readiness.marked.isEmpty())
    }

    @Test
    fun waitForRenderTrueGivesTheBarHalfASecondAfterTheContent() {
        val home = screen("Home", ready = false, options = waitingFor(true))
        readiness.barsWaiting += "Home"
        setRoot(home)
        fixture.scheduler.advance(10_000)
        readiness.mount("Home")
        assertTrue(entries.isEmpty())
        fixture.scheduler.advance(RootCoordinator.WAIT_LIMIT_MILLIS - 1)
        assertTrue(entries.isEmpty())
        fixture.scheduler.advance(1)
        assertTrue("resolve:Home" in entries)
        assertTrue(readiness.marked.isEmpty())
    }

    @Test
    fun waitForRenderTrueWaitsWithoutALimit() {
        val home = screen("Home", ready = false, options = waitingFor(true))
        setRoot(home)
        assertEquals(0, fixture.scheduler.pending)
        fixture.scheduler.advance(10_000)
        assertTrue(entries.isEmpty())
        assertFalse(firstRoot.isDrawn)
        readiness.mount("Home")
        assertTrue("resolve:Home" in entries)
        assertTrue(firstRoot.isDrawn)
    }

    @Test
    fun waitForRenderFalseDoesntWait() {
        val home = screen("Home", native = true)
        shown(home)
        val profile = screen("Profile", ready = false, options = waitingFor(false))
        setRoot(profile)
        assertEquals(listOf("place:Profile", "raise:Profile", "show:Profile", "remove:Home"), layer.log)
        assertTrue("resolve:Profile" in entries)
    }

    @Test
    fun enterDisabledComesAtOnceAfterTheWait() {
        shown(screen("Home"))
        val options = Options().apply { animations.setRoot.enter.enabled = Bool(false) }
        val profile = screen("Profile", ready = false, options = options)
        setRoot(profile)
        assertTrue(entries.isEmpty())
        readiness.mount("Profile")
        assertEquals(listOf("place:Profile", "raise:Profile", "show:Profile", "remove:Home"), layer.log)
        assertTrue("resolve:Profile" in entries)
    }

    @Test
    fun aPendingRootWaitsUnderTheCurrentOneAndComesUpAsItsTransitionStarts() {
        shown(screen("Home"))
        setRoot(screen("Profile", ready = false))
        assertEquals(listOf("place:Profile"), layer.log)
        readiness.mount("Profile")
        assertEquals(listOf("place:Profile", "raise:Profile", "fade:Home->Profile"), layer.log)
    }

    @Test
    fun anActivityNobodySeesTakesTheNewRootAtOnce() {
        shown(screen("Home"))
        seen = false
        setRoot(screen("Profile"))
        assertEquals(listOf("place:Profile", "raise:Profile", "show:Profile", "remove:Home"), layer.log)
        assertTrue("resolve:Profile" in entries)
    }

    @Test
    fun aRootOfAJavaScriptThatWentDoesntGoUp() {
        val home = screen("Home", ready = false)
        setRoot(home)
        ReloadCoordinator.advance()
        readiness.mount("Home")
        fixture.scheduler.advance(RootCoordinator.WAIT_LIMIT_MILLIS)
        assertEquals(listOf("place:Home"), layer.log)
        assertNull(coordinator.root)
        assertSame(home, coordinator.pendingRoot)
        coordinator.destroyAll(answerPending = false)
        assertTrue(home.isDestroyed)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun aRootReplacedWhileItWaitsGoesUnseen() {
        val home = screen("Home")
        shown(home)
        val profile = screen("Profile", ready = false)
        setRoot(profile)
        val settings = screen("Settings")
        setRoot(settings)
        assertEquals(listOf(replaced("Profile"), "will:Settings"), entries)
        assertEquals(
            listOf("place:Profile", "remove:Profile", "place:Settings", "raise:Settings", "fade:Home->Settings"),
            layer.log,
        )
        assertTrue(profile.isDestroyed)
        assertFalse(home.isDestroyed)
        layer.endAnimation()
        assertTrue(home.isDestroyed)
        assertTrue("resolve:Settings" in entries)
        assertTrue(entries.none { it.endsWith(":Profile") })
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aRootReplacedInItsTransitionStaysUntilTheNewOneComes() {
        val home = screen("Home")
        shown(home)
        val profile = screen("Profile")
        setRoot(profile)
        val settings = screen("Settings")
        setRoot(settings)
        assertEquals(
            listOf("will:Profile", "didDisappear:Home", "stateNeeded", replaced("Profile"), "will:Settings"),
            entries,
        )
        assertEquals(
            listOf(
                "place:Profile",
                "raise:Profile",
                "fade:Home->Profile",
                "ended:Profile",
                "show:Profile",
                "remove:Home",
                "place:Settings",
                "raise:Settings",
                "fade:Profile->Settings",
            ),
            layer.log,
        )
        assertTrue(home.isDestroyed)
        assertSame(settings, coordinator.root)
        entries.clear()
        layer.endAnimation()
        assertEquals(
            listOf(
                "didDisappear:Profile",
                "stateNeeded",
                "didAppear:Settings",
                "stateNeeded",
                "stateNow",
                "state",
                "resolve:Settings",
                "completed:setRoot",
                "shown",
            ),
            entries,
        )
        assertTrue(profile.isDestroyed)
    }

    @Test
    fun twoSetRootsOfTheSameLayoutLeaveTheLast() {
        val first = screen("Home", ready = false)
        setRoot(first)
        val second = screen("Home")
        setRoot(second)
        assertEquals(1, entries.count { it.startsWith("reject:") })
        assertEquals(1, entries.count { it.startsWith("resolve:") })
        assertTrue(first.isDestroyed)
        assertSame(second, registry.find("Home"))
        assertSame(second, coordinator.root)
    }

    @Test
    fun theActivityGoingResolvesTheWaitingSetRootWithoutAnId() {
        val home = screen("Home")
        shown(home)
        val profile = screen("Profile", ready = false)
        setRoot(profile)
        coordinator.destroyAll(answerPending = true)
        assertEquals(listOf("didDisappear:Home", "stateNeeded", "state", "resolve:", "completed:setRoot"), entries)
        assertTrue(home.isDestroyed)
        assertTrue(profile.isDestroyed)
        assertNull(coordinator.root)
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aReloadTakesEverythingSilentlyAndAnswersNothing() {
        val home = screen("Home", native = true)
        shown(home)
        val profile = screen("Profile", ready = false)
        setRoot(profile)
        ReloadCoordinator.advance()
        coordinator.destroyAll(answerPending = false)
        assertTrue(entries.isEmpty())
        assertTrue(home.isDestroyed)
        assertTrue(profile.isDestroyed)
        assertNull(coordinator.pendingRoot)
    }

    @Test
    fun aReloadDropsTheWaitingSetRootWithoutAnAnswer() {
        shown(screen("Home"))
        setRoot(screen("Profile", ready = false))
        entries.clear()
        coordinator.destroyAll(answerPending = false)
        assertTrue(entries.none { it.startsWith("resolve:") || it.startsWith("reject:") })
    }

    @Test
    fun aRootDestroyedWhileItWaitsRejects() {
        val profile = screen("Profile", ready = false)
        setRoot(profile)
        profile.destroy()
        fixture.scheduler.advance(RootCoordinator.WAIT_LIMIT_MILLIS)
        assertEquals(listOf("reject:${ErrorTexts.rootDestroyed("Profile")}"), entries)
        assertNull(coordinator.root)
    }

    // The modals over the root and the layers of the layout.

    @Test
    fun theLayersShowAfterTheRootOneByOneAndTheAnswerComesAfterThem() {
        val modal = Step("Details")
        val overlay = Step("Toast")
        setRoot(screen("Home"), modal, overlay)
        // The state waits for the answer.
        assertEquals(listOf("will:Home", "didAppear:Home", "shown", "layer:Details"), entries)
        modal.finish()
        assertEquals("layer:Toast", entries.last())
        overlay.finish()
        assertEquals(listOf("state", "resolve:Home", "completed:setRoot"), entries.takeLast(3))
        assertNull(coordinator.pendingRoot)
    }

    @Test
    fun aLayerThatFailsIsSkipped() {
        val last = Step("Toast")
        setRoot(screen("Home"), Step("Details", fails = true), last)
        assertTrue("layer:Details" in entries)
        assertEquals("layer:Toast", entries.last())
        last.finish()
        assertEquals("resolve:Home", entries[entries.size - 2])
    }

    @Test
    fun aNewerSetRootStopsTheLayersAndTheRootStays() {
        val home = screen("Home")
        val modal = Step("Details")
        setRoot(home, modal)
        val profile = screen("Profile", ready = false)
        setRoot(profile)
        // The update the root's appearance asked for goes as the hold ends without an answer.
        assertEquals(listOf("stop:Details", "stateNeeded", replaced("Home")), entries.takeLast(3))
        assertEquals(0, fixture.journal.holds)
        assertSame(home, coordinator.root)
        assertFalse(home.isDestroyed)
        modal.finish()
        assertTrue(entries.none { it.startsWith("resolve:") })
        readiness.mount("Profile")
        layer.endAnimation()
        assertTrue(home.isDestroyed)
        assertTrue("resolve:Profile" in entries)
    }

    @Test
    fun theActivityGoingDuringTheLayersResolvesWithTheRootThatShowed() {
        setRoot(screen("Home"), Step("Details"))
        entries.clear()
        coordinator.destroyAll(answerPending = true)
        assertEquals("stop:Details", entries.first())
        assertTrue("resolve:Home" in entries)
        assertTrue(entries.none { it.startsWith("reject:") })
    }

    @Test
    fun theActivityGoingDuringTheTransitionResolvesWithTheRootThatShowed() {
        shown(screen("Home"))
        setRoot(screen("Profile"))
        assertEquals("fade:Home->Profile", layer.log.last())
        entries.clear()
        coordinator.destroyAll(answerPending = true)
        assertTrue("ended:Profile" in layer.log)
        assertEquals(1, entries.count { it == "resolve:Profile" })
        assertTrue(entries.none { it.startsWith("reject:") })
        assertNull(coordinator.root)
        assertNull(coordinator.pendingRoot)
    }

    @Test
    fun aLayoutModalMakesOneStateRightBeforeTheAnswer() {
        shown(screen("Home"))
        val modal = Step("Login", shows = screen("Login"))
        setRoot(screen("Profile"), modal)
        layer.endAnimation()
        assertEquals(1, fixture.journal.holds)
        modal.finish()
        assertEquals(
            listOf(
                "will:Profile",
                "didDisappear:Home",
                "didAppear:Profile",
                "shown",
                "layer:Login",
                "will:Login",
                "didAppear:Login",
                "stateNeeded",
                "state",
                "resolve:Profile",
                "completed:setRoot",
            ),
            entries,
        )
        assertEquals(0, fixture.journal.holds)
    }

    @Test
    fun twoLayoutModalsMakeOneStateToo() {
        shown(screen("Home"))
        val first = Step("Login", shows = screen("Login"))
        val second = Step("Details", shows = screen("Details"))
        setRoot(screen("Profile"), first, second)
        layer.endAnimation()
        first.finish()
        second.finish()
        assertEquals(1, entries.count { it == "stateNeeded" })
        assertEquals(listOf("stateNeeded", "state", "resolve:Profile", "completed:setRoot"), entries.takeLast(4))
        assertEquals(0, fixture.journal.holds)
    }

    @Test
    fun theHoldEndsWithTheActivityAndAfterASupersededLayerOnce() {
        setRoot(screen("Home"), Step("Details"))
        assertEquals(1, fixture.journal.holds)
        coordinator.destroyAll(answerPending = true)
        assertEquals(0, fixture.journal.holds)
        assertTrue("resolve:Home" in entries)
        // A newer `setRoot` during the layers, then its own layers and their answer: each hold ends once.
        setRoot(screen("Profile"), Step("Login"))
        val last = Step("Toast")
        setRoot(screen("Settings"), last)
        layer.endAnimation()
        assertEquals(1, fixture.journal.holds)
        last.finish()
        assertEquals(0, fixture.journal.holds)
        assertTrue("resolve:Settings" in entries)
    }

    @Test
    fun aSupersedeByARootThatShowsAtOnceSendsOneStateRightBeforeItsAnswer() {
        fixture.journal.defersPosts = true
        shown(screen("Home"))
        fixture.journal.runPosted()
        setRoot(screen("Profile"), Step("Details"))
        layer.endAnimation()
        // The newer root shows in the same pass as the release of the older one's hold, and holds the state itself:
        // the update the release posted runs while held and sends nothing.
        val login = Step("Login", shows = screen("Login"))
        setRoot(screen("Settings", options = waitingFor(false)), login)
        assertEquals("layer:Login", entries.last())
        fixture.journal.runPosted()
        assertEquals(1, fixture.journal.holds)
        login.finish()
        val rejected = entries.indexOf(replaced("Profile"))
        val answered = entries.indexOf("resolve:Settings")
        assertTrue(rejected in 0 until answered)
        assertEquals(listOf("state"), entries.subList(rejected, answered).filter { it.startsWith("state") })
        // After the answer the posted update finds the state sent already.
        fixture.journal.runPosted()
        assertEquals("stateNeeded", entries.last())
        assertEquals(0, fixture.journal.holds)
    }

    @Test
    fun theModalsOverTheRootAreTakenCarriedAndDestroyedWithIt() {
        shown(screen("Home"))
        overRoot.clear()
        setRoot(screen("Profile", ready = false))
        assertEquals(listOf("accepted"), overRoot)
        readiness.mount("Profile")
        assertEquals(listOf("accepted", "replaced:Home"), overRoot)
        layer.endAnimation()
        assertEquals(listOf("accepted", "replaced:Home", "gone"), overRoot)
    }

    @Test
    fun theModalsComeBackWhenTheSetRootEndsWithoutReplacing() {
        shown(screen("Home"))
        overRoot.clear()
        val profile = screen("Profile", ready = false)
        setRoot(profile)
        profile.destroy()
        fixture.scheduler.advance(RootCoordinator.WAIT_LIMIT_MILLIS)
        assertEquals(listOf("accepted", "kept"), overRoot)
        overRoot.clear()
        assertTrue(runCatching { coordinator.setRoot({ error("no root") }, answer()) }.isFailure)
        assertEquals(listOf("kept"), overRoot)
    }

    @Test
    fun aSupersededWaitingSetRootHandsTheModalsToTheNewer() {
        shown(screen("Home"))
        setRoot(screen("Profile", ready = false))
        overRoot.clear()
        setRoot(screen("Settings", ready = false))
        assertEquals(listOf("accepted"), overRoot)
    }

    // A root with its own animation goes over the old one by its sides, instead of the fade through.
    @Test
    fun aRootWithItsOwnAnimation() {
        shown(screen("Home"))
        val profile = screen("Profile", options = Options().apply {
            animations.setRoot.enter.alpha.from = Fraction(0.0)
            animations.setRoot.exit.scaleX.to = Fraction(0.9)
        })
        setRoot(profile)
        assertEquals(listOf("place:Profile", "raise:Profile", "animate:Home->Profile:own:own"), layer.log)
        layer.endAnimation()
        assertEquals("remove:Home", layer.log.last())
        assertTrue(entries.contains("resolve:Profile"))
    }

    // The first root with an animation of its own plays it over the splash, `enter` only.
    @Test
    fun theFirstRootPlaysItsOwnEnter() {
        val home = screen("Home", options = Options().apply {
            animations.setRoot.enter.translationY.from = Fraction(50.0)
        })
        setRoot(home)
        assertEquals(listOf("place:Home", "raise:Home", "animate:null->Home:own:-"), layer.log)
        layer.endAnimation()
        assertTrue(entries.contains("resolve:Home"))
    }

    // `enabled: false` — even from the defaults — doesn't play; `enter.enabled: false` shows it at once.
    @Test
    fun aTurnedOffSideDoesntPlay() {
        shown(screen("Home"))
        setRoot(screen("Profile", options = Options().apply {
            animations.setRoot.enter.enabled = Bool(false)
            animations.setRoot.exit.alpha.to = Fraction(0.0)
        }))
        assertEquals(listOf("place:Profile", "raise:Profile", "show:Profile", "remove:Home"), layer.log)
        layer.log.clear()
        setRoot(screen("Settings", options = Options().apply {
            animations.setRoot.enter.alpha.from = Fraction(0.0)
            animations.setRoot.exit.alpha.to = Fraction(0.0)
            animations.setRoot.exit.enabled = Bool(false)
        }))
        assertEquals(listOf("place:Settings", "raise:Settings", "animate:Profile->Settings:own:-"), layer.log)
    }

    // A newer `setRoot` during a root's own animation ends it at its end values and rejects the older one.
    @Test
    fun aNewerSetRootEndsTheOwnAnimation() {
        shown(screen("Home"))
        setRoot(screen("Profile", options = Options().apply { animations.setRoot.enter.alpha.from = Fraction(0.0) }))
        layer.log.clear()
        setRoot(screen("Settings"))
        assertEquals(listOf("ended:Profile", "show:Profile", "remove:Home"), layer.log.take(3))
        assertTrue(entries.contains(replaced("Profile")))
    }

    /** The views of the root layer, as a journal; the default transition ends when the test says so. */
    // The steps that come later go through the coordinator's guard.

    @Test
    fun aFailureAsTheWaitEndsRejectsAndPutsTheRootUp() {
        val home = screen("Home")
        shown(home)
        overRoot.clear()
        val profile = screen("Profile", ready = false)
        setRoot(profile)
        layer.failRaise = true
        readiness.mount("Profile")
        assertEquals("reject:${ErrorTexts.couldNot("show the root 'Profile'", "no room")}", entries.last())
        assertSame(profile, coordinator.root)
        assertNull(coordinator.pendingRoot)
        assertTrue(home.isDestroyed)
        assertEquals(listOf("accepted", "replaced:Home", "gone"), overRoot)
        assertEquals(0, fixture.scheduler.pending)
    }

    @Test
    fun aFailureAtTheEndOfTheTransitionRejectsAndTheOldRootStillGoes() {
        val home = screen("Home")
        shown(home)
        val profile = screen("Profile")
        setRoot(profile)
        layer.failRemove = true
        layer.endAnimation()
        assertEquals("reject:${ErrorTexts.couldNot("show the root 'Profile'", "no room")}", entries.last())
        assertTrue(entries.none { it.startsWith("resolve") })
        assertSame(profile, coordinator.root)
        assertNull(coordinator.pendingRoot)
        assertTrue(home.isDestroyed)
        assertTrue("remove:Home" in layer.log)
    }

    @Test
    fun aLayerThatFailsAfterAnotherEndedRejectsTheSetRoot() {
        val first = Step("Login")
        setRoot(screen("Home"), first, Step("Settings", throws = true))
        first.finish()
        assertEquals("reject:${ErrorTexts.couldNot("show the root 'Home'", "no layer")}", entries.last())
        assertNull(coordinator.pendingRoot)
        assertEquals(0, fixture.journal.holds)
    }

    // The wait starts under the guard.

    @Test
    fun aWaitThatFailsAsItStartsRejectsAndTheCurrentRootStays() {
        val home = screen("Home")
        shown(home)
        overRoot.clear()
        readiness.failWhenReady = true
        val profile = screen("Profile", ready = false)
        setRoot(profile)
        assertEquals("reject:${ErrorTexts.couldNot("show the root 'Profile'", "no readiness")}", entries.last())
        assertNull(coordinator.pendingRoot)
        assertSame(home, coordinator.root)
        assertTrue(profile.isDestroyed)
        assertTrue("remove:Profile" in layer.log)
        assertEquals(listOf("accepted", "kept"), overRoot)
        assertEquals(0, fixture.scheduler.pending)
        setRoot(screen("Settings"))
        layer.endAnimation()
        assertEquals("resolve:Settings", entries.last { it.startsWith("resolve") })
    }

    private class FakeLayer : RootCoordinator.Layer {
        val log = mutableListOf<String>()
        private var onEnd: (() -> Unit)? = null

        // Each throws once, at the next call: a layer that fails.
        var failRaise = false
        var failRemove = false

        override fun place(root: ViewController<*>) {
            log += "place:${root.id}"
        }

        override fun raise(root: ViewController<*>) {
            if (failRaise) {
                failRaise = false
                error("no room")
            }
            log += "raise:${root.id}"
        }

        override fun show(root: ViewController<*>) {
            log += "show:${root.id}"
        }

        override fun remove(root: ViewController<*>) {
            if (failRemove) {
                failRemove = false
                error("no room")
            }
            log += "remove:${root.id}"
        }

        override fun fadeThrough(old: ViewController<*>, new: ViewController<*>, onEnd: () -> Unit): () -> Unit {
            log += "fade:${old.id}->${new.id}"
            this.onEnd = onEnd
            return {
                log += "ended:${new.id}"
                this.onEnd = null
            }
        }

        // `animate:<old>-><new>:<enter>:<exit>`, each side `own` or `-`.
        override fun animate(
            old: ViewController<*>?,
            new: ViewController<*>,
            enter: ScreenAnimationOptions?,
            exit: ScreenAnimationOptions?,
            onEnd: () -> Unit,
        ): () -> Unit {
            val sides = listOf(enter, exit).joinToString(":") { if (it != null) "own" else "-" }
            log += "animate:${old?.id}->${new.id}:$sides"
            this.onEnd = onEnd
            return {
                log += "ended:${new.id}"
                this.onEnd = null
            }
        }

        fun endAnimation() {
            val end = onEnd ?: return
            onEnd = null
            end()
        }
    }

    /** Screens are ready by id; [mount] makes one ready and tells who waits. */
    private class FakeReadiness : Readiness {
        val ready = mutableSetOf<String>()
        val marked = mutableListOf<String>()
        // Throws once, at the next signal asked for: a readiness that fails as the wait starts.
        var failWhenReady = false
        private val waiting = mutableMapOf<String, MutableList<() -> Unit>>()

        fun mount(id: String) {
            ready += id
            waiting.remove(id)?.forEach { it() }
        }

        override fun isReady(screen: ViewController<*>): Boolean = screen.id in ready

        override fun whenReady(screen: ViewController<*>, listener: () -> Unit) {
            if (failWhenReady) {
                failWhenReady = false
                error("no readiness")
            }
            if (isReady(screen)) listener() else waiting.getOrPut(screen.id) { mutableListOf() } += listener
        }

        override fun markReady(screen: ViewController<*>) {
            marked += screen.id
        }

        // The components of the bars over the screens, drawn in advance: ready unless listed here.
        val barsWaiting = mutableSetOf<String>()
        private val barListeners = mutableMapOf<String, MutableList<() -> Unit>>()

        fun drawBar(id: String) {
            barsWaiting -= id
            barListeners.remove(id)?.forEach { it() }
        }

        override fun areBarComponentsReady(screen: ViewController<*>): Boolean = screen.id !in barsWaiting

        override fun whenBarComponentsReady(screen: ViewController<*>, listener: () -> Unit) {
            if (areBarComponentsReady(screen)) listener() else barListeners.getOrPut(screen.id) { mutableListOf() } += listener
        }
    }
}

/**
 * A screen node that reports its appearance through its own record, ready at once, and goes as a screen does: it
 * disappears if it showed, leaves the registry, and a native one tells `ANN.NativeScreenReleased`.
 */
internal class RootScreen(
    activity: Activity,
    registry: ChildControllersRegistry,
    id: String,
    options: Options,
    private val native: Boolean,
) : ChildController<FrameLayout>(activity, registry, id, Presenter(activity, Options()), options) {
    private val appearance = ScreenAppearance(id, id, ComponentType.Component, null)

    /** Its back throws once, at the next call: content whose own back fails. */
    var failsBack = false

    override val currentComponentName: String = id

    override fun createView(): FrameLayout = FrameLayout(activity)

    override fun onViewWillAppear() = AppearanceDispatcher.willAppear(appearance)

    override fun onViewDidAppear() {
        AppearanceDispatcher.didAppear(appearance)
        super.onViewDidAppear()
    }

    override fun onViewDisappear() = AppearanceDispatcher.didDisappear(appearance)

    override fun handleBack(listener: CommandListener): Boolean {
        if (failsBack) {
            failsBack = false
            error("no back")
        }
        return super.handleBack(listener)
    }

    override fun destroy() {
        if (isDestroyed) return
        onViewDisappear()
        super.destroy()
        if (native) AppearanceDispatcher.nativeScreenReleased(this)
    }
}

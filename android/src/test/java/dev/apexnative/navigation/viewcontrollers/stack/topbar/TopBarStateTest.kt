package dev.apexnative.navigation.viewcontrollers.stack.topbar

import android.app.Activity
import android.view.MotionEvent
import android.view.ViewGroup
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.options.BackButtonOptions
import dev.apexnative.navigation.options.ButtonMenuOptions
import dev.apexnative.navigation.options.ButtonOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SearchBarOptions
import dev.apexnative.navigation.options.TitleComponentOptions
import dev.apexnative.navigation.options.TopBarAnimationOptions
import dev.apexnative.navigation.options.TopBarBackgroundComponentOptions
import dev.apexnative.navigation.options.TopBarEnterExitAnimationOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Fraction
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.options.params.ThemeColour
import dev.apexnative.navigation.react.events.ComponentType
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.react.events.EventJournal
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.component.ComponentViewController
import dev.apexnative.navigation.viewcontrollers.component.ScreenReadiness
import dev.apexnative.navigation.viewcontrollers.stack.StackDisplay.BarMotion
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView
import dev.apexnative.navigation.viewcontrollers.viewcontroller.Presenter
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ScrollEventListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state the bar keeps: showing and hiding, the owner of the row's transition, the React components by screen, the
 * user's menu checks in the screen's own options, the back button's merge exceptions, the presses of a screen
 * (EVT-NavigationButtonPressed) and the search's copy of its group.
 */
class TopBarStateTest {
    private val journal = mutableListOf<String>()
    private val fixture = DispatcherFixture(EventJournal(journal))

    @After
    fun reset() {
        components.destroyAll()
        fixture.uninstall()
        ScreenReadiness.reset()
    }

    // Visibility.

    private class FakeBar : TopBarVisibility.Target {
        val log = mutableListOf<String>()
        var end: (() -> Unit)? = null
        override val hiddenOffset = 200f
        override var translationY = 0f
        override var isGone = false

        override fun slide(to: Float, duration: Long, onEnd: () -> Unit): () -> Unit {
            log += "slide:$to:$duration"
            end = onEnd
            return { log += "stop" }
        }

        override fun play(side: TopBarAnimationOptions, onEnd: () -> Unit): () -> Unit {
            log += "play"
            end = onEnd
            return { log += "stop" }
        }

        override fun resetCollapse() {
            log += "reset"
        }
    }

    @Test
    fun aHideSlidesUpAndGoesGoneAtItsEnd() {
        val bar = FakeBar()
        val visibility = TopBarVisibility(bar)
        visibility.hide(animated = true)
        assertEquals(listOf("slide:-200.0:300"), bar.log)
        assertFalse(bar.isGone)
        bar.end?.invoke()
        assertTrue(bar.isGone)
        assertTrue(visibility.isHidden)
    }

    // In a transition the side of the options plays: its `exit` hides, its `enter` shows; `enabled: false` at once; a
    // side that doesn't play leaves the slide.
    @Test
    fun aTransitionsSideHidesAndShowsTheBar() {
        val bar = FakeBar()
        val visibility = TopBarVisibility(bar)
        val sides = TopBarEnterExitAnimationOptions().apply {
            exit.translationY.to = Fraction(-56.0)
            enter.enabled = Bool(false)
        }
        visibility.apply(visible = false, animated = true, side = sides)
        assertEquals(listOf("play"), bar.log)
        assertFalse(bar.isGone)
        bar.end?.invoke()
        assertTrue(bar.isGone)
        bar.log.clear()
        visibility.apply(visible = true, animated = true, side = sides)
        assertEquals(listOf("reset"), bar.log)
        assertFalse(bar.isGone)
        assertFalse(visibility.isHidden)
        bar.log.clear()
        visibility.apply(visible = false, animated = true, side = TopBarEnterExitAnimationOptions().apply {
            exit.alpha.duration = Fraction(500.0)
        })
        assertEquals(listOf("slide:-200.0:300"), bar.log)
    }

    // An opposite command cancels the side's motion with its own token.
    @Test
    fun anOppositeCommandCancelsTheSide() {
        val bar = FakeBar()
        val visibility = TopBarVisibility(bar)
        val sides = TopBarEnterExitAnimationOptions().apply { exit.alpha.to = Fraction(0.0) }
        visibility.apply(visible = false, animated = true, side = sides)
        val hideEnd = bar.end
        visibility.show(animated = true)
        assertEquals(listOf("play", "stop", "reset", "slide:0.0:300"), bar.log)
        hideEnd?.invoke()
        assertFalse(bar.isGone)
    }

    @Test
    fun aShowCancelsARunningHideAndStaysShown() {
        val bar = FakeBar()
        val visibility = TopBarVisibility(bar)
        visibility.hide(animated = true)
        val hideEnd = bar.end
        visibility.show(animated = true)
        assertEquals(listOf("slide:-200.0:300", "stop", "reset", "slide:0.0:300"), bar.log)
        // The cancelled hide's end does nothing any more.
        hideEnd?.invoke()
        assertFalse(bar.isGone)
        assertFalse(visibility.isHidden)
    }

    @Test
    fun withoutAnimationAtOnceAndShownAgainInPlace() {
        val bar = FakeBar()
        val visibility = TopBarVisibility(bar)
        visibility.hide(animated = false)
        assertTrue(bar.isGone)
        bar.translationY = -50f
        visibility.show(animated = false)
        assertFalse(bar.isGone)
        assertEquals(0f, bar.translationY, 0f)
        assertEquals(listOf("reset"), bar.log)
    }

    @Test
    fun theSameVisibilityAgainDoesNothing() {
        val bar = FakeBar()
        val visibility = TopBarVisibility(bar)
        visibility.show(animated = true)
        assertTrue(bar.log.isEmpty())
    }

    // The row's transition.

    private class FakeRow(var drawable: Boolean = true) : BarRowTransition.Target {
        val log = mutableListOf<String>()
        val frames = mutableListOf<(Long) -> Unit>()
        override val isRtl = false
        override val curve: (Float) -> Float = { it }

        override fun takeSnapshot(): Boolean {
            log += "snapshot:$drawable"
            return drawable
        }

        override fun dropSnapshot() {
            log += "drop"
        }

        override fun show(frame: RowTimeline.Frame, rtl: Boolean) {
            log += "row:${frame.rowAlpha}"
        }

        override fun rest() {
            log += "rest"
        }

        override fun play(duration: Long, onFrame: (elapsed: Long) -> Unit): () -> Unit {
            frames += onFrame
            return { log += "stop" }
        }
    }

    @Test
    fun theSnapshotOncePerTransitionAndRestAtItsEnd() {
        val row = FakeRow()
        val transition = BarRowTransition(row)
        transition.begin(BarMotion.Push)
        assertEquals(listOf("snapshot:true", "row:0.0"), row.log)
        transition.end()
        assertEquals(listOf("snapshot:true", "row:0.0", "stop", "rest"), row.log)
        assertFalse(transition.isRunning)
    }

    @Test
    fun aLaterTransitionTakesTheBarAndTheOlderWritesNoMore() {
        val row = FakeRow()
        val transition = BarRowTransition(row)
        transition.begin(BarMotion.Push)
        val first = row.frames.first()
        transition.begin(BarMotion.Pop)
        row.log.clear()
        first(200L)
        assertTrue(row.log.isEmpty())
        row.frames.last()(200L)
        assertEquals(listOf("row:1.0"), row.log)
    }

    @Test
    fun atOnceNoSnapshotAndTheRowAtRest() {
        val row = FakeRow()
        val transition = BarRowTransition(row)
        transition.begin(BarMotion.Push)
        row.log.clear()
        transition.begin(null)
        assertEquals(listOf("stop", "rest"), row.log)
        assertFalse(transition.isRunning)
    }

    @Test
    fun aBarThatCantBeDrawnHasNoSnapshotAndItsRowHides() {
        val row = FakeRow(drawable = false)
        BarRowTransition(row).begin(BarMotion.Pop)
        assertEquals(listOf("snapshot:false", "drop", "row:0.0"), row.log)
    }

    // Components.

    private class Root(var ready: Boolean = false) : IReactView {
        var destroyed = 0
        val events = mutableListOf<String>()
        private val waiting = mutableListOf<Runnable>()

        fun mount() {
            ready = true
            waiting.toList().forEach(Runnable::run)
            waiting.clear()
        }

        override fun asView(): ViewGroup = throw UnsupportedOperationException()
        override fun isReady(): Boolean = ready
        override fun isRendered(): Boolean = ready
        override fun destroy() {
            destroyed++
        }
        override fun sendOnNavigationButtonPressed(buttonId: String) = Unit
        override fun getScrollEventListener(): ScrollEventListener? = null
        override fun dispatchTouchEventToJs(event: MotionEvent) = Unit
        override fun whenReady(listener: Runnable) {
            if (ready) listener.run() else waiting += listener
        }
        override fun sendComponentWillStart(type: ComponentType) {
            events += "will:${type.jsName}"
        }
    }

    private val made = mutableListOf<String>()
    private val roots = HashMap<String, Root>()
    private val components = BarComponents { id, name ->
        made += "$name:$id"
        Root().also { roots[id] = it }
    }
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    private fun screen(id: String) =
        ComponentViewController(activity, registry, id, "Home", Presenter(activity, Options()), Options())

    private fun title(name: String, id: String) = TitleComponentOptions().apply {
        this.name = Text(name)
        componentId = Text(id)
    }

    private fun background(name: String, id: String) = TopBarBackgroundComponentOptions().apply {
        this.name = Text(name)
        componentId = Text(id)
    }

    @Test
    fun theSameNameAndIdIsTheSameRootAnotherReplacesIt() {
        val a = screen("A")
        val first = components.title(a, title("Title", "T1"))
        assertSame(first, components.title(a, title("Title", "T1")))
        val second = components.title(a, title("Title", "T2"))
        assertNotSame(first, second)
        assertEquals(1, roots.getValue("T1").destroyed)
        assertEquals(listOf("Title:T1", "Title:T2"), made)
        assertNull(components.title(a, TitleComponentOptions()))
        assertEquals(1, roots.getValue("T2").destroyed)
    }

    @Test
    fun aSharedBackgroundIsOneRootAndGoesWhenNoScreenNamesIt() {
        val a = screen("A")
        val b = screen("B")
        val first = components.background(a, background("Wave", "W"))
        assertSame(first, components.background(b, background("Wave", "W")))
        assertEquals(listOf("Wave:W"), made)
        components.screenDestroyed(b)
        assertEquals(0, roots.getValue("W").destroyed)
        components.screenDestroyed(a)
        assertEquals(1, roots.getValue("W").destroyed)
    }

    @Test
    fun theButtonRootsAreKeptByComponentIdAndTheUnusedGo() {
        val a = screen("A")
        fun button(id: String, componentId: String) = ButtonOptions(Text(id)).apply {
            component.name = Text("Badge")
            component.componentId = Text(componentId)
        }
        components.buttons(a, listOf(button("one", "C1"), button("two", "C2")))
        components.buttons(a, listOf(button("one", "C1")))
        assertEquals(listOf("Badge:C1", "Badge:C2"), made)
        assertEquals(0, roots.getValue("C1").destroyed)
        assertEquals(1, roots.getValue("C2").destroyed)
        components.screenDestroyed(a)
        assertEquals(1, roots.getValue("C1").destroyed)
    }

    @Test
    fun theReadinessCountsTheTitleAndTheBackgroundUnlessTheirWaitForRenderIsFalse() {
        val a = screen("A")
        val options = Options()
        options.topBar.title.component = title("Title", "T")
        options.topBar.background.component = background("Wave", "W")
        components.title(a, options.topBar.title.component)
        components.background(a, options.topBar.background.component)
        assertFalse(components.isDrawn(a, options))
        var drawn = 0
        components.whenDrawn(a, options) { drawn++ }
        roots.getValue("T").mount()
        assertEquals(0, drawn)
        roots.getValue("W").mount()
        assertEquals(1, drawn)
        assertTrue(components.isDrawn(a, options))
        val notWaited = Options()
        notWaited.topBar.title.component = title("Title", "T3").apply { waitForRender = Bool(false) }
        notWaited.topBar.background.component = background("Wave", "W3").apply { waitForRender = Bool(false) }
        val b = screen("B")
        components.title(b, notWaited.topBar.title.component)
        components.background(b, notWaited.topBar.background.component)
        assertTrue(components.isDrawn(b, notWaited))
    }

    @Test
    fun aRootRendersAtOnceAndItsMarkGoesWithIt() {
        val marking = BarComponents { id, _ ->
            ScreenReadiness.setRendersAtOnce(id, true)
            Root()
        }
        val a = screen("A")
        marking.title(a, title("Title", "Marked"))
        assertFalse(ScreenReadiness.waitsForReadiness("Marked"))
        marking.screenDestroyed(a)
        assertTrue(ScreenReadiness.waitsForReadiness("Marked"))
    }

    // The cells of React buttons.

    @Test
    fun aButtonOfTheStacksOptionsHasACellOnEachScreenAndADestroyKeepsTheOthers() {
        val emptied = mutableListOf<String>()
        var count = 0
        val cells = ButtonCells({ "cell${++count}" }) { emptied += it }
        val a = screen("A")
        val b = screen("B")
        // One `componentId` on both screens: a button of the stack's options.
        val cellA = cells.cellOf(a, "CC1")
        val cellB = cells.cellOf(b, "CC1")
        assertNotSame(cellA, cellB)
        cells.screenDestroyed(b)
        assertEquals(listOf(cellB), emptied)
        assertSame(cellA, cells.cellOf(a, "CC1"))
        cells.retain(a, emptySet())
        assertEquals(listOf(cellB, cellA), emptied)
    }

    // Menu checks.

    private fun menu(single: Boolean = true) = ButtonMenuOptions(
        single,
        listOf(
            ButtonMenuOptions.Item("list", "List", null, false, false, true),
            ButtonMenuOptions.Item("grid", "Grid", null, false, false, false),
        ),
    )

    @Test
    fun aPickOfAMenuFromTheStackLandsInTheScreensOwnCopy() {
        val a = screen("A")
        val shared = menu()
        val resolved = Options()
        resolved.topBar.buttons.right = listOf(ButtonOptions(Text("view")).apply { this.menu = shared })
        MenuChecks.write(a, resolved, "view", "grid")
        val own = a.options.topBar.buttons.right?.single()?.menu
        val ownItems = own?.items?.filterIsInstance<ButtonMenuOptions.Item>().orEmpty()
        assertEquals(listOf(false, true), ownItems.map { it.checked })
        // The stack's menu is untouched.
        assertEquals(listOf(true, false), shared.items.filterIsInstance<ButtonMenuOptions.Item>().map { it.checked })
    }

    @Test
    fun aPickOfTheOverflowMenuLandsInTheScreensOwnCopy() {
        val a = screen("A")
        val shared = menu()
        val resolved = Options().apply { topBar.overflowMenu = shared }
        MenuChecks.write(a, resolved, null, "grid")
        val own = a.options.topBar.overflowMenu?.items?.filterIsInstance<ButtonMenuOptions.Item>().orEmpty()
        assertEquals(listOf(false, true), own.map { it.checked })
        assertTrue((shared.items[0] as ButtonMenuOptions.Item).checked)
    }

    @Test
    fun aMenuWithoutSingleSelectionMovesNoCheck() {
        val a = screen("A")
        val resolved = Options().apply { topBar.overflowMenu = menu(single = false) }
        MenuChecks.write(a, resolved, null, "grid")
        assertNull(a.options.topBar.overflowMenu)
    }

    // The back button's merge exceptions.

    @Test
    fun theDefaultIdAndLabelFromAStrongerLayerDoNotReplaceOnesBelow() {
        val below = BackButtonOptions().apply {
            id = Text("close")
            accessibilityLabel = Text("Close")
        }
        below.mergeWith(BackButtonOptions().apply {
            id = Text(BackButtonOptions.DEFAULT_ID)
            accessibilityLabel = Text(BackButtonOptions.DEFAULT_LABEL)
        })
        assertEquals("close", below.id.get(null))
        assertEquals("Close", below.accessibilityLabel.get(null))
        val own = BackButtonOptions().apply { id = Text(BackButtonOptions.DEFAULT_ID) }
        own.mergeWithDefault(BackButtonOptions().apply { id = Text("close") })
        assertEquals("close", own.id.get(null))
    }

    @Test
    fun anIconBackgroundWithAColorReplacesTheWholeGroupWithoutOneItIsNoLayer() {
        val below = BackButtonOptions().apply {
            iconBackground.color = colour(0xFF0000FF.toInt())
            iconBackground.cornerRadius = Fraction(8.0)
        }
        below.mergeWith(BackButtonOptions().apply { iconBackground.width = Fraction(40.0) })
        assertEquals(8.0, below.iconBackground.cornerRadius.get(0.0), 0.0)
        assertFalse(below.iconBackground.width.hasValue())
        below.mergeWith(BackButtonOptions().apply { iconBackground.color = colour(0xFF00FF00.toInt()) })
        assertFalse(below.iconBackground.cornerRadius.hasValue())
    }

    private fun colour(argb: Int): ThemeColour =
        ThemeColour.parse(null, JavaOnlyMap.of("color", argb.toDouble()), "color")

    // Presses (EVT-NavigationButtonPressed).

    @Test
    fun aScreenSendsItsPressWithItsOwnId() {
        fixture.install()
        screen("A").sendOnNavigationButtonPressed("save")
        assertEquals(listOf("pressed:A:save"), journal)
    }

    // The search gets a copy of its group: what it writes doesn't reach the resolved options.

    @Test
    fun theSearchsCopyLeavesTheResolvedGroupAsItIs() {
        val resolved = SearchBarOptions().apply { placeholder = Text("Find") }
        val copy = SearchBarOptions().apply { mergeWith(resolved) }
        copy.tintColor = colour(0xFF000000.toInt())
        assertFalse(resolved.tintColor.hasValue())
        assertEquals("Find", copy.placeholder.get(null))
    }
}

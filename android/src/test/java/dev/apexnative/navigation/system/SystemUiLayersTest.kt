package dev.apexnative.navigation.system

import android.app.Activity
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.system.SystemUiLayers.Group
import dev.apexnative.navigation.system.SystemUiLayers.Kind
import dev.apexnative.navigation.system.SystemUiLayers.Layer
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.modal.ModalRecord
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which layer decides each group of the system UI. */
class SystemUiLayersTest {
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    @After
    fun resetDefaults() = DefaultOptions.set(Options())

    private fun layer(kind: Kind, isSheet: Boolean = false, vararg named: Group) =
        Layer(kind, isSheet, named.toSet()) { SystemUiRules.Screen(Options()) }

    private val root = layer(Kind.Root)

    @Test
    fun nothingShowsNothingDecides() {
        assertNull(SystemUiLayers.pick(emptyList()))
    }

    @Test
    fun theRootAloneDecidesEverything() {
        val picked = SystemUiLayers.pick(listOf(root))
        assertSame(root, picked?.statusBar)
        assertSame(root, picked?.navigationBar)
        assertSame(root, picked?.orientation)
    }

    @Test
    fun aModalDecidesEverythingWhateverItsStyle() {
        // Full screen, `overCurrentContext` and `overFullScreen` are all modal records that aren't sheets.
        val modal = layer(Kind.Modal)
        val picked = SystemUiLayers.pick(listOf(root, modal))
        assertSame(modal, picked?.statusBar)
        assertSame(modal, picked?.navigationBar)
        assertSame(modal, picked?.orientation)
    }

    @Test
    fun aSheetLeavesTheStatusBarToTheLayerUnderIt() {
        val sheet = layer(Kind.Modal, isSheet = true)
        val picked = SystemUiLayers.pick(listOf(root, sheet))
        assertSame(root, picked?.statusBar)
        assertSame(sheet, picked?.navigationBar)
        assertSame(sheet, picked?.orientation)
    }

    @Test
    fun underTwoSheetsTheFirstLayerThatIsntOne() {
        val modal = layer(Kind.Modal)
        val first = layer(Kind.Modal, isSheet = true)
        val second = layer(Kind.Modal, isSheet = true)
        val picked = SystemUiLayers.pick(listOf(root, modal, first, second))
        assertSame(modal, picked?.statusBar)
        assertSame(second, picked?.navigationBar)
    }

    @Test
    fun withOnlySheetsNothingDecidesTheStatusBar() {
        val sheet = layer(Kind.Modal, isSheet = true)
        val picked = SystemUiLayers.pick(listOf(sheet))
        assertNull(picked?.statusBar)
        assertSame(sheet, picked?.navigationBar)
    }

    @Test
    fun withOnlyOverlaysTheGroupsTheyDontNameHaveNoDecider() {
        val toast = layer(Kind.Overlay, false, Group.StatusBar)
        val picked = SystemUiLayers.pick(listOf(toast))
        assertSame(toast, picked?.statusBar)
        assertNull(picked?.navigationBar)
        assertNull(picked?.orientation)
    }

    @Test
    fun theStatusBarKeysOnlyIOSReadsNameNothingOnAndroid() {
        val ios = Options().apply {
            statusBar.blur = Bool(true)
            statusBar.animate = Bool(false)
            statusBar.hideWithTopBar = Bool(true)
        }
        assertEquals(emptySet<Group>(), SystemUiLayers.namedGroups(ios))
        val android = Options().apply { statusBar.translucent = Bool(true) }
        assertEquals(setOf(Group.StatusBar), SystemUiLayers.namedGroups(android))
    }

    @Test
    fun onlyAModalThatShowsOrComesInCounts() {
        assertTrue(SystemUiLayers.modalCounts(ModalRecord.State.Presenting))
        assertTrue(SystemUiLayers.modalCounts(ModalRecord.State.Shown))
        assertFalse(SystemUiLayers.modalCounts(ModalRecord.State.Waiting))
        assertFalse(SystemUiLayers.modalCounts(ModalRecord.State.Dismissing))
        assertFalse(SystemUiLayers.modalCounts(ModalRecord.State.ClosedBelow))
        assertFalse(SystemUiLayers.modalCounts(ModalRecord.State.Gone))
    }

    @Test
    fun anOverlayThatNamesNothingDecidesNothing() {
        val toast = layer(Kind.Overlay)
        val picked = SystemUiLayers.pick(listOf(root, toast))
        assertSame(root, picked?.statusBar)
        assertSame(root, picked?.navigationBar)
        assertSame(root, picked?.orientation)
    }

    @Test
    fun anOverlayDecidesOnlyTheGroupsItNames() {
        val status = layer(Kind.Overlay, false, Group.StatusBar)
        val picked = SystemUiLayers.pick(listOf(root, status))
        assertSame(status, picked?.statusBar)
        assertSame(root, picked?.navigationBar)
        assertSame(root, picked?.orientation)
        val rotating = layer(Kind.Overlay, false, Group.Orientation)
        assertSame(rotating, SystemUiLayers.pick(listOf(root, rotating))?.orientation)
    }

    @Test
    fun theTopmostOverlayNamingAGroupWinsIt() {
        val lower = layer(Kind.Overlay, false, Group.StatusBar, Group.NavigationBar)
        val upper = layer(Kind.Overlay, false, Group.StatusBar)
        val picked = SystemUiLayers.pick(listOf(root, lower, upper))
        assertSame(upper, picked?.statusBar)
        assertSame(lower, picked?.navigationBar)
        assertSame(root, picked?.orientation)
    }

    @Test
    fun anOverlayCountsOnlyWhatItsOwnAndItsParentsOptionsName() {
        DefaultOptions.set(
            Options().apply {
                statusBar.style = Text("dark")
                navigationBar.visible = Bool(false)
                layout.orientation = listOf("portrait")
            },
        )
        val toast = TestScreen(activity, registry, "Toast")
        assertEquals(emptySet<Group>(), SystemUiLayers.overlayOf(toast).named)
        val dialog = TestScreen(activity, registry, "Dialog", Options().apply { statusBar.style = Text("light") })
        val stack = TestLayout(activity, registry, "Stack", Options().apply { layout.orientation = listOf("all") })
        stack.add(dialog)
        val overlay = SystemUiLayers.overlayOf(stack)
        assertEquals(setOf(Group.StatusBar, Group.Orientation), overlay.named)
        // Its values are its own: no style of the defaults, its own over its parents'.
        assertEquals("light", overlay.screen.options.statusBar.style.get(null))
        assertFalse(overlay.screen.options.navigationBar.visible.hasValue())
    }

    @Test
    fun theRootAndTheModalsResolveWithTheDefaults() {
        DefaultOptions.set(Options().apply { statusBar.style = Text("dark") })
        val home = TestScreen(activity, registry, "Home")
        val layer = SystemUiLayers.layerOf(home, Kind.Root, isSheet = false)
        assertEquals("dark", layer.screen.options.statusBar.style.get(null))
    }
}

package dev.apexnative.navigation.viewcontrollers.viewcontroller

import android.app.Activity
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.OptionsCache
import dev.apexnative.navigation.options.OptionsFixture
import dev.apexnative.navigation.options.OptionsReader
import dev.apexnative.navigation.options.OptionsTree
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.parent.OptionsScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A node's own options and its resolved options: one result kept while nothing changes, a new one after any change it
 * depends on, and own options that never take the defaults or the one-shot options.
 */
class ResolvedOptionsTest {
    private val activity = Activity()
    private val registry = ChildControllersRegistry()
    private val calls = Calls()

    private fun read(map: JavaOnlyMap): Options = OptionsReader.read(map, null).options

    private fun title(text: String) =
        read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("title", JavaOnlyMap.of("text", text))))

    private fun screen(id: String, options: Options = Options()) =
        TestScreen(activity, registry, id, options, RecordingPresenter(activity, calls))

    private fun layout(id: String, options: Options = Options()) =
        TestLayout(activity, registry, id, options, RecordingPresenter(activity, calls), calls)

    @Before
    fun emptyDefaults() {
        DefaultOptions.set(Options())
    }

    @After
    fun clearDefaults() {
        DefaultOptions.set(Options())
    }

    @Test
    fun theSameResultWhileNothingChanges() {
        val stack = layout("Stack1")
        val home = screen("Home")
        stack.add(home)
        val first = home.resolveCurrentOptions(DefaultOptions.current)
        assertSame(first, home.resolveCurrentOptions(DefaultOptions.current))
        assertSame(first, home.resolveCurrentOptions(stack.presenter.defaultOptions))
    }

    @Test
    fun otherDefaultsAreNotKept() {
        val home = screen("Home")
        val defaults = title("Home")
        assertNotSame(home.resolveCurrentOptions(defaults), home.resolveCurrentOptions(defaults))
        assertEquals("Home", home.resolveCurrentOptions(defaults).topBar.title.text.get())
    }

    @Test
    fun aMergeIntoTheNodeGivesANewResult() {
        val home = screen("Home")
        val first = home.resolveCurrentOptions(DefaultOptions.current)
        home.mergeOptions(title("Profile"))
        val second = home.resolveCurrentOptions(DefaultOptions.current)
        assertNotSame(first, second)
        assertEquals("Profile", second.topBar.title.text.get())
    }

    @Test
    fun aMergeIntoAParentGivesANewResult() {
        val tabs = layout("Tabs")
        val stack = layout("Stack1")
        val home = screen("Home")
        tabs.add(stack)
        stack.add(home)
        val first = home.resolveCurrentOptions(DefaultOptions.current)
        tabs.mergeOptions(title("Tabs"))
        val second = home.resolveCurrentOptions(DefaultOptions.current)
        assertNotSame(first, second)
        assertEquals("Tabs", second.topBar.title.text.get())
    }

    @Test
    fun theNodeIsStrongerThanItsParentsAndTheDefaults() {
        DefaultOptions.set(read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("height", 64, "visible", false))))
        val stack = layout("Stack1", read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("height", 56, "testID", "Stack"))))
        val home = screen("Home", read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("height", 48))))
        stack.add(home)
        val resolved = home.resolveCurrentOptions(DefaultOptions.current)
        assertEquals(48.0, resolved.topBar.height.get(), 0.0)
        assertEquals("Stack", resolved.topBar.testID.get())
        assertTrue(resolved.topBar.visible.isFalse)
    }

    @Test
    fun newDefaultsGiveANewResult() {
        val home = screen("Home")
        val first = home.resolveCurrentOptions(DefaultOptions.current)
        DefaultOptions.set(title("Default"))
        val second = home.resolveCurrentOptions(DefaultOptions.current)
        assertNotSame(first, second)
        assertEquals("Default", second.topBar.title.text.get())
    }

    @Test
    fun aNewParentGivesANewResult() {
        val home = screen("Home")
        val first = home.resolveCurrentOptions(DefaultOptions.current)
        val stack = layout("Stack1", title("Stack"))
        stack.add(home)
        val second = home.resolveCurrentOptions(DefaultOptions.current)
        assertNotSame(first, second)
        assertEquals("Stack", second.topBar.title.text.get())
        val other = layout("Stack2")
        other.add(home)
        assertNotSame(second, home.resolveCurrentOptions(DefaultOptions.current))
    }

    @Test
    fun aNewCurrentChildGivesANewResult() {
        val stack = layout("Stack1")
        val home = screen("Home")
        val profile = screen("Profile")
        stack.add(home)
        val first = stack.resolveCurrentOptions(DefaultOptions.current)
        stack.add(profile)
        assertNotSame(first, stack.resolveCurrentOptions(DefaultOptions.current))
    }

    @Test
    fun resolvingNeverChangesOwnOptions() {
        DefaultOptions.set(read(OptionsFixture.full()))
        val stack = layout("Stack1", title("Stack"))
        val home = screen("Home")
        stack.add(home)
        val own = home.options
        home.resolveCurrentOptions(DefaultOptions.current)
        home.resolveCurrentOptions()
        assertSame(own, home.options)
        assertEquals(emptyList<String>(), OptionsTree.differences(Options(), own))
        assertEquals("Stack", stack.options.topBar.title.text.get())
        assertFalse(stack.options.topBar.visible.hasValue())
    }

    @Test
    fun theUnkeptResultIsANewObjectEachTime() {
        val home = screen("Home", title("Home"))
        val first = home.resolveCurrentOptions()
        first.topBar.title.text = Text("Changed")
        assertEquals("Home", home.resolveCurrentOptions().topBar.title.text.get())
        assertEquals("Home", home.options.topBar.title.text.get())
    }

    @Test
    fun oneShotOptionsAreNotKept() {
        val initial = read(
            JavaOnlyMap.of(
                "bottomTabs", JavaOnlyMap.of("currentTabIndex", 1, "currentTabId", "Profile"),
                "sideMenu", JavaOnlyMap.of("left", JavaOnlyMap.of("visible", true, "width", 280)),
            ),
        )
        val tabs = layout("Tabs", initial)
        assertTrue(tabs.initialOptions.bottomTabs.currentTabIndex.hasValue())
        assertFalse(tabs.options.bottomTabs.currentTabIndex.hasValue())
        assertFalse(tabs.options.bottomTabs.currentTabId.hasValue())
        assertFalse(tabs.options.sideMenu.left.visible.hasValue())
        assertEquals(280.0, tabs.options.sideMenu.left.width.get(), 0.0)
        tabs.mergeOptions(read(JavaOnlyMap.of("sideMenu", JavaOnlyMap.of("right", JavaOnlyMap.of("visible", true)))))
        assertFalse(tabs.options.sideMenu.right.visible.hasValue())
    }

    @Test
    fun theOptionsSetterNeverChangesTheCallersObject() {
        val given = read(
            JavaOnlyMap.of(
                "bottomTabs", JavaOnlyMap.of("currentTabIndex", 1, "currentTabId", "Profile"),
                "sideMenu", JavaOnlyMap.of("left", JavaOnlyMap.of("visible", true, "width", 280)),
            ),
        )
        val tabs = layout("Tabs")
        tabs.options = given
        assertTrue(given.bottomTabs.currentTabIndex.hasValue())
        assertTrue(given.bottomTabs.currentTabId.hasValue())
        assertTrue(given.sideMenu.left.visible.hasValue())
        assertNotSame(given, tabs.options)
        assertFalse(tabs.options.bottomTabs.currentTabIndex.hasValue())
        assertFalse(tabs.options.sideMenu.left.visible.hasValue())
        assertEquals(280.0, tabs.options.sideMenu.left.width.get(), 0.0)
    }

    @Test
    fun initialOptionsKeepTheOneShotOptionsAfterTheyAreSetAsOwnOptions() {
        val initial = read(JavaOnlyMap.of("bottomTabs", JavaOnlyMap.of("currentTabIndex", 1)))
        val tabs = layout("Tabs", initial)
        tabs.options = tabs.initialOptions
        assertSame(initial, tabs.initialOptions)
        assertEquals(1.0, tabs.initialOptions.bottomTabs.currentTabIndex.get(), 0.0)
        assertFalse(tabs.options.bottomTabs.currentTabIndex.hasValue())
    }

    @Test
    fun anOptionsObjectWithoutOneShotOptionsIsKeptAsItIs() {
        val given = title("Home")
        val home = screen("Home")
        home.options = given
        assertSame(given, home.options)
    }

    @Test
    fun changeOptionsStoresAChangedCopy() {
        val home = screen("Home", title("Home"))
        val before = home.options
        val generation = OptionsCache.generation
        home.changeOptions { it.topBar.searchBar.selectedScope = Text("all") }
        assertNotSame(before, home.options)
        assertFalse(before.topBar.searchBar.selectedScope.hasValue())
        assertEquals("all", home.options.topBar.searchBar.selectedScope.get())
        assertEquals("Home", home.options.topBar.title.text.get())
        assertTrue(OptionsCache.generation > generation)
    }

    @Test
    fun aMergeStaysAppliesWhereShownAndGoesUp() {
        val tabs = layout("Tabs")
        val stack = layout("Stack1")
        val home = screen("Home")
        tabs.add(stack)
        stack.add(home)
        home.mergeOptions(title("Profile"))
        assertEquals("Profile", home.options.topBar.title.text.get())
        assertEquals(listOf("mergeChild:Stack1<-Home", "mergeChild:Tabs<-Stack1"), calls.entries)
        calls.entries.clear()
        home.shown = true
        home.mergeOptions(title("Settings"))
        assertEquals(listOf("merge:Home", "mergeChild:Stack1<-Home", "mergeChild:Tabs<-Stack1"), calls.entries)
    }

    @Test
    fun theRepaintGoesDownTheVisibleChainChildrenFirst() {
        val tabs = layout("Tabs")
        val stack = layout("Stack1")
        val home = screen("Home")
        val hidden = screen("Profile")
        tabs.add(hidden)
        tabs.add(stack)
        stack.add(home)
        listOf(tabs, stack, home, hidden).forEach { it.getView() }
        DefaultOptions.set(title("Default"))
        Navigator.repaintLayer(tabs)
        assertEquals(listOf("repaint:Home", "repaint:Stack1", "repaint:Tabs"), calls.entries)
        assertEquals("Default", calls.options[0].topBar.title.text.get())
    }

    // The options a node shows with (`resolveShownOptions`).

    @Test
    fun aScreenShowsWithItsResolvedOptions() {
        val stack = layout("Stack1")
        val home = screen("Home", title("Home"))
        stack.add(home)
        assertSame(home.resolveCurrentOptions(DefaultOptions.current), home.resolveShownOptions(DefaultOptions.current))
    }

    @Test
    fun aLayoutShowsWithTheOptionsOfTheEndOfItsCurrentChain() {
        val tabs = layout("Tabs", title("Tabs"))
        val stack = layout("Stack1")
        val profile = screen("Profile", title("Profile"))
        val home = screen("Home", title("Home"))
        tabs.add(profile)
        tabs.add(stack)
        stack.add(home)
        val shown = tabs.resolveShownOptions(DefaultOptions.current)
        assertSame(home.resolveCurrentOptions(DefaultOptions.current), shown)
        assertSame(shown, stack.resolveShownOptions(DefaultOptions.current))
        assertEquals("Home", shown.topBar.title.text.get())
        // Its own resolution is not what it shows with.
        assertEquals("Tabs", tabs.resolveCurrentOptions(DefaultOptions.current).topBar.title.text.get())
    }

    @Test
    fun theShownOptionsFollowTheCurrentChildAndTheDefaults() {
        val stack = layout("Stack1")
        val home = screen("Home", title("Home"))
        val profile = screen("Profile")
        stack.add(home)
        assertEquals("Home", stack.resolveShownOptions(DefaultOptions.current).topBar.title.text.get())
        stack.add(profile)
        assertFalse(stack.resolveShownOptions(DefaultOptions.current).topBar.title.text.hasValue())
        DefaultOptions.set(title("Default"))
        assertEquals("Default", stack.resolveShownOptions(DefaultOptions.current).topBar.title.text.get())
    }

    @Test
    fun aLayoutWithoutAChildShowsWithItsOwnResolution() {
        val stack = layout("Stack1", title("Stack"))
        assertNull(stack.currentChild)
        val shown = stack.resolveShownOptions(DefaultOptions.current)
        assertSame(stack.resolveCurrentOptions(DefaultOptions.current), shown)
        assertEquals("Stack", shown.topBar.title.text.get())
    }

    @Test
    fun aPairShowsWithItsOptionsScreen() {
        val stack = layout("Stack1")
        val pair = PairLayout("Pair1")
        val primary = screen("Home", title("Home"))
        val secondary = screen("Details", title("Details"))
        stack.add(pair)
        pair.add(primary)
        pair.add(secondary)
        pair.primary = primary
        assertSame(secondary, pair.currentChild)
        assertEquals("Home", stack.resolveShownOptions(DefaultOptions.current).topBar.title.text.get())
    }

    @Test
    fun theRepaintGoesToEveryVisibleChildAndGivesALayoutTheOptionsItShowsWith() {
        val columns = ColumnsLayout("Columns")
        val stack = layout("Stack1")
        val home = screen("Home", title("Home"))
        val details = screen("Details", title("Details"))
        columns.add(details)
        columns.add(stack)
        stack.add(home)
        listOf(columns, stack, home, details).forEach { it.getView() }
        Navigator.repaintLayer(columns)
        assertEquals(listOf("repaint:Details", "repaint:Home", "repaint:Stack1", "repaint:Columns"), calls.entries)
        assertEquals("Home", calls.options[2].topBar.title.text.get())
        assertEquals("Home", calls.options[3].topBar.title.text.get())
    }

    // A pair: its options are its primary's, whatever its current child.
    private inner class PairLayout(id: String) :
        TestLayout(activity, registry, id, Options(), RecordingPresenter(activity, calls), calls), OptionsScreen {
        var primary: ViewController<*>? = null

        override val optionsScreen: ViewController<*>
            get() = primary ?: this
    }

    // Columns side by side: every child shows.
    private inner class ColumnsLayout(id: String) :
        TestLayout(activity, registry, id, Options(), RecordingPresenter(activity, calls), calls) {
        override fun visibleChildren(): List<ViewController<*>> = children.toList()
    }
}

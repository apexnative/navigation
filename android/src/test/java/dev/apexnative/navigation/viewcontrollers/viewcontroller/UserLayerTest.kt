package dev.apexnative.navigation.viewcontrollers.viewcontroller

import android.app.Activity
import dev.apexnative.navigation.options.ButtonMenuOptions
import dev.apexnative.navigation.options.ButtonOptions
import dev.apexnative.navigation.options.DefaultOptions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.OptionsCache
import dev.apexnative.navigation.options.SearchBarScope
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.stack.topbar.MenuChecks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The user layer of a node (`UserChoices`): a menu's check and a search scope the user chose stay apart from the node's
 * own options, go over them as they resolve, survive what doesn't change them and yield to a merge that does.
 */
class UserLayerTest {
    private val activity = Activity()
    private val registry = ChildControllersRegistry()

    private fun screen(id: String, options: Options = Options()) = TestScreen(activity, registry, id, options)

    private fun layout(id: String, options: Options = Options()) = TestLayout(activity, registry, id, options)

    private fun menu(vararg checked: Pair<String, Boolean>, single: Boolean = true) = ButtonMenuOptions(
        single,
        checked.map { (id, on) -> ButtonMenuOptions.Item(id, id, null, false, false, on) },
    )

    private fun button(id: String, menu: ButtonMenuOptions? = null) =
        ButtonOptions(Text(id)).apply { this.menu = menu }

    // Options with the right buttons [buttons].
    private fun right(vararg buttons: ButtonOptions) = Options().apply { topBar.buttons.right = buttons.toList() }

    // The app's options of a view menu with the check on [checked] (a new object each time, as a re-render sends).
    private fun viewMenu(checked: String = "list") =
        right(button("view", menu(*listOf("list", "grid", "map").map { it to (it == checked) }.toTypedArray())))

    private fun title(text: String) = Options().apply { topBar.title.text = Text(text) }

    private fun scopes(vararg ids: String, selected: String? = null) = Options().apply {
        topBar.searchBar.scopes = ids.map { SearchBarScope(it, it) }
        selected?.let { topBar.searchBar.selectedScope = Text(it) }
    }

    private fun resolved(node: ViewController<*>) = node.resolveCurrentOptions(DefaultOptions.current)

    // The checked items of the menu of the button [buttonId] (or of `overflowMenu`) as [node] resolves.
    private fun checkedOf(node: ViewController<*>, buttonId: String? = "view"): List<String> {
        val topBar = resolved(node).topBar
        val menu = if (buttonId == null) {
            topBar.overflowMenu
        } else {
            topBar.buttons.right?.firstOrNull { it.id.get(null) == buttonId }?.menu
        }
        return menu?.items.orEmpty().filterIsInstance<ButtonMenuOptions.Item>().filter { it.checked }.map { it.id }
    }

    private fun pick(node: ViewController<*>, itemId: String, buttonId: String? = "view") =
        MenuChecks.write(node, resolved(node), buttonId, itemId)

    @Before
    fun emptyDefaults() {
        DefaultOptions.set(Options())
    }

    @After
    fun clearDefaults() {
        DefaultOptions.set(Options())
    }

    // Where the choice lives.

    @Test
    fun aPickOfAStackMenuGoesIntoTheUserLayerAndNotIntoAnyOptions() {
        val stack = layout("Stack1", viewMenu())
        val home = screen("Home")
        stack.add(home)
        val shared = stack.options.topBar.buttons.right?.single()?.menu
        val generation = OptionsCache.generation
        pick(home, "grid")
        assertEquals(listOf("grid"), checkedOf(home))
        assertNull(home.options.topBar.buttons.right)
        val items = shared?.items.orEmpty().filterIsInstance<ButtonMenuOptions.Item>()
        assertEquals(listOf(true, false, false), items.map { it.checked })
        assertTrue(OptionsCache.generation > generation)
    }

    @Test
    fun aPickOfTheOverflowMenuGoesIntoTheUserLayer() {
        val home = screen("Home", Options().apply { topBar.overflowMenu = menu("list" to true, "grid" to false) })
        pick(home, "grid", buttonId = null)
        assertEquals(listOf("grid"), checkedOf(home, buttonId = null))
        val own = home.options.topBar.overflowMenu?.items.orEmpty().filterIsInstance<ButtonMenuOptions.Item>()
        assertEquals(listOf(true, false), own.map { it.checked })
    }

    @Test
    fun aMenuWithoutSingleSelectionKeepsNoChoice() {
        val home = screen("Home", Options().apply { topBar.overflowMenu = menu("list" to true, single = false) })
        val before = resolved(home)
        pick(home, "list", buttonId = null)
        assertSame(before, resolved(home))
    }

    @Test
    fun aChangeOfTheUserLayerMovesTheCache() {
        val home = screen("Home", scopes("all", "recent"))
        val first = resolved(home)
        assertSame(first, resolved(home))
        home.changeChoices { it.pickScope("recent") }
        val second = resolved(home)
        assertNotSame(first, second)
        assertEquals("recent", second.topBar.searchBar.selectedScope.get(null))
        assertSame(second, resolved(home))
    }

    // What the choice survives.

    @Test
    fun aChoiceSurvivesAnUnrelatedMerge() {
        val home = screen("Home", viewMenu())
        pick(home, "grid")
        home.mergeOptions(title("Profile"))
        assertEquals(listOf("grid"), checkedOf(home))
        assertEquals("Profile", resolved(home).topBar.title.text.get(null))
    }

    @Test
    fun aChoiceSurvivesTheSameListSentAgain() {
        val home = screen("Home", viewMenu())
        pick(home, "grid")
        home.mergeOptions(viewMenu())
        home.mergeOptions(viewMenu())
        assertEquals(listOf("grid"), checkedOf(home))
    }

    @Test
    fun aChoiceSurvivesTheSameListSentToAScreenThatHadItFromItsStack() {
        val stack = layout("Stack1", viewMenu())
        val home = screen("Home")
        stack.add(home)
        pick(home, "grid")
        home.mergeOptions(viewMenu())
        assertEquals(listOf("grid"), checkedOf(home))
    }

    @Test
    fun aChoiceSurvivesNewDefaultOptions() {
        DefaultOptions.set(viewMenu())
        val home = screen("Home")
        pick(home, "grid")
        DefaultOptions.set(viewMenu().apply { topBar.title.text = Text("Default") })
        assertEquals(listOf("grid"), checkedOf(home))
        // The defaults' menu itself never takes the check.
        val defaults = DefaultOptions.current.topBar.buttons.right?.single()?.menu
        assertEquals(listOf("list"), defaults?.items.orEmpty().filterIsInstance<ButtonMenuOptions.Item>()
            .filter { it.checked }.map { it.id })
    }

    @Test
    fun aNewListKeepsTheChoiceOfAButtonWithTheSameIdAndTheNewButtonTakesTheAppsValue() {
        val home = screen("Home", viewMenu())
        pick(home, "grid")
        home.mergeOptions(right(
            button("sort", menu("name" to true, "date" to false)),
            button("view", menu("list" to true, "grid" to false, "map" to false)),
        ))
        assertEquals(listOf("grid"), checkedOf(home))
        assertEquals(listOf("name"), checkedOf(home, "sort"))
    }

    // What the choice yields to.

    @Test
    fun aChoiceYieldsToAChangeOfTheChecksByTheApp() {
        val home = screen("Home", viewMenu())
        pick(home, "grid")
        home.mergeOptions(viewMenu(checked = "map"))
        assertEquals(listOf("map"), checkedOf(home))
        // The app's value stays the node's own; the same value again keeps it.
        home.mergeOptions(viewMenu(checked = "map"))
        assertEquals(listOf("map"), checkedOf(home))
    }

    @Test
    fun aButtonThatIsGoneTakesItsChoiceWithIt() {
        val home = screen("Home", viewMenu())
        pick(home, "grid")
        home.mergeOptions(right())
        home.mergeOptions(viewMenu())
        assertEquals(listOf("list"), checkedOf(home))
    }

    @Test
    fun anItemThatIsGoneTakesItsChoiceWithIt() {
        val home = screen("Home", viewMenu())
        pick(home, "grid")
        home.mergeOptions(right(button("view", menu("list" to true, "map" to false))))
        assertEquals(listOf("list"), checkedOf(home))
    }

    @Test
    fun theScopeSurvivesTheSameValueAndYieldsToAnotherOne() {
        val home = screen("Home", scopes("all", "recent", "shared", selected = "all"))
        home.changeChoices { it.pickScope("recent") }
        home.mergeOptions(scopes("all", "recent", "shared", selected = "all"))
        home.mergeOptions(title("Profile"))
        DefaultOptions.set(scopes("all", selected = "all"))
        assertEquals("recent", resolved(home).topBar.searchBar.selectedScope.get(null))
        home.mergeOptions(scopes("all", "recent", "shared", selected = "shared"))
        assertEquals("shared", resolved(home).topBar.searchBar.selectedScope.get(null))
    }

    @Test
    fun aScopeThatIsGoneTakesItsChoiceWithIt() {
        val home = screen("Home", scopes("all", "recent"))
        home.changeChoices { it.pickScope("recent") }
        home.mergeOptions(scopes("all"))
        home.mergeOptions(scopes("all", "recent"))
        assertNull(resolved(home).topBar.searchBar.selectedScope.get(null))
    }

    // The order.

    @Test
    fun theUserLayerGoesOverOwnOverParentsOverDefaults() {
        DefaultOptions.set(scopes("all", "recent", "shared", "starred", selected = "all"))
        val stack = layout("Stack1", Options().apply { topBar.searchBar.selectedScope = Text("recent") })
        val home = screen("Home", Options().apply { topBar.searchBar.selectedScope = Text("shared") })
        stack.add(home)
        assertEquals("shared", resolved(home).topBar.searchBar.selectedScope.get(null))
        home.changeChoices { it.pickScope("starred") }
        assertEquals("starred", resolved(home).topBar.searchBar.selectedScope.get(null))
        // Without the defaults' resolution the layer is not applied: a parent hands its children no user layer.
        assertEquals("shared", home.resolveCurrentOptions().topBar.searchBar.selectedScope.get(null))
        assertEquals("shared", home.options.topBar.searchBar.selectedScope.get(null))
    }
}

package dev.apexnative.navigation.options

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.customrow.BottomTabsCustomRowOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The options of the modules: the identifiers tests find things by, and their menus. */
class ModuleOptionsTest {
    private fun options(map: JavaOnlyMap): Options {
        val read = OptionsReader.read(map, null)
        assertNull(read.unreadablePath)
        return read.options
    }

    private fun topBar(vararg keysAndValues: Any?) = options(JavaOnlyMap.of("topBar", JavaOnlyMap.of(*keysAndValues)))

    private fun overflowMenu(options: Options) = requireNotNull(options.topBar.overflowMenu)

    private fun item(options: Options, index: Int) = overflowMenu(options).items[index] as ButtonMenuOptions.Item

    private fun menuWithTestID() = OptionsFixture.menu().apply { putString("testID", "More") }

    @Test
    fun theSearchBarReadsItsTestIDs() {
        val search = topBar(
            "searchBar", JavaOnlyMap.of(
                "testID", "Search",
                "scopes", JavaOnlyArray.of(
                    JavaOnlyMap.of("id", "all", "title", "All", "testID", "AllScope"),
                    JavaOnlyMap.of("id", "people"),
                ),
                "suggestions", JavaOnlyArray.of(
                    JavaOnlyMap.of("id", "recent", "title", "Recent", "testID", "RecentRow"),
                    JavaOnlyMap.of("id", "other", "testID", ""),
                ),
            ),
        ).topBar.searchBar
        assertEquals("Search", search.testID.get())
        assertEquals(listOf("AllScope", null), search.scopes?.map { it.testID })
        assertEquals(listOf("RecentRow", null), search.suggestions?.map { it.testID })
    }

    @Test
    fun theSearchBarTestIDMergesAndTakesDefaults() {
        val upper = topBar("searchBar", JavaOnlyMap.of("testID", "Search"))
        val merged = topBar("searchBar", JavaOnlyMap.of("testID", "Old")).mergeWith(upper)
        assertEquals("Search", merged.topBar.searchBar.testID.get())
        assertEquals("Search", Options().withDefaultOptions(upper).topBar.searchBar.testID.get())
        val own = topBar("searchBar", JavaOnlyMap.of("testID", "Own")).withDefaultOptions(upper)
        assertEquals("Own", own.topBar.searchBar.testID.get())
    }

    @Test
    fun theCustomRowReadsItsTestID() {
        val row = BottomTabsCustomRowOptions.parse(null, JavaOnlyMap.of("testID", "Row", "height", 56))
        assertEquals("Row", row.testID.get())
        assertEquals("Row", row.copy().testID.get())
        assertTrue(BottomTabsCustomRowOptions.parse(null, JavaOnlyMap.of("testID", "Row")).hasValue())
        assertFalse(BottomTabsCustomRowOptions.parse(null, JavaOnlyMap()).testID.hasValue())
        assertEquals("Row", BottomTabsCustomRowOptions().mergeWith(row).testID.get())
        assertEquals("Row", BottomTabsCustomRowOptions().mergeWithDefault(row).testID.get())
    }

    @Test
    fun theSheetReadsItsTestID() {
        val sheet = SheetOptions.parse(JavaOnlyMap.of("testID", "Sheet"))
        assertEquals("Sheet", sheet.testID.get())
        assertTrue(sheet.hasValue)
        assertEquals("Sheet", SheetOptions().also { it.mergeWith(sheet) }.testID.get())
        assertEquals("Sheet", SheetOptions().also { it.mergeWithDefault(sheet) }.testID.get())
    }

    @Test
    fun aMenuReadsTestIDsOfItsOwnItemsAndSubmenus() {
        val menu = overflowMenu(topBar("overflowMenu", menuWithTestID()))
        assertEquals("More", menu.testID)
        assertEquals("Profile", menu.items[0].testID)
        val submenu = menu.items[1] as ButtonMenuOptions.Submenu
        assertEquals("Settings", submenu.testID)
        assertNull(submenu.items[0].testID)
        assertNull(overflowMenu(topBar("overflowMenu", JavaOnlyMap.of("items", JavaOnlyArray()))).testID)
    }

    // The menu taken from the default options is a copy: a pick the module writes into it never reaches them; between a
    // node's own and resolved options the menu stays shared.

    @Test
    fun aMenuFromTheDefaultsIsCopied() {
        val defaults = topBar("overflowMenu", menuWithTestID())
        val resolved = Options().withDefaultOptions(defaults)
        val defaultMenu = overflowMenu(defaults)
        val menu = overflowMenu(resolved)
        assertNotSame(defaultMenu, menu)
        assertNotSame(defaultMenu.items[0], menu.items[0])
        assertNotSame(defaultMenu.items[1], menu.items[1])
        assertEquals("More", menu.testID)
        assertEquals("Settings", menu.items[1].testID)
        assertEquals("Profile", item(resolved, 0).testID)
        assertEquals(defaultMenu.singleSelection, menu.singleSelection)
        assertTrue(item(resolved, 0).checked)
        item(resolved, 0).checked = false
        assertTrue(item(defaults, 0).checked)
        val submenu = menu.items[1] as ButtonMenuOptions.Submenu
        val defaultSubmenu = defaultMenu.items[1] as ButtonMenuOptions.Submenu
        assertNotSame(defaultSubmenu.items[0], submenu.items[0])
        assertEquals(defaultSubmenu.items.map { it.title }, submenu.items.map { it.title })
    }

    @Test
    fun theMenusOfButtonsAndTitlesFromTheDefaultsAreCopied() {
        val defaults = topBar(
            "title", JavaOnlyMap.of("menu", OptionsFixture.menu()),
            "rightButtons", JavaOnlyArray.of(JavaOnlyMap.of("id", "Settings", "menu", OptionsFixture.menu())),
        )
        val resolved = Options().withDefaultOptions(defaults)
        assertNotSame(defaults.topBar.title.menu, resolved.topBar.title.menu)
        assertNotSame(defaults.topBar.buttons.right?.first()?.menu, resolved.topBar.buttons.right?.first()?.menu)
        val button = ButtonOptions().also { it.mergeWithDefault(requireNotNull(defaults.topBar.buttons.right).first()) }
        assertNotSame(defaults.topBar.buttons.right?.first()?.menu, button.menu)
    }

    @Test
    fun aMenuOfTheNodeStaysSharedWithItsResolvedOptions() {
        val own = topBar("overflowMenu", OptionsFixture.menu())
        val resolved = Options().mergeWith(own).withDefaultOptions(Options())
        assertSame(own.topBar.overflowMenu, resolved.topBar.overflowMenu)
        item(resolved, 0).checked = false
        assertFalse(item(own, 0).checked)
    }
}

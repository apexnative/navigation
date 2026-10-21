package dev.apexnative.navigation.options

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.options.params.ThemeColour.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one merge rule of the native side, for layers, `mergeOptions` and defaults alike. */
class MergeRuleTest {
    private fun options(map: JavaOnlyMap): Options = OptionsReader.read(map, null).options

    private fun topBar(vararg keysAndValues: Any?) = options(JavaOnlyMap.of("topBar", JavaOnlyMap.of(*keysAndValues)))

    @Test
    fun groupsMergeKeyByKeyAtDepth() {
        val below = topBar("title", JavaOnlyMap.of("text", "Home", "fontSize", 17), "visible", true)
        val upper = topBar("title", JavaOnlyMap.of("fontFamily", "Inter", "color", OptionsFixture.color()))
        below.mergeWith(upper)
        assertEquals("Home", below.topBar.title.text.get())
        assertEquals(17.0, below.topBar.title.fontSize.get(), 0.0)
        assertEquals("Inter", below.topBar.title.font.fontFamily.get())
        assertTrue(below.topBar.title.color.hasValue())
        assertTrue(below.topBar.visible.isTrue)
    }

    @Test
    fun valuesReplace() {
        val below = topBar("visible", true, "height", 64, "title", JavaOnlyMap.of("text", "Home"))
        below.mergeWith(topBar("visible", false, "height", 56, "title", JavaOnlyMap.of("text", "Profile")))
        assertTrue(below.topBar.visible.isFalse)
        assertEquals(56.0, below.topBar.height.get(), 0.0)
        assertEquals("Profile", below.topBar.title.text.get())
    }

    @Test
    fun unsetLeavesBelow() {
        val below = topBar("visible", true, "title", JavaOnlyMap.of("text", "Home"))
        below.mergeWith(topBar("visible", "yes", "title", JavaOnlyMap.of("text", 3)))
        assertTrue(below.topBar.visible.isTrue)
        assertEquals("Home", below.topBar.title.text.get())
    }

    @Test
    fun arraysReplaceWhole() {
        val buttons = JavaOnlyArray.of(JavaOnlyMap.of("id", "Settings"), JavaOnlyMap.of("id", "Details"))
        val below = topBar("rightButtons", buttons)
        val upper = topBar("rightButtons", JavaOnlyArray.of(JavaOnlyMap.of("text", "Login")))
        below.mergeWith(upper)
        val right = requireNotNull(below.topBar.buttons.right)
        assertEquals(1, right.size)
        assertFalse(right[0].id.hasValue())
        assertEquals("Login", right[0].text.get())
        assertNotSame(upper.topBar.buttons.right?.get(0), right[0])
    }

    @Test
    fun anEmptyArrayReplaces() {
        val below = topBar("rightButtons", JavaOnlyArray.of(JavaOnlyMap.of("id", "Settings")))
        below.mergeWith(topBar("rightButtons", JavaOnlyArray()))
        assertEquals(emptyList<ButtonOptions>(), below.topBar.buttons.right)
        val edges = options(JavaOnlyMap.of("layout", JavaOnlyMap.of("safeAreaEdges", JavaOnlyArray.of("top"))))
        edges.mergeWith(options(JavaOnlyMap.of("layout", JavaOnlyMap.of("safeAreaEdges", JavaOnlyArray()))))
        assertEquals(emptyList<String>(), edges.layout.safeAreaEdges)
    }

    @Test
    fun aMissingArrayLeavesBelow() {
        val below = topBar("rightButtons", JavaOnlyArray.of(JavaOnlyMap.of("id", "Settings")))
        below.mergeWith(topBar("visible", true))
        assertEquals(listOf("Settings"), below.topBar.buttons.right?.map { it.id.get() })
    }

    @Test
    fun colorsMergeSideBySide() {
        val below = topBar("borderColor", OptionsFixture.color())
        below.mergeWith(topBar("borderColor", JavaOnlyMap.of("light", 1)))
        assertEquals(1, below.topBar.borderColor.get(false, null))
        assertEquals(OptionsFixture.DARK, below.topBar.borderColor.get(true, null))
    }

    @Test
    fun noColorReplaces() {
        val below = topBar("borderColor", OptionsFixture.color())
        below.mergeWith(topBar("borderColor", null))
        assertTrue(below.topBar.borderColor.hasValue())
        assertEquals(Side.None, below.topBar.borderColor.sideOf(false))
        assertNull(below.topBar.borderColor.get(true, null))
    }

    @Test
    fun noTextReplaces() {
        val below = topBar("title", JavaOnlyMap.of("text", "Home"))
        below.mergeWith(topBar("title", JavaOnlyMap.of("text", null)))
        assertTrue(below.topBar.title.text.isNone)
        assertEquals("", below.topBar.title.text.get())
        val defaults = topBar("title", JavaOnlyMap.of("text", "Home"))
        val resolved = topBar("title", JavaOnlyMap.of("text", null)).withDefaultOptions(defaults)
        assertTrue(resolved.topBar.title.text.isNone)
    }

    @Test
    fun defaultsFillOnlyWhatIsUnset() {
        val own = topBar("visible", false, "borderColor", JavaOnlyMap.of("light", 1))
        own.withDefaultOptions(topBar("visible", true, "height", 64, "borderColor", OptionsFixture.color()))
        assertTrue(own.topBar.visible.isFalse)
        assertEquals(64.0, own.topBar.height.get(), 0.0)
        assertEquals(1, own.topBar.borderColor.get(false, null))
        assertEquals(OptionsFixture.DARK, own.topBar.borderColor.get(true, null))
    }

    @Test
    fun aButtonMenuIsSharedOnPurpose() {
        val below = topBar("overflowMenu", OptionsFixture.menu())
        val copy = below.copy()
        assertSame(below.topBar.overflowMenu, copy.topBar.overflowMenu)
    }

    @Test
    fun theSearchResultsComponentIsNotShared() {
        val upper = topBar("searchBar", JavaOnlyMap.of("resultsComponent", JavaOnlyMap.of("name", "Results")))
        val merged = Options().mergeWith(upper)
        assertEquals("Results", merged.topBar.searchBar.resultsComponent.name.get())
        assertNotSame(upper.topBar.searchBar.resultsComponent, merged.topBar.searchBar.resultsComponent)
        val resolved = Options().withDefaultOptions(upper)
        assertNotSame(upper.topBar.searchBar.resultsComponent, resolved.topBar.searchBar.resultsComponent)
    }

    @Test
    fun oneShotOptionsAreTakenAway() {
        val options = options(OptionsFixture.full())
        options.clearOneShotOptions()
        assertFalse(options.bottomTabs.currentTabIndex.hasValue())
        assertFalse(options.bottomTabs.currentTabId.hasValue())
        assertFalse(options.sideMenu.left.visible.hasValue())
        assertFalse(options.sideMenu.right.visible.hasValue())
        assertTrue(options.sideMenu.left.width.hasValue())
    }
}

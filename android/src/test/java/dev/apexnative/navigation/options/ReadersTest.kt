package dev.apexnative.navigation.options

import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import dev.apexnative.navigation.options.params.NoneText
import dev.apexnative.navigation.options.parsers.BoolParser
import dev.apexnative.navigation.options.parsers.FontParser
import dev.apexnative.navigation.options.parsers.FractionParser
import dev.apexnative.navigation.options.parsers.IconParser
import dev.apexnative.navigation.options.parsers.StringListParser
import dev.apexnative.navigation.options.parsers.TextParser
import dev.apexnative.navigation.options.parsers.VariantParser
import dev.apexnative.navigation.utils.UiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The readers of each kind of value: what reads and what is unset. */
class ReadersTest {
    private fun map(value: Any?) = JavaOnlyMap.of("key", value)

    // Yes or no: true and false only.

    @Test
    fun booleans() {
        assertTrue(BoolParser.parse(map(true), "key").isTrue)
        assertTrue(BoolParser.parse(map(false), "key").isFalse)
        for (other in listOf(1, 0, "true", null, JavaOnlyMap())) {
            assertFalse("$other", BoolParser.parse(map(other), "key").hasValue())
        }
        assertFalse(BoolParser.parse(JavaOnlyMap(), "key").hasValue())
        assertFalse(BoolParser.parse(null, "key").hasValue())
    }

    // Numbers: finite numbers only.

    @Test
    fun numbers() {
        assertEquals(12.5, FractionParser.parse(map(12.5), "key").get(), 0.0)
        assertEquals(-3.0, FractionParser.parse(map(-3), "key").get(), 0.0)
        for (other in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, "12", true, null)) {
            assertFalse("$other", FractionParser.parse(map(other), "key").hasValue())
        }
    }

    @Test
    fun durations() {
        assertEquals(300.0, FractionParser.parseDuration(map(300), "key").get(), 0.0)
        assertEquals(0.0, FractionParser.parseDuration(map(0), "key").get(), 0.0)
        assertFalse(FractionParser.parseDuration(map(-1), "key").hasValue())
        assertFalse(FractionParser.parseDuration(map(Double.NaN), "key").hasValue())
    }

    // Texts: null is an explicit none.

    @Test
    fun texts() {
        assertEquals("Home", TextParser.parse(map("Home"), "key").get())
        assertEquals("", TextParser.parse(map(""), "key").get())
        assertFalse(TextParser.parse(map(12), "key").hasValue())
        assertFalse(TextParser.parse(map(true), "key").hasValue())
        assertFalse(TextParser.parse(JavaOnlyMap(), "key").hasValue())
    }

    @Test
    fun nullOfATextIsNone() {
        val text = TextParser.parse(map(null), "key")
        assertTrue(text.hasValue())
        assertTrue(text.isNone)
        assertEquals("", text.get())
        assertTrue(text is NoneText)
    }

    // Enums: the allowed values only; unknown ones and null are unset.

    @Test
    fun enums() {
        val allowed = setOf("always", "never")
        assertEquals("never", TextParser.parse(map("never"), "key", allowed).get())
        assertFalse(TextParser.parse(map("sometimes"), "key", allowed).hasValue())
        assertFalse(TextParser.parse(map(null), "key", allowed).hasValue())
        assertFalse(TextParser.parse(map(1), "key", allowed).hasValue())
    }

    // Arrays of texts: strings only; [] is a value.

    @Test
    fun textLists() {
        val array = JavaOnlyArray.of("top", 3, "sideways", "bottom", null)
        assertEquals(listOf("top", "sideways", "bottom"), StringListParser.parse(map(array), "key"))
        assertEquals(listOf("top", "bottom"), StringListParser.parse(map(array), "key", setOf("top", "bottom")))
        assertEquals(emptyList<String>(), StringListParser.parse(map(JavaOnlyArray()), "key"))
        assertNull(StringListParser.parse(map("top"), "key"))
        assertNull(StringListParser.parse(map(null), "key"))
    }

    // Other unions: the forms the type allows.

    @Test
    fun variants() {
        val edge = VariantParser.parse(map("edge"), "key", booleans = true, texts = setOf("edge"))
        assertEquals("edge", edge.asText())
        assertEquals(false, VariantParser.parse(map(false), "key", booleans = true).asBoolean())
        assertFalse(VariantParser.parse(map("side"), "key", booleans = true, texts = setOf("edge")).hasValue())
        assertFalse(VariantParser.parse(map(3), "key", booleans = true).hasValue())
        assertEquals(3.0, VariantParser.parse(map(3), "key", numbers = true).asNumber())
        assertFalse(VariantParser.parse(map(Double.NaN), "key", numbers = true).hasValue())
        assertEquals("new", VariantParser.parse(map("new"), "key", texts = null).asText())
        assertFalse(VariantParser.parse(map(null), "key", booleans = true).hasValue())
        val none = VariantParser.parse(map(null), "key", texts = null, noneOnNull = true)
        assertTrue(none.hasValue())
        assertTrue(none.isNone)
        assertNull(none.asText())
    }

    // Images: a string, a source's uri, the Android side of a platform icon.

    @Test
    fun icons() {
        assertEquals("ic_home", IconParser.parse(map("ic_home"), "key").get())
        val source = JavaOnlyMap.of("uri", "http://10.0.2.2:8081/assets/home.png", "width", 24, "scale", 2)
        assertEquals("http://10.0.2.2:8081/assets/home.png", IconParser.parse(map(source), "key").get())
        val platformIcon = JavaOnlyMap.of("ios", "square", "android", "ic_share")
        assertEquals("ic_share", IconParser.parse(map(platformIcon), "key").get())
        val androidSource = JavaOnlyMap.of("android", JavaOnlyMap.of("uri", "file:///share.png"))
        assertEquals("file:///share.png", IconParser.parse(map(androidSource), "key").get())
        assertFalse(IconParser.parse(map(JavaOnlyMap.of("ios", "square")), "key").hasValue())
        assertFalse(IconParser.parse(map(JavaOnlyMap.of("system", "star")), "key").hasValue())
        assertFalse(IconParser.parse(map(JavaOnlyArray.of(source)), "key").hasValue())
        assertFalse(IconParser.parse(map(null), "key").hasValue())
        assertFalse(IconParser.parse(map(12), "key").hasValue())
    }

    // Fonts: three fields of the text's own object; the size is the text's.

    @Test
    fun fonts() {
        val font = FontParser.parse(JavaOnlyMap.of("fontFamily", "Inter", "fontStyle", "italic", "fontWeight", "600"))
        assertEquals("Inter", font.fontFamily.get())
        assertEquals("italic", font.fontStyle.get())
        assertEquals("600", font.fontWeight.get())
        val unknown = FontParser.parse(JavaOnlyMap.of("fontStyle", "oblique", "fontWeight", "extra"))
        assertFalse(unknown.hasValue())
        assertFalse(FontParser.parse(null).hasValue())
    }

    // Insets: each side on its own.

    @Test
    fun insetsSideBySide() {
        val insets = IconInsetsOptions.parse(JavaOnlyMap.of("top", 4, "left", "2", "right", Double.NaN))
        assertEquals(4.0, insets.top.get(), 0.0)
        assertFalse(insets.left.hasValue())
        assertFalse(insets.bottom.hasValue())
        assertFalse(insets.right.hasValue())
        val below = IconInsetsOptions.parse(JavaOnlyMap.of("top", 1, "left", 1, "bottom", 1, "right", 1))
        insets.mergeWithDefault(below)
        val sides = listOf(insets.top, insets.left, insets.bottom, insets.right)
        assertEquals(listOf(4.0, 1.0, 1.0, 1.0), sides.map { it.get() })
    }

    // Groups: not an object, null included, is an empty group.

    @Test
    fun groupsThatAreNotObjectsAreEmpty() {
        for (value in listOf(null, true, 3, "topBar", JavaOnlyArray())) {
            val map = JavaOnlyMap.of("topBar", value, "statusBar", JavaOnlyMap.of("visible", true))
            val read = OptionsReader.read(map, null)
            assertNull(read.unreadablePath)
            assertEquals(emptyList<String>(), OptionsTree.differences(TopBarOptions(), read.options.topBar))
            assertTrue(read.options.statusBar.visible.isTrue)
        }
    }

    // Arrays of groups: [] replaces; not an array is unset.

    @Test
    fun groupLists() {
        val empty = OptionsReader.read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("rightButtons", JavaOnlyArray())), null)
        assertEquals(emptyList<ButtonOptions>(), empty.options.topBar.buttons.right)
        val wrong = OptionsReader.read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("rightButtons", "Settings")), null)
        assertNull(wrong.options.topBar.buttons.right)
        val items = JavaOnlyArray.of(JavaOnlyMap.of("id", "Settings"), "Details")
        val mixed = OptionsReader.read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("rightButtons", items)), null)
        assertEquals(listOf("Settings", null), mixed.options.topBar.buttons.right?.map { it.id.get(null) })
    }

    // Unknown keys are not read.

    @Test
    fun unknownKeysAreIgnored() {
        val map = JavaOnlyMap.of("topBarr", JavaOnlyMap.of("visible", true), "statusBar", JavaOnlyMap.of("colour", 1))
        val read = OptionsReader.read(map, null)
        assertNull(read.unreadablePath)
        assertFalse(read.options.topBar.visible.hasValue())
    }

    // A value React Native can't give: the group around it is empty, the rest reads, the path is kept.

    @Test
    fun unreadableValueEmptiesItsGroupOnly() {
        val map = JavaOnlyMap.of(
            "topBar", JavaOnlyMap.of(
                "visible", true,
                "title", JavaOnlyMap.of("text", Any(), "fontSize", 17),
                "subtitle", JavaOnlyMap.of("text", "Profile"),
            ),
            "statusBar", JavaOnlyMap.of("visible", false),
        )
        val read = OptionsReader.read(map, null)
        assertEquals("topBar.title.text", read.unreadablePath)
        assertFalse(read.options.topBar.title.fontSize.hasValue())
        assertTrue(read.options.topBar.visible.isTrue)
        assertEquals("Profile", read.options.topBar.subtitle.text.get())
        assertTrue(read.options.statusBar.visible.isFalse)
    }

    @Test
    fun unreadableItemOfAList() {
        val buttons = JavaOnlyArray.of(JavaOnlyMap.of("id", "Settings"), JavaOnlyMap.of("id", "Details", "icon", Any()))
        val read = OptionsReader.read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("rightButtons", buttons)), null)
        assertEquals("topBar.rightButtons[1].icon", read.unreadablePath)
        assertEquals(listOf("Settings", null), read.options.topBar.buttons.right?.map { it.id.get(null) })
    }

    @Test
    fun theFirstUnreadableValueIsKept() {
        val map = JavaOnlyMap.of(
            "topBar", JavaOnlyMap.of("testID", Any()),
            "layout", JavaOnlyMap.of("adjustResize", Any()),
        )
        assertEquals("topBar.testID", OptionsReader.read(map, null).unreadablePath)
    }

    @Test
    fun unreadableValueInAModuleGroup() {
        val map = JavaOnlyMap.of("modal", JavaOnlyMap.of("sheet", JavaOnlyMap.of("grabber", Any())))
        val read = OptionsReader.read(map, null)
        assertEquals("modal.sheet.grabber", read.unreadablePath)
        assertFalse(read.options.modal.sheet.hasValue)
    }

    @Test
    fun anUnreadableGroupIsNamedOnce() {
        val top = OptionsReader.read(JavaOnlyMap.of("topBar", Any()), null)
        assertEquals("topBar", top.unreadablePath)
        val nested = OptionsReader.read(JavaOnlyMap.of("topBar", JavaOnlyMap.of("title", Any())), null)
        assertEquals("topBar.title", nested.unreadablePath)
    }

    // A sheet detent number is a height when it is finite and above zero; a split view width when finite.

    @Test
    fun sheetDetentHeights() {
        assertEquals(SheetDetent.Height(300.0), SheetDetent.parse(300))
        assertEquals(SheetDetent.Height(0.5), SheetDetent.parse(0.5))
        for (other in listOf(0, -1, -0.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull("$other", SheetDetent.parse(other))
        }
        assertEquals(SheetDetent.Medium, SheetDetent.parse("medium"))
        val sheet = SheetOptions.parse(JavaOnlyMap.of("detents", JavaOnlyArray.of("medium", -1, Double.NaN, 200)))
        assertEquals(listOf(SheetDetent.Medium, SheetDetent.Height(200.0)), sheet.detents)
    }

    @Test
    fun splitViewWidth() {
        fun width(value: Any?) = SplitViewOptions.parse(JavaOnlyMap.of("splitView", JavaOnlyMap.of("width", value))).width
        assertEquals(280.0, width(280), 0.0)
        for (other in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, "280")) {
            assertEquals("$other", SplitViewOptions.PANE_WIDTH, width(other), 0.0)
        }
    }

    // dp of the options in pixels.

    @Test
    fun dpToPixels() {
        assertEquals(42, UiUtils.dpToPixels(16.0, 420))
        assertEquals(43, UiUtils.dpToPixels(16.5, 420))
        // A fraction is kept to the end and rounded there, as iOS places 12.5 points: 32.8 pixels are 33, not 32.
        assertEquals(33, UiUtils.dpToPixels(12.5, 420))
        assertEquals(21, UiUtils.dpToPixels(7.9, 420))
        assertEquals(0, UiUtils.dpToPixels(0.0, 420))
        assertEquals(-3, UiUtils.dpToPixels(-3.0, 420))
    }
}

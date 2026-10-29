package dev.apexnative.navigation.options

import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The JSON key paths of `clearOptions` over the options tree ([OptionPaths]). */
class OptionPathsTest {
    private fun full(): Options = OptionsReader.read(OptionsFixture.full(), null).options

    // The fixture without the key at [path]: what the options read when JavaScript never sent it.
    private fun fullWithout(path: List<String>): Options {
        val json = OptionsFixture.full()
        val parent = path.dropLast(1).fold(json) { map, key -> map.getMap(key) as JavaOnlyMap }
        parent.remove(path.last())
        return OptionsReader.read(json, null).options
    }

    private fun cleared(vararg paths: String): Options =
        full().also { options -> paths.forEach { assertTrue(it, OptionPaths.clear(options, it)) } }

    @Test
    fun aPathToAValueClearsOnlyThatValue() {
        val options = cleared("topBar.subtitle.text")
        assertFalse(options.topBar.subtitle.text.hasValue())
        assertTrue(options.topBar.subtitle.color.hasValue())
        assertTrue(options.topBar.title.text.hasValue())
        assertEquals(listOf("topBar.subtitle.text"), OptionsTree.differences(full(), options))
    }

    @Test
    fun aPathToAGroupClearsTheWholeGroup() {
        val options = cleared("topBar.subtitle", "statusBar")
        assertFalse(options.topBar.subtitle.hasValue())
        assertFalse(options.statusBar.hasValue())
        assertTrue(options.topBar.title.hasValue())
        assertTrue(options.navigationBar.hasValue())
    }

    @Test
    fun keysTheFieldsAreNotNamedAfterReachThem() {
        val options = cleared("topBar.rightButtons", "topBar.backButton", "topBar.title.fontFamily")
        assertNull(options.topBar.buttons.right)
        assertNotNull(options.topBar.buttons.left)
        assertFalse(options.topBar.buttons.back.hasValue())
        assertFalse(options.topBar.title.font.fontFamily.hasValue())
        assertTrue(options.topBar.title.font.fontWeight.hasValue())
    }

    @Test
    fun anUnknownEmptyOrInnerPathChangesNothing() {
        val paths = listOf(
            "", ".", "topBar.", ".topBar", "topBar..title", "nothing", "topBar.nothing", "topBar.buttons",
            "topBar.title.color.dark", "topBar.rightButtons.0", "topBar.title.text.value",
        )
        val options = full()
        for (path in paths) {
            assertNull(path, OptionPaths.find(options, path))
            assertFalse(path, OptionPaths.clear(options, path))
        }
        assertEquals(emptyList<String>(), OptionsTree.differences(full(), options))
    }

    @Test
    fun clearingAKeyIsReadingWithoutIt() {
        // Every key path of the whole fixture, groups and values: clearing it from the options read gives what the
        // JSON without that key reads. A key Android doesn't read clears nothing, and reading without it is the same.
        val mismatches = mutableListOf<String>()
        fun walk(map: ReadableMap, path: List<String>) {
            val keys = map.keySetIterator()
            while (keys.hasNextKey()) {
                val key = keys.nextKey()
                val keyPath = path + key
                val dotted = keyPath.joinToString(".")
                val options = full()
                OptionPaths.clear(options, dotted)
                val differences = OptionsTree.differences(fullWithout(keyPath), options)
                if (differences.isNotEmpty()) mismatches += "$dotted: $differences"
                val value = OptionPaths.find(full(), dotted)?.value
                if (map.getType(key) == ReadableType.Map && OptionsTree.isGroup(value)) {
                    walk(requireNotNull(map.getMap(key)), keyPath)
                }
            }
        }
        walk(OptionsFixture.full(), emptyList())
        assertEquals(emptyList<String>(), mismatches)
    }
}

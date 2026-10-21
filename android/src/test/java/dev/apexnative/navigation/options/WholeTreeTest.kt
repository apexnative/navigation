package dev.apexnative.navigation.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every field of every group takes part in reading, copying, merging and defaults: the whole tree, by reflection, over
 * [OptionsFixture.full].
 */
class WholeTreeTest {
    private fun full(): Options {
        val read = OptionsReader.read(OptionsFixture.full(), null)
        assertNull(read.unreadablePath)
        return read.options
    }

    @Test
    fun theFixtureSetsEveryField() {
        assertEquals(emptyList<String>(), OptionsTree.unset(full()))
    }

    @Test
    fun everyGroupHasAValueOnlyWhenSet() {
        fun hasValue(group: Any): Boolean? =
            group.javaClass.methods.firstOrNull { it.name == "hasValue" && it.parameterCount == 0 }
                ?.invoke(group) as? Boolean
        val withoutValue = mutableListOf<String>()
        OptionsTree.containers(full()) { path, group -> if (hasValue(group) == false) withoutValue += path }
        assertEquals(emptyList<String>(), withoutValue)
        val withValue = mutableListOf<String>()
        OptionsTree.containers(Options()) { path, group -> if (hasValue(group) == true) withValue += path }
        assertEquals(emptyList<String>(), withValue)
    }

    @Test
    fun aCopyIsEqualAndSharesNoGroup() {
        val full = full()
        val copy = full.copy()
        assertEquals(emptyList<String>(), OptionsTree.differences(full, copy))
        // A button menu is shared on purpose: it is a value of the walk, never a group.
        assertEquals(emptyList<String>(), OptionsTree.shared(copy, full))
        assertTrue(copy.topBar.overflowMenu === full.topBar.overflowMenu)
    }

    @Test
    fun mergingIntoEmptySetsEveryField() {
        val full = full()
        val merged = Options().mergeWith(full)
        assertEquals(emptyList<String>(), OptionsTree.unset(merged))
        assertEquals(emptyList<String>(), OptionsTree.differences(full, merged))
        assertEquals(emptyList<String>(), OptionsTree.shared(merged, full))
    }

    @Test
    fun defaultsIntoEmptySetEveryField() {
        val full = full()
        val resolved = Options().withDefaultOptions(full)
        assertEquals(emptyList<String>(), OptionsTree.unset(resolved))
        assertEquals(emptyList<String>(), OptionsTree.differences(full, resolved))
        assertEquals(emptyList<String>(), OptionsTree.shared(resolved, full))
    }

    @Test
    fun mergingEmptyChangesNothing() {
        val full = full()
        val before = full.copy()
        full.mergeWith(Options())
        assertEquals(emptyList<String>(), OptionsTree.differences(before, full))
    }

    @Test
    fun emptyDefaultsChangeNothing() {
        val full = full()
        val before = full.copy()
        full.withDefaultOptions(Options())
        assertEquals(emptyList<String>(), OptionsTree.differences(before, full))
    }

    @Test
    fun defaultsNeverChange() {
        val defaults = full()
        val before = defaults.copy()
        val own = OptionsReader.read(OptionsFixture.full(), null).options
        Options().withDefaultOptions(defaults)
        own.withDefaultOptions(defaults)
        val resolved = Options().withDefaultOptions(defaults)
        resolved.mergeWith(own)
        assertEquals(emptyList<String>(), OptionsTree.differences(before, defaults))
        assertEquals(emptyList<String>(), OptionsTree.shared(resolved, defaults))
    }
}

package dev.apexnative.navigation.views.element

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** An element's step that fails is skipped with one line in the log while the others run. */
class ElementStepTest {
    @Test
    fun aFailingStepIsLoggedOnceAndTheOthersRun() {
        val log = mutableListOf<String>()
        val ran = mutableListOf<String>()
        val steps = listOf("'avatar'", "'a' -> 'b'", "'bio'")
        val results = steps.map { name ->
            elementStep(name, log::add) {
                if (name == "'a' -> 'b'") throw IllegalStateException("no place")
                ran += name
            }
        }
        assertEquals(listOf(true, false, true), results)
        assertEquals(listOf("'avatar'", "'bio'"), ran)
        assertEquals(1, log.size)
        assertTrue(log[0].startsWith("[ApexNavigation] the transition's element 'a' -> 'b' is skipped"))
        assertTrue(log[0].contains("no place"))
    }
}

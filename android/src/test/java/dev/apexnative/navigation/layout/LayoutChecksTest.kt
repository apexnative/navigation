package dev.apexnative.navigation.layout

import com.facebook.react.bridge.ReadableMap
import dev.apexnative.navigation.layout.Layouts.center
import dev.apexnative.navigation.layout.Layouts.left
import dev.apexnative.navigation.layout.Layouts.menu
import dev.apexnative.navigation.layout.Layouts.pair
import dev.apexnative.navigation.layout.Layouts.screen
import dev.apexnative.navigation.layout.Layouts.stack
import dev.apexnative.navigation.layout.Layouts.tabs
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutChecksTest {
    private fun read(map: ReadableMap) = LayoutReader.node(map) { false }

    @Test
    fun noneShown() {
        val tree = read(stack("Stack1", screen("Home")))
        assertNull(LayoutChecks.firstShownId(listOf(tree), { false }, { false }))
        assertNull(LayoutChecks.firstShownId(tree, ChildControllersRegistry()))
    }

    @Test
    fun firstShownInTreeOrder() {
        val tree = read(tabs("Tabs", stack("Stack1", screen("Home")), screen("Profile")))
        val shown = setOf("Profile", "Home")
        assertEquals("Home", LayoutChecks.firstShownId(listOf(tree), shown::contains) { false })
    }

    @Test
    fun containersCount() {
        val tree = read(stack("Stack1", screen("Home")))
        assertEquals("Stack1", LayoutChecks.firstShownId(listOf(tree), setOf("Stack1")::contains) { false })
    }

    @Test
    fun sidesAreLeftOut() {
        val tree = read(menu("Menu", left("Left", screen("Settings")), center("Center", screen("Home"))))
        val shown = setOf("Left", "Center", "Home")
        assertEquals("Home", LayoutChecks.firstShownId(listOf(tree), shown::contains) { false })
    }

    @Test
    fun allowedIdsAreExcused() {
        val trees = listOf(read(screen("Home")), read(screen("Details")))
        val shown = setOf("Home", "Details")
        assertEquals("Details", LayoutChecks.firstShownId(trees, shown::contains) { it.id == "Home" })
        assertNull(LayoutChecks.firstShownId(trees, shown::contains) { true })
    }

    @Test
    fun stackChild() {
        assertNull(LayoutChecks.checkStackChild(read(screen("Home")), "Stack1"))
        assertNull(LayoutChecks.checkStackChild(read(pair("Arrangement1", screen("A"), screen("B"))), "Stack1"))
        assertEquals(
            "the stack 'Stack1' takes screens, not a 'Stack'",
            LayoutChecks.checkStackChild(read(stack("Stack2", screen("Home"))), "Stack1"),
        )
        assertEquals(
            "the stack 'Stack1' takes screens, not a 'BottomTabs'",
            LayoutChecks.checkStackChild(read(tabs("Tabs", screen("Home"))), "Stack1"),
        )
    }
}

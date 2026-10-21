package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Text
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The merges a hidden stack keeps until its top shows (`mergeOptions`): a merge into a screen goes only to that screen,
 * if it is the top when the stack shows; a merge with the stack's own id goes to whatever top shows then. The stack's
 * view isn't made in the tests, so it is hidden; what reaches the top is seen through the room it counts again
 * (`topBar.searchBar.hideOnScroll` changes the room: `topInset:<id>`).
 */
class StackKeptMergeTest {
    private val h = StackQueueHarness()

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    // The search's text and focus, with a part that counts the room again.
    private fun searchMerge() = Options().apply {
        topBar.searchBar.text = Text("x")
        topBar.searchBar.focus = Bool(true)
        topBar.searchBar.hideOnScroll = Bool(true)
    }

    @Test
    fun aMergeIntoTheTopOfAHiddenStackDoesNotReachANewTop() {
        val a = h.screen("A")
        h.start(a)
        h.display.isOnScreen = false
        h.stack.mergeChildOptions(searchMerge(), a)
        val b = h.screen("B")
        h.push(b)
        h.calls.clear()
        // The stack shows again with B on top: B gets nothing of A's merge.
        h.stack.applyChildOptions(Options(), b)
        assertFalse(h.calls.any { it.startsWith("topInset") })
    }

    @Test
    fun aMergeIntoTheTopOfAHiddenStackAppliesAsItShows() {
        val a = h.screen("A")
        h.start(a)
        h.stack.mergeChildOptions(searchMerge(), a)
        h.stack.applyChildOptions(Options(), a)
        assertTrue("topInset:A" in h.calls)
        h.calls.clear()
        // Once only.
        h.stack.applyChildOptions(Options(), a)
        assertFalse("topInset:A" in h.calls)
    }

    @Test
    fun aMergeKeptForAScreenWaitsUntilItIsTheTopAgain() {
        val a = h.screen("A")
        h.start(a)
        h.display.isOnScreen = false
        h.stack.mergeChildOptions(searchMerge(), a)
        val b = h.screen("B")
        h.push(b)
        h.pop(b)
        h.calls.clear()
        h.stack.applyChildOptions(Options(), a)
        assertTrue("topInset:A" in h.calls)
    }

    @Test
    fun aMergeIntoTheHiddenStackItselfReachesWhateverTopShows() {
        val a = h.screen("A")
        h.start(a)
        h.display.isOnScreen = false
        h.stack.mergeOptions(Options().apply { topBar.searchBar.hideOnScroll = Bool(true) })
        val b = h.screen("B")
        h.push(b)
        h.calls.clear()
        h.stack.applyChildOptions(Options(), b)
        assertTrue("topInset:B" in h.calls)
    }

    @Test
    fun aKeptMergeOfAScreenThatShowsNoLongerGoesWhenTheStackShowsAnother() {
        val a = h.screen("A")
        h.start(a)
        h.display.isOnScreen = false
        h.stack.mergeChildOptions(searchMerge(), a)
        val b = h.screen("B")
        h.push(b)
        h.stack.applyChildOptions(Options(), b)
        h.pop(b)
        h.calls.clear()
        // The stack showed with B on top: what was kept went then, A shows with its own options.
        h.stack.applyChildOptions(Options(), a)
        assertFalse("topInset:A" in h.calls)
    }
}

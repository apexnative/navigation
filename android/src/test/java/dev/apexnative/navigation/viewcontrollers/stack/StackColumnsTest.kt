package dev.apexnative.navigation.viewcontrollers.stack

import android.app.Activity
import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.react.CommandListener
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.parent.ShowsColumns
import dev.apexnative.navigation.viewcontrollers.stack.StackQueueHarness.Companion.STACK
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The split view's commands on Android (`showInColumn`, `setColumnShown`): the checks before any node, the screen the
 * split view takes and its answer, the push over the screen the command comes from when the split view doesn't take it,
 * the made screen destroyed on every rejection; a split view of the unit tests ([Columns]) around the stack, which
 * takes what the test says.
 */
class StackColumnsTest {
    private val h = StackQueueHarness()
    private val columns = Columns(h.activity, h.registry)

    @Before
    fun install() {
        h.install()
        columns.add(h.stack)
    }

    @After
    fun uninstall() = h.uninstall()

    private fun rejection(): String? = h.events().firstOrNull { " reject " in it }?.substringAfter(" reject ")

    private fun showInColumn(from: String, id: String, type: LayoutType = LayoutType.Component, key: String? = null) =
        h.commands.showInColumn(from, h.layout(id, key = key, type = type), h.answer("showInColumn"), "show '$id'")

    // showInColumn.

    @Test
    fun theSplitViewTakesTheMadeScreenAndAnswersOnceItShows() {
        h.start(h.screen("A"))
        showInColumn("A", "B")
        val made = h.registry.find("B")
        assertSame(made, columns.shown)
        assertSame(h.registry.find("A"), columns.from)
        // Nothing until the pane stopped.
        assertTrue(h.events().isEmpty())
        columns.listener?.onSuccess("B")
        h.assertEvents("state", "showInColumn1 resolve B", "completed:showInColumn")
    }

    @Test
    fun aRejectionOfTheSplitViewDestroysTheMadeScreen() {
        h.start(h.screen("A"))
        showInColumn("A", "B")
        val made = columns.shown ?: error("B")
        val text = ErrorTexts.columnNotStack("A", "secondary")
        columns.listener?.onError(text)
        assertEquals(text, rejection())
        assertTrue(made.isDestroyed)
        assertNull(h.registry.find("B"))
    }

    @Test
    fun withTheCompactLayoutTheMadeScreenIsPushedOverTheSource() {
        h.start(h.screen("A"))
        columns.takes = false
        showInColumn("A", "B")
        val made = h.registry.find("B")
        assertSame(made, columns.shown)
        assertSame(h.stack, made?.parentController)
        assertEquals(listOf("A", "B"), h.entries())
        h.display.finishAll()
        assertTrue(h.events().containsAll(listOf("showInColumn1 resolve B", "completed:showInColumn")))
    }

    @Test
    fun aKeyOfAnEntryGoesBackToItAndTheMadeScreenGoes() {
        h.start(h.screen("A"), h.screen("C"), keys = listOf("Details:k", null))
        columns.takes = false
        showInColumn("C", "B", key = "Details:k")
        val made = columns.shown ?: error("B")
        assertTrue(made.isDestroyed)
        h.display.finishAll()
        assertTrue("showInColumn1 resolve A" in h.events())
        assertEquals(listOf("A"), h.entries())
    }

    @Test
    fun neitherInASplitViewNorInAStackRejects() {
        h.screen("Lone")
        showInColumn("Lone", "B")
        assertEquals(ErrorTexts.neitherSplitViewNorStack("Lone"), rejection())
        assertNull(h.registry.find("B"))
    }

    @Test
    fun aSplitViewThatDoesntTakeItWithoutAStackRejectsAndTheMadeScreenGoes() {
        columns.add(h.screen("Lone"))
        columns.takes = false
        showInColumn("Lone", "B")
        assertEquals(ErrorTexts.neitherSplitViewNorStack("Lone"), rejection())
        // The screen was made, then destroyed.
        val made = columns.shown ?: error("B was not made")
        assertTrue(made.isDestroyed)
        assertNull(h.registry.find("B"))
    }

    @Test
    fun anExceptionAfterTheScreenIsMadeDestroysIt() {
        h.start(h.screen("A"))
        columns.fails = true
        val thrown = runCatching { showInColumn("A", "B") }.exceptionOrNull()
        assertEquals("the split view failed", thrown?.message)
        val made = columns.shown ?: error("B was not made")
        assertTrue(made.isDestroyed)
        assertNull(h.registry.find("B"))
    }

    @Test
    fun aScreenPreparedWithTheIdGoesAndTheMadeScreenTakesItsId() {
        h.start(h.screen("A"))
        val preparedB = h.screen("B")
        h.prepared.add(preparedB, h.stack)
        showInColumn("A", "B")
        assertTrue(preparedB.isDestroyed)
        val made = columns.shown ?: error("B was not made")
        assertTrue(made !== preparedB)
        assertSame(made, h.registry.find("B"))
    }

    @Test
    fun anUnknownScreenRejectsBeforeAnyNode() {
        h.start(h.screen("A"))
        showInColumn("Gone", "B")
        assertEquals(ErrorTexts.screenNotShown("Gone"), rejection())
        assertNull(h.registry.find("B"))
        assertNull(columns.shown)
    }

    @Test
    fun anIdThatShowsAndAContainerRejectBeforeAnyNode() {
        h.start(h.screen("A"))
        showInColumn("A", "A")
        assertEquals(ErrorTexts.alreadyShown("A"), rejection())
        h.clear()
        showInColumn("A", "Stack2", type = LayoutType.Stack)
        assertEquals(ErrorTexts.stackTakesScreens(STACK, "Stack"), rejection())
        assertNull(h.registry.find("Stack2"))
        assertNull(columns.shown)
    }

    @Test
    fun twoCallsInARowEachAnswerTheirOwnScreen() {
        h.start(h.screen("A"))
        showInColumn("A", "B")
        val first = columns.listener
        showInColumn("A", "C")
        first?.onSuccess("B")
        columns.listener?.onSuccess("C")
        assertTrue(h.events().containsAll(listOf("showInColumn1 resolve B", "showInColumn2 resolve C")))
    }

    // setColumnShown.

    @Test
    fun setColumnShownAnswersNothingOnceThePanesStopped() {
        h.start(h.screen("A"))
        h.commands.setColumnShown("A", "primary", false, h.answer("setColumnShown"))
        assertEquals(listOf("primary:false"), columns.columnCalls)
        assertTrue(h.events().isEmpty())
        columns.done?.run()
        // The state before the promise; no ANN.CommandCompleted (`setColumnShown`).
        h.assertEvents("state", "setColumnShown1 resolve null")
    }

    @Test
    fun setColumnShownOutOfASplitViewOrForAnUnknownScreenRejects() {
        h.screen("Lone")
        h.commands.setColumnShown("Lone", "primary", true, h.answer("setColumnShown"))
        assertEquals(ErrorTexts.notInSplitView("Lone"), rejection())
        h.clear()
        h.commands.setColumnShown("Gone", "primary", true, h.answer("setColumnShown"))
        assertEquals(ErrorTexts.screenNotShown("Gone"), rejection())
    }

    // A layout around the stack that shows screens in its columns, as the split view does: it takes a screen while
    // [takes], and answers when the test says.
    private class Columns(activity: Activity, registry: ChildControllersRegistry) :
        TestLayout(activity, registry, "Split1"), ShowsColumns {
        var takes = true
        var fails = false
        var shown: ViewController<*>? = null
        var from: ViewController<*>? = null
        var listener: CommandListener? = null
        val columnCalls = mutableListOf<String>()
        var done: Runnable? = null

        override fun showScreen(
            screen: ViewController<*>,
            from: ViewController<*>,
            listener: CommandListener,
        ): Boolean {
            shown = screen
            this.from = from
            if (fails) error("the split view failed")
            if (!takes) return false
            this.listener = listener
            return true
        }

        override fun setColumnShown(column: String, shown: Boolean, done: Runnable) {
            columnCalls += "$column:$shown"
            this.done = done
        }
    }
}

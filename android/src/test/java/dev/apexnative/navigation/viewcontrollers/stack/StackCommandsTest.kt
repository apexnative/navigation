package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.ErrorTexts
import dev.apexnative.navigation.viewcontrollers.child.ChildControllersRegistry
import dev.apexnative.navigation.viewcontrollers.navigator.LayoutFactory
import dev.apexnative.navigation.viewcontrollers.parent.ReturnsScreenFromColumn
import dev.apexnative.navigation.viewcontrollers.stack.StackQueueHarness.Companion.STACK
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The checks and the values of the stack's commands (`push` … `setStackRoot`): the texts and their order, all before
 * any node is made; the screen each command answers with; keys; the column return; the checks read the stack as
 * JavaScript sees it.
 */
class StackCommandsTest {
    private val h = StackQueueHarness()

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    private fun rejection(): String? = h.events().firstOrNull { " reject " in it }?.substringAfter(" reject ")

    private fun push(from: String, id: String, type: LayoutType = LayoutType.Component, key: String? = null) =
        h.commands.push(from, h.layout(id, key = key, type = type), h.answer("push"), "push the screen '$id'")

    // push.

    @Test
    fun pushRejectsAnUnknownScreen() {
        h.start(h.screen("A"))
        push("Gone", "B")
        assertEquals(ErrorTexts.screenNotShown("Gone"), rejection())
        assertNull(h.registry.find("B"))
    }

    @Test
    fun pushRejectsAScreenOutOfAStack() {
        h.start(h.screen("A"))
        h.screen("Lone")
        push("Lone", "B")
        assertEquals(ErrorTexts.notInStack("Lone"), rejection())
    }

    @Test
    fun pushRejectsAnIdThatShows() {
        h.start(h.screen("A"))
        // "shows already" comes before the check of the stack's children.
        push("A", "A", type = LayoutType.Stack)
        assertEquals(ErrorTexts.alreadyShown("A"), rejection())
    }

    @Test
    fun pushRejectsAContainer() {
        h.start(h.screen("A"))
        push("A", "Tabs1", type = LayoutType.BottomTabs)
        assertEquals(ErrorTexts.stackTakesScreens(STACK, "BottomTabs"), rejection())
        assertNull(h.registry.find("Tabs1"))
    }

    // A pair has its maker; the pair itself reads the window's metrics, which only a device has.
    @Test
    fun aPairHasAMaker() {
        val pair = h.layout("Pair1", type = LayoutType.Arrangement)
            .copy(children = listOf(h.layout("Home1"), h.layout("Details1")))
        assertTrue(LayoutFactory(h.activity, h.registry).canMake(pair))
    }

    @Test
    fun pushFromTheStacksIdOrAScreenGoesOnTop() {
        h.start(h.screen("A"))
        push(STACK, "B")
        h.display.finishAll()
        push("A", "C")
        h.display.finishAll()
        assertEquals(listOf("A", "B", "C"), h.entries())
        assertTrue(h.events().containsAll(listOf("push1 resolve B", "push2 resolve C")))
    }

    @Test
    fun pushFromAScreenPushedWithoutAwait() {
        h.start(h.screen("A"))
        push("A", "B")
        // B shows only at the end of its transition, but it is a screen of its stack already.
        push("B", "C")
        assertEquals(listOf("A", "B", "C"), h.entries())
        h.display.finishAll()
        assertTrue(h.events().containsAll(listOf("push1 resolve B", "push2 resolve C")))
    }

    @Test
    fun aKeyOfAnotherStackMakesANewScreen() {
        h.start(h.screen("A"), keys = listOf("Details:k"))
        val other = StackController(h.activity, h.registry, "Stack2", StackPresenter(h.activity), Options())
        val display = FakeStackDisplay(mutableListOf(), h.scheduler)
        val host = QueueHost(h.journal).also { it.answers = other::answerIdOf }
        other.useQueueParts(display, QueueReadiness(h.journal), host)
        other.setInitialChildren(listOf(h.screen("X")), emptyList())
        push("X", "B", key = "Details:k")
        assertNotNull(h.registry.find("B"))
        assertSame(other, h.registry.find("B")?.parentController)
        display.finishAll()
    }

    @Test
    fun aPushedScreenGetsTheBackArrow() {
        h.start(h.screen("A"))
        push("A", "B")
        val b = h.registry.find("B") ?: error("B")
        assertTrue(b.options.topBar.buttons.back.visible.isTrue)
        assertSame(h.stack, b.parentController)
        h.display.finishAll()
    }

    // pop.

    @Test
    fun popFromALowerScreenTakesItAndWhatIsOverIt() {
        h.start(h.screen("A"), h.screen("B"), h.screen("C"))
        h.commands.pop("B", h.answer("pop"), "pop the screen 'B'")
        h.display.finishAll()
        assertEquals(listOf("A"), h.entries())
        assertTrue("pop1 resolve B" in h.events())
    }

    @Test
    fun popFromTheStacksIdAnswersTheTop() {
        h.start(h.screen("A"), h.screen("B"))
        h.commands.pop(STACK, h.answer("pop"), "pop the screen '$STACK'")
        h.display.finishAll()
        assertTrue("pop1 resolve B" in h.events())
    }

    @Test
    fun popOfTheFirstScreenRejects() {
        h.start(h.screen("A"), h.screen("B"))
        h.commands.pop("A", h.answer("pop"), "pop the screen 'A'")
        assertEquals(ErrorTexts.firstScreenOfStack("A"), rejection())
        assertEquals(listOf("A", "B"), h.entries())
    }

    @Test
    fun popOfTheOnlyScreenRejectsOutOfAColumn() {
        h.start(h.screen("A"))
        h.commands.pop("A", h.answer("pop"), "pop the screen 'A'")
        assertEquals(ErrorTexts.firstScreenOfStack("A"), rejection())
    }

    @Test
    fun popOfTheOnlyScreenOfAColumnGoesBackToTheColumnBefore() {
        val columns = Columns(h.activity, h.registry)
        h.stack.parentController = columns
        h.start(h.screen("A"))
        h.commands.pop("A", h.answer("pop"), "pop the screen 'A'")
        // The module gets the stack node itself, and the answer comes once the column stopped.
        assertSame(h.stack, columns.asked)
        assertTrue(h.events().isEmpty())
        columns.done?.run()
        h.assertEvents("state", "pop1 resolve A", "completed:pop")
        assertEquals(listOf("A"), h.entries())
    }

    @Test
    fun popFromAScreenOutOfAStackRejects() {
        h.screen("Lone")
        h.commands.pop("Lone", h.answer("pop"), "pop the screen 'Lone'")
        assertEquals(ErrorTexts.notInStack("Lone"), rejection())
    }

    // popTo.

    @Test
    fun popToTheTopAnswersAtOnce() {
        h.start(h.screen("A"), h.screen("B"))
        h.commands.popTo("B", h.answer("popTo"), "pop to the screen 'B'")
        h.assertEvents("state", "popTo1 resolve B", "completed:popTo")
    }

    @Test
    fun popToADeeperScreenAnswersIt() {
        h.start(h.screen("A"), h.screen("B"), h.screen("C"))
        h.commands.popTo("A", h.answer("popTo"), "pop to the screen 'A'")
        h.display.finishAll()
        assertTrue("popTo1 resolve A" in h.events())
        assertEquals(listOf("A"), h.entries())
    }

    @Test
    fun popToAScreenNotInThisStackRejects() {
        h.start(h.screen("A"), h.screen("B"))
        h.commands.popTo(STACK, h.answer("popTo"), "pop to the screen '$STACK'")
        assertEquals(ErrorTexts.notInThisStack(STACK), rejection())
        h.clear()
        h.back()
        h.commands.popTo("B", h.answer("popTo"), "pop to the screen 'B'")
        // B is still a node of the stack, but a cut took it: gone for later commands.
        assertEquals(ErrorTexts.screenNotShown("B"), rejection())
        h.display.finishAll()
    }

    @Test
    fun popToChecksInTheSpecsOrder() {
        h.commands.popTo("Gone", h.answer("popTo"), "pop to the screen 'Gone'")
        assertEquals(ErrorTexts.screenNotShown("Gone"), rejection())
        h.clear()
        h.screen("Lone")
        h.commands.popTo("Lone", h.answer("popTo"), "pop to the screen 'Lone'")
        assertEquals(ErrorTexts.notInStack("Lone"), rejection())
    }

    // popToRoot.

    @Test
    fun popToRootAnswersTheFirstScreen() {
        h.start(h.screen("A"), h.screen("B"), h.screen("C"))
        h.commands.popToRoot("C", h.answer("popToRoot"), "pop the stack of the screen 'C'")
        h.display.finishAll()
        assertTrue("popToRoot1 resolve A" in h.events())
    }

    // setStackRoot.

    private fun setStackRoot(from: String, vararg ids: String) = h.commands.setStackRoot(
        from, ids.map { h.layout(it) }, h.answer("setStackRoot"), "set the stack of the screen '$from'",
    )

    @Test
    fun setStackRootWithoutScreensRejects() {
        h.start(h.screen("A"))
        setStackRoot("A")
        assertEquals(ErrorTexts.noScreensForStack("A"), rejection())
    }

    @Test
    fun setStackRootRejectsAnIdShownOutOfTheStack() {
        h.start(h.screen("A"))
        h.screen("Elsewhere")
        setStackRoot("A", "C", "Elsewhere")
        assertEquals(ErrorTexts.alreadyShown("Elsewhere"), rejection())
        assertNull(h.registry.find("C"))
    }

    @Test
    fun setStackRootTakesAnIdOfItsOwnStack() {
        val a = h.screen("A")
        h.start(a, h.screen("B"))
        setStackRoot("B", "A", "C")
        h.display.finishAll()
        assertTrue("setStackRoot1 resolve C" in h.events())
        // The old A goes without ScreenPopped; the new A has the id.
        assertTrue("popped:A" !in h.events())
        assertTrue(a.isDestroyed)
        assertEquals(listOf("A", "C"), h.entries())
    }

    @Test
    fun setStackRootWithAsPushPlaysThePush() {
        h.start(h.screen("A"))
        val layout = h.layout("C").copy(options = Options().apply { animations.setStackRoot.asPush = Bool(true) })
        h.commands.setStackRoot("A", listOf(layout), h.answer("setStackRoot"), "set the stack of the screen 'A'")
        assertTrue(h.display.starts.first().startsWith("PushIn:C"))
        h.display.finishAll()
    }

    @Test
    fun setStackRootWithoutAnimation() {
        h.start(h.screen("A"))
        val layout = h.layout("C").copy(options = Options().apply { animations.setStackRoot.enabled = Bool(false) })
        h.commands.setStackRoot("A", listOf(layout), h.answer("setStackRoot"), "set the stack of the screen 'A'")
        assertTrue(h.display.starts.isEmpty())
        assertTrue("setStackRoot1 resolve C" in h.events())
    }

    @Test
    fun setStackRootRejectsAContainer() {
        h.start(h.screen("A"))
        h.commands.setStackRoot(
            "A", listOf(h.layout("C"), h.layout("Menu", type = LayoutType.SideMenuRoot)), h.answer("setStackRoot"),
            "set",
        )
        assertEquals(ErrorTexts.stackTakesScreens(STACK, "SideMenuRoot"), rejection())
        assertNull(h.registry.find("C"))
    }

    // preparePush and cancelPreparedPush without a stack's view: nothing prepared.

    @Test
    fun preparePushPreparesNothingWithoutTheStacksView() {
        h.start(h.screen("A"))
        assertNull(h.commands.preparePush("A", h.layout("B", name = "slot:Details"), hasActivity = true))
        assertNull(h.registry.find("B"))
        // An unknown id cancels nothing.
        h.commands.cancelPreparedPush("Nothing")
    }

    // A layout around the stack that returns screens from its columns, as the split view does.
    private class Columns(activity: android.app.Activity, registry: ChildControllersRegistry) :
        TestLayout(activity, registry, "Split1"), ReturnsScreenFromColumn {
        var asked: ViewController<*>? = null
        var done: Runnable? = null

        override fun returnsScreenFromColumn(screen: ViewController<*>, done: Runnable): Boolean {
            asked = screen
            this.done = done
            return true
        }
    }
}

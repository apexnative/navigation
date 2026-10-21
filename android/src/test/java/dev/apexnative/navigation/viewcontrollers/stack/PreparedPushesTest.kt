package dev.apexnative.navigation.viewcontrollers.stack

import dev.apexnative.navigation.layout.LayoutType
import dev.apexnative.navigation.options.ElementTransitionOptions
import dev.apexnative.navigation.options.ElementTransitions
import dev.apexnative.navigation.options.Options
import dev.apexnative.navigation.options.SharedElementTransitionOptions
import dev.apexnative.navigation.options.SharedElements
import dev.apexnative.navigation.options.StackPushAnimationOptions
import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.options.params.Text
import dev.apexnative.navigation.viewcontrollers.stack.PreparedPushes.Refusal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The prepared push (`preparePush`, `cancelPreparedPush`): every case that prepares nothing, as a pure decision; the
 * prepared screens of an activity — taken by their push, replaced by a second with the same id, discarded for another
 * stack, cancelled, gone with their stack — and the content of a slot prepared only.
 */
class PreparedPushesTest {
    private val h = StackQueueHarness()

    @Before
    fun install() = h.install()

    @After
    fun uninstall() = h.uninstall()

    private fun refusal(
        hasActivity: Boolean = true,
        name: String = "slot:Details",
        type: LayoutType = LayoutType.Component,
        hasStack: Boolean = true,
        idShown: Boolean = false,
        stackHasView: Boolean = true,
        stackShown: Boolean = true,
        width: Int = 1080,
        height: Int = 2400,
        keyInStack: Boolean = false,
        push: StackPushAnimationOptions = StackPushAnimationOptions(),
    ): Refusal? = PreparedPushes.refusal(
        hasActivity = hasActivity,
        node = h.layout("B", name = name, type = type),
        hasStack = hasStack,
        idShown = idShown,
        stackHasView = stackHasView,
        stackShown = stackShown,
        stackWidth = width,
        stackHeight = height,
        keyInStack = keyInStack,
        push = push,
    )

    @Test
    fun aSlotScreenInAShownStackIsPrepared() {
        assertNull(refusal())
    }

    @Test
    fun everyCaseThatPreparesNothing() {
        assertEquals(Refusal.NoActivity, refusal(hasActivity = false))
        assertEquals(Refusal.NotASlot, refusal(name = "Details"))
        assertEquals(Refusal.NotASlot, refusal(type = LayoutType.ExternalComponent))
        assertEquals(Refusal.NoStack, refusal(hasStack = false))
        assertEquals(Refusal.Shown, refusal(idShown = true))
        assertEquals(Refusal.NoStackView, refusal(stackHasView = false))
        assertEquals(Refusal.StackNotShown, refusal(stackShown = false))
        assertEquals(Refusal.ZeroSize, refusal(width = 0))
        assertEquals(Refusal.ZeroSize, refusal(height = 0))
        assertEquals(Refusal.KeyInStack, refusal(keyInStack = true))
        assertEquals(
            Refusal.WaitsForRender,
            refusal(push = StackPushAnimationOptions().apply { waitForRender = Bool(true) }),
        )
        assertEquals(
            Refusal.ElementTransitions,
            refusal(push = StackPushAnimationOptions().apply {
                sharedElements = SharedElements(listOf(SharedElementTransitionOptions()))
            }),
        )
        assertEquals(
            Refusal.ElementTransitions,
            refusal(push = StackPushAnimationOptions().apply {
                elementTransitions = ElementTransitions(listOf(ElementTransitionOptions()))
            }),
        )
    }

    @Test
    fun emptyListsOfElementsDontCount() {
        assertNull(refusal(push = StackPushAnimationOptions().apply {
            sharedElements = SharedElements(emptyList())
            elementTransitions = ElementTransitions(emptyList())
        }))
    }

    @Test
    fun itsPushTakesItAsItIs() {
        val b = h.screen("B", slot = true).apply { isPreparedPush = true }
        h.prepared.add(b, h.stack)
        assertTrue(h.prepared.isPrepared(b))
        assertSame(b, h.prepared.take("B", h.stack))
        assertFalse(h.prepared.isPrepared(b))
        assertFalse(b.isDestroyed)
    }

    @Test
    fun aPushIntoAnotherStackDiscardsIt() {
        val other = StackController(h.activity, h.registry, "Stack2", StackPresenter(h.activity), Options())
        val b = h.screen("B", slot = true).apply { isPreparedPush = true }
        h.prepared.add(b, other)
        assertNull(h.prepared.take("B", h.stack))
        assertTrue(b.isDestroyed)
        // Only prepared: JavaScript decides about its slot itself.
        assertFalse("slotDestroyed:B" in h.events())
    }

    @Test
    fun aSecondPreparationWithTheSameIdReplacesTheFirst() {
        val first = h.screen("B", slot = true).apply { isPreparedPush = true }
        h.prepared.add(first, h.stack)
        h.prepared.discard("B")
        val second = h.screen("B", slot = true).apply { isPreparedPush = true }
        h.prepared.add(second, h.stack)
        assertTrue(first.isDestroyed)
        assertTrue(h.prepared.isPrepared(second))
        assertSame(second, h.registry.find("B"))
    }

    @Test
    fun cancelPreparedPushDestroysItAndAnUnknownIdNothing() {
        val b = h.screen("B", slot = true).apply { isPreparedPush = true }
        h.prepared.add(b, h.stack)
        h.commands.cancelPreparedPush("Unknown")
        assertFalse(b.isDestroyed)
        h.commands.cancelPreparedPush("B")
        assertTrue(b.isDestroyed)
        assertTrue(h.events().none { it.startsWith("slotDestroyed") || " resolve " in it })
    }

    @Test
    fun theNavigationsDestroyTakesEveryPreparedScreen() {
        val b = h.screen("B", slot = true).apply { isPreparedPush = true }
        val c = h.screen("C", slot = true).apply { isPreparedPush = true }
        h.prepared.add(b, h.stack)
        h.prepared.add(c, h.stack)
        h.prepared.destroyAll()
        assertTrue(b.isDestroyed)
        assertTrue(c.isDestroyed)
    }

    @Test
    fun aPreparedScreenIsNoChildOfTheStackButMeasuresAsItsTop() {
        h.start(h.screen("A"))
        val b = h.screen("B", slot = true).apply { isPreparedPush = true }
        b.parentController = h.stack
        h.prepared.add(b, h.stack)
        // Not in the stack: pop, back, other pushes and the state don't see it.
        assertFalse(b in h.stack.childControllers)
        assertEquals(listOf("A"), h.entries())
        // It measures its safe area as the stack's top, though it doesn't show.
        assertTrue(h.stack.isPrepared(b))
        assertTrue(h.stack.isTopOfStack(b))
        assertTrue(h.stack.measuresThoughNotShown(b))
        assertFalse(h.stack.measuresThoughNotShown(h.registry.find("A") ?: error("A")))
        // Its merge stays in its options for its show.
        b.mergeOptions(Options().apply { fab.id = Text("add") })
        assertEquals("add", b.options.fab.id.get())
    }
}

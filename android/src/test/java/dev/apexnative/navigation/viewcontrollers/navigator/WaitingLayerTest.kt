package dev.apexnative.navigation.viewcontrollers.navigator

import android.app.Activity
import dev.apexnative.navigation.react.events.DispatcherFixture
import dev.apexnative.navigation.viewcontrollers.modal.ModalRecord
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestLayout
import dev.apexnative.navigation.viewcontrollers.viewcontroller.TestScreen
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The layers that wait to show: a node asks the navigation whether it is inside one, and tells it when a stack of it
 * changed its top; the layer it is in answers, whichever it is (the pending root, a modal later).
 */
class WaitingLayerTest {
    private val fixture = DispatcherFixture()
    private val activity = Activity()
    private val navigator = Navigator(activity)
    private val registry = navigator.registry

    private class Layer(override var waitingNode: ViewController<*>?) : WaitingLayer {
        var changes = 0

        override fun shownScreensChanged() {
            changes++
        }
    }

    @Before
    fun install() = fixture.install()

    @After
    fun uninstall() = fixture.uninstall()

    @Test
    fun aNodeInsideAWaitingLayerAsksItAndTellsIt() {
        val modal = TestLayout(activity, registry, "Modal1")
        val stack = TestLayout(activity, registry, "Stack1")
        val home = TestScreen(activity, registry, "Home")
        modal.add(stack)
        stack.add(home)
        val layer = Layer(modal)
        navigator.addWaitingLayer(layer)
        assertTrue(navigator.isInWaitingLayer(stack))
        assertTrue(navigator.isInWaitingLayer(home))
        navigator.shownScreensChanged(stack)
        assertEquals(1, layer.changes)
    }

    // A modal record is the waiting layer of its stack while it waits, and no longer once it comes in.
    @Test
    fun aWaitingModalIsAWaitingLayerOfTheNavigator() {
        val stack = TestLayout(activity, registry, "Stack1")
        val home = TestScreen(activity, registry, "Home")
        stack.add(home)
        val record = ModalRecord(stack, isSheet = false)
        navigator.addWaitingLayer(record)
        assertTrue(navigator.isInWaitingLayer(home))
        record.state = ModalRecord.State.Presenting
        assertFalse(navigator.isInWaitingLayer(home))
    }

    @Test
    fun aNodeOutsideOrALayerThatNoLongerWaitsIsOnScreen() {
        val modal = TestLayout(activity, registry, "Modal1")
        val other = TestLayout(activity, registry, "Stack2")
        val layer = Layer(modal)
        navigator.addWaitingLayer(layer)
        assertFalse(navigator.isInWaitingLayer(other))
        navigator.shownScreensChanged(other)
        assertEquals(0, layer.changes)
        layer.waitingNode = null
        assertFalse(navigator.isInWaitingLayer(modal))
        layer.waitingNode = modal
        navigator.removeWaitingLayer(layer)
        assertFalse(navigator.isInWaitingLayer(modal))
        navigator.shownScreensChanged(modal)
        assertEquals(0, layer.changes)
    }
}

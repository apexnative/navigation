package dev.apexnative.navigation.views.animations

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hardware layer of an animation: taken only for a fade of a view whose rendering overlaps and only from no layer,
 * given back at the end and at a cancel, never over a layer someone else set.
 */
class AnimationLayerTest {
    // A view's layer type and the writes of it.
    private class FakeLayer(var type: Int = View.LAYER_TYPE_NONE) {
        val writes = mutableListOf<Int>()
        val hold = AnimationLayer.Hold({ type }, {
            type = it
            writes += it
        })
    }

    @Test
    fun takesOnlyForAFadeOfAnOverlappingViewWithoutALayer() {
        assertTrue(AnimationLayer.takes(changesAlpha = true, overlapping = true, layerType = View.LAYER_TYPE_NONE))
        assertFalse(AnimationLayer.takes(changesAlpha = false, overlapping = true, layerType = View.LAYER_TYPE_NONE))
        assertFalse(AnimationLayer.takes(changesAlpha = true, overlapping = false, layerType = View.LAYER_TYPE_NONE))
        assertFalse(AnimationLayer.takes(changesAlpha = true, overlapping = true, layerType = View.LAYER_TYPE_HARDWARE))
        assertFalse(AnimationLayer.takes(changesAlpha = true, overlapping = true, layerType = View.LAYER_TYPE_SOFTWARE))
    }

    @Test
    fun setsTheLayerAtTheStartAndGivesItBackAtTheEnd() {
        val layer = FakeLayer()
        layer.hold.take(changesAlpha = true, overlapping = true)
        assertEquals(View.LAYER_TYPE_HARDWARE, layer.type)
        assertTrue(layer.hold.isTaken)
        layer.hold.release()
        assertEquals(View.LAYER_TYPE_NONE, layer.type)
        assertFalse(layer.hold.isTaken)
    }

    // A cancel is followed by the animator's end: the second release changes nothing.
    @Test
    fun givesItBackOnceAtACancelAndItsEnd() {
        val layer = FakeLayer()
        layer.hold.take(changesAlpha = true, overlapping = true)
        layer.hold.release()
        layer.hold.release()
        assertEquals(listOf(View.LAYER_TYPE_HARDWARE, View.LAYER_TYPE_NONE), layer.writes)
    }

    @Test
    fun aPureMoveTakesNoLayer() {
        val layer = FakeLayer()
        layer.hold.take(changesAlpha = false, overlapping = true)
        layer.hold.release()
        assertTrue(layer.writes.isEmpty())
    }

    @Test
    fun aViewWithoutOverlappingRenderingTakesNoLayer() {
        val layer = FakeLayer()
        layer.hold.take(changesAlpha = true, overlapping = false)
        layer.hold.release()
        assertTrue(layer.writes.isEmpty())
    }

    // The stack's default transition, or the app, set one: it stays theirs, also after the animation.
    @Test
    fun neverTakesOrDropsALayerSomeoneElseSet() {
        for (type in listOf(View.LAYER_TYPE_HARDWARE, View.LAYER_TYPE_SOFTWARE)) {
            val layer = FakeLayer(type)
            layer.hold.take(changesAlpha = true, overlapping = true)
            assertFalse(layer.hold.isTaken)
            layer.hold.release()
            assertEquals(type, layer.type)
            assertTrue(layer.writes.isEmpty())
        }
    }

    // Changed to a software layer while the animation ran: the change stays.
    @Test
    fun keepsALayerChangedMeanwhile() {
        val layer = FakeLayer()
        layer.hold.take(changesAlpha = true, overlapping = true)
        layer.type = View.LAYER_TYPE_SOFTWARE
        layer.hold.release()
        assertEquals(View.LAYER_TYPE_SOFTWARE, layer.type)
        assertFalse(layer.hold.isTaken)
    }

    // A second start without an end in between doesn't take it twice; after the end a new start takes it again.
    @Test
    fun takesOnceAtATimeAndAgainAfterTheEnd() {
        val layer = FakeLayer()
        layer.hold.take(changesAlpha = true, overlapping = true)
        layer.hold.take(changesAlpha = true, overlapping = true)
        layer.hold.release()
        layer.hold.take(changesAlpha = true, overlapping = true)
        layer.hold.release()
        assertEquals(
            listOf(View.LAYER_TYPE_HARDWARE, View.LAYER_TYPE_NONE, View.LAYER_TYPE_HARDWARE, View.LAYER_TYPE_NONE),
            layer.writes,
        )
    }

    // Two animations on one view: the second finds the first's layer and leaves it to the first.
    @Test
    fun aSecondAnimationLeavesTheLayerToTheFirst() {
        var type = View.LAYER_TYPE_NONE
        val first = AnimationLayer.Hold({ type }, { type = it })
        val second = AnimationLayer.Hold({ type }, { type = it })
        first.take(changesAlpha = true, overlapping = true)
        second.take(changesAlpha = true, overlapping = true)
        assertFalse(second.isTaken)
        second.release()
        assertEquals(View.LAYER_TYPE_HARDWARE, type)
        first.release()
        assertEquals(View.LAYER_TYPE_NONE, type)
    }
}

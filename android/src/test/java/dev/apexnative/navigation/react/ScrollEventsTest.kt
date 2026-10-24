package dev.apexnative.navigation.react

import android.app.Activity
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.facebook.react.views.scroll.ScrollEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * React Native's scroll listener as the screens hear it: each kind of event, the reading's fields, nothing from a
 * horizontal scroll view, and only the handlers listening now.
 */
class ScrollEventsTest {
    private val activity = Activity()

    // A vertical scroll view with its tag; the JVM's stub views have no scroll, so the offset reads 0.
    private class Vertical(context: Activity, private val tag: Int) : ScrollView(context) {
        override fun getId(): Int = tag
    }

    private class Horizontal(context: Activity, private val tag: Int) : HorizontalScrollView(context) {
        override fun getId(): Int = tag
    }

    private val stops = mutableListOf<() -> Unit>()

    @After
    fun stopListening() {
        stops.forEach { it() }
    }

    private fun listen(handler: (ScrollEvents.Reading) -> Unit) {
        stops += ScrollEvents.listen(handler)
    }

    @Test
    fun everyKindOfEventMapsToItsKind() {
        val expected = mapOf(
            ScrollEventType.BEGIN_DRAG to ScrollEvents.Kind.BeginDrag,
            ScrollEventType.END_DRAG to ScrollEvents.Kind.EndDrag,
            ScrollEventType.SCROLL to ScrollEvents.Kind.Scroll,
            ScrollEventType.MOMENTUM_BEGIN to ScrollEvents.Kind.MomentumBegin,
            ScrollEventType.MOMENTUM_END to ScrollEvents.Kind.MomentumEnd,
        )
        assertEquals(ScrollEventType.entries.toSet(), expected.keys)
        expected.forEach { (type, kind) -> assertEquals(kind, ScrollEvents.kindOf(type)) }
    }

    @Test
    fun aVerticalScrollViewGivesItsTagOffsetAndSpeed() {
        val reading = ScrollEvents.read(Vertical(activity, 42), ScrollEventType.END_DRAG, -3.5f)
        assertNotNull(reading)
        requireNotNull(reading)
        assertEquals(42, reading.viewTag)
        assertEquals(ScrollEvents.Kind.EndDrag, reading.kind)
        assertEquals(0f, reading.offsetY)
        assertEquals(-3.5f, reading.velocityY)
    }

    @Test
    fun aHorizontalScrollViewGivesNothing() {
        assertNull(ScrollEvents.read(Horizontal(activity, 42), ScrollEventType.SCROLL, 0f))
        assertNull(ScrollEvents.read(Horizontal(activity, 42), ScrollEventType.BEGIN_DRAG, 0f))
    }

    @Test
    fun noViewOrNoKindGivesNothing() {
        assertNull(ScrollEvents.read(null, ScrollEventType.SCROLL, 0f))
        assertNull(ScrollEvents.read(Vertical(activity, 42), null, 0f))
    }

    @Test
    fun onlyTheHandlersListeningNowHear() {
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()
        val stopFirst = ScrollEvents.listen { first += it.viewTag }
        listen { second += it.viewTag }
        ScrollEvents.hear(Vertical(activity, 1), ScrollEventType.SCROLL, 0f)
        stopFirst()
        ScrollEvents.hear(Vertical(activity, 2), ScrollEventType.SCROLL, 0f)
        ScrollEvents.hear(Horizontal(activity, 3), ScrollEventType.SCROLL, 0f)
        assertEquals(listOf(1), first)
        assertEquals(listOf(1, 2), second)
    }

    @Test
    fun stoppingTwiceIsHarmless() {
        val heard = mutableListOf<Int>()
        val handler: (ScrollEvents.Reading) -> Unit = { heard += it.viewTag }
        val stop = ScrollEvents.listen(handler)
        listen(handler)
        stop()
        stop()
        // The same handler listening a second time still hears: a stop takes away only its own listening.
        ScrollEvents.hear(Vertical(activity, 5), ScrollEventType.SCROLL, 0f)
        assertEquals(listOf(5), heard)
    }
}

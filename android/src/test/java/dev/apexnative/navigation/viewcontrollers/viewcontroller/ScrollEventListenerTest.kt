package dev.apexnative.navigation.viewcontrollers.viewcontroller

import android.app.Activity
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import dev.apexnative.navigation.react.ScrollEvents
import dev.apexnative.navigation.react.ScrollEvents.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scroll listener of a screen: one registration per follower, only the screen's own vertical lists, the drag as a
 * translation and its end as a way to settle, the offset during the inertia too, nothing after the screen went.
 */
class ScrollEventListenerTest {
    // Tag 7 is a vertical list of the screen; any other tag is someone else's or horizontal.
    private val listener = ScrollEventListener { it == OWN }
    private val heard = mutableListOf<String>()

    private val follower = object : ScrollEventListener.OnScrollListener, ScrollEventListener.OnDragListener {
        override fun onScrollUp(nextTranslation: Float) {
            heard += "up:$nextTranslation"
        }

        override fun onScrollDown(nextTranslation: Float) {
            heard += "down:$nextTranslation"
        }

        override fun onScrollOffset(offsetY: Float) {
            heard += "offset:$offsetY"
        }

        override fun onShow() {
            heard += "show"
        }

        override fun onHide() {
            heard += "hide"
        }
    }

    private fun event(kind: Kind, offsetY: Float, velocityY: Float = 0f, tag: Int = OWN) =
        listener.onScrollEvent(ScrollEvents.Reading(tag, kind, offsetY, velocityY))

    @Test
    fun aDragIsFollowedAndSettlesTheWayTheFingerWent() {
        listener.register(null, follower, follower)
        event(Kind.BeginDrag, 0f)
        event(Kind.Scroll, 30f)
        event(Kind.Scroll, 20f)
        event(Kind.EndDrag, 20f, velocityY = -2f)
        assertEquals(
            listOf("offset:0.0", "up:-30.0", "offset:30.0", "down:10.0", "offset:20.0", "hide", "offset:20.0"),
            heard,
        )
    }

    @Test
    fun withoutSpeedTheDragSettlesTheWayItWentLast() {
        listener.register(null, follower, follower)
        event(Kind.BeginDrag, 100f)
        event(Kind.Scroll, 90f)
        event(Kind.EndDrag, 90f)
        assertTrue("show" in heard)
        heard.clear()
        event(Kind.BeginDrag, 90f)
        event(Kind.Scroll, 120f)
        event(Kind.EndDrag, 120f)
        assertTrue("hide" in heard)
    }

    @Test
    fun theInertiaGivesTheOffsetButMovesNothing() {
        listener.register(null, follower, follower)
        event(Kind.MomentumBegin, 50f)
        event(Kind.Scroll, 80f)
        event(Kind.MomentumEnd, 90f)
        assertEquals(listOf("offset:50.0", "offset:80.0", "offset:90.0"), heard)
    }

    @Test
    fun onlyTheScreensOwnVerticalListsCount() {
        listener.register(null, follower, follower)
        event(Kind.BeginDrag, 0f, tag = OTHER)
        event(Kind.Scroll, 30f, tag = OTHER)
        assertTrue(heard.isEmpty())
    }

    @Test
    fun aFollowerRegisteredAgainIsHeardOnce() {
        listener.register(null, follower, follower)
        listener.register(null, follower, follower)
        event(Kind.MomentumBegin, 10f)
        assertEquals(listOf("offset:10.0"), heard)
        listener.unregister(follower)
        assertFalse(listener.hasListeners)
    }

    @Test
    fun registeringAgainMidDragKeepsTheDrag() {
        listener.register(null, follower, follower)
        event(Kind.BeginDrag, 0f)
        event(Kind.Scroll, 30f)
        listener.register(null, follower, follower)
        event(Kind.Scroll, 50f)
        event(Kind.EndDrag, 50f, velocityY = -1f)
        assertTrue("up:-20.0" in heard)
        assertTrue("hide" in heard)
    }

    @Test
    fun theLastOffsetStaysWhenTheLastFollowerLeaves() {
        assertEquals(0f, listener.lastOffset)
        listener.register(null, follower, follower)
        event(Kind.BeginDrag, 0f)
        event(Kind.Scroll, 120f)
        event(Kind.Scroll, 40f, tag = OTHER)
        assertEquals(120f, listener.lastOffset)
        listener.unregister(follower)
        assertEquals(120f, listener.lastOffset)
        listener.register(null, follower, follower)
        assertEquals(120f, listener.lastOffset)
        event(Kind.MomentumEnd, 90f)
        assertEquals(90f, listener.lastOffset)
    }

    @Test
    fun theLastOffsetIsForgottenOnlyWhenNothingListens() {
        listener.register(null, follower, follower)
        event(Kind.MomentumEnd, 120f)
        listener.forgetOffset()
        assertEquals(120f, listener.lastOffset)
        listener.unregister(follower)
        listener.forgetOffset()
        assertEquals(0f, listener.lastOffset)
    }

    @Test
    fun onlyScrollViewsAreLists() {
        val activity = Activity()
        assertTrue(ScrollEventListener.isVerticalList(ScrollView(activity)))
        assertFalse(ScrollEventListener.isVerticalList(HorizontalScrollView(activity)))
        assertFalse(ScrollEventListener.isVerticalList(EditText(activity)))
        assertFalse(ScrollEventListener.isVerticalList(null))
    }

    @Test
    fun aReleasedListenerHearsNothingAndTakesNoFollower() {
        listener.register(null, follower, follower)
        listener.release()
        event(Kind.MomentumBegin, 10f)
        listener.register(null, follower, follower)
        assertFalse(listener.hasListeners)
        assertTrue(heard.isEmpty())
    }

    private companion object {
        const val OWN = 7
        const val OTHER = 8
    }
}

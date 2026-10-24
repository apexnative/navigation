package dev.apexnative.navigation.react

import android.view.ViewGroup
import android.widget.HorizontalScrollView
import com.facebook.react.views.scroll.ReactScrollViewHelper
import com.facebook.react.views.scroll.ScrollEventType

/**
 * The scroll events of React Native's scroll views, as the screens' scroll listeners hear them: through React Native's
 * public scroll listener ([ReactScrollViewHelper.addScrollListener]), which every `ReactScrollView`,
 * `ReactHorizontalScrollView` and `ReactNestedScrollView` of the process calls on the main thread just before it
 * dispatches the same event to JavaScript, after the same throttle. One listener serves every screen and every React
 * context; it is added while a screen listens and removed when the last one stops. Horizontal scroll views are left
 * out: a pager or a carousel doesn't move the bars. Main thread only.
 */
internal object ScrollEvents {
    /** What a scroll event is. */
    enum class Kind { BeginDrag, EndDrag, Scroll, MomentumBegin, MomentumEnd }

    /** One scroll event: the list's tag (its view's id), its kind, its vertical offset and speed, in pixels. */
    class Reading(val viewTag: Int, val kind: Kind, val offsetY: Float, val velocityY: Float)

    // One screen's listening: its own object, so that a stop takes away that listening and no other.
    private class Listening(val handler: (Reading) -> Unit)

    // The screens' listenings, in the order they came.
    private val listenings = mutableListOf<Listening>()

    // React Native holds its scroll listeners weakly: this object keeps the one it added for as long as it is added.
    private val listener = object : ReactScrollViewHelper.ScrollListener {
        override fun onScroll(
            scrollView: ViewGroup?,
            scrollEventType: ScrollEventType?,
            xVelocity: Float,
            yVelocity: Float,
        ) = hear(scrollView, scrollEventType, yVelocity)

        override fun onLayout(scrollView: ViewGroup?) = Unit
    }

    /**
     * Calls [handler] with each scroll event of a vertical scroll view of the app, until the function returned is
     * called.
     */
    fun listen(handler: (Reading) -> Unit): () -> Unit {
        val listening = Listening(handler)
        if (listenings.isEmpty()) ReactScrollViewHelper.addScrollListener(listener)
        listenings += listening
        return {
            // By identity: the second call of a stop finds nothing to take away.
            if (listenings.removeAll { it === listening } && listenings.isEmpty()) {
                ReactScrollViewHelper.removeScrollListener(listener)
            }
        }
    }

    /** One call of React Native's listener: the handlers listening now hear its reading, if it has one. */
    fun hear(scrollView: ViewGroup?, type: ScrollEventType?, velocityY: Float) {
        val reading = read(scrollView, type, velocityY) ?: return
        listenings.toList().forEach { it.handler(reading) }
    }

    /**
     * The reading of one call of React Native's listener: none for a horizontal scroll view, or when React Native gives
     * no view or no kind. The offset is the view's `scrollY` and the speed React Native's own, in pixels, as the event
     * sent to JavaScript carries them before it converts them to dp.
     */
    fun read(scrollView: ViewGroup?, type: ScrollEventType?, velocityY: Float): Reading? {
        if (scrollView == null || type == null || scrollView is HorizontalScrollView) return null
        return Reading(scrollView.id, kindOf(type), scrollView.scrollY.toFloat(), velocityY)
    }

    /** The kind of a reading for React Native's [type]. */
    fun kindOf(type: ScrollEventType): Kind = when (type) {
        ScrollEventType.BEGIN_DRAG -> Kind.BeginDrag
        ScrollEventType.END_DRAG -> Kind.EndDrag
        ScrollEventType.SCROLL -> Kind.Scroll
        ScrollEventType.MOMENTUM_BEGIN -> Kind.MomentumBegin
        ScrollEventType.MOMENTUM_END -> Kind.MomentumEnd
    }
}

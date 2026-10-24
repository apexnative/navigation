package dev.apexnative.navigation.viewcontrollers.viewcontroller

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import dev.apexnative.navigation.react.ScrollEvents
import java.lang.ref.WeakReference

/**
 * The scroll of a React screen's content, which the bars follow (`topBar.hideOnScroll`, `bottomTabs.hideOnScroll`, the
 * large title, the FAB): one per screen's view, with several followers at once, each registered once however often it
 * registers again. It hears only the vertical lists inside the screen's view — a list whose view (its id is its Fabric
 * tag) is in the screen, found once per tag —, not the horizontal ones, nor those of a modal, an overlay or another
 * screen; a slot screen's lists are in its container too. While a list is dragged, each follower gets the drag as a
 * translation to follow; when the drag ends, the way to settle; at every scroll event, the drags' and the inertia's,
 * the offset. It listens to React Native's scroll views ([ScrollEvents]) only while it has followers and its screen's
 * view. Released with the screen. Main thread only.
 */
class ScrollEventListener() {
    /** Follows the content while it is dragged. */
    interface OnScrollListener {
        /** The content went up; [nextTranslation] is where the follower would be, from its place of rest. */
        fun onScrollUp(nextTranslation: Float)

        /** The content went down; as [onScrollUp]. */
        fun onScrollDown(nextTranslation: Float)

        /**
         * The vertical offset of the list that scrolled, in pixels: at each of its scroll events, while it is dragged
         * and while it goes on by itself.
         */
        fun onScrollOffset(offsetY: Float) = Unit
    }

    /** Settles when a drag ends: the finger went down (show) or up (hide). */
    interface OnDragListener {
        fun onShow()

        fun onHide()
    }

    private class Registration(val view: View?, val onScroll: OnScrollListener, val onDrag: OnDragListener)

    private val registrations = mutableListOf<Registration>()

    // Whether the list of a tag is one of the screen's own vertical lists; a test gives its own answer.
    private var ownLists: ((Int) -> Boolean)? = null
    private var scope: WeakReference<ViewGroup>? = null
    private val ownByTag = HashMap<Int, Boolean>()
    private var stopListening: (() -> Unit)? = null
    private var released = false
    // The list being dragged, its last offset by tag, and the way the drag went last.
    private var dragged: Int? = null
    private val offsets = HashMap<Int, Float>()
    private var lastStep = 0f

    /** A listener for the unit tests: [ownLists] tells which tags are the screen's own vertical lists. */
    internal constructor(ownLists: (Int) -> Boolean) : this() {
        this.ownLists = ownLists
    }

    /**
     * The vertical offset of the screen's own list heard last, in pixels; 0 until one scrolls. It stays while no
     * follower listens (the screen is covered), so a follower that comes back starts where the list is.
     */
    var lastOffset = 0f
        private set

    /**
     * The offset heard last no longer stands, when nothing listens to the lists any more while the screen shows: they
     * may scroll unheard from now on. A screen that gets covered keeps it.
     */
    fun forgetOffset() {
        if (registrations.isEmpty()) lastOffset = 0f
    }

    /** Whether a listener follows the screen. */
    val hasListeners: Boolean
        get() = registrations.isNotEmpty()

    /**
     * Adds a follower: [view] is what moves (up from rest, as far as the follower lets it; none for a follower that
     * only hears the way), [onScroll] and [onDrag] hear the scroll. A follower registered again replaces itself.
     */
    fun register(view: View?, onScroll: OnScrollListener, onDrag: OnDragListener) {
        if (released) return
        // In its place: followers register again at every apply of the options, and a drag in progress, the offsets and
        // the listening to React Native stay as they are.
        val registration = Registration(view, onScroll, onDrag)
        val index = registrations.indexOfFirst { it.onScroll === onScroll }
        if (index >= 0) registrations[index] = registration else registrations += registration
        attachIfNeeded()
    }

    /** Removes the follower whose scroll listener is [listener]. */
    fun unregister(listener: OnScrollListener) {
        registrations.removeAll { it.onScroll === listener }
        if (registrations.isEmpty()) detach()
    }

    /** The lists inside [screenView] are the screen's. */
    internal fun watch(screenView: ViewGroup) {
        scope = WeakReference(screenView)
        ownByTag.clear()
    }

    /**
     * Listens to React Native's scroll views when followers wait for them and it doesn't yet, once it has the screen's
     * view to tell its own lists by; asked again as the screen joins a window.
     */
    internal fun attachIfNeeded() {
        if (released || stopListening != null || registrations.isEmpty() || scope?.get() == null) return
        stopListening = ScrollEvents.listen(::onScrollEvent)
    }

    /** The screen is destroyed: no follower hears it any more. */
    internal fun release() {
        released = true
        registrations.clear()
        detach()
        scope = null
        ownByTag.clear()
    }

    /** One scroll event of a list of the app; only the screen's own vertical lists count. */
    internal fun onScrollEvent(event: ScrollEvents.Reading) {
        if (released || registrations.isEmpty() || !isOwnVerticalList(event.viewTag)) return
        val tag = event.viewTag
        lastOffset = event.offsetY
        val step = event.offsetY - (offsets[tag] ?: event.offsetY)
        offsets[tag] = event.offsetY
        when (event.kind) {
            ScrollEvents.Kind.BeginDrag -> {
                dragged = tag
                lastStep = 0f
            }
            ScrollEvents.Kind.EndDrag -> if (dragged == tag) endDrag(event.velocityY)
            ScrollEvents.Kind.Scroll -> if (dragged == tag && step != 0f) follow(step)
            ScrollEvents.Kind.MomentumBegin, ScrollEvents.Kind.MomentumEnd -> Unit
        }
        registrations.toList().forEach { it.onScroll.onScrollOffset(event.offsetY) }
    }

    // The rows moved by [step] pixels while dragged: up when the offset grows.
    private fun follow(step: Float) {
        lastStep = step
        for (registration in registrations.toList()) {
            val view = registration.view
            // The view's follower bounds how far it goes: the top bar below the status bar goes by its margin too.
            val next = if (view == null) -step else (view.translationY - step).coerceAtMost(0f)
            if (step > 0) registration.onScroll.onScrollUp(next) else registration.onScroll.onScrollDown(next)
        }
    }

    // The finger went up (a negative speed: the offset grows) or down; without speed, the way the drag went last.
    private fun endDrag(velocityY: Float) {
        dragged = null
        val hide = if (velocityY != 0f) velocityY < 0f else lastStep > 0f
        for (registration in registrations.toList()) {
            if (hide) registration.onDrag.onHide() else registration.onDrag.onShow()
        }
    }

    private fun isOwnVerticalList(tag: Int): Boolean {
        ownLists?.let { return it(tag) }
        ownByTag[tag]?.let { return it }
        val screenView = scope?.get() ?: return false
        val own = isVerticalList(screenView.findViewById(tag))
        if (ownByTag.size >= TAG_CACHE_LIMIT) ownByTag.clear()
        ownByTag[tag] = own
        return own
    }

    private fun detach() {
        stopListening?.invoke()
        stopListening = null
        dragged = null
        offsets.clear()
    }

    internal companion object {
        // The tags remembered at most: the lists of a screen, and those of the other screens it saw.
        private const val TAG_CACHE_LIMIT = 256

        /**
         * Whether [view] is a vertical list: a scroll view (React Native's `ReactScrollView`). Horizontal lists and
         * the other views that send scroll events (a multiline text input) don't count.
         */
        fun isVerticalList(view: View?): Boolean = view is ScrollView
    }
}

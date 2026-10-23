package dev.apexnative.navigation.viewcontrollers.component

/**
 * The readiness of each screen by its id: the safe area view of a screen (`ANNSafeAreaView`) tells it here that it
 * has one, that it is laid out in its place and that the screen's content rendered into it, and asks whether the
 * screen waits for that at all. The content of a screen or of a component listens by the id: no view walks up or down
 * the views to find the other. Main thread only.
 *
 * As on iOS (`ANNScreenReadiness`), what a safe area view said is kept: a listener that comes later hears it at once.
 * Several listeners may wait on one id (the roots of a bar component that several screens show with one id), each
 * under a key of its own, which removes it. Each signal carries its source, the React surface of the safe area view
 * that gave it, and a listener hears only the sources it [accepts][Listener.accepts]: React Native never gives a
 * surface's id twice in a process, so the late signal of another root with the same id (a root of the JavaScript that
 * went, a destroyed screen, another live root of a bar component) never makes a new root ready.
 */
object ScreenReadiness {
    /** The source of a signal that names none: a listener that accepts every source hears it. */
    const val NO_SOURCE = -1

    /** What the content of a screen or of a component hears from the safe area view of its id. */
    interface Listener {
        /**
         * Whether a signal of [source] (the id of the React surface of the safe area view that gave it) is this
         * listener's: by default every one is.
         */
        fun accepts(source: Int): Boolean = true

        /** The safe area view is laid out in its place, with a size. */
        fun onLaidOut() = Unit

        /** The screen rendered its content into its safe area view, also when it renders nothing (`null`). */
        fun onContentRendered()
    }

    // What the safe area views of one id said: that one exists, and the sources that are laid out and rendered.
    private class Entry {
        var wrapped = false
        val laidOut = HashSet<Int>()
        val rendered = HashSet<Int>()
    }

    private val entries = HashMap<String, Entry>()
    private val listeners = HashMap<String, LinkedHashMap<Any, Listener>>()
    // The roots that render at once, by id: an id renders at once while one of them lives.
    private val atOnce = HashMap<String, MutableSet<Any>>()

    /**
     * Whether the screen [screenId] waits for its safe area view to be laid out before its content renders: every
     * screen does, as on iOS, unless a root of the id renders at once ([setRendersAtOnce]) — a React component of a
     * bar or a tab item, whose size comes from its content.
     */
    fun waitsForReadiness(screenId: String): Boolean = atOnce[screenId].isNullOrEmpty()

    /**
     * The root [key] of [screenId] renders at once (a component of a bar, a tab item), or stopped. The id renders at
     * once while a root marks it; when the last one stops, what was said of the id goes too, so a later root with it
     * starts anew.
     */
    fun setRendersAtOnce(screenId: String, key: Any, rendersAtOnce: Boolean) {
        if (rendersAtOnce) {
            atOnce.getOrPut(screenId, ::HashSet).add(key)
            return
        }
        val keys = atOnce[screenId] ?: return
        if (!keys.remove(key) || keys.isNotEmpty()) return
        atOnce.remove(screenId)
        entries.remove(screenId)
    }

    /** The screen [screenId] has a safe area view: it is rendered only once that view says so. */
    fun wrapped(screenId: String) {
        entryOf(screenId).wrapped = true
    }

    /** The safe area view of [screenId], in the React surface [source], is laid out in its place, with a size. */
    fun laidOut(screenId: String, source: Int = NO_SOURCE) {
        val entry = entryOf(screenId)
        entry.wrapped = true
        if (entry.laidOut.add(source)) tell(screenId, source, Listener::onLaidOut)
    }

    /** The screen [screenId] rendered its content into its safe area view, in the React surface [source]. */
    fun contentRendered(screenId: String, source: Int = NO_SOURCE) {
        val entry = entryOf(screenId)
        entry.wrapped = true
        if (entry.rendered.add(source)) tell(screenId, source, Listener::onContentRendered)
    }

    /**
     * Whether the screen [screenId] rendered its content into its safe area view, in a source [listener] accepts (any
     * source without one); null when no safe area view told anything of the id (a screen without one, of the old API:
     * its content decides by its own children).
     */
    fun isContentRendered(screenId: String, listener: Listener? = null): Boolean? {
        val entry = entries[screenId] ?: return null
        return entry.rendered.any { listener?.accepts(it) ?: true }
    }

    /**
     * [listener] hears the signals of [screenId] under [key] (its owner) until [removeListener] with the key: a key
     * has one listener per id, the other keys keep theirs. What the safe area views said before reaches it at once, in
     * order, for the sources it accepts.
     */
    fun addListener(screenId: String, key: Any, listener: Listener) {
        listeners.getOrPut(screenId, ::LinkedHashMap)[key] = listener
        val entry = entries[screenId] ?: return
        if (entry.laidOut.any(listener::accepts) && isListening(screenId, key, listener)) listener.onLaidOut()
        if (entry.rendered.any(listener::accepts) && isListening(screenId, key, listener)) listener.onContentRendered()
    }

    /** The listener of [key] stops hearing [screenId]; those of other keys (a later root with the id) keep theirs. */
    fun removeListener(screenId: String, key: Any) {
        val keys = listeners[screenId] ?: return
        keys.remove(key)
        if (keys.isEmpty()) listeners.remove(screenId)
    }

    /**
     * The screen [screenId] owns the id from now on (made), or went while no other node has it (destroyed): what its
     * safe area views said and the marks of rendering at once go, so a later screen with the id starts anew. The
     * listeners stay with their keys, which remove them.
     */
    fun remove(screenId: String) {
        entries.remove(screenId)
        atOnce.remove(screenId)
    }

    /**
     * The JavaScript went (a reload): everything goes, before the new JavaScript renders. It counts the ids anew, and a
     * "rendered" kept for a screen of the old one would make its new screen with the same id ready at once.
     */
    fun reset() {
        entries.clear()
        listeners.clear()
        atOnce.clear()
    }

    private fun entryOf(screenId: String): Entry = entries.getOrPut(screenId, ::Entry)

    private fun isListening(screenId: String, key: Any, listener: Listener): Boolean =
        listeners[screenId]?.get(key) === listener

    // Each listener of the id that accepts the source, as they are now: one that a listener before it removed hears
    // nothing.
    private fun tell(screenId: String, source: Int, signal: (Listener) -> Unit) {
        val now = listeners[screenId]?.entries?.toList() ?: return
        for ((key, listener) in now) {
            if (isListening(screenId, key, listener) && listener.accepts(source)) signal(listener)
        }
    }
}

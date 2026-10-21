package dev.apexnative.navigation.viewcontrollers.component

/**
 * The readiness of each screen by its id: the safe area view of a screen (`ANNSafeAreaView`) tells it here that it is
 * laid out in its place and that the screen's content rendered into it, and asks whether the screen waits for that at
 * all. The screen's container listens by the screen's id: no view walks up or down the views to find the other. Main
 * thread only.
 */
object ScreenReadiness {
    /** What a screen's container hears from the safe area view of its screen. */
    interface Listener {
        /** The safe area view is laid out in its place, with a size. */
        fun onLaidOut()

        /** The screen rendered its content into its safe area view, also when it renders nothing (`null`). */
        fun onContentRendered()
    }

    private val atOnce = HashSet<String>()
    private val wrapped = HashSet<String>()
    private val laidOut = HashSet<String>()
    private val rendered = HashSet<String>()
    private val listeners = HashMap<String, Listener>()

    /**
     * Whether the screen [screenId] waits for its safe area view to be laid out before its content renders: every
     * screen does, as on iOS, unless the core marked it as one that renders at once ([setRendersAtOnce]) — a React
     * component of a bar or a tab item, whose size comes from its content.
     */
    fun waitsForReadiness(screenId: String): Boolean = screenId !in atOnce

    /** The core marks a React root it made as one that renders at once (a component of a bar, a tab item), or not. */
    fun setRendersAtOnce(screenId: String, rendersAtOnce: Boolean) {
        if (rendersAtOnce) atOnce += screenId else atOnce -= screenId
    }

    /** The screen [screenId] has a safe area view: it is rendered only once that view says so. */
    fun wrapped(screenId: String) {
        wrapped += screenId
    }

    /** The safe area view of [screenId] is laid out in its place, with a size. */
    fun laidOut(screenId: String) {
        if (laidOut.add(screenId)) listeners[screenId]?.onLaidOut()
    }

    /** The screen [screenId] rendered its content into its safe area view. */
    fun contentRendered(screenId: String) {
        if (rendered.add(screenId)) listeners[screenId]?.onContentRendered()
    }

    /**
     * Whether the screen [screenId] rendered its content into its safe area view; null when no safe area view told
     * anything of it (a screen without one, of the old API: its container decides by its own content).
     */
    fun isContentRendered(screenId: String): Boolean? = when (screenId) {
        in rendered -> true
        in laidOut, in wrapped -> false
        else -> null
    }

    /** [listener] hears the signals of [screenId] from now on; null stops it. */
    fun setListener(screenId: String, listener: Listener?) {
        if (listener == null) listeners.remove(screenId) else listeners[screenId] = listener
    }

    /**
     * [listener] stops hearing [screenId], if it is the one that listens: a later screen with the same id (a `setRoot`
     * makes the new root before the old one goes) keeps its own.
     */
    fun removeListener(screenId: String, listener: Listener) {
        if (listeners[screenId] === listener) listeners.remove(screenId)
    }

    /**
     * A new root takes [screenId] while another with it lives (a component of a bar on two screens): what the other
     * rendered isn't the new one's, which waits for its own render. Its mark of rendering at once stays.
     */
    fun forgetRender(screenId: String) {
        laidOut -= screenId
        rendered -= screenId
    }

    /** The screen is destroyed: what was kept of it goes, so a later screen with the same id starts anew. */
    fun remove(screenId: String) {
        atOnce -= screenId
        wrapped -= screenId
        laidOut -= screenId
        rendered -= screenId
        listeners -= screenId
    }

    /**
     * The JavaScript went (a reload): everything goes, before the new JavaScript renders. It counts the ids anew, and a
     * "rendered" kept for a screen of the old one would make its new screen with the same id ready at once.
     */
    fun reset() {
        atOnce.clear()
        wrapped.clear()
        laidOut.clear()
        rendered.clear()
        listeners.clear()
    }
}

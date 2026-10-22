package dev.apexnative.navigation.react.events

import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.viewcontrollers.viewcontroller.IReactView

/**
 * The appearance of one thing that tells JavaScript when it appears and disappears: the content of a React screen (the
 * core's React view, the slot screen view), a React component of a bar or a tab, the results of a search, a native
 * screen. Its owner makes it once and hands it to [AppearanceDispatcher], the only code that sends
 * `ANN.ComponentWillAppear`, `ANN.ComponentDidAppear` and `ANN.ComponentDidDisappear`; the record keeps what the
 * dispatcher needs between the calls: the phase of the appearance, the events held until the content is ready, and
 * whether it is. Main thread only.
 */
internal class ScreenAppearance(
    /** The id the events carry. */
    val componentId: String,
    /** The name the events carry: the React module without `slot:`, or a native screen's registration name. */
    val componentName: String,
    componentType: ComponentType,
    // What the appearance waits for: its content mounted. None for a native screen, which is ready at once.
    private val content: IReactView?,
    /** The JavaScript the record was made for (`ReloadCoordinator`): a later one hears nothing. */
    val generation: Int = ReloadCoordinator.generation,
) {
    /** Where the appearance is: hidden, appearing (WillAppear asked), shown (DidAppear asked). */
    enum class Phase { Hidden, Appearing, Shown }

    /** The appearance events, as the dispatcher sends or holds them. */
    enum class Event { WillAppear, DidAppear, DidDisappear }

    /** `componentType` of the events: a screen, or a component of a bar or a tab. */
    var componentType: ComponentType = componentType
        internal set

    /** The phase; the dispatcher moves it. */
    var phase: Phase = Phase.Hidden
        internal set

    /** The events asked but not sent yet, in order: at most WillAppear, then DidAppear. */
    internal val held = ArrayList<Event>(2)

    /** What runs right after the DidAppear of this appearance goes (`AppearanceDispatcher.afterDidAppear`). */
    internal val afterDidAppear = ArrayList<() -> Unit>(1)

    /** Cancels the hold limit running for [held]; none when nothing is held. */
    internal var cancelLimit: (() -> Unit)? = null

    private var ready = content == null
    private var waitsForContent = false

    /**
     * The content counts as mounted: it has mounted (or there is none to wait for), a command's readiness wait ended
     * for it ([markReady]), or the hold limit passed. Once ready, it stays so.
     */
    val isReady: Boolean
        get() {
            if (!ready && content?.isReady() == true) ready = true
            return ready
        }

    /**
     * A readiness wait of a command ended for the content: it mounted, or the wait reached its limit. The content
     * counts as mounted from now on, and the events held for it go at once, in order.
     */
    fun markReady() = AppearanceDispatcher.contentReady(this)

    /** The dispatcher found the record ready. */
    internal fun setReady() {
        ready = true
    }

    /**
     * Asks the content, once per record, to call [onReady] when it is ready. A call after the record is ready, or
     * after what it held was dropped, finds nothing to send.
     */
    internal fun waitForContent(onReady: () -> Unit) {
        if (waitsForContent) return
        waitsForContent = true
        content?.whenReady { onReady() }
    }
}

package dev.apexnative.navigation.viewcontrollers.component

import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * Whether a screen's content rendered, which a command waits for before the screen comes in: a new root (`setRoot`),
 * a push (unless `waitForRender: false`), the new top of a `setStackRoot`. A wait never listens to the readiness store
 * itself: the content keeps its own listener of its id and tells every waiter. Main thread only.
 */
internal interface Readiness {
    /** The content of [screen] rendered; a screen without content to wait for is ready. */
    fun isReady(screen: ViewController<*>): Boolean

    /** Calls [listener] once [screen] is ready; it can't be taken back, so the waiter guards it itself. */
    fun whenReady(screen: ViewController<*>, listener: () -> Unit)

    /** The wait ended at its limit: the content of [screen] counts as mounted. */
    fun markReady(screen: ViewController<*>)

    /**
     * Whether the readiness store knows nothing of [screen] now: no safe area view has reported its id (none mounted
     * yet, or it has none). Such a screen that renders `null` would never be ready, so a wait without a limit counts it
     * as ready after a while; once a safe area view reported, it reports `null` too.
     */
    fun isRenderUnknown(screen: ViewController<*>): Boolean = false

    /**
     * Whether the components of the bar over [screen] that a wait counts have drawn: its stack's answer
     * (`StackController.areBarComponentsDrawn`); a screen out of a stack has none.
     */
    fun areBarComponentsReady(screen: ViewController<*>): Boolean = true

    /** Calls [listener] once [areBarComponentsReady]; it can't be taken back, so the waiter guards it itself. */
    fun whenBarComponentsReady(screen: ViewController<*>, listener: () -> Unit) = listener()
}

/**
 * The readiness of the core's screens: a React screen's content is ready when it rendered (`IReactView.isReady`); a
 * screen whose view isn't made yet, and a node without React content (a native screen), are ready at once. A wait
 * makes the screen's view before it asks.
 */
internal object ContentReadiness : Readiness {
    override fun isReady(screen: ViewController<*>): Boolean =
        (screen as? ComponentViewController)?.content?.isReady() ?: true

    override fun whenReady(screen: ViewController<*>, listener: () -> Unit) {
        val content = (screen as? ComponentViewController)?.content ?: return listener()
        content.whenReady { listener() }
    }

    override fun markReady(screen: ViewController<*>) {
        (screen as? ComponentViewController)?.markContentReady()
    }

    override fun isRenderUnknown(screen: ViewController<*>): Boolean =
        screen is ComponentViewController && ScreenReadiness.isContentRendered(screen.id) == null

    override fun areBarComponentsReady(screen: ViewController<*>): Boolean =
        screen.findParentStack()?.areBarComponentsDrawn(screen) ?: true

    override fun whenBarComponentsReady(screen: ViewController<*>, listener: () -> Unit) {
        val stack = screen.findParentStack() ?: return listener()
        stack.whenBarComponentsDrawn(screen, listener)
    }
}

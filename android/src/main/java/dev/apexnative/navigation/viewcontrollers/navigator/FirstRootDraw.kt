package dev.apexnative.navigation.viewcontrollers.navigator

import dev.apexnative.navigation.options.params.Bool
import dev.apexnative.navigation.react.events.AppearanceDispatcher

/**
 * When an activity's first root counts as drawn: the splash's hold of the window (`SplashHold`) lets frames through
 * from then on. Drawn when the first root is up and opaque — it goes up once its screens rendered, or at the limit of
 * its wait —; or [LIMIT_MILLIS] after the first `setRoot` whose root's `animations.setRoot.enter.waitForRender` isn't
 * `true`; at once with `false`; at once when a root that was on its way is destroyed. Drawn once, drawn for good:
 * [onDrawn] runs once. Main thread only.
 */
internal class FirstRootDraw(
    private val scheduler: AppearanceDispatcher.Scheduler,
    private val onDrawn: () -> Unit,
) {
    /** The first root has drawn, or the hold may end anyway. */
    var isDrawn = false
        private set

    private var cancelLimit: (() -> Unit)? = null

    /** A `setRoot` of this activity starts, with its root's `enter.waitForRender`. */
    fun rootSet(waitForRender: Bool) {
        if (isDrawn) return
        if (waitForRender.isFalse) return drawn()
        if (waitForRender.isTrue || cancelLimit != null) return
        cancelLimit = scheduler.schedule(LIMIT_MILLIS) {
            cancelLimit = null
            drawn()
        }
    }

    /** A root went up, opaque. */
    fun rootUp() = drawn()

    /** A root on its way was destroyed before it went up. */
    fun rootDestroyed() = drawn()

    /** The activity goes: nothing runs later. */
    fun cancel() {
        val cancel = cancelLimit ?: return
        cancelLimit = null
        cancel()
    }

    private fun drawn() {
        if (isDrawn) return
        isDrawn = true
        cancel()
        onDrawn()
    }

    companion object {
        /** How long after the first `setRoot` the hold lasts at most, unless its root waits for its render. */
        const val LIMIT_MILLIS = 500L
    }
}

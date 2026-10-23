package dev.apexnative.navigation.react.events

import android.os.Handler
import android.os.Looper
import dev.apexnative.navigation.react.ReloadCoordinator
import dev.apexnative.navigation.viewcontrollers.bottomtabs.BottomTabsController
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * The one place of the navigation state JavaScript hears (`ANN.NavigationStateChanged`): the screen the user sees and
 * the selected tab, the last state sent, the update posted for a pass of the main loop and its holds. Every change of
 * the screen the user sees comes with a screen appearing: a push, a pop, a modal, a tab, a menu.
 *
 * Two ways ask for it. The dispatcher asks for one update after the events of a pass ([setNeedsUpdate], coalesced and
 * held while a `setRoot` shows the modals and the overlays of its layout). A command's answer sends it at once
 * ([updateNow], never held), once per answer (`CommandAnswer.reportStateNow`): at the end of its transition, else
 * right before its promise. Each goes only when the state differs from the last one sent, through the JavaScript that
 * runs now. Main thread only.
 */
internal object NavigationState : AppearanceDispatcher.StateUpdates {
    /** What JavaScript hears: the screen the user sees (`null` for none) and the selected tab (`null` out of tabs). */
    data class State(val screenId: String?, val screenName: String?, val tab: Int?)

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    // The last state sent to the JavaScript that runs now; none before its first.
    private var sent: State? = null

    // The one posted update of a pass of the main loop, which checks the holds when it runs.
    private val updates = Updates(
        post = { task -> handler.post(task) },
        generation = { ReloadCoordinator.generation },
        update = ::updateNow,
    )

    override fun setNeedsUpdate() {
        if (EventEmitter.current().canSend) updates.setNeedsUpdate()
    }

    override fun hold() = updates.hold()

    override fun release() = updates.release()

    /**
     * Sends the state at once when it differs from the last one sent. A command's answer calls it once, at the end of
     * its transition or right before its promise: the event and the resolution reach JavaScript in that order, so the
     * state is new when the `await` of the command goes on. The update posted for the pass then finds nothing new.
     */
    override fun updateNow() {
        val emitter = EventEmitter.current()
        if (!emitter.canSend) return
        // The navigation of the activity the commands run in (`Navigators.current`).
        val navigator = Navigators.current()?.navigator ?: return
        val next = stateOf(navigator.visibleLayout)
        if (next == sent) return
        sent = next
        emitter.emitNavigationStateChanged(next.screenId, next.screenName, next.tab)
    }

    /** The new JavaScript has no state yet (`ANN.AppLaunched`): the next one goes to it whatever it is. */
    fun reset() {
        sent = null
    }

    /**
     * The JavaScript went with its layouts: a hold its `setRoot` left, and an update wanted under it, go with it, so
     * the new JavaScript's updates are never held by the old one's.
     */
    fun javaScriptWent() = updates.dropHolds()

    // The top modal, or the root ([layout]), down to the screen the user sees; overlays don't count. The tab is the
    // selected one of the nearest tabs the screen is in, the last met on the way down, in a modal too: the tabs
    // `selectTab` acts on from it. None for a screen out of tabs.
    internal fun stateOf(layout: ViewController<*>?): State {
        var tab: Int? = null
        var current = layout ?: return State(null, null, null)
        while (current is ParentController<*>) {
            if (current is BottomTabsController) tab = current.selectedIndex
            current = current.currentChild ?: return State(null, null, null)
        }
        return State(current.id, current.currentComponentName, tab)
    }

    /**
     * The posted update and its holds. [setNeedsUpdate] posts one update ([post]), coalesced: the asks of one pass of
     * the main loop make one, which runs [update] in a later pass. While held, an ask, or a posted update that runs
     * while held (asked before the hold was taken), only marks one as wanted, and the last release posts it. Released
     * right before the answer, that update finds nothing new: the state before the answer is sent at once, never
     * held, and an identical state is never sent twice. Every [hold] has one [release]; a release without a hold does
     * nothing, so the count never goes below zero. An update keeps the JavaScript it was asked for ([generation]): one
     * asked by a JavaScript that went before it runs is dropped; a newer ask of the new JavaScript still goes.
     */
    internal class Updates(
        private val post: (() -> Unit) -> Unit,
        private val generation: () -> Int = { 0 },
        private val update: () -> Unit,
    ) {
        /** The holds not released yet. */
        var count = 0
            private set

        // An update was asked while held.
        private var wanted = false
        // An update is posted and hasn't run yet.
        private var scheduled = false
        // The JavaScript the last ask was for.
        private var askedIn = 0

        /** Asks for one update in a later pass of the main loop; while held, once the last hold is released. */
        fun setNeedsUpdate() {
            askedIn = generation()
            if (count > 0) wanted = true else schedule()
        }

        /** The updates asked from now on, and those posted that run from now on, wait for as many [release]s. */
        fun hold() {
            count++
        }

        /** One hold ends; with the last, the update asked while held is posted now. */
        fun release() {
            if (count == 0) return
            count--
            if (count > 0 || !wanted) return
            wanted = false
            schedule()
        }

        /**
         * The holds and the update wanted under them go, unreleased: the JavaScript that took them went. An update
         * already posted stays; it runs only for the JavaScript it was asked for.
         */
        fun dropHolds() {
            count = 0
            wanted = false
        }

        private fun schedule() {
            if (scheduled) return
            scheduled = true
            post {
                scheduled = false
                // A hold taken since it was posted: the update waits for the release. The JavaScript it was asked for
                // went: nothing goes to the new one, which hears its first state after its own changes.
                when {
                    count > 0 -> wanted = true
                    askedIn == generation() -> update()
                }
            }
        }
    }
}

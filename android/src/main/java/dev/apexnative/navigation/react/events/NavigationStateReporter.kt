package dev.apexnative.navigation.react.events

import android.os.Handler
import android.os.Looper
import dev.apexnative.navigation.viewcontrollers.bottomtabs.BottomTabsController
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators
import dev.apexnative.navigation.viewcontrollers.parent.ParentController
import dev.apexnative.navigation.viewcontrollers.viewcontroller.ViewController

/**
 * Tells JavaScript the screen the user sees and the selected tab when they change. Every change of the screen the
 * user sees comes with a screen appearing: a push, a pop, a modal, a tab, a menu. One update after the events of a
 * pass of the main loop, and one at once before a command resolves its promise; each sent only when the state changed.
 */
object NavigationStateReporter {
    internal data class State(val screenId: String?, val screenName: String?, val tab: Int?)

    private val handler = Handler(Looper.getMainLooper())
    private var state: State? = null
    private var scheduled = false

    /** The new JavaScript has no state yet: the next one goes to it whatever it is. */
    fun reset() {
        state = null
    }

    fun setNeedsUpdate(emitter: EventEmitter) {
        if (scheduled) return
        scheduled = true
        handler.post {
            scheduled = false
            updateNow(emitter)
        }
    }

    /**
     * Sends the state at once when it differs from the last one sent. A command calls it right before it resolves its
     * promise: the event and the resolution reach JavaScript in that order, so the state is new when the `await` of
     * the command goes on. The update already scheduled then finds nothing new. Main thread only.
     */
    fun updateNow(emitter: EventEmitter) {
        // The navigation of the activity the commands run in (`Navigators.current`).
        val navigator = Navigators.current()?.navigator ?: return
        val next = stateOf(navigator.visibleLayout)
        if (next == state) return
        state = next
        emitter.emitNavigationStateChanged(next.screenId, next.screenName, next.tab)
    }

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
}

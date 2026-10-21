package dev.apexnative.navigation.react

import com.facebook.react.bridge.ReactContext
import dev.apexnative.navigation.NavigationActivity
import dev.apexnative.navigation.nativescreen.NativeScreenCalls
import dev.apexnative.navigation.react.events.EventEmitter
import dev.apexnative.navigation.react.events.NavigationStateReporter
import dev.apexnative.navigation.utils.UiThread
import dev.apexnative.navigation.viewcontrollers.navigator.BackCallbackState
import dev.apexnative.navigation.viewcontrollers.navigator.Navigator
import dev.apexnative.navigation.viewcontrollers.navigator.Navigators
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The JavaScript the navigation answers to. [generation] grows each time the JavaScript goes; every command takes it as
 * it arrives, and its queued work and its answer do nothing once it has moved: the commands of the JavaScript that went
 * get no answer. `ReactGateway` tells it when the JavaScript goes and when a new React context is ready; it resets the
 * navigation for the new JavaScript and sends `ANN.AppLaunched` once per pair of a navigation activity and a React
 * context (EVT-AppLaunched).
 */
internal object ReloadCoordinator {
    private val counter = AtomicInteger()

    // Which activity got AppLaunched from which React context. Main thread only.
    private val launches = AppLaunches<NavigationActivity, ReactContext>()

    /** The number of the JavaScript that runs now. Any thread. */
    val generation: Int
        get() = counter.get()

    /** Whether [generation] is still the number of the JavaScript that runs now. Any thread. */
    fun isCurrent(generation: Int): Boolean = generation == counter.get()

    /** The JavaScript went: the number grows. Any thread. */
    fun advance(): Int = counter.incrementAndGet()

    /**
     * The JavaScript goes (a reload, or the host is destroyed). Any thread: the number grows at once; the layouts of
     * every navigation activity go on the main thread, with the "reloading" mark, and so do the listeners of the
     * JavaScript that went (back, hinge).
     */
    fun onJavaScriptGoes() {
        advance()
        Navigator.setReloading(true)
        UiThread.runOnMain {
            launches.contextGone()
            try {
                Navigators.all().forEach { it.onReload() }
                BackCallbackState.setJsListeners(false)
                Hinge.setEnabled(Navigators.current(), false)
            } finally {
                Navigator.setReloading(false)
            }
        }
    }

    /**
     * The developer tools of [activity] reload the JavaScript ("R R", the reload broadcast): the number grows first, so
     * the navigation of the activity goes without a word to the JavaScript that goes, then its layers go
     * ([NavigationActivity.onReload]). The host's own reset follows as the reload destroys it. Main thread.
     */
    fun resetForDeveloperReload(activity: NavigationActivity) {
        advance()
        Navigator.setReloading(true)
        try {
            activity.onReload()
        } finally {
            Navigator.setReloading(false)
        }
    }

    /**
     * A React context is ready, the first or one after a reload: the calls of native screens the JavaScript that went
     * didn't answer fail, and every resumed navigation activity gets its `ANN.AppLaunched`.
     */
    fun onReactContextReady(context: ReactContext) {
        UiThread.runOnMain {
            launches.contextReady(context)
            NativeScreenCalls.failAll { name, componentId -> ErrorTexts.nativeCallReloaded(name, componentId) }
            // Scroll listeners that found no event dispatcher listen now.
            ScrollEvents.dispatcherReady()
            Navigators.all().filter { it.isNavigationResumed }.forEach(::launchIfDue)
        }
    }

    /**
     * Sends `ANN.AppLaunched` for [activity] when it is resumed, the React context is ready, and that context hasn't
     * sent it for this activity yet. The navigation state forgets what it sent last, so the new JavaScript gets the
     * next one whatever it is. Main thread only.
     */
    fun launchIfDue(activity: NavigationActivity) {
        val context = launches.takeDue(activity, activity.isNavigationResumed) ?: return
        NavigationStateReporter.reset()
        EventEmitter(context).emitAppLaunched()
    }
}

/**
 * When `ANN.AppLaunched` is due (EVT-AppLaunched): once per pair of an activity and a React context, to a resumed
 * activity once the context is ready; a paused activity gets it at its next resume. Both are held weakly. Main thread
 * only.
 */
internal class AppLaunches<A : Any, C : Any> {
    private var ready: WeakReference<C>? = null
    private val launched = WeakHashMap<A, WeakReference<C>>()

    /** [context] is ready for events. */
    fun contextReady(context: C) {
        ready = WeakReference(context)
    }

    /** The context went with its JavaScript: nothing is due until the next one is ready. */
    fun contextGone() {
        ready = null
    }

    /**
     * The context to send `ANN.AppLaunched` with to [activity] now, marked as sent; none when [resumed] is false, no
     * context is ready, or this context sent it to this activity already.
     */
    fun takeDue(activity: A, resumed: Boolean): C? {
        if (!resumed) return null
        val context = ready?.get() ?: return null
        if (launched[activity]?.get() === context) return null
        launched[activity] = WeakReference(context)
        return context
    }
}
